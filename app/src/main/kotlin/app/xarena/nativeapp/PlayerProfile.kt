package app.xarena.nativeapp

import androidx.compose.foundation.background
import androidx.compose.foundation.horizontalScroll
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import coil.compose.AsyncImage
import kotlinx.coroutines.launch
import org.json.JSONObject

/* ============ Player profile - full-screen card per the owner's mockup.
   Real data only: player_profiles_public view (safe columns), badges table, approved feed videos.
   Level curve + badge names mirror the site's assets/js/game.js exactly. ============ */

fun levelFromXp(xpIn: Int): Triple<Int, Int, Int> { // level, into, need - mirrors web levelFromXp
    var x = maxOf(0, xpIn); var lvl = 1
    while (true) { val need = 50 + 25 * (lvl - 1); if (x < need) return Triple(lvl, x, need); x -= need; lvl++ }
}
fun levelTitle(lvl: Int): String = when {
    lvl >= 30 -> "LEGEND"; lvl >= 20 -> "ELITE"; lvl >= 10 -> "PRO"; lvl >= 5 -> "CHALLENGER"; else -> "ROOKIE"
}
fun badgeMeta(key: String): Triple<String, String, String> = when (key) { // name, icon, desc - mirrors web badgeMeta
    "streak_3" -> Triple("Warming Up", "🔥", "3-day login streak")
    "streak_7" -> Triple("On Fire", "🔥", "7-day login streak")
    "streak_30" -> Triple("Unstoppable", "💎", "30-day login streak")
    "lucky_spinner" -> Triple("Lucky Spinner", "🍀", "Hit the rare spin segment")
    "quiz_first_win" -> Triple("Quiz Debut", "🧠", "First correct quiz answer")
    "quiz_sharp_10" -> Triple("Sharp Mind", "🏆", "10 correct quiz answers")
    "clean_cup" -> Triple("Clean Cup", "🧼", "Kept World Chat clean all week")
    "top_fan" -> Triple("Top Fan", "💜", "Most active supporter of the week")
    else -> Triple(key.replace('_', ' '), "🎖️", "XARENA badge")
}
private fun lastSeenText(iso: String): String? {
    if (iso.isBlank()) return null
    val t = runCatching { java.time.Instant.parse(iso.replace(" ", "T")).toEpochMilli() }.getOrNull() ?: return null
    val mins = (System.currentTimeMillis() - t) / 60000
    if (mins < 2) return "Online now"
    if (mins < 60) return "Last seen " + mins + "m ago"
    val h = mins / 60
    if (h < 24) return "Last seen " + h + "h ago"
    return "Last seen " + (h / 24) + "d ago"
}

data class PlayerProfileData(
    val profile: JSONObject, val badges: List<JSONObject>, val highlights: List<JSONObject>,
    val friendState: String, val friendRequestId: String?
)

@Composable
fun PlayerProfileScreen(playerId: String, onBack: () -> Unit, onOpenConv: (String, String, String, String, Boolean) -> Unit) {
    val scope = rememberCoroutineScope()
    var reloadTick by remember { mutableIntStateOf(0) }
    var actionErr by remember { mutableStateOf<String?>(null) }
    var actionBusy by remember { mutableStateOf(false) }
    var playUrl by remember { mutableStateOf<String?>(null) }
    Column(Modifier.fillMaxSize().background(Ink)) {
        LoadState2(reloadTick, {
            val rows = Api.playerProfiles()
            var prof: JSONObject? = null
            for (i in 0 until rows.length()) { val r = rows.getJSONObject(i); if (r.s("id") == playerId) { prof = r; break } }
            val p = prof ?: throw Exception("Player not found.")
            val br = Api.playerBadges(playerId); val badges = (0 until br.length()).map { br.getJSONObject(it) }
            val hr = Api.playerHighlights(playerId); val hl = (0 until hr.length()).map { hr.getJSONObject(it) }
            val fs = Api.friendStateWith(playerId)
            PlayerProfileData(p, badges, hl, fs.s("state"), if (fs.has("request_id")) fs.s("request_id") else null)
        }) { d ->
            val p = d.profile
            val name = p.s("ign").ifBlank { p.s("username") }.ifBlank { "Player" }
            val xp = p.optInt("xp")
            val (lvl, into, need) = levelFromXp(xp)
            val seen = lastSeenText(p.s("last_seen_at"))
            LazyColumn(Modifier.fillMaxSize()) {
                item {
                    Row(Modifier.fillMaxWidth().padding(horizontal = 8.dp, vertical = 8.dp), verticalAlignment = Alignment.CenterVertically) {
                        Text("< Back", color = Gold, fontSize = 14.sp, modifier = Modifier.clip(RoundedCornerShape(6.dp)).clickable { onBack() }.padding(8.dp))
                        Spacer(Modifier.weight(1f))
                        Text("XARENA", color = Gold, fontSize = 18.sp, fontWeight = FontWeight.Black, letterSpacing = 2.sp)
                        Spacer(Modifier.weight(1f)); Spacer(Modifier.width(56.dp))
                    }
                    Row(Modifier.fillMaxWidth().padding(horizontal = 20.dp, vertical = 8.dp), verticalAlignment = Alignment.CenterVertically) {
                        Box(Modifier.size(92.dp).clip(CircleShape).border(2.dp, Gold, CircleShape).background(Card), contentAlignment = Alignment.Center) {
                            val av = p.s("avatar_url")
                            if (av.isNotBlank()) AsyncImage(model = xrImageModel(androidx.compose.ui.platform.LocalContext.current, av), contentDescription = name, modifier = Modifier.fillMaxSize(), contentScale = ContentScale.Crop)
                            else Text(name.first().uppercase(), color = Gold, fontSize = 34.sp, fontWeight = FontWeight.Black)
                        }
                        Spacer(Modifier.width(16.dp))
                        Column {
                            Row(verticalAlignment = Alignment.CenterVertically) {
                                Text(name, color = Color.White, fontSize = 24.sp, fontWeight = FontWeight.Bold)
                                if (p.optBoolean("verified")) {
                                    Spacer(Modifier.width(8.dp))
                                    Text("VERIFIED", color = Gold2, fontSize = 9.sp, fontWeight = FontWeight.Bold,
                                        modifier = Modifier.clip(RoundedCornerShape(4.dp)).border(1.dp, Gold2, RoundedCornerShape(4.dp)).padding(horizontal = 5.dp, vertical = 1.dp))
                                }
                            }
                            if (p.s("username").isNotBlank()) Text("@" + p.s("username"), color = Muted, fontSize = 14.sp)
                            if (seen != null) Row(verticalAlignment = Alignment.CenterVertically, modifier = Modifier.padding(top = 4.dp)) {
                                Box(Modifier.size(8.dp).clip(CircleShape).background(if (seen == "Online now") Color(0xFF4ADE80) else Muted))
                                Spacer(Modifier.width(6.dp))
                                Text(seen, color = if (seen == "Online now") Color(0xFF4ADE80) else Muted, fontSize = 12.sp)
                            }
                        }
                    }
                    if (d.friendState != "self") {
                        Row(Modifier.fillMaxWidth().padding(horizontal = 20.dp, vertical = 10.dp)) {
                            val frLabel = when (d.friendState) { "friends" -> "Friends ✓"; "requested" -> "Requested"; "incoming" -> "Accept Request"; else -> "+ Add Friend" }
                            val frEnabled = d.friendState == "none" || d.friendState == "incoming"
                            Text(frLabel, color = if (frEnabled) Ink else Muted, fontSize = 14.sp, fontWeight = FontWeight.Bold,
                                modifier = Modifier.weight(1f).clip(RoundedCornerShape(10.dp))
                                    .background(if (frEnabled) Gold else Card)
                                    .clickable(enabled = frEnabled && !actionBusy) {
                                        scope.launch {
                                            actionBusy = true; actionErr = null
                                            try {
                                                val r = if (d.friendState == "incoming") Api.dmFriendRespond(d.friendRequestId!!, true) else Api.dmFriendRequest(playerId)
                                                if (r.optBoolean("ok")) reloadTick++ else actionErr = r.s("error").ifBlank { "Could not send request." }
                                            } catch (e: Exception) { actionErr = e.message }
                                            actionBusy = false
                                        }
                                    }.padding(vertical = 13.dp).wrapContentWidth(Alignment.CenterHorizontally))
                            Spacer(Modifier.width(10.dp))
                            Text("💬 Message", color = Gold, fontSize = 14.sp, fontWeight = FontWeight.Bold,
                                modifier = Modifier.weight(1f).clip(RoundedCornerShape(10.dp)).border(1.dp, Gold, RoundedCornerShape(10.dp))
                                    .clickable(enabled = !actionBusy) {
                                        scope.launch {
                                            actionBusy = true; actionErr = null
                                            try {
                                                val r = Api.dmOpen(playerId)
                                                if (r.optBoolean("ok")) onOpenConv(r.getString("thread"), playerId, name, if (r.optBoolean("requested")) "requested" else "open", r.optBoolean("requester"))
                                                else actionErr = r.s("error").ifBlank { "Could not open chat." }
                                            } catch (e: Exception) { actionErr = e.message }
                                            actionBusy = false
                                        }
                                    }.padding(vertical = 13.dp).wrapContentWidth(Alignment.CenterHorizontally))
                        }
                    }
                    actionErr?.let { Text(it, color = ErrRed, fontSize = 13.sp, modifier = Modifier.padding(horizontal = 20.dp, vertical = 2.dp)) }
                    var reportMsg by remember { mutableStateOf<String?>(null) }
                    var reportOpen by remember { mutableStateOf(false) }
                    Text("Report player", color = Muted, fontSize = 12.sp,
                        modifier = Modifier.padding(horizontal = 20.dp, vertical = 4.dp).clickable { reportOpen = !reportOpen })
                    if (reportOpen) Row(Modifier.fillMaxWidth().padding(horizontal = 16.dp).horizontalScroll(androidx.compose.foundation.rememberScrollState()), horizontalArrangement = Arrangement.spacedBy(6.dp)) {
                        listOf("cheating", "abuse", "spam", "fake_account", "other").forEach { reason ->
                            Text(reason.replace('_', ' '), color = Color(0xFFFF5252), fontSize = 11.sp, fontWeight = FontWeight.Bold,
                                modifier = Modifier.clip(RoundedCornerShape(8.dp)).border(1.dp, Color(0xFFFF5252), RoundedCornerShape(8.dp))
                                    .clickable(enabled = !actionBusy) {
                                        scope.launch {
                                            actionBusy = true; reportMsg = null; reportOpen = false
                                            try {
                                                val r = Api.authedRpc("report_player", JSONObject().put("p_username", p.s("username").ifBlank { name }).put("p_reason", reason).put("p_details", ""))
                                                reportMsg = if (r.optBoolean("ok")) "Report sent - the team reviews every report." else r.s("error").ifBlank { "Could not send report." }
                                            } catch (e: Exception) { reportMsg = e.message }
                                            actionBusy = false
                                        }
                                    }.padding(horizontal = 10.dp, vertical = 6.dp))
                        }
                    }
                    reportMsg?.let { Text(it, color = if (it.startsWith("Report sent")) Color(0xFF4ADE80) else ErrRed, fontSize = 12.sp, modifier = Modifier.padding(horizontal = 20.dp)) }
                }
                item {
                    Column(Modifier.fillMaxWidth().padding(horizontal = 16.dp, vertical = 6.dp).clip(RoundedCornerShape(16.dp)).background(Card).border(1.dp, Gold.copy(alpha = .25f), RoundedCornerShape(16.dp)).padding(16.dp)) {
                        Row(verticalAlignment = Alignment.CenterVertically) {
                            Text("🏆", fontSize = 20.sp)
                            Spacer(Modifier.width(10.dp))
                            Text("LEVEL " + lvl + " - " + levelTitle(lvl), color = Gold2, fontSize = 14.sp, fontWeight = FontWeight.Bold)
                            Spacer(Modifier.weight(1f))
                            Text(into.toString() + " / " + need + " XP", color = Muted, fontSize = 12.sp)
                        }
                        Spacer(Modifier.height(10.dp))
                        Box(Modifier.fillMaxWidth().height(8.dp).clip(RoundedCornerShape(4.dp)).background(Color(0x33FFFFFF))) {
                            Box(Modifier.fillMaxWidth((into.toFloat() / need).coerceIn(0f, 1f)).fillMaxHeight().clip(RoundedCornerShape(4.dp))
                                .background(Brush.horizontalGradient(listOf(Gold, Gold2))))
                        }
                        Spacer(Modifier.height(6.dp))
                        Text((need - into).toString() + " XP until LEVEL " + (lvl + 1), color = Muted, fontSize = 11.sp)
                        Spacer(Modifier.height(14.dp))
                        Row(Modifier.fillMaxWidth()) {
                            listOf(
                                "WINS" to p.optInt("wins").toString(),
                                "K/D" to (if (p.isNull("kd")) "-" else p.optDouble("kd").let { if (it == it.toLong().toDouble()) it.toLong().toString() else String.format(java.util.Locale.US, "%.2f", it) }),
                                "MATCHES" to p.optInt("matches").toString()
                            ).forEach { (label, value) ->
                                Column(Modifier.weight(1f), horizontalAlignment = Alignment.CenterHorizontally) {
                                    Text(label, color = Muted, fontSize = 10.sp, fontWeight = FontWeight.Bold, letterSpacing = 1.sp)
                                    Text(value, color = Gold, fontSize = 22.sp, fontWeight = FontWeight.Black)
                                }
                            }
                        }
                    }
                }
                item {
                    Row(Modifier.fillMaxWidth().padding(horizontal = 20.dp).padding(top = 14.dp, bottom = 6.dp), verticalAlignment = Alignment.CenterVertically) {
                        Text("🛡️", fontSize = 14.sp); Spacer(Modifier.width(8.dp))
                        Text("BADGES", color = Color.White, fontSize = 14.sp, fontWeight = FontWeight.Bold, letterSpacing = 1.sp)
                    }
                    if (d.badges.isEmpty()) {
                        Text("No badges yet - they show up here as they're earned.", color = Muted, fontSize = 13.sp, modifier = Modifier.padding(horizontal = 20.dp))
                    }
                }
                items(d.badges.chunked(4)) { row ->
                    Row(Modifier.fillMaxWidth().padding(horizontal = 16.dp, vertical = 3.dp)) {
                        row.forEach { b ->
                            val (bn, icon, desc) = badgeMeta(b.s("badge"))
                            Column(Modifier.weight(1f).padding(horizontal = 3.dp).clip(RoundedCornerShape(12.dp)).background(Card).border(1.dp, Gold.copy(alpha = .2f), RoundedCornerShape(12.dp)).padding(vertical = 12.dp, horizontal = 4.dp), horizontalAlignment = Alignment.CenterHorizontally) {
                                Text(icon, fontSize = 22.sp)
                                Text(bn.uppercase(), color = Gold2, fontSize = 9.sp, fontWeight = FontWeight.Bold, modifier = Modifier.padding(top = 4.dp), maxLines = 1)
                                Text(desc, color = Muted, fontSize = 8.sp, maxLines = 2, textAlign = androidx.compose.ui.text.style.TextAlign.Center)
                            }
                        }
                        repeat(4 - row.size) { Spacer(Modifier.weight(1f)) }
                    }
                }
                item {
                    Row(Modifier.fillMaxWidth().padding(horizontal = 20.dp).padding(top = 14.dp, bottom = 6.dp), verticalAlignment = Alignment.CenterVertically) {
                        Text("▶", color = Gold, fontSize = 12.sp); Spacer(Modifier.width(8.dp))
                        Text("HIGHLIGHTS", color = Color.White, fontSize = 14.sp, fontWeight = FontWeight.Bold, letterSpacing = 1.sp)
                    }
                    if (d.highlights.isEmpty()) {
                        Text("No highlights yet - videos they post to the Feed will show up here.", color = Muted, fontSize = 13.sp, modifier = Modifier.padding(horizontal = 20.dp))
                    }
                }
                items(d.highlights) { h ->
                    Row(Modifier.fillMaxWidth().padding(horizontal = 16.dp, vertical = 3.dp).clip(RoundedCornerShape(12.dp)).background(Card).border(1.dp, Gold.copy(alpha = .2f), RoundedCornerShape(12.dp))
                        .clickable { feedMediaUrl(h)?.let { playUrl = it } }.padding(12.dp), verticalAlignment = Alignment.CenterVertically) {
                        Box(Modifier.size(40.dp).clip(RoundedCornerShape(8.dp)).background(Gold.copy(alpha = .15f)), contentAlignment = Alignment.Center) {
                            Text("▶", color = Gold, fontSize = 16.sp)
                        }
                        Spacer(Modifier.width(12.dp))
                        Column {
                            Text(h.s("caption").ifBlank { "Highlight clip" }, color = Color.White, fontSize = 14.sp, fontWeight = FontWeight.Bold, maxLines = 1)
                            Text("Tap to play", color = Muted, fontSize = 11.sp)
                        }
                    }
                }
                item { Spacer(Modifier.height(28.dp)) }
            }
        }
    }
    playUrl?.let { url ->
        androidx.compose.ui.window.Dialog(onDismissRequest = { playUrl = null }) {
            Column(Modifier.fillMaxWidth().clip(RoundedCornerShape(16.dp)).background(Card).padding(12.dp)) {
                androidx.compose.ui.viewinterop.AndroidView(factory = { ctx ->
                    xrVideoView(ctx, url).apply { setOnPreparedListener { it.start() } }
                }, modifier = Modifier.fillMaxWidth().height(420.dp))
                Text("Close", color = Gold, fontWeight = FontWeight.Bold, modifier = Modifier.align(Alignment.CenterHorizontally).clickable { playUrl = null }.padding(12.dp))
            }
        }
    }
}
