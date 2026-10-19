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
