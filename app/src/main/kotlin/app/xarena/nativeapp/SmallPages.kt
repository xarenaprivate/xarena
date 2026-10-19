package app.xarena.nativeapp

import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.asImageBitmap
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import kotlinx.coroutines.launch
import org.json.JSONArray
import org.json.JSONObject

@Composable
fun PageHeader(title: String, sub: String, onBack: () -> Unit) {
    Row(Modifier.fillMaxWidth().padding(horizontal = 8.dp, vertical = 8.dp), verticalAlignment = Alignment.CenterVertically) {
        Text("< Back", color = Gold, fontSize = 14.sp, modifier = Modifier.clip(RoundedCornerShape(6.dp)).clickable { onBack() }.padding(8.dp))
        Spacer(Modifier.width(8.dp))
        Column {
            Text(title, color = Gold2, fontSize = 18.sp, fontWeight = FontWeight.Bold)
            Text(sub, color = Muted, fontSize = 11.sp)
        }
    }
}

@Composable
fun SmallCard(content: @Composable ColumnScope.() -> Unit) {
    Column(Modifier.fillMaxWidth().padding(horizontal = 16.dp, vertical = 6.dp).clip(RoundedCornerShape(Look.cardRadius))
        .background(Look.cardBg).border(1.dp, Look.cardBorder, RoundedCornerShape(Look.cardRadius)).padding(14.dp), content = content)
}

private fun staticPages(): JSONObject {
    val txt = Api.ctx.assets.open("static_pages.json").bufferedReader().use { it.readText() }
    return JSONObject(txt)
}

/* ============ Fan Vote ============ */
@Composable
fun FanVoteScreen(onBack: () -> Unit) {
    val scope = rememberCoroutineScope()
    var tick by remember { mutableIntStateOf(0) }
    Column(Modifier.fillMaxSize().background(Ink)) {
        PageHeader("Fan Vote", "One vote per player - changeable while open", onBack)
        LoadState2(tick, {
            val polls = Api.authedRpc("active_polls", JSONObject())
            val arr = polls.optJSONArray("polls") ?: JSONArray()
            if (arr.length() == 0) null else {
                val p = arr.getJSONObject(0)
                val r = Api.authedRpc("poll_results", JSONObject().put("p_poll", p.getString("id")))
                if (r.optBoolean("ok")) r else null
            }
        }) { res ->
            if (res == null) { Text("No vote is open right now - the next one lands here.", color = Muted, fontSize = 14.sp, modifier = Modifier.padding(24.dp)); return@LoadState2 }
            val poll = res.getJSONObject("poll")
            val opts = res.optJSONArray("options") ?: JSONArray()
            val total = res.optInt("total", -1)
            val closed = poll.optString("status") != "open"
            var sel by remember { mutableStateOf(res.optString("my_vote").ifBlank { null }) }
            var msg by remember { mutableStateOf<String?>(null) }
            Text(if (closed) "CLOSED" else "LIVE", color = if (closed) Muted else Color(0xFF22C55E), fontSize = 12.sp, fontWeight = FontWeight.Bold, modifier = Modifier.padding(horizontal = 16.dp))
            Text(poll.optString("title"), color = Gold2, fontSize = 19.sp, fontWeight = FontWeight.Bold, modifier = Modifier.padding(horizontal = 16.dp, vertical = 6.dp))
            if (total >= 0) Text(total.toString() + " votes", color = Muted, fontSize = 12.sp, modifier = Modifier.padding(horizontal = 16.dp))
            Spacer(Modifier.height(8.dp))
            LazyColumn {
                items((0 until opts.length()).map { opts.getJSONObject(it) }) { o ->
                    val oid = o.getString("id")
                    val votes = o.optInt("votes")
                    val pct = if (total > 0) 100 * votes / total else 0
                    val mine = res.optString("my_vote") == oid
                    Column(Modifier.fillMaxWidth().padding(horizontal = 16.dp, vertical = 4.dp).clip(RoundedCornerShape(12.dp))
                        .background(if (sel == oid) Gold.copy(alpha = .25f) else Card.copy(alpha = .85f))
                        .border(1.dp, if (sel == oid) Gold else Gold.copy(alpha = .2f), RoundedCornerShape(12.dp))
                        .clickable(enabled = !closed) { sel = oid }.padding(12.dp)) {
                        Text(o.optString("label"), color = Color.White, fontSize = 15.sp, fontWeight = FontWeight.SemiBold)
                        Row {
                            Text(if (mine) "Your vote" else " ", color = Gold, fontSize = 12.sp, modifier = Modifier.weight(1f))
                            if (total >= 0) Text(votes.toString() + " votes - " + pct + "%", color = Muted, fontSize = 12.sp)
                        }
                        if (total >= 0) Box(Modifier.fillMaxWidth().padding(top = 6.dp).height(4.dp).clip(RoundedCornerShape(2.dp)).background(Ink)) {
                            Box(Modifier.fillMaxWidth(pct / 100f).fillMaxHeight().background(Gold))
                        }
                    }
                }
            }
            if (!closed) {
                Spacer(Modifier.height(8.dp))
                Box(Modifier.padding(horizontal = 16.dp)) {
                    GoldButton("CONFIRM VOTE", enabled = sel != null) {
                        scope.launch {
                            try {
                                val r = Api.authedRpc("cast_vote", JSONObject().put("p_poll", poll.getString("id")).put("p_option", sel))
                                if (r.optBoolean("ok")) tick++ else msg = r.optString("error").ifBlank { "Vote failed." }
                            } catch (e: Exception) { msg = e.message }
                        }
                    }
                }
                Text("You can change your vote until voting ends.", color = Muted, fontSize = 12.sp, modifier = Modifier.padding(16.dp))
            } else Text("Voting is closed - final counts above.", color = Muted, fontSize = 12.sp, modifier = Modifier.padding(16.dp))
            msg?.let { Text(it, color = ErrRed, fontSize = 13.sp, modifier = Modifier.padding(16.dp)) }
        }
    }
}

/* ============ Streak ============ */
@Composable
fun StreakScreen(onBack: () -> Unit) {
    val scope = rememberCoroutineScope()
    var tick by remember { mutableIntStateOf(0) }
    Column(Modifier.fillMaxSize().background(Ink)) {
        PageHeader("Daily Streak", "Log in daily, earn badges", onBack)
        LoadState2(tick, { Api.authedRpc("my_game_stats", JSONObject()) }) { s ->
            if (!s.optBoolean("ok")) { Text("Could not load your streak - try again in a moment.", color = ErrRed, modifier = Modifier.padding(24.dp)); return@LoadState2 }
            val streak = s.optInt("streak")
            val claimed = s.optBoolean("checked_in_today")
            val badges = s.optJSONArray("badges") ?: JSONArray()
            val has = { k: String -> (0 until badges.length()).any { badges.optString(it) == k } }
            SmallCard {
                Text(streak.toString(), color = Gold2, fontSize = 44.sp, fontWeight = FontWeight.Bold, modifier = Modifier.align(Alignment.CenterHorizontally))
                Text("day streak", color = Muted, fontSize = 14.sp, modifier = Modifier.align(Alignment.CenterHorizontally))
                Spacer(Modifier.height(10.dp))
                if (claimed) Text("Checked in today - come back tomorrow", color = Color(0xFF22C55E), fontSize = 13.sp, modifier = Modifier.align(Alignment.CenterHorizontally))
                else GoldButton("CHECK IN TODAY") {
                    scope.launch { try { Api.authedRpc("daily_checkin", JSONObject()); tick++ } catch (e: Exception) { } }
                }
            }
            val weekDay = if (streak <= 0) 0 else ((streak - 1) % 7) + 1
            SmallCard {
                Text("DAILY LOGIN STREAK", color = Muted, fontSize = 11.sp, fontWeight = FontWeight.Bold, modifier = Modifier.align(Alignment.CenterHorizontally))
                Spacer(Modifier.height(8.dp))
                Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.SpaceEvenly) {
                    for (d in 1..7) {
                        val on = d <= weekDay
                        Box(Modifier.size(34.dp).clip(RoundedCornerShape(17.dp))
                            .background(if (on) Gold else Color(0x22FFFFFF)),
                            contentAlignment = Alignment.Center) {
                            Text(if (on) "✓" else d.toString(), color = if (on) Ink else Muted, fontSize = 13.sp, fontWeight = FontWeight.Bold)
                        }
                    }
                }
                Spacer(Modifier.height(8.dp))
                Text(if (streak > 0) "Day " + minOf(streak, 7) + " of this streak" + (if (streak > 7) " - day " + streak + " total" else "") else "Check in today to start day 1",
                    color = Gold2, fontSize = 12.sp, modifier = Modifier.align(Alignment.CenterHorizontally))
                val milestones = listOf(Triple("streak_3", 3, "Warming Up"), Triple("streak_7", 7, "On Fire"), Triple("streak_30", 30, "Unstoppable"))
                val next = milestones.firstOrNull { !has(it.first) }
                if (next != null) {
                    Spacer(Modifier.height(8.dp))
                    Text("Next badge: " + next.third + " at " + next.second + " days", color = Color.White, fontSize = 12.sp, modifier = Modifier.align(Alignment.CenterHorizontally))
                    Spacer(Modifier.height(6.dp))
                    Box(Modifier.fillMaxWidth().height(9.dp).clip(RoundedCornerShape(6.dp)).background(Color(0x22FFFFFF))) {
                        Box(Modifier.fillMaxWidth((streak.toFloat() / next.second).coerceIn(0f, 1f)).fillMaxHeight().background(Gold))
                    }
                }
            }
            Text("BADGES", color = Muted, fontSize = 11.sp, fontWeight = FontWeight.Bold, modifier = Modifier.padding(16.dp))
            listOf(Triple("streak_3", 3, "Warming Up"), Triple("streak_7", 7, "On Fire"), Triple("streak_30", 30, "Unstoppable")).forEach { (k, d, n) ->
                SmallCard {
                    Row(verticalAlignment = Alignment.CenterVertically) {
                        Text(if (has(k)) "🏅" else "🔒", fontSize = 22.sp)
                        Spacer(Modifier.width(12.dp))
                        Column {
                            Text(n, color = Color.White, fontSize = 15.sp, fontWeight = FontWeight.SemiBold)
                            Text(d.toString() + "-day login streak" + if (has(k)) " - earned" else " - " + maxOf(0, d - streak) + " days to go", color = Muted, fontSize = 12.sp)
                        }
                    }
                }
            }
        }
    }
}

/* ============ Certificates ============ */
@Composable
fun CertsScreen(onBack: () -> Unit) {
    Column(Modifier.fillMaxSize().background(Ink)) {
        PageHeader("Certificates", "Real certificates from your tournament record", onBack)
        LoadState2(0, {
            val regs = Api.authedGetArray("/rest/v1/registrations?user_id=eq." + Api.uid + "&verified=eq.true&select=id,squad,ign,created_at,tournament_id&order=created_at.desc&limit=20")
            val badges = Api.authedGetArray("/rest/v1/badges?user_id=eq." + Api.uid + "&select=badge,earned_at&order=earned_at.desc&limit=20")
            val ts = Api.publicGet("/rest/v1/tournaments?select=id,name")
            val names = HashMap<String, String>()
            for (i in 0 until ts.length()) { val t = ts.getJSONObject(i); names[t.getString("id")] = t.getString("name") }
            Triple(regs, badges, names)
        }) { (regs, badges, names) ->
            val me = remember { mutableStateOf("") }
            LaunchedEffect(Unit) { me.value = runCatching { Api.myProfile()?.optString("ign") ?: "" }.getOrDefault("") }
            LazyColumn {
                if (regs.length() == 0 && badges.length() == 0) item {
                    Text("No certificates yet. They appear here after a verified registration or a title.", color = Muted, fontSize = 14.sp, modifier = Modifier.padding(24.dp))
                }
                items((0 until badges.length()).map { badges.getJSONObject(it) }.filter { it.optString("badge").startsWith("Champion: ") }) { b ->
                    CertCard("CHAMPION", "champion", b.getString("badge").removePrefix("Champion: "), me.value, b.optString("earned_at").take(10))
                }
                items((0 until regs.length()).map { regs.getJSONObject(it) }) { r ->
                    CertCard("PARTICIPATION", "participation", names[r.optString("tournament_id")] ?: "XARENA Tournament", r.optString("squad").ifBlank { me.value }, r.optString("created_at").take(10))
                }
            }
        }
    }
}

private fun qrBitmap(text: String, size: Int): android.graphics.Bitmap {
    val bits = com.google.zxing.qrcode.QRCodeWriter().encode(text, com.google.zxing.BarcodeFormat.QR_CODE, size, size)
    val bmp = android.graphics.Bitmap.createBitmap(size, size, android.graphics.Bitmap.Config.ARGB_8888)
    for (x in 0 until size) for (y in 0 until size) bmp.setPixel(x, y, if (bits.get(x, y)) 0xFF12082A.toInt() else 0xFFF5C451.toInt())
    return bmp
}

@Composable
private fun CertCard(kind: String, kindRaw: String, tournament: String, who: String, date: String) {
    Column(Modifier.fillMaxWidth().padding(horizontal = 16.dp, vertical = 8.dp).clip(RoundedCornerShape(8.dp)).background(Color(0xFF221338))
        .border(2.dp, Gold, RoundedCornerShape(8.dp)).padding(4.dp)
        .border(1.dp, Gold.copy(alpha = .35f), RoundedCornerShape(6.dp)).padding(16.dp), horizontalAlignment = Alignment.CenterHorizontally) {
        Text("XARENA ESPORTS", color = Gold, fontSize = 18.sp, fontWeight = FontWeight.Bold)
        Text("OFFICIAL CERTIFICATE", color = Muted, fontSize = 11.sp)
        Spacer(Modifier.height(10.dp))
        Text(kind, color = Gold2, fontSize = 15.sp, fontWeight = FontWeight.Bold)
        Text(tournament, color = Color.White, fontSize = 16.sp, fontWeight = FontWeight.SemiBold)
        Spacer(Modifier.height(6.dp))
        Text("Awarded to " + who.ifBlank { "Player" }, color = Color.White, fontSize = 13.sp)
        Text(date, color = Muted, fontSize = 12.sp)
        Spacer(Modifier.height(10.dp))
        val payload = "XARENA-CERT|" + kindRaw + "|" + who.ifBlank { "Player" } + "|" + tournament + "|" + date
        val qr = remember(payload) { qrBitmap(payload, 240) }
        androidx.compose.foundation.Image(qr.asImageBitmap(), contentDescription = "QR - scan to check details", modifier = Modifier.size(80.dp).clip(RoundedCornerShape(6.dp)))
        Text("SCAN TO CHECK DETAILS", color = Muted, fontSize = 9.sp, modifier = Modifier.padding(top = 3.dp))
    }
}

/* ============ Academy ============ */
@Composable
fun AcademyScreen(onBack: () -> Unit) {
    val drills = remember { staticPages().getJSONArray("drills") }
    val prefs = remember { Api.ctx.getSharedPreferences("xr_academy", 0) }
    var doneTick by remember { mutableIntStateOf(0) }
    var cat by remember { mutableStateOf("All") }
    var q by remember { mutableStateOf("") }
    Column(Modifier.fillMaxSize().background(Ink)) {
        PageHeader("Academy", "Training drills - progress saves on this device", onBack)
        OutlinedTextField(q, { q = it }, placeholder = { Text("Search drills") }, singleLine = true,
            modifier = Modifier.fillMaxWidth().padding(horizontal = 16.dp, vertical = 4.dp))
        val cats = listOf("All") + (0 until drills.length()).map { drills.getJSONObject(it).getString("cat") }.distinct()
        Row(Modifier.padding(horizontal = 16.dp)) {
            cats.forEach { c ->
                Text(c, color = if (cat == c) Ink else Muted, fontSize = 12.sp, fontWeight = FontWeight.Bold,
                    modifier = Modifier.padding(end = 6.dp).clip(RoundedCornerShape(8.dp)).background(if (cat == c) Gold else Card).clickable { cat = c }.padding(horizontal = 10.dp, vertical = 6.dp))
            }
        }
        Spacer(Modifier.height(6.dp))
        LazyColumn {
            items((0 until drills.length()).map { drills.getJSONObject(it) }.filter { (cat == "All" || it.getString("cat") == cat) && (q.isBlank() || it.getString("title").contains(q.trim(), true)) }) { d ->
                val done = prefs.getBoolean(d.getString("id"), false); doneTick.let { }
                SmallCard {
                    var open by remember { mutableStateOf(false) }
                    Row(Modifier.fillMaxWidth().clickable { open = !open }, verticalAlignment = Alignment.CenterVertically) {
                        Text(if (done) "✅" else "🎯", fontSize = 18.sp)
                        Spacer(Modifier.width(10.dp))
                        Column(Modifier.weight(1f)) {
                            Text(d.getString("title"), color = Color.White, fontSize = 15.sp, fontWeight = FontWeight.SemiBold)
                            Text(d.getString("cat") + " - " + d.getInt("mins") + " min", color = Muted, fontSize = 12.sp)
                        }
                        Text(if (open) "▲" else "▼", color = Gold, fontSize = 12.sp)
                    }
                    if (open) {
                        Spacer(Modifier.height(8.dp))
                        val steps = d.getJSONArray("steps")
                        for (i in 0 until steps.length()) Text((i + 1).toString() + ". " + steps.getString(i), color = Color.White, fontSize = 13.sp, modifier = Modifier.padding(vertical = 2.dp))
                        Spacer(Modifier.height(6.dp))
                        Text(if (done) "Mark not done" else "Mark done", color = Gold, fontSize = 13.sp, fontWeight = FontWeight.Bold, modifier = Modifier.clickable {
                            prefs.edit().putBoolean(d.getString("id"), !done).apply(); doneTick++
                        }.padding(4.dp))
                    }
                }
            }
        }
    }
}

/* ============ News ============ */
@Composable
fun NewsScreen(onBack: () -> Unit) {
    Column(Modifier.fillMaxSize().background(Ink)) {
        PageHeader("News", "Tournament updates and announcements", onBack)
        LoadState2(0, {
            runCatching { Api.publicGet("/rest/v1/news?select=tag,title,body,published_at&published=eq.true&order=published_at.desc&limit=20") }
                .getOrNull()?.takeIf { it.length() > 0 }
                ?: staticPages().getJSONArray("news")
        }) { arr ->
            LazyColumn {
                items((0 until arr.length()).map { arr.getJSONObject(it) }) { n ->
                    SmallCard {
                        Text(n.optString("tag") + " - " + n.optString("published_at").ifBlank { n.optString("date") }.take(10), color = Gold, fontSize = 11.sp, fontWeight = FontWeight.Bold)
                        Spacer(Modifier.height(4.dp))
                        Text(n.optString("title"), color = Gold2, fontSize = 16.sp, fontWeight = FontWeight.Bold)
                        if (!n.optString("body").isBlank()) { Spacer(Modifier.height(4.dp)); Text(n.optString("body"), color = Color.White, fontSize = 13.sp) }
                    }
                }
            }
        }
    }
}

/* ============ Rules ============ */
@Composable
fun RulesScreen(onBack: () -> Unit) {
    val rules = remember { staticPages().getString("rules") }
    Column(Modifier.fillMaxSize().background(Ink)) {
        PageHeader("Rules", "Applies to every XARENA tournament, scrim and squad", onBack)
        LazyColumn(Modifier.padding(horizontal = 16.dp)) {
            item { Text(rules, color = Color.White, fontSize = 13.5.sp, lineHeight = 20.sp, modifier = Modifier.padding(bottom = 24.dp)) }
        }
    }
}

/* ============ Notifications ============ */
@Composable
fun NotificationsScreen(onBack: () -> Unit) {
    val scope = rememberCoroutineScope()
    var tick by remember { mutableIntStateOf(0) }
    Column(Modifier.fillMaxSize().background(Ink)) {
        PageHeader("Notifications", "Alerts from XARENA", onBack)
        LoadState2(tick, { Api.authedRpc("my_notifications", JSONObject()) }) { r ->
            val list = if (r.optBoolean("ok")) r.optJSONArray("list") ?: JSONArray() else JSONArray()
            LazyColumn {
                if (list.length() == 0) item { Text("No alerts right now.", color = Muted, fontSize = 14.sp, modifier = Modifier.padding(24.dp)) }
                items((0 until list.length()).map { list.getJSONObject(it) }) { n ->
                    SmallCard {
                        Text(n.optString("kind").replace("_", " "), color = if (n.optBoolean("read")) Muted else Gold, fontSize = 11.sp, fontWeight = FontWeight.Bold)
                        Spacer(Modifier.height(3.dp))
                        Text(n.optString("title"), color = Gold2, fontSize = 15.sp, fontWeight = FontWeight.Bold)
                        if (!n.optString("body").isBlank()) Text(n.optString("body"), color = Color.White, fontSize = 13.sp)
                        Text(n.optString("at").take(16).replace("T", " "), color = Muted, fontSize = 11.sp)
                    }
                }
                if (list.length() > 0) item {
                    Text("Mark all read", color = Gold, fontSize = 13.sp, fontWeight = FontWeight.Bold, modifier = Modifier.padding(16.dp).clickable {
                        scope.launch { try { Api.authedRpc("notifications_read_all", JSONObject()); tick++ } catch (e: Exception) { } }
                    })
                }
            }
        }
    }
}

/* Simple loading wrapper with retry */
@Composable
fun <T> LoadState2(tick: Int, load: suspend () -> T, content: @Composable (T) -> Unit) {
    var nonce by remember(tick) { mutableIntStateOf(0) }
    var state by remember(tick) { mutableStateOf<Result<T>?>(null) }
    LaunchedEffect(tick, nonce) { state = null; state = runCatching { load() } }
    val s = state
    when {
        s == null -> Box(Modifier.fillMaxSize(), contentAlignment = Alignment.Center) { CircularProgressIndicator(color = Gold) }
        s.isFailure -> Column(Modifier.fillMaxSize().padding(24.dp), verticalArrangement = Arrangement.Center, horizontalAlignment = Alignment.CenterHorizontally) {
            Text(s.exceptionOrNull()?.message ?: "Could not load.", color = ErrRed)
            Spacer(Modifier.height(12.dp))
            Text("Retry", color = Gold, fontWeight = FontWeight.Bold, modifier = Modifier.clickable { nonce++ }.padding(8.dp))
        }
        else -> content(s.getOrThrow())
    }
}
