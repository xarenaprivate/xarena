package app.xarena.nativeapp

import android.Manifest
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import kotlinx.coroutines.Dispatchers
import coil.compose.AsyncImage
import androidx.compose.ui.layout.ContentScale
import kotlinx.coroutines.delay
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import org.json.JSONArray
import org.json.JSONObject
import java.util.UUID

/* XARENA Voice Lobby - native port of voice.js.
   Signaling: Supabase Realtime (Phoenix protocol) channel "voice:arena-lobby",
   presence key = random id, broadcast event "sig" {kind, from, to, data, handle}.
   Deterministic rule: lexicographically smaller id sends the offer. */

private class PhoenixChannel(
    val topic: String,
    val presenceKey: String,
    val onJoined: () -> Unit,
    val onJoins: (Map<String, JSONObject>) -> Unit,
    val onLeaves: (Map<String, JSONObject>) -> Unit,
    val onBroadcast: (JSONObject) -> Unit,
    val onState: (String) -> Unit
) {
    private var ws: okhttp3.WebSocket? = null
    private var ref = 0
    private var joinRef: String? = null
    private val client = okhttp3.OkHttpClient()
    private val handler = android.os.Handler(android.os.Looper.getMainLooper())
    @Volatile var open = false
        private set
    @Volatile var closedByUser = false
    private var retries = 0
    private val heartbeat = object : Runnable {
        override fun run() {
            if (open) {
                ref++
                ws?.send(JSONArray().put(JSONObject.NULL).put(ref.toString()).put("phoenix").put("heartbeat").put(JSONObject()).toString())
                handler.postDelayed(this, 25000)
            }
        }
    }

    fun connect() {
        val url = Api.BASE + "/realtime/v1/websocket?apikey=" + Api.KEY + "&vsn=2.0.0"
        onState("connecting")
        ws = client.newWebSocket(okhttp3.Request.Builder().url(url).build(), object : okhttp3.WebSocketListener() {
            override fun onOpen(webSocket: okhttp3.WebSocket, response: okhttp3.Response) {
                ref = 1; joinRef = "1"
                send("phx_join", JSONObject().put("config", JSONObject()
                    .put("broadcast", JSONObject().put("ack", false).put("self", false))
                    .put("presence", JSONObject().put("key", presenceKey))  // no "enabled" field: the realtime server disables presence relay when it is set
                    .put("postgres_changes", JSONArray())
                    .put("private", false)))
            }
            override fun onMessage(webSocket: okhttp3.WebSocket, text: String) {
                try {
                    val arr = JSONArray(text)
                    val refStr = arr.optString(1)
                    val event = arr.getString(3)
                    val payload = arr.getJSONObject(4)
                    when (event) {
                        "phx_reply" -> {
                            if (refStr == joinRef) {
                                if (payload.s("status") == "ok") {
                                    open = true; retries = 0; onState("joined"); onJoined()
                                    handler.postDelayed(heartbeat, 25000)
                                } else onState("join failed: " + payload.toString().take(80))
                            }
                        }
                        "presence_state" -> onJoins(parsePresences(extractPresences(payload)))  // v2 server sends the presences map directly
                        "presence_diff" -> {
                            onJoins(parsePresences(payload.optJSONObject("joins") ?: JSONObject()))
                            onLeaves(parsePresences(payload.optJSONObject("leaves") ?: JSONObject()))
                        }
                        "broadcast" -> onBroadcast(payload.optJSONObject("payload") ?: JSONObject())
                    }
                } catch (e: Exception) { onState("signal error: " + (e.message ?: "parse").take(60)) }
            }
            override fun onFailure(webSocket: okhttp3.WebSocket, t: Throwable, response: okhttp3.Response?) {
                open = false; onState("disconnected: " + (t.message ?: "network")); retry()
            }
            override fun onClosed(webSocket: okhttp3.WebSocket, code: Int, reason: String) {
                open = false; onState("disconnected"); retry()
            }
        })
    }

    private fun retry() {
        if (closedByUser || retries >= 5) return
        retries++
        onState("reconnecting (" + retries + "/5)...")
        handler.postDelayed({ if (!closedByUser && !open) connect() }, 3000)
    }

    private fun parsePresences(presences: JSONObject): Map<String, JSONObject> {
        val out = HashMap<String, JSONObject>()
        val it = presences.keys()
        while (it.hasNext()) {
            val k = it.next()
            val metas = presences.getJSONObject(k).optJSONArray("metas")
            if (metas != null && metas.length() > 0) out[k] = metas.getJSONObject(0)
        }
        return out
    }

    @Synchronized private fun send(event: String, payload: JSONObject) {
        ref++
        val msg = JSONArray().put(joinRef ?: JSONObject.NULL).put(ref.toString()).put(topic).put(event).put(payload)
        ws?.send(msg.toString())
    }

    fun broadcast(event: String, payload: JSONObject) {
        send("broadcast", JSONObject().put("type", "broadcast").put("event", event).put("payload", payload))
    }

    fun track(data: JSONObject) {
        send("presence", JSONObject().put("type", "presence").put("event", "track").put("payload", data))
    }

    fun close() {
        closedByUser = true; open = false
        handler.removeCallbacksAndMessages(null)
        ws?.close(1000, "bye"); client.dispatcher.executorService.shutdown()
    }
}

internal fun extractPresences(payload: JSONObject): JSONObject = payload.optJSONObject("presences") ?: payload

private data class VoicePeer(val id: String, val handle: String, val muted: Boolean, val listener: Boolean = false, val conn: String = "")

private class VoiceEngine(val onPeers: (List<VoicePeer>) -> Unit, val onState: (String) -> Unit) {
    private val myId = UUID.randomUUID().toString()
    private var myHandle = "Guest-" + (1000..9999).random()
    private var chan: PhoenixChannel? = null
    private var factory: org.webrtc.PeerConnectionFactory? = null
    private var audioSource: org.webrtc.AudioSource? = null
    private var audioTrack: org.webrtc.AudioTrack? = null
    private val pcs = HashMap<String, org.webrtc.PeerConnection>()
    private val pendingIce = HashMap<String, MutableList<org.webrtc.IceCandidate>>()
    private val remoteSet = HashSet<String>()
    private val roster = HashMap<String, VoicePeer>()
    var muted = false
        private set
    var listenOnly = false
        private set

    fun join(handle: String, room: String = "general", listenOnlyMode: Boolean = false) {
        listenOnly = listenOnlyMode
        if (handle.isNotBlank()) myHandle = handle
        try {
            val am = Api.ctx.getSystemService(android.content.Context.AUDIO_SERVICE) as android.media.AudioManager
            am.mode = android.media.AudioManager.MODE_IN_COMMUNICATION
            am.isSpeakerphoneOn = true
        } catch (e: Exception) { }
        val egl = org.webrtc.EglBase.create()
        val initOpts = org.webrtc.PeerConnectionFactory.InitializationOptions.builder(Api.ctx).createInitializationOptions()
        org.webrtc.PeerConnectionFactory.initialize(initOpts)
        factory = org.webrtc.PeerConnectionFactory.builder()
            .setVideoDecoderFactory(org.webrtc.DefaultVideoDecoderFactory(egl.eglBaseContext))
            .setVideoEncoderFactory(org.webrtc.DefaultVideoEncoderFactory(egl.eglBaseContext, true, true))
            .createPeerConnectionFactory()
        val audioConstraints = org.webrtc.MediaConstraints().apply {
            mandatory.add(org.webrtc.MediaConstraints.KeyValuePair("googEchoCancellation", "true"))
            mandatory.add(org.webrtc.MediaConstraints.KeyValuePair("googNoiseSuppression", "true"))
        }
        if (!listenOnly) {
            audioSource = factory!!.createAudioSource(audioConstraints)
            audioTrack = factory!!.createAudioTrack("voice-mic", audioSource)
        }
        val voiceChannel = if (room != "general") "realtime:voice:" + room else "realtime:voice:arena-lobby"
        chan = PhoenixChannel(voiceChannel, myId,
            onJoined = { chan?.track(JSONObject().put("handle", myHandle).put("muted", listenOnly).put("listener", listenOnly).put("at", System.currentTimeMillis())) },
            onJoins = { j -> main { onJoinsUpdate(j) } },
            onLeaves = { l -> main { onLeavesUpdate(l) } },
            onBroadcast = { p -> onSignal(p) },
            onState = { st -> main { onState(st) } })
        chan!!.connect()
    }

    private fun main(block: () -> Unit) {
        android.os.Handler(android.os.Looper.getMainLooper()).post { block() }
    }

    private fun onLeavesUpdate(leaves: Map<String, JSONObject>) {
        for ((id, _) in leaves) {
            pcs.remove(id)?.close()
            pendingIce.remove(id); remoteSet.remove(id)
            roster.remove(id)
        }
        onPeers(roster.values.sortedBy { it.handle })
    }

    private fun onJoinsUpdate(joined: Map<String, JSONObject>) {
        for ((id, meta) in joined) {
            if (id == myId) continue
            roster[id] = VoicePeer(id, meta.s("handle").ifBlank { id.take(6) }, meta.optBoolean("muted"), meta.optBoolean("listener"))
            if (pcs[id] == null && myId < id) offerTo(id)
        }
        onPeers(roster.values.sortedBy { it.handle })
    }

    private fun onSignal(p: JSONObject) {
        if (p.s("to") != myId) return
        val from = p.s("from")
        when (p.s("kind")) {
            "offer" -> {
                val pc = peerFor(from)
                pc.setRemoteDescription(object : SimpleObserver() {
                    override fun onSetSuccess() {
                        flushIce(from)
                        pc.createAnswer(object : SimpleSdpObserver() {
                            override fun onCreateSuccess(desc: org.webrtc.SessionDescription) {
                                pc.setLocalDescription(SimpleObserver(), desc)
                                chan?.broadcast("sig", JSONObject().put("kind", "answer").put("from", myId).put("to", from)
                                    .put("data", JSONObject().put("type", "answer").put("sdp", desc.description)).put("handle", myHandle))
                            }
                            override fun onCreateFailure(err: String?) { main { onState("answer failed: " + (err ?: "")) } }
                        }, org.webrtc.MediaConstraints())
                    }
                    override fun onSetFailure(err: String?) { main { onState("offer failed: " + (err ?: "")) } }
                }, org.webrtc.SessionDescription(org.webrtc.SessionDescription.Type.OFFER, p.optJSONObject("data")?.s("sdp") ?: return))
            }
            "answer" -> pcs[from]?.setRemoteDescription(object : SimpleObserver() {
                override fun onSetSuccess() { flushIce(from) }
                override fun onSetFailure(err: String?) { main { onState("answer rejected: " + (err ?: "")) } }
            }, org.webrtc.SessionDescription(org.webrtc.SessionDescription.Type.ANSWER, p.optJSONObject("data")?.s("sdp") ?: return))
            "ice" -> {
                val d = p.optJSONObject("data") ?: return
                val c = org.webrtc.IceCandidate(d.s("sdpMid"), d.optInt("sdpMLineIndex"), d.s("candidate"))
                if (remoteSet.contains(from)) pcs[from]?.addIceCandidate(c)
                else pendingIce.getOrPut(from) { ArrayList() }.add(c)
            }
        }
    }

    private fun flushIce(id: String) {
        remoteSet.add(id)
        pendingIce.remove(id)?.forEach { pcs[id]?.addIceCandidate(it) }
    }

    private fun offerTo(id: String) {
        val pc = peerFor(id)
        pc.createOffer(object : SimpleSdpObserver() {
            override fun onCreateSuccess(desc: org.webrtc.SessionDescription) {
                pc.setLocalDescription(SimpleObserver(), desc)
                chan?.broadcast("sig", JSONObject().put("kind", "offer").put("from", myId).put("to", id)
                    .put("data", JSONObject().put("type", "offer").put("sdp", desc.description)).put("handle", myHandle))
            }
        }, org.webrtc.MediaConstraints())
    }

    private fun peerFor(id: String): org.webrtc.PeerConnection {
        pcs[id]?.let { return it }
        val ice = listOf(
            org.webrtc.PeerConnection.IceServer.builder(listOf("stun:stun.l.google.com:19302", "stun:stun1.l.google.com:19302")).createIceServer(),
            org.webrtc.PeerConnection.IceServer.builder(listOf("turn:openrelay.metered.ca:80", "turn:openrelay.metered.ca:443", "turn:openrelay.metered.ca:80?transport=tcp", "turn:openrelay.metered.ca:443?transport=tcp", "turns:openrelay.metered.ca:443"))
                .setUsername("openrelayproject").setPassword("openrelayproject").createIceServer())
        val pc = factory!!.createPeerConnection(org.webrtc.PeerConnection.RTCConfiguration(ice), object : org.webrtc.PeerConnection.Observer {
            override fun onIceCandidate(c: org.webrtc.IceCandidate) {
                chan?.broadcast("sig", JSONObject().put("kind", "ice").put("from", myId).put("to", id)
                    .put("data", JSONObject().put("sdpMid", c.sdpMid).put("sdpMLineIndex", c.sdpMLineIndex).put("candidate", c.sdp)).put("handle", myHandle))
            }
            override fun onAddTrack(receiver: org.webrtc.RtpReceiver, streams: Array<out org.webrtc.MediaStream>) {
                (receiver.track() as? org.webrtc.AudioTrack)?.setEnabled(true)
            }
            override fun onSignalingChange(s: org.webrtc.PeerConnection.SignalingState?) {}
            override fun onIceConnectionChange(st: org.webrtc.PeerConnection.IceConnectionState?) {
                val label = when (st) {
                    org.webrtc.PeerConnection.IceConnectionState.CONNECTED, org.webrtc.PeerConnection.IceConnectionState.COMPLETED -> "connected"
                    org.webrtc.PeerConnection.IceConnectionState.CHECKING, org.webrtc.PeerConnection.IceConnectionState.NEW -> "connecting"
                    org.webrtc.PeerConnection.IceConnectionState.FAILED -> "failed"
                    org.webrtc.PeerConnection.IceConnectionState.DISCONNECTED -> "reconnecting"
                    else -> ""
                }
                main {
                    roster[id]?.let { roster[id] = it.copy(conn = label); onPeers(roster.values.sortedBy { p -> p.handle }) }
                    if (st == org.webrtc.PeerConnection.IceConnectionState.FAILED) {
                        roster[id]?.let { pcs[id]?.restartIce() }
                        onState("a peer connection dropped - retrying")
                    }
                }
            }
            override fun onIceConnectionReceivingChange(b: Boolean) {}
            override fun onIceGatheringChange(s: org.webrtc.PeerConnection.IceGatheringState?) {}
            override fun onIceCandidatesRemoved(cs: Array<out org.webrtc.IceCandidate>?) {}
            override fun onAddStream(s: org.webrtc.MediaStream?) {}
            override fun onRemoveStream(s: org.webrtc.MediaStream?) {}
            override fun onDataChannel(dc: org.webrtc.DataChannel?) {}
            override fun onRenegotiationNeeded() {}
        })!!
        audioTrack?.let { pc.addTrack(it, listOf("voice")) }
        pcs[id] = pc
        return pc
    }

    fun toggleMute(): Boolean {
        if (listenOnly || audioTrack == null) return true
        muted = !muted
        audioTrack?.setEnabled(!muted)
        chan?.track(JSONObject().put("handle", myHandle).put("muted", muted).put("listener", false).put("at", System.currentTimeMillis()))
        return muted
    }

    fun leave() {
        pcs.values.forEach { it.close() }; pcs.clear()
        pendingIce.clear(); remoteSet.clear()
        try {
            val am = Api.ctx.getSystemService(android.content.Context.AUDIO_SERVICE) as android.media.AudioManager
            am.isSpeakerphoneOn = false
            am.mode = android.media.AudioManager.MODE_NORMAL
        } catch (e: Exception) { }
        chan?.close(); chan = null
        audioTrack?.dispose(); audioSource?.dispose(); factory?.dispose()
        roster.clear(); onPeers(emptyList())
    }
}

private open class SimpleObserver : org.webrtc.SdpObserver {
    override fun onCreateSuccess(p0: org.webrtc.SessionDescription) {}
    override fun onSetSuccess() {}
    override fun onCreateFailure(p0: String?) {}
    override fun onSetFailure(p0: String?) {}
}
private open class SimpleSdpObserver : SimpleObserver()

@Composable
/* Voice lobby control plane (xr_voice_* RPCs): authoritative lobby presence, seats,
   waitlist, hand raise, history and polls. Reads fail soft (feature hidden) so a
   backend where the control plane is still staged keeps today's behavior. Server
   join is only reported AFTER the audio engine is in the room - no ghost lobby
   presence - and a waitlist answer immediately drops the audio again. p_speaking
   stays false until real client VAD exists; nothing here claims private audio. */
private data class VoiceLobbyInfo(
    val enabled: Boolean,
    val roomTitle: String,
    val roomTopic: String,
    val seatCap: Int,
    val joinedCount: Int,
    val visibleCount: Int,
    val speakingCount: Int,
    val sneakPeek: Boolean,
    val secretAudio: Boolean,
    val participants: List<VoiceLobbyPeer>)

private data class VoiceLobbyPeer(
    val id: String,
    val name: String,
    val avatar: String,
    val speaking: Boolean,
    val handRaised: Boolean)

private data class VoiceLobbySession(val roomId: String, val sessionId: String)

private data class VoiceLobbyPoll(
    val id: String,
    val question: String,
    val options: List<String>,
    val counts: List<Int>,
    val closesAt: String,
    val myChoice: Int)

private fun rpcObj(name: String, body: JSONObject): JSONObject? = runCatching {
    val txt = Api.authed("POST", "/rest/v1/rpc/" + name, body)
    runCatching { JSONObject(txt) }.getOrElse { JSONArray(txt).optJSONObject(0) }
}.getOrNull()

private fun voiceLobbyPresence(): VoiceLobbyInfo? {
    val o = rpcObj("xr_voice_lobby_presence", JSONObject()) ?: return null
    if (!o.optBoolean("ok", true)) return null
    val room = o.optJSONObject("room")
    val parts = ArrayList<VoiceLobbyPeer>()
    val arr = o.optJSONArray("participants")
    if (arr != null) {
        for (pi in 0 until arr.length()) {
            val p = arr.optJSONObject(pi) ?: continue
            parts.add(VoiceLobbyPeer(
                id = p.s("id"),
                name = p.s("username").ifBlank { p.s("ign") }.ifBlank { "Player" },
                avatar = p.s("avatar_url"),
                speaking = p.optBoolean("speaking"),
                handRaised = p.optBoolean("hand_raised")))
        }
    }
    return VoiceLobbyInfo(
        enabled = o.optBoolean("enabled"),
        roomTitle = room?.s("title") ?: "",
        roomTopic = room?.s("topic") ?: "",
        seatCap = room?.optInt("seat_cap") ?: 0,
        joinedCount = o.optInt("joined_count"),
        visibleCount = o.optInt("visible_count"),
        speakingCount = o.optInt("speaking_count"),
        sneakPeek = o.optBoolean("sneak_peek_available"),
        secretAudio = o.optBoolean("secret_audio_available"),
        participants = parts)
}

private fun voiceLobbyJoin(): JSONObject? = rpcObj("xr_voice_join", JSONObject())

/* null = transport problem (tolerate, next tick retries); otherwise the server error
   code such as session_expired or room_closed. */
private fun voiceLobbyHeartbeatError(roomId: String, sessionId: String): String? {
    val o = rpcObj("xr_voice_heartbeat",
        JSONObject().put("p_room", roomId).put("p_session", sessionId).put("p_speaking", false)) ?: return null
    if (o.optBoolean("ok")) return null
    return o.s("error").ifBlank { "unknown" }
}

private fun voiceLobbyLeave(roomId: String, sessionId: String) {
    runCatching { Api.authed("POST", "/rest/v1/rpc/xr_voice_leave",
        JSONObject().put("p_room", roomId).put("p_session", sessionId)) }
}

private fun voiceLobbyHandRaise(on: Boolean): Boolean {
    val o = rpcObj("xr_voice_hand_raise", JSONObject().put("p_on", on))
    return o != null && o.optBoolean("ok")
}

private fun voiceLobbyHistory(): JSONObject? = rpcObj("xr_voice_history", JSONObject())

private fun voiceLobbyPollResults(roomId: String): VoiceLobbyPoll? {
    val o = rpcObj("xr_voice_poll_results", JSONObject().put("p_room", roomId)) ?: return null
    val p = o.optJSONObject("poll") ?: return null
    val opts = ArrayList<String>(); val counts = ArrayList<Int>()
    val oa = p.optJSONArray("options"); val ca = p.optJSONArray("counts")
    if (oa != null) for (oi in 0 until oa.length()) opts.add(oa.optString(oi))
    if (ca != null) for (ci in 0 until ca.length()) counts.add(ca.optInt(ci))
    return VoiceLobbyPoll(p.s("id"), p.s("question"), opts, counts, p.s("closes_at"), p.optInt("my_choice", -1))
}

private fun voiceLobbyPollVote(pollId: String, choice: Int): Boolean {
    val o = rpcObj("xr_voice_poll_vote", JSONObject().put("p_poll", pollId).put("p_choice", choice))
    return o != null && (o.optBoolean("ok") || o.optBoolean("already_voted"))
}

fun VoiceScreen(onBack: () -> Unit) {
    val scope = rememberCoroutineScope()
    var engine by remember { mutableStateOf<VoiceEngine?>(null) }
    var peers by remember { mutableStateOf<List<VoicePeer>>(emptyList()) }
    var lobbyLabel by remember { mutableStateOf("General") }
    var state by remember { mutableStateOf("idle") }
    var muted by remember { mutableStateOf(false) }
    var micGranted by remember { mutableStateOf(
        androidx.core.content.ContextCompat.checkSelfPermission(Api.ctx, Manifest.permission.RECORD_AUDIO) == android.content.pm.PackageManager.PERMISSION_GRANTED) }
    val micLauncher = rememberLauncherForActivityResult(ActivityResultContracts.RequestPermission()) { micGranted = it }
    var err by remember { mutableStateOf<String?>(null) }
    var lobby by remember { mutableStateOf<VoiceLobbyInfo?>(null) }
    var lobbyChecked by remember { mutableStateOf(false) }
    var lobbySession by remember { mutableStateOf<VoiceLobbySession?>(null) }
    var waitlistRank by remember { mutableStateOf(0) }
    var handRaised by remember { mutableStateOf(false) }
    var lobbyPoll by remember { mutableStateOf<VoiceLobbyPoll?>(null) }
    var nextRoomLine by remember { mutableStateOf<String?>(null) }
    var lastRoomLine by remember { mutableStateOf<String?>(null) }

    DisposableEffect(Unit) { onDispose {
        engine?.leave()
        val sess = lobbySession
        if (sess != null) Thread { voiceLobbyLeave(sess.roomId, sess.sessionId) }.start()
    } }

    LaunchedEffect(Unit) {
        while (true) {
            val snap = withContext(Dispatchers.IO) {
                val info = voiceLobbyPresence()
                var nxt: String? = null; var lst: String? = null
                if (info?.enabled == true) {
                    val h = voiceLobbyHistory()
                    if (h != null && h.optBoolean("ok", true)) {
                        val nr = h.optJSONObject("next_room"); val lr = h.optJSONObject("last_room")
                        if (nr != null) nxt = "Next lobby: " + nr.s("title") + " - starts " + nr.s("starts_at").take(16).replace('T', ' ')
                        if (lr != null) lst = "Last lobby: " + lr.s("title") + " - peak " + lr.optInt("peak_count")
                    }
                }
                Triple(info, nxt, lst)
            }
            lobby = snap.first; lobbyChecked = true
            nextRoomLine = snap.second; lastRoomLine = snap.third
            delay(45_000)
        }
    }

    LaunchedEffect(state) {
        if (state == "joined" && lobbySession == null && lobby?.enabled == true) {
            val res = withContext(Dispatchers.IO) { voiceLobbyJoin() }
            if (res != null && res.optBoolean("ok")) {
                val sid = res.s("session_id")
                if (res.s("state") == "joined" && sid.isNotBlank()) {
                    lobbySession = VoiceLobbySession(res.s("room_id"), sid)
                } else if (res.s("state") == "waitlisted") {
                    waitlistRank = res.optInt("rank", 1)
                    engine?.leave(); engine = null; peers = emptyList(); state = "idle"; muted = false
                }
            }
        }
    }

    LaunchedEffect(lobbySession) {
        val sess = lobbySession ?: return@LaunchedEffect
        while (true) {
            delay(30_000)
            val hbErr = withContext(Dispatchers.IO) { voiceLobbyHeartbeatError(sess.roomId, sess.sessionId) }
            if (hbErr == "session_expired" || hbErr == "room_closed") {
                lobbySession = null
                engine?.leave(); engine = null; peers = emptyList(); state = "idle"; muted = false
                err = "Your lobby session ended - join again to re-enter."
                break
            }
            val p = withContext(Dispatchers.IO) { voiceLobbyPollResults(sess.roomId) }
            lobbyPoll = p
        }
    }

    Column(Modifier.fillMaxSize().background(Ink)) {
        PageHeader("Voice Lobby", "Real peer-to-peer voice - one open room", onBack)
        Column(Modifier.padding(16.dp)) {
            if (engine == null) {
                Text("One open room. Join to talk, or just listen - no mic needed to listen. Audio goes device to device; nothing is recorded.", color = Muted, fontSize = 13.sp)
                if (lobbyChecked && lobby?.enabled == true) {
                    Spacer(Modifier.height(12.dp))
                    Row(verticalAlignment = Alignment.CenterVertically) {
                        Box(Modifier.size(8.dp).clip(CircleShape).background(Color(0xFF22C55E)))
                        Spacer(Modifier.width(6.dp))
                        Text("LIVE NOW - " + lobby!!.joinedCount + " inside" + (if (lobby!!.speakingCount > 0) ", " + lobby!!.speakingCount + " speaking" else ""), color = Gold2, fontSize = 12.sp, fontWeight = FontWeight.Bold)
                    }
                    if (lobby!!.roomTopic.isNotBlank()) Text("Topic: " + lobby!!.roomTopic, color = Color.White, fontSize = 13.sp)
                    if (lobby!!.joinedCount == 0) Text("No one inside right now - be the first in.", color = Muted, fontSize = 12.sp)
                    if (lobby!!.participants.isNotEmpty()) {
                        Spacer(Modifier.height(8.dp))
                        Row(verticalAlignment = Alignment.CenterVertically) {
                            lobby!!.participants.take(6).forEach { pp ->
                                Column(horizontalAlignment = Alignment.CenterHorizontally, modifier = Modifier.padding(end = 10.dp)) {
                                    Box(Modifier.size(34.dp).clip(CircleShape).background(Look.cardBorder), contentAlignment = Alignment.Center) {
                                        if (pp.avatar.isNotBlank()) {
                                            AsyncImage(model = xrImageModel(pp.avatar), contentDescription = pp.name, modifier = Modifier.fillMaxSize().clip(CircleShape), contentScale = ContentScale.Crop)
                                        } else {
                                            Text(pp.name.firstOrNull()?.uppercase() ?: "P", color = Gold2, fontWeight = FontWeight.Bold)
                                        }
                                    }
                                    Text(if (pp.handRaised) "hand up" else pp.name.take(8), color = Muted, fontSize = 10.sp, maxLines = 1)
                                }
                            }
                            if (lobby!!.joinedCount > lobby!!.participants.size) {
                                Text("+" + (lobby!!.joinedCount - lobby!!.participants.size) + " more", color = Muted, fontSize = 11.sp)
                            }
                        }
                    }
                    nextRoomLine?.let { Text(it, color = Muted, fontSize = 12.sp) }
                    lastRoomLine?.let { Text(it, color = Muted, fontSize = 12.sp) }
                }
                if (waitlistRank > 0) {
                    Spacer(Modifier.height(12.dp))
                    Text("The lobby is full right now - you are #" + waitlistRank + " in line. A seat opens when someone leaves; tap Join Voice to try again.", color = Gold2, fontSize = 13.sp)
                }
                Spacer(Modifier.height(14.dp))
                val startJoin: (Boolean) -> Unit = { listenOnlyMode ->
                    val e = VoiceEngine(onPeers = { peers = it }, onState = { state = it })
                    engine = e
                    scope.launch(Dispatchers.IO) {
                        try {
                            val h = runCatching { Api.myProfile()?.s("username") ?: "" }.getOrDefault("")
                            val (room, rl) = runCatching { Api.chatMyRoom() }.getOrDefault("general" to "General")
                            lobbyLabel = rl
                            e.join(h, room, listenOnlyMode)
                            if (listenOnlyMode) { muted = true }
                        } catch (ex: Throwable) { runCatching { e.leave() }; err = "Voice unavailable on this device right now - the rest of the app is unaffected."; engine = null; state = "idle" }
                    }
                }
                GoldButton("JOIN VOICE") {
                    if (!micGranted) { micLauncher.launch(Manifest.permission.RECORD_AUDIO); return@GoldButton }
                    startJoin(false)
                }
                Spacer(Modifier.height(10.dp))
                Text("LISTEN ONLY - NO MIC NEEDED", color = Gold2, fontSize = 13.sp, fontWeight = FontWeight.Bold,
                    modifier = Modifier.clip(RoundedCornerShape(10.dp)).border(1.dp, Gold, RoundedCornerShape(10.dp))
                        .clickable { startJoin(true) }
                        .padding(horizontal = 14.dp, vertical = 10.dp))
                if (!micGranted) Text("Just want to listen? Tap LISTEN ONLY - no mic needed.", color = Muted, fontSize = 12.sp)
                err?.let { Text(it, color = ErrRed, fontSize = 13.sp) }
            } else {
                Text(state.uppercase(), color = if (state == "joined") Color(0xFF22C55E) else Gold2, fontSize = 12.sp, fontWeight = FontWeight.Bold)
                Text("Lobby: " + lobbyLabel, color = Muted, fontSize = 11.sp)
                Spacer(Modifier.height(10.dp))
                val eng = engine
                if (eng != null && eng.listenOnly) {
                    Text("LISTENING - MIC OFF", color = Gold2, fontSize = 13.sp, fontWeight = FontWeight.Bold)
                    Text("To talk, leave and rejoin with Join Voice.", color = Muted, fontSize = 12.sp)
                    Spacer(Modifier.height(10.dp))
                } else {
                    GoldButton(if (muted) "UNMUTE" else "MUTE") { muted = engine!!.toggleMute() }
                    Spacer(Modifier.height(10.dp))
                }
                if (lobbySession != null) {
                    Text(if (handRaised) "LOWER HAND" else "RAISE HAND", color = if (handRaised) Muted else Gold2, fontSize = 13.sp, fontWeight = FontWeight.Bold,
                        modifier = Modifier.clip(RoundedCornerShape(10.dp)).border(1.dp, if (handRaised) Muted else Gold2, RoundedCornerShape(10.dp))
                            .clickable {
                                val next = !handRaised
                                scope.launch(Dispatchers.IO) { if (voiceLobbyHandRaise(next)) { handRaised = next } }
                            }
                            .padding(horizontal = 14.dp, vertical = 10.dp))
                    if (handRaised) Text("Hand raised - the room can see you want to talk.", color = Gold2, fontSize = 12.sp)
                    Spacer(Modifier.height(10.dp))
                }
                lobbyPoll?.let { pl ->
                    Spacer(Modifier.height(12.dp))
                    Text("POLL", color = Muted, fontSize = 11.sp, fontWeight = FontWeight.Bold)
                    Text(pl.question, color = Color.White, fontSize = 14.sp, fontWeight = FontWeight.Bold)
                    Spacer(Modifier.height(6.dp))
                    pl.options.forEachIndexed { idx, opt ->
                        val voted = pl.myChoice >= 0
                        val mine = pl.myChoice == idx
                        val count = pl.counts.getOrElse(idx) { 0 }
                        Text((if (mine) "Voted: " else "") + opt + (if (voted) " - " + count + (if (count == 1) " vote" else " votes") else ""),
                            color = if (mine) Gold2 else Color.White, fontSize = 13.sp,
                            modifier = Modifier.fillMaxWidth().clip(RoundedCornerShape(10.dp))
                                .border(1.dp, if (mine) Gold else Look.cardBorder, RoundedCornerShape(10.dp))
                                .clickable(enabled = !voted) {
                                    val rid = lobbySession?.roomId ?: return@clickable
                                    scope.launch(Dispatchers.IO) {
                                        if (voiceLobbyPollVote(pl.id, idx)) {
                                            lobbyPoll = voiceLobbyPollResults(rid)
                                        }
                                    }
                                }
                                .padding(horizontal = 12.dp, vertical = 10.dp))
                        Spacer(Modifier.height(6.dp))
                    }
                }
                Box(Modifier.fillMaxWidth(), contentAlignment = Alignment.Center) {
                    Text("LEAVE", color = ErrRed, fontSize = 14.sp, fontWeight = FontWeight.Bold,
                        modifier = Modifier.fillMaxWidth().clip(RoundedCornerShape(12.dp)).border(1.dp, ErrRed, RoundedCornerShape(12.dp))
                            .clickable { val sess = lobbySession; engine?.leave(); engine = null; peers = emptyList(); state = "idle"; lobbySession = null; handRaised = false
                                if (sess != null) scope.launch(Dispatchers.IO) { voiceLobbyLeave(sess.roomId, sess.sessionId) } }
                            .padding(vertical = 14.dp))
                }
                Spacer(Modifier.height(14.dp))
                Text("IN THE LOBBY (" + (peers.size + 1) + ")", color = Muted, fontSize = 11.sp, fontWeight = FontWeight.Bold)
                Spacer(Modifier.height(6.dp))
                if (peers.isEmpty()) Text("Only you here right now - others appear when they join.", color = Muted, fontSize = 13.sp)
                peers.forEach { p ->
                    Row(Modifier.fillMaxWidth().padding(vertical = 4.dp), verticalAlignment = Alignment.CenterVertically) {
                        Box(Modifier.size(34.dp).clip(CircleShape).background(Look.cardBorder), contentAlignment = Alignment.Center) {
                            Text(p.handle.firstOrNull()?.uppercase() ?: "P", color = Gold2, fontWeight = FontWeight.Bold)
                        }
                        Spacer(Modifier.width(10.dp))
                        Text(p.handle, color = Color.White, fontSize = 15.sp, modifier = Modifier.weight(1f))
                        Column(horizontalAlignment = Alignment.End) {
                            Text(if (p.listener) "listening" else if (p.muted) "muted" else "● live", color = if (p.listener) Gold2 else if (p.muted) Muted else Color(0xFF22C55E), fontSize = 12.sp)
                            if (p.conn.isNotBlank()) Text(p.conn, color = if (p.conn == "connected") Color(0xFF22C55E) else if (p.conn == "failed") ErrRed else Muted, fontSize = 10.sp)
                        }
                    }
                }
            }
        }
    }
}
