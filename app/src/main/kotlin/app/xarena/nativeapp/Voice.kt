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
                    .put("presence", JSONObject().put("key", presenceKey).put("enabled", false))
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
                        "presence_state" -> onJoins(parsePresences(payload.optJSONObject("presences") ?: JSONObject()))
                        "presence_diff" -> {
                            onJoins(parsePresences(payload.optJSONObject("joins") ?: JSONObject()))
                            onLeaves(parsePresences(payload.optJSONObject("leaves") ?: JSONObject()))
                        }
                        "broadcast" -> onBroadcast(payload.optJSONObject("payload") ?: JSONObject())
                    }
                } catch (e: Exception) { }
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

private data class VoicePeer(val id: String, val handle: String, val muted: Boolean, val conn: String = "")

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

    fun join(handle: String) {
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
        audioSource = factory!!.createAudioSource(audioConstraints)
        audioTrack = factory!!.createAudioTrack("voice-mic", audioSource)
        chan = PhoenixChannel("realtime:voice:arena-lobby", myId,
            onJoined = { chan?.track(JSONObject().put("handle", myHandle).put("muted", false).put("at", System.currentTimeMillis())) },
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
            roster[id] = VoicePeer(id, meta.s("handle").ifBlank { id.take(6) }, meta.optBoolean("muted"))
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
                    override fun onSetSuccess() { flushIce(from) }
                }, org.webrtc.SessionDescription(org.webrtc.SessionDescription.Type.OFFER, p.optJSONObject("data")?.s("sdp") ?: return))
                pc.createAnswer(object : SimpleSdpObserver() {
                    override fun onCreateSuccess(desc: org.webrtc.SessionDescription) {
                        pc.setLocalDescription(SimpleObserver(), desc)
                        chan?.broadcast("sig", JSONObject().put("kind", "answer").put("from", myId).put("to", from)
                            .put("data", JSONObject().put("type", "answer").put("sdp", desc.description)).put("handle", myHandle))
                    }
                }, org.webrtc.MediaConstraints())
            }
            "answer" -> pcs[from]?.setRemoteDescription(object : SimpleObserver() {
                override fun onSetSuccess() { flushIce(from) }
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
            org.webrtc.PeerConnection.IceServer.builder(listOf("turn:openrelay.metered.ca:80", "turn:openrelay.metered.ca:443", "turns:openrelay.metered.ca:443"))
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
                    if (st == org.webrtc.PeerConnection.IceConnectionState.FAILED) onState("a peer connection failed - rejoin to retry")
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
        pc.addTrack(audioTrack, listOf("voice"))
        pcs[id] = pc
        return pc
    }

    fun toggleMute(): Boolean {
        muted = !muted
        audioTrack?.setEnabled(!muted)
        chan?.track(JSONObject().put("handle", myHandle).put("muted", muted).put("at", System.currentTimeMillis()))
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
fun VoiceScreen(onBack: () -> Unit) {
    val scope = rememberCoroutineScope()
    var engine by remember { mutableStateOf<VoiceEngine?>(null) }
    var peers by remember { mutableStateOf<List<VoicePeer>>(emptyList()) }
    var state by remember { mutableStateOf("idle") }
    var muted by remember { mutableStateOf(false) }
    var micGranted by remember { mutableStateOf(
        androidx.core.content.ContextCompat.checkSelfPermission(Api.ctx, Manifest.permission.RECORD_AUDIO) == android.content.pm.PackageManager.PERMISSION_GRANTED) }
    val micLauncher = rememberLauncherForActivityResult(ActivityResultContracts.RequestPermission()) { micGranted = it }
    var err by remember { mutableStateOf<String?>(null) }

    DisposableEffect(Unit) { onDispose { engine?.leave() } }

    Column(Modifier.fillMaxSize().background(Ink)) {
        PageHeader("Voice Lobby", "Real peer-to-peer voice - one open room", onBack)
        Column(Modifier.padding(16.dp)) {
            if (engine == null) {
                Text("Talk live with everyone in the lobby. Audio goes device to device; nothing is recorded.", color = Muted, fontSize = 13.sp)
                Spacer(Modifier.height(14.dp))
                GoldButton("JOIN VOICE") {
                    if (!micGranted) { micLauncher.launch(Manifest.permission.RECORD_AUDIO); return@GoldButton }
                    val e = VoiceEngine(onPeers = { peers = it }, onState = { state = it })
                    engine = e
                    scope.launch(Dispatchers.IO) {
                        try {
                            val h = runCatching { Api.myProfile()?.s("username") ?: "" }.getOrDefault("")
                            e.join(h)
                        } catch (ex: Throwable) { runCatching { e.leave() }; err = "Voice unavailable on this device right now - the rest of the app is unaffected."; engine = null; state = "idle" }
                    }
                }
                if (!micGranted) Text("Microphone permission is needed - Android will ask.", color = Muted, fontSize = 12.sp)
                err?.let { Text(it, color = ErrRed, fontSize = 13.sp) }
            } else {
                Text(state.uppercase(), color = if (state == "joined") Color(0xFF22C55E) else Gold2, fontSize = 12.sp, fontWeight = FontWeight.Bold)
                Spacer(Modifier.height(10.dp))
                Row {
                    GoldButton(if (muted) "UNMUTE" else "MUTE") { muted = engine!!.toggleMute() }
                    Spacer(Modifier.width(10.dp))
                    Text("LEAVE", color = ErrRed, fontSize = 13.sp, fontWeight = FontWeight.Bold,
                        modifier = Modifier.clip(RoundedCornerShape(10.dp)).border(1.dp, ErrRed, RoundedCornerShape(10.dp))
                            .clickable { engine?.leave(); engine = null; peers = emptyList(); state = "idle" }
                            .padding(horizontal = 18.dp, vertical = 11.dp))
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
                            Text(if (p.muted) "muted" else "● live", color = if (p.muted) Muted else Color(0xFF22C55E), fontSize = 12.sp)
                            if (p.conn.isNotBlank()) Text(p.conn, color = if (p.conn == "connected") Color(0xFF22C55E) else if (p.conn == "failed") ErrRed else Muted, fontSize = 10.sp)
                        }
                    }
                }
            }
        }
    }
}
