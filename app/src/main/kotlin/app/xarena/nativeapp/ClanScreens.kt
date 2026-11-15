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
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.unit.sp
import coil.compose.AsyncImage
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import org.json.JSONArray
import org.json.JSONObject

/* Clan members / clan profile / squad finder (v0.17.59) from the owner's mockup spec.
   Real data only: members + roles via xr_sf_squad_get, active dots via presence_of
   (last-seen <2min = "active", absence is hidden NOT offline), stats via squad_profile,
   rank via my_squad_rank (all-time), leaderboard via squad_board, finder via
   xr_sf_player_list (role/language/mic filters only - there is NO city filter).
   Hidden (no real source): clan level/XP/trophies/buffs, trust score, unread counts,
   promote/kick and tryout/invite buttons (no RPC) - no dead buttons. */

private fun JSONArray?.str(i: Int): String = if (this == null) "" else optString(i, "")

@Composable
fun ClanMembersScreen(info: JSONObject, onBack: () -> Unit, onClassic: () -> Unit) {
    var members by remember { mutableStateOf<JSONArray?>(null) }
    var activeIds by remember { mutableStateOf<Set<String>>(emptySet()) }
    var loaded by remember { mutableStateOf(false) }
    var query by remember { mutableStateOf("") }
    var sortRole by remember { mutableStateOf(true) }
    val squadId = info.s("squad_id").ifBlank { info.s("squad") }
    val ctx = LocalContext.current
    LaunchedEffect(Unit) {
        val res = withContext(Dispatchers.IO) {
            runCatching { Api.authedRpc("xr_sf_squad_get", JSONObject().put("p_squad", squadId)) }.getOrNull()
        }
        members = res?.optJSONArray("members") ?: res?.optJSONObject("squad")?.optJSONArray("members")
        if (members != null) {
            val ids = ArrayList<String>()
            for (i in 0 until members!!.length()) {
                val u = members!!.optJSONObject(i)?.optJSONObject("player")?.optString("id", "") ?: ""
                if (u.isNotBlank()) ids.add(u)
            }
            if (ids.isNotEmpty()) {
                val arr = JSONArray(); ids.forEach { arr.put(it) }
                val pres = withContext(Dispatchers.IO) {
                    runCatching { Api.authedRpc("presence_of", JSONObject().put("p_ids", arr)) }.getOrNull()
                }
                val set = HashSet<String>()
                val parr = pres?.optJSONArray("active") ?: pres?.optJSONArray("ids")
                if (parr != null) for (i in 0 until parr.length()) set.add(parr.optString(i))
                activeIds = set
            }
        }
        loaded = true
    }
    Column(Modifier.fillMaxSize().background(Ink)) {
        Row(Modifier.fillMaxWidth().padding(horizontal = 6.dp, vertical = 10.dp), verticalAlignment = Alignment.CenterVertically) {
            Text("<", color = Color.White, fontSize = 24.sp, modifier = Modifier.clickable { onBack() }.padding(horizontal = 10.dp, vertical = 2.dp))
            Box(Modifier.weight(1f), contentAlignment = Alignment.Center) { Text("Clan Members", color = Color.White, fontSize = 16.sp, fontWeight = FontWeight.Bold) }
            Text("Classic", color = Muted, fontSize = 12.sp, fontWeight = FontWeight.Bold,
                modifier = Modifier.clip(RoundedCornerShape(8.dp)).border(1.dp, Muted, RoundedCornerShape(8.dp)).clickable { onClassic() }.padding(horizontal = 10.dp, vertical = 6.dp))
        }
        Row(Modifier.fillMaxWidth().padding(horizontal = 16.dp, vertical = 4.dp).clip(RoundedCornerShape(14.dp)).background(Look.cardBg).border(1.dp, Look.cardBorder, RoundedCornerShape(14.dp)).padding(12.dp), verticalAlignment = Alignment.CenterVertically) {
            Box(Modifier.size(40.dp).clip(RoundedCornerShape(10.dp)).background(Color(0xFF1A0E33)).border(1.dp, Gold.copy(alpha = .6f), RoundedCornerShape(10.dp)), contentAlignment = Alignment.Center) {
                Text(xrInitial(info.s("name")), color = Gold, fontWeight = FontWeight.Bold)
            }
            Spacer(Modifier.width(10.dp))
            Text(info.s("name") + (info.s("tag").takeIf { it.isNotBlank() }?.let { " [$it]" } ?: ""), color = Color.White, fontSize = 15.sp, fontWeight = FontWeight.Bold, modifier = Modifier.weight(1f))
            Text(info.optInt("members").toString() + " members", color = Muted, fontSize = 11.sp)
        }
        Row(Modifier.fillMaxWidth().padding(horizontal = 16.dp, vertical = 6.dp), verticalAlignment = Alignment.CenterVertically) {
            OutlinedTextField(query, { query = it }, placeholder = { Text("Search members...") }, modifier = Modifier.weight(1f), maxLines = 1)
            Spacer(Modifier.width(8.dp))
            Text(if (sortRole) "Sort: Role" else "Sort: Name", color = Gold2, fontSize = 12.sp, fontWeight = FontWeight.Bold,
                modifier = Modifier.clip(RoundedCornerShape(8.dp)).border(1.dp, Gold.copy(alpha = .6f), RoundedCornerShape(8.dp)).clickable { sortRole = !sortRole }.padding(horizontal = 10.dp, vertical = 8.dp))
        }
        if (!loaded) Box(Modifier.fillMaxSize(), contentAlignment = Alignment.Center) { CircularProgressIndicator(color = Gold) }
        else {
            val list = ArrayList<JSONObject>()
            if (members != null) for (i in 0 until members!!.length()) members!!.optJSONObject(i)?.let { list.add(it) }
            val filtered = list.filter { m ->
                val pl = m.optJSONObject("player")
                val nm = pl?.optString("name", "")?.takeIf { it.isNotBlank() } ?: pl?.optString("username", "") ?: ""
                query.isBlank() || nm.contains(query, true)
            }.sortedWith(compareBy({ if (sortRole) it.optString("membership_role", "member") else (it.optJSONObject("player")?.optString("name", "") ?: "") }, { it.optJSONObject("player")?.optString("name", "") ?: "" }))
            LazyColumn(Modifier.fillMaxSize(), contentPadding = PaddingValues(horizontal = 16.dp, vertical = 6.dp)) {
                if (filtered.isEmpty()) item { Text(if (list.isEmpty()) "Member list is not available from the server yet." else "No members match your search.", color = Muted, fontSize = 13.sp, modifier = Modifier.padding(vertical = 12.dp)) }
                items(filtered) { m ->
                    val pl = m.optJSONObject("player")
                    val name = pl?.optString("name", "")?.takeIf { it.isNotBlank() } ?: pl?.optString("username", "")?.takeIf { it.isNotBlank() } ?: "Player"
                    val role = m.optString("membership_role", "member")
                    val active = activeIds.contains(m.optJSONObject("player")?.optString("id", "") ?: "")
                    val pAvatar = pl?.optString("avatar_url", "") ?: ""
                    Row(Modifier.fillMaxWidth().padding(vertical = 5.dp).clip(RoundedCornerShape(12.dp)).background(Look.cardBg).border(1.dp, Look.cardBorder, RoundedCornerShape(12.dp)).padding(10.dp), verticalAlignment = Alignment.CenterVertically) {
                        Box(Modifier.size(38.dp).clip(CircleShape).background(Color(0xFF1A0E33)).border(1.dp, if (active) Color(0xFF22C55E) else Look.cardBorder, CircleShape), contentAlignment = Alignment.Center) {
                            if (pAvatar.isNotBlank()) AsyncImage(model = xrImageModel(ctx, pAvatar), contentDescription = name, contentScale = ContentScale.Crop, modifier = Modifier.fillMaxSize().clip(CircleShape))
                            else Text(xrInitial(name), color = Gold2, fontWeight = FontWeight.Bold)
                        }
                        Spacer(Modifier.width(10.dp))
                        Column(Modifier.weight(1f)) {
                            Text(name, color = Color.White, fontSize = 14.sp, fontWeight = FontWeight.Bold)
                            Row(verticalAlignment = Alignment.CenterVertically) {
                                if (role.equals("captain", true) || role.equals("leader", true)) Text("Leader", color = Gold, fontSize = 11.sp, fontWeight = FontWeight.Bold)
                                else if (role.contains("co", true) && role.contains("leader", true)) Text("Co-Leader", color = Color(0xFF60A5FA), fontSize = 11.sp, fontWeight = FontWeight.Bold)
                                else Text("Member", color = Muted, fontSize = 11.sp)
                                val combat = m.optString("combat_role", "")
                                if (combat.isNotBlank()) { Spacer(Modifier.width(8.dp)); Text("- " + combat.replaceFirstChar { c -> c.uppercase() }, color = Color(0xFFF59E0B), fontSize = 11.sp) }
                                if (active) { Spacer(Modifier.width(8.dp)); Text("- active now", color = Color(0xFF22C55E), fontSize = 11.sp) }
                            }
                        }
                    }
                }
            }
        }
    }
}

@Composable
fun ClanProfileScreen(info: JSONObject, squadId: String?, onBack: () -> Unit, onClassic: () -> Unit) {
    val ctx = LocalContext.current
    var profile by remember { mutableStateOf<JSONObject?>(null) }
    var rank by remember { mutableStateOf<JSONObject?>(null) }
    var board by remember { mutableStateOf<JSONArray?>(null) }
    var loaded by remember { mutableStateOf(false) }
    LaunchedEffect(Unit) {
        if (squadId != null) {
            profile = withContext(Dispatchers.IO) { runCatching { Api.authedRpc("squad_profile", JSONObject().put("p_squad", squadId)) }.getOrNull() }
            board = withContext(Dispatchers.IO) { runCatching { Api.authedRpc("squad_board", JSONObject().put("p_scope", "global")) }.getOrNull() }?.let { it.optJSONArray("rows") ?: it.optJSONArray("board") }
        }
        rank = withContext(Dispatchers.IO) { runCatching { Api.authedRpc("my_squad_rank", JSONObject()) }.getOrNull() }
        loaded = true
    }
    val rec = profile?.optJSONObject("record") ?: profile
    val wins = rec?.optInt("matches", -1) ?: -1
    val kills = rec?.optInt("kills", -1) ?: -1
    Column(Modifier.fillMaxSize().background(Ink)) {
        Row(Modifier.fillMaxWidth().padding(horizontal = 6.dp, vertical = 10.dp), verticalAlignment = Alignment.CenterVertically) {
            Text("<", color = Color.White, fontSize = 24.sp, modifier = Modifier.clickable { onBack() }.padding(horizontal = 10.dp, vertical = 2.dp))
            Box(Modifier.weight(1f), contentAlignment = Alignment.Center) { Text("Clan Profile", color = Color.White, fontSize = 16.sp, fontWeight = FontWeight.Bold) }
            Text("Classic", color = Muted, fontSize = 12.sp, fontWeight = FontWeight.Bold,
                modifier = Modifier.clip(RoundedCornerShape(8.dp)).border(1.dp, Muted, RoundedCornerShape(8.dp)).clickable { onClassic() }.padding(horizontal = 10.dp, vertical = 6.dp))
        }
        LazyColumn(Modifier.fillMaxSize(), contentPadding = PaddingValues(horizontal = 16.dp, vertical = 6.dp)) {
            item {
                Column(Modifier.fillMaxWidth().clip(RoundedCornerShape(16.dp)).background(Look.cardBg).border(1.dp, Look.cardBorder, RoundedCornerShape(16.dp)).padding(16.dp), horizontalAlignment = Alignment.CenterHorizontally) {
                    val sqLogo = profile?.optString("logo_url", "") ?: ""
                    Box(Modifier.size(64.dp).clip(RoundedCornerShape(16.dp)).background(Color(0xFF1A0E33)).border(1.dp, Gold.copy(alpha = .6f), RoundedCornerShape(16.dp)), contentAlignment = Alignment.Center) {
                        if (sqLogo.isNotBlank()) AsyncImage(model = xrImageModel(ctx, sqLogo), contentDescription = info.s("name"), contentScale = ContentScale.Crop, modifier = Modifier.fillMaxSize().clip(RoundedCornerShape(16.dp)))
                        else Text(xrInitial(info.s("name")), color = Gold, fontSize = 26.sp, fontWeight = FontWeight.Bold)
                    }
                    Spacer(Modifier.height(8.dp))
                    Text(info.s("name") + (info.s("tag").takeIf { it.isNotBlank() }?.let { " [$it]" } ?: ""), color = Color.White, fontSize = 18.sp, fontWeight = FontWeight.Bold)
                    val rk = rank?.optInt("rank", 0) ?: 0
                    if (rk > 0) Text("Clan rank #" + rk + " (all-time)", color = Gold2, fontSize = 12.sp, fontWeight = FontWeight.Bold)
                    Text(info.optInt("members").toString() + " members", color = Muted, fontSize = 12.sp)
                }
            }
            if (wins >= 0 || kills >= 0) item {
                Row(Modifier.fillMaxWidth().padding(vertical = 8.dp), horizontalArrangement = Arrangement.spacedBy(10.dp)) {
                    if (wins >= 0) Column(Modifier.weight(1f).clip(RoundedCornerShape(12.dp)).background(Look.cardBg).border(1.dp, Look.cardBorder, RoundedCornerShape(12.dp)).padding(12.dp), horizontalAlignment = Alignment.CenterHorizontally) {
                        Text(wins.toString(), color = Gold, fontSize = 18.sp, fontWeight = FontWeight.Bold)
                        Text("MATCHES", color = Muted, fontSize = 10.sp)
                    }
                    if (kills >= 0) Column(Modifier.weight(1f).clip(RoundedCornerShape(12.dp)).background(Look.cardBg).border(1.dp, Look.cardBorder, RoundedCornerShape(12.dp)).padding(12.dp), horizontalAlignment = Alignment.CenterHorizontally) {
                        Text(kills.toString(), color = Gold, fontSize = 18.sp, fontWeight = FontWeight.Bold)
                        Text("KILLS", color = Muted, fontSize = 10.sp)
                    }
                    val pts = rec?.optInt("points", -1) ?: -1
                    if (pts >= 0) Column(Modifier.weight(1f).clip(RoundedCornerShape(12.dp)).background(Look.cardBg).border(1.dp, Look.cardBorder, RoundedCornerShape(12.dp)).padding(12.dp), horizontalAlignment = Alignment.CenterHorizontally) {
                        Text(pts.toString(), color = Gold, fontSize = 18.sp, fontWeight = FontWeight.Bold)
                        Text("POINTS", color = Muted, fontSize = 10.sp)
                    }
                }
            }
            if (board != null && board!!.length() > 0) {
                item { Text("Leaderboard - All Time", color = Color.White, fontSize = 14.sp, fontWeight = FontWeight.Bold, modifier = Modifier.padding(vertical = 6.dp)) }
                val rows = ArrayList<JSONObject>()
                for (i in 0 until minOf(board!!.length(), 10)) board!!.optJSONObject(i)?.let { rows.add(it) }
                items(rows.size) { i ->
                    val r = rows[i]
                    val nm = r.optString("name", r.optString("squad_name", "Squad"))
                    Row(Modifier.fillMaxWidth().padding(vertical = 4.dp).clip(RoundedCornerShape(10.dp)).background(Look.cardBg).border(1.dp, Look.cardBorder, RoundedCornerShape(10.dp)).padding(10.dp), verticalAlignment = Alignment.CenterVertically) {
                        Text(r.optInt("rank", i + 1).toString(), color = Gold, fontSize = 13.sp, fontWeight = FontWeight.Bold, modifier = Modifier.width(24.dp))
                        val bLogo = r.optString("logo", "")
                        Box(Modifier.size(28.dp).clip(CircleShape).background(Color(0xFF1A0E33)).border(1.dp, Look.cardBorder, CircleShape), contentAlignment = Alignment.Center) {
                            if (bLogo.isNotBlank()) AsyncImage(model = xrImageModel(ctx, bLogo), contentDescription = nm, contentScale = ContentScale.Crop, modifier = Modifier.fillMaxSize().clip(CircleShape))
                            else Text(xrInitial(nm), color = Gold2, fontSize = 11.sp, fontWeight = FontWeight.Bold)
                        }
                        Spacer(Modifier.width(8.dp))
                        Text(nm + (r.optString("tag", "").takeIf { it.isNotBlank() }?.let { " [$it]" } ?: ""), color = Color.White, fontSize = 13.sp, modifier = Modifier.weight(1f))
                        val sc = r.optInt("points", r.optInt("xp", -1))
                        if (sc >= 0) Text(sc.toString(), color = Gold2, fontSize = 12.sp, fontWeight = FontWeight.Bold)
                    }
                }
            }
            if (loaded) item { Spacer(Modifier.height(12.dp)) }
        }
    }
}

@Composable
fun SquadFinderScreen(onBack: () -> Unit, squadId: String?, isCaptain: Boolean) {
    val scope = androidx.compose.runtime.rememberCoroutineScope()
    val ctx = LocalContext.current
    var players by remember { mutableStateOf<JSONArray?>(null) }
    var loaded by remember { mutableStateOf(false) }
    var inviteSentFor by remember { mutableStateOf("") }
    var roleFilter by remember { mutableStateOf("All") }
    var micOnly by remember { mutableStateOf(false) }
    var langFilter by remember { mutableStateOf("") }
    suspend fun load() {
        val body = JSONObject()
        if (roleFilter != "All") body.put("p_role", roleFilter.lowercase())
        if (micOnly) body.put("p_mic", true)
        if (langFilter.isNotBlank()) body.put("p_language", langFilter)
        body.put("p_limit", 30)
        val res = withContext(Dispatchers.IO) { runCatching { Api.authedRpc("xr_sf_player_list", body) }.getOrNull() }
        players = res?.optJSONArray("players") ?: res?.optJSONArray("rows")
        loaded = true
    }
    LaunchedEffect(roleFilter, micOnly, langFilter) { load() }
    Column(Modifier.fillMaxSize().background(Ink)) {
        Row(Modifier.fillMaxWidth().padding(horizontal = 6.dp, vertical = 10.dp), verticalAlignment = Alignment.CenterVertically) {
            Text("<", color = Color.White, fontSize = 24.sp, modifier = Modifier.clickable { onBack() }.padding(horizontal = 10.dp, vertical = 2.dp))
            Box(Modifier.weight(1f), contentAlignment = Alignment.Center) { Text("Squad Finder", color = Color.White, fontSize = 16.sp, fontWeight = FontWeight.Bold) }
            Spacer(Modifier.width(40.dp))
        }
        Row(Modifier.fillMaxWidth().padding(horizontal = 16.dp, vertical = 4.dp), horizontalArrangement = Arrangement.spacedBy(8.dp)) {
            listOf("All", "IGL", "Sniper", "Rusher", "Support").forEach { r ->
                Box(Modifier.clip(RoundedCornerShape(12.dp)).border(1.dp, if (roleFilter == r) Gold else Look.cardBorder, RoundedCornerShape(12.dp))
                    .background(if (roleFilter == r) Color(0xFF1A0E33) else Look.cardBg)
                    .clickable { roleFilter = r }.padding(horizontal = 10.dp, vertical = 6.dp)) { Text(r, color = if (roleFilter == r) Gold else Color.White, fontSize = 11.sp, fontWeight = FontWeight.Bold) }
            }
            Box(Modifier.clip(RoundedCornerShape(12.dp)).border(1.dp, if (micOnly) Gold else Look.cardBorder, RoundedCornerShape(12.dp))
                .background(if (micOnly) Color(0xFF1A0E33) else Look.cardBg)
                .clickable { micOnly = !micOnly }.padding(horizontal = 10.dp, vertical = 6.dp)) { Text("Mic", color = if (micOnly) Gold else Color.White, fontSize = 11.sp, fontWeight = FontWeight.Bold) }
        }
        val count = players?.length() ?: 0
        Text(if (loaded) count.toString() + " player" + (if (count == 1) "" else "s") + " looking for squad" else "Loading...", color = Muted, fontSize = 12.sp, modifier = Modifier.padding(horizontal = 16.dp, vertical = 6.dp))
        LazyColumn(Modifier.fillMaxSize(), contentPadding = PaddingValues(horizontal = 16.dp, vertical = 6.dp)) {
            if (loaded && count == 0) item { Text("No players looking right now - check back soon.", color = Muted, fontSize = 13.sp, modifier = Modifier.padding(vertical = 12.dp)) }
            val list = ArrayList<JSONObject>()
            if (players != null) for (i in 0 until players!!.length()) players!!.optJSONObject(i)?.let { list.add(it) }
            items(list) { p ->
                val nm = p.optString("name", "").takeIf { it.isNotBlank() } ?: p.optString("username", "Player")
                val fAvatar = p.optString("avatar_url", "")
                Row(Modifier.fillMaxWidth().padding(vertical = 5.dp).clip(RoundedCornerShape(12.dp)).background(Look.cardBg).border(1.dp, Look.cardBorder, RoundedCornerShape(12.dp)).padding(10.dp), verticalAlignment = Alignment.CenterVertically) {
                    Box(Modifier.size(40.dp).clip(CircleShape).background(Color(0xFF1A0E33)).border(1.dp, Gold.copy(alpha = .5f), CircleShape), contentAlignment = Alignment.Center) {
                        if (fAvatar.isNotBlank()) AsyncImage(model = xrImageModel(ctx, fAvatar), contentDescription = nm, contentScale = ContentScale.Crop, modifier = Modifier.fillMaxSize().clip(CircleShape))
                        else Text(xrInitial(nm), color = Gold2, fontWeight = FontWeight.Bold)
                    }
                    Spacer(Modifier.width(10.dp))
                    Column(Modifier.weight(1f)) {
                        Text(nm, color = Color.White, fontSize = 14.sp, fontWeight = FontWeight.Bold)
                        val bits = ArrayList<String>()
                        p.optString("role", "").takeIf { it.isNotBlank() }?.let { bits.add(it.replaceFirstChar { c -> c.uppercase() }) }
                        p.optString("primary_language", "").takeIf { it.isNotBlank() }?.let { bits.add(it) }
                        if (p.optBoolean("mic", false)) bits.add("Has mic")
                        val lv = p.optInt("level", 0)
                        if (lv > 0) bits.add("Level " + lv)
                        if (bits.isNotEmpty()) Text(bits.joinToString(" - "), color = Muted, fontSize = 11.sp)
                    }
                    if (isCaptain && squadId != null) {
                        Text("Invite", color = Gold, fontSize = 12.sp, fontWeight = FontWeight.Bold,
                            modifier = Modifier.clip(RoundedCornerShape(8.dp)).border(1.dp, Gold.copy(alpha = .7f), RoundedCornerShape(8.dp))
                                .clickable(enabled = inviteSentFor != p.optString("id", "")) {
                                    val pid = p.optString("id", "")
                                    if (pid.isBlank()) return@clickable
                                    scope.launch(Dispatchers.IO) {
                                        val ok = runCatching { Api.authedRpc("xr_sf_invite_send", JSONObject().put("p_squad", squadId).put("p_player", pid)).optBoolean("ok") }.getOrDefault(false)
                                        if (ok) inviteSentFor = pid
                                    }
                                }.padding(horizontal = 12.dp, vertical = 8.dp))
                    }
                }
            }
        }
    }
}
