package app.xarena.nativeapp

import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Text
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import kotlinx.coroutines.delay
import kotlinx.coroutines.launch
import org.json.JSONArray
import org.json.JSONObject

/* v80: per-match live chat panel for the Live Match screen.
   Room "match-<id>" in the shared chat_messages table (public read, RPC send with p_room).
   Honest rules: panel shows a plain note when the match is not live, when signed out, and when the
   room is empty. No fake messages, no fake counts. Test seams are internal, tests-only. */

internal object MatchChatCache { var msgs: JSONArray? = null }
internal var matchChatSignedInOverride: Boolean? = null
internal var matchChatHandleOverride: String? = null
internal var matchChatLiveOverride: Boolean? = null

private fun matchColorFor(h: String): Color {
    val palette = listOf(Color(0xFFF5C451), Color(0xFF4ADE80), Color(0xFF60A5FA), Color(0xFFF472B6), Color(0xFFC084FC), Color(0xFFFBBF24))
    return palette[(h.hashCode() and Int.MAX_VALUE) % palette.size]
}

@Composable
fun MatchChatPanel(matchId: String, live: Boolean) {
    val isLive = matchChatLiveOverride ?: live
    val signedIn = matchChatSignedInOverride ?: !Api.uid.isNullOrBlank()
    Column(Modifier.fillMaxWidth().padding(top = 10.dp)) {
        Text("LIVE CHAT", color = Gold2, fontSize = 12.sp, fontWeight = FontWeight.Bold, modifier = Modifier.padding(horizontal = 8.dp))
        Spacer(Modifier.height(4.dp))
        if (!isLive) {
            Text("Live chat opens when the match goes live.", color = Muted, fontSize = 12.sp, modifier = Modifier.padding(horizontal = 8.dp, vertical = 6.dp))
            return@Column
        }
        if (!signedIn) {
            Text("Log in to join the live chat.", color = Muted, fontSize = 12.sp, modifier = Modifier.padding(horizontal = 8.dp, vertical = 6.dp))
            return@Column
        }
        MatchChatRoom(matchId)
    }
}

@Composable
private fun MatchChatRoom(matchId: String) {
    val room = "match-" + matchId
    val scope = rememberCoroutineScope()
    var msgs by remember { mutableStateOf(MatchChatCache.msgs) }
    var lastId by remember { mutableLongStateOf(0L) }
    var draft by remember { mutableStateOf("") }
    var sending by remember { mutableStateOf(false) }
    var note by remember { mutableStateOf<String?>(null) }
    var handle by remember { mutableStateOf(matchChatHandleOverride ?: Api.chatHandle) }
    var handleDraft by remember { mutableStateOf("") }
    var claimBusy by remember { mutableStateOf(false) }

    LaunchedEffect(matchId) {
        val hist = runCatching { Api.chatHistory(room) }.getOrNull() ?: return@LaunchedEffect
        MatchChatCache.msgs = hist; msgs = hist
        for (i in 0 until hist.length()) lastId = maxOf(lastId, hist.getJSONObject(i).optLong("id"))
        while (true) {
            delay(10000)
            val more = runCatching { Api.chatSince(lastId, room) }.getOrNull() ?: continue
            if (more.length() > 0) {
                val merged = JSONArray()
                val cur = msgs ?: JSONArray()
                for (i in 0 until cur.length()) merged.put(cur.getJSONObject(i))
                for (i in 0 until more.length()) { val m = more.getJSONObject(i); merged.put(m); lastId = maxOf(lastId, m.optLong("id")) }
                MatchChatCache.msgs = merged; msgs = merged
            }
        }
    }

    val arr = msgs
    Column(Modifier.fillMaxWidth().clip(RoundedCornerShape(Look.cardRadius)).background(Look.cardBg)
        .border(1.dp, Look.cardBorder, RoundedCornerShape(Look.cardRadius)).padding(10.dp)) {
        if (arr == null) Text("Loading chat...", color = Muted, fontSize = 12.sp)
        else {
            if (arr.length() == 0) Text("No messages yet - be the first.", color = Muted, fontSize = 12.sp, modifier = Modifier.padding(vertical = 4.dp))
            Column(Modifier.fillMaxWidth().heightIn(max = 200.dp).verticalScroll(rememberScrollState())) {
                for (i in arr.length() - 1 downTo 0) {
                    val m = arr.getJSONObject(i)
                    Row(Modifier.fillMaxWidth().padding(vertical = 2.dp)) {
                        Text(m.s("handle").ifBlank { "player" } + ": ", color = matchColorFor(m.s("handle")), fontSize = 12.5.sp, fontWeight = FontWeight.Bold)
                        Text(m.s("body"), color = Color.White, fontSize = 12.5.sp, modifier = Modifier.weight(1f))
                        Text(dmTime(m.s("created_at")), color = Muted, fontSize = 10.sp)
                    }
                }
            }
        }
        note?.let { Text(it, color = Gold2, fontSize = 11.sp, modifier = Modifier.padding(top = 4.dp)) }
        Spacer(Modifier.height(6.dp))
        if (handle.isNullOrBlank()) {
            Text("Pick a chat name to join:", color = Muted, fontSize = 12.sp)
            Row(Modifier.fillMaxWidth(), verticalAlignment = Alignment.CenterVertically) {
                OutlinedTextField(handleDraft, { handleDraft = it }, placeholder = { Text("3-20 letters, numbers, _") }, singleLine = true, modifier = Modifier.weight(1f))
                Spacer(Modifier.width(8.dp))
                Text(if (claimBusy) "..." else "JOIN", color = Gold, fontSize = 13.sp, fontWeight = FontWeight.Bold,
                    modifier = Modifier.clickable(enabled = !claimBusy && Regex("^[A-Za-z0-9_]{3,20}$").matches(handleDraft)) {
                        claimBusy = true; note = null
                        scope.launch {
                            val r = runCatching { Api.chatClaim(handleDraft.trim()) }.getOrNull()
                            claimBusy = false
                            if (r?.optBoolean("ok") == true) { handle = matchChatHandleOverride ?: Api.chatHandle } else note = r?.s("error") ?: "Could not claim that name - try another."
                        }
                    }.padding(6.dp))
            }
        } else {
            Row(Modifier.fillMaxWidth(), verticalAlignment = Alignment.CenterVertically) {
                OutlinedTextField(draft, { draft = it.take(200) }, placeholder = { Text("Message as " + handle + "...") }, singleLine = true, modifier = Modifier.weight(1f))
                Spacer(Modifier.width(8.dp))
                Text(if (sending) "..." else "SEND", color = Gold, fontSize = 13.sp, fontWeight = FontWeight.Bold,
                    modifier = Modifier.clickable(enabled = draft.isNotBlank() && !sending) {
                        val body = draft.trim(); draft = ""; sending = true; note = null
                        scope.launch {
                            val r = runCatching { Api.chatSendRoom(room, body) }.getOrNull()
                            sending = false
                            if (r?.optBoolean("ok") == true) {
                                note = null
                                val more = runCatching { Api.chatSince(lastId, room) }.getOrNull()
                                if (more != null && more.length() > 0) {
                                    val merged = JSONArray()
                                    val cur = msgs ?: JSONArray()
                                    for (i in 0 until cur.length()) merged.put(cur.getJSONObject(i))
                                    for (i in 0 until more.length()) { val m = more.getJSONObject(i); merged.put(m); lastId = maxOf(lastId, m.optLong("id")) }
                                    MatchChatCache.msgs = merged; msgs = merged
                                }
                            } else {
                                draft = body
                                note = r?.s("error") ?: "Could not send - check before retrying, it may have posted."
                            }
                        }
                    }.padding(6.dp))
            }
        }
    }
}
