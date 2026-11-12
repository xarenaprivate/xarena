package app.xarena.nativeapp

import androidx.compose.foundation.background
import androidx.compose.foundation.border
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
import kotlinx.coroutines.launch
import org.json.JSONArray
import org.json.JSONObject

/* Squad Finder (v81): role finder, discovery filters, tryouts, invites, IGL tools.
   Backend: xr_sf_* RPCs (contract 12 Nov 2026). REAL data only: Trust Score, level, presence,
   compatibility and ping are shown only when the server provides them; missing = hidden or an
   honest "not rated yet", never a placeholder number. K/D is self-reported and labelled so.
   Presence is player-reported (2-min freshness). SF chat stays behind its server flag. */

internal object SfSeams {
    var players: List<SfPlayer>? = null
    var playerDetail: JSONObject? = null
    var squads: List<SfSquad>? = null
    var squadDetail: JSONObject? = null
    var mySquads: List<SfSquad>? = null
    var invites: JSONArray? = null
    var requests: JSONArray? = null
    var plan: JSONObject? = null
    var rooms: JSONArray? = null
    var roomDetail: JSONObject? = null
    var prefsLoaded = false
    fun clear() { players = null; playerDetail = null; squads = null; squadDetail = null; mySquads = null; invites = null; requests = null; plan = null; rooms = null; roomDetail = null; prefsLoaded = false }
}

internal fun JSONObject.sfBool(key: String): Boolean? = if (has(key) && !isNull(key)) optBoolean(key) else null

/** Player card fields the contract guarantees; anything absent stays hidden. */
internal data class SfPlayer(
    val id: String, val name: String, val avatar: String, val role: String,
    val level: Int?, val verified: Boolean, val trust: Int?,
    val kd: Double?, val kdSelfReported: Boolean,
    val mic: Boolean?, val languages: List<String>, val primaryLanguage: String, val region: String,
    val freeAgent: Boolean, val presence: String, val lastSeen: String
)

internal fun sfPlayerFrom(j: JSONObject): SfPlayer {
    val langs = mutableListOf<String>()
    val la = j.optJSONArray("languages")
    if (la != null) for (i in 0 until la.length()) langs.add(la.optString(i))
    return SfPlayer(
        id = j.s("id"), name = j.s("username").ifBlank { j.s("name") }.ifBlank { "Player" },
        avatar = j.s("avatar"), role = j.s("role"),
        level = if (j.has("level") && !j.isNull("level")) j.optInt("level") else null,
        verified = j.optBoolean("verified", false),
        trust = if (j.has("trust_score") && !j.isNull("trust_score")) j.optInt("trust_score") else null,
        kd = if (j.has("kd") && !j.isNull("kd")) j.optDouble("kd") else null,
        kdSelfReported = j.s("kd_source") == "self_reported",
        mic = j.sfBool("mic"), languages = langs, primaryLanguage = j.s("primary_language"),
        region = j.s("region"), freeAgent = j.optBoolean("free_agent", false),
        presence = j.s("presence"), lastSeen = j.s("last_seen")
    )
}

internal fun presenceColor(p: String): Color = when (p) {
    "online" -> Color(0xFF4ADE80)
    "in_match" -> Color(0xFFFBBF24)
    else -> Color(0xFF6B7280)
}

internal fun presenceLabel(p: String): String = when (p) {
    "online" -> "Online"
    "in_match" -> "In match"
    "offline" -> "Offline"
    else -> "Not shared"
}

@Composable
internal fun SfPlayerCard(p: SfPlayer, onOpen: () -> Unit) {
    PanelCard {
        Row(verticalAlignment = Alignment.CenterVertically, modifier = Modifier.clickable { onOpen() }) {
            Box(Modifier.size(40.dp).clip(RoundedCornerShape(20.dp)).background(Color(0xFF2A1B4E)), contentAlignment = Alignment.Center) {
                Text(p.name.take(1).uppercase(), color = Gold, fontSize = 16.sp, fontWeight = FontWeight.Bold)
            }
            Spacer(Modifier.width(10.dp))
            Column(Modifier.weight(1f)) {
                Row(verticalAlignment = Alignment.CenterVertically) {
                    Text(p.name, color = Color.White, fontSize = 15.sp, fontWeight = FontWeight.Bold)
                    if (p.verified) Text("  Verified", color = Color(0xFF4ADE80), fontSize = 10.sp, fontWeight = FontWeight.Bold)
                }
                val bits = mutableListOf<String>()
                if (p.role.isNotBlank()) bits.add(p.role)
                if (p.region.isNotBlank()) bits.add(p.region)
                if (p.mic == true) bits.add("Mic")
                if (p.primaryLanguage.isNotBlank()) bits.add(p.primaryLanguage)
                if (bits.isNotEmpty()) Text(bits.joinToString(" - "), color = Muted, fontSize = 12.sp)
                Text(if (p.trust != null) "Trust Score " + p.trust + "/100" else "Trust Score not rated yet",
                    color = if (p.trust != null) Gold2 else Muted, fontSize = 11.sp)
                if (p.kd != null) Text("K/D %.1f (self-reported)".format(p.kd), color = Muted, fontSize = 11.sp)
                if (p.freeAgent) Text("Free agent - open to invites", color = Color(0xFF60A5FA), fontSize = 11.sp, fontWeight = FontWeight.Bold)
            }
            Column(horizontalAlignment = Alignment.End) {
                Box(Modifier.size(9.dp).clip(RoundedCornerShape(5.dp)).background(presenceColor(p.presence)))
                Text(presenceLabel(p.presence), color = Muted, fontSize = 10.sp)
            }
        }
    }
}

/* ---------- Root: SF home + internal navigation ---------- */
@Composable
fun SquadFinderRoot(onBack: () -> Unit) {
    var screen by remember { mutableStateOf("home") }
    androidx.activity.compose.BackHandler(screen != "home") { screen = "home" }
    when {
        screen == "players" -> SfPlayerDiscoveryScreen(onBack = { screen = "home" }, onOpenPlayer = { screen = "player:" + it })
        screen.startsWith("player:") -> SfPlayerProfileScreen(screen.removePrefix("player:"), onBack = { screen = "players" })
        screen == "squads" -> SquadDiscoveryScreen(onBack = { screen = "home" }, onOpenSquad = { screen = "squad:" + it })
        screen.startsWith("squad:") -> SquadProfileSfScreen(screen.removePrefix("squad:"), onBack = { screen = "squads" }, onOpenChat = { screen = "chat:" + it })
        screen.startsWith("chat:") -> SquadChatScreen(screen.removePrefix("chat:"), onBack = { screen = "home" })
        screen == "tryouts" -> TryoutRoomsScreen(onBack = { screen = "home" }, onOpenRoom = { screen = "room:" + it })
        screen.startsWith("room:") -> TryoutRoomScreen(screen.removePrefix("room:"), onBack = { screen = "tryouts" })
        screen == "invites" -> InvitesInboxScreen(onBack = { screen = "home" })
        screen == "igl" -> IglDashboardScreen(onBack = { screen = "home" })
        screen == "prefs" -> SfPrefsScreen(onBack = { screen = "home" })
        else -> Column(Modifier.fillMaxSize().background(Ink)) {
            PageHeader("Squad Finder", "Real players, real squads, no fake stats", onBack)
            LazyColumn(contentPadding = PaddingValues(16.dp), verticalArrangement = Arrangement.spacedBy(10.dp)) {
                item { SfEntry("Find Players", "Filter by role, mic, language and Trust Score") { screen = "players" } }
                item { SfEntry("Find a Squad", "Public squads you can join now, private ones by request") { screen = "squads" } }
                item { SfEntry("Tryout Rooms", "Play a trial match before you commit") { screen = "tryouts" } }
                item { SfEntry("My Invites", "Squad invites expire in 30 seconds - act fast") { screen = "invites" } }
                item { SfEntry("IGL Dashboard", "Captain tools: requests, strategy plan, call backup") { screen = "igl" } }
                item { SfEntry("My Finder Profile", "Your role, mic, languages and availability") { screen = "prefs" } }
            }
        }
    }
}

@Composable
private fun SfEntry(title: String, sub: String, onClick: () -> Unit) {
    PanelCard {
        Column(Modifier.fillMaxWidth().clickable { onClick() }) {
            Text(title, color = Color.White, fontSize = 16.sp, fontWeight = FontWeight.Bold)
            Text(sub, color = Muted, fontSize = 12.sp)
        }
    }
}

/* ---------- Player discovery ---------- */
@Composable
fun SfPlayerDiscoveryScreen(onBack: () -> Unit, onOpenPlayer: (String) -> Unit) {
    val scope = rememberCoroutineScope()
    var roles by remember { mutableStateOf(listOf("Rusher", "Support", "Sniper", "IGL")) }
    var role by remember { mutableStateOf<String?>(null) }
    var micOnly by remember { mutableStateOf(false) }
    var language by remember { mutableStateOf("") }
    var minTrust by remember { mutableStateOf(0) }
    var verifiedOnly by remember { mutableStateOf(false) }
    var freeAgentsOnly by remember { mutableStateOf(false) }
    var players by remember { mutableStateOf<List<SfPlayer>?>(SfSeams.players) }
    var err by remember { mutableStateOf<String?>(null) }

    fun load() {
        scope.launch {
            err = null
            val args = JSONObject()
            if (role != null) args.put("p_role", role)
            if (language.isNotBlank()) args.put("p_language", language.trim())
            if (micOnly) args.put("p_mic", true)
            if (minTrust > 0) args.put("p_min_trust", minTrust)
            if (verifiedOnly) args.put("p_verified_only", true)
            if (freeAgentsOnly) args.put("p_free_agents", true)
            val r = runCatching { Api.authedRpc("xr_sf_player_list", args) }.getOrNull()
            if (r != null && r.optBoolean("ok")) {
                val arr = r.optJSONArray("players") ?: JSONArray()
                players = (0 until arr.length()).map { sfPlayerFrom(arr.getJSONObject(it)) }
            } else err = r?.s("error") ?: "Could not load players - pull back and retry."
        }
    }
    LaunchedEffect(Unit) {
        val rc = runCatching { Api.authedRpc("xr_sf_role_catalog", JSONObject()) }.getOrNull()
        val arr = rc?.optJSONArray("roles")
        if (arr != null && arr.length() > 0) roles = (0 until arr.length()).map { arr.getString(it) }
        load()
    }

    Column(Modifier.fillMaxSize().background(Ink)) {
        PageHeader("Find Players", "Every card is a real, opted-in player", onBack)
        Column(Modifier.padding(horizontal = 16.dp)) {
            Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.spacedBy(6.dp)) {
                roles.forEach { r0 ->
                    val on = role == r0
                    Text(r0, color = if (on) Ink else Gold, fontSize = 11.sp, fontWeight = FontWeight.Bold,
                        modifier = Modifier.clip(RoundedCornerShape(8.dp)).background(if (on) Gold else Look.cardBg)
                            .clickable { role = if (on) null else r0; load() }.padding(horizontal = 10.dp, vertical = 6.dp))
                }
            }
            Spacer(Modifier.height(6.dp))
            Row(verticalAlignment = Alignment.CenterVertically) {
                Text("Mic", color = Muted, fontSize = 12.sp); Spacer(Modifier.width(4.dp))
                Switch(checked = micOnly, onCheckedChange = { micOnly = it; load() })
                Spacer(Modifier.width(10.dp))
                Text("Verified only", color = Muted, fontSize = 12.sp); Spacer(Modifier.width(4.dp))
                Switch(checked = verifiedOnly, onCheckedChange = { verifiedOnly = it; load() })
                Spacer(Modifier.width(10.dp))
                Text("Free agents", color = Muted, fontSize = 12.sp); Spacer(Modifier.width(4.dp))
                Switch(checked = freeAgentsOnly, onCheckedChange = { freeAgentsOnly = it; load() })
            }
            Spacer(Modifier.height(6.dp))
            Row(verticalAlignment = Alignment.CenterVertically) {
                OutlinedTextField(language, { language = it }, placeholder = { Text("Language (e.g. Hindi)") }, singleLine = true,
                    modifier = Modifier.weight(1f))
                Spacer(Modifier.width(8.dp))
                Text("APPLY", color = Gold, fontSize = 12.sp, fontWeight = FontWeight.Bold, modifier = Modifier.clickable { load() }.padding(6.dp))
            }
            Spacer(Modifier.height(4.dp))
            Text(if (minTrust > 0) "Min Trust Score: $minTrust (scores appear as players get rated)" else "Trust filter off - most players are not rated yet",
                color = Muted, fontSize = 11.sp, modifier = Modifier.clickable { minTrust = if (minTrust >= 100) 0 else minTrust + 25; load() }.padding(vertical = 4.dp))
        }
        err?.let { Text(it, color = ErrRed, fontSize = 12.sp, modifier = Modifier.padding(16.dp)) }
        val list = players
        LazyColumn(contentPadding = PaddingValues(16.dp), verticalArrangement = Arrangement.spacedBy(10.dp)) {
            if (list == null) item { Text("Loading...", color = Muted, fontSize = 13.sp) }
            else if (list.isEmpty()) item { Text("No players match these filters yet. Loosen a filter or check back later.", color = Muted, fontSize = 13.sp) }
            else items(list) { p -> SfPlayerCard(p) { onOpenPlayer(p.id) } }
        }
    }
}

/* ---------- Player profile (SF view) ---------- */
@Composable
fun SfPlayerProfileScreen(userId: String, onBack: () -> Unit) {
    var data by remember { mutableStateOf<JSONObject?>(SfSeams.playerDetail) }
    var err by remember { mutableStateOf<String?>(null) }
    LaunchedEffect(userId) {
        val r = runCatching { Api.authedRpc("xr_sf_player_get", JSONObject().put("p_user", userId)) }.getOrNull()
        if (r != null && r.optBoolean("ok")) data = r else err = r?.s("error") ?: "Could not load this player."
    }
    Column(Modifier.fillMaxSize().background(Ink)) {
        PageHeader("Player", "Real profile - nothing estimated", onBack)
        val d = data
        if (err != null) Text(err!!, color = ErrRed, fontSize = 13.sp, modifier = Modifier.padding(24.dp))
        else if (d == null) Text("Loading...", color = Muted, fontSize = 13.sp, modifier = Modifier.padding(24.dp))
        else {
            val pj = d.optJSONObject("player")
            if (pj == null) { Text("This player's details are private.", color = Muted, fontSize = 13.sp, modifier = Modifier.padding(24.dp)); return@Column }
            val p = sfPlayerFrom(pj)
            Column(Modifier.fillMaxSize().verticalScroll(rememberScrollState()).padding(16.dp), verticalArrangement = Arrangement.spacedBy(10.dp)) {
                SfPlayerCard(p) {}
                PanelCard {
                    Text("ACTIVITY", color = Gold2, fontSize = 12.sp, fontWeight = FontWeight.Bold)
                    val act = d.optJSONArray("activity")
                    if (act == null || act.length() == 0) Text("No recent activity shared.", color = Muted, fontSize = 12.sp)
                    else for (i in 0 until minOf(act.length(), 10)) {
                        val a0 = act.getJSONObject(i)
                        Text(a0.s("kind") + " +" + a0.optInt("amount") + " XP - " + dmTime(a0.s("at")), color = Color.White, fontSize = 12.sp, modifier = Modifier.padding(vertical = 2.dp))
                    }
                }
                PanelCard {
                    Text("LAST MATCHES", color = Gold2, fontSize = 12.sp, fontWeight = FontWeight.Bold)
                    val lm = d.optJSONArray("last_matches")
                    if (lm == null || lm.length() == 0) Text("No verified matches linked to this account yet.", color = Muted, fontSize = 12.sp)
                    else for (i in 0 until lm.length()) {
                        val m0 = lm.getJSONObject(i)
                        Text(m0.s("title") + " - " + m0.optInt("kills") + " kills" + (if (m0.optBoolean("won")) " - Won" else ""),
                            color = Color.White, fontSize = 12.sp, modifier = Modifier.padding(vertical = 2.dp))
                    }
                    Text("Matches appear only after an admin links real results.", color = Muted, fontSize = 10.sp)
                }
            }
        }
    }
}

/* ---------- My finder profile (prefs + presence) ---------- */
@Composable
fun SfPrefsScreen(onBack: () -> Unit) {
    val scope = rememberCoroutineScope()
    var loaded by remember { mutableStateOf(SfSeams.prefsLoaded) }
    var role by remember { mutableStateOf("") }
    var mic by remember { mutableStateOf<Boolean?>(null) }
    var langs by remember { mutableStateOf("") }
    var primary by remember { mutableStateOf("") }
    var region by remember { mutableStateOf("") }
    var discoverable by remember { mutableStateOf(false) }
    var freeAgent by remember { mutableStateOf(false) }
    var backupOptIn by remember { mutableStateOf(false) }
    var availFrom by remember { mutableStateOf("") }
    var availTo by remember { mutableStateOf("") }
    var msg by remember { mutableStateOf<String?>(null) }
    var roles by remember { mutableStateOf(listOf("Rusher", "Support", "Sniper", "IGL")) }

    LaunchedEffect(Unit) {
        val rc = runCatching { Api.authedRpc("xr_sf_role_catalog", JSONObject()) }.getOrNull()
        val ra = rc?.optJSONArray("roles")
        if (ra != null && ra.length() > 0) roles = (0 until ra.length()).map { ra.getString(it) }
        val r = runCatching { Api.authedRpc("xr_sf_player_preferences", JSONObject()) }.getOrNull()
        val p = r?.optJSONObject("preferences")
        if (p != null) {
            role = p.s("combat_role")
            mic = p.sfBool("mic")
            val la = p.optJSONArray("languages")
            if (la != null) langs = (0 until la.length()).map { la.getString(it) }.joinToString(", ")
            primary = p.s("primary_language"); region = p.s("region")
            discoverable = p.optBoolean("discoverable", false)
            freeAgent = p.optBoolean("free_agent", false)
            backupOptIn = p.optBoolean("backup_opt_in", false)
            availFrom = p.s("available_from"); availTo = p.s("available_to")
        }
        loaded = true
    }

    fun save() {
        scope.launch {
            msg = null
            val patch = JSONObject()
            patch.put("combat_role", if (role.isBlank()) JSONObject.NULL else role)
            patch.put("mic", if (mic == null) JSONObject.NULL else mic)
            val list = langs.split(",").map { it.trim() }.filter { it.isNotBlank() }.toMutableList()
val prim = primary.trim()
if (prim.isNotBlank() && list.none { it.equals(prim, true) }) list.add(0, prim)
            val la = JSONArray(); list.take(8).forEach { la.put(it) }
            patch.put("languages", la)
            patch.put("primary_language", if (primary.isBlank()) JSONObject.NULL else primary.trim())
            patch.put("region", if (region.isBlank()) JSONObject.NULL else region.trim())
            patch.put("discoverable", discoverable)
            patch.put("free_agent", freeAgent)
            patch.put("backup_opt_in", backupOptIn)
            fun localIsoDay(d: String): String { val t = d.trim(); if (t.contains("T")) return t; val off = java.util.TimeZone.getDefault().getOffset(System.currentTimeMillis()); val sign = if (off >= 0) "+" else "-"; val mins = kotlin.math.abs(off) / 60000; return t + "T00:00:00" + sign + (mins / 60).toString().padStart(2, '0') + ":" + (mins % 60).toString().padStart(2, '0') }
if (availFrom.isNotBlank()) patch.put("available_from", localIsoDay(availFrom))
if (availTo.isNotBlank()) patch.put("available_to", localIsoDay(availTo))
            patch.put("availability_timezone", java.util.TimeZone.getDefault().id)
            val rr = runCatching { Api.authedRpc("xr_sf_player_preferences", JSONObject().put("p_patch", patch)) }
val r = rr.getOrNull()
msg = if (r?.optBoolean("ok") == true) "Saved." else (r?.s("error")?.ifBlank { null } ?: rr.exceptionOrNull()?.message ?: "Could not save - try again.")
        }
    }

    Column(Modifier.fillMaxSize().background(Ink)) {
        PageHeader("My Finder Profile", "Only what you choose to share", onBack)
        if (!loaded) { Text("Loading...", color = Muted, fontSize = 13.sp, modifier = Modifier.padding(24.dp)); return@Column }
        Column(Modifier.fillMaxSize().verticalScroll(rememberScrollState()).padding(16.dp), verticalArrangement = Arrangement.spacedBy(10.dp)) {
            PanelCard {
                Text("MY STATUS", color = Gold2, fontSize = 12.sp, fontWeight = FontWeight.Bold)
                Text("Players see this for about 2 minutes after you set it.", color = Muted, fontSize = 11.sp)
                Row(horizontalArrangement = Arrangement.spacedBy(8.dp), modifier = Modifier.padding(top = 6.dp)) {
                    listOf("online" to "Online", "in_match" to "In match", "offline" to "Offline").forEach { (st, label) ->
                        Text(label, color = Ink, fontSize = 12.sp, fontWeight = FontWeight.Bold,
                            modifier = Modifier.clip(RoundedCornerShape(8.dp)).background(Gold).clickable {
                                scope.launch {
                                    val rr = runCatching { Api.authedRpc("xr_sf_presence_set", JSONObject().put("p_state", st)) }
val r = rr.getOrNull()
msg = if (r?.optBoolean("ok") == true) "Status set: " + label else (r?.s("error")?.ifBlank { null } ?: rr.exceptionOrNull()?.message ?: "Could not set status.")
                                }
                            }.padding(horizontal = 12.dp, vertical = 7.dp))
                    }
                }
            }
            PanelCard {
                Text("COMBAT ROLE", color = Gold2, fontSize = 12.sp, fontWeight = FontWeight.Bold)
                Row(horizontalArrangement = Arrangement.spacedBy(6.dp), modifier = Modifier.padding(top = 6.dp)) {
                    roles.forEach { r0 ->
                        val on = role == r0
                        Text(r0, color = if (on) Ink else Gold, fontSize = 12.sp, fontWeight = FontWeight.Bold,
                            modifier = Modifier.clip(RoundedCornerShape(8.dp)).background(if (on) Gold else Look.cardBg)
                                .clickable { role = if (on) "" else r0 }.padding(horizontal = 10.dp, vertical = 6.dp))
                    }
                }
                Spacer(Modifier.height(8.dp))
                Text("Mic", color = Muted, fontSize = 12.sp)
                Row(horizontalArrangement = Arrangement.spacedBy(6.dp)) {
                    listOf(null to "Not set", true to "Have mic", false to "No mic").forEach { (v, label) ->
                        val on = mic == v
                        Text(label, color = if (on) Ink else Gold, fontSize = 12.sp, fontWeight = FontWeight.Bold,
                            modifier = Modifier.clip(RoundedCornerShape(8.dp)).background(if (on) Gold else Look.cardBg)
                                .clickable { mic = v }.padding(horizontal = 10.dp, vertical = 6.dp))
                    }
                }
            }
            PanelCard {
                Text("LANGUAGES AND REGION", color = Gold2, fontSize = 12.sp, fontWeight = FontWeight.Bold)
                OutlinedTextField(langs, { langs = it }, placeholder = { Text("Languages, comma separated (max 8)") }, singleLine = true, modifier = Modifier.fillMaxWidth())
                OutlinedTextField(primary, { primary = it }, placeholder = { Text("Primary language") }, singleLine = true, modifier = Modifier.fillMaxWidth())
                OutlinedTextField(region, { region = it }, placeholder = { Text("Your region/state (you type it)") }, singleLine = true, modifier = Modifier.fillMaxWidth())
            }
            PanelCard {
                Text("DISCOVERY", color = Gold2, fontSize = 12.sp, fontWeight = FontWeight.Bold)
                Row(verticalAlignment = Alignment.CenterVertically) { Text("Show me in Find Players", color = Color.White, fontSize = 13.sp, modifier = Modifier.weight(1f)); Switch(discoverable, { discoverable = it }) }
                Row(verticalAlignment = Alignment.CenterVertically) { Text("Free agent (open to invites, up to 7 days)", color = Color.White, fontSize = 13.sp, modifier = Modifier.weight(1f)); Switch(freeAgent, { freeAgent = it }) }
                Row(verticalAlignment = Alignment.CenterVertically) { Text("Allow backup calls from squads", color = Color.White, fontSize = 13.sp, modifier = Modifier.weight(1f)); Switch(backupOptIn, { backupOptIn = it }) }
                OutlinedTextField(availFrom, { availFrom = it }, placeholder = { Text("Available from (e.g. 2026-11-13T18:00:00+05:30)") }, singleLine = true, modifier = Modifier.fillMaxWidth())
                OutlinedTextField(availTo, { availTo = it }, placeholder = { Text("Available until (optional)") }, singleLine = true, modifier = Modifier.fillMaxWidth())
            }
            GoldButton("SAVE", onClick = { save() })
            msg?.let { Text(it, color = if (it == "Saved." || it.startsWith("Status set")) Color(0xFF4ADE80) else ErrRed, fontSize = 12.sp) }
        }
    }
}
