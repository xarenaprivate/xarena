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

/* Squad Finder: IGL dashboard (requests, strategy plan, call backup), tryout rooms, squad chat.
   Chat stays behind its server flag: no flag row is seeded, so chat_list fails and we show the
   honest off state. mark_test is a captain-recorded marker only - never presented as verified. */

/* ---------- IGL dashboard ---------- */
@Composable
fun IglDashboardScreen(onBack: () -> Unit) {
    val scope = rememberCoroutineScope()
    var squads by remember { mutableStateOf<List<SfSquad>?>(SfSeams.mySquads) }
    var err by remember { mutableStateOf<String?>(null) }
    LaunchedEffect(Unit) {
        val r = runCatching { Api.authedRpc("xr_sf_squad_list", JSONObject().put("p_mine", true)) }.getOrNull()
        if (r != null && r.optBoolean("ok")) {
            val arr = r.optJSONArray("squads") ?: JSONArray()
            squads = (0 until arr.length()).map { sfSquadFrom(arr.getJSONObject(it)) }
        } else err = r?.s("error") ?: "Could not load your squads."
    }
    Column(Modifier.fillMaxSize().background(Ink)) {
        PageHeader("IGL Dashboard", "Captain tools for your squad", onBack)
        val list = squads
        if (err != null) Text(err!!, color = ErrRed, fontSize = 13.sp, modifier = Modifier.padding(24.dp))
        else if (list == null) Text("Loading...", color = Muted, fontSize = 13.sp, modifier = Modifier.padding(24.dp))
        else {
            val mine = list.filter { it.isCaptain }
            if (mine.isEmpty()) Text("Only a squad captain (IGL) has tools here. Create or captain a squad first.", color = Muted, fontSize = 13.sp, modifier = Modifier.padding(24.dp))
            else LazyColumn(contentPadding = PaddingValues(16.dp), verticalArrangement = Arrangement.spacedBy(10.dp)) {
                items(mine) { sq -> IglSquadPanel(sq) }
            }
        }
    }
}

@Composable
private fun IglSquadPanel(sq: SfSquad) {
    val scope = rememberCoroutineScope()
    var requests by remember { mutableStateOf<JSONArray?>(SfSeams.requests) }
    var plan by remember { mutableStateOf<JSONObject?>(SfSeams.plan) }
    var planDraft by remember { mutableStateOf(SfSeams.plan?.s("body") ?: "") }
    var msg by remember { mutableStateOf<String?>(null) }
    var roles by remember { mutableStateOf(listOf("Rusher", "Support", "Sniper", "IGL")) }
    var backupRole by remember { mutableStateOf<String?>(null) }

    fun loadRequests() {
        scope.launch {
            val r = runCatching { Api.authedRpc("xr_sf_request_list", JSONObject().put("p_squad", sq.id)) }.getOrNull()
            requests = r?.optJSONArray("requests") ?: JSONArray()
        }
    }
    fun loadPlan() {
        scope.launch {
            val r = runCatching { Api.authedRpc("xr_sf_plan_get", JSONObject().put("p_squad", sq.id)) }.getOrNull()
            plan = r?.optJSONObject("plan")
            planDraft = r?.optJSONObject("plan")?.s("body") ?: ""
        }
    }
    LaunchedEffect(sq.id) {
        val rc = runCatching { Api.authedRpc("xr_sf_role_catalog", JSONObject()) }.getOrNull()
        val ra = rc?.optJSONArray("roles")
        if (ra != null && ra.length() > 0) roles = (0 until ra.length()).map { ra.getString(it) }
        loadRequests(); loadPlan()
    }

    PanelCard {
        Text(sq.name, color = Color.White, fontSize = 16.sp, fontWeight = FontWeight.Bold)
        Text(sq.memberCount.toString() + "/" + sq.maxMembers + " members", color = Muted, fontSize = 11.sp)
    }
    PanelCard {
        Text("JOIN REQUESTS (10-min expiry)", color = Gold2, fontSize = 12.sp, fontWeight = FontWeight.Bold)
        val rq = requests
        if (rq == null) Text("Loading...", color = Muted, fontSize = 12.sp)
        else if (rq.length() == 0) Text("No requests waiting.", color = Muted, fontSize = 12.sp)
        else {
            val ids = (0 until rq.length()).map { rq.getJSONObject(it).s("id") }.filter { it.isNotBlank() }
            for (i in 0 until rq.length()) {
                val q0 = rq.getJSONObject(i)
                Text(q0.s("player_name").ifBlank { "Player" } + (if (q0.s("note").isNotBlank()) " - " + q0.s("note") else ""),
                    color = Color.White, fontSize = 12.sp, modifier = Modifier.padding(vertical = 2.dp))
            }
            Row(horizontalArrangement = Arrangement.spacedBy(8.dp), modifier = Modifier.padding(top = 6.dp)) {
                Text("ACCEPT ALL", color = Ink, fontSize = 12.sp, fontWeight = FontWeight.Bold,
                    modifier = Modifier.clip(RoundedCornerShape(8.dp)).background(Gold).clickable {
                        scope.launch {
                            val arr = JSONArray(); ids.forEach { arr.put(it) }
                            val r = runCatching { Api.authedRpc("xr_sf_request_decide", JSONObject().put("p_requests", arr).put("p_accept", true)) }.getOrNull()
                            val results = r?.optJSONObject("results") ?: r?.optJSONArray("results")
                            msg = if (r?.optBoolean("ok") == true) "Decisions sent. " + (results?.toString()?.take(120) ?: "") else r?.s("error") ?: "Could not decide."
                            loadRequests()
                        }
                    }.padding(horizontal = 12.dp, vertical = 8.dp))
                Text("DECLINE ALL", color = Color.White, fontSize = 12.sp, fontWeight = FontWeight.Bold,
                    modifier = Modifier.clip(RoundedCornerShape(8.dp)).background(Look.cardBg).clickable {
                        scope.launch {
                            val arr = JSONArray(); ids.forEach { arr.put(it) }
                            runCatching { Api.authedRpc("xr_sf_request_decide", JSONObject().put("p_requests", arr).put("p_accept", false)) }
                            loadRequests()
                        }
                    }.padding(horizontal = 12.dp, vertical = 8.dp))
            }
        }
    }
    PanelCard {
        Text("STRATEGY PLAN", color = Gold2, fontSize = 12.sp, fontWeight = FontWeight.Bold)
        Text("Plain text, members can read. Saves fail if someone else edited first.", color = Muted, fontSize = 11.sp)
        OutlinedTextField(planDraft, { if (it.length <= 4000) planDraft = it }, placeholder = { Text("Drop plans, rotations, calls...") },
            modifier = Modifier.fillMaxWidth().height(140.dp))
        Row(horizontalArrangement = Arrangement.spacedBy(8.dp), modifier = Modifier.padding(top = 6.dp)) {
            Text("SAVE", color = Ink, fontSize = 12.sp, fontWeight = FontWeight.Bold,
                modifier = Modifier.clip(RoundedCornerShape(8.dp)).background(Gold).clickable {
                    scope.launch {
                        val rev = plan?.optLong("revision") ?: 0L
                        val r = runCatching { Api.authedRpc("xr_sf_plan_save", JSONObject().put("p_squad", sq.id).put("p_body", planDraft).put("p_expected_revision", rev)) }.getOrNull()
                        msg = if (r?.optBoolean("ok") == true) "Plan saved." else r?.s("error") ?: "Could not save (stale revision?)."
                        loadPlan()
                    }
                }.padding(horizontal = 12.dp, vertical = 8.dp))
            if (plan != null) Text("DELETE", color = ErrRed, fontSize = 12.sp, fontWeight = FontWeight.Bold,
                modifier = Modifier.clip(RoundedCornerShape(8.dp)).background(Look.cardBg).clickable {
                    scope.launch {
                        val rev = plan?.optLong("revision") ?: 0L
                        runCatching { Api.authedRpc("xr_sf_plan_delete", JSONObject().put("p_squad", sq.id).put("p_expected_revision", rev)) }
                        loadPlan()
                    }
                }.padding(horizontal = 12.dp, vertical = 8.dp))
        }
    }
    PanelCard {
        Text("CALL BACKUP", color = Gold2, fontSize = 12.sp, fontWeight = FontWeight.Bold)
        Text("Invites up to 5 opted-in free agents. Once per squad every 5 minutes. No DMs - in-app inbox only.", color = Muted, fontSize = 11.sp)
        Row(horizontalArrangement = Arrangement.spacedBy(6.dp), modifier = Modifier.padding(vertical = 6.dp)) {
            roles.forEach { r0 ->
                val on = backupRole == r0
                Text(r0, color = if (on) Ink else Gold, fontSize = 11.sp, fontWeight = FontWeight.Bold,
                    modifier = Modifier.clip(RoundedCornerShape(8.dp)).background(if (on) Gold else Look.cardBg)
                        .clickable { backupRole = if (on) null else r0 }.padding(horizontal = 10.dp, vertical = 6.dp))
            }
        }
        GoldButton("SEND BACKUP INVITES", onClick = {
            scope.launch {
                val args = JSONObject().put("p_squad", sq.id)
                if (backupRole != null) args.put("p_role", backupRole)
                val r = runCatching { Api.authedRpc("xr_sf_call_backup", args) }.getOrNull()
                msg = if (r?.optBoolean("ok") == true) {
                    val sent = r.optInt("invited", r.optJSONArray("invites")?.length() ?: 0)
                    if (sent > 0) "Invited " + sent + " players." else "No eligible players right now. The 5-minute cooldown still started."
                } else r?.s("error") ?: "Could not call backup."
            }
        })
    }
    msg?.let { Text(it, color = Gold2, fontSize = 12.sp, modifier = Modifier.padding(vertical = 4.dp)) }
}

/* ---------- Tryout rooms ---------- */
@Composable
fun TryoutRoomsScreen(onBack: () -> Unit, onOpenRoom: (String) -> Unit) {
    val scope = rememberCoroutineScope()
    var rooms by remember { mutableStateOf<JSONArray?>(SfSeams.rooms) }
    var err by remember { mutableStateOf<String?>(null) }
    var creating by remember { mutableStateOf(false) }
    var mode by remember { mutableStateOf("BR") }
    var map by remember { mutableStateOf("") }
    var duration by remember { mutableStateOf("30") }
    var createMsg by remember { mutableStateOf<String?>(null) }

    fun load() {
        scope.launch {
            err = null
            val r = runCatching { Api.authedRpc("xr_sf_tryout_list", JSONObject()) }.getOrNull()
            if (r != null && r.optBoolean("ok")) rooms = r.optJSONArray("rooms") ?: JSONArray()
            else err = r?.s("error") ?: "Could not load tryout rooms."
        }
    }
    LaunchedEffect(Unit) { load() }

    Column(Modifier.fillMaxSize().background(Ink)) {
        PageHeader("Tryout Rooms", "Play a trial match before committing", onBack)
        Column(Modifier.padding(horizontal = 16.dp)) {
            Text(if (creating) "CANCEL" else "+ HOST A TRYOUT", color = Gold, fontSize = 12.sp, fontWeight = FontWeight.Bold,
                modifier = Modifier.clickable { creating = !creating; createMsg = null }.padding(6.dp))
        }
        if (creating) PanelCard {
            Text("HOST A TRYOUT", color = Gold2, fontSize = 12.sp, fontWeight = FontWeight.Bold)
            Text("4 players max including you. This does not create a Free Fire room - it only groups players.", color = Muted, fontSize = 11.sp)
            Row(horizontalArrangement = Arrangement.spacedBy(6.dp), modifier = Modifier.padding(vertical = 4.dp)) {
                listOf("BR", "CS", "Lone Wolf").forEach { m0 ->
                    val on = mode == m0
                    Text(m0, color = if (on) Ink else Gold, fontSize = 11.sp, fontWeight = FontWeight.Bold,
                        modifier = Modifier.clip(RoundedCornerShape(8.dp)).background(if (on) Gold else Look.cardBg)
                            .clickable { mode = m0 }.padding(horizontal = 10.dp, vertical = 6.dp))
                }
            }
            OutlinedTextField(map, { map = it }, placeholder = { Text("Map (e.g. Bermuda)") }, singleLine = true, modifier = Modifier.fillMaxWidth())
            OutlinedTextField(duration, { duration = it.filter { c -> c.isDigit() }.take(3) }, placeholder = { Text("Duration in minutes (5-120)") }, singleLine = true, modifier = Modifier.fillMaxWidth())
            GoldButton("CREATE ROOM", enabled = map.isNotBlank() && (duration.toIntOrNull() ?: 0) in 5..120, onClick = {
                scope.launch {
                    createMsg = null
                    val r = runCatching { Api.authedRpc("xr_sf_tryout_create", JSONObject().put("p_mode", mode).put("p_map", map.trim()).put("p_duration_min", duration.toInt())) }.getOrNull()
                    if (r?.optBoolean("ok") == true) {
                        val rid = r.optJSONObject("room")?.s("id").orEmpty()
                        creating = false; load()
                        if (rid.isNotBlank()) onOpenRoom(rid)
                    } else createMsg = r?.s("error") ?: "Could not create the room."
                }
            })
            createMsg?.let { Text(it, color = ErrRed, fontSize = 12.sp) }
        }
        err?.let { Text(it, color = ErrRed, fontSize = 12.sp, modifier = Modifier.padding(16.dp)) }
        val rr = rooms
        LazyColumn(contentPadding = PaddingValues(16.dp), verticalArrangement = Arrangement.spacedBy(10.dp)) {
            if (rr == null) item { Text("Loading...", color = Muted, fontSize = 13.sp) }
            else if (rr.length() == 0) item { Text("No open tryout rooms. Host one and it appears here.", color = Muted, fontSize = 13.sp) }
            else items((0 until rr.length()).map { rr.getJSONObject(it) }, key = { it.s("id") }) { room ->
                PanelCard {
                    Column(Modifier.fillMaxWidth().clickable { onOpenRoom(room.s("id")) }) {
                        Row(verticalAlignment = Alignment.CenterVertically) {
                            Text(room.s("mode") + " - " + room.s("map"), color = Color.White, fontSize = 15.sp, fontWeight = FontWeight.Bold, modifier = Modifier.weight(1f))
                            Text(room.optInt("member_count").toString() + "/4", color = Gold2, fontSize = 12.sp, fontWeight = FontWeight.Bold)
                        }
                        Text("Host: " + room.s("host_name").ifBlank { "Player" } + " - " + room.optInt("duration_min") + " min", color = Muted, fontSize = 11.sp)
                        if (room.s("code").isNotBlank()) Text("Room code: " + room.s("code"), color = Gold2, fontSize = 11.sp)
                    }
                }
            }
        }
    }
}

@Composable
fun TryoutRoomScreen(roomId: String, onBack: () -> Unit) {
    val scope = rememberCoroutineScope()
    var room by remember { mutableStateOf<JSONObject?>(SfSeams.roomDetail) }
    var err by remember { mutableStateOf<String?>(null) }
    var msg by remember { mutableStateOf<String?>(null) }
    var me by remember { mutableStateOf(SfSeams.roomDetail?.s("__me") ?: "") }

    fun load() {
        scope.launch {
            err = null
            if (me.isBlank()) me = Api.uid.orEmpty()
            val r = runCatching { Api.authedRpc("xr_sf_tryout_get", JSONObject().put("p_room", roomId)) }.getOrNull()
            if (r != null && r.optBoolean("ok")) room = r.optJSONObject("room") else err = r?.s("error") ?: "Room not found or closed."
        }
    }
    LaunchedEffect(roomId) { load() }

    Column(Modifier.fillMaxSize().background(Ink)) {
        PageHeader("Tryout Room", "", onBack)
        val rm = room
        if (err != null) Text(err!!, color = ErrRed, fontSize = 13.sp, modifier = Modifier.padding(24.dp))
        else if (rm == null) Text("Loading...", color = Muted, fontSize = 13.sp, modifier = Modifier.padding(24.dp))
        else {
            val isHost = rm.s("host_id") == me
            val closed = rm.optBoolean("closed", false) || rm.s("status") == "closed"
            Column(Modifier.fillMaxSize().verticalScroll(rememberScrollState()).padding(16.dp), verticalArrangement = Arrangement.spacedBy(10.dp)) {
                PanelCard {
                    Text(rm.s("mode") + " - " + rm.s("map"), color = Color.White, fontSize = 16.sp, fontWeight = FontWeight.Bold)
                    Text(if (closed) "Closed" else "Open - " + rm.optInt("duration_min") + " min", color = if (closed) Muted else Color(0xFF4ADE80), fontSize = 12.sp)
                    if (rm.s("code").isNotBlank()) Text("Room code: " + rm.s("code"), color = Gold2, fontSize = 13.sp, fontWeight = FontWeight.Bold)
                    Text("Join the match together in Free Fire - this room only groups players.", color = Muted, fontSize = 11.sp)
                }
                PanelCard {
                    Text("PLAYERS", color = Gold2, fontSize = 12.sp, fontWeight = FontWeight.Bold)
                    val ma = rm.optJSONArray("members")
                    if (ma == null || ma.length() == 0) Text("No one here yet.", color = Muted, fontSize = 12.sp)
                    else for (i in 0 until ma.length()) {
                        val m0 = ma.getJSONObject(i)
                        Text(m0.s("username").ifBlank { "Player" } + (if (m0.optBoolean("is_host")) " (host)" else ""),
                            color = Color.White, fontSize = 13.sp, modifier = Modifier.padding(vertical = 2.dp))
                    }
                }
                if (!closed) {
                    if (isHost) {
                        GoldButton("MARK AS TESTED", onClick = {
                            scope.launch {
                                val r = runCatching { Api.authedRpc("xr_sf_tryout_mark_test", JSONObject().put("p_room", roomId)) }.getOrNull()
                                msg = if (r?.optBoolean("ok") == true) "Marked. This is your own record - it does not verify the match." else r?.s("error") ?: "Could not mark."
                            }
                        })
                        GoldButton("CLOSE ROOM", onClick = {
                            scope.launch {
                                runCatching { Api.authedRpc("xr_sf_tryout_close", JSONObject().put("p_room", roomId)) }
                                onBack()
                            }
                        })
                    } else {
                        GoldButton("LEAVE ROOM", onClick = {
                            scope.launch {
                                runCatching { Api.authedRpc("xr_sf_tryout_leave", JSONObject().put("p_room", roomId)) }
                                onBack()
                            }
                        })
                    }
                }
                msg?.let { Text(it, color = Gold2, fontSize = 12.sp) }
            }
        }
    }
}

/* ---------- Squad chat (behind server flag - currently off) ---------- */
@Composable
fun SquadChatScreen(squadId: String, onBack: () -> Unit) {
    val scope = rememberCoroutineScope()
    var msgs by remember { mutableStateOf<JSONArray?>(null) }
    var off by remember { mutableStateOf(false) }
    var err by remember { mutableStateOf<String?>(null) }
    var draft by remember { mutableStateOf("") }

    fun load() {
        scope.launch {
            val r = runCatching { Api.authedRpc("xr_sf_chat_list", JSONObject().put("p_squad", squadId)) }.getOrNull()
            if (r != null && r.optBoolean("ok")) { msgs = r.optJSONArray("messages") ?: JSONArray(); off = false }
            else {
                val e = r?.s("error").orEmpty()
                if (e.contains("off") || e.contains("disabled") || e.contains("not_enabled") || r == null) off = true
                else err = e.ifBlank { "Chat unavailable." }
            }
        }
    }
    LaunchedEffect(squadId) { load() }

    Column(Modifier.fillMaxSize().background(Ink)) {
        PageHeader("Squad Chat", "", onBack)
        when {
            off -> Column(Modifier.padding(24.dp)) {
                Text("Squad chat is not live yet.", color = Color.White, fontSize = 15.sp, fontWeight = FontWeight.Bold)
                Text("This feature ships in a later release. Nothing is hidden from you - it simply is not turned on.", color = Muted, fontSize = 12.sp, modifier = Modifier.padding(top = 4.dp))
            }
            err != null -> Text(err!!, color = ErrRed, fontSize = 13.sp, modifier = Modifier.padding(24.dp))
            msgs == null -> Text("Loading...", color = Muted, fontSize = 13.sp, modifier = Modifier.padding(24.dp))
            else -> {
                LazyColumn(Modifier.weight(1f), contentPadding = PaddingValues(16.dp), verticalArrangement = Arrangement.spacedBy(6.dp)) {
                    val mm = msgs!!
                    if (mm.length() == 0) item { Text("No messages yet.", color = Muted, fontSize = 13.sp) }
                    else items((0 until mm.length()).map { mm.getJSONObject(it) }, key = { it.optLong("id") }) { m0 ->
                        Text(m0.s("username").ifBlank { "Player" } + ": " + m0.s("body"), color = Color.White, fontSize = 13.sp)
                    }
                }
                Row(Modifier.padding(12.dp), verticalAlignment = Alignment.CenterVertically) {
                    OutlinedTextField(draft, { if (it.length <= 400) draft = it }, placeholder = { Text("Message") }, singleLine = true, modifier = Modifier.weight(1f))
                    Spacer(Modifier.width(8.dp))
                    Text("SEND", color = Gold, fontSize = 13.sp, fontWeight = FontWeight.Bold, modifier = Modifier.clickable {
                        if (draft.isNotBlank()) scope.launch {
                            val r = runCatching { Api.authedRpc("xr_sf_chat_post", JSONObject().put("p_squad", squadId).put("p_body", draft.trim())) }.getOrNull()
                            if (r?.optBoolean("ok") == true) { draft = ""; load() } else err = r?.s("error") ?: "Could not send."
                        }
                    }.padding(8.dp))
                }
            }
        }
    }
}
