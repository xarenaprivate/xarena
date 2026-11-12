package app.xarena.nativeapp

import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyColumn
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
import kotlinx.coroutines.launch
import org.json.JSONObject

/* Trust & Safety (v82): verification status, real Trust Score, backup-slot preference and
   report/support entries. Every value comes from an existing RPC: my_id_verification,
   xr_sf_player_get (trust_score - NULL until real ratings exist), xr_sf_player_preferences
   (backup_opt_in). No fabricated ratings: an unrated player reads "not rated yet". */

internal object TsSeams {
    var verification: JSONObject? = null
    var trustScore: Int? = null
    var trustLoaded = false
    var backupOptIn = false
    fun clear() { verification = null; trustScore = null; trustLoaded = false; backupOptIn = false }
}

@Composable
fun TrustSafetyScreen(onBack: () -> Unit, onOpenVerify: () -> Unit, onOpenSupport: () -> Unit, onOpenFeedback: () -> Unit) {
    val scope = rememberCoroutineScope()
    var ver by remember { mutableStateOf(TsSeams.verification) }
    var trust by remember { mutableStateOf(TsSeams.trustScore) }
    var trustLoaded by remember { mutableStateOf(TsSeams.trustLoaded) }
    var backup by remember { mutableStateOf(TsSeams.backupOptIn) }
    var msg by remember { mutableStateOf<String?>(null) }
    var busy by remember { mutableStateOf(false) }

    LaunchedEffect(Unit) {
        ver = runCatching { Api.authedRpc("my_id_verification", JSONObject()) }.getOrNull()
        val uid = Api.uid
        if (uid != null) {
            val pr = runCatching { Api.authedRpc("xr_sf_player_get", JSONObject().put("p_user", uid)) }.getOrNull()
            val pj = pr?.optJSONObject("player") ?: pr
            if (pj != null && pj.has("trust_score") && !pj.isNull("trust_score")) trust = pj.optInt("trust_score")
            trustLoaded = true
            val pf = runCatching { Api.authedRpc("xr_sf_player_preferences", JSONObject()) }.getOrNull()?.optJSONObject("preferences")
            if (pf != null) backup = pf.optBoolean("backup_opt_in", false)
        }
    }

    LazyColumn(Modifier.fillMaxSize().background(Ink), contentPadding = PaddingValues(16.dp), verticalArrangement = Arrangement.spacedBy(10.dp)) {
        item { PageHeader("Trust & Safety", "Secure, fair and verified - nothing faked", onBack) }

        item {
            PanelCard {
                val verified = ver?.optBoolean("verified") == true
                val st = ver?.optJSONObject("submission")?.s("status") ?: ""
                Row(verticalAlignment = Alignment.CenterVertically) {
                    Text("ID VERIFICATION", color = Gold, fontSize = 13.sp, fontWeight = FontWeight.Bold, modifier = Modifier.weight(1f))
                    Text(if (verified) "VERIFIED" else st.uppercase().ifBlank { "NOT SUBMITTED" },
                        color = if (verified) Color(0xFF7EE787) else Muted, fontSize = 11.sp, fontWeight = FontWeight.Bold,
                        modifier = Modifier.clip(RoundedCornerShape(6.dp)).border(1.dp, if (verified) Color(0xFF7EE787) else Muted, RoundedCornerShape(6.dp)).padding(horizontal = 8.dp, vertical = 3.dp))
                }
                Spacer(Modifier.height(4.dp))
                Text(when {
                    verified -> "Your Free Fire ID is verified. The badge shows on your profile."
                    st == "pending" -> "Screenshot received - the admin reviews every submission personally."
                    st == "rejected" -> "Not approved. Upload a clearer Free Fire profile screenshot."
                    else -> "Verify your Free Fire ID to earn the verified badge."
                }, color = Muted, fontSize = 12.sp)
                Spacer(Modifier.height(6.dp))
                GoldButton(if (verified) "VIEW VERIFICATION" else "START VERIFICATION") { onOpenVerify() }
            }
        }

        item {
            PanelCard {
                Text("TRUST SCORE", color = Gold, fontSize = 13.sp, fontWeight = FontWeight.Bold)
                Spacer(Modifier.height(4.dp))
                if (!trustLoaded) Text("Loading...", color = Muted, fontSize = 12.sp)
                else if (trust != null) {
                    Text("Trust Score " + trust + "/100", color = Gold2, fontSize = 20.sp, fontWeight = FontWeight.Bold)
                    Text("Built from real match behavior - reports, no-shows and fair play.", color = Muted, fontSize = 12.sp)
                } else {
                    Text("Trust Score not rated yet", color = Muted, fontSize = 14.sp, fontWeight = FontWeight.Bold)
                    Text("Play matches and keep it clean - your score appears once there is real data to rate.", color = Muted, fontSize = 12.sp)
                }
            }
        }

        item {
            PanelCard {
                Row(verticalAlignment = Alignment.CenterVertically) {
                    Column(Modifier.weight(1f)) {
                        Text("BACKUP SLOT", color = Gold, fontSize = 13.sp, fontWeight = FontWeight.Bold)
                        Text("A captain can call you in when a player drops before a match.", color = Muted, fontSize = 12.sp)
                    }
                    Text(if (backup) "ON" else "OFF", color = if (backup) Ink else Muted, fontSize = 12.sp, fontWeight = FontWeight.Bold,
                        modifier = Modifier.clip(RoundedCornerShape(8.dp)).background(if (backup) Gold else Color(0x22FFFFFF))
                            .clickable { backup = !backup }.padding(horizontal = 14.dp, vertical = 8.dp))
                }
            }
        }

        item {
            PanelCard {
                Text("REPORT & SUPPORT", color = Gold, fontSize = 13.sp, fontWeight = FontWeight.Bold)
                Spacer(Modifier.height(4.dp))
                Text("Hacks, teaming or match-fixing - report it. False reports cost your own standing.", color = Muted, fontSize = 12.sp)
                Spacer(Modifier.height(6.dp))
                Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                    GoldButton("REPORT A PLAYER") { onOpenFeedback() }
                    Text("SUPPORT", color = Gold, fontSize = 12.sp, fontWeight = FontWeight.Bold,
                        modifier = Modifier.clip(RoundedCornerShape(8.dp)).border(1.dp, Gold, RoundedCornerShape(8.dp))
                            .clickable { onOpenSupport() }.padding(horizontal = 14.dp, vertical = 10.dp))
                }
            }
        }

        item {
            PanelCard {
                Text("SAFETY GUIDELINES", color = Muted, fontSize = 11.sp, fontWeight = FontWeight.Bold)
                Spacer(Modifier.height(4.dp))
                listOf(
                    "Never share your password or OTP - XARENA staff will never ask for it.",
                    "Room IDs and passwords are for registered players only. Do not post them publicly.",
                    "Play fair: no hacks, no teaming, no match-fixing. Bans are permanent.",
                    "XARENA is free - nobody official will ever ask you for money."
                ).forEach { Text("- " + it, color = Color.White, fontSize = 12.sp, modifier = Modifier.padding(vertical = 2.dp)) }
            }
        }

        item {
            GoldButton(if (busy) "Saving..." else "SAVE CHANGES") {
                if (busy) return@GoldButton
                busy = true
                scope.launch {
                    val patch = JSONObject().put("backup_opt_in", backup)
                    val r = runCatching { Api.authedRpc("xr_sf_player_preferences", JSONObject().put("p_patch", patch)) }.getOrNull()
                    msg = if (r?.optBoolean("ok") == true) "Saved." else r?.s("error") ?: "Could not save - try again."
                    busy = false
                }
            }
            msg?.let { Text(it, color = if (it == "Saved.") Color(0xFF4ADE80) else ErrRed, fontSize = 12.sp, modifier = Modifier.padding(top = 6.dp)) }
        }
    }
}
