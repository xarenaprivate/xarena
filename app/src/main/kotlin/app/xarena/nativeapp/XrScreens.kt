package app.xarena.nativeapp

import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.Text
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.LocalClipboardManager
import androidx.compose.ui.text.AnnotatedString
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import kotlinx.coroutines.delay
import kotlinx.coroutines.launch
import org.json.JSONArray
import org.json.JSONObject

/* Release A (v77): Tournament Details, Room ID & Password, Season Podium, Results & Prizes.
   Additive screens - real data only, honest empty/locked states, existing flows untouched. */

private fun xrFmtWhen(iso: String): String = try {
    val t = java.time.Instant.parse(iso)
    java.time.format.DateTimeFormatter.ofPattern("EEE d MMM, h:mm a").withZone(java.time.ZoneId.systemDefault()).format(t)
} catch (e: Exception) { iso }

private fun xrChip(text: String, color: Color): @Composable () -> Unit = {
    Text(text, color = color, fontSize = 11.sp, fontWeight = FontWeight.Bold,
        modifier = Modifier.clip(RoundedCornerShape(8.dp)).border(1.dp, color, RoundedCornerShape(8.dp)).padding(horizontal = 10.dp, vertical = 4.dp))
}

@Composable
fun TournamentDetailsScreen(onBack: () -> Unit, onJoin: (String) -> Unit) {
    var tick by remember { mutableIntStateOf(0) }
    Column(Modifier.fillMaxSize().background(Ink)) {
        PageHeader("Tournament Details", "Format, prizes, schedule and slots", onBack)
        LoadState2(tick, {
            val ts = Api.publicGet("/rest/v1/tournaments?select=id,name,game,mode,status,date_text,start_at,prize,slots,reg_open,description,entry_cost_ap&order=sort.asc")
            var t: JSONObject? = null
            for (i in 0 until ts.length()) { val c = ts.getJSONObject(i); if (c.optBoolean("reg_open")) { t = c; break } }
            if (t == null && ts.length() > 0) t = ts.getJSONObject(0)
            var matches = JSONArray()
            var slotsSummary: JSONObject? = null
            if (t != null) {
                val tid = t!!.s("id")
                matches = runCatching { Api.publicGet("/rest/v1/matches_public?select=id,title,starts_at,status&tournament_id=eq." + java.net.URLEncoder.encode(tid, "UTF-8") + "&order=starts_at.asc") }.getOrElse { JSONArray() }
                slotsSummary = runCatching { Api.publicRpc("xr_slot_list", JSONObject().put("p_tournament", tid)) }.getOrNull()
            }
            JSONObject().put("t", t ?: JSONObject.NULL).put("matches", matches).put("slots", slotsSummary ?: JSONObject.NULL)
        }) { d ->
            if (d.isNull("t")) { Text("No tournament is published yet.", color = Muted, fontSize = 14.sp, modifier = Modifier.padding(24.dp)); return@LoadState2 }
            val t = d.getJSONObject("t")
            LazyColumn(contentPadding = PaddingValues(16.dp), verticalArrangement = Arrangement.spacedBy(10.dp)) {
                item {
                    PanelCard {
                        Text("TOURNAMENT", color = Muted, fontSize = 11.sp, fontWeight = FontWeight.Bold)
                        Text(t.s("name"), color = Gold2, fontSize = 22.sp, fontWeight = FontWeight.Bold)
                        Spacer(Modifier.height(6.dp))
                        Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                            xrChip(t.s("game").ifBlank { "Free Fire" }, Color.White)()
                            xrChip(t.s("mode").ifBlank { "Squad" }, Color.White)()
                            val st = if (t.optBoolean("reg_open")) "REGISTRATION OPEN" else t.s("status").uppercase()
                            xrChip(st, if (t.optBoolean("reg_open")) Color(0xFF4ADE80) else Muted)()
                        }
                    }
                }
                item {
                    PanelCard {
                        Text("DETAILS", color = Muted, fontSize = 11.sp, fontWeight = FontWeight.Bold)
                        Spacer(Modifier.height(4.dp))
                        val startIso = t.s("start_at")
                        XrInfoRow("Date", if (startIso.isNotBlank()) xrFmtWhen(startIso) else t.s("date_text"))
                        XrInfoRow("Entry", if (t.optInt("entry_cost_ap", 0) > 0) t.optInt("entry_cost_ap").toString() + " AP" else "Free")
                        val slotCount = if (!d.isNull("slots")) d.getJSONObject("slots").optInt("capacity", t.optInt("slots")) else t.optInt("slots")
                        XrInfoRow("Slots", slotCount.toString() + " squads")
                        if (t.s("prize").isNotBlank()) XrInfoRow("Prize pool", t.s("prize"))
                    }
                }
                if (t.s("description").isNotBlank()) item {
                    PanelCard {
                        Text("RULES AND FORMAT", color = Muted, fontSize = 11.sp, fontWeight = FontWeight.Bold)
                        Spacer(Modifier.height(4.dp))
                        Text(t.s("description"), color = Color.White, fontSize = 13.sp)
                    }
                }
                item {
                    PanelCard {
                        Text("SCHEDULE", color = Muted, fontSize = 11.sp, fontWeight = FontWeight.Bold)
                        Spacer(Modifier.height(4.dp))
                        val ms = d.getJSONArray("matches")
                        if (ms.length() == 0) Text("Match schedule will be announced here.", color = Muted, fontSize = 12.sp)
                        else for (i in 0 until ms.length()) {
                            val m = ms.getJSONObject(i)
                            Row(Modifier.fillMaxWidth().padding(vertical = 4.dp), verticalAlignment = Alignment.CenterVertically) {
                                Text(m.s("title"), color = Color.White, fontSize = 13.sp, fontWeight = FontWeight.Bold, modifier = Modifier.weight(1f))
                                Text(xrFmtWhen(m.s("starts_at")), color = Muted, fontSize = 11.sp)
                            }
                        }
                    }
                }
                item {
                    if (t.optBoolean("reg_open")) GoldButton("JOIN TOURNAMENT", onClick = { onJoin(t.s("id")) })
                    else Text("Registration is closed for this tournament.", color = Muted, fontSize = 12.sp, modifier = Modifier.padding(horizontal = 8.dp))
                }
            }
        }
    }
}

@Composable
private fun XrInfoRow(label: String, value: String) {
    Row(Modifier.fillMaxWidth().padding(vertical = 3.dp)) {
        Text(label, color = Muted, fontSize = 12.sp, modifier = Modifier.width(90.dp))
        Text(value, color = Color.White, fontSize = 13.sp, fontWeight = FontWeight.Bold)
    }
}

@Composable
fun MatchRoomScreen(onBack: () -> Unit) {
    var tick by remember { mutableIntStateOf(0) }
    Column(Modifier.fillMaxSize().background(Ink)) {
        PageHeader("Room ID & Password", "Unlocks 15 minutes before the match", onBack)
        LoadState2(tick, {
            val ms = Api.publicGet("/rest/v1/matches_public?select=id,title,starts_at,status,tournament_id&status=neq.done&order=starts_at.asc&limit=1")
            val m = if (ms.length() > 0) ms.getJSONObject(0) else null
            var room: JSONObject? = null
            if (m != null) room = runCatching { Api.authedRpc("match_room", JSONObject().put("p_match", m.s("id"))) }.getOrNull()
            JSONObject().put("m", m ?: JSONObject.NULL).put("room", room ?: JSONObject.NULL)
        }) { d ->
            if (d.isNull("m")) { Text("No upcoming match right now - the next room appears here.", color = Muted, fontSize = 14.sp, modifier = Modifier.padding(24.dp)); return@LoadState2 }
            val m = d.getJSONObject("m")
            val startMs = runCatching { java.time.Instant.parse(m.s("starts_at")).toEpochMilli() }.getOrDefault(0L)
            val revealMs = startMs - 15L * 60L * 1000L
            LazyColumn(contentPadding = PaddingValues(16.dp), verticalArrangement = Arrangement.spacedBy(10.dp)) {
                item {
                    PanelCard {
                        Text(m.s("title"), color = Color.White, fontSize = 16.sp, fontWeight = FontWeight.Bold)
                        Text(m.s("tournament_id") + " - " + xrFmtWhen(m.s("starts_at")), color = Muted, fontSize = 12.sp)
                    }
                }
                val room = if (d.isNull("room")) null else d.getJSONObject("room")
                if (room != null && room.optBoolean("ok")) {
                    item {
                        PanelCard {
                            Text("ROOM IS OPEN", color = Color(0xFF4ADE80), fontSize = 11.sp, fontWeight = FontWeight.Bold)
                            Spacer(Modifier.height(6.dp))
                            XrCopyRow("Room ID", room.s("room_id"))
                            if (room.s("room_pass").isNotBlank()) XrCopyRow("Password", room.s("room_pass"))
                        }
                    }
                    item {
                        GoldButton("OPEN FREE FIRE") {
                            val pm = Api.ctx.packageManager
                            val i = pm.getLaunchIntentForPackage("com.dts.freefireth") ?: pm.getLaunchIntentForPackage("com.dts.freefiremax")
                            if (i != null) Api.ctx.startActivity(i)
                        }
                    }
                } else {
                    item {
                        var nowMs by remember { mutableLongStateOf(System.currentTimeMillis()) }
                        LaunchedEffect(Unit) { while (true) { delay(1000); nowMs = System.currentTimeMillis() } }
                        PanelCard {
                            Text("ROOM LOCKED", color = Gold2, fontSize = 11.sp, fontWeight = FontWeight.Bold)
                            Spacer(Modifier.height(6.dp))
                            val left = revealMs - nowMs
                            if (left > 0) {
                                val h = left / 3600000; val mi = (left % 3600000) / 60000; val se = (left % 60000) / 1000
                                Text(String.format("Unlocks in %02d:%02d:%02d", h, mi, se), color = Color.White, fontSize = 22.sp, fontWeight = FontWeight.Bold)
                            } else Text("Unlocking - pull to refresh in a moment.", color = Color.White, fontSize = 13.sp)
                            XrInfoRow("Room ID", "Locked")
                            XrInfoRow("Password", "Locked")
                        }
                    }
                    if (room != null && !room.optBoolean("ok") && room.s("error").isNotBlank()) item {
                        Text(room.s("error"), color = Muted, fontSize = 12.sp, modifier = Modifier.padding(horizontal = 8.dp))
                    }
                }
                item {
                    PanelCard {
                        Text("HOW TO JOIN", color = Muted, fontSize = 11.sp, fontWeight = FontWeight.Bold)
                        Spacer(Modifier.height(4.dp))
                        Text("1. Open Free Fire and tap Custom Room.\n2. Enter the Room ID and password above.\n3. Be inside the room 5 minutes before start - late entries are not allowed.\n4. Only registered players may join; the list is checked in the room.", color = Color.White, fontSize = 13.sp)
                    }
                }
            }
        }
    }
}

@Composable
private fun XrCopyRow(label: String, value: String) {
    val clipboard = LocalClipboardManager.current
    var copied by remember { mutableStateOf(false) }
    Row(Modifier.fillMaxWidth().padding(vertical = 4.dp), verticalAlignment = Alignment.CenterVertically) {
        Column(Modifier.weight(1f)) {
            Text(label, color = Muted, fontSize = 11.sp)
            Text(value, color = Gold2, fontSize = 18.sp, fontWeight = FontWeight.Bold)
        }
        Text(if (copied) "COPIED" else "COPY", color = if (copied) Color(0xFF4ADE80) else Gold, fontSize = 12.sp, fontWeight = FontWeight.Bold,
            modifier = Modifier.clip(RoundedCornerShape(8.dp)).border(1.dp, if (copied) Color(0xFF4ADE80) else Gold, RoundedCornerShape(8.dp))
                .clickable { clipboard.setText(AnnotatedString(value)); copied = true }.padding(horizontal = 12.dp, vertical = 6.dp))
    }
}

@Composable
fun PodiumScreen(onBack: () -> Unit) {
    var tick by remember { mutableIntStateOf(0) }
    Column(Modifier.fillMaxSize().background(Ink)) {
        PageHeader("Season Podium", "Top squads by points", onBack)
        LoadState2(tick, { Api.authedRpcRaw("points_table", JSONObject().put("p_tournament", JSONObject.NULL)).trim() }) { body ->
            val rows = if (body.startsWith("[")) JSONArray(body) else JSONArray()
            if (rows.length() == 0) {
                Text("No points yet - the table fills in after the first match.", color = Muted, fontSize = 14.sp, modifier = Modifier.padding(24.dp))
            } else LazyColumn(contentPadding = PaddingValues(16.dp), verticalArrangement = Arrangement.spacedBy(8.dp)) {
                item {
                    val n = minOf(3, rows.length())
                    Row(Modifier.fillMaxWidth().padding(vertical = 8.dp), horizontalArrangement = Arrangement.spacedBy(8.dp), verticalAlignment = Alignment.Bottom) {
                        val order = listOf(1, 0, 2)
                        for (idx in order) {
                            if (idx >= n) { Spacer(Modifier.weight(1f)); continue }
                            val r = rows.getJSONObject(idx)
                            val place = idx + 1
                            val h = if (place == 1) 120.dp else if (place == 2) 96.dp else 84.dp
                            val col = if (place == 1) Gold2 else if (place == 2) Color(0xFFC0C0C0) else Color(0xFFCD7F32)
                            Column(Modifier.weight(1f).clip(RoundedCornerShape(12.dp)).background(Look.cardBg).border(1.dp, col, RoundedCornerShape(12.dp)).height(h).padding(8.dp),
                                horizontalAlignment = Alignment.CenterHorizontally, verticalArrangement = Arrangement.Center) {
                                Text("#" + place, color = col, fontSize = 18.sp, fontWeight = FontWeight.Bold)
                                Text(r.s("squad"), color = Color.White, fontSize = 13.sp, fontWeight = FontWeight.Bold, maxLines = 1)
                                Text(r.optInt("points").toString() + " pts", color = Muted, fontSize = 11.sp)
                            }
                        }
                    }
                }
                items(rows.length()) { i ->
                    val r = rows.getJSONObject(i)
                    Row(Modifier.fillMaxWidth().clip(RoundedCornerShape(10.dp)).background(Look.cardBg).padding(horizontal = 10.dp, vertical = 10.dp),
                        verticalAlignment = Alignment.CenterVertically) {
                        Text(r.optInt("rank", i + 1).toString(), color = Gold2, fontSize = 13.sp, fontWeight = FontWeight.Bold, modifier = Modifier.width(30.dp))
                        Text(r.s("squad"), color = Color.White, fontSize = 13.sp, fontWeight = FontWeight.Bold, modifier = Modifier.weight(1f))
                        Text(r.optInt("kills").toString() + " kills", color = Muted, fontSize = 11.sp)
                        Spacer(Modifier.width(10.dp))
                        Text(r.optInt("points").toString() + " pts", color = Gold2, fontSize = 13.sp, fontWeight = FontWeight.Bold)
                    }
                }
            }
        }
    }
}

@Composable
fun ResultsScreen(onBack: () -> Unit, onOpenCerts: () -> Unit) {
    var tick by remember { mutableIntStateOf(0) }
    val scope = rememberCoroutineScope()
    Column(Modifier.fillMaxSize().background(Ink)) {
        PageHeader("Results & Prizes", "Champions and prize winners", onBack)
        LoadState2(tick, {
            val fame = runCatching { Api.publicGet("/rest/v1/hall_of_fame?select=category,name,detail,season,logo_url&order=id.desc&limit=20") }.getOrElse { JSONArray() }
            fame
        }) { fame ->
            if (fame.length() == 0) {
                Text("No results published yet - winners are announced here after the final.", color = Muted, fontSize = 14.sp, modifier = Modifier.padding(24.dp))
            } else LazyColumn(contentPadding = PaddingValues(16.dp), verticalArrangement = Arrangement.spacedBy(10.dp)) {
                var champion: JSONObject? = null
                for (i in 0 until fame.length()) { val r = fame.getJSONObject(i); if (r.s("category").contains("champion", true) || r.s("category").contains("winner", true)) { champion = r; break } }
                if (champion == null) champion = fame.getJSONObject(0)
                item {
                    PanelCard {
                        Text("CHAMPION", color = Muted, fontSize = 11.sp, fontWeight = FontWeight.Bold)
                        Text(champion!!.s("name"), color = Gold2, fontSize = 22.sp, fontWeight = FontWeight.Bold)
                        if (champion!!.s("detail").isNotBlank()) Text(champion!!.s("detail"), color = Color.White, fontSize = 13.sp)
                        if (champion!!.s("season").isNotBlank()) Text(champion!!.s("season"), color = Muted, fontSize = 11.sp)
                    }
                }
                item {
                    PanelCard {
                        Text("RESULTS", color = Muted, fontSize = 11.sp, fontWeight = FontWeight.Bold)
                        Spacer(Modifier.height(4.dp))
                        for (i in 0 until fame.length()) {
                            val r = fame.getJSONObject(i)
                            Row(Modifier.fillMaxWidth().padding(vertical = 4.dp), verticalAlignment = Alignment.CenterVertically) {
                                Column(Modifier.weight(1f)) {
                                    Text(r.s("name"), color = Color.White, fontSize = 13.sp, fontWeight = FontWeight.Bold)
                                    if (r.s("detail").isNotBlank()) Text(r.s("detail"), color = Muted, fontSize = 11.sp)
                                }
                                if (r.s("category").isNotBlank()) Text(r.s("category").uppercase(), color = Gold2, fontSize = 10.sp, fontWeight = FontWeight.Bold)
                            }
                        }
                    }
                }
                item {
                    Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                        Text("SHARE RESULT", color = Gold, fontSize = 12.sp, fontWeight = FontWeight.Bold,
                            modifier = Modifier.clip(RoundedCornerShape(10.dp)).border(1.dp, Gold, RoundedCornerShape(10.dp))
                                .clickable { scope.launch { val f = ShareCard.buildResultRecap(Api.ctx); if (f != null) ShareCard.share(Api.ctx, f) } }
                                .padding(horizontal = 14.dp, vertical = 8.dp))
                        Text("MY CERTIFICATES", color = Gold, fontSize = 12.sp, fontWeight = FontWeight.Bold,
                            modifier = Modifier.clip(RoundedCornerShape(10.dp)).border(1.dp, Gold, RoundedCornerShape(10.dp))
                                .clickable { onOpenCerts() }.padding(horizontal = 14.dp, vertical = 8.dp))
                    }
                }
            }
        }
    }
}
