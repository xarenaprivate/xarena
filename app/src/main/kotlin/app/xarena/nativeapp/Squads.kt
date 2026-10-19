package app.xarena.nativeapp

import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
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

private data class SquadData(
    val squads: JSONArray,
    val mySquadId: Long?,
    val myRole: String?,
    val invites: JSONArray,
    val rankLine: String?,
    val wars: JSONArray
)

private suspend fun loadSquads(): SquadData {
    val squads = Api.squadsPublic()
    var myId: Long? = null; var myRole: String? = null
    try {
        val uid = Api.uid
        if (uid != null) {
            val m = Api.authedGetArray("/rest/v1/squad_members?user_id=eq.$uid&select=squad_id,role")
            if (m.length() > 0) { myId = m.getJSONObject(0).optLong("squad_id"); myRole = m.getJSONObject(0).s("role") }
        }
    } catch (e: Exception) { }
    val invites = try { Api.authedRpc("my_squad_invites", JSONObject()).optJSONArray("invites") ?: JSONArray() } catch (e: Exception) { JSONArray() }
    val rankLine = try {
        val r = Api.authedRpc("my_squad_rank", JSONObject())
        if (r.optBoolean("ok") && r.has("rank") && !r.isNull("rank")) {
            val row = r.optJSONObject("row")
            "Battle board: #" + r.optInt("rank") + " of " + r.optInt("total") + " squads" +
                (if (row != null) " - " + row.optInt("total") + " pts" else "")
        } else null
    } catch (e: Exception) { null }
    val wars = try { Api.authedRpc("wars_board", JSONObject()).optJSONArray("wars") ?: JSONArray() } catch (e: Exception) { JSONArray() }
    return SquadData(squads, myId, myRole, invites, rankLine, wars)
}

@Composable
fun SquadsScreen(onBack: (() -> Unit)? = null) {
    val scope = rememberCoroutineScope()
    var nonce by remember { mutableIntStateOf(0) }
    var notice by remember { mutableStateOf<String?>(null) }
    Column(Modifier.fillMaxSize()) {
        if (onBack != null) PageHeader("Squads", "Find or build your squad", onBack) else Brand()
        LoadState({ loadSquads() }) { d ->
            LazyColumn {
                item {
                    PanelCard {
                        if (d.mySquadId != null) {
                            val mine = (0 until d.squads.length()).map { d.squads.getJSONObject(it) }.firstOrNull { it.optLong("id") == d.mySquadId }
                            Text("Your squad: " + (mine?.s("name") ?: "Squad") + (mine?.s("tag")?.takeIf { it.isNotBlank() }?.let { " [$it]" } ?: ""),
                                color = Gold2, fontSize = 18.sp, fontWeight = FontWeight.Bold)
                            Text("Role: " + (d.myRole ?: "member"), color = Muted, fontSize = 13.sp)
                            d.rankLine?.let { Text(it, color = Color.White, fontSize = 13.sp) }
                        } else {
                            Text("You are not in a squad yet", color = Gold2, fontSize = 16.sp, fontWeight = FontWeight.Bold)
                            Text("Join any squad below. One squad per player.", color = Muted, fontSize = 13.sp)
                        }
                    }
                }
                if (d.invites.length() > 0) item {
                    PanelCard {
                        Text("Squad invites", color = Gold, fontWeight = FontWeight.Bold)
                        (0 until d.invites.length()).forEach { i ->
                            val inv = d.invites.getJSONObject(i)
                            Row(Modifier.fillMaxWidth().padding(vertical = 4.dp), verticalAlignment = Alignment.CenterVertically) {
                                Text(inv.s("squad") + " (by " + inv.s("by", "the captain") + ")",
                                    color = Color.White, fontSize = 13.sp, modifier = Modifier.weight(1f))
                                Text("Accept", color = Color(0xFF2A1400), fontSize = 12.sp, fontWeight = FontWeight.Bold,
                                    modifier = Modifier.clip(RoundedCornerShape(6.dp)).background(Gold)
                                        .clickable {
                                            scope.launch {
                                                try {
                                                    val r = Api.authedRpc("squad_invite_respond", JSONObject().put("p_id", inv.optLong("id")).put("p_accept", true))
                                                    notice = if (r.optBoolean("ok")) "Invite accepted." else r.s("error").ifBlank { "Could not answer the invite." }
                                                } catch (e: Exception) { notice = e.message }
                                                nonce++
                                            }
                                        }.padding(horizontal = 10.dp, vertical = 6.dp))
                                Spacer(Modifier.width(6.dp))
                                Text("Decline", color = Muted, fontSize = 12.sp,
                                    modifier = Modifier.clip(RoundedCornerShape(6.dp)).background(Color(0x22FFFFFF))
                                        .clickable {
                                            scope.launch {
                                                try { Api.authedRpc("squad_invite_respond", JSONObject().put("p_id", inv.optLong("id")).put("p_accept", false)) } catch (e: Exception) { }
                                                nonce++
                                            }
                                        }.padding(horizontal = 10.dp, vertical = 6.dp))
                            }
                        }
                    }
                }
                item { Text("All squads", color = Gold, fontWeight = FontWeight.Bold, fontSize = 16.sp, modifier = Modifier.padding(horizontal = 16.dp, vertical = 6.dp)) }
                if (d.squads.length() == 0) item { EmptyNote("No squads yet", "The first approved creator squad will appear here. Join requests open as soon as squads exist.") }
                items((0 until d.squads.length()).map { d.squads.getJSONObject(it) }) { s ->
                    PanelCard {
                        Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.SpaceBetween, verticalAlignment = Alignment.CenterVertically) {
                            Text(s.s("name") + (s.s("tag").takeIf { it.isNotBlank() }?.let { " [$it]" } ?: ""),
                                color = Gold2, fontSize = 17.sp, fontWeight = FontWeight.Bold)
                            Text(if (s.has("member_count")) s.optInt("member_count").toString() + "/6" else "", color = Muted, fontSize = 12.sp)
                        }
                        if (s.s("bio").isNotBlank()) Text(s.s("bio"), color = Muted, fontSize = 13.sp)
                        val members = s.optJSONArray("members")
                        if (members != null && members.length() > 0) {
                            Text((0 until members.length()).joinToString("  ") { i ->
                                val m = members.getJSONObject(i)
                                (m.s("ign").ifBlank { m.s("username") }) + (if (m.s("role") == "captain") " (captain)" else "")
                            }, color = Color.White, fontSize = 12.sp)
                        }
                        if (d.mySquadId == null && s.has("id") && !s.isNull("id") && s.optInt("member_count", 0) < 6) {
                            Spacer(Modifier.height(8.dp))
                            GoldButton("JOIN THIS SQUAD") {
                                scope.launch {
                                    try {
                                        val r = Api.authedRpc("join_squad", JSONObject().put("p_squad", s.optLong("id")))
                                        notice = if (r.optBoolean("ok")) "Joined " + s.s("name") + "!" else r.s("error").ifBlank { "Could not join right now." }
                                    } catch (e: Exception) { notice = e.message ?: "Could not join right now." }
                                    nonce++
                                }
                            }
                        }
                    }
                }
                if (d.wars.length() > 0) {
                    item { Text("Squad wars", color = Gold, fontWeight = FontWeight.Bold, fontSize = 16.sp, modifier = Modifier.padding(horizontal = 16.dp, vertical = 6.dp)) }
                    items((0 until d.wars.length()).map { d.wars.getJSONObject(it) }) { w ->
                        PanelCard {
                            Text(w.s("challenger") + " vs " + w.s("opponent"), color = Color.White, fontWeight = FontWeight.Bold)
                            Text(w.s("status").uppercase() + (if (w.s("winner").isNotBlank()) " - Winner: " + w.s("winner") else ""),
                                color = Gold, fontSize = 12.sp)
                        }
                    }
                }
                item { Spacer(Modifier.height(16.dp)) }
            }
        }
        notice?.let {
            Text(it, color = Gold2, fontSize = 13.sp, modifier = Modifier.padding(horizontal = 16.dp, vertical = 4.dp))
        }
    }
}
