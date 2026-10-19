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
import kotlinx.coroutines.launch
import org.json.JSONArray
import org.json.JSONObject

private data class LunaMsg(val role: String, val content: String)

/** Luna - the site's own luna-chat Edge Function, same session model. Shown only when she can really reply. */
@Composable
fun LunaScreen(onBack: (() -> Unit)? = null) {
    var alive by remember { mutableStateOf<Boolean?>(null) }
    LaunchedEffect(Unit) { alive = Api.lunaAlive() }
    Column(Modifier.fillMaxSize()) {
        if (onBack != null) PageHeader("Luna", "AI analyst", onBack) else Brand()
        when (alive) {
            null -> Box(Modifier.fillMaxSize(), contentAlignment = Alignment.Center) { CircularProgressIndicator(color = Gold) }
            false -> EmptyNote("Luna is offline", "The assistant backend is not live right now. Nothing here is faked - she appears as soon as she can really reply.")
            true -> LunaChat()
        }
    }
}

@Composable
fun LunaChat() {
    val scope = rememberCoroutineScope()
    val listState = rememberLazyListState()
    var msgs by remember { mutableStateOf(listOf<LunaMsg>()) }
    var input by remember { mutableStateOf("") }
    var thinking by remember { mutableStateOf(false) }

    LaunchedEffect(Unit) {
        val h = Api.lunaHistoryLoad()
        msgs = (0 until h.length()).map { val o = h.getJSONObject(it); LunaMsg(o.optString("role"), o.optString("content")) }
    }
    LaunchedEffect(msgs.size, thinking) { if (msgs.isNotEmpty()) listState.animateScrollToItem(msgs.size - 1 + if (thinking) 1 else 0) }

    fun persist(m: List<LunaMsg>) {
        val h = JSONArray()
        m.takeLast(40).forEach { h.put(JSONObject().put("role", it.role).put("content", it.content)) }
        Api.lunaHistorySave(h)
    }

    Column(Modifier.fillMaxSize()) {
        Row(Modifier.fillMaxWidth().padding(horizontal = 16.dp, vertical = 4.dp), verticalAlignment = Alignment.CenterVertically) {
            Box(Modifier.size(10.dp).clip(CircleShape).background(Color(0xFF4ADE80)))
            Spacer(Modifier.width(8.dp))
            Text("Luna - XARENA assistant", color = Color.White, fontWeight = FontWeight.Bold, fontSize = 15.sp)
        }
        LazyColumn(state = listState, modifier = Modifier.weight(1f).padding(horizontal = 12.dp)) {
            if (msgs.isEmpty()) item {
                PanelCard {
                    Text("Hi! I'm Luna, the XARENA assistant.", color = Gold2, fontWeight = FontWeight.Bold)
                    Text("Ask me about tournaments, registration, points, or anything about the platform.", color = Muted, fontSize = 13.sp)
                }
            }
            items(msgs) { m ->
                val mine = m.role == "user"
                Column(Modifier.fillMaxWidth().padding(vertical = 3.dp), horizontalAlignment = if (mine) Alignment.End else Alignment.Start) {
                    Column(Modifier.widthIn(max = 290.dp).clip(RoundedCornerShape(14.dp))
                        .background(if (mine) Color(0xFF3A2A6B) else Color(0xFF2A1150))
                        .border(1.dp, if (mine) Gold.copy(alpha = .4f) else Color(0xFFBF6AFF).copy(alpha = .35f), RoundedCornerShape(14.dp))
                        .padding(horizontal = 12.dp, vertical = 8.dp)) {
                        if (!mine) Text("Luna", color = Color(0xFFDCB8FF), fontSize = 11.sp, fontWeight = FontWeight.Bold)
                        Text(m.content, color = Color.White, fontSize = 14.sp)
                    }
                }
            }
            if (thinking) item { Text("Luna is thinking...", color = Muted, fontSize = 13.sp, modifier = Modifier.padding(8.dp)) }
        }
        Row(Modifier.fillMaxWidth().background(Color(0xFF1A0E33)).padding(8.dp), verticalAlignment = Alignment.CenterVertically) {
            OutlinedTextField(input, { input = it }, placeholder = { Text("Ask Luna anything...") }, modifier = Modifier.weight(1f), maxLines = 3)
            Spacer(Modifier.width(8.dp))
            Box(Modifier.size(48.dp).clip(CircleShape).background(if (input.isBlank() || thinking) Muted else Gold)
                .clickable(enabled = input.isNotBlank() && !thinking) {
                    val text = input.trim()
                    if (text.isEmpty()) return@clickable
                    input = ""
                    msgs = msgs + LunaMsg("user", text)
                    persist(msgs)
                    thinking = true
                    scope.launch {
                        try {
                            val hist = JSONArray()
                            msgs.takeLast(12).forEach { hist.put(JSONObject().put("role", it.role).put("content", it.content)) }
                            val reply = Api.lunaSend(text, hist)
                            msgs = msgs + LunaMsg("assistant", reply)
                        } catch (e: Exception) {
                            msgs = msgs + LunaMsg("assistant", e.message ?: "Oops, something went wrong on my side. Please try again in a bit!")
                        }
                        persist(msgs)
                        thinking = false
                    }
                }, contentAlignment = Alignment.Center) {
                Text(">", color = Color(0xFF2A1400), fontWeight = FontWeight.Black, fontSize = 18.sp)
            }
        }
    }
}
