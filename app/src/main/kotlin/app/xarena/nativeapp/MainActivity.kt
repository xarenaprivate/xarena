package app.xarena.nativeapp

import android.os.Bundle
import androidx.activity.ComponentActivity
import androidx.activity.compose.setContent
import androidx.compose.animation.core.*
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.alpha
import androidx.compose.ui.draw.clip
import androidx.compose.ui.draw.drawBehind
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.input.KeyboardType
import androidx.compose.ui.text.input.PasswordVisualTransformation
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import kotlinx.coroutines.launch
import org.json.JSONArray
import org.json.JSONObject

val Gold = Color(0xFFF5C451)
val Gold2 = Color(0xFFFFE9A3)
val Ink = Color(0xFF120A24)
val Card = Color(0xFF241440)
val Muted = Color(0xFFB79CE0)

class MainActivity : ComponentActivity() {
    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        Api.init(this); Api.restore()
        setContent { XarenaApp() }
    }
}

@Composable
fun XarenaApp() {
    var loggedIn by remember { mutableStateOf(Api.loggedIn) }
    MaterialTheme(colorScheme = darkColorScheme(primary = Gold, background = Ink, surface = Card, onSurface = Color.White)) {
        NovaBackground {
            if (loggedIn) HomeShell(onLogout = { Api.logout(); loggedIn = false })
            else LoginScreen(onDone = { loggedIn = true })
        }
    }
}

/** Animated purple/gold gradient: slow drifting glow (cheap, no 3D engine). */
@Composable
fun NovaBackground(content: @Composable () -> Unit) {
    val t = rememberInfiniteTransition(label = "bg")
    val drift by t.animateFloat(0f, 1f, infiniteRepeatable(tween(9000, easing = LinearEasing), RepeatMode.Reverse), label = "d")
    Box(Modifier.fillMaxSize().background(Ink).drawBehind {
        drawRect(Brush.radialGradient(listOf(Color(0x558B3DFF), Color.Transparent),
            center = Offset(size.width * (0.2f + 0.6f * drift), size.height * 0.15f), radius = size.width * 0.9f))
        drawRect(Brush.radialGradient(listOf(Color(0x33F5C451), Color.Transparent),
            center = Offset(size.width * (0.9f - 0.5f * drift), size.height * 0.85f), radius = size.width * 0.8f))
    }.statusBarsPadding().navigationBarsPadding()) { content() }
}

@Composable
fun Brand() {
    Column(horizontalAlignment = Alignment.CenterHorizontally, modifier = Modifier.fillMaxWidth().padding(top = 18.dp, bottom = 10.dp)) {
        Text("XARENA", color = Gold, fontSize = 34.sp, fontWeight = FontWeight.Black, letterSpacing = 6.sp)
        Text("ESPORTS", color = Gold2, fontSize = 13.sp, fontWeight = FontWeight.Bold, letterSpacing = 8.sp)
    }
}

@Composable
fun GoldButton(text: String, enabled: Boolean = true, onClick: () -> Unit) {
    Box(Modifier.fillMaxWidth().height(52.dp).clip(RoundedCornerShape(14.dp)).alpha(if (enabled) 1f else .5f)
        .background(Brush.horizontalGradient(listOf(Gold2, Gold)))
        .clickable(enabled = enabled, onClick = onClick), contentAlignment = Alignment.Center) {
        Text(text, color = Color(0xFF2A1400), fontWeight = FontWeight.Bold, fontSize = 16.sp)
    }
}

@Composable
fun LoginScreen(onDone: () -> Unit) {
    var id by remember { mutableStateOf("") }
    var pw by remember { mutableStateOf("") }
    var busy by remember { mutableStateOf(false) }
    var err by remember { mutableStateOf<String?>(null) }
    val scope = rememberCoroutineScope()
    Column(Modifier.fillMaxSize().padding(24.dp), verticalArrangement = Arrangement.Center) {
        Brand()
        Spacer(Modifier.height(20.dp))
        Column(Modifier.clip(RoundedCornerShape(20.dp)).background(Card.copy(alpha = .85f))
            .border(1.dp, Gold.copy(alpha = .35f), RoundedCornerShape(20.dp)).padding(18.dp)) {
            Text("Login", color = Color.White, fontSize = 22.sp, fontWeight = FontWeight.Bold)
            Spacer(Modifier.height(12.dp))
            OutlinedTextField(id, { id = it }, label = { Text("Phone number or email") }, singleLine = true, modifier = Modifier.fillMaxWidth())
            Spacer(Modifier.height(10.dp))
            OutlinedTextField(pw, { pw = it }, label = { Text("Password") }, singleLine = true, visualTransformation = PasswordVisualTransformation(),
                keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Password), modifier = Modifier.fillMaxWidth())
            err?.let { Spacer(Modifier.height(8.dp)); Text(it, color = Color(0xFFFF8A8A), fontSize = 13.sp) }
            Spacer(Modifier.height(16.dp))
            GoldButton(if (busy) "LOGGING IN..." else "LOGIN", enabled = !busy && id.isNotBlank() && pw.length >= 6) {
                busy = true; err = null
                scope.launch {
                    try { Api.login(id, pw); onDone() } catch (e: Exception) { err = e.message ?: "Login failed." }
                    busy = false
                }
            }
            Spacer(Modifier.height(10.dp))
            Text("New player? Create your free account on the XARENA website or WebView app for now - native sign-up is coming.", color = Muted, fontSize = 12.sp)
        }
    }
}

@Composable
fun HomeShell(onLogout: () -> Unit) {
    var tab by remember { mutableIntStateOf(0) }
    val tabs = listOf("Tournaments", "Leaderboard", "Profile")
    Column(Modifier.fillMaxSize()) {
        Box(Modifier.weight(1f)) {
            when (tab) { 0 -> TournamentsScreen(); 1 -> LeaderboardScreen(); else -> ProfileScreen(onLogout) }
        }
        Row(Modifier.fillMaxWidth().background(Color(0xFF1A0E33)).padding(vertical = 6.dp)) {
            tabs.forEachIndexed { i, label ->
                Column(Modifier.weight(1f).clickable { tab = i }.padding(8.dp), horizontalAlignment = Alignment.CenterHorizontally) {
                    Box(Modifier.size(6.dp).clip(CircleShape).background(if (tab == i) Gold else Color.Transparent))
                    Text(label, color = if (tab == i) Gold else Muted, fontSize = 13.sp, fontWeight = if (tab == i) FontWeight.Bold else FontWeight.Medium)
                }
            }
        }
    }
}

@Composable
fun <T> LoadState(load: suspend () -> T, content: @Composable (T) -> Unit) {
    var state by remember { mutableStateOf<Result<T>?>(null) }
    var nonce by remember { mutableIntStateOf(0) }
    LaunchedEffect(nonce) { state = null; state = runCatching { load() } }
    val s = state
    when {
        s == null -> Box(Modifier.fillMaxSize(), contentAlignment = Alignment.Center) { CircularProgressIndicator(color = Gold) }
        s.isFailure -> Column(Modifier.fillMaxSize().padding(24.dp), verticalArrangement = Arrangement.Center, horizontalAlignment = Alignment.CenterHorizontally) {
            Text(s.exceptionOrNull()?.message ?: "Could not load.", color = Color(0xFFFF8A8A)); Spacer(Modifier.height(12.dp))
            GoldButton("Retry") { nonce++ }
        }
        else -> content(s.getOrThrow())
    }
}

@Composable
fun PanelCard(content: @Composable ColumnScope.() -> Unit) {
    Column(Modifier.fillMaxWidth().padding(horizontal = 16.dp, vertical = 6.dp).clip(RoundedCornerShape(16.dp))
        .background(Card.copy(alpha = .85f)).border(1.dp, Gold.copy(alpha = .3f), RoundedCornerShape(16.dp)).padding(14.dp), content = content)
}

@Composable
fun TournamentsScreen() {
    Column(Modifier.fillMaxSize()) {
        Brand()
        LoadState({ Api.publicGet("/rest/v1/tournaments?select=id,name,status,slots,prize,date_text,reg_open,mode&order=sort.asc") }) { arr ->
            if (arr.length() == 0) EmptyNote("No tournaments yet", "Tournaments appear here as soon as the admin creates them.")
            else LazyColumn { items((0 until arr.length()).map { arr.getJSONObject(it) }) { t ->
                PanelCard {
                    Text(t.optString("name"), color = Gold2, fontSize = 18.sp, fontWeight = FontWeight.Bold)
                    Text(t.optString("mode") + " - " + t.optInt("slots") + " slots", color = Muted, fontSize = 13.sp)
                    Spacer(Modifier.height(6.dp))
                    Text(t.optString("date_text"), color = Color.White, fontSize = 14.sp)
                    Text(t.optString("prize"), color = Color.White, fontSize = 14.sp)
                    Spacer(Modifier.height(6.dp))
                    Text((t.optString("status").uppercase()) + if (t.optBoolean("reg_open")) " - REGISTRATION OPEN" else " - REGISTRATION CLOSED",
                        color = Gold, fontSize = 12.sp, fontWeight = FontWeight.Bold)
                }
            } }
        }
    }
}

@Composable
fun LeaderboardScreen() {
    Column(Modifier.fillMaxSize()) {
        Brand()
        LoadState({ Api.publicRpc("game_leaderboard", JSONObject().put("p_scope", "all")).optJSONArray("rows") ?: JSONArray() }) { rows ->
            if (rows.length() == 0) EmptyNote("No XP earned yet", "The board lights up when players check in and play.")
            else LazyColumn { items((0 until rows.length()).map { it to rows.getJSONObject(it) }) { (i, r) ->
                PanelCard {
                    Row(verticalAlignment = Alignment.CenterVertically) {
                        Text("#" + (i + 1), color = Gold, fontSize = 20.sp, fontWeight = FontWeight.Black, modifier = Modifier.width(54.dp))
                        Column(Modifier.weight(1f)) {
                            Text(r.optString("name"), color = Color.White, fontSize = 16.sp, fontWeight = FontWeight.Bold)
                            Text("Level " + r.optInt("level") + " - " + r.optInt("badges") + " badges", color = Muted, fontSize = 12.sp)
                        }
                        Text(r.optInt("xp").toString() + " XP", color = Gold2, fontWeight = FontWeight.Bold)
                    }
                }
            } }
        }
    }
}

@Composable
fun ProfileScreen(onLogout: () -> Unit) {
    Column(Modifier.fillMaxSize()) {
        Brand()
        LoadState({ Api.myProfile() to Api.myStats() }) { (p, s) ->
            Column {
                PanelCard {
                    Text(p?.optString("ign").takeUnless { it.isNullOrBlank() || it == "null" } ?: p?.optString("username") ?: "Player", color = Gold2, fontSize = 22.sp, fontWeight = FontWeight.Bold)
                    if (p != null) {
                        @Composable fun row(k: String, v: String?) { if (!v.isNullOrBlank() && v != "null") Text("$k: $v", color = Color.White, fontSize = 14.sp) }
                        row("Free Fire UID", p.optString("ff_uid")); row("Role", p.optString("role")); row("Rank", p.optString("ff_rank"))
                        row("Verified", if (p.optBoolean("verified")) "Yes" else "Not yet")
                        row("Matches", p.optString("matches")); row("Wins", p.optString("wins"))
                    } else Text("Profile not found.", color = Muted)
                }
                if (s != null) PanelCard {
                    Text("Your game stats", color = Gold, fontWeight = FontWeight.Bold)
                    s.keys().forEach { k -> val v = s.opt(k); if (v is Number || v is String) Text("$k: $v", color = Color.White, fontSize = 13.sp) }
                }
                Spacer(Modifier.height(12.dp))
                Box(Modifier.padding(horizontal = 16.dp)) { GoldButton("LOG OUT", onClick = onLogout) }
            }
        }
    }
}

@Composable
fun EmptyNote(title: String, body: String) {
    Column(Modifier.fillMaxSize().padding(28.dp), verticalArrangement = Arrangement.Center, horizontalAlignment = Alignment.CenterHorizontally) {
        Text(title, color = Gold2, fontSize = 20.sp, fontWeight = FontWeight.Bold)
        Spacer(Modifier.height(8.dp)); Text(body, color = Muted, fontSize = 14.sp)
    }
}
