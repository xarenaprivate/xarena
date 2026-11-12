package app.xarena.nativeapp

import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Switch
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

/* Squad Finder: squad discovery, squad profile, invites (30s expiry), join requests, create. */

internal data class SfSquad(
    val id: String, val name: String, val logo: String, val intro: String,
    val visibility: String, val mode: String, val map: String, val region: String,
    val commsVoice: Boolean, val rolesNeeded: List<String>,
    val memberCount: Int, val maxMembers: Int,
    val isMember: Boolean, val isCaptain: Boolean
)

internal fun sfSquadFrom(j: JSONObject): SfSquad {
    val rn = mutableListOf<String>()
    val ra = j.optJSONArray("roles_needed")
    if (ra != null) for (i in 0 until ra.length()) rn.add(ra.optString(i))
    return SfSquad(
        id = j.s("id"), name = j.s("name").ifBlank { "Squad" }, logo = j.s("logo"), intro = j.s("intro_line"),
        visibility = j.s("visibility"), mode = j.s("mode"), map = j.s("map"), region = j.s("region"),
        commsVoice = j.optBoolean("comms_voice", false), rolesNeeded = rn,
        memberCount = j.optInt("member_count", 0), maxMembers = j.optInt("max_members", 4),
        isMember = j.optBoolean("is_member", false), isCaptain = j.optBoolean("is_captain", false)
    )
}

@Composable
internal fun SfSquadCard(sq: SfSquad, onOpen: () -> Unit) {
    PanelCard {
        Column(Modifier.fillMaxWidth().clickable { onOpen() }) {
            Row(verticalAlignment = Alignment.CenterVertically) {
                Text(sq.name, color = Color.White, fontSize = 16.sp, fontWeight = FontWeight.Bold, modifier = Modifier.weight(1f))
                Text(sq.memberCount.toString() + "/" + sq.maxMembers, color = Gold2, fontSize = 12.sp, fontWeight = FontWeight.Bold)
            }
            if (sq.intro.isNotBlank()) Text(sq.intro, color = Muted, fontSize = 12.sp)
            val bits = mutableListOf<String>()
            if (sq.mode.isNotBlank()) bits.add(sq.mode)
            if (sq.map.isNotBlank()) bits.add(sq.map)
            if (sq.region.isNotBlank()) bits.add(sq.region)
            if (sq.commsVoice) bits.add("Voice")
            bits.add(if (sq.visibility == "public") "Public - join now" else "Private - by request")
            Text(bits.joinToString(" - "), color = Muted, fontSize = 11.sp)
            if (sq.rolesNeeded.isNotEmpty()) Text("Needs: " + sq.rolesNeeded.joinToString(", "), color = Color(0xFF60A5FA), fontSize = 11.sp, fontWeight = FontWeight.Bold)
        }
    }
}

/* ---------- Squad discovery + create ---------- */
@Composable
fun SquadDiscoveryScreen(onBack: () -> Unit, onOpenSquad: (String) -> Unit) {
    val scope = rememberCoroutineScope()
    var role by remember { mutableStateOf("") }
    var visibility by remember { mutableStateOf("") }
    var mineOnly by remember { mutableStateOf(false) }
    var squads by remember { mutableStateOf<List<SfSquad>?>(null) }
    var err by remember { mutableStateOf<String?>(null) }
    var creating by remember { mutableStateOf(false) }
    var newName by remember { mutableStateOf("") }
    var newVis by remember { mutableStateOf("public") }
    var createMsg by remember { mutableStateOf<String?>(null) }

    fun load() {
        scope.launch {
            err = null
            val args = JSONObject()
            if (role.isNotBlank()) args.put("p_role", role)
            if (visibility.isNotBlank()) args.put("p_visibility", visibility)
            if (mineOnly) args.put("p_mine", true)
            val r = runCatching { Api.authedRpc("xr_sf_squad_list", args) }.getOrNull()
            if (r != null && r.optBoolean("ok")) {
                val arr = r.optJSONArray("squads") ?: JSONArray()
                squads = (0 until arr.length()).map { sfSquadFrom(arr.getJSONObject(it)) }
            } else err = r?.s("error") ?: "Could not load squads - pull back and retry."
        }
    }
    LaunchedEffect(Unit) { load() }

    Column(Modifier.fillMaxSize().background(Ink)) {
        PageHeader("Find a Squad", "One roster per account - no dual memberships", onBack)
        Column(Modifier.padding(horizontal = 16.dp)) {
            Row(verticalAlignment = Alignment.CenterVertically) {
                listOf("" to "All", "public" to "Public", "private" to "Private").forEach { (v, label) ->
                    val on = visibility == v
                    Text(label, color = if (on) Ink else Gold, fontSize = 11.sp, fontWeight = FontWeight.Bold,
                        modifier = Modifier.clip(RoundedCornerShape(8.dp)).background(if (on) Gold else Look.cardBg)
                            .clickable { visibility = v; load() }.padding(horizontal = 10.dp, vertical = 6.dp))
                }
                Spacer(Modifier.width(10.dp))
                Text("My squads", color = Muted, fontSize = 12.sp); Spacer(Modifier.width(4.dp))
                Switch(mineOnly, { mineOnly = it; load() })
            }
            OutlinedTextField(role, { role = it }, placeholder = { Text("Filter by role needed (e.g. Rusher)") }, singleLine = true,
                modifier = Modifier.fillMaxWidth().padding(vertical = 6.dp))
            Row(verticalAlignment = Alignment.CenterVertically) {
                Text("APPLY", color = Gold, fontSize = 12.sp, fontWeight = FontWeight.Bold, modifier = Modifier.clickable { load() }.padding(6.dp))
                Spacer(Modifier.width(16.dp))
                Text(if (creating) "CANCEL" else "+ CREATE SQUAD", color = Gold, fontSize = 12.sp, fontWeight = FontWeight.Bold,
                    modifier = Modifier.clickable { creating = !creating; createMsg = null }.padding(6.dp))
            }
        }
        if (creating) PanelCard {
            Text("CREATE SQUAD", color = Gold2, fontSize = 12.sp, fontWeight = FontWeight.Bold)
            Text("You must be an approved squad creator. You become captain (IGL).", color = Muted, fontSize = 11.sp)
            OutlinedTextField(newName, { newName = it }, placeholder = { Text("Squad name") }, singleLine = true, modifier = Modifier.fillMaxWidth())
            Row(horizontalArrangement = Arrangement.spacedBy(6.dp), modifier = Modifier.padding(vertical = 4.dp)) {
                listOf("public", "private").forEach { v ->
                    val on = newVis == v
                    Text(v.uppercase(), color = if (on) Ink else Gold, fontSize = 11.sp, fontWeight = FontWeight.Bold,
                        modifier = Modifier.clip(RoundedCornerShape(8.dp)).background(if (on) Gold else Look.cardBg)
                            .clickable { newVis = v }.padding(horizontal = 10.dp, vertical = 6.dp))
                }
            }
            GoldButton("CREATE", enabled = newName.isNotBlank(), onClick = {
                scope.launch {
                    createMsg = null
                    val r = runCatching { Api.authedRpc("xr_sf_squad_create", JSONObject().put("p_name", newName.trim()).put("p_visibility", newVis)) }.getOrNull()
                    if (r?.optBoolean("ok") == true) {
                        val sid = r.optJSONObject("squad")?.s("id").orEmpty()
                        creating = false; load()
                        if (sid.isNotBlank()) onOpenSquad(sid)
                    } else createMsg = when (r?.s("error")) {
                        "not_creator" -> "Your account is not an approved squad creator yet."
                        "already_in_squad" -> "You are already in a squad - leave it first."
                        else -> r?.s("error") ?: "Could not create the squad."
                    }
                }
            })
            createMsg?.let { Text(it, color = ErrRed, fontSize = 12.sp) }
        }
        err?.let { Text(it, color = ErrRed, fontSize = 12.sp, modifier = Modifier.padding(16.dp)) }
        val list = squads
        LazyColumn(contentPadding = PaddingValues(16.dp), verticalArrangement = Arrangement.spacedBy(10.dp)) {
            if (list == null) item { Text("Loading...", color = Muted, fontSize = 13.sp) }
            else if (list.isEmpty()) item { Text("No squads match yet. New squads appear here as they are created.", color = Muted, fontSize = 13.sp) }
            else items(list) { sq -> SfSquadCard(sq) { onOpenSquad(sq.id) } }
        }
    }
}

/* ---------- Squad profile ---------- */
@Composable
fun SquadProfileSfScreen(squadId: String, onBack: () -> Unit, onOpenChat: (String) -> Unit) {
    val scope = rememberCoroutineScope()
    var data by remember { mutableStateOf<JSONObject?>(null) }
    var err by remember { mutableStateOf<String?>(null) }
    var msg by remember { mutableStateOf<String?>(null) }
    var note by remember { mutableStateOf("") }
    var roles by remember { mutableStateOf(listOf("Rusher", "Support", "Sniper", "IGL")) }

    fun load() {
        scope.launch {
            err = null
            val r = runCatching { Api.authedRpc("xr_sf_squad_get", JSONObject().put("p_squad", squadId)) }.getOrNull()
            if (r != null && r.optBoolean("ok")) data = r else err = r?.s("error") ?: "Could not load this squad."
        }
    }
    LaunchedEffect(squadId) {
        val rc = runCatching { Api.authedRpc("xr_sf_role_catalog", JSONObject()) }.getOrNull()
        val ra = rc?.optJSONArray("roles")
        if (ra != null && ra.length() > 0) roles = (0 until ra.length()).map { ra.getString(it) }
        load()
    }

    Column(Modifier.fillMaxSize().background(Ink)) {
        PageHeader("Squad", "", onBack)
        val d = data
        if (err != null) Text(err!!, color = ErrRed, fontSize = 13.sp, modifier = Modifier.padding(24.dp))
        else if (d == null) Text("Loading...", color = Muted, fontSize = 13.sp, modifier = Modifier.padding(24.dp))
        else {
            val sj = d.optJSONObject("squad")
            if (sj == null) { Text("This squad is private.", color = Muted, fontSize = 13.sp, modifier = Modifier.padding(24.dp)); return@Column }
            val sq = sfSquadFrom(sj)
            Column(Modifier.fillMaxSize().verticalScroll(rememberScrollState()).padding(16.dp), verticalArrangement = Arrangement.spacedBy(10.dp)) {
                SfSquadCard(sq) {}
                if (sq.isMember) {
                    PanelCard {
                        Text("MEMBERS", color = Gold2, fontSize = 12.sp, fontWeight = FontWeight.Bold)
                        val ra = d.optJSONArray("roster")
                        if (ra == null || ra.length() == 0) Text("No members listed yet.", color = Muted, fontSize = 12.sp)
                        else for (i in 0 until ra.length()) {
                            val m0 = ra.getJSONObject(i)
                            Row(verticalAlignment = Alignment.CenterVertically, modifier = Modifier.padding(vertical = 3.dp)) {
                                Text(m0.s("username").ifBlank { "Player" }, color = Color.White, fontSize = 13.sp, modifier = Modifier.weight(1f))
                                Text(m0.s("combat_role").ifBlank { "Role not set" }, color = Muted, fontSize = 11.sp)
                                Spacer(Modifier.width(8.dp))
                                Text(if (m0.optBoolean("ready")) "READY" else "NOT READY",
                                    color = if (m0.optBoolean("ready")) Color(0xFF4ADE80) else Muted, fontSize = 10.sp, fontWeight = FontWeight.Bold)
                            }
                        }
                        val code = sj.s("squad_code")
                        if (code.isNotBlank()) { Spacer(Modifier.height(4.dp)); Text("Squad code: " + code, color = Gold2, fontSize = 12.sp, fontWeight = FontWeight.Bold) }
                    }
                    PanelCard {
                        Text("MY MEMBERSHIP", color = Gold2, fontSize = 12.sp, fontWeight = FontWeight.Bold)
                        Row(horizontalArrangement = Arrangement.spacedBy(6.dp), modifier = Modifier.padding(top = 6.dp)) {
                            roles.forEach { r0 ->
                                Text(r0, color = Ink, fontSize = 11.sp, fontWeight = FontWeight.Bold,
                                    modifier = Modifier.clip(RoundedCornerShape(8.dp)).background(Gold).clickable {
                                        scope.launch {
                                            val r = runCatching { Api.authedRpc("xr_sf_member_ready", JSONObject().put("p_squad", squadId).put("p_ready", true).put("p_combat_role", r0)) }.getOrNull()
                                            msg = if (r?.optBoolean("ok") == true) "Set: READY as " + r0 else r?.s("error") ?: "Could not update."
                                            load()
                                        }
                                    }.padding(horizontal = 10.dp, vertical = 6.dp))
                            }
                            Text("NOT READY", color = Color.White, fontSize = 11.sp, fontWeight = FontWeight.Bold,
                                modifier = Modifier.clip(RoundedCornerShape(8.dp)).background(Look.cardBg).clickable {
                                    scope.launch {
                                        runCatching { Api.authedRpc("xr_sf_member_ready", JSONObject().put("p_squad", squadId).put("p_ready", false)) }
                                        msg = "Set: not ready"; load()
                                    }
                                }.padding(horizontal = 10.dp, vertical = 6.dp))
                        }
                        Text("Tap a role to mark yourself READY in that role.", color = Muted, fontSize = 10.sp)
                    }
                    SmallCard { Text("SQUAD CHAT", color = Gold, fontSize = 13.sp, fontWeight = FontWeight.Bold, modifier = Modifier.clickable { onOpenChat(squadId) }.padding(vertical = 4.dp)) }
                } else {
                    if (sq.visibility == "public") {
                        GoldButton("JOIN THIS SQUAD", onClick = {
                            scope.launch {
                                val r = runCatching { Api.authedRpc("xr_sf_join_public", JSONObject().put("p_squad", squadId)) }.getOrNull()
                                msg = if (r?.optBoolean("ok") == true) "Joined." else when (r?.s("error")) {
                                    "already_in_squad" -> "You are already in a squad - leave it first."
                                    "squad_full" -> "This squad is full."
                                    else -> r?.s("error") ?: "Could not join."
                                }
                                load()
                            }
                        })
                    } else {
                        PanelCard {
                            Text("REQUEST TO JOIN", color = Gold2, fontSize = 12.sp, fontWeight = FontWeight.Bold)
                            Text("Private squad. The captain has 10 minutes to decide.", color = Muted, fontSize = 11.sp)
                            OutlinedTextField(note, { note = it }, placeholder = { Text("Note to the captain (optional)") }, singleLine = true, modifier = Modifier.fillMaxWidth())
                            GoldButton("SEND REQUEST", onClick = {
                                scope.launch {
                                    val r = runCatching { Api.authedRpc("xr_sf_join_request", JSONObject().put("p_squad", squadId).put("p_note", note.trim())) }.getOrNull()
                                    msg = if (r?.optBoolean("ok") == true) "Request sent." else r?.s("error") ?: "Could not send request."
                                }
                            })
                        }
                    }
                }
                msg?.let { Text(it, color = if (it == "Joined." || it == "Request sent." || it.startsWith("Set:")) Color(0xFF4ADE80) else ErrRed, fontSize = 12.sp) }
                PanelCard {
                    Text("MY PENDING REQUESTS", color = Gold2, fontSize = 12.sp, fontWeight = FontWeight.Bold)
                    var reqs by remember { mutableStateOf<JSONArray?>(null) }
                    LaunchedEffect(Unit) {
                        val r = runCatching { Api.authedRpc("xr_sf_request_list", JSONObject()) }.getOrNull()
                        reqs = r?.optJSONArray("requests") ?: JSONArray()
                    }
                    val rq = reqs
                    if (rq == null) Text("Loading...", color = Muted, fontSize = 12.sp)
                    else if (rq.length() == 0) Text("No open requests.", color = Muted, fontSize = 12.sp)
                    else for (i in 0 until rq.length()) {
                        val q0 = rq.getJSONObject(i)
                        Row(verticalAlignment = Alignment.CenterVertically, modifier = Modifier.padding(vertical = 3.dp)) {
                            Text(q0.s("squad_name").ifBlank { "Squad" }, color = Color.White, fontSize = 12.sp, modifier = Modifier.weight(1f))
                            Text(q0.s("status").uppercase(), color = Gold2, fontSize = 10.sp, fontWeight = FontWeight.Bold)
                        }
                    }
                }
            }
        }
    }
}

/* ---------- Invites inbox (30s expiry) ---------- */
@Composable
fun InvitesInboxScreen(onBack: () -> Unit) {
    val scope = rememberCoroutineScope()
    var invites by remember { mutableStateOf<JSONArray?>(null) }
    var err by remember { mutableStateOf<String?>(null) }
    var tick by remember { mutableStateOf(0) }
    var actedMsg by remember { mutableStateOf<String?>(null) }

    fun load() {
        scope.launch {
            err = null
            val r = runCatching { Api.authedRpc("xr_sf_invite_list", JSONObject()) }.getOrNull()
            if (r != null && r.optBoolean("ok")) invites = r.optJSONArray("invites") ?: JSONArray()
            else err = r?.s("error") ?: "Could not load invites."
        }
    }
    LaunchedEffect(Unit) { load() }
    LaunchedEffect(Unit) { while (true) { delay(1000); tick++ } }

    Column(Modifier.fillMaxSize().background(Ink)) {
        PageHeader("My Invites", "Invites expire 30 seconds after they are sent", onBack)
        err?.let { Text(it, color = ErrRed, fontSize = 13.sp, modifier = Modifier.padding(24.dp)) }
        actedMsg?.let { Text(it, color = Gold2, fontSize = 12.sp, modifier = Modifier.padding(horizontal = 24.dp)) }
        val inv = invites
        LazyColumn(contentPadding = PaddingValues(16.dp), verticalArrangement = Arrangement.spacedBy(10.dp)) {
            if (inv == null) item { Text("Loading...", color = Muted, fontSize = 13.sp) }
            else if (inv.length() == 0) item { Text("No invites right now. Set yourself as a free agent or opt in to backup calls to get noticed.", color = Muted, fontSize = 13.sp) }
            else items((0 until inv.length()).map { inv.getJSONObject(it) }, key = { it.s("id") }) { iv ->
                val expires = parseIso(iv.s("expires_at"))
                val left = ((expires - System.currentTimeMillis()) / 1000).coerceAtLeast(0)
                val expired = left <= 0 || iv.s("status") != "pending"
                PanelCard {
                    Row(verticalAlignment = Alignment.CenterVertically) {
                        Column(Modifier.weight(1f)) {
                            Text(iv.s("squad_name").ifBlank { "Squad" }, color = Color.White, fontSize = 15.sp, fontWeight = FontWeight.Bold)
                            Text("From " + iv.s("from_name").ifBlank { "captain" }, color = Muted, fontSize = 11.sp)
                        }
                        if (expired) Text(if (iv.s("status") == "pending") "EXPIRED" else iv.s("status").uppercase(), color = Muted, fontSize = 11.sp, fontWeight = FontWeight.Bold)
                        else Text(left.toString() + "s", color = if (left <= 10) ErrRed else Gold, fontSize = 16.sp, fontWeight = FontWeight.Bold)
                    }
                    if (!expired) Row(horizontalArrangement = Arrangement.spacedBy(8.dp), modifier = Modifier.padding(top = 8.dp)) {
                        Text("ACCEPT", color = Ink, fontSize = 12.sp, fontWeight = FontWeight.Bold,
                            modifier = Modifier.clip(RoundedCornerShape(8.dp)).background(Gold).clickable {
                                scope.launch {
                                    val r = runCatching { Api.authedRpc("xr_sf_invite_respond", JSONObject().put("p_invite", iv.optLong("id")).put("p_accept", true)) }.getOrNull()
                                    actedMsg = if (r?.optBoolean("ok") == true) "Invite accepted - you joined the squad." else r?.s("error") ?: "Could not accept."
                                    load()
                                }
                            }.padding(horizontal = 14.dp, vertical = 8.dp))
                        Text("DECLINE", color = Color.White, fontSize = 12.sp, fontWeight = FontWeight.Bold,
                            modifier = Modifier.clip(RoundedCornerShape(8.dp)).background(Look.cardBg).clickable {
                                scope.launch {
                                    runCatching { Api.authedRpc("xr_sf_invite_respond", JSONObject().put("p_invite", iv.optLong("id")).put("p_accept", false)) }
                                    load()
                                }
                            }.padding(horizontal = 14.dp, vertical = 8.dp))
                    }
                }
            }
        }
    }
}

internal fun parseIso(s: String): Long = runCatching {
    java.time.OffsetDateTime.parse(s).toInstant().toEpochMilli()
}.getOrDefault(0L)
