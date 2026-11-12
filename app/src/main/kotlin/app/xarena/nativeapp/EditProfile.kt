package app.xarena.nativeapp

import androidx.compose.foundation.background
import androidx.compose.foundation.layout.*
import androidx.compose.ui.graphics.Color
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Modifier
import androidx.compose.ui.text.input.KeyboardType
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import kotlinx.coroutines.launch
import org.json.JSONObject

/* Edit profile - players change their own IGN and Free Fire UID here (owner decision: once every
   14 days, enforced server-side by xr_profile_identity_update; the app only shows what the server
   says, including the honest next-change date). Username, phone, XP/AP and verification stay as-is
   except a UID change resets the gameplay verified badge (surfaced after save). */

object EpSeams {
    var status: JSONObject? = null
    var statusLoaded = false
    var profile: JSONObject? = null
    fun clear() { status = null; statusLoaded = false; profile = null }
}

private fun fmtWhen(iso: String): String {
    if (iso.isBlank()) return iso
    val t = iso.trim().replace("+00:00", "Z")
    return runCatching {
        val p = java.text.SimpleDateFormat("yyyy-MM-dd'T'HH:mm:ss", java.util.Locale.US)
        p.timeZone = java.util.TimeZone.getTimeZone("UTC")
        val d = p.parse(t.substringBefore('.').replace("Z", "")) ?: return@runCatching iso
        val o = java.text.SimpleDateFormat("d MMM yyyy, h:mm a", java.util.Locale.getDefault())
        o.format(d)
    }.getOrElse { iso }
}

@Composable
fun EditProfileScreen(onBack: () -> Unit) {
    val scope = rememberCoroutineScope()
    var st by remember { mutableStateOf(EpSeams.status) }
    var loaded by remember { mutableStateOf(EpSeams.statusLoaded) }
    var ign by remember { mutableStateOf(EpSeams.profile?.s("ign") ?: "") }
    var ffUid by remember { mutableStateOf(EpSeams.profile?.s("ff_uid") ?: "") }
    var busy by remember { mutableStateOf(false) }
    var msg by remember { mutableStateOf<String?>(null) }
    var err by remember { mutableStateOf<String?>(null) }
    LaunchedEffect(Unit) {
        if (!loaded) {
            val s = kotlinx.coroutines.withContext(kotlinx.coroutines.Dispatchers.IO) {
                runCatching { Api.authedRpc("xr_profile_identity_status", JSONObject()) }.getOrNull()
            }
            val p = runCatching { Api.myProfile() }.getOrNull()
            st = s; loaded = true
            if (p != null) { ign = p.s("ign"); ffUid = p.s("ff_uid") }
        }
    }
    Column(Modifier.fillMaxSize().background(Ink)) {
        PageHeader("Edit profile", "Your IGN and Free Fire UID", onBack)
        Column(Modifier.fillMaxSize().padding(16.dp)) {
            PanelCard {
                if (!loaded) {
                    Text("Loading your profile...", color = Muted, fontSize = 13.sp)
                } else {
                    val ok = st?.optBoolean("ok") != false
                    val available = st?.optBoolean("available", true) != false
                    val canChange = st?.optBoolean("can_change", true) != false
                    val nextAt = st?.s("next_allowed_at") ?: ""
                    val opensAt = st?.s("opens_at") ?: ""
                    val cd = st?.optInt("cooldown_days", 14) ?: 14
                    if (!ok || !available) {
                        Text("Profile editing is not open yet.", color = Muted, fontSize = 13.sp)
                        if (opensAt.isNotBlank()) Text("It opens on " + fmtWhen(opensAt) + ".", color = Gold2, fontSize = 13.sp)
                    } else {
                        Text("You can change your IGN and Free Fire UID once every " + cd + " days.", color = Muted, fontSize = 12.sp)
                        Spacer(Modifier.height(10.dp))
                        OutlinedTextField(ign, { ign = it }, label = { Text("In-game name (IGN)") }, singleLine = true, modifier = Modifier.fillMaxWidth())
                        Spacer(Modifier.height(10.dp))
                        OutlinedTextField(ffUid, { ffUid = it }, label = { Text("Free Fire UID") }, singleLine = true,
                            keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Number), modifier = Modifier.fillMaxWidth())
                        Text("Changing the UID resets your verified badge until re-verification.", color = Muted, fontSize = 12.sp)
                        if (!canChange && nextAt.isNotBlank()) {
                            Spacer(Modifier.height(8.dp))
                            Text("You can change these again on " + fmtWhen(nextAt) + ".", color = Gold2, fontSize = 13.sp)
                        }
                        msg?.let { Spacer(Modifier.height(8.dp)); Text(it, color = Color(0xFF4ADE80), fontSize = 13.sp) }
                        err?.let { Spacer(Modifier.height(8.dp)); Text(it, color = ErrRed, fontSize = 13.sp) }
                        Spacer(Modifier.height(16.dp))
                        val ignOk = ign.trim().length in 1..40
                        val uidOk = Regex("^[0-9]{5,15}$").matches(ffUid.trim())
                        GoldButton(if (busy) "SAVING..." else "SAVE CHANGES", enabled = !busy && canChange && ignOk && uidOk) {
                            busy = true; msg = null; err = null
                            scope.launch {
                                try {
                                    val r = Api.authedRpc("xr_profile_identity_update",
                                        JSONObject().put("p_ign", ign.trim()).put("p_ff_uid", ffUid.trim()))
                                    if (r.optBoolean("ok")) {
                                        msg = if (r.optBoolean("changed")) "Saved." +
                                            (if (r.optBoolean("verification_reset")) " Your verified badge was reset because the UID changed - it returns after re-verification." else "")
                                            else "No changes - your IGN and UID are already up to date."
                                        if (r.s("next_allowed_at").isNotBlank()) {
                                            st = JSONObject().put("ok", true).put("available", true).put("can_change", false)
                                                .put("next_allowed_at", r.s("next_allowed_at")).put("cooldown_days", r.optInt("cooldown_days", 14))
                                        }
                                    } else {
                                        val na = r.s("next_allowed_at")
                                        err = when (r.s("error")) {
                                            "cooldown" -> r.s("message").ifBlank { "IGN and UID can be changed once every " + cd + " days." } +
                                                (if (na.isNotBlank()) " You can change them again on " + fmtWhen(na) + "." else "")
                                            "not_open" -> "Profile editing is not open yet." + (if (r.s("opens_at").isNotBlank()) " It opens on " + fmtWhen(r.s("opens_at")) + "." else "")
                                            else -> r.s("message").ifBlank { r.s("error").ifBlank { "Could not save - try again." } }
                                        }
                                    }
                                } catch (e: Exception) { err = e.message ?: "Could not save - try again." }
                                busy = false
                            }
                        }
                    }
                }
            }
        }
    }
}
