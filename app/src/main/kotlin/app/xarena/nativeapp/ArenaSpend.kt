package app.xarena.nativeapp

import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.text.BasicTextField
import androidx.compose.material3.Text
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.TextStyle
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import kotlinx.coroutines.delay
import kotlinx.coroutines.launch
import org.json.JSONArray
import org.json.JSONObject

/* Wallet spend (v0.17.53): live gifts to the host, match prediction, live drop on the Live tab,
   plus the clan fund on the Squads screen. Every card is gated ONLY on the new spend settings
   (xr_spend_live_get.settings.*_enabled / xr_spend_status) and release_authorized - never on the
   legacy growth flags. Nothing renders while flags are off or the RPCs are unavailable.
   AP are earned in-app points: no purchase, no top-up, no cash-out, no redemption wording.
   Idempotency: one stable request UUID per attempted gift/contribution/reward, reused on retry. */

private object SpendSeams {
    var status: JSONObject? = null
    var live: JSONObject? = null
    var statusAt = 0L
    fun clear() { status = null; live = null; statusAt = 0L }
}

internal suspend fun spendStatus(): JSONObject? {
    val now = System.currentTimeMillis()
    val c = SpendSeams.status
    if (c != null && now - SpendSeams.statusAt < 60000) return c
    val fresh = runCatching { Api.authedRpc("xr_spend_status", JSONObject()) }.getOrNull() ?: return c
    SpendSeams.status = fresh
    SpendSeams.statusAt = now
    return fresh
}

internal fun spendEnabled(settings: JSONObject?, flag: String): Boolean {
    val s = settings ?: return false
    return s.optBoolean("release_authorized") && s.optBoolean(flag)
}

private fun spendFmt(iso: String): String = try {
    val clean = iso.replace("T", " ").take(19)
    val d = java.text.SimpleDateFormat("yyyy-MM-dd HH:mm:ss", java.util.Locale.US).apply { timeZone = java.util.TimeZone.getTimeZone("UTC") }.parse(clean)
    java.text.SimpleDateFormat("d MMM, h:mm a", java.util.Locale.US).format(d ?: java.util.Date())
} catch (e: Exception) { "" }

@Composable
fun SpendLiveSection(videoId: String) {
    var live by remember { mutableStateOf(SpendSeams.live) }
    var tick by remember { mutableIntStateOf(0) }
    LaunchedEffect(videoId, tick) {
        val lv = runCatching { Api.authedRpc("xr_spend_live_get", JSONObject()) }.getOrNull()
        if (lv != null && lv.optBoolean("ok")) { live = lv; SpendSeams.live = lv }
        delay(30000)
        tick++
    }
    val lv = live?.takeIf { it.optBoolean("ok") } ?: return
    val settings = lv.optJSONObject("settings")
    if (!spendEnabled(settings, "gifts_enabled") && !spendEnabled(settings, "predictions_enabled") && !spendEnabled(settings, "drops_enabled")) return
    Column(Modifier.fillMaxWidth().padding(top = 8.dp), verticalArrangement = Arrangement.spacedBy(8.dp)) {
        val giftsOn = spendEnabled(settings, "gifts_enabled") && lv.optBoolean("active")
        if (giftsOn) GiftBar(lv, videoId) { tick++ }
        val gifts = lv.optJSONArray("gifts")
        if (spendEnabled(settings, "gifts_enabled") && gifts != null && gifts.length() > 0) GiftFeed(gifts)
        val pred = lv.optJSONObject("prediction")
        if (spendEnabled(settings, "predictions_enabled") && pred != null) PredictionCard(pred, settings) { tick++ }
        val drop = lv.optJSONObject("drop")
        if (spendEnabled(settings, "drops_enabled") && drop != null) DropCard(drop)
    }
}

@Composable
private fun GiftBar(lv: JSONObject, videoId: String, onChanged: () -> Unit) {
    val scope = rememberCoroutineScope()
    val settings = lv.optJSONObject("settings") ?: JSONObject()
    val catalog = settings.optJSONArray("gift_catalog") ?: JSONArray()
    if (catalog.length() == 0) return
    val host = lv.s("host_name").ifBlank { "the host" }
    val hostAvailable = lv.optBoolean("host_available")
    var pending by remember { mutableStateOf<JSONObject?>(null) }
    var reqId by remember { mutableStateOf<String?>(null) }
    var busy by remember { mutableStateOf(false) }
    var msg by remember { mutableStateOf<String?>(null) }
    var msgOk by remember { mutableStateOf(false) }
    Column(Modifier.fillMaxWidth().clip(RoundedCornerShape(10.dp)).border(1.dp, Gold.copy(alpha = .3f), RoundedCornerShape(10.dp)).padding(10.dp)) {
        Text("SEND A GIFT", color = Gold2, fontSize = 11.sp, fontWeight = FontWeight.Bold)
        if (!hostAvailable) {
            Text("No host is connected to this stream yet - gifts open when the host joins.", color = Muted, fontSize = 12.sp, modifier = Modifier.padding(top = 4.dp))
            return@Column
        }
        if (pending == null) {
            Text("Points go to " + host + " - gifts cannot be taken back.", color = Muted, fontSize = 12.sp, modifier = Modifier.padding(vertical = 4.dp))
            Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                for (i in 0 until catalog.length()) {
                    val g = catalog.getJSONObject(i)
                    Text(g.s("key").replaceFirstChar { it.uppercase() } + " - " + g.optInt("ap") + " AP",
                        color = Ink, fontSize = 12.sp, fontWeight = FontWeight.Bold,
                        modifier = Modifier.clip(RoundedCornerShape(8.dp)).background(Gold)
                            .clickable { pending = g; reqId = java.util.UUID.randomUUID().toString(); msg = null }
                            .padding(horizontal = 12.dp, vertical = 8.dp))
                }
            }
        } else {
            val g = pending!!
            Text("Send " + g.s("key").replaceFirstChar { it.uppercase() } + " to " + host + " for " + g.optInt("ap") + " AP?",
                color = Color.White, fontSize = 13.sp, fontWeight = FontWeight.SemiBold, modifier = Modifier.padding(vertical = 4.dp))
            Row(horizontalArrangement = Arrangement.spacedBy(10.dp), verticalAlignment = Alignment.CenterVertically) {
                Text(if (busy) "SENDING..." else "CONFIRM GIFT", color = Ink, fontSize = 12.sp, fontWeight = FontWeight.Bold,
                    modifier = Modifier.clip(RoundedCornerShape(8.dp)).background(if (busy) Muted else Gold)
                        .clickable(enabled = !busy) {
                            val rid = reqId ?: return@clickable
                            busy = true
                            scope.launch {
                                val r = runCatching { Api.authedRpc("xr_live_gift_send", JSONObject().put("p_video", videoId).put("p_gift", g.s("key")).put("p_request", rid)) }.getOrNull()
                                when {
                                    r == null -> { msg = "Could not send - check your connection and try again."; msgOk = false }
                                    r.optBoolean("ok") -> {
                                        msg = g.s("key").replaceFirstChar { it.uppercase() } + " sent to " + host + " - " + r.optInt("ap_spent", g.optInt("ap")) + " AP."
                                        msgOk = true
                                        pending = null; reqId = null
                                    }
                                    r.optBoolean("already") -> { msg = "Already sent - no points were taken twice."; msgOk = true; pending = null; reqId = null }
                                    else -> { msg = r.s("error").ifBlank { "Could not send the gift." }; msgOk = false }
                                }
                                busy = false
                                onChanged()
                            }
                        }.padding(horizontal = 12.dp, vertical = 8.dp))
                Text("Cancel", color = Muted, fontSize = 12.sp,
                    modifier = Modifier.clickable(enabled = !busy) { pending = null; reqId = null; msg = null }.padding(8.dp))
            }
        }
        msg?.let { Text(it, color = if (msgOk) Gold2 else ErrRed, fontSize = 12.sp, modifier = Modifier.padding(top = 4.dp)) }
    }
}

@Composable
private fun GiftFeed(gifts: JSONArray) {
    Column(Modifier.fillMaxWidth().clip(RoundedCornerShape(10.dp)).border(1.dp, Gold.copy(alpha = .3f), RoundedCornerShape(10.dp)).padding(10.dp)) {
        Text("RECENT GIFTS", color = Gold2, fontSize = 11.sp, fontWeight = FontWeight.Bold)
        Spacer(Modifier.height(4.dp))
        val now = System.currentTimeMillis()
        for (i in 0 until minOf(gifts.length(), 10)) {
            val g = gifts.getJSONObject(i)
            val hi = runCatching { java.time.Instant.parse(g.s("highlight_until")).toEpochMilli() > now }.getOrDefault(false)
            Row(Modifier.fillMaxWidth().padding(vertical = 3.dp).clip(RoundedCornerShape(8.dp))
                .background(if (hi) Gold.copy(alpha = .22f) else Color(0x10FFFFFF))
                .padding(horizontal = 10.dp, vertical = 6.dp), verticalAlignment = Alignment.CenterVertically) {
                Text(g.s("sender").ifBlank { "A fan" } + " sent " + g.s("gift_key").replaceFirstChar { it.uppercase() },
                    color = if (hi) Gold2 else Color.White, fontSize = 12.sp, modifier = Modifier.weight(1f))
                Text(g.optInt("amount").toString() + " AP", color = Muted, fontSize = 11.sp)
            }
        }
    }
}

@Composable
private fun PredictionCard(pred: JSONObject, settings: JSONObject?, onChanged: () -> Unit) {
    val scope = rememberCoroutineScope()
    val choices = pred.optJSONArray("choices") ?: JSONArray()
    if (choices.length() == 0) return
    val status = pred.s("status")
    val myChoice = pred.s("my_choice").ifBlank { null }
    val winner = pred.s("winner_registration").ifBlank { null }
    val myAward = if (pred.isNull("my_award")) null else pred.optInt("my_award")
    val closesAt = pred.s("closes_at")
    val closed = status.isNotBlank() && status != "open"
    val cost = settings?.optInt("prediction_entry_cost") ?: 0
    val correctAp = settings?.optInt("correct_prediction_ap") ?: 0
    val wrongAp = settings?.optInt("incorrect_prediction_ap") ?: 0
    var confirm by remember { mutableStateOf<JSONObject?>(null) }
    var busy by remember { mutableStateOf(false) }
    var msg by remember { mutableStateOf<String?>(null) }
    Column(Modifier.fillMaxWidth().clip(RoundedCornerShape(10.dp)).border(1.dp, Gold.copy(alpha = .3f), RoundedCornerShape(10.dp)).padding(10.dp)) {
        Text("MATCH PREDICTION", color = Gold2, fontSize = 11.sp, fontWeight = FontWeight.Bold)
        Text((if (cost > 0) "Entry: " + cost + " AP. " else "Free to play. ") + "Pick the winner before it locks" +
            (if (correctAp > 0) " - the right call pays " + correctAp + " AP" else "") +
            (if (wrongAp > 0) ", a wrong one still pays " + wrongAp + " AP" else "") + ".",
            color = Muted, fontSize = 12.sp, modifier = Modifier.padding(vertical = 4.dp))
        if (closesAt.isNotBlank() && !closed) Text("Locks " + spendFmt(closesAt), color = Muted, fontSize = 11.sp)
        if (closed) Text("Locked" + (if (winner != null) "" else " - waiting for the result."), color = Muted, fontSize = 11.sp)
        Spacer(Modifier.height(4.dp))
        for (i in 0 until choices.length()) {
            val c = choices.getJSONObject(i)
            val rid = c.s("registration_id")
            val mine = myChoice != null && myChoice == rid
            val isWinner = winner != null && winner == rid
            Row(Modifier.fillMaxWidth().padding(vertical = 3.dp).clip(RoundedCornerShape(10.dp))
                .background(if (mine) Gold.copy(alpha = .25f) else Color(0x14FFFFFF))
                .border(1.dp, if (mine) Gold else if (isWinner) Color(0xFF22C55E) else Color(0x22FFFFFF), RoundedCornerShape(10.dp))
                .clickable(enabled = !closed && myChoice == null) { confirm = c; msg = null }
                .padding(horizontal = 12.dp, vertical = 9.dp), verticalAlignment = Alignment.CenterVertically) {
                Text(c.s("squad").ifBlank { rid }, color = Color.White, fontSize = 13.sp, modifier = Modifier.weight(1f))
                if (mine) Text("YOUR PICK", color = Gold, fontSize = 10.sp, fontWeight = FontWeight.Bold)
                else if (isWinner) Text("WINNER", color = Color(0xFF22C55E), fontSize = 10.sp, fontWeight = FontWeight.Bold)
            }
        }
        if (myChoice != null) Text("Locked in - you cannot change your pick.", color = Muted, fontSize = 11.sp, modifier = Modifier.padding(top = 4.dp))
        if (myAward != null && myAward > 0) Text("Settled: +" + myAward + " AP to your balance.", color = Gold2, fontSize = 12.sp, fontWeight = FontWeight.Bold, modifier = Modifier.padding(top = 4.dp))
        if (confirm != null && myChoice == null && !closed) {
            val c = confirm!!
            Spacer(Modifier.height(6.dp))
            Text("Lock in " + c.s("squad").ifBlank { c.s("registration_id") } + "? You cannot change it after.", color = Color.White, fontSize = 13.sp)
            Row(Modifier.padding(top = 6.dp), horizontalArrangement = Arrangement.spacedBy(10.dp), verticalAlignment = Alignment.CenterVertically) {
                Text(if (busy) "LOCKING..." else "LOCK MY PICK", color = Ink, fontSize = 12.sp, fontWeight = FontWeight.Bold,
                    modifier = Modifier.clip(RoundedCornerShape(8.dp)).background(if (busy) Muted else Gold)
                        .clickable(enabled = !busy) {
                            busy = true
                            scope.launch {
                                val r = runCatching { Api.authedRpc("xr_prediction_choose", JSONObject().put("p_pool", pred.s("id")).put("p_registration", c.s("registration_id"))) }.getOrNull()
                                msg = when {
                                    r == null -> "Could not lock - check your connection."
                                    r.optBoolean("ok") -> null
                                    else -> r.s("error").ifBlank { "Could not lock your pick." }
                                }
                                busy = false
                                if (r != null && r.optBoolean("ok")) { confirm = null; onChanged() }
                            }
                        }.padding(horizontal = 12.dp, vertical = 8.dp))
                Text("Cancel", color = Muted, fontSize = 12.sp,
                    modifier = Modifier.clickable(enabled = !busy) { confirm = null }.padding(8.dp))
            }
            msg?.let { Text(it, color = ErrRed, fontSize = 12.sp, modifier = Modifier.padding(top = 4.dp)) }
        }
    }
}

@Composable
private fun DropCard(drop: JSONObject) {
    val status = drop.s("status")
    val limit = drop.optInt("winner_limit", 10)
    val award = drop.optInt("award_ap", 50)
    val winners = drop.optJSONArray("winners") ?: JSONArray()
    val myAward = if (drop.isNull("my_award")) null else drop.optInt("my_award")
    Column(Modifier.fillMaxWidth().clip(RoundedCornerShape(10.dp)).border(1.dp, Gold.copy(alpha = .3f), RoundedCornerShape(10.dp)).padding(10.dp)) {
        Text("LIVE DROP", color = Gold2, fontSize = 11.sp, fontWeight = FontWeight.Bold)
        if (status == "open") {
            Text("First " + limit + " unique commenters in the live chat below win " + award + " AP each - awarded automatically, no claim needed.",
                color = Color.White, fontSize = 13.sp, modifier = Modifier.padding(vertical = 4.dp))
            Text(winners.length().toString() + " of " + limit + " claimed", color = Muted, fontSize = 12.sp)
        } else {
            Text("Drop closed" + (if (winners.length() > 0) " - " + winners.length() + " of " + limit + " rewards claimed." else "."),
                color = Muted, fontSize = 12.sp, modifier = Modifier.padding(vertical = 4.dp))
        }
        if (myAward != null && myAward > 0) Text("You won " + myAward + " AP - already in your balance.", color = Gold2, fontSize = 12.sp, fontWeight = FontWeight.Bold, modifier = Modifier.padding(top = 2.dp))
    }
}

@Composable
fun ClanFundCard(squadId: String, myRole: String?, onChanged: () -> Unit) {
    val scope = rememberCoroutineScope()
    var status by remember { mutableStateOf(SpendSeams.status) }
    var fund by remember { mutableStateOf<JSONObject?>(null) }
    var tick by remember { mutableIntStateOf(0) }
    LaunchedEffect(squadId, tick) {
        val st = spendStatus()
        status = st
        if (spendEnabled(st, "clan_fund_enabled")) {
            fund = runCatching { Api.authedRpc("xr_clan_fund_get", JSONObject().put("p_squad", squadId)) }.getOrNull()
        }
    }
    if (!spendEnabled(status, "clan_fund_enabled")) return
    val f = fund?.takeIf { it.optBoolean("ok") } ?: return
    val balance = f.optInt("balance")
    val canReward = f.optBoolean("can_reward") && myRole == "captain"
    val contributors = f.optJSONArray("contributors") ?: JSONArray()
    val mine = f.optJSONArray("my_contributions") ?: JSONArray()
    var amount by remember { mutableStateOf("") }
    var confirmContrib by remember { mutableStateOf(false) }
    var contribReq by remember { mutableStateOf<String?>(null) }
    var rewardFor by remember { mutableStateOf<Pair<String, String>?>(null) }
    var rewardAmt by remember { mutableStateOf("") }
    var confirmReward by remember { mutableStateOf(false) }
    var rewardReq by remember { mutableStateOf<String?>(null) }
    var busy by remember { mutableStateOf(false) }
    var msg by remember { mutableStateOf<String?>(null) }
    var msgOk by remember { mutableStateOf(false) }
    PanelCard {
        Text("CLAN FUND", color = Gold, fontSize = 13.sp, fontWeight = FontWeight.Bold)
        Spacer(Modifier.height(4.dp))
        Text("%,d AP in the fund".format(balance), color = Gold2, fontSize = 22.sp, fontWeight = FontWeight.Black)
        if (contributors.length() > 0) {
            Text("Contributors: " + (0 until contributors.length()).joinToString(", ") { i ->
                val c = contributors.getJSONObject(i); c.s("name").ifBlank { "Member" } + " (" + c.optInt("ap") + ")"
            }, color = Muted, fontSize = 12.sp, modifier = Modifier.padding(top = 2.dp))
        } else Text("No contributions yet.", color = Muted, fontSize = 12.sp, modifier = Modifier.padding(top = 2.dp))
        if (mine.length() > 0) {
            Text("Your contributions: " + (0 until mine.length()).joinToString(", ") { i -> mine.getJSONObject(i).optInt("ap").toString() + " AP" },
                color = Muted, fontSize = 12.sp, modifier = Modifier.padding(top = 2.dp))
        }
        Spacer(Modifier.height(8.dp))
        if (!confirmContrib) {
            Row(verticalAlignment = Alignment.CenterVertically) {
                BasicTextField(value = amount, onValueChange = { amount = it.filter { ch -> ch.isDigit() }.take(6) },
                    textStyle = TextStyle(color = Color.White, fontSize = 13.sp),
                    modifier = Modifier.weight(1f).clip(RoundedCornerShape(8.dp)).border(1.dp, Color(0x22FFFFFF), RoundedCornerShape(8.dp)).padding(horizontal = 10.dp, vertical = 9.dp),
                    decorationBox = { inner -> if (amount.isEmpty()) Text("Amount in AP", color = Muted, fontSize = 13.sp); inner() })
                Spacer(Modifier.width(8.dp))
                Text("CONTRIBUTE", color = Ink, fontSize = 12.sp, fontWeight = FontWeight.Bold,
                    modifier = Modifier.clip(RoundedCornerShape(8.dp)).background(Gold)
                        .clickable {
                            val a = amount.toIntOrNull() ?: 0
                            if (a <= 0) { msg = "Enter an amount first."; msgOk = false; return@clickable }
                            confirmContrib = true; contribReq = java.util.UUID.randomUUID().toString(); msg = null
                        }.padding(horizontal = 12.dp, vertical = 9.dp))
            }
        } else {
            Text("Add " + amount + " AP to the clan fund? Points you put in stay in the fund even if you leave the squad.",
                color = Color.White, fontSize = 13.sp, modifier = Modifier.padding(vertical = 4.dp))
            Row(horizontalArrangement = Arrangement.spacedBy(10.dp), verticalAlignment = Alignment.CenterVertically) {
                Text(if (busy) "ADDING..." else "CONFIRM", color = Ink, fontSize = 12.sp, fontWeight = FontWeight.Bold,
                    modifier = Modifier.clip(RoundedCornerShape(8.dp)).background(if (busy) Muted else Gold)
                        .clickable(enabled = !busy) {
                            val rid = contribReq ?: return@clickable
                            busy = true
                            scope.launch {
                                val r = runCatching { Api.authedRpc("xr_clan_fund_contribute", JSONObject().put("p_squad", squadId).put("p_amount", amount.toIntOrNull() ?: 0).put("p_request", rid)) }.getOrNull()
                                when {
                                    r == null -> { msg = "Could not contribute - check your connection and try again."; msgOk = false }
                                    r.optBoolean("ok") || r.optBoolean("already") -> {
                                        msg = if (r.optBoolean("already")) "Already added - no points were taken twice." else amount + " AP added to the fund."
                                        msgOk = true; confirmContrib = false; contribReq = null; amount = ""
                                    }
                                    else -> { msg = r.s("error").ifBlank { "Could not contribute." }; msgOk = false }
                                }
                                busy = false
                                onChanged(); tick++
                            }
                        }.padding(horizontal = 12.dp, vertical = 8.dp))
                Text("Cancel", color = Muted, fontSize = 12.sp,
                    modifier = Modifier.clickable(enabled = !busy) { confirmContrib = false; contribReq = null }.padding(8.dp))
            }
        }
        if (canReward) {
            val members = f.optJSONArray("members")
            val candidates = mutableListOf<Pair<String, String>>()
            if (members != null) {
                for (i in 0 until members.length()) {
                    val m = members.getJSONObject(i)
                    val id = m.s("id").ifBlank { m.s("user_id") }
                    if (id.isNotBlank() && !m.optBoolean("is_me")) candidates += id to m.s("name").ifBlank { "Member" }
                }
            }
            Spacer(Modifier.height(8.dp))
            Text("REWARD A MEMBER (CAPTAIN)", color = Gold, fontSize = 11.sp, fontWeight = FontWeight.Bold)
            if (candidates.isEmpty()) {
                Text("Member rewards open once the clan roster is listed here.", color = Muted, fontSize = 12.sp, modifier = Modifier.padding(top = 2.dp))
            } else if (!confirmReward) {
                for (cand in candidates) {
                    Text(cand.second, color = Color.White, fontSize = 13.sp,
                        modifier = Modifier.fillMaxWidth().padding(vertical = 3.dp).clip(RoundedCornerShape(8.dp))
                            .background(if (rewardFor?.first == cand.first) Gold.copy(alpha = .25f) else Color(0x14FFFFFF))
                            .clickable { rewardFor = cand }.padding(horizontal = 10.dp, vertical = 8.dp))
                }
                if (rewardFor != null) {
                    Row(Modifier.padding(top = 4.dp), verticalAlignment = Alignment.CenterVertically) {
                        BasicTextField(value = rewardAmt, onValueChange = { rewardAmt = it.filter { ch -> ch.isDigit() }.take(6) },
                            textStyle = TextStyle(color = Color.White, fontSize = 13.sp),
                            modifier = Modifier.weight(1f).clip(RoundedCornerShape(8.dp)).border(1.dp, Color(0x22FFFFFF), RoundedCornerShape(8.dp)).padding(horizontal = 10.dp, vertical = 9.dp),
                            decorationBox = { inner -> if (rewardAmt.isEmpty()) Text("Reward in AP", color = Muted, fontSize = 13.sp); inner() })
                        Spacer(Modifier.width(8.dp))
                        Text("REVIEW", color = Ink, fontSize = 12.sp, fontWeight = FontWeight.Bold,
                            modifier = Modifier.clip(RoundedCornerShape(8.dp)).background(Gold)
                                .clickable {
                                    val a = rewardAmt.toIntOrNull() ?: 0
                                    if (a <= 0) { msg = "Enter a reward amount first."; msgOk = false; return@clickable }
                                    confirmReward = true; rewardReq = java.util.UUID.randomUUID().toString(); msg = null
                                }.padding(horizontal = 12.dp, vertical = 9.dp))
                    }
                }
            } else {
                val cand = rewardFor
                Text("Send " + rewardAmt + " AP from the clan fund to " + (cand?.second ?: "this member") + "?",
                    color = Color.White, fontSize = 13.sp, modifier = Modifier.padding(vertical = 4.dp))
                Row(horizontalArrangement = Arrangement.spacedBy(10.dp), verticalAlignment = Alignment.CenterVertically) {
                    Text(if (busy) "SENDING..." else "CONFIRM REWARD", color = Ink, fontSize = 12.sp, fontWeight = FontWeight.Bold,
                        modifier = Modifier.clip(RoundedCornerShape(8.dp)).background(if (busy) Muted else Gold)
                            .clickable(enabled = !busy) {
                                val rid = rewardReq ?: return@clickable
                                val rec = cand?.first ?: return@clickable
                                busy = true
                                scope.launch {
                                    val r = runCatching { Api.authedRpc("xr_clan_fund_reward", JSONObject().put("p_squad", squadId).put("p_recipient", rec).put("p_amount", rewardAmt.toIntOrNull() ?: 0).put("p_request", rid)) }.getOrNull()
                                    when {
                                        r == null -> { msg = "Could not send the reward - check your connection and try again."; msgOk = false }
                                        r.optBoolean("ok") || r.optBoolean("already") -> {
                                            msg = if (r.optBoolean("already")) "Already sent - no points were taken twice." else rewardAmt + " AP sent to " + cand.second + "."
                                            msgOk = true; confirmReward = false; rewardReq = null; rewardFor = null; rewardAmt = ""
                                        }
                                        else -> { msg = r.s("error").ifBlank { "Could not send the reward." }; msgOk = false }
                                    }
                                    busy = false
                                    onChanged(); tick++
                                }
                            }.padding(horizontal = 12.dp, vertical = 8.dp))
                    Text("Cancel", color = Muted, fontSize = 12.sp,
                        modifier = Modifier.clickable(enabled = !busy) { confirmReward = false; rewardReq = null }.padding(8.dp))
                }
            }
        }
        msg?.let { Text(it, color = if (msgOk) Gold2 else ErrRed, fontSize = 12.sp, modifier = Modifier.padding(top = 6.dp)) }
    }
}
