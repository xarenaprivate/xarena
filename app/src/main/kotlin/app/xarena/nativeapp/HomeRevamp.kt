package app.xarena.nativeapp

import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.*
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
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import coil.compose.AsyncImage
import org.json.JSONArray
import org.json.JSONObject

/* v79 home revamp sections (user reference: hero logo, LIVE MATCHES rail, HIGHLIGHTS WALL,
   FRIENDS ACTIVITY, LIVE CHAT strip, upcoming tournament cards). Portrait only, dark-gold theme.
   ADDITIVE: no existing section, card or button is removed or renamed.
   REAL DATA ONLY: every section renders from its live backend source and hides itself (or shows
   an honest empty line) when that source is empty. No fake viewers, likes, activity, messages,
   team logos or stats anywhere in this file. */

@Composable
private fun RailHeader(title: String, action: String?, onAction: (() -> Unit)?) {
    Row(Modifier.fillMaxWidth().padding(horizontal = 16.dp).padding(top = 14.dp, bottom = 6.dp), verticalAlignment = Alignment.CenterVertically) {
        Text(title, color = Gold, fontSize = 15.sp, fontWeight = FontWeight.Bold, modifier = Modifier.weight(1f))
        if (action != null && onAction != null) Text(action, color = Muted, fontSize = 12.sp,
            modifier = Modifier.clip(RoundedCornerShape(6.dp)).clickable { onAction() }.padding(4.dp))
    }
}

private fun railAgo(iso: String): String {
    val ms = runCatching { java.time.Instant.parse(iso).toEpochMilli() }.getOrNull() ?: return ""
    val d = System.currentTimeMillis() - ms
    if (d < 0) return "now"
    val min = d / 60000
    if (min < 1) return "now"
    if (min < 60) return min.toString() + "m ago"
    val h = min / 60
    if (h < 24) return h.toString() + "h ago"
    return (h / 24).toString() + "d ago"
}

/* ---- LIVE MATCHES rail: only matches whose server status is live; hidden when none are. ---- */
@Composable
fun LiveMatchesRail(matches: JSONArray, ts: JSONArray, onOpenFeature: (String) -> Unit) {
    val tNames = remember(ts) {
        val m = mutableMapOf<String, String>()
        for (i in 0 until ts.length()) { val t = ts.getJSONObject(i); m[t.s("id")] = t.s("name") }
        m
    }
    val live = (0 until matches.length()).map { matches.getJSONObject(it) }
        .filter { val st = it.s("status"); st.equals("live", true) || st.equals("ongoing", true) || st.equals("in_progress", true) }
    if (live.isEmpty()) return
    Column(Modifier.fillMaxWidth()) {
        RailHeader("LIVE MATCHES", "See all >") { onOpenFeature("live") }
        LazyRow(contentPadding = PaddingValues(horizontal = 16.dp), horizontalArrangement = Arrangement.spacedBy(10.dp)) {
            items(live) { m ->
                Column(Modifier.width(220.dp).clip(RoundedCornerShape(Look.cardRadius)).background(Look.cardBg)
                    .border(1.dp, Look.cardBorder, RoundedCornerShape(Look.cardRadius))
                    .clickable { onOpenFeature("live") }.padding(12.dp)) {
                    Row(verticalAlignment = Alignment.CenterVertically) {
                        Box(Modifier.clip(RoundedCornerShape(4.dp)).background(Color(0xFFE10600)).padding(horizontal = 6.dp, vertical = 2.dp)) {
                            Text("LIVE", color = Color.White, fontSize = 9.sp, fontWeight = FontWeight.Bold)
                        }
                        LivePulse()
                    }
                    Spacer(Modifier.height(6.dp))
                    Text(m.s("title").ifBlank { "Match" }, color = Gold2, fontSize = 14.sp, fontWeight = FontWeight.Bold, maxLines = 1, overflow = TextOverflow.Ellipsis)
                    val tn = tNames[m.s("tournament_id")] ?: ""
                    if (tn.isNotBlank()) Text(tn, color = Color.White, fontSize = 12.sp, maxLines = 1, overflow = TextOverflow.Ellipsis)
                    Text(dmTime(m.s("starts_at")), color = Muted, fontSize = 11.sp)
                }
            }
        }
    }
}

/* ---- HIGHLIGHTS WALL: approved community clips (feed_posts kind=video, status=approved).
   Hidden entirely while there are no approved clips. ---- */
internal object HighlightsCache { var clips: JSONArray? = null }

@Composable
fun HighlightsWall(onOpenFeature: (String) -> Unit) {
    var clips by remember { mutableStateOf(HighlightsCache.clips) }
    LaunchedEffect(Unit) {
        val fresh = runCatching { Api.clipsWall() }.getOrNull()
        if (fresh != null) { HighlightsCache.clips = fresh; clips = fresh }
    }
    val arr = clips ?: return
    if (arr.length() == 0) return
    Column(Modifier.fillMaxWidth()) {
        RailHeader("HIGHLIGHTS WALL", "See all >") { onOpenFeature("clips") }
        val itemsList = (0 until minOf(arr.length(), 4)).map { arr.getJSONObject(it) }
        Column(Modifier.padding(horizontal = 16.dp), verticalArrangement = Arrangement.spacedBy(10.dp)) {
            itemsList.chunked(2).forEach { rowItems ->
                Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.spacedBy(10.dp)) {
                    rowItems.forEach { c -> Box(Modifier.weight(1f)) { HighlightCard(c) { onOpenFeature("clips") } } }
                    if (rowItems.size == 1) Spacer(Modifier.weight(1f))
                }
            }
        }
    }
}

@Composable
private fun HighlightCard(c: JSONObject, onOpen: () -> Unit) {
    val url = feedMediaUrl(c)
    val thumb = url?.takeIf { it.contains("res.cloudinary.com") }?.let { u ->
        var t = u.replace("/upload/", "/upload/so_0,q_auto,f_jpg/")
        if (t.endsWith(".mp4")) t = t.removeSuffix(".mp4") + ".jpg"
        t
    }
    Column(Modifier.clip(RoundedCornerShape(Look.cardRadius)).background(Look.cardBg)
        .border(1.dp, Look.cardBorder, RoundedCornerShape(Look.cardRadius)).clickable { onOpen() }) {
        Box(Modifier.fillMaxWidth().height(96.dp).background(Color(0xFF120A24)), contentAlignment = Alignment.Center) {
            if (thumb != null) AsyncImage(model = xrImageModel(androidx.compose.ui.platform.LocalContext.current, thumb),
                contentDescription = c.s("caption"), modifier = Modifier.fillMaxSize(), contentScale = ContentScale.Crop)
            Box(Modifier.size(30.dp).clip(CircleShape).background(Color(0xB3000000)).border(1.dp, Gold, CircleShape), contentAlignment = Alignment.Center) {
                Text("â¶", color = Gold, fontSize = 12.sp)
            }
        }
        Column(Modifier.padding(horizontal = 10.dp, vertical = 7.dp)) {
            Text(c.s("caption").ifBlank { "Clip" }, color = Color.White, fontSize = 12.sp, fontWeight = FontWeight.Bold, maxLines = 1, overflow = TextOverflow.Ellipsis)
            Text("Community clip" + (if (railAgo(c.s("created_at")).isNotBlank()) " Â· " + railAgo(c.s("created_at")) else ""), color = Muted, fontSize = 10.sp)
        }
    }
}

/* ---- FRIENDS ACTIVITY: xr_sf_friends_activity (player-reported presence, 2-min freshness).
   Hidden when signed out, on error, or when there is no friend activity to show. ---- */
/* Test seam: Paparazzi fixtures seed the cache and force the signed-in path;
   production always reads Api.uid (cache only ever fills behind a real authed RPC). */
internal var friendsRailSignedInOverride: Boolean? = null
internal object FriendsRailCache { var data: JSONObject? = null }

@Composable
fun FriendsActivityStrip(onOpenFeature: (String) -> Unit) {
    val signedIn = friendsRailSignedInOverride ?: !Api.uid.isNullOrBlank()
    if (!signedIn) return
    var resp by remember { mutableStateOf(FriendsRailCache.data) }
    LaunchedEffect(Unit) {
        val fresh = runCatching { Api.authedRpc("xr_sf_friends_activity", JSONObject()) }.getOrNull()
        if (fresh != null && fresh.optBoolean("ok")) { FriendsRailCache.data = fresh; resp = fresh }
    }
    val friends = resp?.optJSONArray("friends") ?: return
    if (friends.length() == 0) return
    val online = (0 until friends.length()).count {
        val p = friends.getJSONObject(it).s("presence"); p.equals("online", true) || p.equals("in_match", true)
    }
    Column(Modifier.fillMaxWidth()) {
        RailHeader("FRIENDS ACTIVITY", if (online > 0) online.toString() + " online" else null) { onOpenFeature("playercard") }
        Column(Modifier.padding(horizontal = 16.dp).clip(RoundedCornerShape(Look.cardRadius)).background(Look.cardBg)
            .border(1.dp, Look.cardBorder, RoundedCornerShape(Look.cardRadius))) {
            (0 until minOf(friends.length(), 3)).forEach { i ->
                val f = friends.getJSONObject(i)
                val name = f.s("name").ifBlank { f.s("username") }.ifBlank { "Player" }
                Row(Modifier.fillMaxWidth().padding(horizontal = 12.dp, vertical = 9.dp), verticalAlignment = Alignment.CenterVertically) {
                    Box {
                        val av = f.s("avatar_url").ifBlank { f.s("avatar") }
                        Box(Modifier.size(34.dp).clip(CircleShape).background(Card), contentAlignment = Alignment.Center) {
                            if (av.isNotBlank()) AsyncImage(model = xrImageModel(androidx.compose.ui.platform.LocalContext.current, av),
                                contentDescription = name, modifier = Modifier.fillMaxSize(), contentScale = ContentScale.Crop)
                            else Text(name.firstOrNull()?.uppercase() ?: "?", color = Gold, fontSize = 13.sp, fontWeight = FontWeight.Bold)
                        }
                        val p = f.s("presence")
                        val dot = when { p.equals("online", true) -> Color(0xFF2ECC71); p.equals("in_match", true) -> Color(0xFFF1C40F); else -> Color(0xFF7F8C8D) }
                        Box(Modifier.size(9.dp).clip(CircleShape).background(dot).align(Alignment.BottomEnd))
                    }
                    Spacer(Modifier.width(10.dp))
                    Column(Modifier.weight(1f)) {
                        Text(name, color = Color.White, fontSize = 13.sp, fontWeight = FontWeight.Bold, maxLines = 1, overflow = TextOverflow.Ellipsis)
                        Text(friendPresenceLabel(f), color = Muted, fontSize = 11.sp, maxLines = 1, overflow = TextOverflow.Ellipsis)
                    }
                }
            }
        }
    }
}

private fun friendPresenceLabel(f: JSONObject): String {
    val p = f.s("presence")
    if (p.equals("online", true)) return "Online now"
    if (p.equals("in_match", true)) return "In match"
    val ls = f.s("last_seen")
    if (ls.isNotBlank()) return "Last seen " + railAgo(ls)
    return "Offline"
}

/* ---- LIVE CHAT strip: latest real world-chat messages (room "general").
   When the room is empty it says so honestly and still opens chat. ---- */
internal object HomeChatCache { var msgs: JSONArray? = null }

@Composable
fun LiveChatStrip(onOpenFeature: (String) -> Unit) {
    var msgs by remember { mutableStateOf(HomeChatCache.msgs) }
    var loaded by remember { mutableStateOf(HomeChatCache.msgs != null) }
    LaunchedEffect(Unit) {
        val fresh = runCatching { Api.chatHistory("general") }.getOrNull()
        if (fresh != null) { HomeChatCache.msgs = fresh; msgs = fresh }
        loaded = true
    }
    Column(Modifier.fillMaxWidth()) {
        RailHeader("LIVE CHAT", "Open chat >") { onOpenFeature("chat") }
        Column(Modifier.padding(horizontal = 16.dp).fillMaxWidth().clip(RoundedCornerShape(Look.cardRadius)).background(Look.cardBg)
            .border(1.dp, Look.cardBorder, RoundedCornerShape(Look.cardRadius)).clickable { onOpenFeature("chat") }.padding(12.dp)) {
            val arr = msgs
            if (!loaded) Text("Loading chat...", color = Muted, fontSize = 12.sp)
            else if (arr == null || arr.length() == 0) {
                Text("Say hi to the lobby.", color = Muted, fontSize = 12.sp)
            } else {
                (0 until minOf(arr.length(), 2)).forEach { i ->
                    val m = arr.getJSONObject(i)
                    Row(Modifier.fillMaxWidth().padding(vertical = 3.dp), verticalAlignment = Alignment.CenterVertically) {
                        Text(m.s("handle").ifBlank { "player" } + ": ", color = Gold2, fontSize = 12.sp, fontWeight = FontWeight.Bold)
                        Text(m.s("body"), color = Color.White, fontSize = 12.sp, maxLines = 1, overflow = TextOverflow.Ellipsis, modifier = Modifier.weight(1f))
                        val ago = railAgo(m.s("created_at"))
                        if (ago.isNotBlank()) Text(ago, color = Muted, fontSize = 10.sp, modifier = Modifier.padding(start = 6.dp))
                    }
                }
            }
        }
    }
}

/* ---- UPCOMING TOURNAMENT cards: next two non-closed tournaments from the live table. ---- */
@Composable
fun UpcomingTournamentGrid(ts: JSONArray, onOpenFeature: (String) -> Unit) {
    val up = (0 until ts.length()).map { ts.getJSONObject(it) }
        .filter { val st = it.s("status"); !st.equals("done", true) && !st.equals("closed", true) && !st.equals("completed", true) && !st.equals("cancelled", true) }
        .take(2)
    if (up.isEmpty()) return
    Column(Modifier.fillMaxWidth()) {
        RailHeader("UPCOMING TOURNAMENTS", "See all >") { onOpenFeature("tourney") }
        Row(Modifier.padding(horizontal = 16.dp), horizontalArrangement = Arrangement.spacedBy(10.dp)) {
            up.forEach { t ->
                Column(Modifier.weight(1f).clip(RoundedCornerShape(Look.cardRadius)).background(Look.cardBg)
                    .border(1.dp, Gold.copy(alpha = .45f), RoundedCornerShape(Look.cardRadius))
                    .clickable { onOpenFeature("tourney") }) {
                    Box(Modifier.fillMaxWidth().background(Gold.copy(alpha = .18f)).padding(vertical = 5.dp), contentAlignment = Alignment.Center) {
                        Text(startsRibbon(t), color = Gold2, fontSize = 10.sp, fontWeight = FontWeight.Bold)
                    }
                    Column(Modifier.padding(10.dp)) {
                        Text(t.s("name"), color = Gold2, fontSize = 13.sp, fontWeight = FontWeight.Bold, maxLines = 1, overflow = TextOverflow.Ellipsis)
                        val bits = mutableListOf<String>()
                        if (t.s("prize").isNotBlank()) bits.add(t.s("prize"))
                        if (t.optInt("slots") > 0) bits.add(t.optInt("slots").toString() + " slots")
                        if (t.s("mode").isNotBlank()) bits.add(t.s("mode"))
                        if (bits.isNotEmpty()) Text(bits.joinToString(" Â· "), color = Muted, fontSize = 11.sp, maxLines = 1, overflow = TextOverflow.Ellipsis)
                        Spacer(Modifier.height(6.dp))
                        val open = t.optBoolean("reg_open")
                        Box(Modifier.clip(RoundedCornerShape(8.dp)).background(if (open) Gold else Card).padding(horizontal = 10.dp, vertical = 5.dp)) {
                            Text(if (open) "Open Registration" else "Registration closed",
                                color = if (open) Color(0xFF2A1400) else Muted, fontSize = 10.sp, fontWeight = FontWeight.Bold)
                        }
                    }
                }
            }
            if (up.size == 1) Spacer(Modifier.weight(1f))
        }
    }
}

private fun startsRibbon(t: JSONObject): String {
    val ms = cupStartMs(t) ?: return t.s("date_text").ifBlank { "DATE TBA" }.uppercase()
    val d = ms - System.currentTimeMillis()
    if (d <= 0) return "STARTED"
    val h = d / 3600000
    if (h < 1) return "STARTS IN " + (d / 60000).coerceAtLeast(1) + "M"
    if (h < 24) return "STARTS IN " + h + "H"
    if (h < 48) return "STARTS TOMORROW"
    return "STARTS IN " + (h / 24) + "D"
}
