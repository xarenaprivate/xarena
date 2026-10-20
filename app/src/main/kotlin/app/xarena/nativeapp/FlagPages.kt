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
                    val ms = Api.publicGet("/rest/v1/matches_public?select=*&order=starts_at.asc.nullslast&limit=20")
                    val yt = runCatching { Api.publicGet("/rest/v1/site_settings?key=eq.youtube_live&select=value") }.getOrElse { JSONArray() }
                    JSONObject().put("matches", ms).put("yt", if (yt.length() > 0) yt.getJSONObject(0).s("value") else "")
                }) { d ->
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
                                Text(listOf(m.s("starts_at").take(16).replace("T", " "), m.s("status").uppercase()).filter { it.isNotBlank() }.joinToString(" · "),
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
