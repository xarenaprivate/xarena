package app.xarena.nativeapp

import androidx.compose.foundation.layout.*
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.window.Dialog
import kotlinx.coroutines.launch
import org.json.JSONObject
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Modifier
import androidx.compose.ui.unit.dp

/** Coarse area entry only. Never ask for a house, street address or exact coordinates. */
@Composable
internal fun MyLocationForm(state: String, district: String, busy: Boolean, status: String?,
    onState: (String) -> Unit, onDistrict: (String) -> Unit, onSave: () -> Unit,
    onDetect: () -> Unit, onClear: () -> Unit) {
    Column(Modifier.fillMaxWidth().verticalScroll(rememberScrollState()).padding(16.dp), verticalArrangement = Arrangement.spacedBy(10.dp)) {
        Text("My location", color = Gold2, style = MaterialTheme.typography.headlineSmall)
        Text("Choose your gaming area manually. GPS is optional. Do not enter a house number or street address.", color = Muted)
        OutlinedTextField(state, onState, label = { Text("State / region") }, colors = areaFieldColors(), singleLine = true, enabled = !busy, modifier = Modifier.fillMaxWidth())
        OutlinedTextField(district, onDistrict, label = { Text("City / district (optional)") }, colors = areaFieldColors(), singleLine = true, enabled = !busy, modifier = Modifier.fillMaxWidth())
        Text("Your state and district may appear on PUBLIC leaderboards and select your regional chat. Manual entry sends no GPS coordinates and removes any saved nearby grid. This is a self-selected area, not proof of residence.", color = Muted)
        GoldButton(if (busy) "SAVING..." else "SAVE AREA", enabled = !busy && state.trim().isNotEmpty()) { onSave() }
        TextButton(enabled = !busy, onClick = onDetect) { Text("Detect my state and district once", color = Gold) }
        TextButton(enabled = !busy, onClick = onClear) { Text("Remove saved area", color = Gold) }
        status?.let { Text(it, color = Gold2) }
    }
}


@Composable
private fun areaFieldColors() = OutlinedTextFieldDefaults.colors(focusedTextColor = Color.White, unfocusedTextColor = Color.White,
    focusedLabelColor = Gold, unfocusedLabelColor = Muted, focusedBorderColor = Gold, unfocusedBorderColor = Muted)

@Composable
fun MyLocationDialog(onClose: () -> Unit) {
    var state by remember { mutableStateOf("") }
    var district by remember { mutableStateOf("") }
    var busy by remember { mutableStateOf(true) }
    var status by remember { mutableStateOf<String?>(null) }
    var gpsConfirm by remember { mutableStateOf(false) }
    var nearby by remember { mutableStateOf(false) }
    val scope = rememberCoroutineScope()
    suspend fun readBack() {
        val r = Api.authedRpc("my_ranks", JSONObject())
        check(r.optBoolean("ok")) { "Could not read your saved area." }
        state = r.s("state_name").takeUnless { it == "null" } ?: ""
        district = r.s("district_name").takeUnless { it == "null" } ?: ""
        LocStore.state = state; LocStore.district = district
        LocStore.updatedAt = System.currentTimeMillis()
    }
    LaunchedEffect(Unit) {
        runCatching { readBack() }.onFailure { status = it.message }
        busy = false
    }
    fun detect() {
        busy = true; status = null
        scope.launch {
            val ok = detectLocationNow(Api.ctx, nearby)
            if (ok) {
                status = runCatching { readBack(); Api.chatMyRoom(); "Detected area saved. You can change it manually." }
                    .getOrElse { "Area saved, but could not refresh the display. Close and reopen My location." }
            } else status = "Could not detect a fresh area. Enter your state and district manually, or turn on device location and try again."
            busy = false
        }
    }
    val permission = rememberLauncherForActivityResult(ActivityResultContracts.RequestPermission()) { granted ->
        if (granted) detect() else status = "GPS permission was not given. Manual entry still works."
    }
    Dialog(onDismissRequest = { if (!busy) onClose() }) {
        Surface(color = Card, shape = androidx.compose.foundation.shape.RoundedCornerShape(18.dp)) {
            Column(Modifier.heightIn(max = 660.dp)) {
                Box(Modifier.weight(1f, fill = false)) {
                    MyLocationForm(state, district, busy, status,
                        { state = it.take(60) }, { district = it.take(60) },
                        onSave = {
                            busy = true; status = null
                            scope.launch {
                                try {
                                    val r = Api.authedRpc("set_my_area", AreaPayload.manual(state, district))
                                    check(r.optBoolean("ok")) { r.s("error", "Could not save area.") }
                                    LocStore.consent = false
                                    readBack(); Api.chatMyRoom()
                                    status = "Area saved. Nearby GPS grid is off."
                                } catch (e: Exception) { status = e.message ?: "Could not save area." }
                                busy = false
                            }
                        }, onDetect = { gpsConfirm = true }, onClear = {
                            busy = true; status = null
                            scope.launch {
                                try {
                                    val r = Api.authedRpc("clear_my_area", JSONObject())
                                    check(r.optBoolean("ok")) { r.s("error", "Could not remove area.") }
                                    state = ""; district = ""; LocStore.state = ""; LocStore.district = ""
                                    LocStore.consent = false; LocStore.updatedAt = 0
                                    Api.chatMyRoom(); status = "Saved area and nearby grid removed. Global board is still available."
                                } catch (e: Exception) { status = e.message ?: "Could not remove area." }
                                busy = false
                            }
                        })
                }
                TextButton(enabled = !busy, onClick = onClose, modifier = Modifier.fillMaxWidth()) { Text("Close", color = Gold) }
            }
        }
    }
    if (gpsConfirm) AreaGpsConsent(nearby, { nearby = it },
        { gpsConfirm = false; permission.launch(android.Manifest.permission.ACCESS_COARSE_LOCATION) }, { gpsConfirm = false })
}

@Composable
internal fun AreaGpsConsent(nearby: Boolean, onNearby: (Boolean) -> Unit, onContinue: () -> Unit, onCancel: () -> Unit) {
    AlertDialog(onDismissRequest = onCancel, title = { Text("Optional GPS lookup") },
        text = { Column {
            Text("Use device location once to find your state and district. Those labels may appear on public leaderboards and choose your regional chat. No house address is collected.")
            Row { Checkbox(nearby, onNearby); Text("Also enable the 10 km board. XARENA receives coordinates to save a rough 5 km grid, never shown to other players.") }
        } }, confirmButton = { TextButton(onClick = onContinue) { Text("Continue") } },
        dismissButton = { TextButton(onClick = onCancel) { Text("Cancel") } })
}

internal object AreaPayload {
    fun manual(state: String, district: String): JSONObject = JSONObject().put("p_state", state.trim().take(60))
        .put("p_district", district.trim().take(60).ifBlank { null } ?: JSONObject.NULL)
        .put("p_lat", JSONObject.NULL).put("p_lon", JSONObject.NULL)
}
