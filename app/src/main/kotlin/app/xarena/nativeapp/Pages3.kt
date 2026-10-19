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
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import org.json.JSONArray
import org.json.JSONObject

/* ============ Search / Teams / Players - one real 3-tab screen (same endpoints as the site's search page) ============ */
@Composable
fun SearchScreen(onBack: () -> Unit, initialTab: Int = 0, title: String = "Search", onOpenPlayer: (String) -> Unit = {}) {
    var tab by remember { mutableIntStateOf(initialTab) }
    var q by remember { mutableStateOf("") }
    Column(Modifier.fillMaxSize().background(Ink)) {
        PageHeader(title, "Squads, players, tournaments - live data", onBack)
        OutlinedTextField(q, { q = it }, placeholder = { Text("Search...") }, singleLine = true,
            modifier = Modifier.fillMaxWidth().padding(horizontal = 16.dp, vertical = 4.dp))
        LoadState2(0, {
            val squads = runCatching { Api.publicGet("/rest/v1/public_squads?select=squad,ign,verified&order=created_at.asc") }.getOrElse { JSONArray() }
            val players = runCatching { Api.playerProfiles() }.getOrElse { JSONArray() }
            val ts = runCatching { Api.publicGet("/rest/v1/tournaments?select=name,mode,status,prize,date_text&order=sort.asc") }.getOrElse { JSONArray() }
            Triple(squads, players, ts)
        }) { (squads, players, ts) ->
            val tabs = listOf("Squads", "Players", "Tournaments")
            Row(Modifier.padding(horizontal = 16.dp, vertical = 4.dp)) {
                tabs.forEachIndexed { i, t ->
                    Text(t, color = if (tab == i) Ink else Muted, fontSize = 12.sp, fontWeight = FontWeight.Bold,
                        modifier = Modifier.padding(end = 6.dp).clip(RoundedCornerShape(8.dp))
                            .background(if (tab == i) Gold else Color(0x22FFFFFF))
                            .clickable { tab = i }.padding(horizontal = 12.dp, vertical = 7.dp))
                }
            }
            val needle = q.trim()
            LazyColumn {
                when (tab) {
                    0 -> {
                        val rows = (0 until squads.length()).map { squads.getJSONObject(it) }
                            .filter { needle.isBlank() || it.s("squad").contains(needle, true) || it.s("ign").contains(needle, true) }
                        if (rows.isEmpty()) item { Text("No squads found.", color = Muted, fontSize = 13.sp, modifier = Modifier.padding(16.dp)) }
                        items(rows) { r ->
                            SmallCard {
                                Row(verticalAlignment = Alignment.CenterVertically) {
                                    Text(r.s("squad"), color = Color.White, fontSize = 15.sp, fontWeight = FontWeight.Bold)
                                    if (r.optBoolean("verified")) {
                                        Spacer(Modifier.width(8.dp))
                                        Text("VERIFIED", color = Gold2, fontSize = 9.sp, fontWeight = FontWeight.Bold,
                                            modifier = Modifier.clip(RoundedCornerShape(4.dp)).border(1.dp, Gold2, RoundedCornerShape(4.dp)).padding(horizontal = 5.dp, vertical = 1.dp))
                                    }
                                }
                                Text("Captain IGN: " + r.s("ign").ifBlank { "-" }, color = Muted, fontSize = 12.sp)
                            }
                        }
                    }
                    1 -> {
                        val rows = (0 until players.length()).map { players.getJSONObject(it) }
                            .filter { needle.isBlank() || it.s("ign").contains(needle, true) || it.s("username").contains(needle, true) }
                        if (rows.isEmpty()) item { Text("No players found.", color = Muted, fontSize = 13.sp, modifier = Modifier.padding(16.dp)) }
                        items(rows) { r ->
                            val nm = r.s("ign").ifBlank { r.s("username") }.ifBlank { "Player" }
                            val online = runCatching { java.time.Instant.parse(r.s("last_seen_at")).toEpochMilli() > System.currentTimeMillis() - 120000 }.getOrDefault(false)
                            Row(Modifier.fillMaxWidth().padding(horizontal = 16.dp, vertical = 4.dp).clip(RoundedCornerShape(12.dp)).background(Card).border(1.dp, Gold.copy(alpha = .2f), RoundedCornerShape(12.dp))
                                .clickable { onOpenPlayer(r.s("id")) }.padding(12.dp), verticalAlignment = Alignment.CenterVertically) {
                                Box(Modifier.size(44.dp).clip(androidx.compose.foundation.shape.CircleShape).background(Color(0x33FFFFFF)), contentAlignment = Alignment.Center) {
                                    val av = r.s("avatar_url")
                                    if (av.isNotBlank()) coil.compose.AsyncImage(model = xrImageModel(androidx.compose.ui.platform.LocalContext.current, av), contentDescription = nm, modifier = Modifier.fillMaxSize(), contentScale = androidx.compose.ui.layout.ContentScale.Crop)
                                    else Text(nm.first().uppercase(), color = Gold, fontSize = 17.sp, fontWeight = FontWeight.Black)
                                }
                                Spacer(Modifier.width(12.dp))
                                Column(Modifier.weight(1f)) {
                                    Row(verticalAlignment = Alignment.CenterVertically) {
                                        Text(nm, color = Color.White, fontSize = 15.sp, fontWeight = FontWeight.Bold)
                                        if (r.optBoolean("verified")) {
                                            Spacer(Modifier.width(6.dp))
                                            Text("VERIFIED", color = Gold2, fontSize = 8.sp, fontWeight = FontWeight.Bold,
                                                modifier = Modifier.clip(RoundedCornerShape(4.dp)).border(1.dp, Gold2, RoundedCornerShape(4.dp)).padding(horizontal = 4.dp, vertical = 1.dp))
                                        }
                                    }
                                    Text(buildList {
                                        if (r.s("username").isNotBlank()) add("@" + r.s("username"))
                                        if (r.s("role").isNotBlank()) add(r.s("role"))
                                        if (!r.isNull("kd")) add("K/D " + r.s("kd"))
                                    }.joinToString(" - ").ifBlank { "XARENA player" }, color = Muted, fontSize = 12.sp, maxLines = 1)
                                }
                                if (online) Box(Modifier.size(9.dp).clip(androidx.compose.foundation.shape.CircleShape).background(Color(0xFF4ADE80)))
                            }
                        }
                    }
                    else -> {
                        val rows = (0 until ts.length()).map { ts.getJSONObject(it) }
                            .filter { needle.isBlank() || it.s("name").contains(needle, true) }
                        if (rows.isEmpty()) item { Text("No tournaments found.", color = Muted, fontSize = 13.sp, modifier = Modifier.padding(16.dp)) }
                        items(rows) { r ->
                            SmallCard {
                                Text(r.s("status").uppercase(), color = Gold, fontSize = 10.sp, fontWeight = FontWeight.Bold)
                                Text(r.s("name"), color = Color.White, fontSize = 15.sp, fontWeight = FontWeight.Bold)
                                Text(listOf(r.s("mode"), r.s("date_text"), r.s("prize"))
                                    .filter { it.isNotBlank() }.joinToString(" - "), color = Muted, fontSize = 12.sp)
                            }
                        }
                    }
                }
                item { Spacer(Modifier.height(20.dp)) }
            }
        }
    }
}

/* ============ How to Play (same 3 steps as the site) ============ */
@Composable
fun HowToPlayScreen(onBack: () -> Unit) {
    Column(Modifier.fillMaxSize().background(Ink)) {
        PageHeader("How to Play", "Get started in 3 easy steps", onBack)
        LazyColumn {
            items(listOf(
                Triple("01", "Register Squad", "Create or join your squad on the Squads page. Invite friends and lock your team before battle."),
                Triple("02", "Check-in", "Check-in opens 1 hour before match time and closes 15 minutes after start - confirm your slot from the Match Center."),
                Triple("03", "Play & Earn XP", "Compete in matches, win to earn XP, climb the leaderboard and unlock exclusive rewards. XP only - never cash.")
            )) { (n, t, d) ->
                SmallCard {
                    Row {
                        Text(n, color = Gold, fontSize = 26.sp, fontWeight = FontWeight.Black)
                        Spacer(Modifier.width(14.dp))
                        Column {
                            Text(t, color = Color.White, fontSize = 16.sp, fontWeight = FontWeight.Bold)
                            Text(d, color = Muted, fontSize = 13.sp)
                        }
                    }
                }
            }
            item {
                Text("GOT IT, LET'S PLAY", color = Ink, fontSize = 13.sp, fontWeight = FontWeight.Bold,
                    modifier = Modifier.padding(16.dp).fillMaxWidth().clip(RoundedCornerShape(10.dp)).background(Gold)
                        .clickable { onBack() }.padding(vertical = 12.dp).wrapContentWidth(Alignment.CenterHorizontally))
            }
        }
    }
}
