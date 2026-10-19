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
    val listState = rememberLazyListState()
    LaunchedEffect(Unit) {
        info = runCatching { Api.authedRpc("room_info", JSONObject()) }.getOrNull()
        loaded = true
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
                Text(info!!.optString("name") + (info!!.optString("tag").takeIf { it.isNotBlank() }?.let { " [$it]" } ?: ""),
                    color = Gold2, fontSize = 16.sp, fontWeight = FontWeight.Bold, modifier = Modifier.weight(1f))
                Text(info!!.optInt("members").toString() + " member" + (if (info!!.optInt("members") == 1) "" else "s"), color = Muted, fontSize = 12.sp)
            }
            LazyColumn(state = listState, modifier = Modifier.weight(1f), contentPadding = PaddingValues(horizontal = 16.dp)) {
                if (msgs.isEmpty()) item { Text("No messages yet - say hello to your squad.", color = Muted, fontSize = 13.sp, modifier = Modifier.padding(vertical = 12.dp)) }
                items(msgs) { m ->
                    val mine = m.optString("user_id") == Api.uid
                    Row(Modifier.fillMaxWidth(), horizontalArrangement = if (mine) Arrangement.End else Arrangement.Start) {
                        Column(Modifier.widthIn(max = 280.dp).padding(vertical = 3.dp).clip(RoundedCornerShape(12.dp))
                            .background(if (mine) Color(0xFF7A2E2E) else Look.cardBg).padding(10.dp)) {
                            if (!mine) Text(m.optString("by"), color = Gold2, fontSize = 11.sp, fontWeight = FontWeight.Bold)
                            Text(m.optString("body"), color = Color.White, fontSize = 14.sp)
                            Text(dmTime(m.optString("created_at")), color = Color.White.copy(alpha = .6f), fontSize = 9.sp, modifier = Modifier.align(Alignment.End))
                        }
                    }
                }
            }
            err?.let { Text(it, color = ErrRed, fontSize = 12.sp, modifier = Modifier.padding(horizontal = 16.dp)) }
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
                                if (!r.optBoolean("ok")) { err = r.optString("error").ifBlank { "Could not send." }; input = body }
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
                                    else msg = r.optString("error").ifBlank { "Failed - try again." }
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
                            Text(it2.optString("question"), color = Color.White, fontSize = 14.sp, fontWeight = FontWeight.SemiBold)
                            Spacer(Modifier.height(4.dp))
                            if (it2.optString("status") == "answered") Text(it2.optString("answer"), color = Color(0xFF4ADE80), fontSize = 13.sp)
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
                                    msg = if (r.optBoolean("ok")) { showShare = false; tick++; "Setup published - it is live below." } else r.optString("error").ifBlank { "Could not publish." }
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
                            Text(r.optString("by"), color = Color.White, fontSize = 15.sp, fontWeight = FontWeight.Bold)
                            Text("  on  ", color = Muted, fontSize = 12.sp)
                            Text(r.optString("device"), color = Gold2, fontSize = 14.sp, fontWeight = FontWeight.Bold, modifier = Modifier.weight(1f))
                            if (r.optString("user_id") == Api.uid) {
                                Text("Delete", color = ErrRed, fontSize = 12.sp, modifier = Modifier.clickable {
                                    scope.launch { runCatching { Api.authedRpc("sens_delete_own", JSONObject().put("p_id", r.optString("id"))) }; tick++ }
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
                        if (r.optString("note").isNotBlank()) Text(r.optString("note"), color = Muted, fontSize = 12.sp, modifier = Modifier.padding(top = 6.dp))
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
                                        msg = if (r.optBoolean("ok")) { tick++; "Listed - players can now request you." } else r.optString("error").ifBlank { "Could not publish." }
                                    } catch (e: Exception) { msg = "Network error - try again." }
                                }
                            }
                        }
                    } else SmallCard {
                        Text("Your listing is live: " + listing.optString("specialty"), color = Color(0xFF4ADE80), fontSize = 14.sp, fontWeight = FontWeight.Bold)
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
                            Text(r.optString("by"), color = Color.White, fontSize = 15.sp, fontWeight = FontWeight.Bold)
                            if (r.optString("note").isNotBlank()) Text(r.optString("note"), color = Muted, fontSize = 13.sp)
                            if (r.optString("status") == "pending") Row(Modifier.padding(top = 6.dp)) {
                                Text("Accept", color = Ink, fontSize = 12.sp, fontWeight = FontWeight.Bold,
                                    modifier = Modifier.clip(RoundedCornerShape(8.dp)).background(Gold).clickable {
                                        scope.launch { runCatching { Api.authedRpc("coach_respond", JSONObject().put("p_request", r.optLong("id")).put("p_accept", true)) }; tick++ }
                                    }.padding(horizontal = 14.dp, vertical = 7.dp))
                                Spacer(Modifier.width(10.dp))
                                Text("Decline", color = ErrRed, fontSize = 12.sp, fontWeight = FontWeight.Bold,
                                    modifier = Modifier.clip(RoundedCornerShape(8.dp)).border(1.dp, ErrRed, RoundedCornerShape(8.dp)).clickable {
                                        scope.launch { runCatching { Api.authedRpc("coach_respond", JSONObject().put("p_request", r.optLong("id")).put("p_accept", false)) }; tick++ }
                                    }.padding(horizontal = 14.dp, vertical = 7.dp))
                            } else Text(r.optString("status").uppercase(), color = Gold, fontSize = 11.sp, fontWeight = FontWeight.Bold)
                        }
                    }
                }
                // coach feed
                if (feed.length() == 0) item { Text("No coaches listed yet - good at something? Be the first coach.", color = Muted, fontSize = 13.sp, modifier = Modifier.padding(16.dp)) }
                items((0 until feed.length()).map { feed.getJSONObject(it) }) { c ->
                    SmallCard {
                        Row(verticalAlignment = Alignment.CenterVertically) {
                            Text(c.optString("by"), color = Color.White, fontSize = 16.sp, fontWeight = FontWeight.Bold)
                            Spacer(Modifier.width(8.dp))
                            Text(c.optString("specialty"), color = Gold, fontSize = 11.sp, fontWeight = FontWeight.Bold,
                                modifier = Modifier.clip(RoundedCornerShape(6.dp)).border(1.dp, Gold.copy(alpha = .5f), RoundedCornerShape(6.dp)).padding(horizontal = 6.dp, vertical = 2.dp))
                        }
                        Text(c.optInt("coached").toString() + " player" + (if (c.optInt("coached") == 1) "" else "s") + " coached", color = Muted, fontSize = 12.sp)
                        if (c.optString("bio").isNotBlank()) Text(c.optString("bio"), color = Muted, fontSize = 13.sp, modifier = Modifier.padding(top = 4.dp))
                        if (c.optString("user_id") == Api.uid) Text("This is your listing.", color = Gold2, fontSize = 12.sp, modifier = Modifier.padding(top = 6.dp))
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
                            val r = Api.authedRpc("coach_request", JSONObject().put("p_coach", c.optString("user_id")).put("p_note", note))
                            msg = if (r.optBoolean("ok")) "Request sent - the coach got a notification." else r.optString("error").ifBlank { "Could not send the request." }
                        } catch (e: Exception) { msg = "Network error - try again." }
                    }
                }.padding(8.dp)) },
                dismissButton = { Text("Cancel", color = Muted, modifier = Modifier.clickable { reqFor = null }.padding(8.dp)) },
                title = { Text("One line for " + c.optString("by"), color = Color.White) },
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
                                else msg = r.optString("error").ifBlank { "Could not send - try again." }
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
