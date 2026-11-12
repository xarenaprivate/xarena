package app.xarena.nativeapp

import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Text
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.TextStyle
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import coil.compose.AsyncImage
import kotlinx.coroutines.delay
import org.json.JSONArray
import org.json.JSONObject

/* Creator growth tab sections (contract: tools-migrations/20261112-creator-growth-README.md).
   REAL DATA ONLY: every count comes from the backend; empty means honestly empty.
   Free-only. No payouts, no delivered cosmetics - entitlements show their real status. */

private fun cgStatusText(e: JSONObject): String {
    val kind = e.s("key").ifBlank { e.s("kind") }.replace('_', ' ')
    return when (e.s("status")) {
        "granted" -> "$kind - granted"
        "pending_owner_fulfillment" -> "$kind - claimed, awaiting owner fulfillment"
        "pending_funding" -> "$kind - recorded, pending owner funding"
        else -> kind + " - " + e.s("status")
    }
}

@Composable
private fun CgHead(t: String) {
    Text(t, color = Gold, fontSize = 12.sp, fontWeight = FontWeight.Bold, letterSpacing = 1.sp, modifier = Modifier.padding(vertical = 6.dp))
}

@Composable
private fun CgCard(content: @Composable ColumnScope.() -> Unit) {
    Column(Modifier.fillMaxWidth().padding(vertical = 4.dp).clip(RoundedCornerShape(14.dp)).background(Look.cardBg).border(1.dp, Look.cardBorder, RoundedCornerShape(14.dp)).padding(12.dp), content = content)
}

@Composable
private fun CgInput(v: String, onChange: (String) -> Unit, hint: String, number: Boolean = false) {
    OutlinedTextField(value = v, onValueChange = { onChange(if (number) it.filter { c -> c.isDigit() }.take(7) else it.take(80)) },
        label = { Text(hint, fontSize = 12.sp) }, singleLine = true, modifier = Modifier.fillMaxWidth(),
        textStyle = TextStyle(color = Color.White, fontSize = 14.sp))
}

@Composable
private fun CgConsent(checked: Boolean, onToggle: () -> Unit, title: String, sub: String) {
    Row(Modifier.fillMaxWidth().clip(RoundedCornerShape(10.dp)).clickable { onToggle() }.padding(vertical = 6.dp), verticalAlignment = Alignment.CenterVertically) {
        Box(Modifier.size(20.dp).clip(RoundedCornerShape(5.dp)).border(1.dp, if (checked) Gold else Muted, RoundedCornerShape(5.dp)).background(if (checked) Gold.copy(alpha = .2f) else Color.Transparent), contentAlignment = Alignment.Center) {
            if (checked) Text("X", color = Gold, fontSize = 12.sp, fontWeight = FontWeight.Bold)
        }
        Spacer(Modifier.width(10.dp))
        Column { Text(title, color = Color.White, fontSize = 12.sp); Text(sub, color = Muted, fontSize = 11.sp) }
    }
}

@Composable
fun CreatorGrowthSection() {
    if (Api.uid.isNullOrBlank()) return
    var tick by remember { mutableIntStateOf(0) }
    var st by remember { mutableStateOf<JSONObject?>(null) }
    var lb by remember { mutableStateOf<JSONObject?>(null) }
    var act by remember { mutableStateOf<JSONObject?>(null) }
    var fr by remember { mutableStateOf<JSONObject?>(null) }
    LaunchedEffect(tick) {
        st = runCatching { Api.authedRpc("xr_creator_my_status", JSONObject()) }.getOrNull()
        lb = runCatching { Api.authedRpc("xr_creator_city_leaderboard", JSONObject()) }.getOrNull()
        act = runCatching { Api.authedRpc("xr_creator_activity_list", JSONObject().put("p_limit", 20)) }.getOrNull()
        fr = runCatching { Api.authedRpc("xr_creator_friends_applied", JSONObject()) }.getOrNull()
    }
    Column(Modifier.fillMaxWidth().padding(vertical = 4.dp)) {
        val stage = st?.s("stage") ?: ""
        if (st != null && stage.isNotBlank()) {
            CgStatusCard(st!!)
            if (stage == "rejected" || stage == "revoked") CgApplyCard(st!!.s("name"), st!!.s("city")) { tick++ }
        } else CgApplyCard("", "") { tick++ }

        val friends = fr?.optJSONArray("friends")
        if (friends != null && friends.length() > 0) {
            val names = (0 until minOf(3, friends.length())).joinToString(", ") { friends.getJSONObject(it).s("first_name") }
            Text("${friends.length()} of your friends already applied ($names)", color = Gold2, fontSize = 12.sp, modifier = Modifier.padding(vertical = 4.dp))
        }

        CgCreatorsCard(lb)
        CgActivityCard(act)
    }
}

@Composable
private fun CgStatusCard(st: JSONObject) {
    val stage = st.s("stage")
    CgHead("MY CREATOR APPLICATION")
    CgCard {
        if (stage == "waitlisted") {
            Text("WAITLISTED", color = Gold, fontSize = 15.sp, fontWeight = FontWeight.Bold)
            val rank = st.optLong("waitlist_rank")
            if (rank > 0) Text("Position #$rank in ${st.s("city")}", color = Color.White, fontSize = 13.sp)
            val boost = st.optLong("boost_points")
            if (boost > 0) Text("Boost points: $boost from ${st.optLong("qualified_referrals")} qualified referrals", color = Muted, fontSize = 11.sp)
        } else {
            val steps = listOf("applied" to "APPLIED", "under_review" to "UNDER REVIEW", "verified" to "VERIFIED")
            val cur = steps.indexOfFirst { it.first == stage }
            Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.spacedBy(6.dp)) {
                steps.forEachIndexed { i, s ->
                    val on = cur >= 0 && i <= cur
                    Box(Modifier.weight(1f).clip(RoundedCornerShape(8.dp)).border(1.dp, if (on) Gold else Look.cardBorder, RoundedCornerShape(8.dp)).background(if (on) Gold.copy(alpha = .12f) else Color.Transparent).padding(vertical = 8.dp), contentAlignment = Alignment.Center) {
                        Text(s.second, color = if (on) Gold else Muted, fontSize = 9.sp, fontWeight = FontWeight.Bold, textAlign = TextAlign.Center)
                    }
                }
            }
            if (stage == "rejected" || stage == "revoked") Text("Status: $stage - you can apply again below.", color = ErrRed, fontSize = 12.sp, modifier = Modifier.padding(top = 6.dp))
        }
        if (st.s("city").isNotBlank()) Text("City: ${st.s("city")}", color = Muted, fontSize = 11.sp, modifier = Modifier.padding(top = 6.dp))
        val ents = st.optJSONArray("entitlements")
        if (ents != null && ents.length() > 0) {
            Spacer(Modifier.height(6.dp))
            for (i in 0 until ents.length()) Text(cgStatusText(ents.getJSONObject(i)), color = Gold2, fontSize = 12.sp)
        }
    }
}

@Composable
private fun CgApplyCard(defName: String, defCity: String, onApplied: () -> Unit) {
    var name by remember { mutableStateOf(defName) }
    var city by remember { mutableStateOf(defCity) }
    var insta by remember { mutableStateOf("") }
    var players by remember { mutableStateOf("") }
    var pubAct by remember { mutableStateOf(false) }
    var frVis by remember { mutableStateOf(false) }
    var citySt by remember { mutableStateOf<JSONObject?>(null) }
    var msg by remember { mutableStateOf<String?>(null) }
    var busy by remember { mutableStateOf(false) }
    val scope = rememberCoroutineScope()
    LaunchedEffect(city) {
        citySt = null
        if (city.trim().length >= 3) {
            delay(400)
            citySt = runCatching { Api.authedRpc("xr_creator_city_status", JSONObject().put("p_city", city.trim())) }.getOrNull()
        }
    }
    val cs = citySt
    val open = cs?.optBoolean("applications_open") == true
    CgHead("BECOME A CREATOR")
    CgCard {
        Text("Host free tournaments - grow your city team", color = Color.White, fontSize = 13.sp, fontWeight = FontWeight.Bold)
        Spacer(Modifier.height(8.dp))
        CgInput(name, { name = it }, "Your name")
        Spacer(Modifier.height(6.dp))
        CgInput(city, { city = it }, "City")
        Spacer(Modifier.height(6.dp))
        CgInput(insta, { insta = it }, "Instagram handle")
        Spacer(Modifier.height(6.dp))
        CgInput(players, { players = it }, "How many players do you have?", number = true)
        if (cs != null && cs.optBoolean("ok")) {
            Spacer(Modifier.height(8.dp))
            val cap = cs.optLong("capacity"); val filled = cs.optLong("filled"); val rem = cs.optLong("remaining")
            Text("${cs.s("city")}: $filled/$cap creator slots filled - $rem open", color = Gold2, fontSize = 12.sp)
            val dl = cs.s("deadline")
            if (dl.isNotBlank() && dl != "null") Text("Application deadline: $dl", color = Muted, fontSize = 11.sp)
            if (!cs.isNull("bonus")) Text("Early-bird bonus is active for this city", color = Gold2, fontSize = 11.sp)
        }
        Spacer(Modifier.height(8.dp))
        CgConsent(pubAct, { pubAct = !pubAct }, "Show me in the public activity feed", "Publishes your first name + city when you apply or take a slot")
        CgConsent(frVis, { frVis = !frVis }, "Let my friends see I applied", "Shares your first name + city with mutual friends only")
        Spacer(Modifier.height(8.dp))
        msg?.let { Text(it, color = if (it.startsWith("Done")) Color(0xFF22C55E) else ErrRed, fontSize = 12.sp); Spacer(Modifier.height(6.dp)) }
        if (cs != null && !open) Text("Creator applications are not open yet - check back soon.", color = Muted, fontSize = 12.sp)
        GoldButton(if (open) "APPLY - 30 SECONDS" else "APPLY", enabled = !busy && open && name.isNotBlank() && city.trim().length >= 2 && insta.isNotBlank() && players.isNotBlank(), modifier = Modifier.fillMaxWidth()) {
            busy = true; msg = null
            scope.launch {
                try {
                    val r = Api.authedRpc("xr_creator_apply", JSONObject()
                        .put("p_name", name.trim()).put("p_city", city.trim())
                        .put("p_instagram", insta.trim()).put("p_player_count", players.toLongOrNull() ?: 0)
                        .put("p_public_activity", pubAct).put("p_friends_visible", frVis))
                    if (r.optBoolean("ok")) { msg = "Done - application " + r.s("stage"); onApplied() }
                    else msg = r.s("error").ifBlank { "Could not apply - try again" }
                } catch (e: Exception) { msg = e.message ?: "Could not apply - try again" }
                busy = false
            }
        }
    }
}

@Composable
private fun CgCreatorsCard(lb: JSONObject?) {
    CgHead("CREATORS")
    CgCard {
        val cow = lb?.optJSONObject("creator_of_week")
        if (cow != null) {
            Text("CREATOR OF THE WEEK: ${cow.s("name").ifBlank { cow.s("username") }}", color = Gold, fontSize = 12.sp, fontWeight = FontWeight.Bold)
            Spacer(Modifier.height(6.dp))
        }
        val arr = lb?.optJSONArray("creators")
        if (arr == null || arr.length() == 0) {
            Text("No verified creators yet - the board is waiting for its first.", color = Muted, fontSize = 12.sp)
        } else {
            for (i in 0 until arr.length()) {
                val c = arr.getJSONObject(i)
                Row(Modifier.fillMaxWidth().padding(vertical = 5.dp), verticalAlignment = Alignment.CenterVertically) {
                    val nm = c.s("name").ifBlank { c.s("username") }
                    val av = c.s("avatar_url")
                    Box(Modifier.size(38.dp).clip(CircleShape).background(Ink).border(1.dp, Gold.copy(alpha = .5f), CircleShape), contentAlignment = Alignment.Center) {
                        if (av.isNotBlank()) AsyncImage(model = xrImageModel(LocalContext.current, av), contentDescription = nm, modifier = Modifier.fillMaxSize(), contentScale = ContentScale.Crop)
                        else Text(nm.take(1).uppercase(), color = Gold, fontSize = 15.sp, fontWeight = FontWeight.Bold)
                    }
                    Spacer(Modifier.width(10.dp))
                    Column(Modifier.weight(1f)) {
                        Row(verticalAlignment = Alignment.CenterVertically) {
                            Text(nm, color = Color.White, fontSize = 13.sp, fontWeight = FontWeight.Bold)
                            if (c.optBoolean("founding")) { Spacer(Modifier.width(6.dp)); Text("FOUNDING", color = Gold, fontSize = 9.sp, fontWeight = FontWeight.Bold) }
                        }
                        val sub = listOf(c.s("city"), "${c.optLong("followers")} followers", "${c.optLong("event_joins")} event joins").filter { it.isNotBlank() && !it.startsWith("null") }.joinToString(" - ")
                        Text(sub, color = Muted, fontSize = 11.sp)
                    }
                }
            }
        }
    }
}

@Composable
private fun CgActivityCard(act: JSONObject?) {
    CgHead("LIVE ACTIVITY")
    CgCard {
        val arr = act?.optJSONArray("events")
        if (arr == null || arr.length() == 0) {
            Text("No creator activity yet.", color = Muted, fontSize = 12.sp)
        } else {
            for (i in 0 until arr.length()) {
                val e = arr.getJSONObject(i)
                val line = if (e.s("kind") == "slot_taken") "${e.s("first_name")} took a creator slot in ${e.s("city")}"
                    else "${e.s("first_name")} applied from ${e.s("city")}"
                Text(line, color = Color.White, fontSize = 12.sp, modifier = Modifier.padding(vertical = 3.dp))
            }
        }
    }
}
