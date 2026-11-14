package app.xarena.nativeapp

import androidx.compose.foundation.background
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.material3.Text
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import kotlinx.coroutines.launch
import org.json.JSONArray
import org.json.JSONObject

/* Rewards (v82): one honest home for everything a player earns for FREE - Arena Points balance
   (points_balance), daily check-in streak (my_game_stats + daily_checkin) and season progress
   (season_pass). XARENA never takes player money: there are no purchase, top-up or withdrawal
   paths here by design. Entries jump to the existing Play / Season Pass / Store screens. */

internal object RwSeams {
    var balance: Int? = null
    var stats: JSONObject? = null
    var season: JSONObject? = null
    var spendable: Int? = null
    var loaded = false
    fun clear() { balance = null; stats = null; season = null; spendable = null; loaded = false }
}

@Composable
fun RewardsScreen(onBack: () -> Unit, onOpenPlay: () -> Unit, onOpenSeason: () -> Unit, onOpenStore: () -> Unit) {
    val scope = rememberCoroutineScope()
    var balance by remember { mutableStateOf(RwSeams.balance) }
    var stats by remember { mutableStateOf(RwSeams.stats) }
    var season by remember { mutableStateOf(RwSeams.season) }
    var spendable by remember { mutableStateOf(RwSeams.spendable) }
    var loaded by remember { mutableStateOf(RwSeams.loaded) }
    var tick by remember { mutableIntStateOf(0) }
    var busy by remember { mutableStateOf(false) }
    var msg by remember { mutableStateOf<String?>(null) }

    LaunchedEffect(tick) {
        balance = runCatching { Api.authedRpc("points_balance", JSONObject()) }.getOrNull()
            ?.takeIf { it.optBoolean("ok") }?.optInt("balance")
        stats = runCatching { Api.authedRpc("my_game_stats", JSONObject()) }.getOrNull()
        season = runCatching { Api.publicRpc("season_pass", JSONObject()) }.getOrNull()
            ?.takeIf { it.optBoolean("ok") }
        val st = spendStatus()
        spendable = if (spendEnabled(st, "gifts_enabled") || spendEnabled(st, "clan_fund_enabled"))
            runCatching { Api.authedRpc("xr_wallet_balance", JSONObject()) }.getOrNull()
                ?.takeIf { it.has("earned_spendable") }?.optInt("earned_spendable")
            else null
        loaded = true
    }

    LazyColumn(Modifier.fillMaxSize().background(Ink), contentPadding = PaddingValues(16.dp), verticalArrangement = Arrangement.spacedBy(10.dp)) {
        item { PageHeader("Rewards", "Everything here is earned by playing - never bought", onBack) }

        item {
            PanelCard {
                Text("ARENA POINTS", color = Gold, fontSize = 13.sp, fontWeight = FontWeight.Bold)
                Spacer(Modifier.height(4.dp))
                if (!loaded) Text("Loading...", color = Muted, fontSize = 12.sp)
                else if (balance != null) Text("%,d AP".format(balance), color = Gold2, fontSize = 28.sp, fontWeight = FontWeight.Black)
                if (spendable != null) Text("%,d AP available to spend on live gifts and clan rewards".format(spendable), color = Muted, fontSize = 12.sp, modifier = Modifier.padding(top = 2.dp))
                else Text("Log in to see your balance.", color = Muted, fontSize = 12.sp)
                Text("Earned from matches, check-ins and games. Spend them in the Store on real rewards.", color = Muted, fontSize = 12.sp)
                Spacer(Modifier.height(6.dp))
                GoldButton("OPEN STORE") { onOpenStore() }
            }
        }

        item {
            PanelCard {
                Text("DAILY CHECK-IN", color = Gold, fontSize = 13.sp, fontWeight = FontWeight.Bold)
                Spacer(Modifier.height(4.dp))
                val s = stats
                if (s == null || !s.optBoolean("ok")) Text("Log in to check in daily and earn XP.", color = Muted, fontSize = 12.sp)
                else {
                    Text(s.optInt("streak").toString() + "-day streak", color = Gold2, fontSize = 20.sp, fontWeight = FontWeight.Bold)
                    Text(if (s.optBoolean("checked_in_today")) "Today claimed - come back tomorrow." else "Today is still open - claim it.", color = Muted, fontSize = 12.sp)
                    if (!s.optBoolean("checked_in_today")) {
                        Spacer(Modifier.height(6.dp))
                        GoldButton(if (busy) "CLAIMING..." else "CLAIM TODAY") {
                            if (busy) return@GoldButton
                            busy = true
                            scope.launch {
                                val cr = runCatching { Api.authedRpc("daily_checkin", JSONObject()) }.getOrNull()
                                msg = if (cr != null && cr.optBoolean("ok")) null else cr?.s("error")?.ifBlank { null } ?: "Could not check in - try again."
                                busy = false
                                tick++
                            }
                        }
                    }
                }
                msg?.let { Text(it, color = ErrRed, fontSize = 12.sp, modifier = Modifier.padding(top = 4.dp)) }
            }
        }

        item {
            PanelCard {
                Text("SEASON PASS - FREE TRACK", color = Gold, fontSize = 13.sp, fontWeight = FontWeight.Bold)
                Spacer(Modifier.height(4.dp))
                val sp = season
                if (sp == null) Text("Season progress is not available right now.", color = Muted, fontSize = 12.sp)
                else {
                    val tiers = sp.optJSONArray("tiers") ?: JSONArray()
                    var claimable = 0
                    for (i in 0 until tiers.length()) { val t = tiers.getJSONObject(i); if (t.optBoolean("unlocked") && !t.optBoolean("claimed")) claimable++ }
                    Text("Season Level " + sp.optInt("level"), color = Gold2, fontSize = 20.sp, fontWeight = FontWeight.Bold)
                    Text("%,d EXP".format(sp.optInt("xp")) + if (claimable > 0) " - $claimable reward" + (if (claimable == 1) "" else "s") + " ready to claim" else "", color = Muted, fontSize = 12.sp)
                }
                Spacer(Modifier.height(6.dp))
                GoldButton("OPEN SEASON PASS") { onOpenSeason() }
            }
        }

        item {
            PanelCard {
                Text("PLAY & EARN", color = Gold, fontSize = 13.sp, fontWeight = FontWeight.Bold)
                Spacer(Modifier.height(4.dp))
                Text("Daily spin and quizzes pay real XP - free, every day.", color = Muted, fontSize = 12.sp)
                Spacer(Modifier.height(6.dp))
                GoldButton("OPEN PLAY") { onOpenPlay() }
            }
        }
    }
}
