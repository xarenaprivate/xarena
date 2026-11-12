package app.xarena.nativeapp

import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.rememberLazyListState
import androidx.compose.foundation.lazy.LazyRow
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.Text
import androidx.compose.material3.pulltorefresh.rememberPullToRefreshState
import androidx.compose.ui.input.nestedscroll.nestedScroll
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

/* Home tab: featured tournament, LIVE NOW swiper, upcoming tournaments swiper, your stats.
   Every number is from the live backend; empty states where nothing exists. */
@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun HomeScreen(onGoTab: (Int) -> Unit, onOpenFeature: (String) -> Unit) {
    var tick by remember { mutableIntStateOf(0) }
    DisposableEffect(Unit) { Tilt3D.start(); onDispose { Tilt3D.stop() } }
    val ptr = rememberPullToRefreshState()
    if (ptr.isRefreshing) {
        LaunchedEffect(true) { tick++; kotlinx.coroutines.delay(1200); ptr.endRefresh() }
    }
    Box(Modifier.fillMaxSize().nestedScroll(ptr.nestedScrollConnection)) {
    var data by remember { mutableStateOf<HomeData?>(HomeCache.read(Api.ctx)) }
    var refreshing by remember { mutableStateOf(false) }
    LaunchedEffect(tick) {
        refreshing = true
        val fresh = runCatching { loadHomeData() }.getOrNull()
        if (fresh != null) { HomeCache.write(Api.ctx, fresh); data = fresh }
        refreshing = false
    }
    val d = data
    if (d != null) HomeBody(d, onGoTab, onOpenFeature)
    else LoadState2(tick, { loadHomeData().also { HomeCache.write(Api.ctx, it) } }) { dd -> HomeBody(dd, onGoTab, onOpenFeature) }
    if (ptr.isRefreshing || (refreshing && d != null)) CircularProgressIndicator(color = Gold, modifier = Modifier.align(Alignment.TopCenter).padding(top = 12.dp))
    }
}

/* Home data loader - network. Cached by HomeCache so tab switches paint instantly (stale-while-revalidate). */
internal suspend fun loadHomeData(): HomeData {
        val ts = Api.publicGet("/rest/v1/tournaments?select=id,name,status,slots,prize,date_text,reg_open,mode&order=sort.asc")
        val yt = runCatching { Api.publicGet("/rest/v1/site_settings?key=eq.youtube_live&select=value") }.getOrElse { JSONArray() }
        val matches = runCatching { Api.publicGet("/rest/v1/matches_public?select=*&order=starts_at.asc.nullslast&limit=6") }.getOrElse { JSONArray() }
        val stats = runCatching { Api.authedRpc("my_stats", JSONObject()) }.getOrNull()
        val ann = runCatching { RemoteConfig.get(); RemoteConfig.announcement() }.getOrNull()
        val all = (0 until ts.length()).map { ts.getJSONObject(it) }
        val open = all.filter { !it.s("status").equals("done", true) && !it.s("status").equals("closed", true) && !it.s("status").equals("completed", true) && !it.s("status").equals("cancelled", true) }
        val cup = open.firstOrNull { it.optBoolean("reg_open") } ?: open.firstOrNull() ?: all.firstOrNull()
        cup?.let { c -> cupStartMs(c)?.let { ms -> CupWidget.setTarget(Api.ctx, c.s("name"), ms, c.s("date_text")) } }
        val slotsTaken = cup?.let { c ->
            runCatching {
                val body = Api.authedRpcRaw("tournament_reg_counts", JSONObject()).trim()
                val arr = if (body.startsWith("[")) JSONArray(body) else JSONArray()
                var n = -1
                for (i in 0 until arr.length()) { val r = arr.getJSONObject(i); if (r.s("tournament_id") == c.s("id")) n = r.optInt("registered", -1) }
                n
            }.getOrDefault(-1)
        } ?: -1
        return HomeData(ts, if (yt.length() > 0) yt.getJSONObject(0).s("value") else "", matches, stats, ann, cup, slotsTaken)
}

/* Last-loaded home data: in-memory for the session + SharedPreferences across launches.
   Never replaces a fresh load - the background refresh still runs on every home entry. */
internal object HomeCache {
    private var mem: HomeData? = null
    fun read(ctx: android.content.Context): HomeData? {
        mem?.let { return it }
        return runCatching {
            val p = ctx.getSharedPreferences("home_cache", 0)
            val tsS = p.getString("ts", null) ?: return null
            val mS = p.getString("matches", "[]") ?: "[]"
            val cupS = p.getString("cup", null)
            val statsS = p.getString("stats", null)
            val annS = p.getString("ann", null)
            HomeData(JSONArray(tsS), p.getString("yt", "") ?: "", JSONArray(mS),
                if (statsS != null) JSONObject(statsS) else null,
                if (annS != null) JSONObject(annS) else null,
                if (cupS != null) JSONObject(cupS) else null,
                p.getInt("slotsTaken", -1))
        }.getOrNull()
    }
    fun write(ctx: android.content.Context, d: HomeData) {
        mem = d
        runCatching {
            ctx.getSharedPreferences("home_cache", 0).edit()
                .putString("ts", d.ts.toString()).putString("yt", d.ytUrl).putString("matches", d.matches.toString())
                .putString("cup", d.cup?.toString()).putString("stats", d.stats?.toString()).putString("ann", d.ann?.toString())
                .putInt("slotsTaken", d.slotsTaken).apply()
        }
    }
}

/* Home body with loaded data - extracted so render tests can verify populated, empty and offline states without network. */
@Composable
internal fun HomeBody(d: HomeData, onGoTab: (Int) -> Unit, onOpenFeature: (String) -> Unit, skipWalkthrough: Boolean = false) {
    val listState = rememberLazyListState()
        LazyColumn(Modifier.fillMaxSize(), state = listState) {
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
                if (!skipWalkthrough) WalkthroughOnce()
                HomeSponsorCarousel()
                if (d.cup == null) {
                    PanelCard { Text("No tournaments yet", color = Color.White, fontSize = 16.sp, fontWeight = FontWeight.Bold)
                        Text("Tournaments appear here as soon as the admin creates them.", color = Muted, fontSize = 13.sp) }
                } else Rise3D {
                    // scroll parallax: hero tilts and drifts slightly as you scroll past it (off under Data Saver)
                    val par = if (DataSaver.on || listState.firstVisibleItemIndex != 0) 0f else listState.firstVisibleItemScrollOffset.toFloat()
                    Box(Modifier.graphicsLayer {
                        translationY = -par * 0.18f
                        rotationX = (par / 240f).coerceIn(0f, 6f) - Tilt3D.y * 5f
                        rotationY = Tilt3D.x * 5f
                        cameraDistance = 14f * density
                    }) { CupHeroCard(d.cup, d.slotsTaken) { onOpenFeature("register") } }
                }
                Box(Modifier.tilt3d(3f)) { AajKyaHaiCard(d.matches) { onOpenFeature("live") } }
                Box(Modifier.tilt3d(3f)) { UpcomingMatchesStrip(d.matches, d.ts) { onOpenFeature("match") } }
                Box(Modifier.tilt3d(3f)) { CheckinHomeCard { onOpenFeature("streak") } }
                Box(Modifier.tilt3d(3f)) { FanVoteHomeCard { onOpenFeature("vote") } }
                Box(Modifier.tilt3d(3f)) { DailyAvatarHomeCard() }
                Box(Modifier.tilt3d(3f)) { TipHomeCard() }
                Box(Modifier.tilt3d(3f)) { CreatorSpotlightCard { onOpenFeature("creator") } }
            }
            item { WinnersStrip { onOpenFeature("winners") } }
            item { SpotlightsRow { f -> onOpenFeature(f) } }
            item { TopSquadsCard({ onOpenFeature("board") }, { id -> onOpenFeature("squadprofile:" + id) }) }
            item { WeeklyClipCard() }
            item {
                Row(Modifier.fillMaxWidth().padding(horizontal = 16.dp, vertical = 8.dp), verticalAlignment = Alignment.CenterVertically) {
                    LivePulse()
                    Text(Txt.t("LIVE NOW", "ABHI LIVE"), color = Gold, fontSize = 15.sp, fontWeight = FontWeight.Bold, modifier = Modifier.weight(1f))
                    Text("See all →", color = Muted, fontSize = 12.sp, modifier = Modifier.clickable { onOpenFeature("live") }.padding(4.dp))
                }
                val liveCards = mutableListOf<@Composable () -> Unit>()
                if (ytLiveInfo(d.ytUrl) != null) liveCards.add { LiveStreamCard { onOpenFeature("live") } }
                (0 until d.matches.length()).forEach { i ->
                    val m = d.matches.getJSONObject(i)
                    if (!MatchLabels.isPostponed(m)) liveCards.add { LiveMatchCard(m) { onOpenFeature("live") } }
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
                    Text(Txt.t("UPCOMING TOURNAMENTS", "AANE WALE TOURNAMENTS"), color = Gold, fontSize = 15.sp, fontWeight = FontWeight.Bold, modifier = Modifier.weight(1f))
                    Text("See all →", color = Muted, fontSize = 12.sp, modifier = Modifier.clickable { onGoTab(4) }.padding(4.dp))
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
                    Text(Txt.t("YOUR STATS", "TUMHARE STATS"), color = Gold, fontSize = 15.sp, fontWeight = FontWeight.Bold, modifier = Modifier.weight(1f))
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
                HomeClanRoomOption { onOpenFeature("room") }
                Spacer(Modifier.height(24.dp))
            }
        }
}

internal data class HomeData(val ts: JSONArray, val ytUrl: String, val matches: JSONArray, val stats: JSONObject?, val ann: JSONObject? = null, val cup: JSONObject? = null, val slotsTaken: Int = -1)

/** Upcoming scheduled matches strip - header comes from the tournament data, each card taps
    through to Match Center (rooms, check-in, results). Renders nothing when no scheduled matches. */
@Composable
internal fun UpcomingMatchesStrip(matches: JSONArray, tournaments: JSONArray, onOpenMatchCenter: () -> Unit) {
    val upcoming = (0 until matches.length()).map { matches.getJSONObject(it) }
        .filter { it.s("status").equals("scheduled", true) && !MatchLabels.isPostponed(it) }
    if (upcoming.isEmpty()) return
    val names = (0 until tournaments.length()).map { tournaments.getJSONObject(it) }
        .filter { t -> upcoming.any { it.s("tournament_id") == t.s("id") } }.map { it.s("name") }.filter { it.isNotBlank() }
    val header = if (names.size == 1) (names[0] + " matches").uppercase() else "UPCOMING MATCHES"
    Column {
        Row(Modifier.fillMaxWidth().padding(horizontal = 16.dp, vertical = 8.dp), verticalAlignment = Alignment.CenterVertically) {
            Text(header, color = Gold, fontSize = 15.sp, fontWeight = FontWeight.Bold, modifier = Modifier.weight(1f))
            Text("Rooms & check-in \u2192", color = Muted, fontSize = 12.sp, modifier = Modifier.clickable { onOpenMatchCenter() }.padding(4.dp))
        }
        LazyRow(Modifier.fillMaxWidth(), contentPadding = PaddingValues(horizontal = 12.dp)) {
            items(upcoming.size) { i ->
                val m = upcoming[i]
                Column(Modifier.width(220.dp).padding(horizontal = 4.dp).clip(RoundedCornerShape(Look.cardRadius))
                    .background(Look.cardBg).border(1.dp, Look.cardBorder, RoundedCornerShape(Look.cardRadius))
                    .clickable { onOpenMatchCenter() }.padding(14.dp)) {
                    Text(m.s("title").ifBlank { "Match" }, color = Gold2, fontSize = 14.sp, fontWeight = FontWeight.Bold, maxLines = 1)
                    Spacer(Modifier.height(4.dp))
                    Text(dmTime(m.s("starts_at")), color = Color.White, fontSize = 12.sp)
                    Text("Room ID unlocks here before the match", color = Muted, fontSize = 11.sp, maxLines = 2)
                }
            }
        }
    }
}

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

/** Existing private squad room, linked from Home below Your stats. */
@Composable
internal fun HomeClanRoomOption(onOpen: () -> Unit) {
    PanelCard {
        Text("Clan Room", color = Gold2, fontSize = 18.sp, fontWeight = FontWeight.Bold)
        Text("Open your squad's private room. Join a squad first if you do not have one yet.", color = Muted, fontSize = 13.sp)
        GoldButton("OPEN CLAN ROOM") { onOpen() }
    }
}
