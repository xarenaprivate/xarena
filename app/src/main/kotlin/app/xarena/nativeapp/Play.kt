package app.xarena.nativeapp

import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import kotlinx.coroutines.launch
import org.json.JSONArray
import org.json.JSONObject

/* Play - real mini-games from the site's play page: daily spin + quizzes (daily check-in lives in Streak).
   Same RPCs as the web: spin_daily, quiz_feed, quiz_answer. XP only, never cash. */
private val SEG_LABELS = listOf("5 XP", "10 XP", "15 XP", "20 XP", "30 XP", "50 XP", "Lucky")

@Composable
fun PlayScreen(onBack: () -> Unit) {
    val scope = rememberCoroutineScope()
    var tick by remember { mutableIntStateOf(0) }
    var spinBusy by remember { mutableStateOf(false) }
    var spinMsg by remember { mutableStateOf<String?>(null) }
    var answering by remember { mutableStateOf<Int?>(null) }

    val quizzes = quizList(tick)
    Column(Modifier.fillMaxSize().background(Ink)) {
        PageHeader("Play", "Spin and quiz - real XP, free", onBack)
        LazyColumn(Modifier.fillMaxSize()) {
            item {
                var claimBusy by remember { mutableStateOf<String?>(null) }
                var claimMsg by remember { mutableStateOf<String?>(null) }
                var mission by remember { mutableStateOf<JSONObject?>(null) }
                LaunchedEffect(tick) {
                    mission = kotlinx.coroutines.withContext(kotlinx.coroutines.Dispatchers.IO) {
                        runCatching { Api.authedRpc("daily_missions_v2", JSONObject()) }.getOrNull()
                    }
                }
                SmallCard {
                    Text("DAILY MISSIONS", color = Gold, fontSize = 14.sp, fontWeight = FontWeight.Bold)
                    Text("Three real tasks a day - checked on the server, no fake completion.", color = Muted, fontSize = 12.sp)
                    Spacer(Modifier.height(8.dp))
                    val m = mission
                    if (m == null || !m.optBoolean("ok")) {
                        Text(if (m == null) "Loading today's missions..." else "Log in to see today's missions.", color = Muted, fontSize = 12.sp)
                    } else {
                        val arr = m.optJSONArray("missions") ?: JSONArray()
                        (0 until arr.length()).forEach { i ->
                            val mi = arr.getJSONObject(i)
                            val doneIt = mi.optBoolean("done"); val claimedIt = mi.optBoolean("claimed")
                            Row(Modifier.fillMaxWidth().padding(vertical = 3.dp), verticalAlignment = Alignment.CenterVertically) {
                                Text(if (doneIt) "\u2713" else "\u2022", color = if (doneIt) Color(0xFF4ADE80) else Muted, fontSize = 14.sp, fontWeight = FontWeight.Bold, modifier = Modifier.width(18.dp))
                                Column(Modifier.weight(1f)) {
                                    Text(mi.s("title").ifBlank { mi.s("kind") }, color = if (doneIt) Color.White else Muted, fontSize = 13.sp, fontWeight = if (doneIt) FontWeight.Bold else FontWeight.Normal)
                                    Text("+" + mi.optInt("reward") + " AP", color = Gold2, fontSize = 10.sp, fontWeight = FontWeight.Bold)
                                }
                                when {
                                    claimedIt -> Text("CLAIMED", color = Gold2, fontSize = 10.sp, fontWeight = FontWeight.Bold)
                                    doneIt -> Text(if (claimBusy == mi.s("kind")) "..." else "CLAIM", color = Ink, fontSize = 11.sp, fontWeight = FontWeight.Bold,
                                        modifier = Modifier.clip(RoundedCornerShape(8.dp)).background(Gold).clickable(claimBusy == null) {
                                            claimBusy = mi.s("kind"); claimMsg = null
                                            scope.launch {
                                                try {
                                                    val r = Api.authedRpc("claim_mission_v2", JSONObject().put("p_kind", mi.s("kind")))
                                                    if (r.optBoolean("ok")) tick++ else claimMsg = r.s("error").ifBlank { "Could not claim - try again." }
                                                } catch (e: Exception) { claimMsg = e.message }
                                                claimBusy = null
                                            }
                                        }.padding(horizontal = 10.dp, vertical = 5.dp))
                                    else -> Text("PENDING", color = Muted, fontSize = 10.sp, fontWeight = FontWeight.Bold)
                                }
                            }
                        }
                        claimMsg?.let { Text(it, color = ErrRed, fontSize = 12.sp) }
                    }
                }
            }
            item {
                SmallCard {
                    Text("DAILY SPIN", color = Gold, fontSize = 14.sp, fontWeight = FontWeight.Bold)
                    Text("One free spin a day. Wins land as XP on your rank.", color = Muted, fontSize = 12.sp)
                    Spacer(Modifier.height(10.dp))
                    GoldButton(if (spinBusy) "SPINNING..." else "SPIN", enabled = !spinBusy) {
                        spinBusy = true; spinMsg = null
                        scope.launch {
                            try {
                                val r = Api.authedRpc("spin_daily", JSONObject())
                                spinMsg = if (r.optBoolean("ok")) {
                                    val seg = r.optInt("segment", -1)
                                    "+" + r.optInt("xp", 0) + " XP - landed on " + (SEG_LABELS.getOrNull(seg) ?: "the wheel") +
                                        (if (!r.s("badge").isNullOrBlank()) " + Lucky Spinner badge!" else "")
                                } else r.s("error").ifBlank { "Spin failed - try again." }
                            } catch (e: Exception) { spinMsg = e.message ?: "Spin failed - try again." }
                            spinBusy = false; tick++
                        }
                    }
                    spinMsg?.let { Spacer(Modifier.height(6.dp)); Text(it, color = Gold2, fontSize = 13.sp) }
                }
                Spacer(Modifier.height(10.dp))
                Text("QUIZZES", color = Gold, fontSize = 14.sp, fontWeight = FontWeight.Bold, modifier = Modifier.padding(horizontal = 16.dp))
                Spacer(Modifier.height(6.dp))
            }
            if (quizzes.isEmpty()) item {
                Text("New quizzes drop here regularly - check back soon.", color = Muted, fontSize = 13.sp, modifier = Modifier.padding(horizontal = 16.dp))
            }
            items(quizzes) { q ->
                SmallCard {
                    Text((if (q.s("kind") == "daily") "DAILY" else "QUIZ") + " - +" + q.optInt("points", 0) + " XP", color = Muted, fontSize = 11.sp, fontWeight = FontWeight.Bold)
                    Spacer(Modifier.height(4.dp))
                    Text(q.s("question"), color = Color.White, fontSize = 15.sp, fontWeight = FontWeight.Bold)
                    Spacer(Modifier.height(8.dp))
                    val opts = q.optJSONArray("options")
                    val answered = q.optBoolean("answered")
                    if (opts != null) (0 until opts.length()).forEach { i ->
                        val mine = answered && q.optInt("my_choice", -1) == i
                        val right = answered && mine && q.optBoolean("was_correct")
                        val wrong = answered && mine && !q.optBoolean("was_correct")
                        val bg = if (right) Color(0xFF14532D) else if (wrong) Color(0xFF7F1D1D) else Color(0x22FFFFFF)
                        Text(opts.optString(i), color = Color.White, fontSize = 14.sp,
                            modifier = Modifier.fillMaxWidth().padding(vertical = 3.dp).clip(RoundedCornerShape(8.dp)).background(bg)
                                .clickable(enabled = !answered && answering == null) {
                                    answering = q.optInt("id")
                                    scope.launch {
                                        try {
                                            val r = Api.authedRpc("quiz_answer", JSONObject().put("p_quiz", q.optInt("id")).put("p_choice", i))
                                            if (!r.optBoolean("ok")) answering = null
                                        } catch (e: Exception) { answering = null }
                                        answering = null; tick++
                                    }
                                }
                                .padding(horizontal = 12.dp, vertical = 10.dp))
                    }
                    if (answered) {
                        Spacer(Modifier.height(4.dp))
                        Text(if (q.optBoolean("was_correct")) "Correct - +" + q.optInt("points", 0) + " XP" else "Wrong this time",
                            color = if (q.optBoolean("was_correct")) Color(0xFF8FF0B8) else Color(0xFFFF9D9D), fontSize = 12.sp)
                    }
                }
                Spacer(Modifier.height(8.dp))
            }
            item { Spacer(Modifier.height(30.dp)) }
        }
    }
}

/* quiz list with a manual state wrapper so answers re-render after each tap */
@Composable
private fun quizList(tick: Int): List<JSONObject> {
    val state = produceState<List<JSONObject>>(initialValue = emptyList(), tick) {
        value = runCatching {
            val f = Api.authedRpc("quiz_feed", JSONObject())
            val a = f.optJSONArray("quizzes")
            if (a == null) emptyList() else (0 until a.length()).map { a.getJSONObject(it) }
        }.getOrDefault(emptyList())
    }
    return state.value
}
