package app.xarena.nativeapp

import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.Text
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import org.json.JSONArray
import kotlinx.coroutines.launch
import org.json.JSONObject

/* Remote feature flags (site_settings key "flags"). Admin-toggled on the site's admin panel.
   Everything here ships OFF-ready: hidden while its flag is false, no fake buttons ever. */
object Flags {
    @Volatile private var cached: JSONObject? = null
    @Volatile private var fetchedAt = 0L
    suspend fun get(): JSONObject {
        val now = System.currentTimeMillis()
        if (cached != null && now - fetchedAt < 5 * 60_000) return cached!!
        val v = try {
            val a = Api.publicGet("/rest/v1/site_settings?key=eq.flags&select=value")
            if (a.length() > 0) a.getJSONObject(0).optJSONObject("value") ?: JSONObject() else JSONObject()
        } catch (e: Exception) { cached ?: JSONObject() }
        cached = v; fetchedAt = now
        return v
    }
    suspend fun on(key: String): Boolean = get().optBoolean(key, false)
}

/** Spectator page (flag: spectator). Real matches from matches_public; stream link only when a real
    YouTube live is set by the admin. Never renders room IDs or passwords - spectators never see those. */
@Composable
fun SpectatorScreen(onBack: () -> Unit) {
    var allowed by remember { mutableStateOf<Boolean?>(null) }
    LaunchedEffect(Unit) { allowed = Flags.on("spectator") }
    Column(Modifier.fillMaxSize().background(Ink)) {
        PageHeader("Spectator Room", "Watch tournament matches live", onBack)
        when (allowed) {
            null -> Box(Modifier.fillMaxSize(), contentAlignment = Alignment.Center) { Text("Loading...", color = Muted) }
            false -> Box(Modifier.fillMaxSize().padding(28.dp)) {
                PanelCard { Text("Not open yet", color = Color.White, fontSize = 16.sp, fontWeight = FontWeight.Bold)
                    Text("The spectator room opens when the owner switches it on. Match streams are announced on Home.", color = Muted, fontSize = 13.sp) } }
            true -> {
                var tick by remember { mutableIntStateOf(0) }
                LoadState2(tick, {
                    // spectator_info (web batch-7 contract): never returns room_id/room_pass; ok:false while flag off
                    val tourney = Api.regTournament()
                    val info = runCatching { Api.publicRpc("spectator_info", JSONObject().put("p_tournament", tourney)) }.getOrNull()
                    val ms = Api.publicGet("/rest/v1/matches_public?select=*&order=starts_at.asc.nullslast&limit=20")
                    val yt = runCatching { Api.publicGet("/rest/v1/site_settings?key=eq.youtube_live&select=value") }.getOrElse { JSONArray() }
                    JSONObject().put("matches", ms).put("yt", if (yt.length() > 0) yt.getJSONObject(0).s("value") else "").put("info", info)
                }) { d ->
                    val info = d.optJSONObject("info")
                    if (info != null && !info.optBoolean("ok")) {
                        PanelCard { Text(info.s("error").ifBlank { "Spectator info is not available yet." }, color = Muted, fontSize = 13.sp) }
                    }
                    if (info != null && info.optBoolean("ok")) {
                        PanelCard {
                            Text("LIVE SPECTATING", color = Gold, fontSize = 11.sp, fontWeight = FontWeight.Bold)
                            val keys = info.keys().asSequence().filter { it !in setOf("ok", "room_id", "room_pass", "pass", "password") }.toList()
                            for (k in keys) {
                                val v = info.opt(k)?.toString() ?: continue
                                if (v.isBlank() || v == "null") continue
                                if (v.startsWith("http")) {
                                    Spacer(Modifier.height(6.dp))
                                    GoldButton("WATCH STREAM") {
                                        runCatching { Api.ctx.startActivity(android.content.Intent(android.content.Intent.ACTION_VIEW, android.net.Uri.parse(v))) }
                                    }
                                } else Text(k.replace("_", " ").uppercase() + ": " + v, color = Color.White, fontSize = 13.sp, modifier = Modifier.padding(top = 4.dp))
                            }
                        }
                    }
                    val arr = d.optJSONArray("matches") ?: JSONArray()
                    val ytUrl = d.s("yt")
                    if (arr.length() == 0) Box(Modifier.padding(28.dp)) {
                        PanelCard { Text("No matches scheduled yet.", color = Muted, fontSize = 13.sp) } }
                    else LazyColumn(Modifier.fillMaxSize()) {
                        items((0 until arr.length()).map { arr.getJSONObject(it) }) { m ->
                            val ctx = LocalContext.current
                            PanelCard {
                                Text(m.s("title").ifBlank { m.s("name").ifBlank { "Match" } }, color = Color.White, fontSize = 15.sp, fontWeight = FontWeight.Bold)
                                val teams = listOf(m.s("team_a"), m.s("team_b")).filter { it.isNotBlank() }
                                if (teams.isNotEmpty()) Text(teams.joinToString("  VS  "), color = Color.White, fontSize = 13.sp)
                                Text(listOf(m.s("starts_at").take(16).replace("T", " "), MatchLabels.label(m)).filter { it.isNotBlank() }.joinToString(" · "),
                                    color = Muted, fontSize = 12.sp)
                                if (ytUrl.startsWith("http")) {
                                    Spacer(Modifier.height(8.dp))
                                    GoldButton("WATCH STREAM") {
                                        runCatching { ctx.startActivity(android.content.Intent(android.content.Intent.ACTION_VIEW, android.net.Uri.parse(ytUrl))) }
                                    }
                                }
                            }
                        }
                    }
                }
            }
        }
    }
}

/** Weekly mini-cup (flag: mini_cup). Real tournaments whose name marks them as a mini cup. */
@Composable
fun MiniCupScreen(onBack: () -> Unit, onRegister: () -> Unit) {
    var allowed by remember { mutableStateOf<Boolean?>(null) }
    LaunchedEffect(Unit) { allowed = Flags.on("mini_cup") }
    Column(Modifier.fillMaxSize().background(Ink)) {
        PageHeader("Weekly Mini-Cup", "A quick cup every week", onBack)
        when (allowed) {
            null -> Box(Modifier.fillMaxSize(), contentAlignment = Alignment.Center) { Text("Loading...", color = Muted) }
            false -> Box(Modifier.fillMaxSize().padding(28.dp)) {
                PanelCard { Text("Not open yet", color = Color.White, fontSize = 16.sp, fontWeight = FontWeight.Bold)
                    Text("The weekly mini-cup starts after the Ignition Cup. It appears here the moment the owner opens it.", color = Muted, fontSize = 13.sp) } }
            true -> {
                var tick by remember { mutableIntStateOf(0) }
                LoadState2(tick, {
                    Api.publicGet("/rest/v1/tournaments?name=ilike.*mini*&select=id,name,status,slots,prize,date_text,reg_open&order=sort.asc")
                }) { arr ->
                    if (arr.length() == 0) Box(Modifier.padding(28.dp)) {
                        PanelCard { Text("No mini-cup announced this week - check back soon.", color = Muted, fontSize = 13.sp) } }
                    else LazyColumn(Modifier.fillMaxSize()) {
                        items((0 until arr.length()).map { arr.getJSONObject(it) }) { t ->
                            PanelCard {
                                Text(t.s("name"), color = Gold2, fontSize = 16.sp, fontWeight = FontWeight.Bold)
                                Text(listOf(t.s("date_text"), t.s("prize"), if (t.optInt("slots", 0) > 0) t.optInt("slots").toString() + " slots" else "")
                                    .filter { it.isNotBlank() }.joinToString(" · "), color = Muted, fontSize = 12.sp)
                                if (t.optBoolean("reg_open")) {
                                    Spacer(Modifier.height(8.dp))
                                    GoldButton("REGISTER YOUR SQUAD", onClick = onRegister)
                                }
                            }
                        }
                    }
                }
            }
        }
    }
}

/** Live match thread (flag: live_thread). Web batch-7 contract: thread_list/thread_post/thread_report.
    Server returns ok:false while the flag is off; posts auto-hide at 3 reports server-side. */
@Composable
fun LiveThreadScreen(onBack: () -> Unit) {
    var allowed by remember { mutableStateOf<Boolean?>(null) }
    LaunchedEffect(Unit) { allowed = Flags.on("live_thread") }
    Column(Modifier.fillMaxSize().background(Ink)) {
        PageHeader("Live Match Thread", "Talk during the match - be respectful", onBack)
        when (allowed) {
            null -> Box(Modifier.fillMaxSize(), contentAlignment = Alignment.Center) { Text("Loading...", color = Muted) }
            false -> Box(Modifier.fillMaxSize().padding(28.dp)) {
                PanelCard { Text("Not open yet", color = Color.White, fontSize = 16.sp, fontWeight = FontWeight.Bold)
                    Text("The live match thread opens when the owner switches it on for a match.", color = Muted, fontSize = 13.sp) } }
            true -> {
                var tick by remember { mutableIntStateOf(0) }
                LoadState2(tick, {
                    val ms = runCatching { Api.publicGet("/rest/v1/matches_public?select=*&order=starts_at.desc.nullslast&limit=1") }.getOrElse { JSONArray() }
                    if (ms.length() == 0) JSONObject().put("posts", JSONArray()).put("nomatch", true)
                    else {
                        val mid = ms.getJSONObject(0).s("id")
                        val r = runCatching { Api.authedRpc("thread_list", JSONObject().put("p_match", mid).put("p_limit", 60)) }.getOrNull()
                        JSONObject().put("match", ms.getJSONObject(0)).put("resp", r)
                    }
                }) { d ->
                    if (d.optBoolean("nomatch")) {
                        PanelCard { Text("No match to discuss yet.", color = Muted, fontSize = 13.sp) }
                        return@LoadState2
                    }
                    val m = d.optJSONObject("match")
                    val r = d.optJSONObject("resp")
                    if (m != null) Text(m.s("title").ifBlank { m.s("name").ifBlank { "Match" } }, color = Gold2, fontSize = 15.sp,
                        fontWeight = FontWeight.Bold, modifier = Modifier.padding(horizontal = 16.dp, vertical = 4.dp))
                    if (r == null || !r.optBoolean("ok", true)) {
                        PanelCard { Text(r?.s("error")?.ifBlank { "The thread is not open for this match." } ?: "The thread is not open for this match.", color = Muted, fontSize = 13.sp) }
                        return@LoadState2
                    }
                    val posts = r.optJSONArray("posts") ?: r.optJSONArray("thread") ?: JSONArray()
                    var draft by remember { mutableStateOf("") }
                    var msg by remember { mutableStateOf<String?>(null) }
                    val scope = rememberCoroutineScope()
                    LazyColumn(Modifier.weight(1f)) {
                        items((0 until posts.length()).map { posts.getJSONObject(it) }.asReversed()) { p ->
                            Column(Modifier.fillMaxWidth().padding(horizontal = 16.dp, vertical = 3.dp)) {
                                Row(verticalAlignment = Alignment.CenterVertically) {
                                    Text(p.s("name").ifBlank { p.s("author").ifBlank { "Player" } }, color = Gold, fontSize = 12.sp, fontWeight = FontWeight.Bold)
                                    Spacer(Modifier.weight(1f))
                                    Text("report", color = Muted, fontSize = 10.sp, modifier = Modifier.clickable {
                                        val pid = p.optLong("id", -1)
                                        if (pid > 0) scope.launch {
                                            val rr = runCatching { Api.authedRpc("thread_report", JSONObject().put("p_post", pid).put("p_reason", "reported from app")) }.getOrNull()
                                            msg = if (rr?.optBoolean("ok") == true) "Reported - mods review it." else rr?.s("error") ?: "Could not report."
                                        }
                                    }.padding(4.dp))
                                }
                                Text(p.s("body"), color = Color.White, fontSize = 13.sp)
                            }
                        }
                        if (posts.length() == 0) item { Text("No messages yet - say something nice.", color = Muted, fontSize = 12.sp, modifier = Modifier.padding(16.dp)) }
                    }
                    msg?.let { Text(it, color = Gold2, fontSize = 11.sp, modifier = Modifier.padding(horizontal = 16.dp)) }
                    Row(Modifier.fillMaxWidth().padding(12.dp), verticalAlignment = Alignment.CenterVertically) {
                        androidx.compose.material3.OutlinedTextField(draft, { draft = it.take(200) }, placeholder = { Text("Message...") },
                            singleLine = true, modifier = Modifier.weight(1f))
                        Spacer(Modifier.width(8.dp))
                        GoldButton("SEND", enabled = draft.isNotBlank(), modifier = Modifier.width(96.dp)) {
                            val body = draft.trim(); draft = ""
                            scope.launch {
                                val mid = m?.s("id") ?: return@launch
                                val sr = runCatching { Api.authedRpc("thread_post", JSONObject().put("p_match", mid).put("p_body", body)) }.getOrNull()
                                if (sr?.optBoolean("ok") == true) tick++ else msg = sr?.s("error") ?: "Could not send."
                            }
                        }
                    }
                }
            }
        }
    }
}
