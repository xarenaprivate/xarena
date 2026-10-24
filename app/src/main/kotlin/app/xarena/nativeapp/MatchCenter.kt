package app.xarena.nativeapp

import android.graphics.Bitmap
import android.graphics.BitmapFactory
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.PickVisualMediaRequest
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import org.json.JSONArray
import org.json.JSONObject
import java.io.ByteArrayOutputStream
import java.text.SimpleDateFormat
import java.util.Date
import java.util.Locale
import java.util.TimeZone

private fun fmtWhen(iso: String): String = try {
    val clean = iso.replace("T", " ").take(19)
    val d = SimpleDateFormat("yyyy-MM-dd HH:mm:ss", Locale.US).apply { timeZone = TimeZone.getTimeZone("UTC") }.parse(clean)
    SimpleDateFormat("d MMM, h:mm a", Locale.US).format(d ?: Date())
} catch (e: Exception) { "" }

private suspend fun pickJpeg(ctx: android.content.Context, uri: android.net.Uri): ByteArray? = withContext(Dispatchers.IO) {
    try {
        ctx.contentResolver.openInputStream(uri)?.use { input ->
            val raw = input.readBytes()
            val bounds = BitmapFactory.Options().apply { inJustDecodeBounds = true }
            BitmapFactory.decodeByteArray(raw, 0, raw.size, bounds)
            var sample = 1
            while (maxOf(bounds.outWidth, bounds.outHeight) / sample > 1280) sample *= 2
            val bmp = BitmapFactory.decodeByteArray(raw, 0, raw.size, BitmapFactory.Options().apply { inSampleSize = sample })
            val bos = ByteArrayOutputStream()
            bmp.compress(Bitmap.CompressFormat.JPEG, 85, bos)
            bos.toByteArray()
        }
    } catch (e: Exception) { null }
}

/** Match Center - rooms, check-in, result proofs, reports. Same RPCs as the site's tournaments page. */
@Composable
fun MatchCenterScreen(onBack: () -> Unit) {
    val ctx = LocalContext.current
    val scope = rememberCoroutineScope()
    var nonce by remember { mutableIntStateOf(0) }
    Column(Modifier.fillMaxSize()) {
        Brand()
        Text("Match Center", color = Gold, fontWeight = FontWeight.Bold, fontSize = 18.sp, modifier = Modifier.padding(horizontal = 16.dp))
        Text("Rooms unlock for registered players shortly before each match.", color = Muted, fontSize = 12.sp, modifier = Modifier.padding(horizontal = 16.dp))
        LoadState({ Api.publicGet("/rest/v1/matches_public?select=*&order=starts_at.asc.nullslast") }) { matches ->
            if (matches.length() == 0) EmptyNote("No matches scheduled yet", "Match rooms appear here as match control schedules them. Registered players see the room unlock 15 minutes before start.")
            else LazyColumn {
                items((0 until matches.length()).map { matches.getJSONObject(it) }) { m ->
                    MatchCard(m, ctx, scope, onChanged = { nonce++ })
                }
                item { Spacer(Modifier.height(16.dp)) }
            }
        }
        Text("Back to tournaments", color = Gold2, fontSize = 13.sp, modifier = Modifier.align(Alignment.CenterHorizontally)
            .clip(RoundedCornerShape(6.dp)).background(Color(0x22FFFFFF)).clickable { onBack() }.padding(horizontal = 14.dp, vertical = 8.dp))
        Spacer(Modifier.height(12.dp))
    }
}

@Composable
private fun MatchCard(m: JSONObject, ctx: android.content.Context, scope: kotlinx.coroutines.CoroutineScope, onChanged: () -> Unit) {
    val mid = m.optLong("id")
    var roomText by remember { mutableStateOf<String?>(null) }
    var checkinText by remember { mutableStateOf<String?>(null) }
    var checkedIn by remember { mutableStateOf(false) }
    var proofOpen by remember { mutableStateOf(false) }
    var reportOpen by remember { mutableStateOf(false) }
    var busy by remember { mutableStateOf(false) }

    LaunchedEffect(mid) {
        try {
            val st = Api.authedRpc("match_checkin_status", JSONObject().put("p_match", mid))
            if (st.optBoolean("ok")) {
                checkedIn = st.optBoolean("me")
                checkinText = if (st.optBoolean("me")) "You are checked in (" + st.optInt("count") + " in)"
                else if (st.optInt("count") > 0) st.optInt("count").toString() + " checked in" else null
            }
        } catch (e: Exception) { }
    }

    val statusCol = when (m.s("status")) { "live" -> Color(0xFF4ADE80); "done" -> Muted; else -> Gold2 }
    PanelCard {
        Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.SpaceBetween, verticalAlignment = Alignment.CenterVertically) {
            Text(m.s("title"), color = Color.White, fontSize = 16.sp, fontWeight = FontWeight.Bold, modifier = Modifier.weight(1f))
            Text(MatchLabels.label(m), color = statusCol, fontSize = 11.sp, fontWeight = FontWeight.Bold)
        }
        Text(m.s("tournament_id") + " - " + fmtWhen(m.s("starts_at")), color = Muted, fontSize = 12.sp)
        Spacer(Modifier.height(8.dp))
        Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
            if (!MatchLabels.isPostponed(m)) SmallButton("ROOM ID & PASS") {
                if (busy) return@SmallButton
                busy = true
                scope.launch {
                    try {
                        val r = Api.authedRpc("match_room", JSONObject().put("p_match", mid))
                        roomText = if (r.optBoolean("ok")) "Room ID: " + r.s("room_id") + "  -  Password: " + r.s("room_pass").ifBlank { "(none)" }
                        else r.s("error").ifBlank { "Room not available yet." }
                    } catch (e: Exception) { roomText = e.message }
                    busy = false
                }
            }
            if (!MatchLabels.isPostponed(m)) SmallButton(if (checkedIn) "CHECKED IN" else "CHECK IN") {
                if (busy || checkedIn) return@SmallButton
                busy = true
                scope.launch {
                    try {
                        val r = Api.authedRpc("match_checkin", JSONObject().put("p_match", mid))
                        if (r.optBoolean("ok")) { checkedIn = true; checkinText = "You are checked in (" + r.optInt("count") + " in)" }
                        else checkinText = r.s("error").ifBlank { "Check-in not open yet." }
                    } catch (e: Exception) { checkinText = e.message }
                    busy = false
                }
            }
            SmallButton("RESULT PROOF") { proofOpen = !proofOpen }
            SmallButton("REPORT") { reportOpen = !reportOpen }
        }
        roomText?.let { Text(it, color = Gold2, fontSize = 13.sp, fontWeight = FontWeight.Bold) }
        checkinText?.let { Text(it, color = Muted, fontSize = 12.sp) }
        if (proofOpen) ProofForm(mid, ctx, scope) { proofOpen = false }
        if (reportOpen) ReportForm(mid, ctx, scope) { reportOpen = false }
    }
}

@Composable
private fun SmallButton(label: String, onClick: () -> Unit) {
    Text(label, color = Gold, fontSize = 11.sp, fontWeight = FontWeight.Bold,
        modifier = Modifier.clip(RoundedCornerShape(8.dp)).background(Color(0x22F5C451)).clickable { onClick() }.padding(horizontal = 10.dp, vertical = 7.dp))
}

@Composable
private fun ProofForm(matchId: Long, ctx: android.content.Context, scope: kotlinx.coroutines.CoroutineScope, onDone: () -> Unit) {
    var note by remember { mutableStateOf("") }
    var img by remember { mutableStateOf<ByteArray?>(null) }
    var msg by remember { mutableStateOf<String?>(null) }
    var busy by remember { mutableStateOf(false) }
    val picker = rememberLauncherForActivityResult(ActivityResultContracts.PickVisualMedia()) { uri ->
        if (uri != null) scope.launch { img = pickJpeg(ctx, uri); msg = if (img != null) "Screenshot attached." else "Could not read that image." }
    }
    Column(Modifier.padding(top = 8.dp)) {
        Text("Submit result proof", color = Color.White, fontWeight = FontWeight.Bold, fontSize = 14.sp)
        Text("Screenshot of the final scoreboard. The owner reviews every proof - approved ones pay Arena Points.", color = Muted, fontSize = 11.sp)
        OutlinedTextField(note, { note = it }, label = { Text("Note (e.g. 8 kills, #2 finish)") }, singleLine = true, modifier = Modifier.fillMaxWidth())
        Spacer(Modifier.height(6.dp))
        Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
            SmallButton(if (img != null) "IMAGE ATTACHED" else "ATTACH SCREENSHOT") {
                picker.launch(PickVisualMediaRequest(ActivityResultContracts.PickVisualMedia.ImageOnly))
            }
            SmallButton(if (busy) "SENDING..." else "SUBMIT") {
                if (busy) return@SmallButton
                if (img == null) { msg = "Attach the screenshot first."; return@SmallButton }
                busy = true
                scope.launch {
                    try {
                        val url = Api.cloudinaryUpload(img!!)
                        val r = Api.authedRpc("proof_submit", JSONObject().put("p_match", matchId).put("p_image", url).put("p_note", note))
                        msg = if (r.optBoolean("ok")) "Submitted - the owner reviews it soon." else r.s("error").ifBlank { "Could not submit." }
                        if (r.optBoolean("ok")) onDone()
                    } catch (e: Exception) { msg = e.message ?: "Upload failed." }
                    busy = false
                }
            }
        }
        msg?.let { Text(it, color = if (it.contains("Submitted")) Color(0xFF4ADE80) else Gold2, fontSize = 12.sp) }
    }
}

@Composable
private fun ReportForm(matchId: Long, ctx: android.content.Context, scope: kotlinx.coroutines.CoroutineScope, onDone: () -> Unit) {
    var name by remember { mutableStateOf("") }
    var reason by remember { mutableStateOf("") }
    var msg by remember { mutableStateOf<String?>(null) }
    var busy by remember { mutableStateOf(false) }
    Column(Modifier.padding(top = 8.dp)) {
        Text("Report a player", color = Color.White, fontWeight = FontWeight.Bold, fontSize = 14.sp)
        Text("Hacks, teaming or match-fixing. False reports cost your own standing.", color = Muted, fontSize = 11.sp)
        OutlinedTextField(name, { name = it }, label = { Text("Player name or squad") }, singleLine = true, modifier = Modifier.fillMaxWidth())
        OutlinedTextField(reason, { reason = it }, label = { Text("What happened?") }, modifier = Modifier.fillMaxWidth())
        Spacer(Modifier.height(6.dp))
        SmallButton(if (busy) "SENDING..." else "SEND REPORT") {
            if (busy || name.isBlank() || reason.isBlank()) { if (name.isBlank() || reason.isBlank()) msg = "Name and reason are needed."; return@SmallButton }
            busy = true
            scope.launch {
                try {
                    val r = Api.authedRpc("cheat_report", JSONObject().put("p_name", name).put("p_reason", reason).put("p_match", matchId))
                    msg = if (r.optBoolean("ok")) "Report sent to the owner." else r.s("error").ifBlank { "Could not send the report." }
                    if (r.optBoolean("ok")) onDone()
                } catch (e: Exception) { msg = e.message }
                busy = false
            }
        }
        msg?.let { Text(it, color = Gold2, fontSize = 12.sp) }
    }
}
