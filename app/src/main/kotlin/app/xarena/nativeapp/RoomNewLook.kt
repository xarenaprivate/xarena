package app.xarena.nativeapp

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
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import org.json.JSONObject

/* Clan Room - "new look" layout from the owner's reference mockup (v0.17.54).
   ADDITIVE ONLY: the classic RoomScreen layout stays one tap away ("Classic" chip; "New look"
   chip brings this back). Same data and same RPCs (room_info / room_feed / room_post /
   xr_room_growth) - no new backend calls. No fabricated presence: no online counts and no
   fake voice join counts - only real fields from real RPCs. */
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
    onSend: (String) -> Unit
) {
    val listState = rememberLazyListState()
    var pinDismissed by remember { mutableStateOf(false) }
    LaunchedEffect(msgs.size) { if (msgs.isNotEmpty()) listState.scrollToItem(msgs.size - 1) }
    val pin = growth?.optJSONObject("pin")
    val memberCount = info.optInt("members")
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
            Text("Classic", color = Muted, fontSize = 12.sp, fontWeight = FontWeight.Bold,
                modifier = Modifier.clip(RoundedCornerShape(8.dp)).border(1.dp, Muted, RoundedCornerShape(8.dp)).clickable { onClassic() }.padding(horizontal = 10.dp, vertical = 6.dp))
        }
        if (pin != null && !pinDismissed && pin.s("body").isNotBlank()) {
            Row(Modifier.fillMaxWidth().padding(horizontal = 12.dp, vertical = 4.dp).clip(RoundedCornerShape(12.dp)).background(Look.cardBg).border(1.dp, NeonYellow, RoundedCornerShape(12.dp)).padding(10.dp), verticalAlignment = Alignment.CenterVertically) {
                Column(Modifier.weight(1f)) {
                    Text("PINNED", color = NeonYellow, fontSize = 10.sp, fontWeight = FontWeight.Bold)
                    Text(pin.s("body"), color = Color.White, fontSize = 13.sp)
                }
                Text("x", color = Muted, fontSize = 14.sp, modifier = Modifier.clickable { pinDismissed = true }.padding(6.dp))
            }
        }
        val sq = growth?.optJSONObject("squad")
        val reqs = if (growth == null || growth.isNull("pending_join_requests")) null else growth.optInt("pending_join_requests")
        if (reqs != null && reqs > 0 && sq != null && sq.optBoolean("is_captain")) {
            Text(reqs.toString() + " join request" + (if (reqs == 1) "" else "s") + " waiting - review in Squads", color = NeonYellow, fontSize = 12.sp, fontWeight = FontWeight.Bold, modifier = Modifier.padding(horizontal = 16.dp, vertical = 2.dp).clickable { onOpenSquads() })
        }
        LazyColumn(state = listState, modifier = Modifier.weight(1f), contentPadding = PaddingValues(horizontal = 12.dp, vertical = 6.dp)) {
            if (msgs.isEmpty()) item { Text("No messages yet - write the first one and say hello to your squad.", color = Muted, fontSize = 13.sp, modifier = Modifier.padding(vertical = 12.dp)) }
            items(msgs) { m ->
                val mine = m.s("user_id") == Api.uid
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
                            Text(m.s("body"), color = Color.White, fontSize = 14.sp)
                            if (mine) Text(dmTime(m.s("created_at")), color = Muted, fontSize = 10.sp)
                        }
                    }
                }
            }
        }
        if (err != null) Text(err, color = ErrRed, fontSize = 12.sp, modifier = Modifier.padding(horizontal = 12.dp))
        Row(Modifier.fillMaxWidth().padding(horizontal = 12.dp, vertical = 4.dp).clip(RoundedCornerShape(14.dp)).background(Color(0xFF1A0E33)).border(1.dp, Gold.copy(alpha = .4f), RoundedCornerShape(14.dp)).padding(horizontal = 14.dp, vertical = 10.dp), verticalAlignment = Alignment.CenterVertically) {
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
            OutlinedTextField(input, onInput, placeholder = { Text("Message your squad...") }, modifier = Modifier.weight(1f), maxLines = 3)
            Spacer(Modifier.width(8.dp))
            Box(Modifier.size(48.dp).clip(CircleShape).background(if (input.isBlank()) Muted else Gold).clickable(enabled = input.isNotBlank()) { onSend(input.trim()) }, contentAlignment = Alignment.Center) {
                Text(">", color = Ink, fontSize = 18.sp, fontWeight = FontWeight.Bold)
            }
        }
    }
}
