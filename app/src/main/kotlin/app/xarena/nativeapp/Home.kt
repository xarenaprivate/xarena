package app.xarena.nativeapp

import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.LazyRow
import androidx.compose.foundation.lazy.items
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
        HomeData(ts, if (yt.length() > 0) yt.getJSONObject(0).optString("value") else "", matches, stats)
    }) { d ->
        LazyColumn(Modifier.fillMaxSize()) {
            item {
                Brand()
                // Featured tournament = first with open registration, else first in list
                val openIdx = (0 until d.ts.length()).indexOfFirst { d.ts.getJSONObject(it).optBoolean("reg_open") }
                val featIdx = if (openIdx >= 0) openIdx else if (d.ts.length() > 0) 0 else -1
                if (featIdx < 0) {
                    PanelCard { Text("No tournaments yet", color = Color.White, fontSize = 16.sp, fontWeight = FontWeight.Bold)
                        Text("Tournaments appear here as soon as the admin creates them.", color = Muted, fontSize = 13.sp) }
                } else {
                    val t = d.ts.getJSONObject(featIdx)
                    Column(Modifier.fillMaxWidth().padding(horizontal = 16.dp, vertical = 6.dp).clip(RoundedCornerShape(Look.cardRadius))
                        .background(Look.cardBg).border(1.dp, Gold.copy(alpha = .45f), RoundedCornerShape(Look.cardRadius)).padding(18.dp)) {
                        Text(t.optString("name"), color = Gold2, fontSize = 24.sp, fontWeight = FontWeight.Bold)
                        Text(listOf(t.optString("mode"), t.optString("date_text")).filter { it.isNotBlank() }.joinToString(" · "), color = Muted, fontSize = 12.sp)
                        if (t.optString("prize").isNotBlank()) {
                            Spacer(Modifier.height(8.dp))
                            Text("Prize: " + t.optString("prize"), color = Ink, fontSize = 12.sp, fontWeight = FontWeight.Bold,
                                modifier = Modifier.clip(RoundedCornerShape(8.dp)).background(Gold).padding(horizontal = 10.dp, vertical = 4.dp))
                        }
                        if (t.optInt("slots", 0) > 0) { Spacer(Modifier.height(4.dp)); Text(t.optInt("slots").toString() + " slots", color = Muted, fontSize = 12.sp) }
                        Spacer(Modifier.height(10.dp))
                        Text("VIEW DETAILS →", color = Gold, fontSize = 13.sp, fontWeight = FontWeight.Bold,
                            modifier = Modifier.clip(RoundedCornerShape(10.dp)).border(1.dp, Gold, RoundedCornerShape(10.dp))
                                .clickable { onGoTab(1) }.padding(horizontal = 16.dp, vertical = 8.dp))
                    }
                }
            }
            item {
                Row(Modifier.fillMaxWidth().padding(horizontal = 16.dp, vertical = 8.dp), verticalAlignment = Alignment.CenterVertically) {
                    Text("LIVE NOW", color = Gold, fontSize = 15.sp, fontWeight = FontWeight.Bold, modifier = Modifier.weight(1f))
                    Text("See all →", color = Muted, fontSize = 12.sp, modifier = Modifier.clickable { onOpenFeature("live") }.padding(4.dp))
                }
                val liveCards = mutableListOf<@Composable () -> Unit>()
                if (d.ytUrl.isNotBlank()) liveCards.add { LiveStreamCard { onOpenFeature("live") } }
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
                            Text(t.optString("name"), color = Gold2, fontSize = 15.sp, fontWeight = FontWeight.Bold, maxLines = 2)
                            Text(listOf(t.optString("date_text"), if (t.optInt("slots", 0) > 0) t.optInt("slots").toString() + " slots" else "").filter { it.isNotBlank() }.joinToString(" · "),
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

private data class HomeData(val ts: JSONArray, val ytUrl: String, val matches: JSONArray, val stats: JSONObject?)

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
        Text(m.optString("title").ifBlank { m.optString("name").ifBlank { "Match" } }, color = Gold2, fontSize = 14.sp, fontWeight = FontWeight.Bold, maxLines = 1)
        Spacer(Modifier.height(4.dp))
        val teams = listOf(m.optString("team_a"), m.optString("team_b")).filter { it.isNotBlank() }
        if (teams.isNotEmpty()) Text(teams.joinToString("  VS  "), color = Color.White, fontSize = 13.sp, fontWeight = FontWeight.Medium, maxLines = 1)
        Text(dmTime(m.optString("starts_at")), color = Muted, fontSize = 11.sp)
    }
}
