package app.xarena.nativeapp

import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Text
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
import kotlinx.coroutines.launch
import org.json.JSONArray
import org.json.JSONObject

/* Release B (v78): Slot Booking, MVP Voting, Live Match (host kill feed, no chat yet).
   Backend: xr_slot_* (5-min provisional hold, capacity server-side), xr_mvp_* (one immutable vote
   after match done, nominees from real lb_entries), xr_kill_feed_* (owner/helper/feed-only post),
   xr_feed_capability() gates host controls. Chat flag is OFF: render the honest disabled state. */

@Composable
fun SlotBookingScreen(onBack: () -> Unit) {
    val scope = rememberCoroutineScope()
    var tick by remember { mutableIntStateOf(0) }
    var target by remember { mutableStateOf<String?>(null) }
    var targetErr by remember { mutableStateOf<String?>(null) }
    LaunchedEffect(Unit) {
        target = try { Api.regTournament() } catch (e: Exception) { targetErr = e.message; null }
    }
    Column(Modifier.fillMaxSize().background(Ink)) {
        PageHeader("Slot Booking", "Pick your squad's slot - held 5 minutes", onBack)
        val tid = target
        if (targetErr != null) { Text(targetErr!!, color = Muted, fontSize = 14.sp, modifier = Modifier.padding(24.dp)); return@Column }
        if (tid == null) { Text("Loading...", color = Muted, fontSize = 14.sp, modifier = Modifier.padding(24.dp)); return@Column }
        LoadState2(tick, {
            val list = runCatching { Api.authedRpc("xr_slot_list", JSONObject().put("p_tournament", tid)) }.getOrNull()
            val mine = runCatching { Api.authedRpc("xr_slot_my", JSONObject().put("p_tournament", tid)) }.getOrNull()
            JSONObject().put("list", list ?: JSONObject.NULL).put("mine", mine ?: JSONObject.NULL)
        }) { d ->
            val list = if (d.isNull("list")) null else d.getJSONObject("list")
            val mine = if (d.isNull("mine")) null else d.getJSONObject("mine")
            if (list == null || !list.optBoolean("ok")) {
                Text(list?.s("error")?.ifBlank { "Slots are not available right now." } ?: "Slots are not available right now.",
                    color = Muted, fontSize = 14.sp, modifier = Modifier.padding(24.dp))
                return@LoadState2
            }
            val myReg = mine?.takeIf { it.optBoolean("ok") }?.optJSONObject("registration")
            var squad by remember { mutableStateOf("") }
            var ign by remember { mutableStateOf("") }
            var uid by remember { mutableStateOf("") }
            var phone by remember { mutableStateOf("") }
            val pIgn = remember { mutableStateListOf("", "", "") }
            val pUid = remember { mutableStateListOf("", "", "") }
            var held by remember { mutableStateOf<Int?>(null) }
            var holdExp by remember { mutableLongStateOf(0L) }
            var msg by remember { mutableStateOf<String?>(null) }
            var busy by remember { mutableStateOf(false) }
            LazyColumn(contentPadding = PaddingValues(16.dp), verticalArrangement = Arrangement.spacedBy(10.dp)) {
                item {
                    PanelCard {
                        Text("CAPACITY", color = Muted, fontSize = 11.sp, fontWeight = FontWeight.Bold)
                        Text(list.optInt("registered").toString() + " / " + list.optInt("capacity") + " squads registered",
                            color = Color.White, fontSize = 16.sp, fontWeight = FontWeight.Bold)
                        Text("Some squads register without choosing a slot - a free number does not guarantee a free place.",
                            color = Muted, fontSize = 11.sp)
                    }
                }
                if (myReg != null) {
                    item {
                        PanelCard {
                            Text("YOUR SLOT", color = Color(0xFF4ADE80), fontSize = 11.sp, fontWeight = FontWeight.Bold)
                            Text("Slot " + (if (myReg.optInt("slot_no", 0) > 0) myReg.optInt("slot_no").toString() else "not chosen") + " - " + myReg.s("squad"),
                                color = Gold2, fontSize = 18.sp, fontWeight = FontWeight.Bold)
                            Text("Status: " + myReg.s("status").ifBlank { "pending" }, color = Muted, fontSize = 12.sp)
                            val pl = myReg.optJSONArray("players")
                            if (pl != null && pl.length() > 0) {
                                Spacer(Modifier.height(4.dp))
                                for (i in 0 until pl.length()) {
                                    val p = pl.getJSONObject(i)
                                    Text(p.s("ign") + " (" + p.s("uid") + ")", color = Color.White, fontSize = 12.sp)
                                }
                            }
                        }
                    }
                } else {
                    item {
                        Text("CHOOSE A SLOT", color = Muted, fontSize = 11.sp, fontWeight = FontWeight.Bold, modifier = Modifier.padding(horizontal = 8.dp))
                        val slots = list.optJSONArray("slots") ?: JSONArray()
                        val cap = slots.length()
                        val cols = 6
                        for (rowStart in 0 until cap step cols) {
                            Row(Modifier.fillMaxWidth().padding(vertical = 3.dp), horizontalArrangement = Arrangement.spacedBy(6.dp)) {
                                for (c in 0 until cols) {
                                    val idx = rowStart + c
                                    if (idx >= cap) { Spacer(Modifier.weight(1f)); continue }
                                    val s = slots.getJSONObject(idx)
                                    val no = s.optInt("slot_no")
                                    val st = s.s("state")
                                    val isMine = s.optBoolean("mine") || held == no
                                    val bg = when {
                                        isMine -> Gold2
                                        st == "available" -> Look.cardBg
                                        else -> Color(0xFF2A2A3A)
                                    }
                                    val fg = when {
                                        isMine -> Color(0xFF1A1208)
                                        st == "available" -> Color.White
                                        else -> Muted
                                    }
                                    Box(Modifier.weight(1f).aspectRatio(1f).clip(RoundedCornerShape(8.dp)).background(bg)
                                        .clickable(enabled = st == "available" && !busy) {
                                            if (busy) return@clickable
                                            busy = true; msg = null
                                            scope.launch {
                                                val h = runCatching { Api.authedRpc("xr_slot_hold", JSONObject().put("p_tournament", tid).put("p_slot", no)) }.getOrNull()
                                                if (h != null && h.optBoolean("ok")) {
                                                    held = no
                                                    holdExp = runCatching { java.time.Instant.parse(h.s("expires_at")).toEpochMilli() }.getOrDefault(System.currentTimeMillis() + 300000)
                                                    msg = "Slot " + no + " held for 5 minutes - confirm your squad below."
                                                } else msg = h?.s("error")?.ifBlank { "Could not hold that slot." } ?: "Could not hold that slot."
                                                busy = false
                                            }
                                        }, contentAlignment = Alignment.Center) {
                                        Text(no.toString(), color = fg, fontSize = 13.sp, fontWeight = FontWeight.Bold)
                                    }
                                }
                            }
                        }
                        Row(Modifier.padding(top = 4.dp), horizontalArrangement = Arrangement.spacedBy(12.dp)) {
                            Text("Free", color = Color.White, fontSize = 10.sp)
                            Text("Taken", color = Muted, fontSize = 10.sp)
                            Text("Yours", color = Gold2, fontSize = 10.sp)
                        }
                    }
                    if (held != null) {
                        item {
                            var nowMs by remember { mutableLongStateOf(System.currentTimeMillis()) }
                            LaunchedEffect(held) { while (true) { delay(1000); nowMs = System.currentTimeMillis() } }
                            val left = (holdExp - nowMs) / 1000
                            PanelCard {
                                Text("SLOT " + held + " HELD", color = Gold2, fontSize = 11.sp, fontWeight = FontWeight.Bold)
                                Text(if (left > 0) "Confirm within " + (left / 60) + ":" + String.format("%02d", left % 60) else "Hold expiring - re-tap the slot if needed.",
                                    color = Color.White, fontSize = 13.sp, fontWeight = FontWeight.Bold)
                            }
                        }
                        item {
                            PanelCard {
                                Text("SQUAD DETAILS", color = Muted, fontSize = 11.sp, fontWeight = FontWeight.Bold)
                                XrField("Squad name", squad) { squad = it }
                                XrField("Captain IGN", ign) { ign = it }
                                XrField("Captain Free Fire UID", uid, KeyboardType.Number) { uid = it.filter(Char::isDigit) }
                                XrField("Contact phone", phone, KeyboardType.Phone) { phone = it.filter(Char::isDigit) }
                                Text("PLAYERS (captain + up to 3, IGN and UID)", color = Muted, fontSize = 10.sp, modifier = Modifier.padding(top = 6.dp))
                                for (i in 0..2) {
                                    Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.spacedBy(6.dp)) {
                                        Box(Modifier.weight(1f)) { XrField("Player " + (i + 2) + " IGN", pIgn[i]) { pIgn[i] = it } }
                                        Box(Modifier.weight(1f)) { XrField("UID", pUid[i], KeyboardType.Number) { pUid[i] = it.filter(Char::isDigit) } }
                                    }
                                }
                            }
                        }
                        item {
                            GoldButton(if (busy) "CONFIRMING..." else "CONFIRM SLOT " + held, enabled = !busy) {
                                if (busy) return@GoldButton
                                busy = true; msg = null
                                scope.launch {
                                    val players = JSONArray()
                                    players.put(JSONObject().put("ign", ign.trim()).put("uid", uid.trim()))
                                    for (i in 0..2) if (pIgn[i].isNotBlank() && pUid[i].isNotBlank())
                                        players.put(JSONObject().put("ign", pIgn[i].trim()).put("uid", pUid[i].trim()))
                                    val reg = JSONObject().put("squad", squad.trim()).put("captain", ign.trim()).put("ign", ign.trim())
                                        .put("uid", uid.trim()).put("phone", phone.trim())
                                    val r = runCatching { Api.authedRpc("xr_slot_confirm", JSONObject().put("p_tournament", tid).put("p_slot", held)
                                        .put("p_registration", reg).put("p_players", players)) }.getOrNull()
                                    if (r != null && r.optBoolean("ok")) { msg = "Slot " + r.optInt("slot_no") + " booked for " + squad.trim() + "."; tick++ }
                                    else msg = r?.s("error")?.ifBlank { "Confirmation failed - the slot may have been taken." } ?: "Confirmation failed."
                                    busy = false
                                }
                            }
                        }
                    }
                    msg?.let { item { Text(it, color = if (it.startsWith("Slot ")) Color(0xFF4ADE80) else Color(0xFFF87171), fontSize = 13.sp, modifier = Modifier.padding(horizontal = 8.dp)) } }
                }
            }
        }
    }
}

@Composable
private fun XrField(label: String, value: String, type: KeyboardType = KeyboardType.Text, onChange: (String) -> Unit) {
    OutlinedTextField(value = value, onValueChange = onChange, label = { Text(label, fontSize = 11.sp) },
        singleLine = true, keyboardOptions = KeyboardOptions(keyboardType = type),
        modifier = Modifier.fillMaxWidth().padding(vertical = 3.dp))
}

@Composable
fun MvpVoteScreen(onBack: () -> Unit) {
    val scope = rememberCoroutineScope()
    var tick by remember { mutableIntStateOf(0) }
    var msg by remember { mutableStateOf<String?>(null) }
    Column(Modifier.fillMaxSize().background(Ink)) {
        PageHeader("MVP Voting", "One vote per player, after the match ends", onBack)
        LoadState2(tick, {
            val ms = runCatching { Api.publicGet("/rest/v1/matches_public?select=id,title,starts_at,status,tournament_id&status=eq.done&order=starts_at.desc&limit=1") }.getOrElse { JSONArray() }
            val m = if (ms.length() > 0) ms.getJSONObject(0) else null
            var tally: JSONObject? = null
            if (m != null) tally = runCatching { Api.authedRpc("xr_mvp_tally", JSONObject().put("p_match", m.s("id"))) }.getOrNull()
            JSONObject().put("m", m ?: JSONObject.NULL).put("tally", tally ?: JSONObject.NULL)
        }) { d ->
            if (d.isNull("m")) { Text("No finished match yet - MVP voting opens after the first result.", color = Muted, fontSize = 14.sp, modifier = Modifier.padding(24.dp)); return@LoadState2 }
            val m = d.getJSONObject("m")
            val tally = if (d.isNull("tally")) null else d.getJSONObject("tally")
            LazyColumn(contentPadding = PaddingValues(16.dp), verticalArrangement = Arrangement.spacedBy(8.dp)) {
                item {
                    PanelCard {
                        Text(m.s("title"), color = Color.White, fontSize = 16.sp, fontWeight = FontWeight.Bold)
                        Text(m.s("tournament_id"), color = Muted, fontSize = 12.sp)
                    }
                }
                if (tally == null || !tally.optBoolean("ok")) {
                    item { Text(tally?.s("error")?.ifBlank { "Voting is not available for this match." } ?: "Voting is not available for this match.", color = Muted, fontSize = 13.sp, modifier = Modifier.padding(horizontal = 8.dp)) }
                } else if (!tally.optBoolean("open")) {
                    item { Text("Voting is closed for this match.", color = Muted, fontSize = 13.sp, modifier = Modifier.padding(horizontal = 8.dp)) }
                }
                if (tally != null && tally.optBoolean("ok")) {
                    val myVote = if (tally.isNull("my_vote")) null else tally.s("my_vote")
                    val cands = tally.optJSONArray("candidates") ?: JSONArray()
                    if (cands.length() == 0) item { Text("No nominees yet - they come from the real match scoreboard.", color = Muted, fontSize = 13.sp, modifier = Modifier.padding(horizontal = 8.dp)) }
                    items(cands.length()) { i ->
                        val c = cands.getJSONObject(i)
                        val mine = myVote != null && myVote == c.s("entry_id")
                        Row(Modifier.fillMaxWidth().clip(RoundedCornerShape(10.dp))
                            .background(Look.cardBg)
                            .border(if (mine) 2.dp else 0.dp, if (mine) Gold2 else Color.Transparent, RoundedCornerShape(10.dp))
                            .padding(horizontal = 12.dp, vertical = 10.dp), verticalAlignment = Alignment.CenterVertically) {
                            Column(Modifier.weight(1f)) {
                                Text(c.s("player_name"), color = Color.White, fontSize = 14.sp, fontWeight = FontWeight.Bold)
                                Text(c.s("squad") + " - " + c.optInt("kills") + " kills", color = Muted, fontSize = 11.sp)
                            }
                            Text(c.optInt("votes").toString() + " votes", color = Gold2, fontSize = 12.sp, fontWeight = FontWeight.Bold)
                            if (tally.optBoolean("open") && myVote == null) {
                                Spacer(Modifier.width(10.dp))
                                Text("VOTE", color = Gold, fontSize = 12.sp, fontWeight = FontWeight.Bold,
                                    modifier = Modifier.clip(RoundedCornerShape(8.dp)).border(1.dp, Gold, RoundedCornerShape(8.dp))
                                        .clickable {
                                            scope.launch {
                                                val r = runCatching { Api.authedRpc("xr_mvp_vote", JSONObject().put("p_match", m.s("id")).put("p_entry", c.s("entry_id"))) }.getOrNull()
                                                if (r != null && r.optBoolean("ok")) { msg = "Vote counted for " + c.s("player_name") + "."; tick++ }
                                                else msg = r?.s("error")?.ifBlank { "Vote failed." } ?: "Vote failed."
                                            }
                                        }.padding(horizontal = 12.dp, vertical = 6.dp))
                            }
                        }
                    }
                    if (myVote != null) item { Text("Your vote is locked in - one vote per player.", color = Color(0xFF4ADE80), fontSize = 12.sp, modifier = Modifier.padding(horizontal = 8.dp)) }
                    msg?.let { item { Text(it, color = Muted, fontSize = 12.sp, modifier = Modifier.padding(horizontal = 8.dp)) } }
                }
            }
        }
    }
}

@Composable
fun LiveMatchScreen(onBack: () -> Unit, onVote: () -> Unit) {
    val scope = rememberCoroutineScope()
    var match by remember { mutableStateOf<JSONObject?>(null) }
    var noMatch by remember { mutableStateOf(false) }
    val events = remember { mutableStateListOf<JSONObject>() }
    var lastId by remember { mutableLongStateOf(0L) }
    var canPost by remember { mutableStateOf(false) }
    var killer by remember { mutableStateOf("") }
    var victim by remember { mutableStateOf("") }
    var weapon by remember { mutableStateOf("") }
    var postMsg by remember { mutableStateOf<String?>(null) }
    LaunchedEffect(Unit) {
        val ms = runCatching { Api.publicGet("/rest/v1/matches_public?select=id,title,starts_at,status,tournament_id&order=starts_at.desc&limit=8") }.getOrElse { JSONArray() }
        var pick: JSONObject? = null
        for (i in 0 until ms.length()) { val c = ms.getJSONObject(i); if (c.s("status") == "live") { pick = c; break } }
        if (pick == null) for (i in 0 until ms.length()) { val c = ms.getJSONObject(i); if (c.s("status") != "done") { pick = c; break } }
        if (pick == null && ms.length() > 0) pick = ms.getJSONObject(0)
        match = pick
        noMatch = pick == null
        canPost = runCatching { Api.authedRpc("xr_feed_capability", JSONObject()).optBoolean("can_post") }.getOrDefault(false)
        if (pick != null) {
            while (true) {
                runCatching {
                    val r = Api.authedRpc("xr_kill_feed_list", JSONObject().put("p_match", pick!!.s("id")).put("p_after", lastId).put("p_limit", 100))
                    if (r.optBoolean("ok")) {
                        val ev = r.optJSONArray("events")
                        if (ev != null) for (i in 0 until ev.length()) {
                            val e = ev.getJSONObject(i)
                            if (!e.optBoolean("hidden", false)) { events.add(e); lastId = maxOf(lastId, e.optLong("id")) }
                        }
                    }
                }
                delay(5000)
            }
        }
    }
    Column(Modifier.fillMaxSize().background(Ink)) {
        PageHeader("Live Match", "Host-posted kill feed", onBack)
        val m = match
        if (noMatch) { Text("No match scheduled right now.", color = Muted, fontSize = 14.sp, modifier = Modifier.padding(24.dp)); return@Column }
        if (m == null) { Text("Loading...", color = Muted, fontSize = 14.sp, modifier = Modifier.padding(24.dp)); return@Column }
        LazyColumn(contentPadding = PaddingValues(16.dp), verticalArrangement = Arrangement.spacedBy(8.dp)) {
            item {
                PanelCard {
                    Row(verticalAlignment = Alignment.CenterVertically) {
                        Text(m.s("title"), color = Color.White, fontSize = 16.sp, fontWeight = FontWeight.Bold, modifier = Modifier.weight(1f))
                        val live = m.s("status") == "live"
                        Text(if (live) "LIVE" else m.s("status").uppercase(), color = if (live) Color(0xFFF87171) else Muted,
                            fontSize = 11.sp, fontWeight = FontWeight.Bold,
                            modifier = Modifier.clip(RoundedCornerShape(6.dp)).border(1.dp, if (live) Color(0xFFF87171) else Muted, RoundedCornerShape(6.dp)).padding(horizontal = 8.dp, vertical = 3.dp))
                    }
                    Text(m.s("tournament_id") + " - " + xrMatchWhen(m.s("starts_at")), color = Muted, fontSize = 12.sp)
                }
            }
            if (m.s("status") == "done") item {
                GoldButton("VOTE FOR MVP", onClick = onVote)
            }
            if (canPost) item {
                PanelCard {
                    Text("POST KILL (HOST)", color = Muted, fontSize = 11.sp, fontWeight = FontWeight.Bold)
                    XrField("Killer IGN", killer) { killer = it }
                    XrField("Victim IGN", victim) { victim = it }
                    XrField("Weapon (optional)", weapon) { weapon = it }
                    GoldButton("POST", enabled = killer.isNotBlank() && victim.isNotBlank()) {
                        scope.launch {
                            val r = runCatching {
                                Api.authedRpc("xr_kill_feed_post", JSONObject().put("p_match", m.s("id")).put("p_killer", killer.trim()).put("p_victim", victim.trim())
                                    .put("p_weapon", if (weapon.isBlank()) JSONObject.NULL else weapon.trim()))
                            }.getOrNull()
                            if (r != null && r.optBoolean("ok")) { postMsg = "Posted."; killer = ""; victim = ""; weapon = "" } else postMsg = r?.s("error") ?: "Post failed."
                        }
                    }
                    postMsg?.let { Text(it, color = Muted, fontSize = 11.sp) }
                }
            }
            item {
                Text("KILL FEED", color = Muted, fontSize = 11.sp, fontWeight = FontWeight.Bold, modifier = Modifier.padding(horizontal = 8.dp))
                if (events.isEmpty()) Text("No kills posted yet - the host posts them here as they happen.", color = Muted, fontSize = 12.sp, modifier = Modifier.padding(horizontal = 8.dp))
            }
            items(events.size) { i ->
                val e = events[i]
                Row(Modifier.fillMaxWidth().clip(RoundedCornerShape(8.dp)).background(Look.cardBg).padding(horizontal = 10.dp, vertical = 8.dp),
                    verticalAlignment = Alignment.CenterVertically) {
                    Text(e.s("killer"), color = Color(0xFF4ADE80), fontSize = 13.sp, fontWeight = FontWeight.Bold)
                    Text("  eliminated  ", color = Muted, fontSize = 12.sp)
                    Text(e.s("victim"), color = Color(0xFFF87171), fontSize = 13.sp, fontWeight = FontWeight.Bold)
                    if (e.s("weapon").isNotBlank()) Text("  (" + e.s("weapon") + ")", color = Muted, fontSize = 11.sp)
                }
            }
            item {
                Text("Live chat is off for now - it switches on in the next update.", color = Muted, fontSize = 11.sp, modifier = Modifier.padding(horizontal = 8.dp, vertical = 6.dp))
            }
        }
    }
}

private fun xrMatchWhen(iso: String): String = try {
    val t = java.time.Instant.parse(iso)
    java.time.format.DateTimeFormatter.ofPattern("EEE d MMM, h:mm a").withZone(java.time.ZoneId.systemDefault()).format(t)
} catch (e: Exception) { iso }
