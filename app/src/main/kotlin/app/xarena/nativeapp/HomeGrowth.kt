package app.xarena.nativeapp

import android.content.Intent
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyRow
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.Text
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import org.json.JSONArray
import org.json.JSONObject
import androidx.compose.animation.core.animateFloat
import kotlinx.coroutines.launch
import java.text.SimpleDateFormat
import java.util.Locale

/* Growth home cards (Oct 20 batch): Ignition Cup hero + countdown, Aaj kya hai, daily check-in,
   fan vote, winners strip, top-3 squads, 3-step first-run walkthrough. Real data only. */

fun cupStartMs(t: JSONObject): Long? {
    val iso = t.s("start_at")
    if (iso.isNotBlank()) return runCatching { java.time.Instant.parse(iso).toEpochMilli() }.getOrNull()
    val dt = t.s("date_text")
    if (dt.isBlank()) return null
    return runCatching { SimpleDateFormat("EEEE, MMMM d, yyyy 'at' h:mm a z", Locale.ENGLISH).parse(dt)?.time }.getOrNull()
        ?: runCatching { SimpleDateFormat("EEEE, MMMM d, yyyy", Locale.ENGLISH).parse(dt)?.time }.getOrNull()
}

@Composable
fun CountdownLine(targetMs: Long) {
    var now by remember { mutableStateOf(System.currentTimeMillis()) }
    LaunchedEffect(targetMs) { while (true) { now = System.currentTimeMillis(); kotlinx.coroutines.delay(1000) } }
    val diff = targetMs - now
    val txt = if (diff <= 0) "Live now / starting"
    else {
        val d = diff / 86400000; val h = (diff % 86400000) / 3600000; val m = (diff % 3600000) / 60000; val s = (diff % 60000) / 1000
        if (d > 0) "${d}d ${h}h ${m}m" else if (h > 0) "${h}h ${m}m ${s}s" else "${m}m ${s}s"
    }
    Text(txt, color = Gold2, fontSize = 26.sp, fontWeight = FontWeight.Black)
}

/** Hero = Ignition Cup (or the first open tournament): countdown, slots bar, register + share. */
@Composable
fun CupHeroCard(t: JSONObject, slotsTaken: Int, onRegister: () -> Unit) {
    val ctx = androidx.compose.ui.platform.LocalContext.current
    Column(Modifier.fillMaxWidth().padding(horizontal = 16.dp, vertical = 6.dp).clip(RoundedCornerShape(Look.cardRadius))
        .background(Look.cardBg).border(1.dp, Gold.copy(alpha = .6f), RoundedCornerShape(Look.cardRadius)).padding(18.dp)) {
        Text(t.s("name"), color = Gold2, fontSize = 24.sp, fontWeight = FontWeight.Bold)
        Text(listOf(t.s("mode"), t.s("date_text")).filter { it.isNotBlank() }.joinToString(" · "), color = Muted, fontSize = 12.sp)
        val start = cupStartMs(t)
        if (start != null) { Spacer(Modifier.height(10.dp)); CountdownLine(start); Text("until start", color = Muted, fontSize = 11.sp) }
        val slots = t.optInt("slots", 0)
        if (slots > 0) {
            Spacer(Modifier.height(10.dp))
            if (slotsTaken >= 0) {
                Text("$slotsTaken / $slots squads registered", color = Color.White, fontSize = 13.sp, fontWeight = FontWeight.Bold)
                Spacer(Modifier.height(4.dp))
                Box(Modifier.fillMaxWidth().height(8.dp).clip(RoundedCornerShape(4.dp)).background(Color(0x33FFFFFF))) {
                    Box(Modifier.fillMaxWidth((slotsTaken.toFloat() / slots).coerceIn(0f, 1f)).height(8.dp).clip(RoundedCornerShape(4.dp)).background(Gold))
                }
            } else Text("$slots squad slots", color = Muted, fontSize = 12.sp)
        }
        Spacer(Modifier.height(12.dp))
        Row {
            if (t.optBoolean("reg_open")) GoldButton(Txt.t("REGISTER YOUR SQUAD", "SQUAD REGISTER KARO"), modifier = Modifier.weight(1f)) { onRegister() }
            Spacer(Modifier.width(10.dp))
            Text("SHARE", color = Gold, fontSize = 13.sp, fontWeight = FontWeight.Bold,
                modifier = Modifier.clip(RoundedCornerShape(10.dp)).border(1.dp, Gold, RoundedCornerShape(10.dp))
                    .clickable {
                        val i = Intent(Intent.ACTION_SEND).setType("text/plain")
                            .putExtra(Intent.EXTRA_TEXT, "Join XARENA ESPORTS - free Free Fire tournaments. https://xarenaprivate.github.io/xarena/tournaments.html")
                        ctx.startActivity(Intent.createChooser(i, "Share"))
                    }.padding(horizontal = 16.dp, vertical = 9.dp))
            Spacer(Modifier.width(10.dp))
            val scope2 = rememberCoroutineScope()
            Text("POSTER", color = Gold, fontSize = 13.sp, fontWeight = FontWeight.Bold,
                modifier = Modifier.clip(RoundedCornerShape(10.dp)).border(1.dp, Gold, RoundedCornerShape(10.dp))
                    .clickable {
                        scope2.launch {
                            val fx = ShareCard.buildStory(ctx, t)
                            if (fx != null) ShareCard.share(ctx, fx)
                        }
                    }.padding(horizontal = 16.dp, vertical = 9.dp))
        }
    }
}

/** "Aaj kya hai" - next real match + countdown + Join + room details for registered players. */
@Composable
fun AajKyaHaiCard(matches: JSONArray, onJoin: () -> Unit) {
    val next = (0 until matches.length()).map { matches.getJSONObject(it) }.firstOrNull { it.s("status") != "done" && !MatchLabels.isPostponed(it) } ?: return
    var room by remember { mutableStateOf<JSONObject?>(null) }
    LaunchedEffect(next.s("id")) {
        room = kotlinx.coroutines.withContext(kotlinx.coroutines.Dispatchers.IO) {
            runCatching { Api.authedRpc("match_room", JSONObject().put("p_match", next.s("id"))) }.getOrNull()
        }
    }
    PanelCard {
        Text("AAJ KYA HAI", color = Gold, fontSize = 13.sp, fontWeight = FontWeight.Bold)
        Spacer(Modifier.height(6.dp))
        Text(next.s("title").ifBlank { next.s("name").ifBlank { "Match" } }, color = Color.White, fontSize = 17.sp, fontWeight = FontWeight.Bold)
        val teams = listOf(next.s("team_a"), next.s("team_b")).filter { it.isNotBlank() }
        if (teams.isNotEmpty()) Text(teams.joinToString("  VS  "), color = Muted, fontSize = 12.sp)
        val st = runCatching { java.time.Instant.parse(next.s("starts_at")).toEpochMilli() }.getOrNull()
        if (st != null && st > System.currentTimeMillis()) { Spacer(Modifier.height(6.dp)); CountdownLine(st) }
        else Text(dmTime(next.s("starts_at")) + " · " + next.s("status").uppercase(), color = Muted, fontSize = 12.sp)
        Spacer(Modifier.height(6.dp))
        val r = room
        if (r != null && r.optBoolean("ok")) {
            Text("Room ID: " + r.s("room_id") + "   Pass: " + r.s("room_pass").ifBlank { "(none)" }, color = Gold2, fontSize = 13.sp, fontWeight = FontWeight.Bold)
        } else Text("Room ID unlocks shortly before start (registered players).", color = Muted, fontSize = 11.sp)
        Spacer(Modifier.height(8.dp))
        GoldButton("JOIN / MATCH CENTER") { onJoin() }
    }
}

/** Daily check-in card - streak flame + claim; links to the full Streak screen. */
@Composable
fun CheckinHomeCard(onOpenStreak: () -> Unit) {
    var tick by remember { mutableIntStateOf(0) }
    var confetti by remember { mutableStateOf(false) }
    ConfettiBurst(confetti) { confetti = false }
    LoadState2(tick, { runCatching { Api.authedRpc("my_game_stats", JSONObject()) }.getOrNull() }) { s ->
        PanelCard {
            Row(verticalAlignment = Alignment.CenterVertically) {
                Column(Modifier.weight(1f)) {
                    Text(Txt.t("DAILY CHECK-IN", "ROZ CHECK-IN"), color = Gold, fontSize = 13.sp, fontWeight = FontWeight.Bold)
                    Spacer(Modifier.height(4.dp))
                    if (s == null || !s.optBoolean("ok")) Text("Log in to check in daily and earn XP.", color = Muted, fontSize = 12.sp)
                    else {
                        val streak = s.optInt("streak")
                        Text("$streak-day streak", color = Gold2, fontSize = 20.sp, fontWeight = FontWeight.Bold)
                        Text(if (s.optBoolean("checked_in_today")) "Today claimed - come back tomorrow" else "Today is still open - claim it",
                            color = Muted, fontSize = 12.sp)
                    }
                }
                val scope = rememberCoroutineScope()
                if (s != null && s.optBoolean("ok") && !s.optBoolean("checked_in_today"))
                    GoldButton(Txt.t("CLAIM", "CLAIM KARO"), modifier = Modifier.width(110.dp)) { scope.launch {
                        val cr = runCatching { Api.authedRpc("daily_checkin", JSONObject()) }.getOrNull()
                        if (cr != null && cr.optBoolean("ok")) { confetti = true; Fx.blip() }
                        tick++
                    } }
                else Text("OPEN", color = Gold, fontSize = 13.sp, fontWeight = FontWeight.Bold,
                    modifier = Modifier.clip(RoundedCornerShape(10.dp)).border(1.dp, Gold, RoundedCornerShape(10.dp))
                        .clickable { onOpenStreak() }.padding(horizontal = 16.dp, vertical = 9.dp))
            }
        }
    }
}

/** Fan vote card - first active community poll, inline voting, live counts. */
@Composable
fun FanVoteHomeCard(onOpenVote: () -> Unit) {
    var tick by remember { mutableIntStateOf(0) }
    LoadState2(tick, {
        val body = runCatching { Api.authedRpcRaw("active_polls", JSONObject()).trim() }.getOrNull() ?: return@LoadState2 null
        val polls = if (body.startsWith("[")) JSONArray(body) else JSONObject(body).optJSONArray("polls") ?: JSONArray()
        if (polls.length() == 0) null
        else {
            val p = polls.getJSONObject(0)
            val res = runCatching { Api.authedRpc("poll_results", JSONObject().put("p_poll", p.s("id"))) }.getOrNull()
            Pair(p, res)
        }
    }) { pr ->
        val (poll, res) = pr ?: return@LoadState2
        PanelCard {
            Text(Txt.t("FAN VOTE", "FAN VOTE"), color = Gold, fontSize = 13.sp, fontWeight = FontWeight.Bold)
            Spacer(Modifier.height(4.dp))
            Text(poll.s("title"), color = Color.White, fontSize = 15.sp, fontWeight = FontWeight.Bold)
            if (res == null || !res.optBoolean("ok")) {
                Text("Voting is open - tap to cast your vote.", color = Muted, fontSize = 12.sp)
                Spacer(Modifier.height(6.dp)); GoldButton("VOTE NOW") { onOpenVote() }; return@PanelCard
            }
            val opts = res.optJSONArray("options") ?: JSONArray()
            val total = res.optInt("total").coerceAtLeast(0)
            val myVote = res.optString("my_vote", "").ifBlank { null }
            var sel by remember { mutableStateOf<String?>(null) }
            var msg by remember { mutableStateOf<String?>(null) }
            val scope = rememberCoroutineScope()
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
                    Text(if (mine) "Your vote · $votes ($pct%)" else "$votes ($pct%)", color = Muted, fontSize = 11.sp)
                }
            }
            Spacer(Modifier.height(6.dp))
            msg?.let { Text(it, color = ErrRed, fontSize = 12.sp) }
            if (sel != null && sel != myVote) GoldButton("CONFIRM VOTE") {
                val pick = sel ?: return@GoldButton
                scope.launch {
                    val r = runCatching { Api.authedRpc("cast_vote", JSONObject().put("p_poll", poll.s("id")).put("p_option", pick)) }.getOrNull()
                    if (r != null && r.optBoolean("ok")) tick++ else msg = r?.s("error")?.ifBlank { null } ?: "Could not vote - log in first."
                }
            } else Text("You can change your vote until voting ends.", color = Muted, fontSize = 11.sp)
        }
    }
}

/** Winners strip - latest real hall-of-fame entries (with squad logos when set); hidden when empty. */
@Composable
fun WinnersStrip(onOpenWinners: () -> Unit) {
    LoadState2(0, { runCatching { Api.publicGet("/rest/v1/hall_of_fame?select=category,name,detail,season,logo_url&order=id.desc&limit=6") }.getOrElse { JSONArray() } }) { aw ->
        if (aw.length() == 0) return@LoadState2
        Column {
            Row(Modifier.fillMaxWidth().padding(horizontal = 16.dp, vertical = 8.dp), verticalAlignment = Alignment.CenterVertically) {
                Text(Txt.t("LATEST WINNERS", "JEETNE WALE"), color = Gold, fontSize = 15.sp, fontWeight = FontWeight.Bold, modifier = Modifier.weight(1f))
                Text("Hall of Fame →", color = Muted, fontSize = 12.sp, modifier = Modifier.clickable { onOpenWinners() }.padding(4.dp))
            }
            LazyRow(Modifier.fillMaxWidth(), contentPadding = PaddingValues(horizontal = 12.dp)) {
                items((0 until aw.length()).map { aw.getJSONObject(it) }) { a ->
                    Row(Modifier.padding(horizontal = 4.dp).clip(RoundedCornerShape(Look.cardRadius))
                        .background(Look.cardBg).border(1.dp, Gold.copy(alpha = .4f), RoundedCornerShape(Look.cardRadius)).padding(10.dp),
                        verticalAlignment = Alignment.CenterVertically) {
                        if (a.s("logo_url").isNotBlank()) coil.compose.AsyncImage(model = xrImageModel(Api.ctx, a.s("logo_url")), contentDescription = a.s("name"),
                            modifier = Modifier.width(40.dp).height(40.dp).clip(RoundedCornerShape(10.dp)), contentScale = androidx.compose.ui.layout.ContentScale.Crop)
                        else Box(Modifier.width(40.dp).height(40.dp).clip(RoundedCornerShape(10.dp)).background(Gold.copy(alpha = .2f)), contentAlignment = Alignment.Center) {
                            Text("W", color = Gold, fontSize = 16.sp, fontWeight = FontWeight.Bold)
                        }
                        Spacer(Modifier.width(10.dp))
                        Column {
                            Text(a.s("name"), color = Color.White, fontSize = 14.sp, fontWeight = FontWeight.Bold, maxLines = 1)
                            Text(listOf(a.s("category"), a.s("detail"), a.s("season")).filter { it.isNotBlank() }.joinToString(" · "),
                                color = Muted, fontSize = 11.sp, maxLines = 1)
                        }
                    }
                }
            }
        }
    }
}

/** Top 3 squads - from the real squad board; hidden when empty. */
@Composable
fun TopSquadsCard(onOpenLeaderboard: () -> Unit, onOpenSquad: (String) -> Unit = {}) {
    LoadState2(0, {
        runCatching { Api.publicRpc("squad_board", JSONObject().put("p_scope", "global")) }.getOrNull()
    }) { r ->
        val rows = r?.optJSONArray("rows") ?: return@LoadState2
        if (rows.length() == 0) return@LoadState2
        PanelCard {
            Row(verticalAlignment = Alignment.CenterVertically) {
                Text(Txt.t("TOP SQUADS", "TOP SQUADS"), color = Gold, fontSize = 13.sp, fontWeight = FontWeight.Bold, modifier = Modifier.weight(1f))
                Text("All →", color = Muted, fontSize = 12.sp, modifier = Modifier.clickable { onOpenLeaderboard() }.padding(4.dp))
            }
            for (i in 0 until minOf(3, rows.length())) {
                val row = rows.getJSONObject(i)
                Row(Modifier.fillMaxWidth().padding(vertical = 4.dp).clip(RoundedCornerShape(8.dp))
                    .clickable(enabled = row.s("id").isNotBlank()) { onOpenSquad(row.s("id")) }.padding(4.dp), verticalAlignment = Alignment.CenterVertically) {
                    Text("#" + row.optInt("rank", i + 1), color = if (i == 0) Gold2 else Muted, fontSize = 14.sp, fontWeight = FontWeight.Bold, modifier = Modifier.width(34.dp))
                    Text(row.s("name") + (row.s("tag").takeIf { it.isNotBlank() }?.let { " [$it]" } ?: ""), color = Color.White, fontSize = 14.sp, modifier = Modifier.weight(1f))
                    Text(row.optInt("xp").toString() + " XP · " + row.optInt("members") + " players", color = Gold2, fontSize = 11.sp)
                }
            }
        }
    }
}

/** Avatar of the day - free cosmetic unlock (item 9). Real backend: daily_avatar / daily_avatar_claim. */
@Composable
fun DailyAvatarHomeCard() {
    var tick by remember { mutableIntStateOf(0) }
    LoadState2(tick, { runCatching { Api.publicRpc("daily_avatar", JSONObject()) }.getOrNull() }) { r ->
        if (r == null || !r.optBoolean("ok")) return@LoadState2
        val av = r.optJSONObject("avatar") ?: return@LoadState2
        val owned = r.optBoolean("owned")
        val img = "https://xarenaprivate.github.io/xarena/assets/avatars/" + av.s("asset") + ".webp"
        PanelCard {
            Row(verticalAlignment = Alignment.CenterVertically) {
                coil.compose.AsyncImage(model = xrImageModel(Api.ctx, img), contentDescription = av.s("title"),
                    modifier = Modifier.width(56.dp).height(56.dp).clip(RoundedCornerShape(14.dp)).background(Color(0x22FFFFFF)))
                Spacer(Modifier.width(12.dp))
                Column(Modifier.weight(1f)) {
                    Text(Txt.t("AVATAR OF THE DAY", "AAJ KA AVATAR"), color = Gold, fontSize = 12.sp, fontWeight = FontWeight.Bold)
                    Text(av.s("title") + " · " + av.s("tier").uppercase(), color = Color.White, fontSize = 14.sp, fontWeight = FontWeight.Bold)
                    Text(if (owned) "In your locker - free" else "Free cosmetic unlock - today only", color = Muted, fontSize = 11.sp)
                }
                if (!owned) {
                    val scope = rememberCoroutineScope()
                    var msg by remember { mutableStateOf<String?>(null) }
                    GoldButton(Txt.t("CLAIM", "CLAIM KARO"), modifier = Modifier.width(110.dp)) {
                        scope.launch {
                            val cr = runCatching { Api.authedRpc("daily_avatar_claim", JSONObject()) }.getOrNull()
                            if (cr != null && cr.optBoolean("ok")) tick++ else msg = cr?.s("error")?.ifBlank { null } ?: "Log in to claim"
                        }
                    }
                    msg?.let { Text(it, color = ErrRed, fontSize = 11.sp) }
                } else Text("OWNED ✓", color = Color(0xFF22C55E), fontSize = 12.sp, fontWeight = FontWeight.Bold)
            }
        }
    }
}

/** First-run 3-step walkthrough - same steps as the site's "How to join in 3 steps". Shown once. */
@Composable
fun WalkthroughOnce() {
    val prefs = remember { Api.ctx.getSharedPreferences("xr_prefs", 0) }
    var show by remember { mutableStateOf(!prefs.getBoolean("seen_walkthrough", false)) }
    if (!show) return
    androidx.compose.ui.window.Dialog(onDismissRequest = {}) {
        Column(Modifier.fillMaxWidth().clip(RoundedCornerShape(18.dp)).background(Look.cardBg)
            .border(1.dp, Gold.copy(alpha = .6f), RoundedCornerShape(18.dp)).padding(20.dp)) {
            Text(Txt.t("How to join in 3 steps", "Join kaise kare - 3 steps"), color = Gold2, fontSize = 20.sp, fontWeight = FontWeight.Bold)
            Spacer(Modifier.height(12.dp))
            listOf(
                "1" to ("Create a free account" to "Sign up on the Account page and finish phone verification as shown there. It costs nothing."),
                "2" to ("Register your squad" to "Open Tournaments, pick an open tournament and register your squad. Entry is free."),
                "3" to ("Get your room details" to "Registered players and their squadmates see the Room ID and password on match day, shortly before the match starts.")
            ).forEach { (n, step) ->
                Row(Modifier.padding(vertical = 6.dp)) {
                    Box(Modifier.size(28.dp).clip(RoundedCornerShape(14.dp)).background(Gold), contentAlignment = Alignment.Center) {
                        Text(n, color = Ink, fontSize = 14.sp, fontWeight = FontWeight.Bold)
                    }
                    Spacer(Modifier.width(10.dp))
                    Column {
                        Text(step.first, color = Color.White, fontSize = 14.sp, fontWeight = FontWeight.Bold)
                        Text(step.second, color = Muted, fontSize = 12.sp)
                    }
                }
            }
            Spacer(Modifier.height(14.dp))
            GoldButton(Txt.t("GOT IT", "SAMJH GAYA")) { prefs.edit().putBoolean("seen_walkthrough", true).apply(); show = false }
        }
    }
}

/** Aaj ka tip - one tip per day from site_settings key "tips" (IST day rotation), hidden when empty. */
@Composable
fun TipHomeCard() {
    LoadState2(0, {
        runCatching { Api.publicGet("/rest/v1/site_settings?key=eq.tips&select=value") }.getOrElse { JSONArray() }
    }) { rows ->
        if (rows.length() == 0) return@LoadState2
        val items = rows.getJSONObject(0).optJSONObject("value")?.optJSONArray("items") ?: return@LoadState2
        if (items.length() == 0) return@LoadState2
        val dayIdx = ((System.currentTimeMillis() + 19800000L) / 86400000L % items.length()).toInt()
        PanelCard {
            Text(Txt.t("AAJ KA TIP", "AAJ KA TIP"), color = Gold, fontSize = 13.sp, fontWeight = FontWeight.Bold)
            Spacer(Modifier.height(4.dp))
            Text(items.optString(dayIdx), color = Color.White, fontSize = 14.sp)
        }
    }
}

/** Creator spotlight - one verified creator per day (same IST rotation as web), hidden when none. */
@Composable
fun CreatorSpotlightCard(onOpenCreator: () -> Unit) {
    LoadState2(0, {
        runCatching { Api.publicGet("/rest/v1/creators_public?select=username,ign,avatar_url,tier,self_followers,channel_url,verified_creator") }.getOrElse { JSONArray() }
    }) { rows ->
        if (rows.length() == 0) return@LoadState2
        val idx = ((System.currentTimeMillis() + 19800000L) / 86400000L % rows.length()).toInt()
        val c = rows.getJSONObject(idx)
        PanelCard {
            Row(verticalAlignment = Alignment.CenterVertically) {
                if (c.s("avatar_url").isNotBlank()) coil.compose.AsyncImage(model = xrImageModel(Api.ctx, c.s("avatar_url")), contentDescription = c.s("ign"),
                    modifier = Modifier.width(52.dp).height(52.dp).clip(RoundedCornerShape(26.dp)), contentScale = androidx.compose.ui.layout.ContentScale.Crop)
                Spacer(Modifier.width(12.dp))
                Column(Modifier.weight(1f)) {
                    Text(Txt.t("CREATOR SPOTLIGHT", "CREATOR SPOTLIGHT"), color = Gold, fontSize = 12.sp, fontWeight = FontWeight.Bold)
                    Text(c.s("ign").ifBlank { c.s("username") } + (if (c.optBoolean("verified_creator")) " ✓" else ""), color = Color.White, fontSize = 15.sp, fontWeight = FontWeight.Bold)
                    Text(listOf(c.s("tier").uppercase().takeIf { it.isNotBlank() } ?: "", if (c.optInt("self_followers") > 0) c.optInt("self_followers").toString() + " followers" else "").filter { it.isNotBlank() }.joinToString(" · "),
                        color = Muted, fontSize = 11.sp)
                }
                Text("VIEW", color = Gold, fontSize = 13.sp, fontWeight = FontWeight.Bold,
                    modifier = Modifier.clip(RoundedCornerShape(10.dp)).border(1.dp, Gold, RoundedCornerShape(10.dp))
                        .clickable { onOpenCreator() }.padding(horizontal = 14.dp, vertical = 8.dp))
            }
        }
    }
}

/** 3D-rise entrance wrapper - subtle depth (slide up + tilt + fade). Off when Data Saver is on. */
@Composable
fun Rise3D(content: @Composable () -> Unit) {
    if (DataSaver.on) { content(); return }
    var shown by remember { mutableStateOf(false) }
    LaunchedEffect(Unit) { shown = true }
    val p by androidx.compose.animation.core.animateFloatAsState(if (shown) 1f else 0f, label = "rise")
    androidx.compose.foundation.layout.Box(androidx.compose.ui.Modifier.graphicsLayer {
        alpha = p
        translationY = (1f - p) * 60f
        rotationX = (1f - p) * -12f
        cameraDistance = 16f * density
    }) { content() }
}

/** One-shot gold confetti burst - ~1.4s, then gone. Skipped entirely under Data Saver. */
@Composable
fun ConfettiBurst(fire: Boolean, onDone: () -> Unit) {
    if (!fire || DataSaver.on) { if (fire) onDone(); return }
    val parts = remember { List(36) { Triple((10..90).random() / 100f, (20..100).random() / 100f, (4..9).random()) } }
    var now by remember { mutableStateOf(0f) }
    LaunchedEffect(Unit) {
        val s = System.currentTimeMillis()
        while (System.currentTimeMillis() - s < 1450) { now = (System.currentTimeMillis() - s) / 1450f; kotlinx.coroutines.delay(32) }
        onDone()
    }
    androidx.compose.foundation.Canvas(Modifier.fillMaxSize()) {
        parts.forEach { (fx, seed, sz) ->
            val x = size.width * fx + kotlin.math.sin((now * 6 + seed * 10).toDouble()).toFloat() * 30f
            val y = size.height * (seed * 0.2f) + now * size.height * (0.5f + seed * 0.6f)
            drawCircle(if (seed > 0.6f) Gold else Gold2, radius = sz.dp.toPx() / 2f, center = androidx.compose.ui.geometry.Offset(x, y), alpha = 1f - now * 0.6f)
        }
    }
}

/** Pulsing red dot for the LIVE NOW header. Static dot under Data Saver. */
@Composable
fun LivePulse() {
    val a = if (DataSaver.on) 1f else {
        val inf = androidx.compose.animation.core.rememberInfiniteTransition(label = "lp")
        inf.animateFloat(0.25f, 1f, androidx.compose.animation.core.infiniteRepeatable(
            androidx.compose.animation.core.tween(900), androidx.compose.animation.core.RepeatMode.Reverse), label = "lpa").value
    }
    Box(Modifier.padding(end = 8.dp).size(10.dp).clip(RoundedCornerShape(5.dp)).background(Color(0xFFE10600).copy(alpha = a)))
}
