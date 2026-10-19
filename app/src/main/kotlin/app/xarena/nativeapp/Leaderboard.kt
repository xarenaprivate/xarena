package app.xarena.nativeapp

import android.content.Context
import android.location.Geocoder
import android.location.LocationManager
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.itemsIndexed
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Place
import androidx.compose.material3.Icon
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Text
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import org.json.JSONArray
import org.json.JSONObject
import java.util.Locale

/** Auto-detected coarse area (state + district). Exact coordinates are never stored. */
object LocStore {
    private val p get() = Api.ctx.getSharedPreferences("xr_loc", 0)
    var state: String get() = p.getString("state", "") ?: ""; set(v) = p.edit().putString("state", v).apply()
    var district: String get() = p.getString("district", "") ?: ""; set(v) = p.edit().putString("district", v).apply()
    var consent: Boolean get() = p.getBoolean("consent", false); set(v) = p.edit().putBoolean("consent", v).apply()
    var updatedAt: Long get() = p.getLong("updated_at", 0L); set(v) = p.edit().putLong("updated_at", v).apply()
    fun known() = state.isNotBlank()
    fun stale() = System.currentTimeMillis() - updatedAt > 7L * 24 * 3600 * 1000
    fun pill() = if (state.isNotBlank() && district.isNotBlank()) "STATE: $state | DISTRICT: $district"
        else if (state.isNotBlank()) "STATE: $state" else null
}

/** On-device coarse detection: last-known location + Android Geocoder, then set_my_area. No paid APIs. */
suspend fun detectLocationNow(ctx: Context): Boolean = withContext(Dispatchers.IO) {
    runCatching {
        val lm = ctx.getSystemService(Context.LOCATION_SERVICE) as LocationManager
        val loc = lm.getProviders(true).mapNotNull { runCatching { lm.getLastKnownLocation(it) }.getOrNull() }
            .maxByOrNull { it.time } ?: return@withContext false
        if (!Geocoder.isPresent()) return@withContext false
        val a = runCatching { Geocoder(ctx, Locale.ENGLISH).getFromLocation(loc.latitude, loc.longitude, 1)?.firstOrNull() }.getOrNull() ?: return@withContext false
        val st = a.adminArea?.trim()?.takeIf { it.isNotBlank() } ?: return@withContext false
        val di = (a.subAdminArea ?: a.locality)?.trim() ?: ""
        LocStore.state = st.uppercase(); LocStore.district = di.uppercase()
        LocStore.updatedAt = System.currentTimeMillis()
        // Server rounds coordinates to a ~5 km grid; only sent because the user opted in
        Api.authedRpc("set_my_area", JSONObject().put("p_state", st).put("p_district", di)
            .put("p_lat", loc.latitude).put("p_lon", loc.longitude))
        true
    }.getOrDefault(false)
}

private data class LbRow(val rank: Int, val id: String, val name: String, val xp: Int, val kd: Double?, val wins: Int,
    val avatar: String?, val state: String?, val district: String?, val isMe: Boolean, val rival: Boolean)

private fun lbRowFrom(j: JSONObject) = LbRow(
    rank = j.optInt("rank"), id = j.s("id"),
    name = j.s("name").ifBlank { j.s("ign").ifBlank { j.s("username") } },
    xp = j.optInt("xp"), kd = if (j.isNull("kd")) null else j.optDouble("kd"), wins = j.optInt("wins"),
    avatar = j.s("avatar").ifBlank { j.s("avatar_url") }.takeIf { it.isNotBlank() && it != "null" },
    state = j.s("state").takeIf { it.isNotBlank() && it != "null" },
    district = j.s("district").takeIf { it.isNotBlank() && it != "null" },
    isMe = j.optBoolean("is_me"), rival = j.optBoolean("rival"))

private sealed class LbResult {
    data class Rows(val rows: List<LbRow>) : LbResult()
    object LocationRequired : LbResult()
    data class Unavailable(val msg: String) : LbResult()
}

private suspend fun lbFetch(scopeIdx: Int): LbResult = withContext(Dispatchers.IO) {
    val scope = listOf("global", "state", "district", "near")[scopeIdx]
    runCatching {
        val r = if (scope == "global") Api.publicRpc("leaderboard_scope", JSONObject().put("p_scope", scope).put("p_limit", 100))
            else Api.authedRpc("leaderboard_scope", JSONObject().put("p_scope", scope).put("p_limit", 100))
        if (!r.optBoolean("ok")) {
            if (r.s("error") == "location_required") LbResult.LocationRequired
            else LbResult.Unavailable(r.s("error").ifBlank { "unavailable" })
        } else {
            val a = r.optJSONArray("rows") ?: JSONArray()
            LbResult.Rows((0 until a.length()).map { lbRowFrom(a.getJSONObject(it)) })
        }
    }.getOrElse { LbResult.Unavailable(it.message ?: "unavailable") }
}

private val LbGold = Color(0xFFFFD700)
private val LbSilver = Color(0xFFC0C0C0)
private val LbBronze = Color(0xFFCD7F32)

@Composable
private fun LbAvatar(r: LbRow, size: Int, ring: Color) {
    Box(Modifier.size(size.dp).clip(CircleShape).background(Look.cardBorder).border(2.dp, ring, CircleShape), contentAlignment = Alignment.Center) {
        if (r.avatar != null) coil.compose.AsyncImage(model = xrImageModel(Api.ctx, r.avatar), contentDescription = r.name,
            modifier = Modifier.fillMaxSize().clip(CircleShape), contentScale = ContentScale.Crop)
        else Text(r.name.firstOrNull()?.uppercase() ?: "P", color = Gold2, fontWeight = FontWeight.Bold, fontSize = (size / 2.4).sp)
    }
}

@Composable
private fun StatChip(label: String, value: String) {
    Column(Modifier.border(1.dp, Gold.copy(alpha = .55f), RoundedCornerShape(8.dp)).padding(horizontal = 10.dp, vertical = 4.dp), horizontalAlignment = Alignment.CenterHorizontally) {
        Text(label, color = Gold2, fontSize = 9.sp, fontWeight = FontWeight.Bold)
        Text(value, color = Color.White, fontSize = 13.sp, fontWeight = FontWeight.Bold)
    }
}

@Composable
private fun ChallengeButton(target: LbRow, onDone: (String) -> Unit) {
    var busy by remember { mutableStateOf(false) }
    var sent by remember { mutableStateOf(false) }
    Box(Modifier.clip(RoundedCornerShape(8.dp)).background(if (sent) Look.cardBorder else Color(0xFFE10600))
        .clickable(enabled = !busy && !sent) {
            busy = true
            kotlinx.coroutines.GlobalScope.launch(Dispatchers.IO) {
                val msg = runCatching {
                    val r = Api.authedRpc("challenge_create", JSONObject().put("p_target", target.id).put("p_stake", 100))
                    if (r.optBoolean("ok")) "sent" else r.s("error").ifBlank { "failed" }
                }.getOrElse { "failed" }
                kotlinx.coroutines.withContext(Dispatchers.Main) { busy = false; if (msg == "sent") sent = true; onDone(msg) }
            }
        }.padding(horizontal = 12.dp, vertical = 8.dp)) {
        Text(if (sent) "Challenged" else if (busy) "..." else "Challenge · 100 AP stake", color = Color.White, fontSize = 11.sp, fontWeight = FontWeight.Bold)
    }
}

@Composable
private fun TopCard(rank: Int, r: LbRow, onChallenge: (String) -> Unit) {
    val accent = when (rank) { 1 -> LbGold; 2 -> LbSilver; else -> LbBronze }
    Column(Modifier.fillMaxWidth().padding(horizontal = 16.dp, vertical = 6.dp)
        .clip(RoundedCornerShape(Look.cardRadius)).background(Look.cardBg)
        .border(if (rank == 1) 2.dp else 1.dp, accent.copy(alpha = if (rank == 1) .9f else .6f), RoundedCornerShape(Look.cardRadius))
        .padding(14.dp)) {
        Row(verticalAlignment = Alignment.CenterVertically) {
            Column(horizontalAlignment = Alignment.CenterHorizontally) {
                Text("#" + rank + if (rank == 1) " 👑" else "", color = accent, fontSize = 18.sp, fontWeight = FontWeight.Black)
                Spacer(Modifier.height(4.dp))
                LbAvatar(r, if (rank == 1) 76 else 62, accent)
            }
            Spacer(Modifier.width(14.dp))
            Column(Modifier.weight(1f)) {
                Text(r.name, color = Color.White, fontSize = if (rank == 1) 19.sp else 16.sp, fontWeight = FontWeight.Bold, maxLines = 1)
                Spacer(Modifier.height(6.dp))
                Row(horizontalArrangement = Arrangement.spacedBy(6.dp)) {
                    StatChip("EXP", "%,d".format(r.xp))
                    StatChip("K/D", r.kd?.let { "%.1f".format(it) } ?: "-")
                    StatChip("WINS", r.wins.toString())
                }
                val tag = listOfNotNull(r.state, r.district).filter { it.isNotBlank() }.joinToString(" · ")
                if (tag.isNotBlank()) { Spacer(Modifier.height(6.dp)); Text(tag, color = Gold2, fontSize = 11.sp, fontWeight = FontWeight.Bold) }
                if (r.rival) { Spacer(Modifier.height(8.dp)); ChallengeButton(r, onChallenge) }
            }
            if (rank == 1) Column(horizontalAlignment = Alignment.CenterHorizontally) {
                Text("🏅", fontSize = 26.sp)
                Text("MVP", color = LbGold, fontSize = 12.sp, fontWeight = FontWeight.Black)
            }
        }
    }
}

@Composable
private fun CompactRow(rank: Int, r: LbRow, onChallenge: (String) -> Unit) {
    PanelCard {
        Row(verticalAlignment = Alignment.CenterVertically) {
            Text("#" + rank, color = Gold, fontSize = 16.sp, fontWeight = FontWeight.Black, modifier = Modifier.width(44.dp))
            LbAvatar(r, 34, if (r.rival) Color(0xFFE10600) else Look.cardBorder)
            Spacer(Modifier.width(10.dp))
            Column(Modifier.weight(1f)) {
                Text(r.name, color = Color.White, fontSize = 15.sp, fontWeight = FontWeight.Bold, maxLines = 1)
                val tag = listOfNotNull(r.state, r.district).filter { it.isNotBlank() }.joinToString(" · ")
                Text(if (r.rival) "Nearby Rival" + (if (tag.isNotBlank()) " · $tag" else "") else tag, color = if (r.rival) Color(0xFFFF5252) else Muted, fontSize = 11.sp)
            }
            Column(horizontalAlignment = Alignment.End) {
                Text("%,d EXP".format(r.xp), color = Gold2, fontWeight = FontWeight.Bold, fontSize = 13.sp)
                Text("K/D " + (r.kd?.let { "%.1f".format(it) } ?: "-") + " · " + r.wins + " wins", color = Muted, fontSize = 10.sp)
                if (r.rival) { Spacer(Modifier.height(4.dp)); ChallengeButton(r, onChallenge) }
            }
        }
    }
}

@Composable
private fun ChallengesStrip(onChanged: () -> Unit) {
    var rows by remember { mutableStateOf<JSONArray?>(null) }
    LaunchedEffect(Unit) {
        rows = runCatching {
            withContext(Dispatchers.IO) { Api.authedRpc("my_challenges", JSONObject()).optJSONArray("rows") }
        }.getOrNull()
    }
    val a = rows ?: return
    if (a.length() == 0) return
    Column(Modifier.fillMaxWidth().padding(horizontal = 16.dp, vertical = 4.dp)) {
        Text("CHALLENGES", color = Gold2, fontSize = 11.sp, fontWeight = FontWeight.Bold)
        (0 until a.length()).forEach { i ->
            val c = a.getJSONObject(i)
            val incoming = c.optBoolean("incoming")
            val status = c.s("status")
            Row(Modifier.fillMaxWidth().padding(vertical = 3.dp).clip(RoundedCornerShape(10.dp)).background(Look.cardBg)
                .border(1.dp, Look.cardBorder, RoundedCornerShape(10.dp)).padding(10.dp), verticalAlignment = Alignment.CenterVertically) {
                Column(Modifier.weight(1f)) {
                    Text((if (incoming) c.s("other_name") + " challenged you" else "You challenged " + c.s("other_name")) +
                        (if (c.s("district").isNotBlank()) " for " + c.s("district") + " King" else ""),
                        color = Color.White, fontSize = 13.sp, fontWeight = FontWeight.Bold)
                    Text("Stake " + c.optInt("stake") + " AP · " + status, color = Muted, fontSize = 11.sp)
                }
                if (status == "open" || status == "pending") {
                    if (incoming) {
                        Text("Accept", color = Color(0xFF4ADE80), fontSize = 12.sp, fontWeight = FontWeight.Bold,
                            modifier = Modifier.clickable {
                                kotlinx.coroutines.GlobalScope.launch(Dispatchers.IO) {
                                    runCatching { Api.authedRpc("challenge_respond", JSONObject().put("p_id", c.s("id")).put("p_accept", true)) }
                                    withContext(Dispatchers.Main) { onChanged() }
                                }
                            }.padding(6.dp))
                        Text("Decline", color = Color(0xFFFF5252), fontSize = 12.sp, fontWeight = FontWeight.Bold,
                            modifier = Modifier.clickable {
                                kotlinx.coroutines.GlobalScope.launch(Dispatchers.IO) {
                                    runCatching { Api.authedRpc("challenge_respond", JSONObject().put("p_id", c.s("id")).put("p_accept", false)) }
                                    withContext(Dispatchers.Main) { onChanged() }
                                }
                            }.padding(6.dp))
                    } else Text("Cancel", color = Muted, fontSize = 12.sp, fontWeight = FontWeight.Bold,
                        modifier = Modifier.clickable {
                            kotlinx.coroutines.GlobalScope.launch(Dispatchers.IO) {
                                runCatching { Api.authedRpc("challenge_cancel", JSONObject().put("p_id", c.s("id"))) }
                                withContext(Dispatchers.Main) { onChanged() }
                            }
                        }.padding(6.dp))
                }
            }
        }
    }
}

@Composable
private fun ConsentCard(onEnable: () -> Unit) {
    PanelCard {
        Row(verticalAlignment = Alignment.CenterVertically) {
            Icon(Icons.Default.Place, contentDescription = null, tint = Gold, modifier = Modifier.size(28.dp))
            Spacer(Modifier.width(10.dp))
            Column(Modifier.weight(1f)) {
                Text("Turn on leaderboard location", color = Color.White, fontSize = 15.sp, fontWeight = FontWeight.Bold)
                Text("Used only to place you on the State and District boards. Detection is automatic and we store a coarse area only - never your exact location. Skip it and you still appear on Global.",
                    color = Muted, fontSize = 12.sp)
            }
        }
        Spacer(Modifier.height(10.dp))
        Box(Modifier.clip(RoundedCornerShape(10.dp)).background(Gold).clickable { onEnable() }.padding(horizontal = 16.dp, vertical = 10.dp)) {
            Text("Enable location", color = Color(0xFF1A0E33), fontWeight = FontWeight.Bold, fontSize = 13.sp)
        }
    }
}

@Composable
fun LeaderboardScreen() {
    var tick by remember { mutableIntStateOf(0) }
    var scope by remember { mutableIntStateOf(if (LocStore.known() && LocStore.district.isNotBlank()) 2 else 0) }
    var query by remember { mutableStateOf("") }
    var toast by remember { mutableStateOf<String?>(null) }
    val ctx = Api.ctx
    val permLauncher = androidx.activity.compose.rememberLauncherForActivityResult(
        androidx.activity.result.contract.ActivityResultContracts.RequestPermission()) { granted ->
        if (granted) { LocStore.consent = true; tick++ }
    }
    LaunchedEffect(Unit) {
        if (LocStore.consent && LocStore.stale() &&
            androidx.core.content.ContextCompat.checkSelfPermission(ctx, android.Manifest.permission.ACCESS_COARSE_LOCATION) == android.content.pm.PackageManager.PERMISSION_GRANTED) {
            detectLocationNow(ctx); tick++
        }
    }
    LoadState2(tick, {
        if (scope > 0 && LocStore.consent && !LocStore.known()) detectLocationNow(ctx)
        val res = lbFetch(scope)
        val ranks = runCatching { Api.authedRpc("my_ranks", JSONObject()) }.getOrNull()
        res to ranks
    }) { (res, ranks) ->
        Column(Modifier.fillMaxSize()) {
            Brand()
            (ranks?.takeIf { it.optBoolean("ok") }?.let { "STATE: " + it.s("state_name").uppercase() + " | DISTRICT: " + it.s("district_name").uppercase() }
                ?: LocStore.pill())?.let { pill ->
                Row(Modifier.fillMaxWidth().padding(horizontal = 16.dp, vertical = 2.dp), verticalAlignment = Alignment.CenterVertically) {
                    Icon(Icons.Default.Place, contentDescription = null, tint = Gold2, modifier = Modifier.size(14.dp))
                    Spacer(Modifier.width(4.dp))
                    Text(pill, color = Gold2, fontSize = 11.sp, fontWeight = FontWeight.Bold)
                }
            }
            Row(Modifier.fillMaxWidth().padding(horizontal = 12.dp, vertical = 6.dp), horizontalArrangement = Arrangement.spacedBy(6.dp)) {
                val labels = listOf("Global", "State", LocStore.district.takeIf { it.isNotBlank() } ?: "District", "Location")
                labels.forEachIndexed { i, l ->
                    val sel = scope == i
                    Box(Modifier.weight(1f).clip(RoundedCornerShape(10.dp))
                        .background(if (sel) Gold else Look.cardBg)
                        .border(1.dp, if (sel) Gold else Look.cardBorder, RoundedCornerShape(10.dp))
                        .clickable { scope = i; tick++ }.padding(vertical = 9.dp), contentAlignment = Alignment.Center) {
                        Text(l, color = if (sel) Color(0xFF1A0E33) else Color.White, fontSize = 12.sp, fontWeight = FontWeight.Bold, maxLines = 1)
                    }
                }
            }
            Text("LEADERBOARD", color = Gold2, fontSize = 24.sp, fontWeight = FontWeight.Black, modifier = Modifier.padding(horizontal = 16.dp))
            Text(when (scope) { 0 -> "Top players - All India"; 1 -> "Top players - " + LocStore.state.ifBlank { "your state" };
                    2 -> "Top players - " + (LocStore.district.ifBlank { "your district" }) + " district"; else -> "Players within about 10 km of you" } +
                " · updated just now", color = Muted, fontSize = 11.sp, modifier = Modifier.padding(horizontal = 16.dp, vertical = 2.dp))
            ChallengesStrip { tick++ }
            OutlinedTextField(query, { query = it }, placeholder = { Text("Search a player by name") }, singleLine = true,
                modifier = Modifier.fillMaxWidth().padding(horizontal = 16.dp, vertical = 4.dp))
            LazyColumn(Modifier.weight(1f)) {
                when (res) {
                    is LbResult.LocationRequired -> item { ConsentCard { permLauncher.launch(android.Manifest.permission.ACCESS_COARSE_LOCATION) } }
                    is LbResult.Unavailable -> item { EmptyNote("This board is not available right now", "Global rankings are live on the first tab.") }
                    is LbResult.Rows -> {
                        val rows = res.rows
                        val shown = rows.filter { query.isBlank() || it.name.contains(query.trim(), true) }
                        if (shown.isEmpty()) item {
                            EmptyNote(if (rows.isEmpty()) "No players here yet" else "No player matches " + query.trim(),
                                if (rows.isEmpty()) "Be the first to earn EXP here." else "Try a different name.")
                        }
                        itemsIndexed(shown.take(3)) { i, r -> TopCard(i + 1, r) { toast = it; tick++ } }
                        itemsIndexed(shown.drop(3)) { i, r -> CompactRow(i + 4, r) { toast = it; tick++ } }
                    }
                }
            }
            toast?.let { Text(it, color = Gold2, fontSize = 11.sp, modifier = Modifier.padding(horizontal = 16.dp)) }
            ranks?.takeIf { it.optBoolean("ok") }?.let { m ->
                Row(Modifier.fillMaxWidth().padding(horizontal = 16.dp, vertical = 8.dp).clip(RoundedCornerShape(Look.cardRadius))
                    .background(Look.cardBg).border(1.dp, Gold, RoundedCornerShape(Look.cardRadius)).padding(14.dp),
                    verticalAlignment = Alignment.CenterVertically) {
                    Column(Modifier.weight(1f)) {
                        Text("MY RANKS", color = Muted, fontSize = 10.sp, fontWeight = FontWeight.Bold)
                        Text("Global #" + m.optInt("global") +
                            (if (m.optInt("state") > 0) " | State #" + m.optInt("state") else "") +
                            (if (m.optInt("district") > 0) " | District #" + m.optInt("district") else ""),
                            color = Color.White, fontSize = 14.sp, fontWeight = FontWeight.Bold)
                    }
                    Text("%,d EXP".format(m.optInt("xp")), color = Gold2, fontSize = 16.sp, fontWeight = FontWeight.Bold)
                }
            }
        }
    }
}
