package app.xarena.nativeapp

import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.lazy.rememberLazyListState
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
import kotlinx.coroutines.delay
import kotlinx.coroutines.isActive
import kotlinx.coroutines.launch
import org.json.JSONObject
import java.text.SimpleDateFormat
import java.util.Date
import java.util.Locale

private fun fmtTime(ts: String): String = try {
    val clean = ts.replace("T", " ").take(19)
    val d = SimpleDateFormat("yyyy-MM-dd HH:mm:ss", Locale.US).parse(clean)
    SimpleDateFormat("h:mm a", Locale.US).format(d ?: Date())
} catch (e: Exception) { "" }

private fun colorFor(h: String): Color {
    val palette = listOf(0xFF7C4DFF, 0xFF448AFF, 0xFF00BFA5, 0xFFFF7043, 0xFFEC407A, 0xFFAB47BC, 0xFF26A69A)
    return Color(palette[(h.hashCode() and Int.MAX_VALUE) % palette.size])
}

/** World Chat - real messages from the shared chat_messages table, polling every 10s (Realtime channel comes later). */
@Composable
fun ChatScreen(onGoTab: (Int) -> Unit = {}, onBack: (() -> Unit)? = null) {
    var handleReady by remember { mutableStateOf(Api.chatHandle != null && Api.chatToken != null) }
    Column(Modifier.fillMaxSize()) {
        if (onBack != null) PageHeader("Chat", "World chat lobby", onBack) else Brand()
        ChatChips(onGoTab)
        if (!handleReady) ChatGate(onReady = { handleReady = true })
        else ChatRoom()
    }
}

/* Lobby chips like the web: next real match + quick links. */
@Composable
private fun ChatChips(onGoTab: (Int) -> Unit) {
    var next by remember { mutableStateOf<JSONObject?>(null) }
    LaunchedEffect(Unit) {
        next = runCatching {
            val arr = Api.publicGet("/rest/v1/matches_public?select=title,starts_at,status&order=starts_at.asc&limit=10")
            val now = System.currentTimeMillis()
            (0 until arr.length()).map { arr.getJSONObject(it) }.firstOrNull {
                val st = it.s("starts_at")
                st.isNotBlank() && runCatching { java.time.Instant.parse(st).toEpochMilli() }.getOrDefault(0L) > now - 15 * 60000 &&
                    it.s("status") != "done"
            }
        }.getOrNull()
    }
    Row(Modifier.fillMaxWidth().padding(horizontal = 16.dp, vertical = 2.dp)) {
        val m = next
        if (m != null) {
            val whenStr = runCatching {
                val t = java.time.Instant.parse(m.s("starts_at")).atZone(java.time.ZoneId.systemDefault())
                t.dayOfMonth.toString() + " " + t.month.name.take(3).lowercase().replaceFirstChar { it.uppercase() } + " " +
                    String.format("%d:%02d %s", (t.hour % 12).let { if (it == 0) 12 else it }, t.minute, if (t.hour < 12) "AM" else "PM")
            }.getOrDefault("")
            Text("🎮 " + m.s("title") + " · " + whenStr, color = Ink, fontSize = 11.sp, fontWeight = FontWeight.Bold,
                modifier = Modifier.padding(end = 6.dp).clip(RoundedCornerShape(8.dp)).background(Gold)
                    .clickable { onGoTab(1) }.padding(horizontal = 10.dp, vertical = 6.dp))
        }
        Text("🏆 Leaderboard", color = Gold, fontSize = 11.sp, fontWeight = FontWeight.Bold,
            modifier = Modifier.padding(end = 6.dp).clip(RoundedCornerShape(8.dp)).background(Color(0x22FFFFFF))
                .clickable { onGoTab(2) }.padding(horizontal = 10.dp, vertical = 6.dp))
        Text("👥 Squads", color = Gold, fontSize = 11.sp, fontWeight = FontWeight.Bold,
            modifier = Modifier.clip(RoundedCornerShape(8.dp)).background(Color(0x22FFFFFF))
                .clickable { onGoTab(5) }.padding(horizontal = 10.dp, vertical = 6.dp))
    }
}

@Composable
fun ChatGate(onReady: () -> Unit) {
    var handle by remember { mutableStateOf("") }
    var busy by remember { mutableStateOf(false) }
    var err by remember { mutableStateOf<String?>(null) }
    val scope = rememberCoroutineScope()
    LaunchedEffect(Unit) {
        // Pre-fill with the profile username, like the site's account-linked handle.
        if (handle.isBlank()) handle = runCatching { Api.myProfile()?.s("username") ?: "" }.getOrDefault("")
    }
    Column(Modifier.fillMaxSize().padding(24.dp), verticalArrangement = Arrangement.Center) {
        PanelCard {
            Text("Pick your chat name", color = Color.White, fontSize = 20.sp, fontWeight = FontWeight.Bold)
            Spacer(Modifier.height(6.dp))
            Text("This is how players see you in World Chat.", color = Muted, fontSize = 13.sp)
            Spacer(Modifier.height(10.dp))
            OutlinedTextField(handle, { handle = it }, label = { Text("Chat name (3-20 letters, numbers, _)") }, singleLine = true, modifier = Modifier.fillMaxWidth())
            err?.let { Spacer(Modifier.height(8.dp)); Text(it, color = ErrRed, fontSize = 13.sp) }
            Spacer(Modifier.height(14.dp))
            GoldButton(if (busy) "CHECKING..." else "ENTER WORLD CHAT", enabled = !busy && Regex("^[A-Za-z0-9_]{3,20}$").matches(handle)) {
                busy = true; err = null
                scope.launch {
                    try {
                        val r = Api.chatClaim(handle.trim())
                        if (r.optBoolean("ok")) onReady()
                        else err = r.s("error").ifBlank { "Could not claim the name right now. Try again." }
                    } catch (e: Exception) { err = e.message ?: "Could not claim the name right now." }
                    busy = false
                }
            }
        }
    }
}

private data class ChatMsg(val id: Long, val handle: String, val body: String, val at: String)

@Composable
fun ChatRoom() {
    val scope = rememberCoroutineScope()
    val listState = rememberLazyListState()
    var msgs by remember { mutableStateOf(listOf<ChatMsg>()) }
    var loadErr by remember { mutableStateOf<String?>(null) }
    var input by remember { mutableStateOf("") }
    var sending by remember { mutableStateOf(false) }
    var notice by remember { mutableStateOf<String?>(null) }
    var lastId by remember { mutableLongStateOf(0L) }

    fun addRows(arr: org.json.JSONArray, toFront: Boolean) {
        val fresh = (0 until arr.length()).map {
            val o: JSONObject = arr.getJSONObject(it)
            ChatMsg(o.optLong("id"), o.s("handle"), o.s("body"), o.s("created_at"))
        }.filter { it.id > 0 }
        if (fresh.isEmpty()) return
        msgs = (if (toFront) fresh + msgs else msgs + fresh).distinctBy { it.id }.sortedBy { it.id }
        lastId = maxOf(lastId, fresh.maxOf { it.id })
    }

    LaunchedEffect(Unit) {
        try {
            val hist = Api.chatHistory()
            val rev = org.json.JSONArray()
            for (i in hist.length() - 1 downTo 0) rev.put(hist.getJSONObject(i))
            addRows(rev, toFront = false)
            loadErr = null
        } catch (e: Exception) { loadErr = e.message ?: "Could not load chat." }
        var ticks = 0
        while (isActive) {
            delay(10_000)
            ticks++
            try {
                if (ticks % 18 == 0 || lastId == 0L) {
                    // Full resync every ~3 min: world chat is purged every few hours server-side,
                    // and a purge can restart ids - an append-only lastId poll would freeze forever.
                    val hist = Api.chatHistory()
                    val rev = org.json.JSONArray()
                    for (i in hist.length() - 1 downTo 0) rev.put(hist.getJSONObject(i))
                    val fresh = (0 until rev.length()).map { rev.getJSONObject(it) }
                        .map { ChatMsg(it.optLong("id"), it.s("handle"), it.s("body"), it.s("created_at")) }
                        .filter { it.id > 0 }
                    msgs = fresh
                    lastId = fresh.maxOfOrNull { it.id } ?: 0L
                } else {
                    addRows(Api.chatSince(lastId), toFront = false)
                }
            } catch (e: Exception) { /* keep polling */ }
        }
    }

    LaunchedEffect(msgs.size) { if (msgs.isNotEmpty()) listState.animateScrollToItem(msgs.size - 1) }

    Column(Modifier.fillMaxSize()) {
        if (loadErr != null && msgs.isEmpty()) {
            Column(Modifier.weight(1f).padding(24.dp), verticalArrangement = Arrangement.Center, horizontalAlignment = Alignment.CenterHorizontally) {
                Text(loadErr ?: "", color = ErrRed); Spacer(Modifier.height(12.dp))
            }
        } else if (msgs.isEmpty()) {
            Box(Modifier.weight(1f), contentAlignment = Alignment.Center) { CircularProgressIndicator(color = Gold) }
        } else {
            LazyColumn(state = listState, modifier = Modifier.weight(1f).padding(horizontal = 12.dp)) {
                items(msgs) { m ->
                    val mine = m.handle == Api.chatHandle
                    val luna = m.handle == "Luna"
                    Column(Modifier.fillMaxWidth().padding(vertical = 3.dp), horizontalAlignment = if (mine) Alignment.End else Alignment.Start) {
                        Row(verticalAlignment = Alignment.CenterVertically) {
                            if (!mine) {
                                Box(Modifier.size(28.dp).clip(CircleShape).background(if (luna) Color(0xFF4B2F8F) else colorFor(m.handle)),
                                    contentAlignment = Alignment.Center) {
                                    Text(if (luna) "✦" else m.handle.take(1).uppercase(), color = Color.White, fontSize = 13.sp, fontWeight = FontWeight.Bold)
                                }
                                Spacer(Modifier.width(6.dp))
                            }
                            Column(Modifier.widthIn(max = 280.dp).clip(RoundedCornerShape(14.dp))
                                .background(if (mine) Color(0xFF3A2A6B) else Card.copy(alpha = .9f))
                                .border(1.dp, if (mine) Gold.copy(alpha = .4f) else Color(0x22FFFFFF), RoundedCornerShape(14.dp))
                                .padding(horizontal = 12.dp, vertical = 8.dp)) {
                                if (!mine) Text(m.handle + if (luna) " (AI)" else "", color = if (luna) Gold2 else colorFor(m.handle), fontSize = 11.sp, fontWeight = FontWeight.Bold)
                                Text(m.body, color = Color.White, fontSize = 14.sp)
                                Text(fmtTime(m.at), color = Muted, fontSize = 10.sp)
                            }
                        }
                    }
                }
            }
        }
        notice?.let { Text(it, color = Gold2, fontSize = 12.sp, modifier = Modifier.padding(horizontal = 16.dp, vertical = 2.dp)) }
        Text("World chat refreshes every few hours - old messages get cleared.", color = Muted, fontSize = 11.sp,
            modifier = Modifier.fillMaxWidth().background(Color(0xFF1A0E33)).padding(horizontal = 12.dp, vertical = 2.dp))
        Row(Modifier.fillMaxWidth().background(Color(0xFF1A0E33)).padding(8.dp), verticalAlignment = Alignment.CenterVertically) {
            OutlinedTextField(input, { input = it }, placeholder = { Text("Message World Chat...") },
                modifier = Modifier.weight(1f), maxLines = 3)
            Spacer(Modifier.width(8.dp))
            Box(Modifier.size(48.dp).clip(CircleShape).background(if (input.isBlank() || sending) Muted else Gold)
                .clickable(enabled = input.isNotBlank() && !sending) {
                    val body = input.trim()
                    if (body.isEmpty()) return@clickable
                    sending = true; notice = null
                    scope.launch {
                        try {
                            val r = Api.chatSend(body)
                            if (r.optBoolean("ok")) { input = "" } else {
                                when (r.s("error")) {
                                    "unauthorized" -> { Api.clearChat(); notice = "Chat name no longer active - reopen the Chat tab to pick it again." }
                                    "muted" -> notice = "You are muted by the owner for now."
                                    "rate_limited" -> notice = "Slow down a little - too many messages."
                                    else -> notice = r.s("error").ifBlank { "Message not sent." }
                                }
                            }
                        } catch (e: Exception) { notice = "Message not sent - check your connection." }
                        sending = false
                    }
                }, contentAlignment = Alignment.Center) {
                Text(">", color = Color(0xFF2A1400), fontWeight = FontWeight.Black, fontSize = 18.sp)
            }
        }
    }
}
