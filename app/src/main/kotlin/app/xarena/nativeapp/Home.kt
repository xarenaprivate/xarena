package app.xarena.nativeapp

import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.LazyRow
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.shape.CircleShape
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
import org.json.JSONArray
import org.json.JSONObject

/* Home tab: featured tournament, LIVE NOW swiper, upcoming tournaments swiper, your stats.
   Every number is from the live backend; empty states where nothing exists. */
@Composable
fun HomeScreen(onGoTab: (Int) -> Unit, onOpenFeature: (String) -> Unit) {
    var tick by remember { mutableIntStateOf(0) }
    LoadState2(tick, {
        val ts = Api.publicGet("/rest/v1/tournaments?select=id,name,status,slots,prize,date_text,reg_open,mode&order=sort.asc")
        val yt = runCatching { Api.publicGet("/rest/v1/site_settings?key=eq.youtube_live&select=value") }.getOrElse { JSONArray() }
        val matches = runCatching { Api.publicGet("/rest/v1/matches_public?select=*&order=starts_at.asc.nullslast&limit=6") }.getOrElse { JSONArray() }
        val stats = runCatching { Api.authedRpc("my_stats", JSONObject()) }.getOrNull()
        val ann = runCatching { RemoteConfig.get(); RemoteConfig.announcement() }.getOrNull()
        val all = (0 until ts.length()).map { ts.getJSONObject(it) }
        val cup = all.firstOrNull { it.s("name").contains("ignition", true) } ?: all.firstOrNull { it.optBoolean("reg_open") } ?: all.firstOrNull()
        val slotsTaken = cup?.let { c ->
            runCatching {
                val body = Api.authedRpcRaw("tournament_reg_counts", JSONObject()).trim()
                val arr = if (body.startsWith("[")) JSONArray(body) else JSONArray()
                var n = -1
                for (i in 0 until arr.length()) { val r = arr.getJSONObject(i); if (r.s("tournament_id") == c.s("id")) n = r.optInt("registered", -1) }
                n
            }.getOrDefault(-1)
        } ?: -1
        HomeData(ts, if (yt.length() > 0) yt.getJSONObject(0).s("value") else "", matches, stats, ann, cup, slotsTaken)
    }) { d ->
        LazyColumn(Modifier.fillMaxSize()) {
            item {
                Brand()
                d.ann?.let { a ->
                    val ctx = androidx.compose.ui.platform.LocalContext.current
                    Column(Modifier.fillMaxWidth().padding(horizontal = 16.dp, vertical = 6.dp).clip(RoundedCornerShape(12.dp))
                        .background(Gold.copy(alpha = .15f)).border(1.dp, Gold.copy(alpha = .5f), RoundedCornerShape(12.dp))
                        .clickable(enabled = a.s("link").isNotBlank()) {
                            runCatching { ctx.startActivity(android.content.Intent(android.content.Intent.ACTION_VIEW, android.net.Uri.parse(a.s("link")))) }
                        }.padding(14.dp)) {
                        Text(a.s("text"), color = Gold2, fontSize = 14.sp, fontWeight = FontWeight.Bold)
                        if (a.s("link").isNotBlank()) Text("Tap to open", color = Muted, fontSize = 11.sp)
                    }
                }
                WalkthroughOnce()
                if (d.cup == null) {
                    PanelCard { Text("No tournaments yet", color = Color.White, fontSize = 16.sp, fontWeight = FontWeight.Bold)
                        Text("Tournaments appear here as soon as the admin creates them.", color = Muted, fontSize = 13.sp) }
                } else CupHeroCard(d.cup, d.slotsTaken) { onOpenFeature("register") }
                AajKyaHaiCard(d.matches) { onOpenFeature("live") }
                CheckinHomeCard { onOpenFeature("streak") }
                FanVoteHomeCard { onOpenFeature("vote") }
                DailyAvatarHomeCard()
            }
            item { WinnersStrip { onOpenFeature("winners") } }
            item { TopSquadsCard { onGoTab(5) } }
            item { WeeklyClipCard() }
            item {
                Row(Modifier.fillMaxWidth().padding(horizontal = 16.dp, vertical = 8.dp), verticalAlignment = Alignment.CenterVertically) {
                    Text("LIVE NOW", color = Gold, fontSize = 15.sp, fontWeight = FontWeight.Bold, modifier = Modifier.weight(1f))
                    Text("See all →", color = Muted, fontSize = 12.sp, modifier = Modifier.clickable { onOpenFeature("live") }.padding(4.dp))
                }
                val liveCards = mutableListOf<@Composable () -> Unit>()
                if (ytLiveInfo(d.ytUrl) != null) liveCards.add { LiveStreamCard { onOpenFeature("live") } }
                (0 until d.matches.length()).forEach { i ->
                    val m = d.matches.getJSONObject(i)
                    liveCards.add { LiveMatchCard(m) { onOpenFeature("live") } }
                }
                if (liveCards.isEmpty()) {
                    PanelCard { Text("Nothing live right now", color = Color.White, fontSize = 15.sp, fontWeight = FontWeight.Bold)
                        Text("When a stream or match goes live it appears here - real streams only, never reruns marked live.", color = Muted, fontSize = 12.sp) }
                } else LazyRow(Modifier.fillMaxWidth(), contentPadding = PaddingValues(horizontal = 12.dp)) {
                    items(liveCards) { card -> Box(Modifier.width(290.dp).padding(horizontal = 4.dp)) { card() } }
                }
            }
            item {
                Row(Modifier.fillMaxWidth().padding(horizontal = 16.dp, vertical = 8.dp), verticalAlignment = Alignment.CenterVertically) {
                    Text("UPCOMING TOURNAMENTS", color = Gold, fontSize = 15.sp, fontWeight = FontWeight.Bold, modifier = Modifier.weight(1f))
                    Text("See all →", color = Muted, fontSize = 12.sp, modifier = Modifier.clickable { onGoTab(1) }.padding(4.dp))
                }
                if (d.ts.length() == 0) PanelCard { Text("No upcoming tournaments yet.", color = Muted, fontSize = 13.sp) }
                else LazyRow(Modifier.fillMaxWidth(), contentPadding = PaddingValues(horizontal = 12.dp)) {
                    items((0 until d.ts.length()).map { d.ts.getJSONObject(it) }) { t ->
                        Column(Modifier.width(220.dp).padding(horizontal = 4.dp).clip(RoundedCornerShape(Look.cardRadius))
                            .background(Look.cardBg).border(1.dp, Look.cardBorder, RoundedCornerShape(Look.cardRadius)).padding(14.dp)) {
                            Text(t.s("name"), color = Gold2, fontSize = 15.sp, fontWeight = FontWeight.Bold, maxLines = 2)
                            Text(listOf(t.s("date_text"), if (t.optInt("slots", 0) > 0) t.optInt("slots").toString() + " slots" else "").filter { it.isNotBlank() }.joinToString(" · "),
                                color = Muted, fontSize = 11.sp)
                            Spacer(Modifier.height(8.dp))
                            if (t.optBoolean("reg_open")) Text("REGISTER", color = Ink, fontSize = 12.sp, fontWeight = FontWeight.Bold,
                                modifier = Modifier.clip(RoundedCornerShape(8.dp)).background(Gold).clickable { onOpenFeature("register") }.padding(horizontal = 14.dp, vertical = 7.dp))
                            else Text("Registration closed", color = Muted, fontSize = 11.sp)
                        }
                    }
                }
            }
            item {
                Row(Modifier.fillMaxWidth().padding(horizontal = 16.dp, vertical = 8.dp)) {
                    Text("YOUR STATS", color = Gold, fontSize = 15.sp, fontWeight = FontWeight.Bold, modifier = Modifier.weight(1f))
                    Text("All stats →", color = Muted, fontSize = 12.sp, modifier = Modifier.clickable { onOpenFeature("stats") }.padding(4.dp))
                }
                PanelCard {
                    val s = d.stats
                    if (s == null || !s.optBoolean("ok", true)) Text("No stats yet - play a tournament and check in daily.", color = Muted, fontSize = 13.sp)
                    else {
                        val wanted = listOf("rank", "wins", "kd", "k_d", "winrate", "win_rate", "xp", "level")
                        val keys = s.keys().asSequence().filter { it !in setOf("ok", "user_id", "id") }.toList()
                        val shown = wanted.mapNotNull { w -> keys.firstOrNull { it.equals(w, true) } }.take(4)
                            .ifEmpty { keys.take(4) }
                        if (shown.isEmpty()) Text("No stats yet - play a tournament and check in daily.", color = Muted, fontSize = 13.sp)
                        else Row(Modifier.fillMaxWidth()) {
                            shown.forEach { k ->
                                Column(Modifier.weight(1f), horizontalAlignment = Alignment.CenterHorizontally) {
                                    Text(statDisplay(s.opt(k)), color = Gold2, fontSize = 20.sp, fontWeight = FontWeight.Bold)
                                    Text(k.replace("_", " ").replace("kd", "K/D", true), color = Muted, fontSize = 11.sp)
                                }
                            }
                        }
                    }
                }
                Spacer(Modifier.height(24.dp))
            }
        }
    }
}

private data class HomeData(val ts: JSONArray, val ytUrl: String, val matches: JSONArray, val stats: JSONObject?, val ann: JSONObject? = null, val cup: JSONObject? = null, val slotsTaken: Int = -1)

@Composable
private fun LiveStreamCard(onOpen: () -> Unit) {
    Column(Modifier.clip(RoundedCornerShape(Look.cardRadius)).background(Look.cardBg)
        .border(1.dp, Color(0xFFE10600).copy(alpha = .6f), RoundedCornerShape(Look.cardRadius)).clickable { onOpen() }.padding(14.dp)) {
        Text("● LIVE STREAM", color = Color(0xFFFF5252), fontSize = 12.sp, fontWeight = FontWeight.Bold)
        Spacer(Modifier.height(6.dp))
        Text("XARENA is streaming now", color = Color.White, fontSize = 15.sp, fontWeight = FontWeight.Bold)
        Text("Tap to watch inside the app", color = Muted, fontSize = 12.sp)
    }
}

@Composable
private fun LiveMatchCard(m: JSONObject, onOpen: () -> Unit) {
    Column(Modifier.clip(RoundedCornerShape(Look.cardRadius)).background(Look.cardBg)
        .border(1.dp, Look.cardBorder, RoundedCornerShape(Look.cardRadius)).clickable { onOpen() }.padding(14.dp)) {
        Text(m.s("title").ifBlank { m.s("name").ifBlank { "Match" } }, color = Gold2, fontSize = 14.sp, fontWeight = FontWeight.Bold, maxLines = 1)
        Spacer(Modifier.height(4.dp))
        val teams = listOf(m.s("team_a"), m.s("team_b")).filter { it.isNotBlank() }
        if (teams.isNotEmpty()) Text(teams.joinToString("  VS  "), color = Color.White, fontSize = 13.sp, fontWeight = FontWeight.Medium, maxLines = 1)
        Text(dmTime(m.s("starts_at")), color = Muted, fontSize = 11.sp)
    }
}


/** Clip of the week - from the real approved-clips feed; renders nothing when there is no winner yet. */
@Composable
fun WeeklyClipCard() {
    var clip by remember { mutableStateOf<org.json.JSONObject?>(null) }
    var loaded by remember { mutableStateOf(false) }
    LaunchedEffect(Unit) {
        clip = kotlinx.coroutines.withContext(kotlinx.coroutines.Dispatchers.IO) {
            runCatching { Api.publicRpc("weekly_top_clip", org.json.JSONObject()).optJSONObject("clip") }.getOrNull()
        }
        loaded = true
    }
    val c = clip ?: return
    val url = feedMediaUrl(c) ?: return
    Column(Modifier.fillMaxWidth().padding(horizontal = 16.dp, vertical = 6.dp).clip(RoundedCornerShape(Look.cardRadius)).background(Look.cardBg)
        .border(1.dp, Gold.copy(alpha = .5f), RoundedCornerShape(Look.cardRadius))) {
        Column(Modifier.padding(12.dp)) {
            Text("CLIP OF THE WEEK", color = Gold2, fontSize = 12.sp, fontWeight = FontWeight.Black)
            Text(c.s("name").ifBlank { c.s("username") } + (if (c.optInt("likes") > 0) " · " + c.optInt("likes") + " likes" else ""), color = Muted, fontSize = 11.sp)
            if (c.s("caption").isNotBlank()) Text(c.s("caption"), color = Color.White, fontSize = 13.sp, maxLines = 2)
        }
        if (c.s("kind") == "clip") {
            var muted by remember { mutableStateOf(true) }
            var player by remember { mutableStateOf<android.media.MediaPlayer?>(null) }
            LaunchedEffect(muted) { player?.setVolume(if (muted) 0f else 1f, if (muted) 0f else 1f) }
            Box(Modifier.fillMaxWidth().height(200.dp)) {
                androidx.compose.ui.viewinterop.AndroidView(factory = { ctx ->
                    xrVideoView(ctx, url).apply {
                        setOnPreparedListener { mp -> player = mp; mp.isLooping = true; mp.setVolume(0f, 0f); mp.start() }
                    }
                }, modifier = Modifier.fillMaxSize(), onRelease = { it.stopPlayback() })
                Text(if (muted) "\ud83d\udd07" else "\ud83d\udd0a", color = Color.White, fontSize = 16.sp,
                    modifier = Modifier.align(Alignment.BottomEnd).padding(8.dp).clip(CircleShape)
                        .background(Color(0x88000000)).clickable { muted = !muted }.padding(horizontal = 10.dp, vertical = 6.dp))
            }
        } else {
            coil.compose.AsyncImage(model = xrImageModel(Api.ctx, url), contentDescription = c.s("caption"),
                modifier = Modifier.fillMaxWidth().height(200.dp), contentScale = androidx.compose.ui.layout.ContentScale.Crop)
        }
    }
}
