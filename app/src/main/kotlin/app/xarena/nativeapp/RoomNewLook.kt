package app.xarena.nativeapp

import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.PickVisualMediaRequest
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.layout.widthIn
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.lazy.rememberLazyListState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableLongStateOf
import androidx.compose.runtime.mutableStateMapOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextDecoration
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import coil.compose.AsyncImage
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.delay
import kotlinx.coroutines.isActive
import kotlinx.coroutines.launch
import org.json.JSONObject
import java.util.UUID

/* Clan Room "new look" (v0.17.54 chat, v0.17.57 home + media) from the owner's mockup spec.
   ADDITIVE ONLY: the classic RoomScreen layout stays one tap away ("Classic" chip).
   Same data + same RPCs (room_info / room_feed / room_post / room_post_media / xr_room_growth)
   plus the clan-media storage contract (private bucket, signed URLs <=300s, 14-day TTL).
   No fabricated data: no online counts, no unread counts, no pre-join voice occupancy, no
   clan level/XP/trophies/war/buffs - those have no real source and stay hidden. */

fun xrIsoMs(s: String): Long {
    return runCatching {
        val m = java.util.regex.Pattern.compile("^(\\d{4}-\\d{2}-\\d{2})T(\\d{2}:\\d{2}:\\d{2})(?:\\.\\d+)?([+-]\\d{2}:?\\d{2}|Z)?").matcher(s.trim())
        if (!m.find()) return Long.MAX_VALUE
        val off = m.group(3) ?: "Z"
        val norm = m.group(1) + "T" + m.group(2) + (if (off == "Z") "+00:00" else off)
        val sdf = java.text.SimpleDateFormat("yyyy-MM-dd'T'HH:mm:ssXXX", java.util.Locale.US)
        sdf.parse(norm)?.time ?: Long.MAX_VALUE
    }.getOrDefault(Long.MAX_VALUE)
}

private fun xrStorageUpload(path: String, bytes: ByteArray, mime: String): Boolean {
    return try {
        val c = java.net.URL(Api.BASE + "/storage/v1/object/clan-media/" + path).openConnection() as java.net.HttpURLConnection
        c.requestMethod = "POST"
        c.setRequestProperty("apikey", Api.KEY)
        c.setRequestProperty("Authorization", "Bearer " + (Api.access ?: return false))
        c.setRequestProperty("Content-Type", mime)
        c.setRequestProperty("x-upsert", "false")
        c.doOutput = true
        c.outputStream.use { it.write(bytes) }
        c.responseCode in 200..299
    } catch (e: Exception) { false }
}

private fun xrStorageSign(path: String, expiresInSec: Int): String? {
    return try {
        val txt = Api.authed("POST", "/storage/v1/object/sign/clan-media/" + path, JSONObject().put("expiresIn", expiresInSec))
        val u = JSONObject(txt).optString("signedURL", "")
        if (u.isBlank()) null else if (u.startsWith("http")) u else Api.BASE + u
    } catch (e: Exception) { null }
}

private fun xrMmSs(ms: Long): String {
    val s = ms / 1000
    return (s / 60).toString() + ":" + (s % 60).toString().padStart(2, '0')
}

/* ============ CLAN ROOM HOME (mockup 1) ============ */
@Composable
fun RoomHomeNewLook(
    info: JSONObject,
    growth: JSONObject?,
    onBack: () -> Unit,
    onClassic: () -> Unit,
    onOpenChat: () -> Unit,
    onOpenVoice: () -> Unit
) {
    val memberCount = info.optInt("members")
    Column(Modifier.fillMaxSize().background(Ink)) {
        Row(Modifier.fillMaxWidth().padding(horizontal = 6.dp, vertical = 10.dp), verticalAlignment = Alignment.CenterVertically) {
            Text("<", color = Color.White, fontSize = 24.sp, modifier = Modifier.clickable { onBack() }.padding(horizontal = 10.dp, vertical = 2.dp))
            Box(Modifier.weight(1f), contentAlignment = Alignment.Center) {
                Text("CLAN ROOM", color = Color.White, fontSize = 16.sp, fontWeight = FontWeight.Bold)
            }
            Text("Classic", color = Muted, fontSize = 12.sp, fontWeight = FontWeight.Bold,
                modifier = Modifier.clip(RoundedCornerShape(8.dp)).border(1.dp, Muted, RoundedCornerShape(8.dp)).clickable { onClassic() }.padding(horizontal = 10.dp, vertical = 6.dp))
        }
        /* Hero card: real fields only - name, tag, member count. Level/XP/trophies hidden (no source). */
        Column(Modifier.fillMaxWidth().padding(horizontal = 16.dp, vertical = 6.dp).clip(RoundedCornerShape(16.dp)).background(Look.cardBg).border(1.dp, Look.cardBorder, RoundedCornerShape(16.dp)).padding(14.dp)) {
            Row(verticalAlignment = Alignment.CenterVertically) {
                Box(Modifier.size(52.dp).clip(RoundedCornerShape(14.dp)).background(Color(0xFF1A0E33)).border(1.dp, Gold.copy(alpha = .6f), RoundedCornerShape(14.dp)), contentAlignment = Alignment.Center) {
                    Text(info.s("name").take(1).uppercase(), color = Gold, fontSize = 22.sp, fontWeight = FontWeight.Bold)
                }
                Spacer(Modifier.width(12.dp))
                Column {
                    Text(info.s("name") + (info.s("tag").takeIf { it.isNotBlank() }?.let { " [$it]" } ?: ""), color = Color.White, fontSize = 18.sp, fontWeight = FontWeight.Bold)
                    Text(memberCount.toString() + " member" + (if (memberCount == 1) "" else "s"), color = Muted, fontSize = 12.sp)
                }
            }
        }
        val sq = growth?.optJSONObject("squad")
        val reqs = if (growth == null || growth.isNull("pending_join_requests")) null else growth.optInt("pending_join_requests")
        if (reqs != null && reqs > 0 && sq != null && sq.optBoolean("is_captain")) {
            Text(reqs.toString() + " join request" + (if (reqs == 1) "" else "s") + " waiting - review in Squads", color = NeonYellow, fontSize = 12.sp, fontWeight = FontWeight.Bold, modifier = Modifier.padding(horizontal = 16.dp, vertical = 4.dp))
        }
        Text("QUICK ACTIONS", color = Muted, fontSize = 11.sp, fontWeight = FontWeight.Bold, modifier = Modifier.padding(horizontal = 16.dp, vertical = 8.dp))
        /* Clan War row hidden (no war system). Clan Members row hidden until the real members
           screen lands - no dead buttons. */
        Column(Modifier.fillMaxWidth().padding(horizontal = 16.dp).clip(RoundedCornerShape(16.dp)).background(Look.cardBg).border(1.dp, Look.cardBorder, RoundedCornerShape(16.dp))) {
            Row(Modifier.fillMaxWidth().clickable { onOpenChat() }.padding(14.dp), verticalAlignment = Alignment.CenterVertically) {
                Box(Modifier.size(36.dp).clip(RoundedCornerShape(10.dp)).background(Color(0xFF1A0E33)), contentAlignment = Alignment.Center) { Text("C", color = Gold2, fontWeight = FontWeight.Bold) }
                Spacer(Modifier.width(12.dp))
                Text("Clan Chat", color = Color.White, fontSize = 15.sp, fontWeight = FontWeight.Bold, modifier = Modifier.weight(1f))
                Text(">", color = Gold, fontSize = 16.sp, fontWeight = FontWeight.Bold)
            }
            Box(Modifier.fillMaxWidth().padding(horizontal = 14.dp).height(1.dp).background(Look.cardBorder))
            Row(Modifier.fillMaxWidth().clickable { onOpenVoice() }.padding(14.dp), verticalAlignment = Alignment.CenterVertically) {
                Box(Modifier.size(36.dp).clip(RoundedCornerShape(10.dp)).background(Color(0xFF1A0E33)), contentAlignment = Alignment.Center) { Text("V", color = Gold2, fontWeight = FontWeight.Bold) }
                Spacer(Modifier.width(12.dp))
                Column(Modifier.weight(1f)) {
                    Text("Clan Voice", color = Color.White, fontSize = 15.sp, fontWeight = FontWeight.Bold)
                    Text("Join the clan voice room", color = Muted, fontSize = 11.sp)
                }
                Text("JOIN", color = Ink, fontSize = 12.sp, fontWeight = FontWeight.Bold,
                    modifier = Modifier.clip(RoundedCornerShape(8.dp)).background(Gold).clickable { onOpenVoice() }.padding(horizontal = 14.dp, vertical = 7.dp))
            }
        }
    }
}

/* ============ CLAN CHAT (mockup 2: media + links + 14-day prune) ============ */
@Composable
fun RoomNewLook(
    info: JSONObject,
    growth: JSONObject?,
    msgs: List<JSONObject>,
    input: String,
    onInput: (String) -> Unit,
    err: String?,
    onBack: () -> Unit,
    onClassic: () -> Unit,
    onOpenSquads: () -> Unit,
    onOpenVoice: () -> Unit,
    squadId: String?,
    onSendMedia: (String, String, String, Int?) -> Unit,
    onSend: (String) -> Unit
) {
    val scope = rememberCoroutineScope()
    val ctx = LocalContext.current
    val listState = rememberLazyListState()
    var pinDismissed by remember { mutableStateOf(false) }
    var nowTick by remember { mutableLongStateOf(System.currentTimeMillis()) }
    LaunchedEffect(Unit) { while (isActive) { delay(20_000); nowTick = System.currentTimeMillis() } }
    LaunchedEffect(msgs.size) { if (msgs.isNotEmpty()) listState.scrollToItem(msgs.size - 1) }
    val pin = growth?.optJSONObject("pin")
    val memberCount = info.optInt("members")
    /* 14-day TTL: prune already-rendered messages by expires_at, not just new fetches. */
    val visible = msgs.filter { m ->
        val ea = m.optString("expires_at", "")
        ea.isBlank() || xrIsoMs(ea) > nowTick
    }
    val signedUrls = remember { mutableStateMapOf<Long, String>() }
    var playingId by remember { mutableLongStateOf(-1L) }
    var player by remember { mutableStateOf<android.media.MediaPlayer?>(null) }
    var busyMedia by remember { mutableStateOf(false) }
    var mediaErr by remember { mutableStateOf<String?>(null) }
    var recording by remember { mutableStateOf<android.media.MediaRecorder?>(null) }
    var recFile by remember { mutableStateOf<java.io.File?>(null) }
    var recStart by remember { mutableLongStateOf(0L) }
    var recElapsed by remember { mutableLongStateOf(0L) }
    var micGranted by remember { mutableStateOf(
        androidx.core.content.ContextCompat.checkSelfPermission(Api.ctx, android.Manifest.permission.RECORD_AUDIO) == android.content.pm.PackageManager.PERMISSION_GRANTED) }
    val micLauncher = rememberLauncherForActivityResult(ActivityResultContracts.RequestPermission()) { micGranted = it }
    val pickImage = rememberLauncherForActivityResult(ActivityResultContracts.PickVisualMedia()) { uri ->
        if (uri == null || squadId == null) return@rememberLauncherForActivityResult
        scope.launch(Dispatchers.IO) {
            busyMedia = true; mediaErr = null
            try {
                val mime = ctx.contentResolver.getType(uri) ?: "image/jpeg"
                val ext = when (mime) { "image/png" -> "png"; "image/webp" -> "webp"; else -> "jpg" }
                val bytes = ctx.contentResolver.openInputStream(uri)?.use { it.readBytes() }
                if (bytes == null || bytes.size > 8 * 1024 * 1024) { mediaErr = "Image too large (max 8 MB)." }
                else {
                    val path = squadId + "/" + (Api.uid ?: "me") + "/" + UUID.randomUUID().toString() + "." + ext
                    if (xrStorageUpload(path, bytes, mime)) onSendMedia("image", path, "", null)
                    else mediaErr = "Image upload failed - try again."
                }
            } catch (e: Exception) { mediaErr = "Image upload failed - try again." }
            busyMedia = false
        }
    }
    fun stopRecording(send: Boolean) {
        val r = recording ?: return
        recording = null
        runCatching { r.stop() }; runCatching { r.release() }
        val f = recFile; recFile = null
        val dur = System.currentTimeMillis() - recStart
        if (send && f != null && squadId != null && dur >= 1000) {
            scope.launch(Dispatchers.IO) {
                busyMedia = true; mediaErr = null
                try {
                    val bytes = f.readBytes()
                    val path = squadId + "/" + (Api.uid ?: "me") + "/" + UUID.randomUUID().toString() + ".m4a"
                    if (bytes.size <= 8 * 1024 * 1024 && xrStorageUpload(path, bytes, "audio/mp4")) onSendMedia("voice", path, "", dur.toInt().coerceIn(1, 120000))
                    else mediaErr = "Voice note upload failed - try again."
                } catch (e: Exception) { mediaErr = "Voice note upload failed - try again." }
                runCatching { f.delete() }
                busyMedia = false
            }
        } else if (f != null) runCatching { f.delete() }
    }
    LaunchedEffect(recording != null) {
        recElapsed = 0L
        while (isActive && recording != null) {
            delay(1000); recElapsed = System.currentTimeMillis() - recStart
            if (recElapsed >= 120_000) stopRecording(true)
        }
    }
    Column(Modifier.fillMaxSize().background(Ink)) {
        Row(Modifier.fillMaxWidth().padding(horizontal = 6.dp, vertical = 10.dp), verticalAlignment = Alignment.CenterVertically) {
            Text("<", color = Color.White, fontSize = 24.sp, modifier = Modifier.clickable { onBack() }.padding(horizontal = 10.dp, vertical = 2.dp))
            Box(Modifier.size(38.dp).clip(CircleShape).background(Look.cardBg).border(1.dp, Gold.copy(alpha = .6f), CircleShape), contentAlignment = Alignment.Center) {
                Text(info.s("name").take(1).uppercase(), color = Gold, fontWeight = FontWeight.Bold)
            }
            Spacer(Modifier.width(10.dp))
            Column(Modifier.weight(1f)) {
                Text(info.s("name") + (info.s("tag").takeIf { it.isNotBlank() }?.let { " [$it]" } ?: ""), color = Color.White, fontSize = 16.sp, fontWeight = FontWeight.Bold)
                Text(memberCount.toString() + " member" + (if (memberCount == 1) "" else "s"), color = Muted, fontSize = 11.sp)
            }
            Row(verticalAlignment = Alignment.CenterVertically,
                modifier = Modifier.clip(RoundedCornerShape(14.dp)).border(1.dp, Gold.copy(alpha = .7f), RoundedCornerShape(14.dp)).clickable { onOpenVoice() }.padding(horizontal = 10.dp, vertical = 6.dp)) {
                Box(Modifier.size(7.dp).clip(CircleShape).background(Color(0xFF22C55E)))
                Spacer(Modifier.width(6.dp))
                Text("Voice Room - Join", color = Gold, fontSize = 12.sp, fontWeight = FontWeight.Bold)
            }
            Spacer(Modifier.width(8.dp))
            Text("Classic", color = Muted, fontSize = 12.sp, fontWeight = FontWeight.Bold,
                modifier = Modifier.clip(RoundedCornerShape(8.dp)).border(1.dp, Muted, RoundedCornerShape(8.dp)).clickable { onClassic() }.padding(horizontal = 10.dp, vertical = 6.dp))
        }
        if (pin != null && !pinDismissed && pin.s("body").isNotBlank()) {
            Row(Modifier.fillMaxWidth().padding(horizontal = 12.dp, vertical = 4.dp).clip(RoundedCornerShape(12.dp)).background(Look.cardBg).border(1.dp, NeonYellow, RoundedCornerShape(12.dp)).padding(10.dp), verticalAlignment = Alignment.CenterVertically) {
                Column(Modifier.weight(1f)) {
                    Text("PINNED", color = NeonYellow, fontSize = 10.sp, fontWeight = FontWeight.Bold)
                    Text(pin.s("body"), color = Color.White, fontSize = 13.sp)
                }
                Text("X", color = Muted, fontSize = 14.sp, fontWeight = FontWeight.Bold, modifier = Modifier.clickable { pinDismissed = true }.padding(6.dp))
            }
        }
        val sq = growth?.optJSONObject("squad")
        val reqs = if (growth == null || growth.isNull("pending_join_requests")) null else growth.optInt("pending_join_requests")
        if (reqs != null && reqs > 0 && sq != null && sq.optBoolean("is_captain")) {
            Text(reqs.toString() + " join request" + (if (reqs == 1) "" else "s") + " waiting - review in Squads", color = NeonYellow, fontSize = 12.sp, fontWeight = FontWeight.Bold, modifier = Modifier.padding(horizontal = 16.dp, vertical = 2.dp).clickable { onOpenSquads() })
        }
        LazyColumn(state = listState, modifier = Modifier.weight(1f), contentPadding = PaddingValues(horizontal = 12.dp, vertical = 6.dp)) {
            if (visible.isEmpty()) item { Text("No messages yet - write the first one and say hello to your squad.", color = Muted, fontSize = 13.sp, modifier = Modifier.padding(vertical = 12.dp)) }
            items(visible) { m ->
                val mine = m.s("user_id") == Api.uid
                val kind = m.optString("kind", "text")
                Row(Modifier.fillMaxWidth().padding(vertical = 4.dp), horizontalArrangement = if (mine) Arrangement.End else Arrangement.Start) {
                    if (!mine) {
                        Box(Modifier.size(34.dp).clip(CircleShape).background(Look.cardBg).border(1.dp, Gold.copy(alpha = .5f), CircleShape), contentAlignment = Alignment.Center) {
                            Text(m.s("by").take(1).uppercase(), color = Gold2, fontSize = 13.sp, fontWeight = FontWeight.Bold)
                        }
                        Spacer(Modifier.width(8.dp))
                    }
                    Column(Modifier.widthIn(max = 280.dp)) {
                        if (!mine) Row(verticalAlignment = Alignment.CenterVertically) {
                            Text(m.s("by"), color = Gold2, fontSize = 12.sp, fontWeight = FontWeight.Bold)
                            Spacer(Modifier.width(8.dp))
                            Text(dmTime(m.s("created_at")), color = Muted, fontSize = 10.sp)
                        }
                        Column(Modifier.padding(top = 2.dp).clip(RoundedCornerShape(12.dp)).background(if (mine) Color(0xFF7A2E2E) else Look.cardBg).padding(horizontal = 12.dp, vertical = 8.dp)) {
                            val mid = m.optLong("id")
                            if (kind == "image") {
                                val url = signedUrls[mid]
                                LaunchedEffect(mid) {
                                    if (signedUrls[mid] == null) {
                                        val remaining = ((xrIsoMs(m.optString("expires_at", "")) - System.currentTimeMillis()) / 1000).toInt()
                                        if (remaining > 0) {
                                            val u = kotlinx.coroutines.withContext(Dispatchers.IO) { xrStorageSign(m.s("media_path"), minOf(300, remaining)) }
                                            if (u != null) signedUrls[mid] = u
                                        }
                                    }
                                }
                                if (url != null) {
                                    AsyncImage(model = url, contentDescription = m.s("body").ifBlank { "image" }, contentScale = ContentScale.Crop,
                                        modifier = Modifier.fillMaxWidth().heightIn(max = 260.dp).clip(RoundedCornerShape(8.dp)))
                                } else Text("Loading image...", color = Muted, fontSize = 12.sp)
                                if (m.s("body").isNotBlank()) Text(m.s("body"), color = Color.White, fontSize = 14.sp, modifier = Modifier.padding(top = 4.dp))
                            } else if (kind == "voice") {
                                val url = signedUrls[mid]
                                LaunchedEffect(mid) {
                                    if (signedUrls[mid] == null) {
                                        val remaining = ((xrIsoMs(m.optString("expires_at", "")) - System.currentTimeMillis()) / 1000).toInt()
                                        if (remaining > 0) {
                                            val u = kotlinx.coroutines.withContext(Dispatchers.IO) { xrStorageSign(m.s("media_path"), minOf(300, remaining)) }
                                            if (u != null) signedUrls[mid] = u
                                        }
                                    }
                                }
                                Row(verticalAlignment = Alignment.CenterVertically) {
                                    Box(Modifier.size(34.dp).clip(CircleShape).background(if (playingId == mid) Gold else Look.cardBorder).clickable(enabled = url != null) {
                                        if (playingId == mid) { runCatching { player?.stop(); player?.release() }; player = null; playingId = -1L }
                                        else if (url != null) {
                                            runCatching { player?.release() }
                                            playingId = mid
                                            val mp = android.media.MediaPlayer()
                                            player = mp
                                            runCatching {
                                                mp.setDataSource(url)
                                                mp.setOnPreparedListener { it.start() }
                                                mp.setOnCompletionListener { playingId = -1L; runCatching { mp.release() }; player = null }
                                                mp.prepareAsync()
                                            }
                                        }
                                    }, contentAlignment = Alignment.Center) {
                                        Text(if (playingId == mid) "||" else ">", color = if (playingId == mid) Ink else Color.White, fontSize = 13.sp, fontWeight = FontWeight.Bold)
                                    }
                                    Spacer(Modifier.width(8.dp))
                                    Text(if (playingId == mid) "Playing..." else "Voice note - " + xrMmSs(m.optLong("duration_ms")), color = if (url != null) Color.White else Muted, fontSize = 13.sp)
                                }
                            } else {
                                Text(m.s("body"), color = Color.White, fontSize = 14.sp)
                                val links = Regex("https?://\\S+").findAll(m.s("body")).map { it.value }.toList()
                                links.forEach { u ->
                                    Text(u, color = Gold2, fontSize = 12.sp, textDecoration = TextDecoration.Underline,
                                        modifier = Modifier.padding(top = 3.dp).clickable {
                                            runCatching { ctx.startActivity(android.content.Intent(android.content.Intent.ACTION_VIEW, android.net.Uri.parse(u))) }
                                        })
                                }
                            }
                            if (mine) Text(dmTime(m.s("created_at")), color = Muted, fontSize = 10.sp)
                        }
                    }
                }
            }
        }
        if (err != null) Text(err, color = ErrRed, fontSize = 12.sp, modifier = Modifier.padding(horizontal = 12.dp))
        mediaErr?.let { Text(it, color = ErrRed, fontSize = 12.sp, modifier = Modifier.padding(horizontal = 12.dp)) }
        if (recording != null) {
            Row(Modifier.fillMaxWidth().padding(horizontal = 12.dp, vertical = 4.dp).clip(RoundedCornerShape(14.dp)).background(Color(0xFF1A0E33)).border(1.dp, ErrRed, RoundedCornerShape(14.dp)).padding(horizontal = 14.dp, vertical = 10.dp), verticalAlignment = Alignment.CenterVertically) {
                Box(Modifier.size(8.dp).clip(CircleShape).background(ErrRed))
                Spacer(Modifier.width(8.dp))
                Text("Recording - " + xrMmSs(recElapsed) + " / 2:00", color = Color.White, fontSize = 13.sp, modifier = Modifier.weight(1f))
                Text("CANCEL", color = Muted, fontSize = 12.sp, fontWeight = FontWeight.Bold, modifier = Modifier.clickable { stopRecording(false) }.padding(horizontal = 8.dp, vertical = 4.dp))
                Text("SEND", color = Ink, fontSize = 12.sp, fontWeight = FontWeight.Bold,
                    modifier = Modifier.clip(RoundedCornerShape(8.dp)).background(Gold).clickable { stopRecording(true) }.padding(horizontal = 14.dp, vertical = 7.dp))
            }
        }
        Row(Modifier.fillMaxWidth().padding(horizontal = 12.dp, vertical = 4.dp).clip(RoundedCornerShape(14.dp)).background(Color(0xFF1A0E33)).border(1.dp, Gold.copy(alpha = .4f), RoundedCornerShape(14.dp)).padding(horizontal = 14.dp, vertical = 10.dp), verticalAlignment = Alignment.CenterVertically) {
            Box(Modifier.size(7.dp).clip(CircleShape).background(Color(0xFF22C55E)))
            Spacer(Modifier.width(8.dp))
            Text("Voice Room", color = Color.White, fontSize = 13.sp, fontWeight = FontWeight.Bold)
            Spacer(Modifier.width(8.dp))
            Text("Tap to join", color = Muted, fontSize = 11.sp, modifier = Modifier.weight(1f))
            Text("JOIN", color = Ink, fontSize = 12.sp, fontWeight = FontWeight.Bold,
                modifier = Modifier.clip(RoundedCornerShape(8.dp)).background(Gold).clickable { onOpenVoice() }.padding(horizontal = 14.dp, vertical = 7.dp))
        }
        Row(Modifier.fillMaxWidth().padding(horizontal = 12.dp, vertical = 4.dp), horizontalArrangement = Arrangement.spacedBy(8.dp)) {
            listOf("READY", "GG", "WP", "1V1").forEach { quick ->
                Box(Modifier.clip(RoundedCornerShape(14.dp)).border(1.dp, Gold.copy(alpha = .7f), RoundedCornerShape(14.dp))
                    .clickable { onSend(quick) }.padding(horizontal = 12.dp, vertical = 6.dp)) { Text(quick, color = Gold, fontSize = 12.sp, fontWeight = FontWeight.Bold) }
            }
        }
        Row(Modifier.fillMaxWidth().background(Color(0xFF1A0E33)).padding(8.dp), verticalAlignment = Alignment.CenterVertically) {
            Box(Modifier.size(40.dp).clip(CircleShape).background(Look.cardBg).border(1.dp, Gold.copy(alpha = .5f), CircleShape)
                .clickable(enabled = !busyMedia && squadId != null) { pickImage.launch(PickVisualMediaRequest(ActivityResultContracts.PickVisualMedia.ImageOnly)) }, contentAlignment = Alignment.Center) {
                Text("+", color = Gold, fontSize = 20.sp, fontWeight = FontWeight.Bold)
            }
            Spacer(Modifier.width(8.dp))
            OutlinedTextField(input, onInput, placeholder = { Text("Message your squad...") }, modifier = Modifier.weight(1f), maxLines = 3)
            Spacer(Modifier.width(8.dp))
            Box(Modifier.size(40.dp).clip(CircleShape).background(if (recording != null) ErrRed else Look.cardBg).border(1.dp, Gold.copy(alpha = .5f), CircleShape)
                .clickable(enabled = !busyMedia && squadId != null) {
                    if (recording != null) { stopRecording(true); return@clickable }
                    if (!micGranted) { micLauncher.launch(android.Manifest.permission.RECORD_AUDIO); return@clickable }
                    val f = java.io.File(Api.ctx.cacheDir, "xr_voice_" + System.currentTimeMillis() + ".m4a")
                    val r = if (android.os.Build.VERSION.SDK_INT >= 31) android.media.MediaRecorder(Api.ctx) else android.media.MediaRecorder()
                    try {
                        r.setAudioSource(android.media.MediaRecorder.AudioSource.MIC)
                        r.setOutputFormat(android.media.MediaRecorder.OutputFormat.MPEG_4)
                        r.setAudioEncoder(android.media.MediaRecorder.AudioEncoder.AAC)
                        r.setAudioEncodingBitRate(96000)
                        r.setAudioSamplingRate(44100)
                        r.setMaxDuration(120000)
                        r.setOutputFile(f.absolutePath)
                        r.prepare(); r.start()
                        recFile = f; recStart = System.currentTimeMillis(); recording = r
                    } catch (e: Exception) { runCatching { r.release() }; runCatching { f.delete() }; mediaErr = "Could not start recording - check mic permission." }
                }, contentAlignment = Alignment.Center) {
                Text("MIC", color = if (recording != null) Color.White else Gold, fontSize = 10.sp, fontWeight = FontWeight.Bold)
            }
            Spacer(Modifier.width(8.dp))
            Box(Modifier.size(48.dp).clip(CircleShape).background(if (input.isBlank()) Muted else Gold).clickable(enabled = input.isNotBlank()) { onSend(input.trim()) }, contentAlignment = Alignment.Center) {
                Text(">", color = Ink, fontSize = 18.sp, fontWeight = FontWeight.Bold)
            }
        }
    }
}
