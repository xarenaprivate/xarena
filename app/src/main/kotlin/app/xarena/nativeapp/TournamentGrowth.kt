package app.xarena.nativeapp

import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.shape.RoundedCornerShape
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
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import org.json.JSONArray
import org.json.JSONObject

/* Tournament growth reads (tools-migrations/20261112-tournament-growth-README.md).
   Every growth line is gated on the per-tournament enabled flag: while growth is
   disabled these render NOTHING, so players see only canonical registration state.
   Counts are registered entries, never "played". No online count, no reminder
   promise, no coin claim, no fee exists anywhere here - entry is always free. */
internal object TournamentOverviewCache {
    var atMs: Long = 0
    var byId: HashMap<String, JSONObject> = HashMap()
}

internal fun loadTournamentOverview(force: Boolean = false): HashMap<String, JSONObject>? {
    if (!Api.loggedIn) return null
    if (!force && System.currentTimeMillis() - TournamentOverviewCache.atMs < 30_000 && TournamentOverviewCache.byId.isNotEmpty()) return TournamentOverviewCache.byId
    val r = runCatching { Api.authedRpc("xr_tournament_overview", JSONObject()) }.getOrNull() ?: return null
    if (!r.optBoolean("ok")) return null
    val arr = r.optJSONArray("tournaments") ?: return null
    val map = HashMap<String, JSONObject>()
    for (i in 0 until arr.length()) {
        val t = arr.optJSONObject(i) ?: continue
        if (t.s("id").isNotBlank()) map[t.s("id")] = t
    }
    TournamentOverviewCache.byId = map
    TournamentOverviewCache.atMs = System.currentTimeMillis()
    return map
}

internal fun tournamentSeasonSummary(id: String): JSONObject? {
    val r = runCatching { Api.authedRpc("xr_tournament_season_summary", JSONObject().put("p_tournament", id)) }.getOrNull() ?: return null
    return if (r.optBoolean("ok")) r else null
}

private fun isoMs(s: String): Long? = runCatching { java.time.Instant.parse(s).toEpochMilli() }.getOrNull()

private fun fmtWhen(s: String): String = s.take(16).replace('T', ' ')

/* Real slots line + progress bar from canonical counts. Shows for any enabled
   state - counts are registrations, not growth promotion. */
@Composable
fun TournamentSlotsBlock(ov: JSONObject?, fallbackSlots: Int) {
    if (ov == null) return
    val taken = ov.optInt("slots_taken", -1)
    val total = ov.optInt("slots_total", fallbackSlots)
    if (taken < 0 || total <= 0) return
    Column(Modifier.fillMaxWidth()) {
        Text(if (taken > 0) taken.toString() + " / " + total + " squads registered - " + (total - taken).coerceAtLeast(0) + " left"
            else total.toString() + " slots open - entry free", color = Color.White, fontSize = 13.sp, fontWeight = FontWeight.Bold)
        Spacer(Modifier.height(4.dp))
        Box(Modifier.fillMaxWidth().height(8.dp).clip(RoundedCornerShape(4.dp)).background(Color(0x33FFFFFF))) {
            Box(Modifier.fillMaxWidth((taken.toFloat() / total).coerceIn(0f, 1f)).height(8.dp).clip(RoundedCornerShape(4.dp)).background(Gold))
        }
    }
}

/* Growth lines under a tournament card. Render only when the overview card exists
   AND its enabled flag is on - staged-off growth shows nothing at all. */
@Composable
fun TournamentGrowthLines(ov: JSONObject?) {
    if (ov == null || !ov.optBoolean("enabled")) return
    Column(Modifier.fillMaxWidth()) {
        val closes = ov.s("registration_closes_at")
        val closeMs = if (closes.isNotBlank()) isoMs(closes) else null
        if (closeMs != null) {
            Spacer(Modifier.height(6.dp))
            if (closeMs > System.currentTimeMillis()) { CountdownLine(closeMs); Text("until registration closes", color = Muted, fontSize = 11.sp) }
            else Text("Registration closed", color = Muted, fontSize = 12.sp, fontWeight = FontWeight.Bold)
        }
        val ebTotal = ov.optInt("early_bird_free_total", 0); val ebLeft = ov.optInt("early_bird_free_left", 0)
        if (ebTotal > 0 && ebLeft > 0) { Spacer(Modifier.height(4.dp)); Text("Early Bird: " + ebLeft + " of " + ebTotal + " early-bird places left - entry is always free", color = Gold2, fontSize = 12.sp) }
        val fLeft = ov.optInt("founding_slots_left", 0)
        if (fLeft > 0) { Spacer(Modifier.height(4.dp)); Text("First squads get the FOUNDING SQUAD badge - " + fLeft + " left", color = Gold2, fontSize = 12.sp) }
        val pool = ov.s("prize_pool"); val reveal = ov.s("prize_reveal_at")
        if (pool.isNotBlank()) { Spacer(Modifier.height(4.dp)); Text("Prize pool: " + pool, color = Color.White, fontSize = 13.sp) }
        else if (reveal.isNotBlank()) { Spacer(Modifier.height(4.dp)); Text("Prize pool reveals " + fmtWhen(reveal), color = Muted, fontSize = 12.sp) }
        val mine = ov.optJSONObject("my_registration")
        if (mine != null) {
            Spacer(Modifier.height(6.dp))
            Text("Registered as " + mine.s("squad_name") + (if (mine.optBoolean("is_founding")) " - FOUNDING SQUAD" else "") + (if (mine.optBoolean("checked_in")) " - checked in" else ""), color = Color(0xFF4ADE80), fontSize = 12.sp, fontWeight = FontWeight.Bold)
        }
        val wlRank = ov.optInt("my_waitlist_rank", 0)
        if (wlRank > 0) { Spacer(Modifier.height(4.dp)); Text("You are #" + wlRank + " on the waitlist - a seat opens if a squad drops out", color = Gold2, fontSize = 12.sp) }
        val cities = ov.optJSONArray("city_breakdown")
        if (cities != null && cities.length() > 0) {
            val parts = ArrayList<String>()
            for (i in 0 until minOf(cities.length(), 4)) { val c = cities.optJSONObject(i) ?: continue; parts.add(c.s("city") + " " + c.optInt("squads", c.optInt("count"))) }
            if (parts.isNotEmpty()) { Spacer(Modifier.height(6.dp)); Text("City race: " + parts.joinToString(" - "), color = Gold2, fontSize = 12.sp, fontWeight = FontWeight.Bold) }
        }
        val friends = ov.optJSONArray("friends_registered")
        if (friends != null && friends.length() > 0) {
            val names = ArrayList<String>()
            for (i in 0 until friends.length()) { val f = friends.optJSONObject(i) ?: continue; names.add(f.s("username").ifBlank { f.s("ign") }) }
            val shown = names.filter { it.isNotBlank() }.take(3)
            if (shown.isNotEmpty()) { Spacer(Modifier.height(4.dp)); Text((if (names.size > 3) names.size.toString() + " friends registered: " else "Friends registered: ") + shown.joinToString(", ") + (if (names.size > 3) " +" + (names.size - 3) + " more" else ""), color = Color.White, fontSize = 12.sp) }
        }
        val recent = ov.optJSONArray("recent_registrations")
        if (recent != null && recent.length() > 0) {
            val j0 = recent.optJSONObject(0)
            if (j0 != null && j0.s("squad_name").isNotBlank()) { Spacer(Modifier.height(4.dp)); Text("Latest join: " + j0.s("squad_name"), color = Muted, fontSize = 11.sp) }
        }
        val ci = ov.optInt("checked_in_count", 0)
        if (ci > 0) { Spacer(Modifier.height(4.dp)); Text("Checked in so far: " + ci + " (tournament-wide)", color = Muted, fontSize = 11.sp) }
    }
}

/* Finished-season truth: winner only when the server has an admin-confirmed one;
   squads_count is registrations, never "played". */
@Composable
fun SeasonSummaryLine(tournamentId: String) {
    var summary by remember(tournamentId) { mutableStateOf<JSONObject?>(null) }
    var asked by remember(tournamentId) { mutableStateOf(false) }
    LaunchedEffect(tournamentId) {
        summary = withContext(Dispatchers.IO) { tournamentSeasonSummary(tournamentId) }
        asked = true
    }
    val s = summary ?: return
    val winner = s.s("winner_squad")
    val count = s.optInt("squads_count", 0)
    Column(Modifier.fillMaxWidth()) {
        if (winner.isNotBlank()) Text("Winner: " + winner, color = Gold2, fontSize = 13.sp, fontWeight = FontWeight.Bold)
        if (count > 0) Text(count.toString() + " squads registered", color = Muted, fontSize = 12.sp)
    }
}

/* Sharing consent for the caller's own registration - everything off by default,
   plain explanations, saved through xr_tournament_registration_sharing. */
@Composable
fun TournamentSharingPanel(tournamentId: String) {
    if (tournamentId.isBlank() || !Api.loggedIn) return
    val scope = rememberCoroutineScope()
    var pub by remember { mutableStateOf(false) }
    var friends by remember { mutableStateOf(false) }
    var city by remember { mutableStateOf(false) }
    var busy by remember { mutableStateOf(false) }
    var seeded by remember { mutableStateOf(false) }
    LaunchedEffect(tournamentId) {
        if (!seeded) {
            val mr = withContext(Dispatchers.IO) { loadTournamentOverview()?.get(tournamentId)?.optJSONObject("my_registration") }
            if (mr != null) {
                pub = mr.optBoolean("public_activity")
                friends = mr.optBoolean("friends_visible")
                city = mr.optBoolean("city_visible")
            }
            seeded = true
        }
    }
    fun saveShare(p: Boolean, f: Boolean, c: Boolean) {
        busy = true
        scope.launch(Dispatchers.IO) {
            runCatching { Api.authedRpc("xr_tournament_registration_sharing", JSONObject()
                .put("p_tournament", tournamentId).put("p_public_activity", p)
                .put("p_friends_visible", f).put("p_city_visible", c)) }
            busy = false
        }
    }
    Column(Modifier.fillMaxWidth().padding(top = 10.dp).clip(RoundedCornerShape(10.dp)).border(1.dp, Look.cardBorder, RoundedCornerShape(10.dp)).padding(12.dp)) {
        Text("SHARING - OFF BY DEFAULT", color = Muted, fontSize = 11.sp, fontWeight = FontWeight.Bold)
        Text("Nothing about your registration is shown to other players unless you turn these on.", color = Muted, fontSize = 11.sp)
        Spacer(Modifier.height(6.dp))
        Row(verticalAlignment = Alignment.CenterVertically) { Text("Show my join in the activity feed", color = Color.White, fontSize = 12.sp, modifier = Modifier.weight(1f)); Switch(checked = pub, enabled = !busy, onCheckedChange = { pub = it; saveShare(pub, friends, city) }) }
        Row(verticalAlignment = Alignment.CenterVertically) { Text("Let my friends see I registered", color = Color.White, fontSize = 12.sp, modifier = Modifier.weight(1f)); Switch(checked = friends, enabled = !busy, onCheckedChange = { friends = it; saveShare(pub, friends, city) }) }
        Row(verticalAlignment = Alignment.CenterVertically) { Text("Count my squad for my city", color = Color.White, fontSize = 12.sp, modifier = Modifier.weight(1f)); Switch(checked = city, enabled = !busy, onCheckedChange = { city = it; saveShare(pub, friends, city) }) }
    }
}
