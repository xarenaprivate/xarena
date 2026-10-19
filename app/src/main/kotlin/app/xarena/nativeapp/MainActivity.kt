package app.xarena.nativeapp

import android.os.Bundle
import androidx.activity.ComponentActivity
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.compose.BackHandler
import androidx.activity.compose.setContent
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.animation.core.*
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.horizontalScroll
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.grid.GridCells
import androidx.compose.foundation.lazy.grid.LazyVerticalGrid
import androidx.compose.foundation.lazy.grid.items
import androidx.compose.ui.unit.offset
import androidx.compose.foundation.layout.offset
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.AccountBox
import androidx.compose.material.icons.filled.CheckCircle
import androidx.compose.material.icons.filled.Create
import androidx.compose.material.icons.filled.DateRange
import androidx.compose.material.icons.filled.Done
import androidx.compose.material.icons.filled.AccountCircle
import androidx.compose.material.icons.filled.Email
import androidx.compose.material.icons.filled.Edit
import androidx.compose.material.icons.filled.FavoriteBorder
import androidx.compose.material.icons.filled.Lock
import androidx.compose.material.icons.filled.Face
import androidx.compose.material.icons.filled.Favorite
import androidx.compose.material.icons.filled.Home
import androidx.compose.material.icons.filled.Info
import androidx.compose.material.icons.filled.List
import androidx.compose.material.icons.filled.MailOutline
import androidx.compose.material.icons.filled.Menu
import androidx.compose.material.icons.filled.Notifications
import androidx.compose.material.icons.filled.Person
import androidx.compose.material.icons.filled.Place
import androidx.compose.material.icons.filled.PlayArrow
import androidx.compose.material.icons.filled.Refresh
import androidx.compose.material.icons.filled.Search
import androidx.compose.material.icons.filled.Settings
import androidx.compose.material.icons.filled.Share
import androidx.compose.material.icons.filled.ShoppingCart
import androidx.compose.material.icons.filled.Star
import androidx.compose.material.icons.filled.ThumbUp
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

/* Look: Liquid (default, iOS-like glass) or Nova (classic dark purple/gold). Never named "iphone" in UI. */
object Look {
    private const val PREF = "xr_look"
    var liquid: Boolean = true
        private set
    fun init(c: android.content.Context) { liquid = c.getSharedPreferences(PREF, 0).getBoolean("liquid", true) }
    fun set(c: android.content.Context, v: Boolean) { liquid = v; c.getSharedPreferences(PREF, 0).edit().putBoolean("liquid", v).apply() }
    val accent: Color get() = if (liquid) Color(0xFF0A84FF) else Gold
    val cardBg: Color get() = if (liquid) Card.copy(alpha = .5f) else Card.copy(alpha = .85f)
    val cardBorder: Color get() = if (liquid) Color.White.copy(alpha = .16f) else Gold.copy(alpha = .3f)
    val cardRadius get() = if (liquid) 22.dp else 16.dp
}

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
    androidx.compose.runtime.LaunchedEffect(Unit) { Look.init(Api.ctx) }
    var screen by remember { mutableStateOf(if (Api.loggedIn) Screen.Home else Screen.Login) }
    var setupChecked by remember { mutableStateOf(false) }
    var update by remember { mutableStateOf<UpdateChecker.Info?>(null) }
    var updateDismissed by remember { mutableStateOf(false) }
    val notifPerm = rememberLauncherForActivityResult(ActivityResultContracts.RequestPermission()) { }
    LaunchedEffect(screen) {
        if (screen == Screen.Home && Api.loggedIn) {
            runCatching {
                Class.forName("app.xarena.nativeapp.PushRegistrar")
                    .getMethod("sync", android.content.Context::class.java)
                    .invoke(null, Api.ctx)
            }
            if (android.os.Build.VERSION.SDK_INT >= 33 && !Api.prefs.getBoolean("notif_asked", false)) {
                Api.prefs.edit().putBoolean("notif_asked", true).apply()
                notifPerm.launch(android.Manifest.permission.POST_NOTIFICATIONS)
            }
        }
    }
    LaunchedEffect(Unit) {
        val latest = UpdateChecker.latest()
        if (latest != null && latest.code > BuildConfig.VERSION_CODE && latest.url.startsWith("https://github.com/xarenaprivate/xarena/releases/")) update = latest
    }
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
        val u = update
        if (u != null && !updateDismissed) UpdateDialog(u) { updateDismissed = true }
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
        Text(text, color = Color(0xFF2A1400), fontWeight = FontWeight.Bold, fontSize = 15.sp, textAlign = androidx.compose.ui.text.style.TextAlign.Center, modifier = Modifier.padding(horizontal = 12.dp))
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

private data class DmConv(val threadId: String, val peerId: String, val peerName: String, val status: String, val reqByMe: Boolean)

@Composable
fun HomeShell(onLogout: () -> Unit) {
    var tab by remember { mutableIntStateOf(0) }
    var feature by remember { mutableStateOf<String?>(null) }
    var conv by remember { mutableStateOf<DmConv?>(null) }
    val tabs = listOf(
        "Home" to Icons.Default.Home, "Live" to Icons.Default.PlayArrow, "Tournaments" to Icons.Default.Star, "Board" to Icons.Default.List,
        "Chat" to Icons.Default.MailOutline, "Luna" to Icons.Default.Face, "Squads" to Icons.Default.AccountBox,
        "Store" to Icons.Default.ShoppingCart, "Profile" to Icons.Default.Person, "More" to Icons.Default.Menu)
    val c = conv
    if (c != null) { BackHandler { conv = null }; ConversationScreen(c.threadId, c.peerId, c.peerName, c.status, c.reqByMe) { conv = null }; return }
    if (feature != null) BackHandler { feature = null }
    when (feature) {
        "messages" -> { MessagesScreen(onBack = { feature = null }, onOpenConv = { t, p, n, st, r -> conv = DmConv(t, p, n, st, r) }); return }
        "match" -> { MatchCenterScreen(onBack = { feature = null }); return }
        "settings" -> { SettingsScreen(onBack = { feature = null }, onLogout = onLogout); return }
        "playercard" -> { PlayerCardScreen(onBack = { feature = null }, onOpenConv = { t, p, n -> if (t != null) conv = DmConv(t, p, n, "open", false) else feature = "messages" }); return }
        "vote" -> { FanVoteScreen(onBack = { feature = null }); return }
        "streak" -> { StreakScreen(onBack = { feature = null }); return }
        "certs" -> { CertsScreen(onBack = { feature = null }); return }
        "academy" -> { AcademyScreen(onBack = { feature = null }); return }
        "news" -> { NewsScreen(onBack = { feature = null }); return }
        "rules" -> { RulesScreen(onBack = { feature = null }); return }
        "notifications" -> { NotificationsScreen(onBack = { feature = null }); return }
        "stories" -> { StoriesScreen(onBack = { feature = null }); return }
        "feed" -> { FeedScreen(onBack = { feature = null }); return }
        "clips" -> { ClipsScreen(onBack = { feature = null }); return }
        "winners" -> { WinnersScreen(onBack = { feature = null }); return }
        "stats" -> { StatsScreen(onBack = { feature = null }); return }
        "tasks" -> { TasksScreen(onBack = { feature = null }); return }
        "live" -> { LiveScreen(onBack = { feature = null }); return }
        "scrims" -> { ScrimsScreen(onBack = { feature = null }); return }
        "calendar" -> { CalendarScreen(onBack = { feature = null }); return }
        "ping" -> { PingScreen(onBack = { feature = null }); return }
        "refer" -> { ReferScreen(onBack = { feature = null }); return }
        "verify" -> { VerifyScreen(onBack = { feature = null }); return }
        "roles" -> { RolesScreen(onBack = { feature = null }); return }
        "winner" -> { WinnerScreen(onBack = { feature = null }); return }
        "sponsors" -> { SponsorsScreen(onBack = { feature = null }); return }
        "register" -> { RegisterScreen(onBack = { feature = null }); return }
        "room" -> { RoomScreen(onBack = { feature = null }); return }
        "support" -> { SupportScreen(onBack = { feature = null }); return }
        "sensitivity" -> { SensitivityScreen(onBack = { feature = null }); return }
        "coaches" -> { CoachesScreen(onBack = { feature = null }); return }
        "cup" -> { CupScreen(onBack = { feature = null }); return }
        "search" -> { SearchScreen(onBack = { feature = null }); return }
        "teams" -> { SearchScreen(onBack = { feature = null }, initialTab = 0, title = "Teams & Roster"); return }
        "players" -> { SearchScreen(onBack = { feature = null }, initialTab = 1, title = "Players"); return }
        "howto" -> { HowToPlayScreen(onBack = { feature = null }); return }
        "voice" -> { VoiceScreen(onBack = { feature = null }); return }
    }
    var msgBadge by remember { mutableIntStateOf(0) }
    LaunchedEffect(feature, conv) {
        msgBadge = runCatching {
            Api.dmRequestsIncoming().length() + Api.dmMessageRequests().length()
        }.getOrDefault(0)
    }
    Column(Modifier.fillMaxSize()) {
        Row(Modifier.fillMaxWidth().background(Color(0xFF1A0E33)).padding(horizontal = 14.dp, vertical = 8.dp), verticalAlignment = Alignment.CenterVertically) {
            Text("XARENA", color = Gold2, fontSize = 16.sp, fontWeight = FontWeight.Bold, modifier = Modifier.weight(1f))
            Box(Modifier.clip(RoundedCornerShape(8.dp)).clickable { feature = "messages" }.padding(8.dp)) {
                Icon(Icons.Default.Email, contentDescription = "Messages", tint = Gold, modifier = Modifier.size(20.dp))
                if (msgBadge > 0) Text(msgBadge.toString(), color = Color.White, fontSize = 9.sp, fontWeight = FontWeight.Bold,
                    modifier = Modifier.align(Alignment.TopEnd).offset(x = 8.dp, y = (-4).dp).clip(CircleShape).background(Color(0xFFE10600)).padding(horizontal = 4.dp, vertical = 1.dp))
            }
            Icon(Icons.Default.Settings, contentDescription = "Settings", tint = Gold, modifier = Modifier.size(20.dp).clip(RoundedCornerShape(8.dp)).clickable { feature = "settings" }.padding(2.dp))
        }
        BackHandler(enabled = tab != 0) { tab = 0 }
        Box(Modifier.weight(1f)) {
            when (tab) { 0 -> HomeScreen(onGoTab = { tab = it }, onOpenFeature = { feature = it }); 1 -> LiveScreen(onBack = { tab = 0 }); 2 -> TournamentsScreen(); 3 -> LeaderboardScreen(); 4 -> ChatScreen(onGoTab = { tab = it }); 5 -> LunaScreen(); 6 -> SquadsScreen(); 7 -> StoreScreen(); 8 -> ProfileScreen(onLogout); else -> MoreGrid { feature = it } }
        }
        Row(Modifier.fillMaxWidth().background(Color(0xFF1A0E33)).horizontalScroll(androidx.compose.foundation.rememberScrollState()).padding(vertical = 6.dp)) {
            tabs.forEachIndexed { i, (label, icon) ->
                Column(Modifier.width(78.dp).clickable { tab = i }.padding(vertical = 6.dp, horizontal = 4.dp), horizontalAlignment = Alignment.CenterHorizontally) {
                    Icon(icon, contentDescription = label, tint = if (tab == i) Gold else Muted, modifier = Modifier.size(20.dp))
                    Spacer(Modifier.height(2.dp))
                    Text(label, color = if (tab == i) Gold else Muted, fontSize = 11.sp, maxLines = 1, fontWeight = if (tab == i) FontWeight.Bold else FontWeight.Medium)
                }
            }
        }
    }
}

data class GridItem(val key: String, val name: String, val sub: String, val icon: androidx.compose.ui.graphics.vector.ImageVector)

@Composable
fun MoreGrid(onOpen: (String) -> Unit) {
    // One button per feature, each opening its own full screen. Only real, working features are listed.
    val items = listOf(
        GridItem("messages", "Messages", "Private end-to-end encrypted chats", Icons.Default.Email),
        GridItem("match", "Match Center", "Rooms, check-in, result proofs, reports", Icons.Default.Place),
        GridItem("playercard", "Player Card", "Your card, followers, following", Icons.Default.Person),
        GridItem("vote", "Fan Vote", "Vote in the open poll", Icons.Default.ThumbUp),
        GridItem("streak", "Daily Streak", "Check in daily, earn badges", Icons.Default.Favorite),
        GridItem("certs", "Certificates", "Your real tournament certificates", Icons.Default.CheckCircle),
        GridItem("academy", "Academy", "Free Fire training drills", Icons.Default.Create),
        GridItem("news", "News", "Updates and announcements", Icons.Default.Info),
        GridItem("rules", "Rules", "Tournament and squad rules", Icons.Default.Menu),
        GridItem("notifications", "Notifications", "Your alerts", Icons.Default.Notifications),
        GridItem("stories", "Stories", "24-hour highlights from players", Icons.Default.Face),
        GridItem("clips", "Clips", "Approved community clips", Icons.Default.PlayArrow),
        GridItem("feed", "Feed", "Player highlights - post, like, comment, save", Icons.Default.FavoriteBorder),
        GridItem("live", "Live", "Streams and match schedule", Icons.Default.Share),
        GridItem("winners", "Winners", "Hall of fame and awards", Icons.Default.Star),
        GridItem("stats", "Stats", "Your numbers and platform numbers", Icons.Default.List),
        GridItem("tasks", "Tasks", "Finish tasks, earn points", Icons.Default.Done),
        GridItem("scrims", "Scrims", "Looking-for-game board", Icons.Default.Search),
        GridItem("calendar", "Calendar", "Tournament and event dates", Icons.Default.DateRange),
        GridItem("ping", "Ping Check", "Real connection and device test", Icons.Default.Refresh),
        GridItem("refer", "Refer & Earn", "Invite friends, earn Store Points", Icons.Default.Share),
        GridItem("verify", "Verification", "Anti-cheat photo verification badge", Icons.Default.Lock),
        GridItem("roles", "Team Roles", "Your role and squad formation", Icons.Default.Edit),
        GridItem("winner", "Champions", "Winner celebration - real finals", Icons.Default.FavoriteBorder),
        GridItem("sponsors", "Sponsors", "Real signed partners", Icons.Default.AccountCircle),
        GridItem("room", "Clan Room", "Your squad's private room", Icons.Default.Home),
        GridItem("support", "Support", "Ask the owner directly", Icons.Default.Email),
        GridItem("sensitivity", "Sensitivity", "Share your aim setup", Icons.Default.Settings),
        GridItem("coaches", "Coaches", "Learn from verified players", Icons.Default.Star),
        GridItem("cup", "Sponsor a Cup", "Your brand on a real tournament", Icons.Default.ShoppingCart),
        GridItem("search", "Search", "Squads, players, tournaments", Icons.Default.Search),
        GridItem("teams", "Teams", "Registered squads", Icons.Default.AccountBox),
        GridItem("players", "Players", "Verified player profiles", Icons.Default.Person),
        GridItem("howto", "How to Play", "3 steps to your first match", Icons.Default.Info),
        GridItem("voice", "Voice Lobby", "Talk live - peer to peer", Icons.Default.Notifications),
        GridItem("settings", "Settings", "App version, activity status, logout", Icons.Default.Settings),
    )
    Column(Modifier.fillMaxSize()) {
        Brand()
        LazyVerticalGrid(columns = GridCells.Fixed(2), modifier = Modifier.fillMaxSize().padding(horizontal = 14.dp)) {
            items(items) { it ->
                Column(Modifier.padding(6.dp).clip(RoundedCornerShape(Look.cardRadius)).background(Look.cardBg)
                    .border(1.dp, Look.cardBorder, RoundedCornerShape(Look.cardRadius)).clickable { onOpen(it.key) }.padding(16.dp)) {
                    Icon(it.icon, contentDescription = it.name, tint = Look.accent, modifier = Modifier.size(24.dp))
                    Spacer(Modifier.height(8.dp))
                    Text(it.name, color = Gold2, fontSize = 16.sp, fontWeight = FontWeight.Bold)
                    Spacer(Modifier.height(4.dp))
                    Text(it.sub, color = Muted, fontSize = 12.sp)
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
    Column(Modifier.fillMaxWidth().padding(horizontal = 16.dp, vertical = 6.dp).clip(RoundedCornerShape(Look.cardRadius))
        .background(Look.cardBg).border(1.dp, Look.cardBorder, RoundedCornerShape(Look.cardRadius)).padding(14.dp), content = content)
}

@Composable
fun TournamentsScreen() {
    var showRegister by remember { mutableStateOf(false) }
    var showMatches by remember { mutableStateOf(false) }
    if (showRegister) { androidx.activity.compose.BackHandler { showRegister = false }; RegisterScreen(onBack = { showRegister = false }); return }
    if (showMatches) { androidx.activity.compose.BackHandler { showMatches = false }; MatchCenterScreen(onBack = { showMatches = false }); return }
    Column(Modifier.fillMaxSize()) {
        Brand()
        Box(Modifier.padding(horizontal = 16.dp, vertical = 2.dp)) { GoldButton("REGISTER YOUR SQUAD - SEASON 1") { showRegister = true } }
        Box(Modifier.padding(horizontal = 16.dp, vertical = 2.dp)) { GoldButton("MATCH CENTER - ROOMS, CHECK-IN, RESULTS") { showMatches = true } }
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
    var tick by remember { mutableIntStateOf(0) }
    var query by remember { mutableStateOf("") }
    LoadState2(tick, {
        val rows = Api.publicRpc("leaderboard_nova", JSONObject()).optJSONArray("rows") ?: JSONArray()
        val mine = runCatching { Api.authedRpc("my_rank_nova", JSONObject()) }.getOrNull()
        rows to mine
    }) { (rows, mine) ->
        Column(Modifier.fillMaxSize()) {
            Brand()
            OutlinedTextField(query, { query = it }, placeholder = { Text("Search a player by name") },
                singleLine = true, modifier = Modifier.fillMaxWidth().padding(horizontal = 16.dp, vertical = 4.dp))
            LazyColumn(Modifier.weight(1f)) {
                val all = (0 until rows.length()).map { rows.getJSONObject(it) }
                val shown = all.filter { query.isBlank() || it.optString("name").contains(query.trim(), true) }
                if (shown.isEmpty()) item { EmptyNote(if (all.isEmpty()) "No XP earned yet" else "No player matches " + query.trim(),
                    if (all.isEmpty()) "The board lights up when players earn XP." else "Try a different name.") }
                items(shown.size) { i ->
                    val r = shown[i]; val rank = all.indexOf(r) + 1
                    val medal = when (rank) { 1 -> Color(0xFFFFD700); 2 -> Color(0xFFC0C0C0); 3 -> Color(0xFFCD7F32); else -> Gold }
                    PanelCard {
                        Row(verticalAlignment = Alignment.CenterVertically) {
                            Text("#" + rank, color = medal, fontSize = if (rank <= 3) 22.sp else 18.sp, fontWeight = FontWeight.Black,
                                modifier = Modifier.width(48.dp))
                            Box(Modifier.size(38.dp).clip(CircleShape).background(Look.cardBorder),
                                contentAlignment = Alignment.Center) {
                                Text(r.optString("name").firstOrNull()?.uppercase() ?: "P", color = Gold2, fontWeight = FontWeight.Bold)
                            }
                            Spacer(Modifier.width(10.dp))
                            Column(Modifier.weight(1f)) {
                                Text(r.optString("name"), color = Color.White, fontSize = 16.sp, fontWeight = FontWeight.Bold)
                                val moh = r.optString("mohalla").takeIf { it.isNotBlank() && it != "null" }
                                if (moh != null) Text(moh, color = Muted, fontSize = 12.sp)
                            }
                            Column(horizontalAlignment = Alignment.End) {
                                Text(r.optInt("xp").toString() + " XP", color = Gold2, fontWeight = FontWeight.Bold)
                                Text(tierOf(r.optInt("xp")), color = tierColor(tierOf(r.optInt("xp"))), fontSize = 11.sp, fontWeight = FontWeight.Bold)
                            }
                        }
                    }
                }
            }
            if (mine != null && mine.optBoolean("ok")) {
                Row(Modifier.fillMaxWidth().padding(horizontal = 16.dp, vertical = 8.dp).clip(RoundedCornerShape(Look.cardRadius))
                    .background(Look.cardBg).border(1.dp, Gold, RoundedCornerShape(Look.cardRadius)).padding(14.dp),
                    verticalAlignment = Alignment.CenterVertically) {
                    Column(Modifier.weight(1f)) {
                        Text("YOUR RANK", color = Muted, fontSize = 10.sp, fontWeight = FontWeight.Bold)
                        Text("You (" + mine.optString("name") + ")", color = Color.White, fontSize = 15.sp, fontWeight = FontWeight.Bold)
                        Text(tierOf(mine.optInt("xp")), color = tierColor(tierOf(mine.optInt("xp"))), fontSize = 11.sp, fontWeight = FontWeight.Bold)
                    }
                    Text("#" + mine.optInt("rank"), color = Gold, fontSize = 24.sp, fontWeight = FontWeight.Black)
                    Spacer(Modifier.width(14.dp))
                    Column(horizontalAlignment = Alignment.End) {
                        val wx = mine.optInt("week_xp", 0)
                        Text((if (wx > 0) "+" + wx else mine.optInt("xp")).toString() + " XP", color = Gold2, fontSize = 16.sp, fontWeight = FontWeight.Bold)
                        Text(if (wx > 0) "this week" else "earned", color = Muted, fontSize = 10.sp)
                    }
                }
            }
        }
    }
}

fun tierOf(xp: Int) = if (xp >= 5000) "Grandmaster" else if (xp >= 2500) "Heroic" else if (xp >= 1200) "Diamond" else if (xp >= 600) "Platinum" else if (xp >= 250) "Gold" else if (xp >= 80) "Silver" else "Bronze"
fun tierColor(t: String) = when (t) {
    "Bronze" -> Color(0xFFCD7F32); "Silver" -> Color(0xFFC0C0C0); "Gold" -> Color(0xFFFFD700)
    "Platinum" -> Color(0xFF7DD3FC); "Diamond" -> Color(0xFF60A5FA); "Heroic" -> Color(0xFFC084FC)
    else -> Color(0xFFF0ABFC)
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
