package app.xarena.nativeapp

import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.shape.RoundedCornerShape
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

/* Arena Live (v82): one screen for the live-match experience - fan poll, host-posted kill feed,
   app-only live comments and the clip of the week. Every card wires an EXISTING RPC
   (active_polls/poll_results/cast_vote, xr_kill_feed_list, xr_live_thread_*, weekly_top_clip).
   Honest states everywhere: no poll -> says so, no kills -> says the host has not posted,
   comments flag off -> LiveComments renders its own disabled note. Nothing is simulated. */

internal object AlSeams {
    var match: JSONObject? = null
    var noMatch = false
    var poll: JSONObject? = null
    var pollResults: JSONObject? = null
    var kills: JSONArray? = null
    var clip: JSONObject? = null
    fun clear() { match = null; noMatch = false; poll = null; pollResults = null; kills = null; clip = null }
}

/** Live match wins; otherwise the soonest upcoming one. Postponed matches never count. */
private fun alPickMatch(ms: JSONArray): JSONObject? {
    var next: JSONObject? = null
    val now = System.currentTimeMillis()
    for (i in 0 until ms.length()) {
        val m = ms.getJSONObject(i)
        if (MatchLabels.isPostponed(m)) continue
        if (m.s("status") == "live") return m
        val st = runCatching { java.time.Instant.parse(m.s("starts_at")).toEpochMilli() }.getOrDefault(Long.MAX_VALUE)
        if (st != Long.MAX_VALUE && st > now && next == null) next = m
    }
    return next
}

@Composable
fun ArenaLiveScreen(onBack: () -> Unit, onOpenMvp: () -> Unit, onOpenFeed: () -> Unit) {
    var match by remember { mutableStateOf<JSONObject?>(AlSeams.match) }
    var noMatch by remember { mutableStateOf(AlSeams.noMatch) }
    var poll by remember { mutableStateOf<JSONObject?>(AlSeams.poll) }
    var pollRes by remember { mutableStateOf<JSONObject?>(AlSeams.pollResults) }
    var kills by remember { mutableStateOf<JSONArray?>(AlSeams.kills) }
    var clip by remember { mutableStateOf<JSONObject?>(AlSeams.clip) }
    var feedTick by remember { mutableIntStateOf(0) }

    LaunchedEffect(Unit) {
        val ms = runCatching { Api.publicGet("/rest/v1/matches_public?select=*&order=starts_at.asc.nullslast") }.getOrNull()
        val m = if (ms == null) null else alPickMatch(ms)
        if (m == null) { noMatch = true; return@LaunchedEffect }
        match = m
        val body = runCatching { Api.authedRpcRaw("active_polls", JSONObject()).trim() }.getOrNull()
        if (!body.isNullOrBlank()) {
            val polls = if (body.startsWith("[")) JSONArray(body) else runCatching { JSONObject(body).optJSONArray("polls") }.getOrNull() ?: JSONArray()
            if (polls.length() > 0) {
                val p = polls.getJSONObject(0)
                poll = p
                pollRes = runCatching { Api.authedRpc("poll_results", JSONObject().put("p_poll", p.s("id"))) }.getOrNull()
            }
        }
        clip = runCatching { Api.publicRpc("weekly_top_clip", JSONObject()).optJSONObject("clip") }.getOrNull()
    }

    val mid = match?.s("id") ?: ""
    LaunchedEffect(mid, feedTick) {
        if (mid.isBlank()) return@LaunchedEffect
        val r = runCatching { Api.authedRpc("xr_kill_feed_list", JSONObject().put("p_match", mid).put("p_after", 0).put("p_limit", 100)) }.getOrNull()
        if (r != null && r.optBoolean("ok")) kills = r.optJSONArray("events") ?: JSONArray()
        if (match?.s("status") == "live") { delay(5000); feedTick++ }
    }

    Column(Modifier.fillMaxSize().background(Ink)) {
        PageHeader("Arena Live", "Poll, kill feed and comments - all real, all live", onBack)
        if (noMatch) {
            Text("No match is scheduled right now. This screen lights up as soon as match control publishes the next one.",
                color = Muted, fontSize = 14.sp, modifier = Modifier.padding(24.dp))
            return@Column
        }
        LazyColumn(contentPadding = PaddingValues(16.dp), verticalArrangement = Arrangement.spacedBy(10.dp)) {
            item {
                PanelCard {
                    val live = match?.s("status") == "live"
                    Row(verticalAlignment = Alignment.CenterVertically) {
                        Text(match?.s("title") ?: "", color = Color.White, fontSize = 16.sp, fontWeight = FontWeight.Bold, modifier = Modifier.weight(1f))
                        Text(if (live) "LIVE" else (match?.s("status") ?: "").uppercase(), color = if (live) Color(0xFFF87171) else Muted,
                            fontSize = 11.sp, fontWeight = FontWeight.Bold,
                            modifier = Modifier.clip(RoundedCornerShape(6.dp)).border(1.dp, if (live) Color(0xFFF87171) else Muted, RoundedCornerShape(6.dp)).padding(horizontal = 8.dp, vertical = 3.dp))
                    }
                    Text((match?.s("tournament_id") ?: "") + " - " + alFmtWhen(match?.s("starts_at") ?: ""), color = Muted, fontSize = 12.sp)
                }
            }
            if (match?.s("status") == "done") item {
                GoldButton("VOTE FOR MVP") { onOpenMvp() }
            }
            item { AlPollCard(poll, pollRes) { pollRes = it } }
            item {
                PanelCard {
                    Text("KILL FEED", color = Gold, fontSize = 13.sp, fontWeight = FontWeight.Bold)
                    Spacer(Modifier.height(4.dp))
                    val ks = kills
                    val visible = if (ks == null) emptyList() else (0 until ks.length()).map { ks.getJSONObject(it) }.filter { !it.optBoolean("hidden", false) }
                    if (visible.isEmpty()) Text("No kills posted yet - the host posts them here as they happen.", color = Muted, fontSize = 12.sp)
                    else visible.takeLast(30).reversed().forEach { e ->
                        Row(Modifier.fillMaxWidth().padding(vertical = 2.dp), verticalAlignment = Alignment.CenterVertically) {
                            Text(e.s("killer"), color = Color(0xFF4ADE80), fontSize = 13.sp, fontWeight = FontWeight.Bold)
                            Text("  eliminated  ", color = Muted, fontSize = 11.sp)
                            Text(e.s("victim"), color = Color(0xFFF87171), fontSize = 13.sp, fontWeight = FontWeight.Bold)
                            if (e.s("weapon").isNotBlank()) Text("  -  " + e.s("weapon"), color = Muted, fontSize = 11.sp)
                        }
                    }
                }
            }
            item {
                PanelCard { LiveComments(match?.s("tournament_id") ?: "") }
            }
            val c = clip
            if (c != null) item {
                PanelCard {
                    Text("CLIP OF THE WEEK", color = Gold2, fontSize = 12.sp, fontWeight = FontWeight.Black)
                    Text(c.s("name").ifBlank { c.s("username") } + (if (c.optInt("likes") > 0) " - " + c.optInt("likes") + " likes" else ""), color = Muted, fontSize = 11.sp)
                    if (c.s("caption").isNotBlank()) Text(c.s("caption"), color = Color.White, fontSize = 13.sp, maxLines = 2)
                    Spacer(Modifier.height(6.dp))
                    GoldButton("WATCH IN FEED") { onOpenFeed() }
                }
            }
        }
    }
}

@Composable
private fun AlPollCard(poll: JSONObject?, res0: JSONObject?, onResults: (JSONObject?) -> Unit) {
    val scope = rememberCoroutineScope()
    var res by remember { mutableStateOf(res0) }
    var sel by remember { mutableStateOf<String?>(null) }
    var msg by remember { mutableStateOf<String?>(null) }
    PanelCard {
        Text("FAN POLL", color = Gold, fontSize = 13.sp, fontWeight = FontWeight.Bold)
        Spacer(Modifier.height(4.dp))
        if (poll == null) {
            Text("No poll is open right now. Polls appear here when the admin opens one for a match.", color = Muted, fontSize = 12.sp)
            return@PanelCard
        }
        Text(poll.s("title"), color = Color.White, fontSize = 15.sp, fontWeight = FontWeight.Bold)
        val r = res
        if (r == null || !r.optBoolean("ok")) {
            Text("Vote counts are not available yet - voting opens with the match.", color = Muted, fontSize = 12.sp)
            return@PanelCard
        }
        val opts = r.optJSONArray("options") ?: JSONArray()
        val total = r.optInt("total").coerceAtLeast(0)
        val myVote = r.optString("my_vote", "").ifBlank { null }
        Spacer(Modifier.height(6.dp))
        for (i in 0 until opts.length()) {
            val o = opts.getJSONObject(i)
            val oid = o.s("id"); val votes = o.optInt("votes")
            val pct = if (total > 0) (100 * votes / total) else 0
            val mine = myVote == oid
            Row(Modifier.fillMaxWidth().padding(vertical = 3.dp).clip(RoundedCornerShape(10.dp))
                .background(if (sel == oid || mine) Gold.copy(alpha = .25f) else Color(0x14FFFFFF))
                .border(1.dp, if (sel == oid || mine) Gold else Color(0x22FFFFFF), RoundedCornerShape(10.dp))
                .clickable { sel = oid }.padding(horizontal = 12.dp, vertical = 9.dp),
                verticalAlignment = Alignment.CenterVertically) {
                Text(o.s("label"), color = Color.White, fontSize = 13.sp, modifier = Modifier.weight(1f))
                Text(if (mine) "Your vote - $votes ($pct%)" else "$votes ($pct%)", color = Muted, fontSize = 11.sp)
            }
        }
        Spacer(Modifier.height(6.dp))
        msg?.let { Text(it, color = ErrRed, fontSize = 12.sp) }
        if (sel != null && sel != myVote) GoldButton("CONFIRM VOTE") {
            val pick = sel ?: return@GoldButton
            scope.launch {
                val rr = runCatching { Api.authedRpc("cast_vote", JSONObject().put("p_poll", poll.s("id")).put("p_option", pick)) }.getOrNull()
                if (rr != null && rr.optBoolean("ok")) {
                    msg = null
                    val fresh = runCatching { Api.authedRpc("poll_results", JSONObject().put("p_poll", poll.s("id"))) }.getOrNull()
                    res = fresh
                    onResults(fresh)
                } else msg = rr?.s("error")?.ifBlank { null } ?: "Could not vote - log in first."
            }
        } else Text(if (myVote != null) "You can change your vote until voting ends." else "Tap an option to vote.", color = Muted, fontSize = 11.sp)
    }
}

private fun alFmtWhen(iso: String): String = try {
    val clean = iso.replace("T", " ").take(19)
    val d = java.text.SimpleDateFormat("yyyy-MM-dd HH:mm:ss", java.util.Locale.US).apply { timeZone = java.util.TimeZone.getTimeZone("UTC") }.parse(clean)
    java.text.SimpleDateFormat("d MMM, h:mm a", java.util.Locale.US).format(d ?: java.util.Date())
} catch (e: Exception) { "" }
