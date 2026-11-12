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
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.input.KeyboardType
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import kotlinx.coroutines.delay
import kotlinx.coroutines.isActive
import kotlinx.coroutines.launch
import coil.compose.AsyncImage
import org.json.JSONArray
import org.json.JSONObject

/* ============ Clan Room (room_info / room_feed / room_post) ============ */
@Composable
fun RoomScreen(onBack: () -> Unit) {
    val scope = rememberCoroutineScope()
    var info by remember { mutableStateOf<JSONObject?>(null) }
    var loaded by remember { mutableStateOf(false) }
    val msgs = remember { mutableStateListOf<JSONObject>() }
    var lastId by remember { mutableLongStateOf(0L) }
    var input by remember { mutableStateOf("") }
    var err by remember { mutableStateOf<String?>(null) }
    var growth by remember { mutableStateOf<JSONObject?>(null) }
    var growthAt by remember { mutableLongStateOf(0L) }
    var showSquads by remember { mutableStateOf(false) }
    if (showSquads) { SquadsScreen(onBack = { showSquads = false }); return }
    val listState = rememberLazyListState()
    LaunchedEffect(Unit) {
        info = runCatching { Api.authedRpc("room_info", JSONObject()) }.getOrNull()
        loaded = true
        growth = runCatching { Api.authedRpc("xr_room_growth", JSONObject()) }.getOrNull()
        growthAt = System.currentTimeMillis()
        while (isActive) {
            runCatching {
                val r = Api.authedRpc("room_feed", JSONObject().put("p_after", lastId))
                val rows = r.optJSONArray("rows")
                if (r.optBoolean("ok") && rows != null && rows.length() > 0) {
                    for (i in 0 until rows.length()) {
                        val m = rows.getJSONObject(i)
                        msgs.add(m); lastId = maxOf(lastId, m.optLong("id"))
                    }
                }
            }
            if (System.currentTimeMillis() - growthAt > 300_000) {
                growth = runCatching { Api.authedRpc("xr_room_growth", JSONObject()) }.getOrNull()
                growthAt = System.currentTimeMillis()
            }
            delay(4000)
        }
    }
    LaunchedEffect(msgs.size) { if (msgs.isNotEmpty()) listState.animateScrollToItem(msgs.size - 1) }
    Column(Modifier.fillMaxSize().background(Ink)) {
        PageHeader("Clan Room", "Your squad's private room", onBack)
        if (!loaded) Box(Modifier.fillMaxSize(), contentAlignment = Alignment.Center) { CircularProgressIndicator(color = Gold) }
        else if (info == null || !info!!.optBoolean("ok")) {
            Column(Modifier.fillMaxSize().padding(24.dp), verticalArrangement = Arrangement.Center) {
                Text("No clan room yet", color = Color.White, fontSize = 18.sp, fontWeight = FontWeight.Bold)
                Text("Clan rooms unlock the moment you join a squad.", color = Muted, fontSize = 13.sp)
            }
        } else {
            Row(Modifier.fillMaxWidth().padding(horizontal = 16.dp, vertical = 6.dp), verticalAlignment = Alignment.CenterVertically) {
                Text(info!!.s("name") + (info!!.s("tag").takeIf { it.isNotBlank() }?.let { " [$it]" } ?: ""),
                    color = Gold2, fontSize = 16.sp, fontWeight = FontWeight.Bold, modifier = Modifier.weight(1f))
                Text(info!!.optInt("members").toString() + " member" + (if (info!!.optInt("members") == 1) "" else "s"), color = Muted, fontSize = 12.sp)
            }
            // growth overview - roster/capacity are real dark-safe reads; promotion blocks render only when the backend returns them
            growth?.let { g ->
                val sq = g.optJSONObject("squad")
                if (sq != null) {
                    val maxM = sq.optInt("max_members", 0)
                    val openS = sq.optInt("open_seats", 0)
                    val mems = sq.optJSONArray("members")
                    if (maxM > 0 && mems != null) {
                        Column(Modifier.fillMaxWidth().padding(horizontal = 16.dp, vertical = 4.dp)) {
                            Row(horizontalArrangement = Arrangement.spacedBy(10.dp)) {
                                for (i in 0 until mems.length()) {
                                    val mm = mems.optJSONObject(i) ?: continue
                                    Column(horizontalAlignment = Alignment.CenterHorizontally) {
                                        val av = mm.s("avatar_url")
                                        if (av.isNotBlank() && av != "null") AsyncImage(model = av, contentDescription = mm.s("name"), modifier = Modifier.size(44.dp).clip(CircleShape).border(1.dp, Gold.copy(alpha = .6f), CircleShape))
                                        else Box(Modifier.size(44.dp).clip(CircleShape).background(Look.cardBg).border(1.dp, Gold.copy(alpha = .6f), CircleShape), contentAlignment = Alignment.Center) { Text(mm.s("name").take(1).uppercase(), color = Gold, fontWeight = FontWeight.Bold) }
                                        Text(mm.s("name") + (if (mm.optBoolean("is_me")) " (you)" else ""), color = Muted, fontSize = 10.sp, maxLines = 1, modifier = Modifier.widthIn(max = 64.dp))
                                        val roleLine = listOf(if (mm.optBoolean("is_captain")) "Captain" else "", if (mm.s("combat_role").isNotBlank() && mm.s("combat_role") != "null") mm.s("combat_role") else "").filter { it.isNotBlank() }.joinToString(" / ")
                                        if (roleLine.isNotBlank()) Text(roleLine, color = Gold2, fontSize = 9.sp)
                                    }
                                }
                                repeat(openS) {
                                    Column(horizontalAlignment = Alignment.CenterHorizontally, modifier = Modifier.clickable { showSquads = true }) {
                                        Box(Modifier.size(44.dp).clip(CircleShape).border(1.dp, Muted, CircleShape), contentAlignment = Alignment.Center) { Text("+", color = Muted, fontSize = 18.sp, fontWeight = FontWeight.Bold) }
                                        Text("Invite", color = Muted, fontSize = 10.sp)
                                    }
                                }
                            }
                            if (openS > 0) Text(openS.toString() + " seat" + (if (openS == 1) "" else "s") + " open - tap a seat to invite", color = Muted, fontSize = 11.sp, modifier = Modifier.padding(top = 4.dp))
                        }
                    }
                    val reqs = if (g.isNull("pending_join_requests")) null else g.optInt("pending_join_requests")
                    if (reqs != null && reqs > 0 && sq.optBoolean("is_captain")) {
                        Text(reqs.toString() + " join request" + (if (reqs == 1) "" else "s") + " waiting - review in Squads", color = NeonYellow, fontSize = 12.sp, fontWeight = FontWeight.Bold, modifier = Modifier.padding(horizontal = 16.dp, vertical = 2.dp).clickable { showSquads = true })
                    }
                }
                val pin = g.optJSONObject("pin")
                if (pin != null) {
                    Column(Modifier.fillMaxWidth().padding(horizontal = 16.dp, vertical = 4.dp).clip(RoundedCornerShape(12.dp)).background(Look.cardBg).border(1.dp, NeonYellow, RoundedCornerShape(12.dp)).padding(12.dp)) {
                        Text("PINNED - Captain's plan", color = NeonYellow, fontSize = 11.sp, fontWeight = FontWeight.Bold)
                        Spacer(Modifier.height(4.dp))
                        Text(pin.s("body"), color = Color.White, fontSize = 13.sp)
                        Text("Updated " + pin.s("updated_at").take(10), color = Muted, fontSize = 10.sp)
                    }
                }
                val nm = g.optJSONObject("next_match")
                if (nm != null) {
                    val owner = nm.optJSONObject("registration_owner")
                    val regBy = owner?.s("name") ?: ""
                    Column(Modifier.fillMaxWidth().padding(horizontal = 16.dp, vertical = 4.dp).clip(RoundedCornerShape(12.dp)).background(Look.cardBg).border(1.dp, Look.cardBorder, RoundedCornerShape(12.dp)).padding(12.dp)) {
                        Text("NEXT MATCH", color = NeonName, fontSize = 11.sp, fontWeight = FontWeight.Bold)
                        Spacer(Modifier.height(4.dp))
                        Text(nm.s("title"), color = Color.White, fontSize = 14.sp, fontWeight = FontWeight.Bold)
                        Text("Starts " + dmTime(nm.s("starts_at")), color = Muted, fontSize = 12.sp)
                        if (regBy.isNotBlank()) Text("Registered by " + regBy + (if (nm.s("registered_squad_name").isNotBlank() && nm.s("registered_squad_name") != "null") " (" + nm.s("registered_squad_name") + ")" else "") + " - their entry, not a whole-clan one", color = Muted, fontSize = 11.sp)
                        val closes = nm.s("checkin_closes_at")
                        if (nm.optBoolean("access_enabled") && closes.isNotBlank() && closes != "null") {
                            Text(if (nm.optBoolean("registration_owner_checked_in")) regBy + " is checked in" else regBy + " has not checked in yet", color = if (nm.optBoolean("registration_owner_checked_in")) Color(0xFF4ADE80) else NeonYellow, fontSize = 12.sp)
                            Text("Check-in closes " + dmTime(closes), color = Muted, fontSize = 11.sp)
                        }
                    }
                }
                val riv = g.optJSONObject("rivalry")
                if (riv != null) {
                    Column(Modifier.fillMaxWidth().padding(horizontal = 16.dp, vertical = 4.dp).clip(RoundedCornerShape(12.dp)).background(Look.cardBg).border(1.dp, NeonBorder, RoundedCornerShape(12.dp)).padding(12.dp)) {
                        Text("SCHEDULED CLAN BATTLE", color = NeonName, fontSize = 11.sp, fontWeight = FontWeight.Bold)
                        Spacer(Modifier.height(4.dp))
                        Text("vs " + riv.s("opponent"), color = Color.White, fontSize = 14.sp, fontWeight = FontWeight.Bold)
                        Text(dmTime(riv.s("scheduled_at")), color = Muted, fontSize = 12.sp)
                    }
                }
                val act = g.optJSONObject("activity")
                if (act != null) {
                    Column(Modifier.fillMaxWidth().padding(horizontal = 16.dp, vertical = 4.dp).clip(RoundedCornerShape(12.dp)).background(Look.cardBg).border(1.dp, Look.cardBorder, RoundedCornerShape(12.dp)).padding(12.dp)) {
                        Text("ROOM ACTIVITY", color = NeonName, fontSize = 11.sp, fontWeight = FontWeight.Bold)
                        Spacer(Modifier.height(4.dp))
                        Text("Today: " + act.optInt("messages_today") + " messages", color = Color.White, fontSize = 13.sp)
                        val sd = act.optInt("streak_days", 0)
                        if (sd > 1) Text("Room active " + sd + " days in a row", color = Gold2, fontSize = 12.sp)
                        val myDays = if (act.isNull("days_since_my_last_message")) null else act.optInt("days_since_my_last_message")
                        if (myDays != null && myDays >= 2) Text("You last wrote " + myDays + " days ago - say hello", color = Muted, fontSize = 12.sp)
                        val wc = act.optJSONArray("weekly_counts")
                        if (wc != null && wc.length() > 0) {
                            val top = wc.optJSONObject(0)
                            if (top != null && top.optInt("messages") > 0) Text("Top contributor this week: " + top.s("handle") + " (" + top.optInt("messages") + " messages)", color = Muted, fontSize = 12.sp)
                        }
                    }
                }
            }
            LazyColumn(state = listState, modifier = Modifier.weight(1f), contentPadding = PaddingValues(horizontal = 16.dp)) {
                if (msgs.isEmpty()) item { Text("No messages yet - write the first one and say hello to your squad.", color = Muted, fontSize = 13.sp, modifier = Modifier.padding(vertical = 12.dp)) }
                items(msgs) { m ->
                    val mine = m.s("user_id") == Api.uid
                    Row(Modifier.fillMaxWidth(), horizontalArrangement = if (mine) Arrangement.End else Arrangement.Start) {
                        Column(Modifier.widthIn(max = 280.dp).padding(vertical = 3.dp).clip(RoundedCornerShape(12.dp))
                            .background(if (mine) Color(0xFF7A2E2E) else Look.cardBg).padding(10.dp)) {
                            if (!mine) Text(m.s("by"), color = Gold2, fontSize = 11.sp, fontWeight = FontWeight.Bold)
                            Text(m.s("body"), color = Color.White, fontSize = 14.sp)
                            Text(dmTime(m.s("created_at")), color = Color.White.copy(alpha = .6f), fontSize = 9.sp, modifier = Modifier.align(Alignment.End))
                        }
                    }
                }
            }
            err?.let { Text(it, color = ErrRed, fontSize = 12.sp, modifier = Modifier.padding(horizontal = 16.dp)) }
            Row(Modifier.fillMaxWidth().background(Color(0xFF1A0E33)).padding(horizontal = 8.dp, vertical = 4.dp), horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                listOf("READY", "GG WP", "1V1").forEach { quick ->
                    Box(Modifier.clip(RoundedCornerShape(14.dp)).border(1.dp, Gold.copy(alpha = .7f), RoundedCornerShape(14.dp))
                        .clickable {
                            err = null
                            scope.launch {
                                try {
                                    val r = Api.authedRpc("room_post", JSONObject().put("p_body", quick))
                                    if (!r.optBoolean("ok")) err = r.s("error").ifBlank { "Could not send." }
                                } catch (e: Exception) { err = "Network error - try again." }
                            }
                        }.padding(horizontal = 12.dp, vertical = 6.dp)) { Text(quick, color = Gold, fontSize = 12.sp, fontWeight = FontWeight.Bold) }
                }
            }
            Row(Modifier.fillMaxWidth().background(Color(0xFF1A0E33)).padding(8.dp), verticalAlignment = Alignment.CenterVertically) {
                OutlinedTextField(input, { input = it }, placeholder = { Text("Message your squad...") },
                    modifier = Modifier.weight(1f), maxLines = 3)
                Spacer(Modifier.width(8.dp))
                Box(Modifier.size(48.dp).clip(CircleShape).background(if (input.isBlank()) Muted else Gold)
                    .clickable(enabled = input.isNotBlank()) {
                        val body = input.trim(); if (body.isEmpty()) return@clickable
                        input = ""; err = null
                        scope.launch {
                            try {
                                val r = Api.authedRpc("room_post", JSONObject().put("p_body", body))
                                if (!r.optBoolean("ok")) { err = r.s("error").ifBlank { "Could not send." }; input = body }
                            } catch (e: Exception) { err = "Network error - try again."; input = body }
                        }
                    }, contentAlignment = Alignment.Center) { Text("➤", color = Ink, fontSize = 18.sp) }
            }
        }
    }
}

/* ============ Contact & Support (support_ask / my_support) ============ */
@Composable
fun SupportScreen(onBack: () -> Unit) {
    val scope = rememberCoroutineScope()
    var tick by remember { mutableIntStateOf(0) }
    var q by remember { mutableStateOf("") }
    var busy by remember { mutableStateOf(false) }
    var msg by remember { mutableStateOf<String?>(null) }
    Column(Modifier.fillMaxSize().background(Ink)) {
        PageHeader("Contact & Support", "Ask the owner directly", onBack)
        LoadState2(tick, { Api.authedRpc("my_support", JSONObject()).optJSONArray("items") ?: JSONArray() }) { items ->
            LazyColumn {
                item {
                    SmallCard {
                        Text("Ask a question", color = Color.White, fontSize = 16.sp, fontWeight = FontWeight.Bold)
                        Spacer(Modifier.height(8.dp))
                        OutlinedTextField(q, { q = it }, placeholder = { Text("Type your full question...") },
                            modifier = Modifier.fillMaxWidth(), maxLines = 4)
                        Spacer(Modifier.height(8.dp))
                        GoldButton(if (busy) "ASKING..." else "ASK", enabled = !busy && q.trim().length >= 4) {
                            busy = true; msg = null
                            scope.launch {
                                try {
                                    val r = Api.authedRpc("support_ask", JSONObject().put("p_question", q.trim()))
                                    if (r.optBoolean("ok")) { q = ""; msg = "Sent - the owner will answer here."; tick++ }
                                    else msg = r.s("error").ifBlank { "Failed - try again." }
                                } catch (e: Exception) { msg = "Network error - try again." }
                                busy = false
                            }
                        }
                        msg?.let { Text(it, color = Gold2, fontSize = 13.sp, modifier = Modifier.padding(top = 8.dp)) }
                    }
                }
                if (items.length() > 0) {
                    item { Text("MY QUESTIONS", color = Muted, fontSize = 11.sp, fontWeight = FontWeight.Bold, modifier = Modifier.padding(16.dp)) }
                    items((0 until items.length()).map { items.getJSONObject(it) }) { it2 ->
                        SmallCard {
                            Text(it2.s("question"), color = Color.White, fontSize = 14.sp, fontWeight = FontWeight.SemiBold)
                            Spacer(Modifier.height(4.dp))
                            if (it2.s("status") == "answered") Text(it2.s("answer"), color = Color(0xFF4ADE80), fontSize = 13.sp)
                            else Text("With the owner - you will get a notification.", color = Gold2, fontSize = 12.sp)
                        }
                    }
                }
                item { Spacer(Modifier.height(20.dp)) }
            }
        }
    }
}

/* ============ Sensitivity Share (sens_feed / sens_share / sens_delete_own) ============ */
private val SENS_FIELDS = listOf("general" to "General", "red_dot" to "Red dot", "scope2x" to "2x scope", "scope4x" to "4x scope", "sniper" to "Sniper", "free_look" to "Free look")

@Composable
fun SensitivityScreen(onBack: () -> Unit) {
    val scope = rememberCoroutineScope()
    var tick by remember { mutableIntStateOf(0) }
    var search by remember { mutableStateOf("") }
    var showShare by remember { mutableStateOf(false) }
    var device by remember { mutableStateOf("") }
    var note by remember { mutableStateOf("") }
    val vals = remember { mutableStateMapOf<String, String>() }
    var msg by remember { mutableStateOf<String?>(null) }
    Column(Modifier.fillMaxSize().background(Ink)) {
        PageHeader("Sensitivity Share", "Real setups from real players", onBack)
        OutlinedTextField(search, { search = it; tick++ }, placeholder = { Text("Search by device name") },
            singleLine = true, modifier = Modifier.fillMaxWidth().padding(horizontal = 16.dp, vertical = 4.dp))
        LoadState2(tick, {
            Api.authedRpc("sens_feed", JSONObject().put("p_search", if (search.isBlank()) JSONObject.NULL else search.trim())).optJSONArray("rows") ?: JSONArray()
        }) { rows ->
            LazyColumn {
                item {
                    Text(if (showShare) "CLOSE" else "SHARE YOUR SETUP", color = Gold, fontSize = 13.sp, fontWeight = FontWeight.Bold,
                        modifier = Modifier.padding(horizontal = 16.dp, vertical = 6.dp).clickable { showShare = !showShare }.padding(4.dp))
                    if (showShare) SmallCard {
                        OutlinedTextField(device, { device = it }, label = { Text("Device (e.g. Redmi Note 12)") }, singleLine = true, modifier = Modifier.fillMaxWidth())
                        Spacer(Modifier.height(6.dp))
                        SENS_FIELDS.chunked(3).forEach { rowF ->
                            Row(Modifier.fillMaxWidth()) {
                                rowF.forEach { (k, label) ->
                                    OutlinedTextField(vals[k] ?: "", { v -> vals[k] = v.filter { c -> c.isDigit() }.take(3) },
                                        label = { Text(label, fontSize = 10.sp) }, singleLine = true,
                                        keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Number),
                                        modifier = Modifier.weight(1f).padding(2.dp))
                                }
                            }
                        }
                        Spacer(Modifier.height(6.dp))
                        OutlinedTextField(note, { note = it }, label = { Text("Note (optional)") }, modifier = Modifier.fillMaxWidth(), maxLines = 2)
                        Spacer(Modifier.height(8.dp))
                        val ready = device.isNotBlank() && SENS_FIELDS.all { (vals[it.first] ?: "").isNotBlank() }
                        GoldButton("PUBLISH SETUP", enabled = ready) {
                            scope.launch {
                                try {
                                    val args = JSONObject().put("p_device", device.trim()).put("p_note", note.trim())
                                    SENS_FIELDS.forEach { (k, _) -> args.put("p_" + k, (vals[k] ?: "0").toIntOrNull() ?: 0) }
                                    val r = Api.authedRpc("sens_share", args)
                                    msg = if (r.optBoolean("ok")) { showShare = false; tick++; "Setup published - it is live below." } else r.s("error").ifBlank { "Could not publish." }
                                } catch (e: Exception) { msg = "Network error - try again." }
                            }
                        }
                        msg?.let { Text(it, color = Gold2, fontSize = 12.sp, modifier = Modifier.padding(top = 6.dp)) }
                    }
                }
                if (rows.length() == 0) item {
                    Text(if (search.isBlank()) "No setups shared yet - be the first." else "No setup matches \"" + search.trim() + "\".",
                        color = Muted, fontSize = 13.sp, modifier = Modifier.padding(16.dp))
                }
                items((0 until rows.length()).map { rows.getJSONObject(it) }) { r ->
                    SmallCard {
                        Row(verticalAlignment = Alignment.CenterVertically) {
                            Text(r.s("by"), color = Color.White, fontSize = 15.sp, fontWeight = FontWeight.Bold)
                            Text("  on  ", color = Muted, fontSize = 12.sp)
                            Text(r.s("device"), color = Gold2, fontSize = 14.sp, fontWeight = FontWeight.Bold, modifier = Modifier.weight(1f))
                            if (r.s("user_id") == Api.uid) {
                                Text("Delete", color = ErrRed, fontSize = 12.sp, modifier = Modifier.clickable {
                                    scope.launch { runCatching { Api.authedRpc("sens_delete_own", JSONObject().put("p_id", r.s("id"))) }; tick++ }
                                }.padding(4.dp))
                            }
                        }
                        Spacer(Modifier.height(8.dp))
                        Row(Modifier.fillMaxWidth()) {
                            SENS_FIELDS.forEach { (k, label) ->
                                Column(Modifier.weight(1f), horizontalAlignment = Alignment.CenterHorizontally) {
                                    Text(r.optInt(k).toString(), color = Gold2, fontSize = 15.sp, fontWeight = FontWeight.Bold)
                                    Text(label, color = Muted, fontSize = 9.sp)
                                }
                            }
                        }
                        if (r.s("note").isNotBlank()) Text(r.s("note"), color = Muted, fontSize = 12.sp, modifier = Modifier.padding(top = 6.dp))
                    }
                }
                item { Spacer(Modifier.height(20.dp)) }
            }
        }
    }
}

/* ============ Coaches (coach_feed / coach_publish / coach_unpublish / coach_request / coach_requests_mine / coach_respond) ============ */
@Composable
fun CoachesScreen(onBack: () -> Unit) {
    val scope = rememberCoroutineScope()
    var tick by remember { mutableIntStateOf(0) }
    var specialty by remember { mutableStateOf("") }
    var bio by remember { mutableStateOf("") }
    var msg by remember { mutableStateOf<String?>(null) }
    var reqFor by remember { mutableStateOf<JSONObject?>(null) }
    var reqNote by remember { mutableStateOf("") }
    Column(Modifier.fillMaxSize().background(Ink)) {
        PageHeader("Coaches", "Learn from verified players", onBack)
        LoadState2(tick, {
            val feed = Api.authedRpc("coach_feed", JSONObject()).optJSONArray("rows") ?: JSONArray()
            val mine = runCatching { Api.authedRpc("coach_requests_mine", JSONObject()) }.getOrNull()
            feed to mine
        }) { (feed, mine) ->
            LazyColumn {
                // my listing / publish form
                item {
                    val listing = mine?.optJSONObject("listing")
                    if (listing == null || !listing.optBoolean("active")) {
                        SmallCard {
                            Text("List yourself as a coach", color = Color.White, fontSize = 16.sp, fontWeight = FontWeight.Bold)
                            Spacer(Modifier.height(8.dp))
                            OutlinedTextField(specialty, { specialty = it }, label = { Text("Your specialty (e.g. Aim & recoil)") }, singleLine = true, modifier = Modifier.fillMaxWidth())
                            Spacer(Modifier.height(6.dp))
                            OutlinedTextField(bio, { bio = it }, label = { Text("Short bio") }, modifier = Modifier.fillMaxWidth(), maxLines = 3)
                            Spacer(Modifier.height(8.dp))
                            GoldButton("PUBLISH LISTING", enabled = specialty.isNotBlank()) {
                                scope.launch {
                                    try {
                                        val r = Api.authedRpc("coach_publish", JSONObject().put("p_specialty", specialty.trim()).put("p_bio", bio.trim()))
                                        msg = if (r.optBoolean("ok")) { tick++; "Listed - players can now request you." } else r.s("error").ifBlank { "Could not publish." }
                                    } catch (e: Exception) { msg = "Network error - try again." }
                                }
                            }
                        }
                    } else SmallCard {
                        Text("Your listing is live: " + listing.s("specialty"), color = Color(0xFF4ADE80), fontSize = 14.sp, fontWeight = FontWeight.Bold)
                        Text("Take it down", color = ErrRed, fontSize = 12.sp, modifier = Modifier.clickable {
                            scope.launch { runCatching { Api.authedRpc("coach_unpublish", JSONObject()) }; tick++ }
                        }.padding(vertical = 4.dp))
                    }
                    msg?.let { Text(it, color = Gold2, fontSize = 12.sp, modifier = Modifier.padding(horizontal = 16.dp, vertical = 4.dp)) }
                }
                // incoming requests
                val incoming = mine?.optJSONArray("incoming") ?: JSONArray()
                if (incoming.length() > 0) {
                    item { Text("REQUESTS FOR YOU", color = Muted, fontSize = 11.sp, fontWeight = FontWeight.Bold, modifier = Modifier.padding(horizontal = 16.dp, vertical = 6.dp)) }
                    items((0 until incoming.length()).map { incoming.getJSONObject(it) }) { r ->
                        SmallCard {
                            Text(r.s("by"), color = Color.White, fontSize = 15.sp, fontWeight = FontWeight.Bold)
                            if (r.s("note").isNotBlank()) Text(r.s("note"), color = Muted, fontSize = 13.sp)
                            if (r.s("status") == "pending") Row(Modifier.padding(top = 6.dp)) {
                                Text("Accept", color = Ink, fontSize = 12.sp, fontWeight = FontWeight.Bold,
                                    modifier = Modifier.clip(RoundedCornerShape(8.dp)).background(Gold).clickable {
                                        scope.launch { runCatching { Api.authedRpc("coach_respond", JSONObject().put("p_request", r.optLong("id")).put("p_accept", true)) }; tick++ }
                                    }.padding(horizontal = 14.dp, vertical = 7.dp))
                                Spacer(Modifier.width(10.dp))
                                Text("Decline", color = ErrRed, fontSize = 12.sp, fontWeight = FontWeight.Bold,
                                    modifier = Modifier.clip(RoundedCornerShape(8.dp)).border(1.dp, ErrRed, RoundedCornerShape(8.dp)).clickable {
                                        scope.launch { runCatching { Api.authedRpc("coach_respond", JSONObject().put("p_request", r.optLong("id")).put("p_accept", false)) }; tick++ }
                                    }.padding(horizontal = 14.dp, vertical = 7.dp))
                            } else Text(r.s("status").uppercase(), color = Gold, fontSize = 11.sp, fontWeight = FontWeight.Bold)
                        }
                    }
                }
                // coach feed
                if (feed.length() == 0) item { Text("No coaches listed yet - good at something? Be the first coach.", color = Muted, fontSize = 13.sp, modifier = Modifier.padding(16.dp)) }
                items((0 until feed.length()).map { feed.getJSONObject(it) }) { c ->
                    SmallCard {
                        Row(verticalAlignment = Alignment.CenterVertically) {
                            Text(c.s("by"), color = Color.White, fontSize = 16.sp, fontWeight = FontWeight.Bold)
                            Spacer(Modifier.width(8.dp))
                            Text(c.s("specialty"), color = Gold, fontSize = 11.sp, fontWeight = FontWeight.Bold,
                                modifier = Modifier.clip(RoundedCornerShape(6.dp)).border(1.dp, Gold.copy(alpha = .5f), RoundedCornerShape(6.dp)).padding(horizontal = 6.dp, vertical = 2.dp))
                        }
                        Text(c.optInt("coached").toString() + " player" + (if (c.optInt("coached") == 1) "" else "s") + " coached", color = Muted, fontSize = 12.sp)
                        if (c.s("bio").isNotBlank()) Text(c.s("bio"), color = Muted, fontSize = 13.sp, modifier = Modifier.padding(top = 4.dp))
                        if (c.s("user_id") == Api.uid) Text("This is your listing.", color = Gold2, fontSize = 12.sp, modifier = Modifier.padding(top = 6.dp))
                        else Text("REQUEST COACHING", color = Gold, fontSize = 12.sp, fontWeight = FontWeight.Bold,
                            modifier = Modifier.padding(top = 8.dp).clip(RoundedCornerShape(8.dp)).border(1.dp, Gold, RoundedCornerShape(8.dp))
                                .clickable { reqFor = c; reqNote = "" }.padding(horizontal = 12.dp, vertical = 7.dp))
                    }
                }
                item { Spacer(Modifier.height(20.dp)) }
            }
        }
        reqFor?.let { c ->
            androidx.compose.material3.AlertDialog(onDismissRequest = { reqFor = null },
                confirmButton = { Text("SEND", color = Gold, fontWeight = FontWeight.Bold, modifier = Modifier.clickable {
                    val note = reqNote.trim(); reqFor = null
                    scope.launch {
                        try {
                            val r = Api.authedRpc("coach_request", JSONObject().put("p_coach", c.s("user_id")).put("p_note", note))
                            msg = if (r.optBoolean("ok")) "Request sent - the coach got a notification." else r.s("error").ifBlank { "Could not send the request." }
                        } catch (e: Exception) { msg = "Network error - try again." }
                    }
                }.padding(8.dp)) },
                dismissButton = { Text("Cancel", color = Muted, modifier = Modifier.clickable { reqFor = null }.padding(8.dp)) },
                title = { Text("One line for " + c.s("by"), color = Color.White) },
                text = { OutlinedTextField(reqNote, { reqNote = it }, placeholder = { Text("What do you want to work on?") }) },
                containerColor = Color(0xFF241239))
        }
    }
}

/* ============ Sponsor a Cup (sponsor_apply) ============ */
@Composable
fun CupScreen(onBack: () -> Unit) {
    val scope = rememberCoroutineScope()
    var brand by remember { mutableStateOf("") }
    var contact by remember { mutableStateOf("") }
    var note by remember { mutableStateOf("") }
    var msg by remember { mutableStateOf<String?>(null) }
    var busy by remember { mutableStateOf(false) }
    Column(Modifier.fillMaxSize().background(Ink)) {
        PageHeader("Sponsor a Cup", "Your shop's name on a real tournament", onBack)
        LazyColumn {
            item {
                SmallCard {
                    Text("For local shops, cafes and brands", color = Gold2, fontSize = 16.sp, fontWeight = FontWeight.Bold)
                    Spacer(Modifier.height(6.dp))
                    Text("Put your name on a real Free Fire MAX tournament - \"YourShop Cup\" - run end to end by XARENA ESPORTS. Players always enter free. You only fund the prize.", color = Color.White, fontSize = 13.sp)
                }
                SmallCard {
                    OutlinedTextField(brand, { brand = it }, label = { Text("Brand / shop name") }, singleLine = true, modifier = Modifier.fillMaxWidth())
                    Spacer(Modifier.height(6.dp))
                    OutlinedTextField(contact, { contact = it }, label = { Text("How do we reach you?") }, singleLine = true, modifier = Modifier.fillMaxWidth())
                    Spacer(Modifier.height(6.dp))
                    OutlinedTextField(note, { note = it }, label = { Text("Anything we should know?") }, modifier = Modifier.fillMaxWidth(), maxLines = 3)
                    Spacer(Modifier.height(8.dp))
                    GoldButton(if (busy) "SENDING..." else "SEND INQUIRY", enabled = !busy && brand.isNotBlank() && contact.isNotBlank()) {
                        busy = true; msg = null
                        scope.launch {
                            try {
                                val r = Api.authedRpc("sponsor_apply", JSONObject().put("p_brand", brand.trim()).put("p_contact", contact.trim()).put("p_note", note.trim()))
                                if (r.optBoolean("ok")) { brand = ""; contact = ""; note = ""; msg = "Inquiry sent - the owner reads every one and will reach out." }
                                else msg = r.s("error").ifBlank { "Could not send - try again." }
                            } catch (e: Exception) { msg = "Network error - try again." }
                            busy = false
                        }
                    }
                    msg?.let { Text(it, color = Gold2, fontSize = 13.sp, modifier = Modifier.padding(top = 8.dp)) }
                }
            }
        }
    }
}
