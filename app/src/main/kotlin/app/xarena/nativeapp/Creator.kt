package app.xarena.nativeapp

/* XARENA Creator Mode (native) - mirrors the web creator.js flows exactly.
   Free-entry events only. No money, no wallet, no paid entry - the DB rejects cash wording.
   Orange creator accent over the app dark theme (the app's own look stays unchanged elsewhere). */

import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import coil.compose.AsyncImage
import kotlinx.coroutines.launch
import org.json.JSONArray
import org.json.JSONObject
import java.text.SimpleDateFormat
import java.util.Date
import java.util.Locale
import java.util.TimeZone

val CrOrange = Color(0xFFFF7A1A)
private val CrOrangeDim = Color(0x55FF7A1A)
private const val CR_NOTE = "Free entry only. No cash, no wallet. Prizes, if any, are arranged by the creator, not XARENA. Follower counts are self-reported by the creator."

fun numCr(n: Long): String = when {
    n >= 1_000_000 -> (n / 1e6).let { if (it == it.toLong().toDouble()) "${it.toLong()}M" else "%.1fM".format(it) }
    n >= 1_000 -> (n / 1e3).let { if (it == it.toLong().toDouble()) "${it.toLong()}K" else "%.1fK".format(it) }
    else -> n.toString()
}

fun fmtCr(iso: String): String {
    if (iso.isBlank()) return ""
    val patterns = arrayOf("yyyy-MM-dd'T'HH:mm:ss.SSSXXX", "yyyy-MM-dd'T'HH:mm:ssXXX", "yyyy-MM-dd'T'HH:mm:ss.SSS'Z'", "yyyy-MM-dd'T'HH:mm:ss'Z'")
    for (p in patterns) {
        try {
            val f = SimpleDateFormat(p, Locale.US)
            if (p.endsWith("'Z'")) f.timeZone = TimeZone.getTimeZone("UTC")
            val d = f.parse(iso) ?: continue
            return SimpleDateFormat("d MMM, hh:mm a", Locale.getDefault()).format(d)
        } catch (e: Exception) { }
    }
    return iso
}

/** Local "yyyy-MM-dd HH:mm" input -> ISO UTC string, or null when unparseable. */
fun localToIso(t: String): String? {
    return try {
        val d = SimpleDateFormat("yyyy-MM-dd HH:mm", Locale.US).parse(t.trim()) ?: return null
        val out = SimpleDateFormat("yyyy-MM-dd'T'HH:mm:ss'Z'", Locale.US)
        out.timeZone = TimeZone.getTimeZone("UTC")
        out.format(d)
    } catch (e: Exception) { null }
}

private fun jsonList(a: JSONArray?): List<JSONObject> =
    if (a == null) emptyList() else (0 until a.length()).map { a.getJSONObject(it) }

@Composable
fun CreatorVerifiedPill(tier: String) {
    Row(verticalAlignment = Alignment.CenterVertically,
        modifier = Modifier.clip(RoundedCornerShape(6.dp)).border(1.dp, CrOrange, RoundedCornerShape(6.dp)).padding(horizontal = 6.dp, vertical = 2.dp)) {
        Text("✓", color = CrOrange, fontSize = 10.sp, fontWeight = FontWeight.Black)
        Spacer(Modifier.width(3.dp))
        Text("VERIFIED CREATOR" + if (tier.isNotBlank()) " · " + tier.uppercase() else "",
            color = CrOrange, fontSize = 9.sp, fontWeight = FontWeight.Bold, letterSpacing = 0.5.sp)
    }
}

@Composable
private fun CrBtn(text: String, accent: Boolean = false, danger: Boolean = false, enabled: Boolean = true, onClick: () -> Unit) {
    val c = if (danger) ErrRed else CrOrange
    Text(text, color = if (accent) Color(0xFF1A0A00) else c, fontSize = 12.sp, fontWeight = FontWeight.Bold,
        modifier = Modifier.clip(RoundedCornerShape(8.dp))
            .background(if (accent) c else Color.Transparent)
            .border(1.dp, c, RoundedCornerShape(8.dp))
            .clickable(enabled = enabled, onClick = onClick)
            .padding(horizontal = 12.dp, vertical = 7.dp))
}

@Composable
private fun CrInput(value: String, onChange: (String) -> Unit, hint: String, number: Boolean = false) {
    OutlinedTextField(value, onChange, placeholder = { Text(hint, fontSize = 13.sp) },
        modifier = Modifier.fillMaxWidth(), singleLine = true,
        keyboardOptions = if (number) androidx.compose.foundation.text.KeyboardOptions(keyboardType = androidx.compose.ui.text.input.KeyboardType.Number) else androidx.compose.foundation.text.KeyboardOptions.Default)
}

/** One creator event card with player or host actions - mirrors web events()/wire(). */
@Composable
private fun CrEventCard(e: JSONObject, mine: Boolean, scope: kotlinx.coroutines.CoroutineScope, onReload: () -> Unit) {
    var msg by remember { mutableStateOf<String?>(null) }
    var roomInfo by remember { mutableStateOf<String?>(null) }
    var players by remember { mutableStateOf<List<JSONObject>?>(null) }
    var dialog by remember { mutableStateOf<String?>(null) } // report/setroom/stream/delay/cancel
    var busy by remember { mutableStateOf(false) }
    val id = e.optLong("id")
    val status = e.s("status")
    val live = e.optBoolean("stream_live")

    fun act(name: String, args: JSONObject, okMsg: String = "Done.", reload: Boolean = true) {
        if (busy) return
        busy = true; msg = null
        scope.launch {
            try {
                val r = Api.authedRpc(name, args)
                msg = if (r.optBoolean("ok")) okMsg else r.s("error").ifBlank { "Failed." }
                if (r.optBoolean("ok") && reload) onReload()
            } catch (ex: Exception) { msg = "Failed - check your connection." }
            busy = false
        }
    }

    Column(Modifier.fillMaxWidth().padding(vertical = 5.dp).clip(RoundedCornerShape(14.dp))
        .border(1.dp, if (live) CrOrange else CrOrangeDim, RoundedCornerShape(14.dp)).background(Card).padding(12.dp)) {
        Row(verticalAlignment = Alignment.CenterVertically) {
            Text(e.s("title"), color = Color.White, fontSize = 15.sp, fontWeight = FontWeight.Bold, modifier = Modifier.weight(1f))
            if (live) Text("LIVE", color = Color(0xFFFF6B5E), fontSize = 10.sp, fontWeight = FontWeight.Black,
                modifier = Modifier.clip(RoundedCornerShape(4.dp)).border(1.dp, Color(0xFFFF6B5E), RoundedCornerShape(4.dp)).padding(horizontal = 5.dp, vertical = 1.dp))
            Spacer(Modifier.width(5.dp))
            Text(status.uppercase(), color = CrOrange, fontSize = 10.sp, fontWeight = FontWeight.Bold,
                modifier = Modifier.clip(RoundedCornerShape(4.dp)).border(1.dp, CrOrangeDim, RoundedCornerShape(4.dp)).padding(horizontal = 5.dp, vertical = 1.dp))
        }
        val host = e.s("host_ign").ifBlank { e.s("host_username") }
        if (!mine && host.isNotBlank()) {
            Spacer(Modifier.height(3.dp))
            Row(verticalAlignment = Alignment.CenterVertically) {
                CreatorVerifiedPill(e.s("host_tier"))
                Spacer(Modifier.width(6.dp)); Text(host, color = CrOrange, fontSize = 11.sp, fontWeight = FontWeight.Bold)
            }
        }
        Spacer(Modifier.height(3.dp))
        Text(listOf(e.s("mode"), e.s("map"), fmtCr(e.s("starts_at"))).filter { it.isNotBlank() }.joinToString(" · ") +
            " · " + (if (e.has("joined")) e.optInt("joined").toString() + "/" else "") + e.optInt("slots") + " players · FREE entry",
            color = Muted, fontSize = 12.sp)
        if (e.s("description").isNotBlank()) { Spacer(Modifier.height(2.dp)); Text(e.s("description"), color = Muted, fontSize = 12.sp) }
        Spacer(Modifier.height(8.dp))
        Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.spacedBy(8.dp)) {
            if (status != "ended" && status != "cancelled") {
                if (mine) {
                    if (status == "scheduled") CrBtn("Start", accent = true, enabled = !busy) { act("creator_event_manage", JSONObject().put("p_event", id).put("p_action", "start")) }
                    else CrBtn("End", accent = true, enabled = !busy) { act("creator_event_manage", JSONObject().put("p_event", id).put("p_action", "end")) }
                    CrBtn(if (players == null) "Players" else "Hide", enabled = !busy) {
                        if (players != null) { players = null; return@CrBtn }
                        scope.launch {
                            try {
                                val r = Api.authedRpc("creator_event_players", JSONObject().put("p_event", id))
                                players = if (r.optBoolean("ok")) jsonList(r.optJSONArray("players")) else null
                                if (!r.optBoolean("ok")) msg = r.s("error").ifBlank { "Failed." }
                            } catch (ex: Exception) { msg = "Failed." }
                        }
                    }
                    CrBtn("Room", enabled = !busy) { dialog = "setroom" }
                } else {
                    if (status == "scheduled") {
                        CrBtn("Join (free)", accent = true, enabled = !busy) { act("creator_event_join", JSONObject().put("p_event", id), "Joined.") }
                        CrBtn("Leave", enabled = !busy) { act("creator_event_leave", JSONObject().put("p_event", id), "Left.") }
                    }
                    CrBtn("Room ID / pass", enabled = !busy) {
                        scope.launch {
                            roomInfo = null; msg = null
                            try {
                                val r = Api.authedRpc("creator_event_room", JSONObject().put("p_event", id))
                                if (r.optBoolean("ok")) roomInfo = "Room ID: " + r.s("room_id") + "   Pass: " + r.s("room_pass").ifBlank { "-" }
                                else msg = r.s("error").ifBlank { "Not available yet - room details unlock 15 min before start." }
                            } catch (ex: Exception) { msg = "Not available yet." }
                        }
                    }
                }
            }
            if (!mine && status != "ended" && status != "cancelled") CrBtn("Report", enabled = !busy) { dialog = "report" }
        }
        if (mine && status != "ended" && status != "cancelled") {
            Spacer(Modifier.height(6.dp))
            Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                CrBtn("Live link", enabled = !busy) { dialog = "stream" }
                CrBtn("Delay", enabled = !busy) { dialog = "delay" }
                CrBtn("Cancel", danger = true, enabled = !busy) { dialog = "cancel" }
            }
        }
        players?.let { list ->
            Spacer(Modifier.height(8.dp))
            if (list.isEmpty()) Text("No players yet.", color = Muted, fontSize = 12.sp)
            list.forEach { p ->
                Row(Modifier.fillMaxWidth().padding(vertical = 2.dp), verticalAlignment = Alignment.CenterVertically) {
                    Text(p.s("name") + " (" + p.s("status") + ")", color = Color.White, fontSize = 12.sp, modifier = Modifier.weight(1f))
                    if (p.s("status") == "joined") {
                        Text("kick", color = CrOrange, fontSize = 11.sp, fontWeight = FontWeight.Bold,
                            modifier = Modifier.clickable { act("creator_event_kick", JSONObject().put("p_event", id).put("p_user", p.s("user_id")).put("p_ban", false), "Kicked.") }.padding(4.dp))
                        Text("ban", color = ErrRed, fontSize = 11.sp, fontWeight = FontWeight.Bold,
                            modifier = Modifier.clickable { act("creator_event_kick", JSONObject().put("p_event", id).put("p_user", p.s("user_id")).put("p_ban", true), "Banned.") }.padding(4.dp))
                    }
                }
            }
        }
        roomInfo?.let { Spacer(Modifier.height(6.dp)); Text(it, color = CrOrange, fontSize = 13.sp, fontWeight = FontWeight.Bold) }
        msg?.let { Spacer(Modifier.height(6.dp)); Text(it, color = if (it == "Done." || it == "Joined." || it == "Left." || it == "Kicked." || it == "Banned.") Color(0xFF22C55E) else ErrRed, fontSize = 12.sp) }
    }

    when (dialog) {
        "report" -> CrOneFieldDialog("Report this event", "What is wrong with this event?", "Send") { v ->
            dialog = null; if (v != null && v.isNotBlank()) act("creator_event_report", JSONObject().put("p_event", id).put("p_reason", v), "Reported. Admin will review.") }
        "setroom" -> CrTwoFieldDialog("Set room", "Room ID", "Room password", "Save") { a, b ->
            dialog = null; if (a != null) act("creator_event_set_room", JSONObject().put("p_event", id).put("p_room_id", a).put("p_room_pass", b ?: "")) }
        "stream" -> CrOneFieldDialog("YouTube live link", "https://... (empty clears)", "Save") { v ->
            dialog = null; if (v != null) act("creator_event_set_stream", JSONObject().put("p_event", id).put("p_url", v).put("p_live", v.isNotBlank())) }
        "delay" -> CrOneFieldDialog("Delay event", "New start (YYYY-MM-DD HH:MM, your time)", "Delay") { v ->
            dialog = null
            if (v != null) {
                val iso = localToIso(v)
                if (iso == null) msg = "Invalid time - use YYYY-MM-DD HH:MM."
                else act("creator_event_manage", JSONObject().put("p_event", id).put("p_action", "delay").put("p_starts", iso))
            } }
        "cancel" -> AlertDialog(onDismissRequest = { dialog = null },
            confirmButton = { Text("CANCEL EVENT", color = ErrRed, fontWeight = FontWeight.Bold, modifier = Modifier.clickable {
                dialog = null; act("creator_event_manage", JSONObject().put("p_event", id).put("p_action", "cancel"), "Cancelled.") }.padding(10.dp)) },
            dismissButton = { Text("Keep", color = Muted, modifier = Modifier.clickable { dialog = null }.padding(10.dp)) },
            title = { Text("Cancel this event?", color = Color.White) }, text = { Text("Players are told it is cancelled. This cannot be undone.", color = Muted) },
            containerColor = Card)
    }
}

@Composable
private fun CrOneFieldDialog(title: String, hint: String, go: String, onDone: (String?) -> Unit) {
    var v by remember { mutableStateOf("") }
    AlertDialog(onDismissRequest = { onDone(null) },
        confirmButton = { Text(go.uppercase(), color = CrOrange, fontWeight = FontWeight.Bold, modifier = Modifier.clickable { onDone(v) }.padding(10.dp)) },
        dismissButton = { Text("Back", color = Muted, modifier = Modifier.clickable { onDone(null) }.padding(10.dp)) },
        title = { Text(title, color = Color.White, fontSize = 16.sp) },
        text = { OutlinedTextField(v, { v = it }, placeholder = { Text(hint, fontSize = 13.sp) }, singleLine = true) },
        containerColor = Card)
}

@Composable
private fun CrTwoFieldDialog(title: String, h1: String, h2: String, go: String, onDone: (String?, String?) -> Unit) {
    var a by remember { mutableStateOf("") }
    var b by remember { mutableStateOf("") }
    AlertDialog(onDismissRequest = { onDone(null, null) },
        confirmButton = { Text(go.uppercase(), color = CrOrange, fontWeight = FontWeight.Bold, modifier = Modifier.clickable { onDone(a, b) }.padding(10.dp)) },
        dismissButton = { Text("Back", color = Muted, modifier = Modifier.clickable { onDone(null, null) }.padding(10.dp)) },
        title = { Text(title, color = Color.White, fontSize = 16.sp) },
        text = { Column { OutlinedTextField(a, { a = it.take(20) }, placeholder = { Text(h1, fontSize = 13.sp) }, singleLine = true)
            Spacer(Modifier.height(8.dp)); OutlinedTextField(b, { b = it.take(20) }, placeholder = { Text(h2, fontSize = 13.sp) }, singleLine = true) } },
        containerColor = Card)
}

@Composable
private fun CrSectionTitle(t: String) {
    Text(t, color = CrOrange, fontSize = 12.sp, fontWeight = FontWeight.Black, letterSpacing = 1.5.sp,
        modifier = Modifier.padding(top = 16.dp, bottom = 6.dp))
}

@Composable
fun CreatorScreen(onBack: () -> Unit) {
    val scope = rememberCoroutineScope()
    var tick by remember { mutableIntStateOf(0) }
    var loaded by remember { mutableStateOf(false) }
    var st by remember { mutableStateOf<JSONObject?>(null) }
    var dash by remember { mutableStateOf<JSONObject?>(null) }
    var events by remember { mutableStateOf<List<JSONObject>>(emptyList()) }
    var squads by remember { mutableStateOf<List<JSONObject>>(emptyList()) }
    var mySquads by remember { mutableStateOf<List<JSONObject>>(emptyList()) }
    var sponsors by remember { mutableStateOf<List<JSONObject>?>(null) }
    var section by remember { mutableStateOf<String?>(null) }
    var profileId by remember { mutableStateOf<String?>(null) }
    val reload: () -> Unit = { tick++ }

    LaunchedEffect(tick) {
        st = if (Api.access != null) Api.creatorStatus() else null
        dash = if (st?.optBoolean("is_creator") == true) Api.creatorDashboard() else null
        events = jsonList(Api.creatorEventsPublic())
        squads = jsonList(Api.citySquadsOpen())
        mySquads = if (st?.optBoolean("is_creator") == true) jsonList(Api.myCitySquads()) else emptyList()
        loaded = true
    }
    LaunchedEffect(section) { if (section == "sponsors" && sponsors == null) sponsors = jsonList(Api.sponsorsPublic()) }

    val isCreator = st?.optBoolean("is_creator") == true
    val appStatus = st?.s("application_status") ?: ""

    Column(Modifier.fillMaxSize().background(Ink)) {
        profileId?.let { pid ->
            CreatorProfileScreen(userId = pid, onBack = { profileId = null })
            return@Column
        }
        PageHeader("Creator", "Host free tournaments - grow your city team", onBack)
        Row(Modifier.padding(horizontal = 14.dp, vertical = 4.dp)) {
            Text("CREATOR BOARD", color = Gold, fontSize = 12.sp, fontWeight = FontWeight.Bold,
                modifier = Modifier.clip(RoundedCornerShape(10.dp)).border(1.dp, Gold, RoundedCornerShape(10.dp))
                    .clickable { section = "board" }.padding(horizontal = 14.dp, vertical = 8.dp))
        }
        if (section == "board") {
            CreatorBoardScreen(onBack = { section = null }, onOpenProfile = { id -> profileId = id })
            return@Column
        }
        if (isCreator && section == "bio") {
            Column(Modifier.padding(14.dp)) {
                CreatorBioEditor(onDone = { err -> section = null })
            }
            return@Column
        }
        if (isCreator) {
            Row(Modifier.padding(horizontal = 14.dp, vertical = 2.dp)) {
                Text("EDIT BIO", color = Gold, fontSize = 12.sp, fontWeight = FontWeight.Bold,
                    modifier = Modifier.clip(RoundedCornerShape(10.dp)).border(1.dp, Gold, RoundedCornerShape(10.dp))
                        .clickable { section = "bio" }.padding(horizontal = 14.dp, vertical = 8.dp))
            }
        }
        if (!loaded) { Box(Modifier.fillMaxSize(), contentAlignment = Alignment.Center) { CircularProgressIndicator(color = CrOrange) }; return@Column }
        LazyColumn(Modifier.fillMaxSize().padding(horizontal = 14.dp)) {
            if (isCreator && dash != null && dash!!.optBoolean("ok", true)) {
                val d = dash!!
                item {
                    Column(Modifier.fillMaxWidth().clip(RoundedCornerShape(16.dp)).border(1.dp, CrOrangeDim, RoundedCornerShape(16.dp)).background(Card).padding(14.dp)) {
                        Row(verticalAlignment = Alignment.CenterVertically) {
                            val nm = d.s("name").ifBlank { d.s("username") }
                            Box(Modifier.size(56.dp).clip(CircleShape).border(2.dp, CrOrange, CircleShape).background(Ink), contentAlignment = Alignment.Center) {
                                val av = d.s("avatar_url")
                                if (av.isNotBlank()) AsyncImage(model = xrImageModel(LocalContext.current, av), contentDescription = nm, modifier = Modifier.fillMaxSize(), contentScale = ContentScale.Crop)
                                else Text(nm.firstOrNull()?.uppercase() ?: "C", color = CrOrange, fontSize = 22.sp, fontWeight = FontWeight.Black)
                            }
                            Spacer(Modifier.width(12.dp))
                            Column {
                                Text(nm, color = Color.White, fontSize = 18.sp, fontWeight = FontWeight.Bold)
                                Spacer(Modifier.height(3.dp))
                                CreatorVerifiedPill(d.s("tier"))
                            }
                        }
                        Spacer(Modifier.height(12.dp))
                        Row(Modifier.fillMaxWidth()) {
                            Column(Modifier.weight(1f), horizontalAlignment = Alignment.CenterHorizontally) {
                                Text(numCr(d.optLong("self_reported_followers")), color = Color.White, fontSize = 18.sp, fontWeight = FontWeight.Black)
                                Text("Followers (self-reported)", color = Muted, fontSize = 10.sp)
                            }
                            Column(Modifier.weight(1f), horizontalAlignment = Alignment.CenterHorizontally) {
                                Text(d.optInt("tournaments_hosted").toString(), color = Color.White, fontSize = 18.sp, fontWeight = FontWeight.Black)
                                Text("Tournaments", color = Muted, fontSize = 10.sp)
                            }
                            Column(Modifier.weight(1f), horizontalAlignment = Alignment.CenterHorizontally) {
                                Text(d.optInt("wins").toString(), color = Color.White, fontSize = 18.sp, fontWeight = FontWeight.Black)
                                Text("Wins", color = Muted, fontSize = 10.sp)
                            }
                        }
                        if (d.s("followers_note").isNotBlank()) { Spacer(Modifier.height(6.dp)); Text(d.s("followers_note"), color = Muted, fontSize = 10.sp) }
                    }
                }
                item {
                    Row(Modifier.fillMaxWidth().padding(top = 8.dp).clip(RoundedCornerShape(12.dp)).border(1.dp, CrOrangeDim, RoundedCornerShape(12.dp)).background(Card).padding(12.dp),
                        verticalAlignment = Alignment.CenterVertically) {
                        Box(Modifier.size(8.dp).clip(CircleShape).background(Color(0xFFFF6B5E)))
                        Spacer(Modifier.width(8.dp))
                        Text("LIVE NOW: " + d.optInt("live_now") + " creator tournament" + (if (d.optInt("live_now") == 1) "" else "s") + " running",
                            color = Color.White, fontSize = 13.sp, fontWeight = FontWeight.Bold)
                    }
                }
                item {
                    CrSectionTitle("QUICK ACTIONS")
                    CreatorActionBtn("Host Free Tournament", "Create and manage your own free-entry event", section == "host") { section = if (section == "host") null else "host" }
                    CreatorActionBtn("Find Sponsors", "Browse partner brands and ask XARENA admin for an intro", section == "sponsors") { section = if (section == "sponsors") null else "sponsors" }
                    CreatorActionBtn("Build My City Team", "Recruit players from your city", section == "city") { section = if (section == "city") null else "city" }
                }
                if (section == "host") item { HostForm(scope, reload) }
                if (section == "sponsors") item { SponsorsPanel(sponsors, scope) }
                if (section == "city") item { CityPanel(mySquads, scope, reload) }
                item {
                    CrSectionTitle("MY TOURNAMENTS")
                    val mine = jsonList(d.optJSONArray("my_events"))
                    if (mine.isEmpty()) Text("You have not hosted yet. Use Host Free Tournament above.", color = Muted, fontSize = 12.sp)
                }
                items(jsonList(d.optJSONArray("my_events"))) { e -> CrEventCard(e, true, scope, reload) }
            } else {
                item {
                    Column(Modifier.fillMaxWidth().clip(RoundedCornerShape(16.dp)).border(1.dp, CrOrangeDim, RoundedCornerShape(16.dp)).background(Card).padding(14.dp)) {
                        when {
                            st == null -> {
                                Text("Log in to apply as a creator or join creator tournaments.", color = Color.White, fontSize = 14.sp)
                            }
                            appStatus == "pending" -> {
                                Text("Application pending.", color = Color.White, fontSize = 15.sp, fontWeight = FontWeight.Bold)
                                Spacer(Modifier.height(4.dp))
                                Text("The admin will review your channel. You will be able to host as soon as it is approved.", color = Muted, fontSize = 13.sp)
                            }
                            else -> ApplyForm(appStatus, scope, reload)
                        }
                    }
                }
            }
            item { CrSectionTitle("CREATOR TOURNAMENTS") }
            if (events.isEmpty()) item { Text("No creator tournaments yet. They appear here when an approved creator schedules one.", color = Muted, fontSize = 12.sp) }
            items(events) { e -> CrEventCard(e, false, scope, reload) }
            item { CrSectionTitle("CITY SQUADS RECRUITING") }
            if (squads.isEmpty()) item { Text("No city squads are recruiting yet.", color = Muted, fontSize = 12.sp) }
            items(squads) { q -> CitySquadCard(q, scope) }
            item {
                Spacer(Modifier.height(10.dp))
                Text(CR_NOTE, color = Muted, fontSize = 11.sp, lineHeight = 15.sp, modifier = Modifier.padding(bottom = 20.dp))
            }
        }
    }
}

@Composable
private fun CreatorActionBtn(title: String, sub: String, open: Boolean, onClick: () -> Unit) {
    Column(Modifier.fillMaxWidth().padding(vertical = 4.dp).clip(RoundedCornerShape(12.dp))
        .border(1.dp, if (open) CrOrange else CrOrangeDim, RoundedCornerShape(12.dp)).background(Card)
        .clickable(onClick = onClick).padding(13.dp)) {
        Text(title, color = if (open) CrOrange else Color.White, fontSize = 15.sp, fontWeight = FontWeight.Bold)
        Text(sub, color = Muted, fontSize = 11.sp)
    }
}

@Composable
private fun ApplyForm(appStatus: String, scope: kotlinx.coroutines.CoroutineScope, onReload: () -> Unit) {
    var channel by remember { mutableStateOf("") }
    var followers by remember { mutableStateOf("") }
    var note by remember { mutableStateOf("") }
    var msg by remember { mutableStateOf<String?>(null) }
    var busy by remember { mutableStateOf(false) }
    Text("BECOME A VERIFIED CREATOR", color = CrOrange, fontSize = 13.sp, fontWeight = FontWeight.Black, letterSpacing = 1.5.sp)
    if (appStatus == "rejected" || appStatus == "revoked") {
        Spacer(Modifier.height(4.dp)); Text("Your last application was $appStatus. You can apply again.", color = ErrRed, fontSize = 12.sp)
    }
    Spacer(Modifier.height(8.dp))
    CrInput(channel, { channel = it }, "YouTube or Instagram channel link (https://...)")
    Spacer(Modifier.height(8.dp))
    CrInput(followers, { followers = it.filter { c -> c.isDigit() }.take(9) }, "Followers (self-reported)", number = true)
    Spacer(Modifier.height(8.dp))
    CrInput(note, { note = it.take(300) }, "Why do you want to host? (optional)")
    Spacer(Modifier.height(10.dp))
    CrBtn("Apply", accent = true, enabled = !busy && channel.isNotBlank()) {
        busy = true; msg = "Sending..."
        scope.launch {
            try {
                val r = Api.authedRpc("creator_apply", JSONObject().put("p_channel", channel.trim())
                    .put("p_followers", followers.toLongOrNull() ?: 0L).put("p_note", note.trim()))
                if (r.optBoolean("ok")) onReload() else { msg = r.s("error").ifBlank { "Failed." }; busy = false }
            } catch (e: Exception) { msg = "Failed - check your connection."; busy = false }
        }
    }
    msg?.let { Spacer(Modifier.height(6.dp)); Text(it, color = CrOrange, fontSize = 12.sp) }
}

@Composable
private fun HostForm(scope: kotlinx.coroutines.CoroutineScope, onReload: () -> Unit) {
    var title by remember { mutableStateOf("") }
    var mode by remember { mutableStateOf("Squad") }
    var map by remember { mutableStateOf("Bermuda") }
    var starts by remember { mutableStateOf(run {
        val f = SimpleDateFormat("yyyy-MM-dd HH:mm", Locale.US); f.format(Date(System.currentTimeMillis() + 3 * 3600_000))
    }) }
    var slots by remember { mutableStateOf("48") }
    var desc by remember { mutableStateOf("") }
    var roomId by remember { mutableStateOf("") }
    var roomPass by remember { mutableStateOf("") }
    var msg by remember { mutableStateOf<String?>(null) }
    var busy by remember { mutableStateOf(false) }

    Column(Modifier.fillMaxWidth().padding(vertical = 6.dp).clip(RoundedCornerShape(14.dp)).border(1.dp, CrOrangeDim, RoundedCornerShape(14.dp)).background(Card).padding(12.dp)) {
        Text("HOST A FREE TOURNAMENT", color = CrOrange, fontSize = 12.sp, fontWeight = FontWeight.Black, letterSpacing = 1.5.sp)
        Spacer(Modifier.height(8.dp))
        CrInput(title, { title = it.take(80) }, "Title")
        Spacer(Modifier.height(8.dp))
        Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
            listOf("Solo", "Duo", "Squad").forEach { m ->
                Text(m, color = if (mode == m) Color(0xFF1A0A00) else CrOrange, fontSize = 12.sp, fontWeight = FontWeight.Bold,
                    modifier = Modifier.clip(RoundedCornerShape(8.dp)).background(if (mode == m) CrOrange else Color.Transparent)
                        .border(1.dp, CrOrange, RoundedCornerShape(8.dp)).clickable { mode = m }.padding(horizontal = 14.dp, vertical = 7.dp))
            }
        }
        Spacer(Modifier.height(8.dp))
        CrInput(map, { map = it.take(30) }, "Map")
        Spacer(Modifier.height(8.dp))
        CrInput(starts, { starts = it.take(16) }, "Start (YYYY-MM-DD HH:MM, your time)")
        Spacer(Modifier.height(8.dp))
        CrInput(slots, { slots = it.filter { c -> c.isDigit() }.take(3) }, "Slots (2-100)", number = true)
        Spacer(Modifier.height(8.dp))
        CrInput(desc, { desc = it.take(400) }, "Short description (optional)")
        Spacer(Modifier.height(8.dp))
        CrInput(roomId, { roomId = it.take(20) }, "Room ID (can add later)")
        Spacer(Modifier.height(8.dp))
        CrInput(roomPass, { roomPass = it.take(20) }, "Room password (can add later)")
        Spacer(Modifier.height(10.dp))
        CrBtn("Create free event", accent = true, enabled = !busy && title.isNotBlank()) {
            val iso = localToIso(starts)
            if (iso == null) { msg = "Invalid start time - use YYYY-MM-DD HH:MM."; return@CrBtn }
            val n = slots.toIntOrNull() ?: 48
            if (n < 2 || n > 100) { msg = "Slots must be 2 to 100."; return@CrBtn }
            busy = true; msg = "Saving..."
            scope.launch {
                try {
                    val r = Api.authedRpc("creator_event_create", JSONObject().put("p_title", title.trim()).put("p_mode", mode)
                        .put("p_map", map.trim()).put("p_starts", iso).put("p_slots", n)
                        .put("p_desc", desc.trim()).put("p_room_id", roomId.trim()).put("p_room_pass", roomPass.trim()))
                    msg = if (r.optBoolean("ok")) "Event created." else r.s("error").ifBlank { "Failed." }
                    if (r.optBoolean("ok")) onReload()
                } catch (e: Exception) { msg = "Failed - check your connection." }
                busy = false
            }
        }
        msg?.let { Spacer(Modifier.height(6.dp)); Text(it, color = if (it == "Event created.") Color(0xFF22C55E) else CrOrange, fontSize = 12.sp) }
    }
}

@Composable
private fun SponsorsPanel(sponsors: List<JSONObject>?, scope: kotlinx.coroutines.CoroutineScope) {
    var dialogFor by remember { mutableStateOf<JSONObject?>(null) }
    var pitchMsg by remember { mutableStateOf<Pair<Long, String>?>(null) }
    Column(Modifier.fillMaxWidth().padding(vertical = 6.dp).clip(RoundedCornerShape(14.dp)).border(1.dp, CrOrangeDim, RoundedCornerShape(14.dp)).background(Card).padding(12.dp)) {
        Text("PARTNER BRANDS", color = CrOrange, fontSize = 12.sp, fontWeight = FontWeight.Black, letterSpacing = 1.5.sp)
        Spacer(Modifier.height(6.dp))
        when {
            sponsors == null -> CircularProgressIndicator(color = CrOrange, modifier = Modifier.size(24.dp))
            sponsors.isEmpty() -> Text("No partner brands listed yet.", color = Muted, fontSize = 12.sp)
            else -> sponsors.forEach { sp ->
                Row(Modifier.fillMaxWidth().padding(vertical = 6.dp), verticalAlignment = Alignment.CenterVertically) {
                    val img = sp.s("image_url")
                    if (img.isNotBlank()) AsyncImage(model = xrImageModel(LocalContext.current, img), contentDescription = sp.s("name"),
                        modifier = Modifier.size(28.dp).clip(RoundedCornerShape(6.dp)), contentScale = ContentScale.Fit)
                    Spacer(Modifier.width(8.dp))
                    Text(sp.s("name"), color = Color.White, fontSize = 14.sp, fontWeight = FontWeight.Bold, modifier = Modifier.weight(1f))
                    Text(sp.s("tier").uppercase(), color = CrOrange, fontSize = 10.sp, fontWeight = FontWeight.Bold,
                        modifier = Modifier.clip(RoundedCornerShape(4.dp)).border(1.dp, CrOrangeDim, RoundedCornerShape(4.dp)).padding(horizontal = 5.dp, vertical = 1.dp))
                    Spacer(Modifier.width(8.dp))
                    CrBtn("Request intro") { dialogFor = sp }
                }
                pitchMsg?.takeIf { it.first == sp.optLong("id") }?.let { Text(it.second, color = CrOrange, fontSize = 11.sp) }
            }
        }
        Spacer(Modifier.height(4.dp))
        Text("Send a short pitch. XARENA admin reviews it and introduces you. No contact details are shared automatically.", color = Muted, fontSize = 11.sp, lineHeight = 15.sp)
    }
    dialogFor?.let { sp ->
        CrOneFieldDialog("Pitch " + sp.s("name"), "Short pitch (10-400 characters, no phone numbers or money wording)", "Send") { v ->
            dialogFor = null
            if (v != null) {
                if (v.trim().length < 10 || v.trim().length > 400) { pitchMsg = sp.optLong("id") to "Pitch must be 10-400 characters."; return@CrOneFieldDialog }
                scope.launch {
                    pitchMsg = try {
                        val r = Api.authedRpc("creator_sponsor_request", JSONObject().put("p_sponsor", sp.optLong("id")).put("p_pitch", v.trim()))
                        sp.optLong("id") to (if (r.optBoolean("ok")) "Sent. Admin will review." else r.s("error").ifBlank { "Failed." })
                    } catch (e: Exception) { sp.optLong("id") to "Failed - check your connection." }
                }
            }
        }
    }
}

@Composable
private fun CityPanel(mySquads: List<JSONObject>, scope: kotlinx.coroutines.CoroutineScope, onReload: () -> Unit) {
    var name by remember { mutableStateOf("") }
    var city by remember { mutableStateOf("") }
    var state by remember { mutableStateOf("") }
    var desc by remember { mutableStateOf("") }
    var slots by remember { mutableStateOf("4") }
    var msg by remember { mutableStateOf<String?>(null) }
    var busy by remember { mutableStateOf(false) }

    Column(Modifier.fillMaxWidth().padding(vertical = 6.dp).clip(RoundedCornerShape(14.dp)).border(1.dp, CrOrangeDim, RoundedCornerShape(14.dp)).background(Card).padding(12.dp)) {
        Text("BUILD MY CITY TEAM", color = CrOrange, fontSize = 12.sp, fontWeight = FontWeight.Black, letterSpacing = 1.5.sp)
        Spacer(Modifier.height(8.dp))
        CrInput(name, { name = it.take(40) }, "Squad name")
        Spacer(Modifier.height(8.dp))
        CrInput(city, { city = it.take(40) }, "City")
        Spacer(Modifier.height(8.dp))
        CrInput(state, { state = it.take(40) }, "State")
        Spacer(Modifier.height(8.dp))
        CrInput(desc, { desc = it.take(300) }, "Who are you looking for?")
        Spacer(Modifier.height(8.dp))
        CrInput(slots, { slots = it.filter { c -> c.isDigit() }.take(2) }, "Open slots (1-50)", number = true)
        Spacer(Modifier.height(10.dp))
        CrBtn("Open recruiting", accent = true, enabled = !busy && name.isNotBlank() && city.isNotBlank()) {
            val n = slots.toIntOrNull() ?: 4
            if (n < 1 || n > 50) { msg = "Slots must be 1 to 50."; return@CrBtn }
            busy = true; msg = null
            scope.launch {
                try {
                    val r = Api.authedRpc("city_squad_create", JSONObject().put("p_name", name.trim()).put("p_city", city.trim())
                        .put("p_state", state.trim()).put("p_desc", desc.trim()).put("p_slots", n))
                    msg = if (r.optBoolean("ok")) "Recruiting is open." else r.s("error").ifBlank { "Failed." }
                    if (r.optBoolean("ok")) onReload()
                } catch (e: Exception) { msg = "Failed - check your connection." }
                busy = false
            }
        }
        msg?.let { Spacer(Modifier.height(6.dp)); Text(it, color = if (it == "Recruiting is open.") Color(0xFF22C55E) else CrOrange, fontSize = 12.sp) }
        if (mySquads.isNotEmpty()) {
            Spacer(Modifier.height(10.dp))
            Text("MY CITY SQUADS", color = CrOrange, fontSize = 12.sp, fontWeight = FontWeight.Black, letterSpacing = 1.5.sp)
            mySquads.forEach { q -> MySquadRow(q, scope, onReload) }
        }
        Spacer(Modifier.height(4.dp))
        Text("Applicants show in-game stats only. No phone numbers or IDs are shared. No money, salary or fees.", color = Muted, fontSize = 11.sp, lineHeight = 15.sp)
    }
}

@Composable
private fun MySquadRow(q: JSONObject, scope: kotlinx.coroutines.CoroutineScope, onReload: () -> Unit) {
    var applicants by remember { mutableStateOf<List<JSONObject>?>(null) }
    var msg by remember { mutableStateOf<String?>(null) }
    val id = q.optLong("id")
    Column(Modifier.fillMaxWidth().padding(vertical = 5.dp).clip(RoundedCornerShape(10.dp)).border(1.dp, CrOrangeDim, RoundedCornerShape(10.dp)).padding(10.dp)) {
        Row(verticalAlignment = Alignment.CenterVertically) {
            Text(q.s("name"), color = Color.White, fontSize = 14.sp, fontWeight = FontWeight.Bold, modifier = Modifier.weight(1f))
            Text(q.s("status").uppercase(), color = CrOrange, fontSize = 10.sp, fontWeight = FontWeight.Bold,
                modifier = Modifier.clip(RoundedCornerShape(4.dp)).border(1.dp, CrOrangeDim, RoundedCornerShape(4.dp)).padding(horizontal = 5.dp, vertical = 1.dp))
        }
        Text(q.s("city") + " · " + q.optInt("members") + "/" + q.optInt("slots") + " members", color = Muted, fontSize = 12.sp)
        Spacer(Modifier.height(6.dp))
        Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
            CrBtn(if (applicants == null) "Applicants" else "Hide") {
                if (applicants != null) { applicants = null; return@CrBtn }
                scope.launch {
                    try {
                        val r = Api.authedRpc("city_squad_applicants", JSONObject().put("p_squad", id))
                        applicants = if (r.optBoolean("ok")) jsonList(r.optJSONArray("applicants")) else null
                        if (!r.optBoolean("ok")) msg = r.s("error").ifBlank { "Failed." }
                    } catch (e: Exception) { msg = "Failed." }
                }
            }
            if (q.s("status") == "open") CrBtn("Close", danger = true) {
                scope.launch { runCatching { Api.authedRpc("city_squad_close", JSONObject().put("p_squad", id)) }; onReload() }
            }
        }
        applicants?.let { list ->
            Spacer(Modifier.height(6.dp))
            if (list.isEmpty()) Text("No applicants yet.", color = Muted, fontSize = 12.sp)
            list.forEach { a ->
                Column(Modifier.fillMaxWidth().padding(vertical = 3.dp)) {
                    Row(verticalAlignment = Alignment.CenterVertically) {
                        Text(a.s("name") + " · K/D " + (if (a.has("kd") && !a.isNull("kd")) a.optString("kd") else "-") + " · " + a.optInt("matches") + " matches · " + a.optInt("wins") + " wins" +
                            (if (a.s("rank").isNotBlank()) " · " + a.s("rank") else "") + " (" + a.s("status") + ")",
                            color = Color.White, fontSize = 12.sp, modifier = Modifier.weight(1f))
                        if (a.s("status") == "pending") {
                            Text("accept", color = Color(0xFF22C55E), fontSize = 11.sp, fontWeight = FontWeight.Bold,
                                modifier = Modifier.clickable {
                                    scope.launch { runCatching { Api.authedRpc("city_squad_decide", JSONObject().put("p_app", a.optLong("id")).put("p_accept", true)) }
                                        applicants = null }
                                }.padding(4.dp))
                            Text("reject", color = ErrRed, fontSize = 11.sp, fontWeight = FontWeight.Bold,
                                modifier = Modifier.clickable {
                                    scope.launch { runCatching { Api.authedRpc("city_squad_decide", JSONObject().put("p_app", a.optLong("id")).put("p_accept", false)) }
                                        applicants = null }
                                }.padding(4.dp))
                        }
                    }
                    if (a.s("note").isNotBlank()) Text(a.s("note"), color = Muted, fontSize = 11.sp)
                }
            }
        }
        msg?.let { Text(it, color = ErrRed, fontSize = 11.sp) }
    }
}

@Composable
private fun CitySquadCard(q: JSONObject, scope: kotlinx.coroutines.CoroutineScope) {
    var msg by remember { mutableStateOf<String?>(null) }
    var dialog by remember { mutableStateOf(false) }
    Column(Modifier.fillMaxWidth().padding(vertical = 5.dp).clip(RoundedCornerShape(14.dp)).border(1.dp, CrOrangeDim, RoundedCornerShape(14.dp)).background(Card).padding(12.dp)) {
        Text(q.s("name"), color = Color.White, fontSize = 15.sp, fontWeight = FontWeight.Bold)
        Spacer(Modifier.height(3.dp))
        Row(verticalAlignment = Alignment.CenterVertically) {
            CreatorVerifiedPill("")
            Spacer(Modifier.width(6.dp))
            Text(q.s("host_ign").ifBlank { q.s("host_username") } + " · " + q.s("city") + (if (q.s("state").isNotBlank()) ", " + q.s("state") else "") +
                " · " + q.optInt("members") + "/" + q.optInt("slots") + " members", color = Muted, fontSize = 12.sp)
        }
        if (q.s("description").isNotBlank()) { Spacer(Modifier.height(2.dp)); Text(q.s("description"), color = Muted, fontSize = 12.sp) }
        Spacer(Modifier.height(8.dp))
        CrBtn("Apply", accent = true) { dialog = true }
        msg?.let { Spacer(Modifier.height(6.dp)); Text(it, color = CrOrange, fontSize = 12.sp) }
    }
    if (dialog) CrOneFieldDialog("Apply to " + q.s("name"), "Short note: your role, experience (no phone numbers)", "Apply") { v ->
        dialog = false
        if (v != null) scope.launch {
            try {
                val r = Api.authedRpc("city_squad_apply", JSONObject().put("p_squad", q.optLong("id")).put("p_note", v))
                msg = if (r.optBoolean("ok")) "Applied. The host will review." else r.s("error").ifBlank { "Failed." }
            } catch (e: Exception) { msg = "Failed - check your connection." }
        }
    }
}
