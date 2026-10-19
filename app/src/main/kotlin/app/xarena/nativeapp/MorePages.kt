package app.xarena.nativeapp

import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.text.BasicTextField
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.TextStyle
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import coil.compose.AsyncImage
import kotlinx.coroutines.launch
import org.json.JSONArray
import org.json.JSONObject

/* ============ Clips (approved clip posts, same query as the site) ============ */
@Composable
fun ClipsScreen(onBack: () -> Unit) {
    Column(Modifier.fillMaxSize().background(Ink)) {
        PageHeader("Clips", "Approved clips from the community feed", onBack)
        LoadState2(0, { Api.publicGet("/rest/v1/feed_posts?kind=eq.clip&status=eq.approved&select=id,user_id,caption,media_path,created_at&order=created_at.desc&limit=30") }) { arr ->
            if (arr.length() == 0) Text("No clips published yet.", color = Muted, fontSize = 14.sp, modifier = Modifier.padding(24.dp))
            else LazyColumn {
                items((0 until arr.length()).map { arr.getJSONObject(it) }) { c ->
                    SmallCard {
                        Text(c.s("caption").ifBlank { "Clip" }, color = Gold2, fontSize = 15.sp, fontWeight = FontWeight.SemiBold)
                        Text(dmTime(c.s("created_at")), color = Muted, fontSize = 11.sp)
                        Spacer(Modifier.height(6.dp))
                        val path = c.s("media_path")
                        val url = if (path.startsWith("https://res.cloudinary.com/")) path.replace("/upload/", "/upload/q_auto/") else null
                        if (url != null) {
                            var playing by remember { mutableStateOf(false) }
                            Box(Modifier.fillMaxWidth().height(220.dp).clip(RoundedCornerShape(12.dp)).background(Color.Black), contentAlignment = Alignment.Center) {
                                if (playing) androidx.compose.ui.viewinterop.AndroidView(factory = { ctx ->
                                    android.widget.VideoView(ctx).apply { setVideoPath(url); setOnPreparedListener { it.start() } }
                                }, modifier = Modifier.fillMaxSize())
                                else Text("▶ Play", color = Color.White, fontSize = 14.sp, fontWeight = FontWeight.Bold,
                                    modifier = Modifier.clip(RoundedCornerShape(10.dp)).background(Gold.copy(alpha = .35f)).clickable { playing = true }.padding(horizontal = 18.dp, vertical = 10.dp))
                            }
                        } else Text("Video opens in the feed.", color = Muted, fontSize = 12.sp)
                    }
                }
            }
        }
    }
}

/* ============ Winners (hall of fame + awards) ============ */
@Composable
fun WinnersScreen(onBack: () -> Unit) {
    Column(Modifier.fillMaxSize().background(Ink)) {
        PageHeader("Winners", "Hall of fame - real results only", onBack)
        LoadState2(0, {
            val hof = runCatching { Api.publicGet("/rest/v1/hall_of_fame?select=*&order=created_at.desc&limit=30") }.getOrElse { JSONArray() }
            val awards = runCatching { Api.publicGet("/rest/v1/awards?select=*&order=created_at.desc&limit=30") }.getOrElse { JSONArray() }
            hof to awards
        }) { (hof, awards) ->
            LazyColumn {
                if (hof.length() == 0 && awards.length() == 0) item { Text("No winners recorded yet - Season 1 results land here.", color = Muted, fontSize = 14.sp, modifier = Modifier.padding(24.dp)) }
                if (hof.length() > 0) {
                    item { Text("HALL OF FAME", color = Muted, fontSize = 11.sp, fontWeight = FontWeight.Bold, modifier = Modifier.padding(14.dp)) }
                    items((0 until hof.length()).map { hof.getJSONObject(it) }) { w ->
                        SmallCard {
                            Text(w.s("title").ifBlank { w.s("name") }, color = Gold2, fontSize = 16.sp, fontWeight = FontWeight.Bold)
                            Text(listOf(w.s("team"), w.s("tournament"), w.s("season")).filter { it.isNotBlank() }.joinToString(" - "), color = Color.White, fontSize = 13.sp)
                            Text(dmTime(w.s("created_at")), color = Muted, fontSize = 11.sp)
                        }
                    }
                }
                if (awards.length() > 0) {
                    item { Text("AWARDS", color = Muted, fontSize = 11.sp, fontWeight = FontWeight.Bold, modifier = Modifier.padding(14.dp)) }
                    items((0 until awards.length()).map { awards.getJSONObject(it) }) { a ->
                        SmallCard {
                            Text(a.s("title").ifBlank { a.s("name") }, color = Gold2, fontSize = 15.sp, fontWeight = FontWeight.Bold)
                            Text(listOf(a.s("holder"), a.s("team"), a.s("note")).filter { it.isNotBlank() }.joinToString(" - "), color = Color.White, fontSize = 13.sp)
                        }
                    }
                }
            }
        }
    }
}

/* ============ Stats ============ */
@Composable
fun StatsScreen(onBack: () -> Unit) {
    Column(Modifier.fillMaxSize().background(Ink)) {
        PageHeader("Stats", "Your numbers and platform numbers", onBack)
        LoadState2(0, {
            val mine = runCatching { Api.authedRpc("my_stats", JSONObject()) }.getOrNull()
            val plat = runCatching { Api.authedRpc("platform_stats", JSONObject()) }.getOrNull()
            mine to plat
        }) { (mine, plat) ->
            LazyColumn {
                item { Text("YOU", color = Muted, fontSize = 11.sp, fontWeight = FontWeight.Bold, modifier = Modifier.padding(14.dp)) }
                item { SmallCard {
                    if (mine == null || !mine.optBoolean("ok", true)) Text("No stats yet - play a tournament.", color = Muted, fontSize = 13.sp)
                    else {
                        val skip = setOf("ok", "user_id", "id")
                        val keys = mine.keys().asSequence().filter { it !in skip }.toList()
                        if (keys.isEmpty()) Text("No stats yet - play a tournament.", color = Muted, fontSize = 13.sp)
                        keys.forEach { k ->
                            Row(Modifier.fillMaxWidth().padding(vertical = 4.dp)) {
                                Text(k.replace("_", " "), color = Muted, fontSize = 13.sp, modifier = Modifier.weight(1f))
                                Text(statDisplay(mine.opt(k)), color = Color.White, fontSize = 13.sp, fontWeight = FontWeight.SemiBold)
                            }
                        }
                    }
                } }
                item { Text("PLATFORM", color = Muted, fontSize = 11.sp, fontWeight = FontWeight.Bold, modifier = Modifier.padding(14.dp)) }
                item { SmallCard {
                    if (plat == null || !plat.optBoolean("ok", true)) Text("Platform stats unavailable.", color = Muted, fontSize = 13.sp)
                    else {
                        val skip = setOf("ok")
                        plat.keys().asSequence().filter { it !in skip }.forEach { k ->
                            Row(Modifier.fillMaxWidth().padding(vertical = 4.dp)) {
                                Text(k.replace("_", " "), color = Muted, fontSize = 13.sp, modifier = Modifier.weight(1f))
                                Text(statDisplay(plat.opt(k)), color = Gold2, fontSize = 13.sp, fontWeight = FontWeight.SemiBold)
                            }
                        }
                    }
                } }
            }
        }
    }
}

/* ============ Tasks ============ */
@Composable
fun TasksScreen(onBack: () -> Unit) {
    val scope = rememberCoroutineScope()
    var tick by remember { mutableIntStateOf(0) }
    Column(Modifier.fillMaxSize().background(Ink)) {
        PageHeader("Tasks", "Finish tasks, earn points", onBack)
        LoadState2(tick, {
            val tasks = Api.publicGet("/rest/v1/tasks?active=eq.true&select=*&order=id.asc")
            val done = runCatching { Api.authedGetArray("/rest/v1/task_completions?user_id=eq." + Api.uid + "&select=task_id,status") }.getOrElse { JSONArray() }
            tasks to done
        }) { (tasks, done) ->
            val doneIds = (0 until done.length()).map { done.getJSONObject(it).s("task_id") }.toSet()
            LazyColumn {
                if (tasks.length() == 0) item { Text("No active tasks right now.", color = Muted, fontSize = 14.sp, modifier = Modifier.padding(24.dp)) }
                items((0 until tasks.length()).map { tasks.getJSONObject(it) }) { t ->
                    val id = t.s("id")
                    val finished = id in doneIds
                    var note by remember { mutableStateOf("") }
                    var busy by remember { mutableStateOf(false) }
                    var msg by remember { mutableStateOf<String?>(null) }
                    SmallCard {
                        Row(verticalAlignment = Alignment.CenterVertically) {
                            Text(if (finished) "✅" else "🎯", fontSize = 18.sp)
                            Spacer(Modifier.width(10.dp))
                            Column(Modifier.weight(1f)) {
                                Text(t.s("title").ifBlank { t.s("name") }, color = Color.White, fontSize = 15.sp, fontWeight = FontWeight.SemiBold)
                                if (t.s("description").isNotBlank()) Text(t.s("description"), color = Muted, fontSize = 12.sp)
                                if (t.optInt("points") > 0) Text(t.optInt("points").toString() + " points", color = Gold, fontSize = 12.sp, fontWeight = FontWeight.Bold)
                            }
                        }
                        if (!finished) {
                            Spacer(Modifier.height(6.dp))
                            BasicTextField(note, { note = it }, modifier = Modifier.fillMaxWidth().clip(RoundedCornerShape(8.dp)).background(Ink).padding(8.dp),
                                textStyle = TextStyle(color = Color.White, fontSize = 12.5.sp),
                                decorationBox = { inner -> if (note.isEmpty()) Text("Note for the reviewer (optional)", color = Muted, fontSize = 12.5.sp); inner() })
                            Spacer(Modifier.height(6.dp))
                            Text(if (busy) "Submitting..." else "Submit for review", color = Ink, fontSize = 12.5.sp, fontWeight = FontWeight.Bold,
                                modifier = Modifier.clip(RoundedCornerShape(8.dp)).background(Gold).clickable {
                                    if (busy) return@clickable
                                    busy = true
                                    scope.launch {
                                        try {
                                            val r = Api.authedRpc("submit_task", JSONObject().put("p_task_id", id).put("p_note", note.ifBlank { null }))
                                            if (r.optBoolean("ok", true)) tick++ else msg = r.s("error").ifBlank { "Submit failed." }
                                        } catch (e: Exception) { msg = e.message }
                                        busy = false
                                    }
                                }.padding(horizontal = 12.dp, vertical = 7.dp))
                            msg?.let { Text(it, color = ErrRed, fontSize = 11.sp) }
                        }
                    }
                }
            }
        }
    }
}

/* ============ Live ============ */
@Composable
fun LiveScreen(onBack: () -> Unit) {
    Column(Modifier.fillMaxSize().background(Ink)) {
        PageHeader("Live", "Real streams and the real match schedule", onBack)
        LoadState2(0, {
            val yt = runCatching { Api.publicGet("/rest/v1/site_settings?key=eq.youtube_live&select=value") }.getOrElse { JSONArray() }
            val matches = runCatching { Api.publicGet("/rest/v1/matches_public?select=*&order=starts_at.asc.nullslast&limit=10") }.getOrElse { JSONArray() }
            yt to matches
        }) { (yt, matches) ->
            val live = ytLiveInfo(if (yt.length() > 0) yt.getJSONObject(0).s("value") else "")
            LazyColumn {
                item { SmallCard {
                    Text("STREAM", color = Muted, fontSize = 11.sp, fontWeight = FontWeight.Bold)
                    Spacer(Modifier.height(6.dp))
                    if (live == null) Text("No stream is live right now. When XARENA goes live, the stream plays here inside the app.", color = Muted, fontSize = 13.sp)
                    else {
                        Text("Live now", color = Color(0xFF22C55E), fontSize = 14.sp, fontWeight = FontWeight.Bold)
                        if (live.second.isNotBlank()) Text(live.second, color = Color.White, fontSize = 13.sp)
                        Spacer(Modifier.height(6.dp))
                        YouTubeEmbed(live.first, Modifier.fillMaxWidth().height(220.dp).clip(RoundedCornerShape(12.dp))
                            .border(1.dp, Gold.copy(alpha = .45f), RoundedCornerShape(12.dp)).background(Ink))
                        Spacer(Modifier.height(6.dp))
                        Text("Open in YouTube", color = Muted, fontSize = 12.sp,
                            modifier = Modifier.clickable { openExternalUrl("https://youtu.be/" + live.first) }.padding(4.dp))
                    }
                } }
                item { Text("UPCOMING MATCHES", color = Muted, fontSize = 11.sp, fontWeight = FontWeight.Bold, modifier = Modifier.padding(14.dp)) }
                if (matches.length() == 0) item { Text("No matches scheduled yet.", color = Muted, fontSize = 13.sp, modifier = Modifier.padding(horizontal = 16.dp)) }
                items((0 until matches.length()).map { matches.getJSONObject(it) }) { m ->
                    SmallCard {
                        Text(m.s("title").ifBlank { m.s("name").ifBlank { "Match" } }, color = Gold2, fontSize = 15.sp, fontWeight = FontWeight.Bold)
                        Text(listOf(m.s("team_a"), m.s("team_b")).filter { it.isNotBlank() }.joinToString(" vs "), color = Color.White, fontSize = 13.sp)
                        Text(dmTime(m.s("starts_at")), color = Muted, fontSize = 11.sp)
                    }
                }
            }
        }
    }
}

/* ============ Scrims (LFG board) ============ */
@Composable
fun ScrimsScreen(onBack: () -> Unit) {
    Column(Modifier.fillMaxSize().background(Ink)) {
        PageHeader("Scrims", "Looking-for-game board", onBack)
        LoadState2(0, { Api.authedRpc("lfg_list", JSONObject()) }) { r ->
            val list = r.optJSONArray("posts") ?: r.optJSONArray("list") ?: r.optJSONArray("items") ?: JSONArray()
            LazyColumn {
                if (list.length() == 0) item { Text("No LFG posts right now.", color = Muted, fontSize = 14.sp, modifier = Modifier.padding(24.dp)) }
                items((0 until list.length()).map { list.getJSONObject(it) }) { p ->
                    SmallCard {
                        Text(p.s("ign").ifBlank { p.s("author").ifBlank { "Player" } }, color = Gold2, fontSize = 15.sp, fontWeight = FontWeight.Bold)
                        Text(p.s("note").ifBlank { p.s("body") }, color = Color.White, fontSize = 13.sp)
                        Text(dmTime(p.s("created_at").ifBlank { p.s("at") }), color = Muted, fontSize = 11.sp)
                    }
                }
            }
        }
    }
}

/* ============ Calendar ============ */
@Composable
fun CalendarScreen(onBack: () -> Unit) {
    Column(Modifier.fillMaxSize().background(Ink)) {
        PageHeader("Calendar", "Tournament and event dates", onBack)
        LoadState2(0, { Api.authedRpc("calendar_feed", JSONObject()) }) { r ->
            val list = r.optJSONArray("events") ?: r.optJSONArray("items") ?: r.optJSONArray("list") ?: JSONArray()
            LazyColumn {
                if (list.length() == 0) item { Text("No events scheduled yet.", color = Muted, fontSize = 14.sp, modifier = Modifier.padding(24.dp)) }
                items((0 until list.length()).map { list.getJSONObject(it) }) { e ->
                    SmallCard {
                        Text(e.s("title").ifBlank { e.s("name") }, color = Gold2, fontSize = 15.sp, fontWeight = FontWeight.Bold)
                        Text(e.s("date_text").ifBlank { dmTime(e.s("at").ifBlank { e.s("starts_at") }) }, color = Color.White, fontSize = 13.sp)
                        if (e.s("note").isNotBlank()) Text(e.s("note"), color = Muted, fontSize = 12.sp)
                    }
                }
            }
        }
    }
}


/* youtube_live site_setting: value may be a JSON object {video_id, title, active}, a full URL, or a bare 11-char id.
   Returns (videoId, title) only when active and an id could be extracted, else null. */
fun ytLiveInfo(raw: String): Pair<String, String>? {
    if (raw.isBlank()) return null
    var vid = raw.trim()
    var title = ""
    var active = true
    val obj = runCatching { JSONObject(raw) }.getOrNull()
    if (obj != null) {
        vid = obj.s("video_id").ifBlank { obj.s("url") }.trim()
        title = obj.s("title")
        active = obj.optBoolean("active", true)
    }
    val id = Regex("(?:youtu\\.be/|youtube(?:-nocookie)?\\.com/(?:watch\\?(?:[^#]*&)?v=|live/|embed/|shorts/|v/))([A-Za-z0-9_-]{11})").find(vid)?.groupValues?.get(1)
        ?: vid.takeIf { Regex("^[A-Za-z0-9_-]{11}$").matches(it) }
    return if (active && id != null) Pair(id, title) else null
}

/* Lightweight YouTube embed - a plain WebView pointed at youtube.com/embed/<id>. */
@Composable
fun YouTubeEmbed(url: String, modifier: Modifier = Modifier) {
    val id = remember(url) {
        Regex("^[A-Za-z0-9_-]{11}$").find(url.trim())?.value
            ?: Regex("(?:youtube\\.com/(?:watch\\?.*v=|live/|shorts/|embed/)|youtu\\.be/)([A-Za-z0-9_-]{6,20})").find(url)?.groupValues?.get(1)
    }
    if (id == null) {
        Text("Open the stream", color = Gold, fontSize = 13.sp, modifier = Modifier.clickable {
            val i = android.content.Intent(android.content.Intent.ACTION_VIEW, android.net.Uri.parse(url))
            i.addFlags(android.content.Intent.FLAG_ACTIVITY_NEW_TASK); Api.ctx.startActivity(i)
        }.padding(4.dp))
        return
    }
    androidx.compose.ui.viewinterop.AndroidView(factory = { ctx ->
        try {
            android.webkit.WebView(ctx).apply {
                settings.javaScriptEnabled = true
                settings.mediaPlaybackRequiresUserGesture = false
                settings.domStorageEnabled = true
                settings.loadsImagesAutomatically = true
                // hardware layer + chrome client: without these HTML5 video renders as a black box on many devices
                setLayerType(android.view.View.LAYER_TYPE_HARDWARE, null)
                webChromeClient = android.webkit.WebChromeClient()
                webViewClient = object : android.webkit.WebViewClient() {
                    override fun onRenderProcessGone(view: android.webkit.WebView, detail: android.webkit.RenderProcessGoneDetail): Boolean {
                        view.destroy()
                        return true
                    }
                    override fun onReceivedError(view: android.webkit.WebView, request: android.webkit.WebResourceRequest, error: android.webkit.WebResourceError) {
                        if (request.isForMainFrame) view.loadUrl("https://m.youtube.com/watch?v=" + id)
                    }
                }
                val html = "<!doctype html><html><head><meta name='viewport' content='width=device-width,initial-scale=1'>" +
                    "<style>html,body{margin:0;padding:0;background:#0a0a14;height:100%}iframe{border:0;width:100%;height:100%;border-radius:12px}</style></head>" +
                    "<body><iframe src='https://www.youtube-nocookie.com/embed/" + id + "?rel=0&modestbranding=1&playsinline=1&iv_load_policy=3&color=white&autoplay=1&mute=1' allowfullscreen allow='autoplay; encrypted-media; picture-in-picture'></iframe></body></html>"
                loadDataWithBaseURL("https://www.youtube-nocookie.com/", html, "text/html", "utf-8", null)
            }
        } catch (t: Throwable) {
            android.view.View(ctx) // no WebView on this device - the fallback link below stays visible
        }
    }, modifier = modifier)
}
