package app.xarena.nativeapp

import androidx.compose.foundation.background
import androidx.compose.foundation.border
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
import android.graphics.BitmapFactory
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.text.BasicTextField
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.text.TextStyle
import coil.compose.AsyncImage
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import org.json.JSONArray
import org.json.JSONObject

private data class SquadData(
    val squads: JSONArray,
    val mySquadId: String?,
    val myRole: String?,
    val invites: JSONArray,
    val rankLine: String?,
    val wars: JSONArray,
    val creator: Boolean,
    val creatorPending: Boolean
)

private suspend fun loadSquads(): SquadData {
    val squads = Api.squadsPublic()
    var myId: String? = null; var myRole: String? = null
    try {
        val uid = Api.uid
        if (uid != null) {
            val m = Api.authedGetArray("/rest/v1/squad_members?user_id=eq.$uid&select=squad_id,role")
            if (m.length() > 0) { myId = m.getJSONObject(0).s("squad_id").ifBlank { null }; myRole = m.getJSONObject(0).s("role") }
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
    var creator = false; var pending = false
    try {
        val st = Api.authedRpc("squad_creator_status", JSONObject())
        if (st.optBoolean("ok")) { creator = st.optBoolean("creator"); pending = st.optBoolean("pending") }
    } catch (e: Exception) { }
    return SquadData(squads, myId, myRole, invites, rankLine, wars, creator, pending)
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
                    SquadWarsBlock(d.mySquadId, d.myRole) { notice = it; nonce++ }
                }
                item {
                    PanelCard {
                        if (d.mySquadId != null) {
                            val mine = (0 until d.squads.length()).map { d.squads.getJSONObject(it) }.firstOrNull { it.s("id") == d.mySquadId }
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
                if (d.mySquadId == null) item {
                    when {
                        d.creator -> CreateSquadCard { notice = it; nonce++ }
                        d.creatorPending -> PanelCard {
                            Text("Creator access requested", color = Gold2, fontSize = 16.sp, fontWeight = FontWeight.Bold)
                            Text("The owner reviews every request - you will be able to create your squad here once approved.", color = Muted, fontSize = 13.sp)
                        }
                        else -> RequestCreatorCard { notice = it; nonce++ }
                    }
                }
                if (d.mySquadId != null && d.myRole == "captain") item {
                    CaptainManageCard(d.mySquadId!!) { notice = it; nonce++ }
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
                            Row(verticalAlignment = Alignment.CenterVertically, modifier = Modifier.weight(1f)) {
                                if (s.s("logo_url").isNotBlank()) {
                                    AsyncImage(model = s.s("logo_url"), contentDescription = null, contentScale = ContentScale.Crop,
                                        modifier = Modifier.size(26.dp).clip(CircleShape))
                                    Spacer(Modifier.width(8.dp))
                                }
                                Text(s.s("name") + (s.s("tag").takeIf { it.isNotBlank() }?.let { " [$it]" } ?: ""),
                                    color = Gold2, fontSize = 17.sp, fontWeight = FontWeight.Bold)
                            }
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
                        if (d.mySquadId == null && s.s("id").isNotBlank() && s.optInt("member_count", 0) < 6) {
                            Spacer(Modifier.height(8.dp))
                            GoldButton("JOIN THIS SQUAD") {
                                scope.launch {
                                    try {
                                        val r = Api.authedRpc("join_squad", JSONObject().put("p_squad", s.s("id")))
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

@Composable
private fun SquadField(value: String, onChange: (String) -> Unit, hint: String, modifier: Modifier = Modifier) {
    BasicTextField(value, onChange, modifier = modifier.fillMaxWidth().clip(RoundedCornerShape(10.dp)).background(Ink).padding(11.dp),
        textStyle = TextStyle(color = Color.White, fontSize = 14.sp), singleLine = true,
        decorationBox = { inner -> if (value.isEmpty()) Text(hint, color = Muted, fontSize = 14.sp); inner() })
}

@Composable
private fun CreateSquadCard(onDone: (String) -> Unit) {
    val scope = rememberCoroutineScope()
    var name by remember { mutableStateOf("") }
    var tag by remember { mutableStateOf("") }
    var bio by remember { mutableStateOf("") }
    var busy by remember { mutableStateOf(false) }
    PanelCard {
        Text("Create your squad", color = Gold2, fontSize = 16.sp, fontWeight = FontWeight.Bold)
        Text("You are an approved creator. You become the captain - one squad per player, 6 players max.", color = Muted, fontSize = 12.sp)
        Spacer(Modifier.height(8.dp))
        SquadField(name, { name = it }, "Squad name")
        Spacer(Modifier.height(6.dp))
        SquadField(tag, { tag = it.uppercase().take(5) }, "Tag (up to 5 letters)")
        Spacer(Modifier.height(6.dp))
        SquadField(bio, { bio = it }, "Bio (optional)")
        Spacer(Modifier.height(10.dp))
        GoldButton(if (busy) "CREATING..." else "CREATE SQUAD") {
            if (busy) return@GoldButton
            if (name.trim().length < 3) { onDone("Give the squad a name (3+ characters)."); return@GoldButton }
            busy = true
            scope.launch {
                try {
                    val r = Api.authedRpc("create_squad", JSONObject().put("p_name", name.trim()).put("p_tag", tag.trim()).put("p_bio", bio.trim()))
                    onDone(if (r.optBoolean("ok")) "Squad created - you are the captain." else r.s("error").ifBlank { "Could not create the squad." })
                } catch (e: Exception) { onDone(e.message ?: "Could not create the squad.") ; busy = false }
            }
        }
    }
}

@Composable
private fun RequestCreatorCard(onDone: (String) -> Unit) {
    val scope = rememberCoroutineScope()
    var note by remember { mutableStateOf("") }
    var busy by remember { mutableStateOf(false) }
    PanelCard {
        Text("Want to create a squad?", color = Gold2, fontSize = 16.sp, fontWeight = FontWeight.Bold)
        Text("Only approved creators can start squads (this keeps squads real). Request access and the owner will review it.", color = Muted, fontSize = 12.sp)
        Spacer(Modifier.height(8.dp))
        SquadField(note, { note = it }, "Why should you host a squad? (optional)")
        Spacer(Modifier.height(10.dp))
        GoldButton(if (busy) "SENDING..." else "REQUEST CREATOR ACCESS") {
            if (busy) return@GoldButton
            busy = true
            scope.launch {
                try {
                    val r = Api.authedRpc("request_squad_creator", JSONObject().put("p_note", note.trim()))
                    onDone(if (r.optBoolean("ok")) "Request sent - the owner will review it." else r.s("error").ifBlank { "Could not send the request." })
                } catch (e: Exception) { onDone(e.message ?: "Could not send the request."); busy = false }
            }
        }
    }
}

@Composable
private fun CaptainManageCard(squadId: String, onDone: (String) -> Unit) {
    val scope = rememberCoroutineScope()
    var invite by remember { mutableStateOf("") }
    var showShop by remember { mutableStateOf(false) }
    if (showShop) {
        LogoStoreSheet(squadId, onDone = { msg -> if (msg != null) onDone(msg) }, onClose = { showShop = false })
    }
    PanelCard {
        Text("Captain tools", color = Gold, fontWeight = FontWeight.Bold, fontSize = 15.sp)
        Spacer(Modifier.height(8.dp))
        Row(verticalAlignment = Alignment.CenterVertically) {
            SquadField(invite, { invite = it }, "Invite player by name", Modifier.weight(1f))
            Spacer(Modifier.width(8.dp))
            Text("Invite", color = Ink, fontSize = 13.sp, fontWeight = FontWeight.Bold,
                modifier = Modifier.clip(RoundedCornerShape(8.dp)).background(Gold).clickable {
                    val nm = invite.trim()
                    if (nm.isEmpty()) return@clickable
                    scope.launch {
                        try {
                            val r = Api.authedRpc("squad_invite", JSONObject().put("p_name", nm))
                            onDone(if (r.optBoolean("ok")) "Invite sent to " + nm + "." else r.s("error").ifBlank { "Could not send the invite." })
                            invite = ""
                        } catch (e: Exception) { onDone(e.message ?: "Could not send the invite.") }
                    }
                }.padding(horizontal = 14.dp, vertical = 10.dp))
        }
        Spacer(Modifier.height(8.dp))
        Text("Team logo (store)", color = Gold, fontSize = 13.sp, fontWeight = FontWeight.Bold,
            modifier = Modifier.clickable { showShop = true }.padding(vertical = 6.dp))
        Spacer(Modifier.height(4.dp))
        Row {
            Text("Leave squad", color = Muted, fontSize = 13.sp,
                modifier = Modifier.clickable {
                    scope.launch {
                        try {
                            val r = Api.authedRpc("leave_squad", JSONObject())
                            onDone(if (r.optBoolean("ok")) "You left the squad." else r.s("error").ifBlank { "Could not leave." })
                        } catch (e: Exception) { onDone(e.message ?: "Could not leave.") }
                    }
                }.padding(vertical = 6.dp))
            Spacer(Modifier.width(18.dp))
            Text("Disband squad", color = ErrRed, fontSize = 13.sp,
                modifier = Modifier.clickable {
                    scope.launch {
                        try {
                            val r = Api.authedRpc("disband_squad", JSONObject().put("p_squad", squadId))
                            onDone(if (r.optBoolean("ok")) "Squad disbanded." else r.s("error").ifBlank { "Could not disband." })
                        } catch (e: Exception) { onDone(e.message ?: "Could not disband.") }
                    }
                }.padding(vertical = 6.dp))
        }
    }
}

private val TIER_COLORS = mapOf("common" to Color(0xFF9AA4B2), "rare" to Color(0xFF38BDF8), "epic" to Color(0xFFA78BFA), "legendary" to Color(0xFFFFD700))

private data class LogoShopData(val catalog: JSONArray, val owned: Set<String>, val balance: Int?)

private suspend fun loadLogoShop(): LogoShopData {
    val catalog = Api.publicGet("/rest/v1/squad_logos?active=eq.true&select=id,title,image_url,tier,cost_ap,sort&order=sort.asc")
    val owned = HashSet<String>()
    var balance: Int? = null
    try {
        val r = Api.authedRpc("my_squad_logos", JSONObject())
        val arr = r.optJSONArray("logos") ?: JSONArray()
        for (i in 0 until arr.length()) owned.add(arr.getJSONObject(i).s("id"))
    } catch (e: Exception) { }
    try {
        val b = Api.authedRpc("points_balance", JSONObject())
        if (b.optBoolean("ok")) balance = b.optInt("balance")
    } catch (e: Exception) { }
    return LogoShopData(catalog, owned, balance)
}

@Composable
private fun LogoStoreSheet(squadId: String, onDone: (String?) -> Unit, onClose: () -> Unit) {
    val scope = rememberCoroutineScope()
    var tick by remember { mutableIntStateOf(0) }
    var busy by remember { mutableStateOf(false) }
    var msg by remember { mutableStateOf<String?>(null) }
    androidx.compose.ui.window.Dialog(onDismissRequest = { if (!busy) onClose() },
        properties = androidx.compose.ui.window.DialogProperties(usePlatformDefaultWidth = false)) {
        Column(Modifier.fillMaxSize().background(Ink).padding(horizontal = 12.dp, vertical = 10.dp)) {
            Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.SpaceBetween, verticalAlignment = Alignment.CenterVertically) {
                Text("Squad Logo Store", color = Gold2, fontSize = 17.sp, fontWeight = FontWeight.Bold)
                Text("Close", color = Muted, fontSize = 14.sp, modifier = Modifier.clickable { if (!busy) onClose() }.padding(10.dp))
            }
            LoadState2(tick, { loadLogoShop() }) { d ->
                if (d == null || d.catalog.length() == 0) {
                    Text("The logo store is being stocked - check back soon.", color = Muted, fontSize = 14.sp)
                } else {
                    Text("Your AP: " + (d.balance?.toString() ?: "-") + "  •  Buy once, keep forever. AP only, never cash.",
                        color = Muted, fontSize = 12.sp)
                    msg?.let { Text(it, color = ErrRed, fontSize = 12.sp) }
                    Spacer(Modifier.height(8.dp))
                    androidx.compose.foundation.lazy.grid.LazyVerticalGrid(
                        columns = androidx.compose.foundation.lazy.grid.GridCells.Fixed(3),
                        modifier = Modifier.fillMaxSize(),
                        horizontalArrangement = Arrangement.spacedBy(8.dp),
                        verticalArrangement = Arrangement.spacedBy(8.dp)
                    ) {
                        items(d.catalog.length()) { i ->
                            val l = d.catalog.getJSONObject(i)
                            val id = l.s("id")
                            val tier = l.s("tier")
                            val owned = d.owned.contains(id)
                            val afford = d.balance != null && d.balance >= l.optInt("cost_ap")
                            val tcol = TIER_COLORS[tier] ?: Muted
                            Column(Modifier.clip(RoundedCornerShape(10.dp)).background(Card)
                                .border(1.5.dp, tcol, RoundedCornerShape(10.dp))
                                .clickable(enabled = !busy) {
                                    if (busy) return@clickable
                                    if (!owned && d.balance == null) { msg = "Log in to buy logos."; return@clickable }
                                    if (!owned && !afford) { msg = "Not enough AP for " + l.s("title") + " (" + l.optInt("cost_ap") + " AP)."; return@clickable }
                                    busy = true; msg = null
                                    scope.launch {
                                        try {
                                            if (!owned) {
                                                val r = Api.authedRpc("buy_squad_logo", JSONObject().put("p_logo", id))
                                                if (!r.optBoolean("ok")) { msg = r.s("error").ifBlank { "Could not buy this logo." }; busy = false; return@launch }
                                            }
                                            val e = Api.authedRpc("equip_squad_logo", JSONObject().put("p_squad", squadId).put("p_logo", id))
                                            if (e.optBoolean("ok")) { onDone("Team logo set: " + l.s("title")); onClose() }
                                            else { msg = e.s("error").ifBlank { "Could not set the logo." } }
                                        } catch (ex: Exception) { msg = ex.message }
                                        busy = false
                                    }
                                }.padding(8.dp),
                                horizontalAlignment = Alignment.CenterHorizontally) {
                                AsyncImage(model = l.s("image_url"), contentDescription = l.s("title"), contentScale = ContentScale.Fit,
                                    modifier = Modifier.size(64.dp))
                                Spacer(Modifier.height(4.dp))
                                Text(l.s("title"), color = Color.White, fontSize = 11.sp, maxLines = 1)
                                Text(if (owned) "OWNED - tap to use" else l.optInt("cost_ap").toString() + " AP",
                                    color = if (owned) Color(0xFF8FF0B8) else (if (afford) Gold else Muted), fontSize = 10.sp, fontWeight = FontWeight.Bold)
                                Text(tier.uppercase(), color = tcol, fontSize = 9.sp)
                            }
                        }
                    }
                }
            }
        }
    }
}


/** Squad Wars + district-wise squad leaderboard (squad_board / squad_war_* RPCs). */
@Composable
private fun SquadWarsBlock(mySquadId: String?, myRole: String?, onEvent: (String?) -> Unit) {
    val scope = rememberCoroutineScope()
    var scopeIdx by remember { mutableIntStateOf(0) }
    var tick by remember { mutableIntStateOf(0) }
    var board by remember { mutableStateOf<JSONArray?>(null) }
    var wars by remember { mutableStateOf<JSONArray?>(null) }
    var warMsg by remember { mutableStateOf<String?>(null) }
    LaunchedEffect(scopeIdx, tick) {
        board = runCatching {
            withContext(Dispatchers.IO) { Api.publicRpc("squad_board", JSONObject().put("p_scope", listOf("global", "state", "district")[scopeIdx])).optJSONArray("rows") }
        }.getOrNull()
        wars = runCatching {
            withContext(Dispatchers.IO) { Api.publicRpc("wars_board", JSONObject()).optJSONArray("wars") }
        }.getOrNull()
    }
    PanelCard {
        Text("SQUAD WARS", color = Gold2, fontSize = 15.sp, fontWeight = FontWeight.Bold)
        Text("Squad vs squad - the district board decides who rules.", color = Muted, fontSize = 12.sp)
        Spacer(Modifier.height(8.dp))
        Row(horizontalArrangement = Arrangement.spacedBy(6.dp)) {
            listOf("Global", "State", "District").forEachIndexed { i, l ->
                val sel = scopeIdx == i
                Box(Modifier.clip(RoundedCornerShape(8.dp)).background(if (sel) Gold else Color(0xFF120A24))
                    .border(1.dp, if (sel) Gold else Look.cardBorder, RoundedCornerShape(8.dp))
                    .clickable { scopeIdx = i }.padding(horizontal = 12.dp, vertical = 6.dp)) {
                    Text(l, color = if (sel) Ink else Color.White, fontSize = 11.sp, fontWeight = FontWeight.Bold)
                }
            }
        }
        Spacer(Modifier.height(8.dp))
        val b = board
        if (b == null) Text("Loading squad board...", color = Muted, fontSize = 12.sp)
        else if (b.length() == 0) Text(if (scopeIdx == 0) "No squads ranked yet - earn squad XP to open the board." else "No squads from this area ranked yet.", color = Muted, fontSize = 12.sp)
        else (0 until b.length()).forEach { i ->
            val q = b.getJSONObject(i)
            Row(Modifier.fillMaxWidth().padding(vertical = 4.dp), verticalAlignment = Alignment.CenterVertically) {
                Text("#" + q.optInt("rank"), color = Gold, fontSize = 14.sp, fontWeight = FontWeight.Black, modifier = Modifier.width(36.dp))
                Column(Modifier.weight(1f)) {
                    Text(q.s("name") + (q.s("tag").takeIf { it.isNotBlank() }?.let { " [$it]" } ?: ""), color = Color.White, fontSize = 14.sp, fontWeight = FontWeight.Bold, maxLines = 1)
                    Text(listOf(q.optInt("members").toString() + " members", "%,d XP".format(q.optInt("xp")), listOf(q.s("state"), q.s("district")).filter { it.isNotBlank() && it != "null" }.joinToString(" · ")).filter { it.isNotBlank() }.joinToString(" · "),
                        color = Muted, fontSize = 11.sp)
                }
                if (mySquadId != null && myRole == "captain" && q.s("id") != mySquadId) {
                    Text("War", color = Color(0xFFFF5252), fontSize = 12.sp, fontWeight = FontWeight.Bold,
                        modifier = Modifier.clip(RoundedCornerShape(8.dp)).border(1.dp, Color(0xFFFF5252), RoundedCornerShape(8.dp))
                            .clickable {
                                scope.launch {
                                    warMsg = null
                                    try {
                                        val r = withContext(Dispatchers.IO) { Api.authedRpc("squad_war_challenge", JSONObject().put("p_opponent", q.s("id"))) }
                                        warMsg = if (r.optBoolean("ok")) "War challenge sent to " + q.s("name") else r.s("error").ifBlank { "Could not challenge." }
                                        tick++
                                    } catch (e: Exception) { warMsg = e.message }
                                }
                            }.padding(horizontal = 10.dp, vertical = 6.dp))
                }
            }
        }
        warMsg?.let { Text(it, color = Gold2, fontSize = 11.sp) }
        val w = wars
        if (w != null && w.length() > 0) {
            Spacer(Modifier.height(8.dp))
            Text("WARS", color = Gold2, fontSize = 11.sp, fontWeight = FontWeight.Bold)
            (0 until w.length()).forEach { i ->
                val x = w.getJSONObject(i)
                Row(Modifier.fillMaxWidth().padding(vertical = 3.dp), verticalAlignment = Alignment.CenterVertically) {
                    Column(Modifier.weight(1f)) {
                        Text(x.s("challenger") + "  vs  " + x.s("opponent"), color = Color.White, fontSize = 13.sp, fontWeight = FontWeight.Bold)
                        Text(x.s("status") + (x.s("winner").takeIf { it.isNotBlank() && it != "null" }?.let { " · winner: $it" } ?: "") +
                            (x.s("scheduled_at").takeIf { it.isNotBlank() && it != "null" }?.let { " · " + dmTime(it) } ?: ""), color = Muted, fontSize = 11.sp)
                    }
                    if (x.s("status") == "pending") {
                        Text("Accept", color = Color(0xFF4ADE80), fontSize = 11.sp, fontWeight = FontWeight.Bold,
                            modifier = Modifier.clickable {
                                scope.launch {
                                    try {
                                        val r = withContext(Dispatchers.IO) { Api.authedRpc("squad_war_respond", JSONObject().put("p_war", x.optLong("id")).put("p_accept", true)) }
                                        warMsg = if (r.optBoolean("ok")) "War accepted" else r.s("error").ifBlank { "Could not respond." }
                                        tick++
                                    } catch (e: Exception) { warMsg = e.message }
                                }
                            }.padding(6.dp))
                        Text("Decline", color = Color(0xFFFF5252), fontSize = 11.sp, fontWeight = FontWeight.Bold,
                            modifier = Modifier.clickable {
                                scope.launch {
                                    try {
                                        val r = withContext(Dispatchers.IO) { Api.authedRpc("squad_war_respond", JSONObject().put("p_war", x.optLong("id")).put("p_accept", false)) }
                                        warMsg = if (r.optBoolean("ok")) "War declined" else r.s("error").ifBlank { "Could not respond." }
                                        tick++
                                    } catch (e: Exception) { warMsg = e.message }
                                }
                            }.padding(6.dp))
                    }
                }
            }
        }
    }
}
