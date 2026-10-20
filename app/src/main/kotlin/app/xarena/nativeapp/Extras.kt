package app.xarena.nativeapp

import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.text.BasicTextField
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.TextStyle
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import kotlinx.coroutines.launch
import org.json.JSONArray
import org.json.JSONObject

@Composable
private fun ExtrasHeader(title: String, sub: String, onBack: () -> Unit) {
    Row(Modifier.fillMaxWidth().padding(horizontal = 8.dp, vertical = 8.dp), verticalAlignment = Alignment.CenterVertically) {
        Text("< Back", color = Gold, fontSize = 14.sp, modifier = Modifier.clip(RoundedCornerShape(6.dp)).clickable { onBack() }.padding(8.dp))
        Spacer(Modifier.width(8.dp))
        Column {
            Text(title, color = Gold2, fontSize = 18.sp, fontWeight = FontWeight.Bold)
            Text(sub, color = Muted, fontSize = 11.sp)
        }
    }
}

/* ============ Settings ============ */
@Composable
fun SettingsScreen(onBack: () -> Unit, onLogout: () -> Unit) {
    val scope = rememberCoroutineScope()
    var activityOn by remember { mutableStateOf<Boolean?>(null) }
    var msg by remember { mutableStateOf<String?>(null) }
    var updateInfo by remember { mutableStateOf<String?>(null) }
    LaunchedEffect(Unit) {
        activityOn = runCatching {
            val r = Api.authedRpc("presence_of", JSONObject().put("p_ids", org.json.JSONArray().put(Api.uid)))
            val arr = r.optJSONArray("presence")
            if (arr != null && arr.length() > 0) arr.getJSONObject(0).optBoolean("show", true) else true
        }.getOrDefault(true)
    }
    Column(Modifier.fillMaxSize().background(Ink)) {
        ExtrasHeader("Settings", "App and account options", onBack)
        Column(Modifier.padding(16.dp).clip(RoundedCornerShape(Look.cardRadius)).background(Look.cardBg).border(1.dp, Look.cardBorder, RoundedCornerShape(Look.cardRadius)).padding(16.dp)) {
            Text("App", color = Gold2, fontWeight = FontWeight.Bold, fontSize = 15.sp)
            Spacer(Modifier.height(6.dp))
            Text("Version: " + BuildConfig.VERSION_NAME + " (" + BuildConfig.VERSION_CODE + ")", color = Color.White, fontSize = 14.sp)
            Spacer(Modifier.height(4.dp))
            Text("Updates: checked automatically on start", color = Muted, fontSize = 12.sp)
            Spacer(Modifier.height(4.dp))
            Text("Check now", color = Gold, fontSize = 13.sp, fontWeight = FontWeight.Bold, modifier = Modifier.clickable {
                scope.launch {
                    updateInfo = "Checking..."
                    val latest = UpdateChecker.latest()
                    updateInfo = when {
                        latest == null -> "Could not check right now."
                        latest.code > BuildConfig.VERSION_CODE -> "Update available: " + latest.name + " - reopen the app to use the update popup."
                        else -> "You are on the latest version."
                    }
                }
            }.padding(vertical = 4.dp))
            updateInfo?.let { Text(it, color = Muted, fontSize = 12.sp) }
            Spacer(Modifier.height(14.dp))
            Text("X-Panel (floating)", color = Gold2, fontWeight = FontWeight.Bold, fontSize = 14.sp)
            Text("Floating tools over your game - full details in More > X-Panel.", color = Muted, fontSize = 12.sp)
            Spacer(Modifier.height(4.dp))
            XPanelSwitchRow()
            Spacer(Modifier.height(14.dp))
            Text("Data Saver", color = Gold2, fontWeight = FontWeight.Bold, fontSize = 14.sp)
            Text("For 2GB phones and slow networks.", color = Muted, fontSize = 12.sp)
            Spacer(Modifier.height(4.dp))
            DataSaverSwitchRow()
            Spacer(Modifier.height(14.dp))
            Text("Look", color = Gold2, fontWeight = FontWeight.Bold, fontSize = 14.sp)
            Spacer(Modifier.height(4.dp))
            LookThemeRows()
            Spacer(Modifier.height(14.dp))
            Text("Feel", color = Gold2, fontWeight = FontWeight.Bold, fontSize = 14.sp)
            Spacer(Modifier.height(4.dp))
            FxSwitchRows()
            Spacer(Modifier.height(14.dp))
            Text("Screen Recorder", color = Gold2, fontWeight = FontWeight.Bold, fontSize = 14.sp)
            Text("Records your screen to this phone only (Movies > XARENA). Nothing is uploaded.", color = Muted, fontSize = 12.sp)
            Spacer(Modifier.height(4.dp))
            RecorderControls()
        }
        Column(Modifier.padding(16.dp).clip(RoundedCornerShape(Look.cardRadius)).background(Look.cardBg).border(1.dp, Look.cardBorder, RoundedCornerShape(Look.cardRadius)).padding(16.dp)) {
            Text("Look", color = Gold2, fontWeight = FontWeight.Bold, fontSize = 15.sp)
            Spacer(Modifier.height(8.dp))
            var lookTick by remember { mutableIntStateOf(0) }
            Row {
                listOf(true to "Liquid", false to "Nova").forEach { (isLiquid, label) ->
                    val on = Look.liquid == isLiquid; lookTick.let { }
                    Text(label, color = if (on) Ink else Muted, fontSize = 13.sp, fontWeight = FontWeight.Bold,
                        modifier = Modifier.padding(end = 8.dp).clip(RoundedCornerShape(10.dp)).background(if (on) (if (isLiquid) Look.accent else Gold) else Card)
                            .clickable { Look.set(Api.ctx, isLiquid); lookTick++ }.padding(horizontal = 16.dp, vertical = 9.dp))
                }
            }
            Text("Liquid is the default glass look. Nova is the classic dark purple.", color = Muted, fontSize = 11.sp, modifier = Modifier.padding(top = 6.dp))
        }
        Column(Modifier.padding(16.dp).clip(RoundedCornerShape(Look.cardRadius)).background(Look.cardBg).border(1.dp, Look.cardBorder, RoundedCornerShape(Look.cardRadius)).padding(16.dp)) {
            Text("Privacy", color = Gold2, fontWeight = FontWeight.Bold, fontSize = 15.sp)
            Spacer(Modifier.height(8.dp))
            Row(Modifier.fillMaxWidth(), verticalAlignment = Alignment.CenterVertically) {
                Column(Modifier.weight(1f)) {
                    Text("Show my activity status", color = Color.White, fontSize = 14.sp)
                    Text("Green dot and last-seen for other players", color = Muted, fontSize = 12.sp)
                }
                Switch(checked = activityOn ?: true, onCheckedChange = { v ->
                    activityOn = v
                    scope.launch {
                        try { Api.authedRpc("presence_set", JSONObject().put("p_show", v)); msg = null }
                        catch (e: Exception) { msg = e.message; activityOn = !v }
                    }
                }, colors = SwitchDefaults.colors(checkedTrackColor = Look.accent))
            }
            msg?.let { Text(it, color = ErrRed, fontSize = 12.sp) }
        }
        Column(Modifier.padding(16.dp).clip(RoundedCornerShape(Look.cardRadius)).background(Look.cardBg).border(1.dp, Look.cardBorder, RoundedCornerShape(Look.cardRadius)).padding(16.dp)) {
            Text("Account", color = Gold2, fontWeight = FontWeight.Bold, fontSize = 15.sp)
            Spacer(Modifier.height(8.dp))
            Text("Log out", color = ErrRed, fontSize = 14.sp, fontWeight = FontWeight.Bold, modifier = Modifier.clickable { onLogout() }.padding(vertical = 6.dp))
        }
    }
}

/* ============ Player Card ============ */
@Composable
fun PlayerCardScreen(onBack: () -> Unit, onOpenConv: (String?, String, String) -> Unit) {
    val scope = rememberCoroutineScope()
    var search by remember { mutableStateOf("") }
    var card by remember { mutableStateOf<JSONObject?>(null) }
    var counts by remember { mutableStateOf<JSONObject?>(null) }
    var listKind by remember { mutableStateOf<String?>(null) }
    var people by remember { mutableStateOf<JSONArray?>(null) }
    var err by remember { mutableStateOf<String?>(null) }
    var notFound by remember { mutableStateOf(false) }
    var busy by remember { mutableStateOf(false) }
    var myName by remember { mutableStateOf("") }

    fun loadCard(name: String) {
        busy = true; err = null; notFound = false; card = null; counts = null; listKind = null; people = null
        scope.launch {
            try {
                var arr = Api.publicGet("/rest/v1/player_cards?username=eq." + java.net.URLEncoder.encode(name, "UTF-8") + "&select=*")
                if (arr.length() == 0) arr = Api.publicGet("/rest/v1/player_cards?ign=eq." + java.net.URLEncoder.encode(name, "UTF-8") + "&select=*")
                if (arr.length() == 0) { notFound = true; busy = false; return@launch }
                val p = arr.getJSONObject(0)
                card = p
                val c = Api.authedRpc("follow_counts", JSONObject().put("p_name", p.getString("username")))
                if (c.optBoolean("ok")) counts = c
            } catch (e: Exception) { err = e.message }
            busy = false
        }
    }
    LaunchedEffect(Unit) {
        val me = runCatching { Api.myProfile() }.getOrNull()
        myName = me?.s("username") ?: ""
        if (myName.isNotBlank()) loadCard(myName)
    }

    Column(Modifier.fillMaxSize().background(Ink)) {
        ExtrasHeader("Player Card", "Public card, followers and following", onBack)
        Row(Modifier.fillMaxWidth().padding(horizontal = 16.dp, vertical = 4.dp), verticalAlignment = Alignment.CenterVertically) {
            BasicTextField(search, { search = it }, modifier = Modifier.weight(1f).clip(RoundedCornerShape(10.dp)).background(Card).padding(12.dp),
                textStyle = TextStyle(color = Color.White, fontSize = 14.sp),
                decorationBox = { inner -> if (search.isEmpty()) Text("Open any player by name or IGN", color = Muted, fontSize = 14.sp); inner() })
            Spacer(Modifier.width(8.dp))
            Text("Open", color = Ink, fontWeight = FontWeight.Bold, fontSize = 13.sp,
                modifier = Modifier.clip(RoundedCornerShape(10.dp)).background(Gold).clickable { if (search.isNotBlank()) loadCard(search.trim()) }.padding(horizontal = 14.dp, vertical = 10.dp))
        }
        err?.let { Text(it, color = ErrRed, fontSize = 13.sp, modifier = Modifier.padding(16.dp)) }
        if (notFound) Text("Player not found. Check the name and try again.", color = Muted, fontSize = 14.sp, modifier = Modifier.padding(24.dp))
        if (busy) Box(Modifier.fillMaxWidth().padding(24.dp), contentAlignment = Alignment.Center) { CircularProgressIndicator(color = Gold) }
        card?.let { p ->
            val uname = p.getString("username")
            val isMe = uname == myName
            LazyColumn(Modifier.fillMaxSize().padding(horizontal = 16.dp)) {
                item {
                    Column(Modifier.fillMaxWidth().clip(RoundedCornerShape(18.dp)).background(Card.copy(alpha = .85f)).border(1.dp, Gold.copy(alpha = .35f), RoundedCornerShape(18.dp)).padding(18.dp), horizontalAlignment = Alignment.CenterHorizontally) {
                        Box(Modifier.size(84.dp).clip(CircleShape).background(Ink).border(2.dp, Gold2, CircleShape), contentAlignment = Alignment.Center) {
                            Text((p.s("ign").ifBlank { uname }).firstOrNull()?.uppercase() ?: "?", color = Gold, fontSize = 30.sp, fontWeight = FontWeight.Bold)
                        }
                        Spacer(Modifier.height(10.dp))
                        Text(p.s("ign").ifBlank { uname } + if (p.optBoolean("verified")) " ✓" else "", color = Gold2, fontSize = 20.sp, fontWeight = FontWeight.Bold)
                        Text("@" + uname + if (!p.s("squad").isBlank()) " - Squad: " + p.s("squad") else "", color = Muted, fontSize = 13.sp)
                        Spacer(Modifier.height(12.dp))
                        Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.SpaceEvenly) {
                            Column(horizontalAlignment = Alignment.CenterHorizontally) {
                                Text((counts?.optInt("posts") ?: 0).toString(), color = Gold2, fontWeight = FontWeight.Bold, fontSize = 16.sp); Text("Posts", color = Muted, fontSize = 11.sp)
                            }
                            Column(horizontalAlignment = Alignment.CenterHorizontally, modifier = Modifier.clickable {
                                listKind = if (listKind == "followers") null else "followers"; people = null
                                if (listKind != null) scope.launch {
                                    people = runCatching { Api.authedRpc("follow_list", JSONObject().put("p_name", uname).put("p_kind", "followers")).optJSONArray("people") }.getOrNull()
                                }
                            }) {
                                Text((counts?.optInt("followers") ?: 0).toString(), color = Gold2, fontWeight = FontWeight.Bold, fontSize = 16.sp); Text("Followers", color = Muted, fontSize = 11.sp)
                            }
                            Column(horizontalAlignment = Alignment.CenterHorizontally, modifier = Modifier.clickable {
                                listKind = if (listKind == "following") null else "following"; people = null
                                if (listKind != null) scope.launch {
                                    people = runCatching { Api.authedRpc("follow_list", JSONObject().put("p_name", uname).put("p_kind", "following")).optJSONArray("people") }.getOrNull()
                                }
                            }) {
                                Text((counts?.optInt("following") ?: 0).toString(), color = Gold2, fontWeight = FontWeight.Bold, fontSize = 16.sp); Text("Following", color = Muted, fontSize = 11.sp)
                            }
                        }
                        Spacer(Modifier.height(12.dp))
                        if (!isMe) Row {
                            Text(if (counts?.optBoolean("i_follow") == true) "Following ✓" else "Follow", color = Ink, fontWeight = FontWeight.Bold, fontSize = 13.sp,
                                modifier = Modifier.clip(RoundedCornerShape(10.dp)).background(Gold).clickable {
                                    scope.launch {
                                        try {
                                            val r = Api.authedRpc("follow_toggle", JSONObject().put("p_name", uname))
                                            val c = Api.authedRpc("follow_counts", JSONObject().put("p_name", uname))
                                            if (c.optBoolean("ok")) counts = c
                                            if (!r.optBoolean("ok", true)) err = r.s("error")
                                        } catch (e: Exception) { err = e.message }
                                    }
                                }.padding(horizontal = 16.dp, vertical = 9.dp))
                            Spacer(Modifier.width(10.dp))
                            Text("Message", color = Gold, fontSize = 13.sp, fontWeight = FontWeight.Bold,
                                modifier = Modifier.clip(RoundedCornerShape(10.dp)).border(1.dp, Gold, RoundedCornerShape(10.dp)).clickable {
                                    scope.launch {
                                        try {
                                            val r = Api.authedRpc("dm_open_by_name", JSONObject().put("p_name", uname))
                                            when {
                                                r.optBoolean("requested") -> err = "Message request sent - they can accept it in Messages > Requests."
                                                r.optBoolean("ok") -> {
                                                    val tid = r.s("thread").ifBlank { r.s("thread_id") }
                                                    onOpenConv(tid.ifBlank { null }, "", p.s("ign").ifBlank { uname })
                                                }
                                                else -> err = r.s("error").ifBlank { "Could not open chat." }
                                            }
                                        } catch (e: Exception) { err = e.message }
                                    }
                                }.padding(horizontal = 16.dp, vertical = 9.dp))
                        } else Text("This is your card", color = Muted, fontSize = 12.sp)
                    }
                }
                listKind?.let { kind ->
                    item { Text(kind.uppercase(), color = Muted, fontSize = 11.sp, fontWeight = FontWeight.Bold, modifier = Modifier.padding(vertical = 8.dp)) }
                    if (people == null) item { CircularProgressIndicator(color = Gold) }
                    else if (people!!.length() == 0) item { Text("Nobody here yet.", color = Muted, fontSize = 13.sp, modifier = Modifier.padding(bottom = 12.dp)) }
                    else items((0 until people!!.length()).map { people!!.getJSONObject(it) }) { x ->
                        Text(x.s("name").ifBlank { x.s("ign") }, color = Color.White, fontSize = 14.sp,
                            modifier = Modifier.fillMaxWidth().padding(vertical = 2.dp).clip(RoundedCornerShape(8.dp)).background(Card.copy(alpha = .6f))
                                .clickable { loadCard(x.s("name")) }.padding(10.dp))
                    }
                }
                item {
                    Spacer(Modifier.height(12.dp))
                    Column(Modifier.fillMaxWidth().clip(RoundedCornerShape(16.dp)).background(Card.copy(alpha = .85f)).padding(16.dp)) {
                        Text("Free Fire identity", color = Gold2, fontWeight = FontWeight.Bold, fontSize = 15.sp)
                        Spacer(Modifier.height(6.dp))
                        listOf("Role" to p.s("role"), "Free Fire rank" to p.s("ff_rank"), "K/D" to (if (p.isNull("kd")) "" else p.optDouble("kd").toString())).forEach { (k, v) -> // device/ff_uid hidden from public player_cards
                            if (v.isNotBlank() && v != "null") Row(Modifier.fillMaxWidth().padding(vertical = 5.dp)) {
                                Text(k, color = Muted, fontSize = 13.sp, modifier = Modifier.weight(1f))
                                Text(v, color = Color.White, fontSize = 13.sp)
                            }
                        }
                    }
                    Spacer(Modifier.height(12.dp))
                    Row(Modifier.fillMaxWidth().clip(RoundedCornerShape(16.dp)).background(Card.copy(alpha = .85f)).padding(16.dp), horizontalArrangement = Arrangement.SpaceEvenly) {
                        Column(horizontalAlignment = Alignment.CenterHorizontally) { Text(p.optInt("matches").toString(), color = Gold2, fontWeight = FontWeight.Bold, fontSize = 18.sp); Text("Matches", color = Muted, fontSize = 11.sp) }
                        Column(horizontalAlignment = Alignment.CenterHorizontally) { Text(p.optInt("wins").toString(), color = Gold2, fontWeight = FontWeight.Bold, fontSize = 18.sp); Text("Wins", color = Muted, fontSize = 11.sp) }
                        Column(horizontalAlignment = Alignment.CenterHorizontally) { Text(if (p.optBoolean("verified")) "✓" else "-", color = Gold2, fontWeight = FontWeight.Bold, fontSize = 18.sp); Text("Verified", color = Muted, fontSize = 11.sp) }
                    }
                    Spacer(Modifier.height(24.dp))
                }
            }
        }
    }
}

/* ---- Haptics + sounds (both forced off when Data Saver is on) ---- */
object Fx {
    private val p get() = Api.ctx.getSharedPreferences("xr_prefs", 0)
    var hapticsOn: Boolean get() = p.getBoolean("fx_haptics", true); set(v) = p.edit().putBoolean("fx_haptics", v).apply()
    var soundsOn: Boolean get() = p.getBoolean("fx_sounds", true); set(v) = p.edit().putBoolean("fx_sounds", v).apply()
    fun haptics() = hapticsOn && !DataSaver.on
    fun sounds() = soundsOn && !DataSaver.on
    fun blip() {
        if (!sounds()) return
        runCatching {
            val tg = android.media.ToneGenerator(android.media.AudioManager.STREAM_NOTIFICATION, 60)
            tg.startTone(android.media.ToneGenerator.TONE_PROP_ACK, 140)
            android.os.Handler(android.os.Looper.getMainLooper()).postDelayed({ tg.release() }, 220)
        }
    }
}

@Composable
fun FxSwitchRows() {
    var h by remember { mutableStateOf(Fx.hapticsOn) }
    var so by remember { mutableStateOf(Fx.soundsOn) }
    Column {
        Row(Modifier.fillMaxWidth(), verticalAlignment = Alignment.CenterVertically) {
            Text("Button vibration" + (if (DataSaver.on) " (off while Data Saver is on)" else ""), color = Muted, fontSize = 12.sp, modifier = Modifier.weight(1f))
            Switch(checked = h, onCheckedChange = { h = it; Fx.hapticsOn = it })
        }
        Row(Modifier.fillMaxWidth(), verticalAlignment = Alignment.CenterVertically) {
            Text("Soft sounds on wins and claims" + (if (DataSaver.on) " (off while Data Saver is on)" else ""), color = Muted, fontSize = 12.sp, modifier = Modifier.weight(1f))
            Switch(checked = so, onCheckedChange = { so = it; Fx.soundsOn = it })
        }
    }
}

/* ---- Theme picker - new looks are EXTRA options; the default look stays. Applies app-wide instantly. ---- */
@Composable
fun LookThemeRows() {
    var liquid by remember { mutableStateOf(Look.liquid) }
    var redGold by remember { mutableStateOf(Look.redGold) }
    Column {
        Row(Modifier.fillMaxWidth(), verticalAlignment = Alignment.CenterVertically) {
            Column(Modifier.weight(1f)) {
                Text("Theme: " + if (liquid) "Liquid (default)" else "Classic", color = Color.White, fontSize = 13.sp)
                Text("Classic = the older solid cards look", color = Muted, fontSize = 11.sp)
            }
            Switch(checked = liquid, onCheckedChange = { liquid = it; Look.set(Api.ctx, it) })
        }
        Spacer(Modifier.height(6.dp))
        Row(Modifier.fillMaxWidth(), verticalAlignment = Alignment.CenterVertically) {
            Column(Modifier.weight(1f)) {
                Text("Accent: " + if (redGold) "Red-Gold" else "Gold (default)", color = Color.White, fontSize = 13.sp)
                Text("Red-Gold = the tournament-night accent", color = Muted, fontSize = 11.sp)
            }
            Switch(checked = redGold, onCheckedChange = { redGold = it; Look.setRedGold(Api.ctx, it) })
        }
        Spacer(Modifier.height(6.dp))
        var darkGold by remember { mutableStateOf(Look.darkGold) }
        Row(Modifier.fillMaxWidth(), verticalAlignment = Alignment.CenterVertically) {
            Column(Modifier.weight(1f)) {
                Text("Look: " + if (darkGold) "Dark Gold" else "Purple Night (default)", color = Color.White, fontSize = 13.sp)
                Text("Dark Gold = the site's black-gold look", color = Muted, fontSize = 11.sp)
            }
            Switch(checked = darkGold, onCheckedChange = { darkGold = it; Look.setDarkGold(Api.ctx, it) })
        }
        Spacer(Modifier.height(6.dp))
        var hi by remember { mutableStateOf(Txt.hi) }
        Row(Modifier.fillMaxWidth(), verticalAlignment = Alignment.CenterVertically) {
            Column(Modifier.weight(1f)) {
                Text("Language: " + if (hi) "Hinglish" else "English (default)", color = Color.White, fontSize = 13.sp)
                Text("Fixed app labels only - posts and names stay as written", color = Muted, fontSize = 11.sp)
            }
            Switch(checked = hi, onCheckedChange = { hi = it; Txt.set(Api.ctx, it) })
        }
    }
}

/* ---- Data saver (low-data / low-FX mode for 2GB phones) ---- */
object DataSaver {
    private val p get() = Api.ctx.getSharedPreferences("xr_prefs", 0)
    var on: Boolean get() = p.getBoolean("data_saver", false); set(v) = p.edit().putBoolean("data_saver", v).apply()
}

@Composable
fun DataSaverSwitchRow() {
    var on by remember { mutableStateOf(DataSaver.on) }
    Row(Modifier.fillMaxWidth(), verticalAlignment = Alignment.CenterVertically) {
        Text(if (on) "On - animations, 3D effects and autoplay videos are off (saves data and battery)"
        else "Off - full animations and effects", color = if (on) Color(0xFF4ADE80) else Muted, fontSize = 12.sp, modifier = Modifier.weight(1f))
        Switch(checked = on, onCheckedChange = { on = it; DataSaver.on = it })
    }
}

/* ---- X-Panel + Screen Recorder controls (shared by Settings and the More-menu screens) ---- */

@Composable
fun XPanelSwitchRow() {
    var panelOn by remember { mutableStateOf(XPanelStore.enabled) }
    var overlayOk by remember { mutableStateOf(android.provider.Settings.canDrawOverlays(Api.ctx)) }
    val lifecycleOwner = androidx.compose.ui.platform.LocalLifecycleOwner.current
    DisposableEffect(lifecycleOwner) {
        val obs = androidx.lifecycle.LifecycleEventObserver { _, ev ->
            if (ev == androidx.lifecycle.Lifecycle.Event.ON_RESUME) {
                overlayOk = android.provider.Settings.canDrawOverlays(Api.ctx)
                if (panelOn) XPanelStore.sync(Api.ctx)
            }
        }
        lifecycleOwner.lifecycle.addObserver(obs)
        onDispose { lifecycleOwner.lifecycle.removeObserver(obs) }
    }
    Row(Modifier.fillMaxWidth(), verticalAlignment = Alignment.CenterVertically) {
        Text(when {
            panelOn && overlayOk -> "On - swipe the right edge of your screen to open the panel"
            overlayOk -> "Off"
            panelOn -> "Almost there - allow 'Display over other apps' and come back"
            else -> "Off - switch on to allow the overlay permission"
        }, color = if (panelOn && overlayOk) Color(0xFF4ADE80) else Muted, fontSize = 12.sp, modifier = Modifier.weight(1f))
        Switch(checked = panelOn && overlayOk, onCheckedChange = { want ->
            panelOn = want
            XPanelStore.enabled = want
            if (want && !overlayOk) {
                Api.ctx.startActivity(android.content.Intent(android.provider.Settings.ACTION_MANAGE_OVERLAY_PERMISSION,
                    android.net.Uri.parse("package:" + Api.ctx.packageName)).addFlags(android.content.Intent.FLAG_ACTIVITY_NEW_TASK))
            } else XPanelStore.sync(Api.ctx)
        })
    }
}

@Composable
fun RecorderControls() {
    var rec by remember { mutableStateOf(RecStore.recording) }
    var secs by remember { mutableIntStateOf(0) }
    LaunchedEffect(Unit) {
        while (true) { rec = RecStore.recording; secs = ((System.currentTimeMillis() - RecStore.startedAt) / 1000).toInt(); kotlinx.coroutines.delay(1000) }
    }
    GoldButton(if (rec) "STOP RECORDING" else "START RECORDING") {
        if (RecStore.recording) Api.ctx.startService(android.content.Intent(Api.ctx, RecorderService::class.java).setAction("stop"))
        else Api.ctx.startActivity(android.content.Intent(Api.ctx, RecorderActivity::class.java).addFlags(android.content.Intent.FLAG_ACTIVITY_NEW_TASK))
    }
    if (rec) Text("Recording " + secs + "s - saved on this phone only (Movies > XARENA)", color = Color(0xFF4ADE80), fontSize = 12.sp)
    if (!rec && RecStore.lastError != null) Text("Last try failed: " + RecStore.lastError, color = Color(0xFFFF8A80), fontSize = 11.sp)
}

@Composable
fun XPanelScreen(onBack: () -> Unit) {
    LazyColumn(contentPadding = PaddingValues(16.dp), verticalArrangement = Arrangement.spacedBy(10.dp)) {
        item { Row(verticalAlignment = Alignment.CenterVertically) {
            Text("< Back", color = Gold, fontSize = 14.sp, modifier = Modifier.clip(RoundedCornerShape(6.dp)).clickable { onBack() }.padding(8.dp))
            Text("X-Panel", color = Gold2, fontSize = 20.sp, fontWeight = FontWeight.Bold)
        } }
        item { PanelCard {
            Text("Floating tools over your game", color = Gold2, fontWeight = FontWeight.Bold, fontSize = 15.sp)
            Spacer(Modifier.height(4.dp))
            Text("Swipe in from the small gold handle on the right edge of your screen to open the full panel - over any app.",
                color = Muted, fontSize = 12.sp)
            Spacer(Modifier.height(8.dp))
            XPanelSwitchRow()
        } }
        item { PanelCard {
            Text("What is inside", color = Gold2, fontWeight = FontWeight.Bold, fontSize = 15.sp)
            Spacer(Modifier.height(4.dp))
            listOf(
                "Clan Room - chat with your squad without leaving the game",
                "Match details - today\'s match, room ID and password with one-tap copy",
                "Your EXP, level and AP",
                "Messages - new DM alerts",
                "Voice Lobby - one tap to talk with your clan",
                "XBoost - Do Not Disturb switch, clear app cache, real RAM and battery info",
                "Screen Recorder button"
            ).forEach { Text("- " + it, color = Color.White, fontSize = 13.sp, modifier = Modifier.padding(vertical = 2.dp)) }
            Spacer(Modifier.height(6.dp))
            Text("Honest limits: it never reads your screen, never touches the game, and there are no fake FPS claims. You can turn it off any time.",
                color = Muted, fontSize = 12.sp)
        } }
    }
}

@Composable
fun RecorderScreen(onBack: () -> Unit) {
    LazyColumn(contentPadding = PaddingValues(16.dp), verticalArrangement = Arrangement.spacedBy(10.dp)) {
        item { Row(verticalAlignment = Alignment.CenterVertically) {
            Text("< Back", color = Gold, fontSize = 14.sp, modifier = Modifier.clip(RoundedCornerShape(6.dp)).clickable { onBack() }.padding(8.dp))
            Text("Screen Recorder", color = Gold2, fontSize = 20.sp, fontWeight = FontWeight.Bold)
        } }
        item { PanelCard {
            Text("Record your gameplay", color = Gold2, fontWeight = FontWeight.Bold, fontSize = 15.sp)
            Spacer(Modifier.height(4.dp))
            Text("Records your screen (and your mic if you allowed it). The video is saved ONLY on this phone: Movies > XARENA. Nothing is uploaded, ever.",
                color = Muted, fontSize = 12.sp)
            Spacer(Modifier.height(8.dp))
            RecorderControls()
        } }
        item { PanelCard {
            Text("Good to know", color = Gold2, fontWeight = FontWeight.Bold, fontSize = 15.sp)
            Spacer(Modifier.height(4.dp))
            listOf(
                "Android asks for permission every time you start - that is normal and keeps you in control",
                "Clear quality (up to 1280 px), 30 frames per second, with sound if you allowed the mic",
                "On a 2GB phone, recording while gaming can lower your FPS - close other apps first",
                "Stop from the notification, from here, or from the X-Panel"
            ).forEach { Text("- " + it, color = Color.White, fontSize = 13.sp, modifier = Modifier.padding(vertical = 2.dp)) }
        } }
    }
}

