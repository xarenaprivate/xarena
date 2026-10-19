package app.xarena.nativeapp

import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.itemsIndexed
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.Text
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import org.json.JSONArray
import org.json.JSONObject

/** Season Pass - free track, EXP levels unlock badges/frames/AP. No money path, ever. */
@Composable
fun SeasonPassScreen(onBack: () -> Unit) {
    val scope = rememberCoroutineScope()
    var tick by remember { mutableIntStateOf(0) }
    var msg by remember { mutableStateOf<String?>(null) }
    Column(Modifier.fillMaxSize().background(Ink)) {
        PageHeader("Season Pass", "Free track - level up with EXP", onBack)
        LoadState2(tick, {
            withContext(Dispatchers.IO) { Api.publicRpc("season_pass", JSONObject()) }
        }) { r ->
            if (!r.optBoolean("ok")) {
                EmptyNote("Season Pass is not available right now", "Try again in a bit.")
                return@LoadState2
            }
            val tiers = r.optJSONArray("tiers") ?: JSONArray()
            LazyColumn(Modifier.fillMaxSize()) {
                item {
                    PanelCard {
                        Text("SEASON LEVEL " + r.optInt("level"), color = Gold2, fontSize = 20.sp, fontWeight = FontWeight.Black)
                        Text("%,d EXP".format(r.optInt("xp")), color = Color.White, fontSize = 14.sp, fontWeight = FontWeight.Bold)
                        Text("Everything here is earned by playing - never sold.", color = Muted, fontSize = 12.sp)
                        if (!r.optBoolean("logged_in")) Text("Log in to claim unlocked rewards.", color = Muted, fontSize = 12.sp)
                    }
                    msg?.let { Text(it, color = if (it.startsWith("Claimed")) Color(0xFF4ADE80) else ErrRed, fontSize = 12.sp, modifier = Modifier.padding(horizontal = 16.dp)) }
                }
                itemsIndexed((0 until tiers.length()).map { tiers.getJSONObject(it) }) { _, t ->
                    val unlocked = t.optBoolean("unlocked"); val claimed = t.optBoolean("claimed")
                    Row(Modifier.fillMaxWidth().padding(horizontal = 16.dp, vertical = 4.dp).clip(RoundedCornerShape(12.dp))
                        .background(if (unlocked && !claimed) Look.cardBg else Color(0xFF120A24))
                        .border(1.dp, if (unlocked && !claimed) Gold else Look.cardBorder, RoundedCornerShape(12.dp)).padding(12.dp),
                        verticalAlignment = Alignment.CenterVertically) {
                        Text("Lv " + t.optInt("level"), color = if (unlocked) Gold2 else Muted, fontSize = 14.sp, fontWeight = FontWeight.Black, modifier = Modifier.width(52.dp))
                        Column(Modifier.weight(1f)) {
                            Text(t.s("title"), color = if (unlocked) Color.White else Muted, fontSize = 14.sp, fontWeight = FontWeight.Bold)
                            Text(t.s("kind").uppercase(), color = Muted, fontSize = 10.sp, fontWeight = FontWeight.Bold)
                        }
                        when {
                            claimed -> Text("CLAIMED", color = Gold2, fontSize = 11.sp, fontWeight = FontWeight.Bold)
                            unlocked -> Text("CLAIM", color = Ink, fontSize = 11.sp, fontWeight = FontWeight.Bold,
                                modifier = Modifier.clip(RoundedCornerShape(8.dp)).background(Gold).clickable {
                                    scope.launch {
                                        msg = null
                                        try {
                                            val c = withContext(Dispatchers.IO) { Api.authedRpc("season_claim", JSONObject().put("p_level", t.optInt("level"))) }
                                            if (c.optBoolean("ok")) { msg = "Claimed " + t.s("title"); tick++ } else msg = c.s("error").ifBlank { "Could not claim." }
                                        } catch (e: Exception) { msg = e.message }
                                    }
                                }.padding(horizontal = 12.dp, vertical = 6.dp))
                            else -> Text("LOCKED", color = Muted, fontSize = 11.sp, fontWeight = FontWeight.Bold)
                        }
                    }
                }
            }
        }
    }
}
