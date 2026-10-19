package app.xarena.nativeapp

import android.app.ActivityManager
import android.content.Intent
import android.os.StatFs
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.Text
import androidx.compose.material3.TextField
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.delay
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import org.json.JSONObject
import java.net.HttpURLConnection
import java.net.URL

/* Ping Check + Device Check - mirrors the site's ping.js: real timed requests to the
   XARENA API, never simulated. Ratings identical to the site. */
private fun pingRating(ms: Double): Pair<String, Color> = when {
    ms < 60 -> "Excellent" to Color(0xFF7EE787)
    ms < 110 -> "Good" to Color(0xFF7EE787)
    ms < 180 -> "Fair" to Gold
    else -> "Slow" to Color(0xFFFF8A8A)
}

private fun pingOnce(): Double {
    val t0 = System.nanoTime()
    val c = URL(Api.BASE + "/rest/v1/site_settings?select=key&limit=1&_=" + t0).openConnection() as HttpURLConnection
    c.requestMethod = "GET"; c.connectTimeout = 8000; c.readTimeout = 8000
    c.setRequestProperty("apikey", Api.KEY); c.setRequestProperty("Cache-Control", "no-store")
    val code = c.responseCode
    c.inputStream.use { it.readBytes() }
    if (code !in 200..299) throw Exception("bad")
    return (System.nanoTime() - t0) / 1_000_000.0
}

@Composable
fun PingScreen(onBack: () -> Unit) {
    var running by remember { mutableStateOf(false) }
    var avg by remember { mutableStateOf<Double?>(null) }
    var live by remember { mutableStateOf<Double?>(null) }
    var best by remember { mutableStateOf<Double?>(null) }
    var worst by remember { mutableStateOf<Double?>(null) }
    var jitter by remember { mutableStateOf<Double?>(null) }
    var fails by remember { mutableIntStateOf(0) }
    var nDone by remember { mutableIntStateOf(0) }
    var noConn by remember { mutableStateOf(false) }
    val scope = rememberCoroutineScope()

    val ramGb = remember {
        val am = Api.ctx.getSystemService(android.content.Context.ACTIVITY_SERVICE) as ActivityManager
        val mi = ActivityManager.MemoryInfo(); am.getMemoryInfo(mi)
        mi.totalMem / 1073741824.0
    }
    val cores = remember { Runtime.getRuntime().availableProcessors() }
    val storageFreeGb = remember {
        try { val sf = StatFs(android.os.Environment.getDataDirectory().path); sf.availableBytes / 1073741824.0 } catch (e: Exception) { -1.0 }
    }
    val netLabel = remember {
        try {
            val cm = Api.ctx.getSystemService(android.content.Context.CONNECTIVITY_SERVICE) as android.net.ConnectivityManager
            val caps = cm.getNetworkCapabilities(cm.activeNetwork)
            when {
                caps == null -> "offline"
                caps.hasTransport(android.net.NetworkCapabilities.TRANSPORT_WIFI) -> "Wi-Fi"
                caps.hasTransport(android.net.NetworkCapabilities.TRANSPORT_CELLULAR) -> "Mobile data"
                else -> "Other"
            }
        } catch (e: Exception) { "unknown" }
    }
    val dm = Api.ctx.resources.displayMetrics

    fun runCheck() {
        if (running) return
        running = true; noConn = false; fails = 0; nDone = 0; live = null
        scope.launch {
            val samples = mutableListOf<Double>()
            repeat(8) {
                try { val d = withContext(Dispatchers.IO) { pingOnce() }; samples.add(d); live = d } catch (e: Exception) { fails++ }
                nDone++
                if (it < 7) delay(180)
            }
            if (samples.isEmpty()) { noConn = true; avg = null }
            else {
                avg = samples.average(); best = samples.min(); worst = samples.max()
                jitter = if (samples.size > 1) samples.drop(1).mapIndexed { i, v -> kotlin.math.abs(v - samples[i]) }.average() else 0.0
            }
            running = false
        }
    }

    LazyColumn(Modifier.fillMaxSize()) {
        item { PageHeader("Ping & Device Check", "Real measurements - nothing simulated", onBack) }
        item {
            Column(Modifier.fillMaxWidth().padding(16.dp), horizontalAlignment = Alignment.CenterHorizontally) {
                val shown = live ?: avg
                val rating = avg?.let { pingRating(it) }
                Box(Modifier.size(170.dp).clip(CircleShape).border(3.dp, rating?.second ?: Gold, CircleShape), contentAlignment = Alignment.Center) {
                    Column(horizontalAlignment = Alignment.CenterHorizontally) {
                        Text(if (noConn) "--" else (shown?.let { Math.round(it).toString() } ?: "--"), color = Color.White, fontSize = 40.sp, fontWeight = FontWeight.Bold)
                        Text("ms", color = Muted, fontSize = 12.sp)
                        Text(when { noConn -> "No connection"; running -> "Measuring... ($nDone/8)"; avg != null -> rating!!.first + " ping"; else -> "Tap Run Full Check" },
                            color = if (noConn) ErrRed else (rating?.second ?: Muted), fontSize = 13.sp, fontWeight = FontWeight.Bold)
                    }
                }
                Spacer(Modifier.height(16.dp))
                SmallCard {
                    PingRow("Server", if (noConn) "XARENA API - unreachable" else "XARENA API")
                    PingRow("Latency" + (if (nDone > 0) " (avg of $nDone)" else ""),
                        if (avg == null) "not measured yet" else "${Math.round(avg!!)} ms · best ${Math.round(best!!)} · worst ${Math.round(worst!!)}")
                    PingRow("Jitter", if (jitter == null) "not measured yet" else "${Math.round(jitter!!)} ms" + when {
                        jitter!! < 15 -> " · steady"; jitter!! < 40 -> " · a little uneven"; else -> " · uneven" })
                    if (fails > 0) PingRow("Failed requests", "$fails of 8 - your connection dropped some")
                }
                Spacer(Modifier.height(10.dp))
                SmallCard {
                    PingRow("CPU cores", "$cores cores")
                    PingRow("Memory (RAM)", String.format("%.1f GB", ramGb))
                    PingRow("Screen", "${dm.widthPixels} x ${dm.heightPixels} px")
                    PingRow("Network", netLabel)
                    PingRow("Storage free", if (storageFreeGb < 0) "not available" else String.format("%.1f GB", storageFreeGb))
                }
                Spacer(Modifier.height(10.dp))
                val low = ramGb <= 3.2 || cores <= 4
                SmallCard {
                    Text(if (low) "Performance: low-end device detected - the app keeps visuals light and videos tap-to-play for smooth use."
                        else "Performance: device is comfortable for all app features.",
                        color = Color(0xFF7EE787), fontSize = 13.sp)
                }
                Spacer(Modifier.height(14.dp))
                GoldButton(if (running) "MEASURING..." else "RUN FULL CHECK") { runCheck() }
                Spacer(Modifier.height(6.dp))
                Text("Real measurements on your device and connection - nothing simulated.", color = Muted, fontSize = 11.sp, textAlign = TextAlign.Center)
                Spacer(Modifier.height(30.dp))
            }
        }
    }
}

@Composable
private fun PingRow(label: String, value: String) {
    Row(Modifier.fillMaxWidth().padding(vertical = 7.dp), verticalAlignment = Alignment.CenterVertically) {
        Text(label, color = Muted, fontSize = 13.sp, modifier = Modifier.weight(1f))
        Text(value, color = Color.White, fontSize = 13.sp, fontWeight = FontWeight.Medium)
    }
}

/* Refer & Earn - same RPCs as the site's refer-nova.js. */
@Composable
fun ReferScreen(onBack: () -> Unit) {
    var tick by remember { mutableIntStateOf(0) }
    LoadState2(tick, { Api.authedRpc("my_referral", JSONObject()) }) { info ->
        val code = info.optString("code")
        var friend by remember { mutableStateOf("") }
        var msg by remember { mutableStateOf<String?>(null) }
        var busy by remember { mutableStateOf(false) }
        val scope = rememberCoroutineScope()
        LazyColumn(Modifier.fillMaxSize()) {
            item { PageHeader("Refer & Earn", "Invite friends, earn Store Points", onBack) }
            item {
                Column(Modifier.padding(16.dp)) {
                    SmallCard {
                        Text("YOUR INVITE CODE", color = Muted, fontSize = 11.sp, fontWeight = FontWeight.Bold)
                        Spacer(Modifier.height(6.dp))
                        Row(verticalAlignment = Alignment.CenterVertically) {
                            Text(code, color = Gold2, fontSize = 26.sp, fontWeight = FontWeight.Bold, modifier = Modifier.weight(1f))
                            Text("Copy", color = Gold, fontSize = 13.sp, fontWeight = FontWeight.Bold,
                                modifier = Modifier.clip(RoundedCornerShape(8.dp)).background(Gold.copy(alpha = .18f)).clickable {
                                    val cm = Api.ctx.getSystemService(android.content.Context.CLIPBOARD_SERVICE) as android.content.ClipboardManager
                                    cm.setPrimaryClip(android.content.ClipData.newPlainText("invite code", code))
                                    msg = "Code copied."
                                }.padding(horizontal = 14.dp, vertical = 8.dp))
                        }
                        val invited = info.optInt("invited", info.optInt("count", -1))
                        if (invited >= 0) { Spacer(Modifier.height(6.dp)); Text("Friends joined with your code: $invited", color = Color.White, fontSize = 13.sp) }
                    }
                    Spacer(Modifier.height(10.dp))
                    GoldButton("Share Code with Friends") {
                        val i = Intent(Intent.ACTION_SEND); i.type = "text/plain"
                        i.putExtra(Intent.EXTRA_TEXT, "Join me on XARENA ESPORTS - use my invite code $code when you sign up: https://xarena.gg")
                        Api.ctx.startActivity(Intent.createChooser(i, "Share invite code").addFlags(Intent.FLAG_ACTIVITY_NEW_TASK))
                    }
                    Spacer(Modifier.height(10.dp))
                    SmallCard {
                        ReferStep("1", "Share your code", "Send it to a friend who plays Free Fire.")
                        ReferStep("2", "Friend signs up", "They enter your code on their new account.")
                        ReferStep("3", "You both earn 50 Store Points", "Points unlock cosmetics - never cash.")
                    }
                    Spacer(Modifier.height(10.dp))
                    SmallCard {
                        Text("HAVE A FRIEND'S CODE?", color = Muted, fontSize = 11.sp, fontWeight = FontWeight.Bold)
                        Spacer(Modifier.height(6.dp))
                        TextField(value = friend, onValueChange = { friend = it }, singleLine = true,
                            placeholder = { Text("Enter invite code", color = Muted, fontSize = 13.sp) }, modifier = Modifier.fillMaxWidth())
                        Spacer(Modifier.height(8.dp))
                        GoldButton(if (busy) "Applying..." else "Apply Code") {
                            if (busy || friend.isBlank()) return@GoldButton
                            busy = true; msg = null
                            scope.launch {
                                try {
                                    val r = Api.referralApply(friend.trim())
                                    msg = if (r.optBoolean("ok")) r.optString("message").ifBlank { "Code applied." }
                                          else r.optString("error").ifBlank { "That code did not work." }
                                } catch (e: Exception) { msg = e.message }
                                busy = false
                            }
                        }
                    }
                    msg?.let { Spacer(Modifier.height(8.dp)); Text(it, color = Gold2, fontSize = 13.sp) }
                    Spacer(Modifier.height(10.dp))
                    Text("Store Points can be used to unlock exclusive in-game cosmetics, badges, and leaderboard boosts. No monetary value. XP only.",
                        color = Muted, fontSize = 11.sp)
                    Spacer(Modifier.height(30.dp))
                }
            }
        }
    }
}

@Composable
private fun ReferStep(n: String, title: String, sub: String) {
    Row(Modifier.fillMaxWidth().padding(vertical = 6.dp), verticalAlignment = Alignment.CenterVertically) {
        Box(Modifier.size(26.dp).clip(CircleShape).background(Gold.copy(alpha = .2f)), contentAlignment = Alignment.Center) {
            Text(n, color = Gold, fontSize = 13.sp, fontWeight = FontWeight.Bold)
        }
        Spacer(Modifier.width(10.dp))
        Column { Text(title, color = Color.White, fontSize = 14.sp, fontWeight = FontWeight.Bold); Text(sub, color = Muted, fontSize = 12.sp) }
    }
}

/* Anti-cheat photo verification - mirrors verify.html: upload FF profile screenshot,
   owner reviews personally. No fake "AI check". */
@Composable
fun VerifyScreen(onBack: () -> Unit) {
    var tick by remember { mutableIntStateOf(0) }
    LoadState2(tick, { Api.authedRpc("my_id_verification", JSONObject()) }) { r ->
        val sub = r.optJSONObject("submission")
        val status = sub?.optString("status") ?: ""
        val verified = r.optBoolean("verified")
        var busy by remember { mutableStateOf(false) }
        var msg by remember { mutableStateOf<String?>(null) }
        val scope = rememberCoroutineScope()
        val picker = androidx.activity.compose.rememberLauncherForActivityResult(androidx.activity.result.contract.ActivityResultContracts.OpenDocument()) { uri ->
            if (uri == null) return@rememberLauncherForActivityResult
            busy = true; msg = null
            scope.launch {
                try {
                    val cr = Api.ctx.contentResolver
                    var bytes = withContext(Dispatchers.IO) { cr.openInputStream(uri)!!.use { it.readBytes() } }
                    if (bytes.size > 8 * 1024 * 1024) throw Exception("Image too large (max 8 MB).")
                    if (bytes.size > 2 * 1024 * 1024) {
                        val bmp = android.graphics.BitmapFactory.decodeByteArray(bytes, 0, bytes.size)
                        if (bmp != null) {
                            val f = 1280f / maxOf(bmp.width, bmp.height)
                            val scaled = if (f < 1f) android.graphics.Bitmap.createScaledBitmap(bmp, Math.round(bmp.width * f), Math.round(bmp.height * f), true) else bmp
                            val baos = java.io.ByteArrayOutputStream()
                            scaled.compress(android.graphics.Bitmap.CompressFormat.JPEG, 82, baos)
                            bytes = baos.toByteArray()
                        }
                    }
                    val url = Api.cloudinaryUpload(bytes)
                    val res = Api.authedRpc("submit_id_verification", JSONObject().put("p_image", url))
                    msg = if (res.optBoolean("ok")) "Submitted - pending admin review." else res.optString("error").ifBlank { "Could not submit." }
                    if (res.optBoolean("ok")) tick++
                } catch (e: Exception) { msg = e.message ?: "Upload failed - try again." }
                busy = false
            }
        }
        LazyColumn(Modifier.fillMaxSize()) {
            item { PageHeader("Photo Verification", "Anti-cheat - verified badge", onBack) }
            item {
                Column(Modifier.padding(16.dp)) {
                    SmallCard {
                        GapRow("Player", r.optString("ign").ifBlank { r.optString("username") }.ifBlank { "-" })
                        GapRow("Free Fire ID", r.optString("ff_uid").ifBlank { "-" })
                        GapRow("Status", when {
                            verified -> "Verified ✓"
                            status == "pending" -> "Pending review"
                            status == "rejected" -> "Rejected - upload a new screenshot"
                            else -> "Not submitted"
                        }, when {
                            verified -> Color(0xFF7EE787); status == "pending" -> Gold
                            status == "rejected" -> ErrRed; else -> Muted
                        })
                        if (verified && sub?.optString("reviewed_at")?.isNotBlank() == true)
                            GapRow("Verified at", sub.optString("reviewed_at").take(16).replace('T', ' '))
                        Spacer(Modifier.height(8.dp))
                        val frac = when { verified -> 1f; status == "pending" -> .55f; status == "rejected" -> .15f; else -> .08f }
                        Box(Modifier.fillMaxWidth().height(6.dp).clip(RoundedCornerShape(3.dp)).background(Color.White.copy(alpha = .12f))) {
                            Box(Modifier.fillMaxWidth(frac).fillMaxHeight().clip(RoundedCornerShape(3.dp)).background(if (verified) Color(0xFF7EE787) else Gold))
                        }
                    }
                    sub?.optString("image_path")?.takeIf { it.isNotBlank() }?.let { img ->
                        Spacer(Modifier.height(10.dp))
                        SmallCard {
                            Text("UPLOADED SCREENSHOT", color = Muted, fontSize = 11.sp, fontWeight = FontWeight.Bold)
                            Spacer(Modifier.height(6.dp))
                            coil.compose.AsyncImage(model = img, contentDescription = "uploaded screenshot",
                                modifier = Modifier.fillMaxWidth().clip(RoundedCornerShape(10.dp)))
                        }
                    }
                    Spacer(Modifier.height(10.dp))
                    Text(when {
                        verified -> "Verification complete - badge granted by admin review."
                        status == "pending" -> "Screenshot received - the admin reviews every submission personally."
                        status == "rejected" -> "Not approved" + (sub?.optString("admin_note")?.takeIf { it.isNotBlank() }?.let { ": $it" } ?: " - please upload a clearer Free Fire profile screenshot.")
                        else -> "Upload your Free Fire profile screenshot to start verification."
                    }, color = Muted, fontSize = 13.sp)
                    Spacer(Modifier.height(12.dp))
                    GoldButton(if (busy) "Uploading..." else if (verified) "Upload New Screenshot" else "Upload Screenshot") {
                        if (!busy) picker.launch(arrayOf("image/*"))
                    }
                    msg?.let { Spacer(Modifier.height(8.dp)); Text(it, color = Gold2, fontSize = 13.sp) }
                    Spacer(Modifier.height(8.dp))
                    Text("Anti-cheat active - secure and encrypted - compliant with XARENA ESPORTS policy.", color = Muted, fontSize = 11.sp)
                    Spacer(Modifier.height(30.dp))
                }
            }
        }
    }
}

@Composable
private fun GapRow(label: String, value: String, vc: Color = Color.White) {
    Row(Modifier.fillMaxWidth().padding(vertical = 6.dp), verticalAlignment = Alignment.CenterVertically) {
        Text(label, color = Muted, fontSize = 13.sp, modifier = Modifier.weight(1f))
        Text(value, color = vc, fontSize = 13.sp, fontWeight = FontWeight.Bold)
    }
}

/* Team Roles - mirrors roles.js: roles saved via update_player_card, formation from squad_members. */
private val XR_ROLES = listOf("IGL", "Rusher", "Support", "Sniper", "Grenadier", "Flex")
private val XR_ROLE_DESC = mapOf(
    "IGL" to "Shotcaller - team strategist", "Rusher" to "Entry - frontline aggressor",
    "Support" to "Defend - heals and cover", "Sniper" to "Long range - precision",
    "Grenadier" to "Utility - nades and zone control", "Flex" to "Fills whatever the squad needs")

@Composable
fun RolesScreen(onBack: () -> Unit) {
    var tick by remember { mutableIntStateOf(0) }
    LoadState2(tick, {
        val uid = Api.uid ?: throw Exception("Log in first.")
        val prof = Api.authedGetArray("/rest/v1/profiles?id=eq.$uid&select=role,ign,username&limit=1")
        val myRole = if (prof.length() > 0) prof.getJSONObject(0).optString("role") else ""
        val sq = Api.authedGetArray("/rest/v1/squad_members?user_id=eq.$uid&select=squad_id,role&limit=1")
        var members = org.json.JSONArray()
        var names = mapOf<String, String>()
        if (sq.length() > 0) {
            val sid = sq.getJSONObject(0).getString("squad_id")
            members = Api.authedGetArray("/rest/v1/squad_members?squad_id=eq.$sid&select=user_id,role")
            val ids = (0 until members.length()).map { members.getJSONObject(it).getString("user_id") }
            if (ids.isNotEmpty()) names = Api.dmNames(ids)
        }
        Triple(myRole, sq.length() > 0, members to names)
    }) { (myRole0, hasSquad, mn) ->
        var current by remember { mutableStateOf(myRole0) }
        var saveMsg by remember { mutableStateOf<String?>(null) }
        val scope = rememberCoroutineScope()
        val (members, names) = mn
        LazyColumn(Modifier.fillMaxSize()) {
            item { PageHeader("Team Roles", "Real roles from real players", onBack) }
            item {
                Column(Modifier.padding(16.dp)) {
                    Text("YOUR ROLE", color = Muted, fontSize = 11.sp, fontWeight = FontWeight.Bold)
                    Spacer(Modifier.height(8.dp))
                    Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                        XR_ROLES.take(3).forEach { r -> RoleChip(r, current == r, Modifier.weight(1f)) {
                            scope.launch {
                                saveMsg = "Saving..."
                                try {
                                    val res = Api.authedRpc("update_player_card", JSONObject().put("p_role", r))
                                    if (res.optBoolean("ok")) { current = r; saveMsg = null } else saveMsg = res.optString("error").ifBlank { "Could not save - try again." }
                                } catch (e: Exception) { saveMsg = "Could not save - try again." }
                            }
                        } }
                    }
                    Spacer(Modifier.height(8.dp))
                    Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                        XR_ROLES.drop(3).forEach { r -> RoleChip(r, current == r, Modifier.weight(1f)) {
                            scope.launch {
                                saveMsg = "Saving..."
                                try {
                                    val res = Api.authedRpc("update_player_card", JSONObject().put("p_role", r))
                                    if (res.optBoolean("ok")) { current = r; saveMsg = null } else saveMsg = res.optString("error").ifBlank { "Could not save - try again." }
                                } catch (e: Exception) { saveMsg = "Could not save - try again." }
                            }
                        } }
                    }
                    Spacer(Modifier.height(8.dp))
                    Text(saveMsg ?: (current.takeIf { it.isNotBlank() }?.let { XR_ROLE_DESC[it] ?: "" } ?: "Pick the role you actually play - it shows on your player card."),
                        color = Muted, fontSize = 12.sp)
                    Spacer(Modifier.height(16.dp))
                    Text("SQUAD FORMATION", color = Muted, fontSize = 11.sp, fontWeight = FontWeight.Bold)
                    Spacer(Modifier.height(8.dp))
                    SmallCard {
                        if (!hasSquad) Text("No squad formation yet. When you join or create a squad on the Squads page, its role layout shows here - captain on top, every member in their real role.", color = Muted, fontSize = 13.sp)
                        else if (members.length() == 0) Text("Your squad has no members listed yet.", color = Muted, fontSize = 13.sp)
                        else {
                            val ordered = (0 until members.length()).map { members.getJSONObject(it) }
                                .sortedByDescending { it.optString("role") == "IGL" }
                            ordered.forEach { m ->
                                val nm = names[m.optString("user_id")] ?: "Player"
                                val role = m.optString("role")
                                Row(Modifier.fillMaxWidth().padding(vertical = 6.dp), verticalAlignment = Alignment.CenterVertically) {
                                    Text(nm, color = Color.White, fontSize = 14.sp, fontWeight = FontWeight.Bold, modifier = Modifier.weight(1f))
                                    if (role.isNotBlank()) Text(role, color = if (role == "IGL") Gold else Muted, fontSize = 12.sp, fontWeight = FontWeight.Bold)
                                }
                            }
                            Spacer(Modifier.height(4.dp))
                            Text("Real members and their saved roles - nothing assigned automatically.", color = Muted, fontSize = 11.sp)
                        }
                    }
                    Spacer(Modifier.height(30.dp))
                }
            }
        }
    }
}

@Composable
private fun RoleChip(label: String, on: Boolean, mod: Modifier, onClick: () -> Unit) {
    Box(mod.clip(RoundedCornerShape(10.dp)).background(if (on) Gold else Look.cardBg)
        .border(1.dp, if (on) Gold else Look.cardBorder, RoundedCornerShape(10.dp)).clickable { onClick() }.padding(vertical = 10.dp),
        contentAlignment = Alignment.Center) {
        Text(label.uppercase(), color = if (on) Ink else Color.White, fontSize = 12.sp, fontWeight = FontWeight.Bold)
    }
}

/* Winner Celebration - mirrors winner-nova.js: real final from bracket_matches only. */
@Composable
fun WinnerScreen(onBack: () -> Unit) {
    var tick by remember { mutableIntStateOf(0) }
    LoadState2(tick, {
        val rows = Api.publicGet("/rest/v1/bracket_matches?winner=not.is.null&order=updated_at.desc&limit=20&select=tournament_id,winner,round,updated_at")
        if (rows.length() == 0) return@LoadState2 null
        val byT = (0 until rows.length()).map { rows.getJSONObject(it) }.groupBy { it.getString("tournament_id") }
        var best: JSONObject? = null
        byT.forEach { (_, ms) ->
            val maxR = ms.maxOf { it.optInt("round") }
            val fin = ms.filter { it.optInt("round") == maxR }.maxByOrNull { it.optString("updated_at") }
            if (fin != null && (best == null || fin.optString("updated_at") > best!!.optString("updated_at"))) best = fin
        }
        if (best == null) return@LoadState2 null
        val tname = try {
            val t = Api.publicGet("/rest/v1/tournaments?id=eq." + best!!.getString("tournament_id") + "&select=name&limit=1")
            if (t.length() > 0) t.getJSONObject(0).optString("name") else "Tournament"
        } catch (e: Exception) { "Tournament" }
        var mine = false
        try {
            val uid = Api.uid
            if (uid != null) {
                val sq = Api.authedGetArray("/rest/v1/squad_members?user_id=eq.$uid&select=squad_id")
                val squadIds = (0 until sq.length()).map { sq.getJSONObject(it).getString("squad_id") }
                val sqs = Api.publicGet("/rest/v1/squads_public?select=id,name")
                val myNames = (0 until sqs.length()).map { sqs.getJSONObject(it) }
                    .filter { squadIds.contains(it.optString("id")) }.map { it.optString("name") }
                mine = myNames.any { it.equals(best!!.optString("winner"), ignoreCase = true) }
            }
        } catch (e: Exception) { }
        Triple(best!!.getString("tournament_id"), best!!.optString("winner"), tname to (best!!.optString("updated_at").take(10)) to mine)
    }) { champ ->
        var msg by remember { mutableStateOf<String?>(null) }
        var busy by remember { mutableStateOf(false) }
        val scope = rememberCoroutineScope()
        LazyColumn(Modifier.fillMaxSize()) {
            item { PageHeader("Winner Celebration", "Real finals only", onBack) }
            item {
                Column(Modifier.padding(16.dp), horizontalAlignment = Alignment.CenterHorizontally) {
                    if (champ == null) {
                        SmallCard {
                            Text("No champion crowned yet.", color = Color.White, fontSize = 16.sp, fontWeight = FontWeight.Bold)
                            Spacer(Modifier.height(6.dp))
                            Text("When a tournament final finishes on the bracket, the winner appears here - never before.", color = Muted, fontSize = 13.sp)
                        }
                    } else {
                        val (tid, winner, meta) = champ
                        val tname = meta.first.first; val date = meta.first.second; val mine = meta.second
                        Text("WINNER CELEBRATION", color = Gold, fontSize = 13.sp, fontWeight = FontWeight.Bold)
                        Spacer(Modifier.height(10.dp))
                        Text("🏆", fontSize = 64.sp)
                        Spacer(Modifier.height(10.dp))
                        Text(winner, color = Gold2, fontSize = 28.sp, fontWeight = FontWeight.Bold, textAlign = TextAlign.Center)
                        Text("$tname · Finals" + (if (date.isNotBlank()) " - $date" else ""), color = Muted, fontSize = 13.sp)
                        Spacer(Modifier.height(12.dp))
                        SmallCard {
                            Text("CONGRATULATIONS!", color = Color.White, fontSize = 15.sp, fontWeight = FontWeight.Bold)
                            Spacer(Modifier.height(4.dp))
                            Text(if (mine) "Your team has claimed the championship title!" else "$winner has claimed the championship title!", color = Muted, fontSize = 13.sp)
                        }
                        Spacer(Modifier.height(12.dp))
                        GoldButton(if (busy) "Claiming..." else "Claim Title") {
                            if (busy) return@GoldButton
                            busy = true; msg = null
                            scope.launch {
                                try {
                                    val r = Api.authedRpc("claim_champion_title", JSONObject().put("p_tournament", tid))
                                    msg = if (r.optBoolean("ok")) "Title claimed - recorded on squad profiles." else r.optString("error").ifBlank { "Only squad members of the champion can claim." }
                                } catch (e: Exception) { msg = e.message }
                                busy = false
                            }
                        }
                        Spacer(Modifier.height(8.dp))
                        GoldButton("Share Victory") {
                            val i = Intent(Intent.ACTION_SEND); i.type = "text/plain"
                            i.putExtra(Intent.EXTRA_TEXT, "$winner are the $tname champions! Booyah! - XARENA ESPORTS")
                            Api.ctx.startActivity(Intent.createChooser(i, "Share victory").addFlags(Intent.FLAG_ACTIVITY_NEW_TASK))
                        }
                        msg?.let { Spacer(Modifier.height(8.dp)); Text(it, color = Gold2, fontSize = 13.sp, textAlign = TextAlign.Center) }
                        Spacer(Modifier.height(8.dp))
                        Text("Champion title is recorded on the squad members' profiles. XP only - never cash.", color = Muted, fontSize = 11.sp, textAlign = TextAlign.Center)
                    }
                    Spacer(Modifier.height(30.dp))
                }
            }
        }
    }
}

/* Sponsor Wall - real signed partners from the sponsors table; honest empty state. */
@Composable
fun SponsorsScreen(onBack: () -> Unit) {
    var tick by remember { mutableIntStateOf(0) }
    LoadState2(tick, { Api.publicGet("/rest/v1/sponsors_wall?select=name,tier,url,image_url&order=id.asc") }) { rows ->
        LazyColumn(Modifier.fillMaxSize()) {
            item { PageHeader("Sponsor Wall", "Proudly supporting the community", onBack) }
            item {
                Column(Modifier.padding(16.dp)) {
                    Text("THANK YOU SPONSORS", color = Gold, fontSize = 18.sp, fontWeight = FontWeight.Bold)
                    Spacer(Modifier.height(6.dp))
                    Text("We're grateful to the local partners who power XARENA ESPORTS tournaments in Munger, Bihar. Every name on this wall is a real signed partner - it appears only after the deal is done.",
                        color = Muted, fontSize = 13.sp)
                    Spacer(Modifier.height(14.dp))
                    if (rows.length() == 0) {
                        SmallCard {
                            Text("No sponsors announced yet.", color = Color.White, fontSize = 15.sp, fontWeight = FontWeight.Bold)
                            Spacer(Modifier.height(4.dp))
                            Text("Partner names appear here once a deal is signed - never before.", color = Muted, fontSize = 13.sp)
                        }
                    } else (0 until rows.length()).forEach {
                        val sp = rows.getJSONObject(it)
                        Spacer(Modifier.height(8.dp))
                        SmallCard {
                            sp.optString("image_url").takeIf { u -> u.isNotBlank() && u.startsWith("https://") }?.let { img ->
                                coil.compose.AsyncImage(model = img, contentDescription = sp.optString("name"),
                                    modifier = Modifier.fillMaxWidth().height(200.dp).clip(RoundedCornerShape(10.dp)).background(Look.cardBorder),
                                    contentScale = androidx.compose.ui.layout.ContentScale.Crop)
                                Spacer(Modifier.height(8.dp))
                            }
                            Text(sp.optString("name"), color = Gold2, fontSize = 16.sp, fontWeight = FontWeight.Bold)
                            Text((sp.optString("tier").ifBlank { "partner" }) + " Partner", color = Muted, fontSize = 12.sp)
                            sp.optString("url").takeIf { u -> u.isNotBlank() }?.let { u ->
                                Spacer(Modifier.height(4.dp))
                                Text("Visit", color = Gold, fontSize = 13.sp, fontWeight = FontWeight.Bold,
                                    modifier = Modifier.clickable {
                                        Api.ctx.startActivity(Intent(Intent.ACTION_VIEW, android.net.Uri.parse(u)).addFlags(Intent.FLAG_ACTIVITY_NEW_TASK))
                                    }.padding(vertical = 4.dp))
                            }
                        }
                    }
                    Spacer(Modifier.height(30.dp))
                }
            }
        }
    }
}
