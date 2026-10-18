package app.xarena.nativeapp

import android.os.Bundle
import androidx.activity.ComponentActivity
import androidx.activity.compose.setContent
import androidx.compose.animation.core.*
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.horizontalScroll
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
import androidx.compose.ui.platform.LocalClipboardManager
import androidx.compose.ui.text.AnnotatedString
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.input.KeyboardType
import androidx.compose.ui.text.input.PasswordVisualTransformation
import androidx.compose.ui.text.style.TextAlign
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
val ErrRed = Color(0xFFFF8A8A)

class MainActivity : ComponentActivity() {
    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        Api.init(this); Api.restore()
        setContent { XarenaApp() }
    }
}

enum class Screen { Login, Signup, CompleteProfile, Home }

@Composable
fun XarenaApp() {
    var screen by remember { mutableStateOf(if (Api.loggedIn) Screen.Home else Screen.Login) }
    var setupChecked by remember { mutableStateOf(false) }
    MaterialTheme(colorScheme = darkColorScheme(primary = Gold, background = Ink, surface = Card, onSurface = Color.White)) {
        NovaBackground {
            when (screen) {
                Screen.Login -> LoginScreen(
                    onDone = { setupChecked = false; screen = Screen.Home },
                    onGoSignup = { screen = Screen.Signup })
                Screen.Signup -> SignupScreen(
                    onDone = { setupChecked = false; screen = Screen.Home },
                    onGoLogin = { screen = Screen.Login })
                Screen.CompleteProfile -> CompleteProfileScreen(onDone = { screen = Screen.Home })
                Screen.Home -> {
                    if (!setupChecked) {
                        LaunchedEffect(Unit) {
                            val needs = runCatching { Api.needsProfileSetup() }.getOrDefault(false)
                            setupChecked = true
                            if (needs) screen = Screen.CompleteProfile
                        }
                        Box(Modifier.fillMaxSize(), contentAlignment = Alignment.Center) { CircularProgressIndicator(color = Gold) }
                    } else HomeShell(onLogout = { Api.logout(); screen = Screen.Login })
                }
            }
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
    Column(horizontalAlignment = Alignment.CenterHorizontally, modifier = Modifier.fillMaxWidth().padding(top = 14.dp, bottom = 8.dp)) {
        androidx.compose.foundation.Image(
            painter = androidx.compose.ui.res.painterResource(R.drawable.xarena_logo),
            contentDescription = "XARENA",
            modifier = Modifier.size(76.dp).clip(RoundedCornerShape(18.dp))
        )
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
fun AuthCard(title: String, content: @Composable ColumnScope.() -> Unit) {
    Column(Modifier.fillMaxSize().padding(24.dp), verticalArrangement = Arrangement.Center) {
        Brand()
        Spacer(Modifier.height(20.dp))
        Column(Modifier.clip(RoundedCornerShape(20.dp)).background(Card.copy(alpha = .85f))
            .border(1.dp, Gold.copy(alpha = .35f), RoundedCornerShape(20.dp)).padding(18.dp)) {
            Text(title, color = Color.White, fontSize = 22.sp, fontWeight = FontWeight.Bold)
            Spacer(Modifier.height(12.dp))
            content()
        }
    }
}

@Composable
fun LoginScreen(onDone: () -> Unit, onGoSignup: () -> Unit) {
    var id by remember { mutableStateOf("") }
    var pw by remember { mutableStateOf("") }
    var busy by remember { mutableStateOf(false) }
    var err by remember { mutableStateOf<String?>(null) }
    val scope = rememberCoroutineScope()
    AuthCard("Login") {
        OutlinedTextField(id, { id = it }, label = { Text("Phone number or email") }, singleLine = true, modifier = Modifier.fillMaxWidth())
        Spacer(Modifier.height(10.dp))
        OutlinedTextField(pw, { pw = it }, label = { Text("Password") }, singleLine = true, visualTransformation = PasswordVisualTransformation(),
            keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Password), modifier = Modifier.fillMaxWidth())
        err?.let { Spacer(Modifier.height(8.dp)); Text(it, color = ErrRed, fontSize = 13.sp) }
        Spacer(Modifier.height(16.dp))
        GoldButton(if (busy) "LOGGING IN..." else "LOGIN", enabled = !busy && id.isNotBlank() && pw.length >= 6) {
            busy = true; err = null
            scope.launch {
                try { Api.login(id, pw); onDone() } catch (e: Exception) { err = e.message ?: "Login failed." }
                busy = false
            }
        }
        Spacer(Modifier.height(10.dp))
        Text("New player? Create a free account", color = Gold2, fontSize = 13.sp, modifier = Modifier.clickable { onGoSignup() })
    }
}

@Composable
fun SignupScreen(onDone: () -> Unit, onGoLogin: () -> Unit) {
    var phone by remember { mutableStateOf("") }
    var pw by remember { mutableStateOf("") }
    var busy by remember { mutableStateOf(false) }
    var err by remember { mutableStateOf<String?>(null) }
    val scope = rememberCoroutineScope()
    AuthCard("Create free account") {
        OutlinedTextField(phone, { phone = it }, label = { Text("WhatsApp phone number (10 digit)") }, singleLine = true,
            keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Phone), modifier = Modifier.fillMaxWidth())
        Text("This is your login ID. One number = one account.", color = Muted, fontSize = 12.sp)
        Spacer(Modifier.height(10.dp))
        OutlinedTextField(pw, { pw = it }, label = { Text("Password (6+ chars)") }, singleLine = true, visualTransformation = PasswordVisualTransformation(),
            keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Password), modifier = Modifier.fillMaxWidth())
        err?.let { Spacer(Modifier.height(8.dp)); Text(it, color = ErrRed, fontSize = 13.sp) }
        Spacer(Modifier.height(16.dp))
        GoldButton(if (busy) "CREATING..." else "SIGN UP", enabled = !busy && phone.isNotBlank() && pw.length >= 6) {
            busy = true; err = null
            scope.launch {
                try { Api.signup(phone, pw); onDone() } catch (e: Exception) { err = e.message ?: "Signup failed." }
                busy = false
            }
        }
        Spacer(Modifier.height(10.dp))
        Text("No email needed. Right after signup you get a verification code - send it to the owner on Instagram @ud_speed (manual verification, completely free).",
            color = Muted, fontSize = 12.sp)
        Spacer(Modifier.height(6.dp))
        Text("Already have an account? Login", color = Gold2, fontSize = 13.sp, modifier = Modifier.clickable { onGoLogin() })
    }
}

@Composable
fun CompleteProfileScreen(onDone: () -> Unit) {
    var username by remember { mutableStateOf("") }
    var ign by remember { mutableStateOf("") }
    var ffUid by remember { mutableStateOf("") }
    var busy by remember { mutableStateOf(false) }
    var err by remember { mutableStateOf<String?>(null) }
    val scope = rememberCoroutineScope()
    val userOk = Regex("^[A-Za-z0-9_.]{3,16}$").matches(username)
    val uidOk = ffUid.isBlank() || Regex("^[0-9]{5,15}$").matches(ffUid)
    AuthCard("Complete your profile") {
        Text("One-time setup - this is your XARENA identity.", color = Muted, fontSize = 13.sp)
        Spacer(Modifier.height(10.dp))
        OutlinedTextField(username, { username = it }, label = { Text("Username (3-16, e.g. night_hunter)") }, singleLine = true, modifier = Modifier.fillMaxWidth())
        Spacer(Modifier.height(10.dp))
        OutlinedTextField(ign, { ign = it }, label = { Text("In-game name (IGN)") }, singleLine = true, modifier = Modifier.fillMaxWidth())
        Spacer(Modifier.height(10.dp))
        OutlinedTextField(ffUid, { ffUid = it }, label = { Text("Free Fire UID") }, singleLine = true,
            keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Number), modifier = Modifier.fillMaxWidth())
        Text("Your UID locks after verification.", color = Muted, fontSize = 12.sp)
        err?.let { Spacer(Modifier.height(8.dp)); Text(it, color = ErrRed, fontSize = 13.sp) }
        Spacer(Modifier.height(16.dp))
        GoldButton(if (busy) "SAVING..." else "SAVE PROFILE", enabled = !busy && userOk && uidOk) {
            busy = true; err = null
            scope.launch {
                try { Api.completeProfile(username, ign, ffUid); onDone() } catch (e: Exception) { err = e.message ?: "Could not save." }
                busy = false
            }
        }
    }
}

@Composable
fun HomeShell(onLogout: () -> Unit) {
    var tab by remember { mutableIntStateOf(0) }
    val tabs = listOf("Tournaments", "Board", "Chat", "Luna", "Squads", "Store", "Profile")
    Column(Modifier.fillMaxSize()) {
        Box(Modifier.weight(1f)) {
            when (tab) { 0 -> TournamentsScreen(); 1 -> LeaderboardScreen(); 2 -> ChatScreen(); 3 -> LunaScreen(); 4 -> SquadsScreen(); 5 -> StoreScreen(); else -> ProfileScreen(onLogout) }
        }
        Row(Modifier.fillMaxWidth().background(Color(0xFF1A0E33)).horizontalScroll(androidx.compose.foundation.rememberScrollState()).padding(vertical = 6.dp)) {
            tabs.forEachIndexed { i, label ->
                Column(Modifier.width(92.dp).clickable { tab = i }.padding(8.dp), horizontalAlignment = Alignment.CenterHorizontally) {
                    Box(Modifier.size(6.dp).clip(CircleShape).background(if (tab == i) Gold else Color.Transparent))
                    Text(label, color = if (tab == i) Gold else Muted, fontSize = 11.sp, maxLines = 1, fontWeight = if (tab == i) FontWeight.Bold else FontWeight.Medium)
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
            Text(s.exceptionOrNull()?.message ?: "Could not load.", color = ErrRed); Spacer(Modifier.height(12.dp))
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
    var showRegister by remember { mutableStateOf(false) }
    if (showRegister) { RegisterScreen(onBack = { showRegister = false }); return }
    Column(Modifier.fillMaxSize()) {
        Brand()
        Box(Modifier.padding(horizontal = 16.dp, vertical = 2.dp)) { GoldButton("REGISTER YOUR SQUAD - SEASON 1") { showRegister = true } }
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
    val clipboard = LocalClipboardManager.current
    var copied by remember { mutableStateOf(false) }
    Column(Modifier.fillMaxSize()) {
        Brand()
        LoadState({ Api.myProfile() to Api.myStats() }) { (p, s) ->
            LazyColumn {
                item {
                    PanelCard {
                        Text(p?.optString("ign").takeUnless { it.isNullOrBlank() || it == "null" } ?: p?.optString("username") ?: "Player",
                            color = Gold2, fontSize = 22.sp, fontWeight = FontWeight.Bold)
                        if (p != null) {
                            @Composable fun row(k: String, v: String?) { if (!v.isNullOrBlank() && v != "null") Text("$k: $v", color = Color.White, fontSize = 14.sp) }
                            row("Username", p.optString("username"))
                            row("Free Fire UID", p.optString("ff_uid"))
                            row("Role", p.optString("role")); row("Rank", p.optString("ff_rank"))
                            row("Verified", if (p.optBoolean("verified")) "Yes" else "Not yet")
                            row("Matches", p.optString("matches")); row("Wins", p.optString("wins"))
                        } else Text("Profile not found.", color = Muted)
                    }
                }
                if (p != null && p.optString("phone").isNotBlank() && !p.optBoolean("phone_verified")) {
                    item {
                        PanelCard {
                            Text("PHONE VERIFICATION PENDING", color = Gold2, fontWeight = FontWeight.Bold, fontSize = 13.sp)
                            Spacer(Modifier.height(6.dp))
                            Text("Send this code to the owner on Instagram @ud_speed. Once confirmed, squad registration opens for you.",
                                color = Muted, fontSize = 13.sp)
                            Spacer(Modifier.height(8.dp))
                            Box(Modifier.fillMaxWidth().clip(RoundedCornerShape(8.dp)).background(Color(0x55000000))
                                .clickable { clipboard.setText(AnnotatedString(p.optString("phone_code"))); copied = true }.padding(12.dp),
                                contentAlignment = Alignment.Center) {
                                Text(p.optString("phone_code", "------"), color = Color.White, fontSize = 26.sp,
                                    fontWeight = FontWeight.ExtraBold, letterSpacing = 6.sp, textAlign = TextAlign.Center)
                            }
                            Text(if (copied) "Copied" else "Tap the code to copy it", color = Muted, fontSize = 12.sp,
                                modifier = Modifier.fillMaxWidth(), textAlign = TextAlign.Center)
                        }
                    }
                }
                if (s != null) item {
                    PanelCard {
                        Text("Your game stats", color = Gold, fontWeight = FontWeight.Bold)
                        s.keys().forEach { k -> val v = s.opt(k); if (v is Number || v is String) Text("$k: $v", color = Color.White, fontSize = 13.sp) }
                    }
                }
                item {
                    Spacer(Modifier.height(12.dp))
                    Box(Modifier.padding(horizontal = 16.dp)) { GoldButton("LOG OUT", onClick = onLogout) }
                    Spacer(Modifier.height(24.dp))
                }
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
