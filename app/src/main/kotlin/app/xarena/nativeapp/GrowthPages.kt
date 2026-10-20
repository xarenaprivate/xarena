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
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import kotlinx.coroutines.launch
import org.json.JSONArray
import org.json.JSONObject

/* Growth pages (Oct 20-21 big bundle): points table, head-to-head, squad profile, notice board, feedback box.
   All real backend data, honest empty states. */

@Composable
fun PointsTableScreen(onBack: () -> Unit) {
    var tick by remember { mutableIntStateOf(0) }
    Column(Modifier.fillMaxSize().background(Ink)) {
        PageHeader("Points Table", "Kills and placement points - entered by the organizer", onBack)
        val ctx = androidx.compose.ui.platform.LocalContext.current
        val scope2 = rememberCoroutineScope()
        Row(Modifier.padding(horizontal = 16.dp)) {
            Text("SHARE RESULT RECAP", color = Gold, fontSize = 12.sp, fontWeight = FontWeight.Bold,
                modifier = Modifier.clip(RoundedCornerShape(10.dp)).border(1.dp, Gold, RoundedCornerShape(10.dp))
                    .clickable {
                        scope2.launch {
                            val fx = ShareCard.buildResultRecap(Api.ctx)
                            if (fx != null) ShareCard.share(Api.ctx, fx)
                        }
                    }.padding(horizontal = 14.dp, vertical = 8.dp))
        }
        LoadState2(tick, { Api.authedRpcRaw("points_table", JSONObject().put("p_tournament", JSONObject.NULL)).trim() }) { body ->
            val rows = if (body.startsWith("[")) JSONArray(body) else JSONArray()
            if (rows.length() == 0) {
                SmallCard { Text("No points yet", color = Color.White, fontSize = 15.sp, fontWeight = FontWeight.Bold)
                    Text("The organizer enters kills and placement after each match - the table fills in here.", color = Muted, fontSize = 12.sp) }
            } else LazyColumn(contentPadding = PaddingValues(16.dp), verticalArrangement = Arrangement.spacedBy(6.dp)) {
                item { Row(Modifier.fillMaxWidth().padding(horizontal = 8.dp)) {
                    Text("#", color = Muted, fontSize = 11.sp, modifier = Modifier.width(28.dp))
                    Text("SQUAD", color = Muted, fontSize = 11.sp, modifier = Modifier.weight(1f))
                    Text("M", color = Muted, fontSize = 11.sp, modifier = Modifier.width(30.dp))
                    Text("KILLS", color = Muted, fontSize = 11.sp, modifier = Modifier.width(44.dp))
                    Text("PTS", color = Muted, fontSize = 11.sp, modifier = Modifier.width(44.dp))
                } }
                items((0 until rows.length()).map { rows.getJSONObject(it) }) { r ->
                    Row(Modifier.fillMaxWidth().clip(RoundedCornerShape(10.dp)).background(Look.cardBg).padding(horizontal = 8.dp, vertical = 10.dp),
                        verticalAlignment = Alignment.CenterVertically) {
                        Text(r.optInt("rank", 0).takeIf { it > 0 }?.toString() ?: "-", color = Gold2, fontSize = 13.sp, fontWeight = FontWeight.Bold, modifier = Modifier.width(28.dp))
                        Text(r.s("squad"), color = Color.White, fontSize = 13.sp, fontWeight = FontWeight.Bold, modifier = Modifier.weight(1f))
                        Text(r.optInt("matches").toString(), color = Muted, fontSize = 12.sp, modifier = Modifier.width(30.dp))
                        Text(r.optInt("kills").toString(), color = Muted, fontSize = 12.sp, modifier = Modifier.width(44.dp))
                        Text(r.optInt("points").toString(), color = Gold2, fontSize = 13.sp, fontWeight = FontWeight.Bold, modifier = Modifier.width(44.dp))
                    }
                }
            }
        }
    }
}

@Composable
fun HeadToHeadScreen(onBack: () -> Unit) {
    val scope = rememberCoroutineScope()
    var a by remember { mutableStateOf("") }
    var b by remember { mutableStateOf("") }
    var res by remember { mutableStateOf<JSONObject?>(null) }
    var msg by remember { mutableStateOf<String?>(null) }
    Column(Modifier.fillMaxSize().background(Ink)) {
        PageHeader("Head to Head", "Squad vs squad - real recorded matches", onBack)
        LazyColumn(contentPadding = PaddingValues(16.dp), verticalArrangement = Arrangement.spacedBy(10.dp)) {
            item { SmallCard {
                androidx.compose.material3.OutlinedTextField(value = a, onValueChange = { a = it }, label = { Text("Squad A name") }, modifier = Modifier.fillMaxWidth())
                Spacer(Modifier.height(8.dp))
                androidx.compose.material3.OutlinedTextField(value = b, onValueChange = { b = it }, label = { Text("Squad B name") }, modifier = Modifier.fillMaxWidth())
                Spacer(Modifier.height(10.dp))
                GoldButton("COMPARE") {
                    msg = null; res = null
                    scope.launch {
                        val r = runCatching { Api.authedRpc("head_to_head", JSONObject().put("p_a", a.trim()).put("p_b", b.trim())) }.getOrNull()
                        if (r != null && r.optBoolean("ok", true)) res = r else msg = r?.s("error")?.ifBlank { null } ?: "Could not compare - check the squad names."
                    }
                }
                msg?.let { Text(it, color = ErrRed, fontSize = 12.sp) }
            } }
            res?.let { r -> item { SmallCard {
                val played = r.optInt("played")
                if (played == 0) Text("No recorded matches between these squads yet.", color = Muted, fontSize = 13.sp)
                else {
                    Text("$played match" + (if (played == 1) "" else "es") + " played", color = Muted, fontSize = 12.sp)
                    Spacer(Modifier.height(8.dp))
                    Row(Modifier.fillMaxWidth()) {
                        Column(Modifier.weight(1f), horizontalAlignment = Alignment.CenterHorizontally) {
                            Text(a.trim().ifBlank { "Squad A" }, color = Color.White, fontSize = 14.sp, fontWeight = FontWeight.Bold)
                            Text(r.optInt("a_wins").toString() + " wins", color = Gold2, fontSize = 20.sp, fontWeight = FontWeight.Bold)
                            Text(r.optInt("a_kills").toString() + " kills", color = Muted, fontSize = 12.sp)
                        }
                        Text("VS", color = Muted, fontSize = 14.sp, modifier = Modifier.align(Alignment.CenterVertically))
                        Column(Modifier.weight(1f), horizontalAlignment = Alignment.CenterHorizontally) {
                            Text(b.trim().ifBlank { "Squad B" }, color = Color.White, fontSize = 14.sp, fontWeight = FontWeight.Bold)
                            Text(r.optInt("b_wins").toString() + " wins", color = Gold2, fontSize = 20.sp, fontWeight = FontWeight.Bold)
                            Text(r.optInt("b_kills").toString() + " kills", color = Muted, fontSize = 12.sp)
                        }
                    }
                }
            } } }
        }
    }
}

@Composable
fun SquadProfileScreen(squadId: String, onBack: () -> Unit) {
    var tick by remember { mutableIntStateOf(0) }
    Column(Modifier.fillMaxSize().background(Ink)) {
        PageHeader("Squad", "", onBack)
        val ctx = androidx.compose.ui.platform.LocalContext.current
        Row(Modifier.padding(horizontal = 16.dp)) {
            Text("SHARE SQUAD LINK", color = Gold, fontSize = 12.sp, fontWeight = FontWeight.Bold,
                modifier = Modifier.clip(RoundedCornerShape(10.dp)).border(1.dp, Gold, RoundedCornerShape(10.dp))
                    .clickable {
                        val i = android.content.Intent(android.content.Intent.ACTION_SEND).setType("text/plain")
                            .putExtra(android.content.Intent.EXTRA_TEXT, "Check out our squad on XARENA: https://xarenaprivate.github.io/xarena/more.html?squad=" + squadId + "#squad")
                        ctx.startActivity(android.content.Intent.createChooser(i, "Share squad link"))
                    }.padding(horizontal = 14.dp, vertical = 8.dp))
        }
        LoadState2(tick, { runCatching { Api.authedRpc("squad_profile", JSONObject().put("p_squad", squadId)) }.getOrNull() }) { r ->
            if (r == null || !r.optBoolean("ok")) { SmallCard { Text("Could not load this squad.", color = Muted, fontSize = 13.sp) }; return@LoadState2 }
            LazyColumn(contentPadding = PaddingValues(16.dp), verticalArrangement = Arrangement.spacedBy(10.dp)) {
                item { SmallCard {
                    Row(verticalAlignment = Alignment.CenterVertically) {
                        if (r.s("logo_url").isNotBlank()) coil.compose.AsyncImage(model = xrImageModel(Api.ctx, r.s("logo_url")), contentDescription = r.s("name"),
                            modifier = Modifier.width(64.dp).height(64.dp).clip(RoundedCornerShape(14.dp)), contentScale = androidx.compose.ui.layout.ContentScale.Crop)
                        Spacer(Modifier.width(12.dp))
                        Column {
                            Text(r.s("name") + (r.s("tag").takeIf { it.isNotBlank() }?.let { " [$it]" } ?: ""), color = Gold2, fontSize = 20.sp, fontWeight = FontWeight.Bold)
                            if (r.s("bio").isNotBlank()) Text(r.s("bio"), color = Muted, fontSize = 12.sp)
                            Text(r.s("record"), color = Color.White, fontSize = 12.sp)
                        }
                    }
                } }
                val members = r.optJSONArray("members") ?: JSONArray()
                item { Text("MEMBERS", color = Gold, fontSize = 13.sp, fontWeight = FontWeight.Bold, modifier = Modifier.padding(start = 4.dp)) }
                items((0 until members.length()).map { members.getJSONObject(it) }) { m ->
                    Row(Modifier.fillMaxWidth().clip(RoundedCornerShape(10.dp)).background(Look.cardBg).padding(10.dp), verticalAlignment = Alignment.CenterVertically) {
                        if (m.s("avatar_url").isNotBlank()) coil.compose.AsyncImage(model = xrImageModel(Api.ctx, m.s("avatar_url")), contentDescription = null,
                            modifier = Modifier.width(34.dp).height(34.dp).clip(RoundedCornerShape(17.dp)), contentScale = androidx.compose.ui.layout.ContentScale.Crop)
                        Spacer(Modifier.width(10.dp))
                        Text(m.s("ign"), color = Color.White, fontSize = 14.sp, modifier = Modifier.weight(1f))
                        Text(m.s("role"), color = Muted, fontSize = 11.sp)
                    }
                }
                val ts = r.optJSONArray("tournaments") ?: JSONArray()
                if (ts.length() > 0) {
                    item { Text("TOURNAMENTS", color = Gold, fontSize = 13.sp, fontWeight = FontWeight.Bold, modifier = Modifier.padding(start = 4.dp)) }
                    items((0 until ts.length()).map { ts.getJSONObject(it) }) { t ->
                        Row(Modifier.fillMaxWidth().clip(RoundedCornerShape(10.dp)).background(Look.cardBg).padding(10.dp)) {
                            Text(t.toString().take(60), color = Muted, fontSize = 12.sp)
                        }
                    }
                }
            }
        }
    }
}

@Composable
fun NoticeBoardScreen(onBack: () -> Unit) {
    var tick by remember { mutableIntStateOf(0) }
    Column(Modifier.fillMaxSize().background(Ink)) {
        PageHeader("Notice Board", "Official announcements", onBack)
        LoadState2(tick, { runCatching { Api.publicGet("/rest/v1/notices?select=title,body,link,pinned&order=pinned.desc,created_at.desc") }.getOrElse { JSONArray() } }) { rows ->
            if (rows.length() == 0) SmallCard { Text("No notices right now.", color = Muted, fontSize = 13.sp) }
            else LazyColumn(contentPadding = PaddingValues(16.dp), verticalArrangement = Arrangement.spacedBy(10.dp)) {
                items((0 until rows.length()).map { rows.getJSONObject(it) }) { n ->
                    val ctx = androidx.compose.ui.platform.LocalContext.current
                    Column(Modifier.fillMaxWidth().clip(RoundedCornerShape(Look.cardRadius)).background(Look.cardBg)
                        .border(1.dp, if (n.optBoolean("pinned")) Gold.copy(alpha = .6f) else Look.cardBorder, RoundedCornerShape(Look.cardRadius))
                        .clickable(enabled = n.s("link").isNotBlank()) {
                            runCatching { ctx.startActivity(android.content.Intent(android.content.Intent.ACTION_VIEW, android.net.Uri.parse(n.s("link")))) }
                        }.padding(14.dp)) {
                        if (n.optBoolean("pinned")) Text("PINNED", color = Gold, fontSize = 10.sp, fontWeight = FontWeight.Bold)
                        Text(n.s("title"), color = Color.White, fontSize = 15.sp, fontWeight = FontWeight.Bold)
                        if (n.s("body").isNotBlank()) Text(n.s("body"), color = Muted, fontSize = 12.sp)
                    }
                }
            }
        }
    }
}

@Composable
fun FeedbackScreen(onBack: () -> Unit) {
    val scope = rememberCoroutineScope()
    var kind by remember { mutableStateOf("feedback") }
    var body by remember { mutableStateOf("") }
    var done by remember { mutableStateOf(false) }
    var msg by remember { mutableStateOf<String?>(null) }
    Column(Modifier.fillMaxSize().background(Ink)) {
        PageHeader("Feedback & Reports", "Ideas, bugs, cheater reports - straight to the team", onBack)
        LazyColumn(contentPadding = PaddingValues(16.dp), verticalArrangement = Arrangement.spacedBy(10.dp)) {
            item { SmallCard {
                if (done) {
                    Text("Sent - thank you.", color = Color(0xFF22C55E), fontSize = 15.sp, fontWeight = FontWeight.Bold)
                    Text("The team reads every report. No phone number or private detail is ever shared.", color = Muted, fontSize = 12.sp)
                } else {
                    Row {
                        listOf("feedback", "bug", "idea").forEach { k ->
                            Text(k.replaceFirstChar { it.uppercase() }, color = if (kind == k) Ink else Muted, fontSize = 12.sp, fontWeight = FontWeight.Bold,
                                modifier = Modifier.padding(end = 8.dp).clip(RoundedCornerShape(8.dp))
                                    .background(if (kind == k) Gold else Color(0x14FFFFFF))
                                    .clickable { kind = k }.padding(horizontal = 12.dp, vertical = 7.dp))
                        }
                    }
                    Spacer(Modifier.height(10.dp))
                    androidx.compose.material3.OutlinedTextField(value = body, onValueChange = { body = it },
                        label = { Text(if (kind == "bug") "What broke? Which screen?" else if (kind == "idea") "Your idea" else "Report or feedback") },
                        modifier = Modifier.fillMaxWidth().height(130.dp))
                    Spacer(Modifier.height(10.dp))
                    GoldButton("SEND") {
                        if (body.isBlank()) return@GoldButton
                        msg = null
                        scope.launch {
                            val r = runCatching { Api.authedRpc("feedback_submit", JSONObject().put("p_kind", kind).put("p_body", body.trim())) }.getOrNull()
                            if (r != null && r.optBoolean("ok")) { done = true; Fx.blip() } else msg = r?.s("error")?.ifBlank { null } ?: "Could not send - log in first."
                        }
                    }
                    msg?.let { Text(it, color = ErrRed, fontSize = 12.sp) }
                    Text("Never share your phone number or password here.", color = Muted, fontSize = 11.sp)
                }
            } }
        }
    }
}
