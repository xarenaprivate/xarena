package app.xarena.nativeapp

import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.shape.RoundedCornerShape
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
import kotlinx.coroutines.launch
import org.json.JSONArray
import org.json.JSONObject

/* Team finder (LFG) - web contract: lfg_list/lfg_create/lfg_join/lfg_leave + lfg_set_role (host only).
   Lobbies close after 12h server-side. */
private val LFG_ROLES = listOf("Any", "IGL", "Rusher", "Sniper", "Support", "Grenadier")

@Composable
fun TeamFinderScreen(onBack: () -> Unit) {
    val scope = rememberCoroutineScope()
    var tick by remember { mutableIntStateOf(0) }
    var msg by remember { mutableStateOf<String?>(null) }
    var roleFilter by remember { mutableStateOf("Any") }
    var showCreate by remember { mutableStateOf(false) }
    Column(Modifier.fillMaxSize().background(Ink)) {
        PageHeader("Team Finder", "Find a squad or host a lobby", onBack)
        LoadState2(tick, { Api.authedRpc("lfg_list", JSONObject()) }) { r ->
            if (!r.optBoolean("ok", true)) { Text(r.s("error").ifBlank { "Could not load lobbies." }, color = ErrRed, modifier = Modifier.padding(24.dp)); return@LoadState2 }
            val lobbies = r.optJSONArray("lobbies") ?: JSONArray()
            val all = (0 until lobbies.length()).map { lobbies.getJSONObject(it) }
            val shown = if (roleFilter == "Any") all else all.filter { it.s("role_wanted").equals(roleFilter, true) || it.s("role_wanted").isBlank() }
            LazyColumn(Modifier.fillMaxSize()) {
                item {
                    Row(Modifier.fillMaxWidth().padding(horizontal = 16.dp, vertical = 6.dp), verticalAlignment = Alignment.CenterVertically) {
                        Text("Role: ", color = Muted, fontSize = 12.sp)
                        LFG_ROLES.forEach { role ->
                            Text(role, color = if (roleFilter == role) Ink else Gold, fontSize = 12.sp, fontWeight = FontWeight.Bold,
                                modifier = Modifier.padding(end = 6.dp).clip(RoundedCornerShape(8.dp))
                                    .background(if (roleFilter == role) Gold else Color(0x22FFFFFF))
                                    .clickable { roleFilter = role }.padding(horizontal = 10.dp, vertical = 5.dp))
                        }
                    }
                    Box(Modifier.padding(horizontal = 16.dp)) { GoldButton(if (showCreate) "HIDE HOST FORM" else "HOST A LOBBY") { showCreate = !showCreate } }
                    if (showCreate) LfgCreateForm(onDone = { m -> msg = m; showCreate = false; tick++ })
                    msg?.let { Text(it, color = Gold2, fontSize = 12.sp, modifier = Modifier.padding(horizontal = 16.dp, vertical = 4.dp)) }
                }
                if (shown.isEmpty()) item { PanelCard { Text("No open lobbies right now - host one and players will join.", color = Muted, fontSize = 13.sp) } }
                items(shown) { l ->
                    val lid = l.optLong("id", -1)
                    PanelCard {
                        Text(l.s("mode").ifBlank { "Lobby" } + if (l.s("map").isNotBlank()) " - " + l.s("map") else "", color = Color.White, fontSize = 15.sp, fontWeight = FontWeight.Bold)
                        Text(listOf("host " + l.s("host").ifBlank { "?" },
                            if (l.s("role_wanted").isNotBlank()) "wants " + l.s("role_wanted") else "any role",
                            if (l.optInt("slots", 0) > 0) l.optInt("slots").toString() + " slots" else "",
                            if (l.optBoolean("mic")) "mic" else "").filter { it.isNotBlank() }.joinToString(" · "), color = Muted, fontSize = 11.sp)
                        if (l.s("note").isNotBlank()) Text(l.s("note"), color = Muted, fontSize = 12.sp)
                        val members = l.optJSONArray("members")
                        if (members != null && members.length() > 0)
                            Text("In: " + (0 until members.length()).joinToString(", ") { members.getJSONObject(it).s("name") }, color = Gold2, fontSize = 11.sp, modifier = Modifier.padding(top = 4.dp))
                        Spacer(Modifier.height(8.dp))
                        Row {
                            if (l.optBoolean("host_mine")) {
                                var ri by remember { mutableStateOf(0) }
                                Text("Role wanted: " + LFG_ROLES[ri], color = Gold, fontSize = 12.sp, fontWeight = FontWeight.Bold,
                                    modifier = Modifier.clip(RoundedCornerShape(8.dp)).border(1.dp, Gold, RoundedCornerShape(8.dp))
                                        .clickable {
                                            ri = (ri + 1) % LFG_ROLES.size
                                            scope.launch { runCatching { Api.authedRpc("lfg_set_role", JSONObject().put("p_lobby", lid).put("p_role", LFG_ROLES[ri])) }; tick++ }
                                        }.padding(horizontal = 12.dp, vertical = 7.dp))
                            } else if (l.optBoolean("mine")) {
                                Text("LEAVE", color = ErrRed, fontSize = 12.sp, fontWeight = FontWeight.Bold,
                                    modifier = Modifier.clip(RoundedCornerShape(8.dp)).border(1.dp, ErrRed, RoundedCornerShape(8.dp))
                                        .clickable { scope.launch { runCatching { Api.authedRpc("lfg_leave", JSONObject().put("p_lobby", lid)) }; tick++ } }
                                        .padding(horizontal = 12.dp, vertical = 7.dp))
                            } else {
                                GoldButton("JOIN") { scope.launch {
                                    val jr = runCatching { Api.authedRpc("lfg_join", JSONObject().put("p_lobby", lid)) }.getOrNull()
                                    if (jr?.optBoolean("ok") == false) msg = jr.s("error")
                                    tick++
                                } }
                            }
                        }
                    }
                }
            }
        }
    }
}

@Composable
private fun LfgCreateForm(onDone: (String?) -> Unit) {
    val scope = rememberCoroutineScope()
    var mode by remember { mutableStateOf("") }
    var map by remember { mutableStateOf("") }
    var note by remember { mutableStateOf("") }
    var slots by remember { mutableStateOf("4") }
    var mic by remember { mutableStateOf(false) }
    PanelCard {
        Text("HOST A LOBBY", color = Gold2, fontSize = 13.sp, fontWeight = FontWeight.Bold)
        OutlinedTextField(mode, { mode = it.take(24) }, placeholder = { Text("Mode (BR, CS, TDM...)") }, singleLine = true, modifier = Modifier.fillMaxWidth())
        OutlinedTextField(map, { map = it.take(24) }, placeholder = { Text("Map (optional)") }, singleLine = true, modifier = Modifier.fillMaxWidth())
        OutlinedTextField(note, { note = it.take(120) }, placeholder = { Text("Note (rank, timing...)") }, singleLine = true, modifier = Modifier.fillMaxWidth())
        OutlinedTextField(slots, { slots = it.filter { c -> c.isDigit() }.take(2) }, placeholder = { Text("Slots") }, singleLine = true, modifier = Modifier.fillMaxWidth())
        Row(verticalAlignment = Alignment.CenterVertically) {
            androidx.compose.material3.Switch(checked = mic, onCheckedChange = { mic = it })
            Text("Mic required", color = Color.White, fontSize = 12.sp)
        }
        Spacer(Modifier.height(8.dp))
        GoldButton("CREATE LOBBY", enabled = mode.isNotBlank()) {
            scope.launch {
                val r = runCatching {
                    Api.authedRpc("lfg_create", JSONObject().put("p_mode", mode.trim()).put("p_map", map.trim())
                        .put("p_rank_min", 0).put("p_rank_max", 0).put("p_mic", mic).put("p_note", note.trim())
                        .put("p_slots", slots.toIntOrNull() ?: 4))
                }.getOrNull()
                onDone(if (r?.optBoolean("ok") == true) null else r?.s("error") ?: "Could not create lobby.")
            }
        }
    }
}
