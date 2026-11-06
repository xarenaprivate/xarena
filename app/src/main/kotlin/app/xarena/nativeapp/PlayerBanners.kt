package app.xarena.nativeapp

import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import kotlinx.coroutines.launch
import org.json.JSONObject

@Composable
fun PlayerBanner(username: String, editable: Boolean = false) {
    var tick by remember { mutableIntStateOf(0) }
    val banner by produceState<JSONObject?>(null, username, tick) {
        value = runCatching { val raw = Api.authedRpcRaw("player_banner", JSONObject().put("p_username", username));
            if (raw.trim() == "null") null else JSONObject(raw) }.getOrNull()
    }
    Column(Modifier.fillMaxWidth()) {
        banner?.let { BannerSwatch(it) }
        if (editable) OwnedBannerPicker { tick++ }
    }
}

@Composable
internal fun BannerSwatch(banner: JSONObject) {
    fun color(key: String) = runCatching { Color(android.graphics.Color.parseColor(banner.s(key))) }.getOrDefault(Ink)
    Box(Modifier.fillMaxWidth().height(76.dp).clip(RoundedCornerShape(14.dp))
        .background(Brush.horizontalGradient(listOf(color("color_start"), color("color_end")))).padding(16.dp)) {
        Text(banner.s("title"), color = Color.White, fontSize = 18.sp)
    }
}

@Composable
private fun OwnedBannerPicker(changed: () -> Unit) {
    var open by remember { mutableStateOf(false) }
    var owned by remember { mutableStateOf<List<JSONObject>>(emptyList()) }
    var busy by remember { mutableStateOf(false) }
    var error by remember { mutableStateOf<String?>(null) }
    val scope = rememberCoroutineScope()
    TextButton(onClick = {
        open = true; busy = true; error = null
        scope.launch {
            try {
                val uid = Api.uid ?: error("Not logged in.")
                val rights = Api.authedGetArray("/rest/v1/player_banner_owners?user_id=eq.$uid&select=banner_id")
                val ids = (0 until rights.length()).map { rights.getJSONObject(it).s("banner_id") }.toSet()
                val catalog = Api.publicGet("/rest/v1/player_banner_catalog?active=eq.true&select=*&order=sort.asc")
                owned = (0 until catalog.length()).map { catalog.getJSONObject(it) }.filter { it.s("id") in ids }
            } catch (e: Exception) { error = e.message }
            busy = false
        }
    }) { Text("Choose owned banner", color = Gold) }
    if (open) AlertDialog(onDismissRequest = { if (!busy) open = false }, title = { Text("Your banners") },
        text = {
            Column {
                error?.let { Text(it, color = ErrRed) }
                if (busy) CircularProgressIndicator()
                if (!busy && owned.isEmpty()) Text("No owned banners yet. Claim a banner gift from your mailbox to use it.")
                fun equip(id: String?) {
                    busy = true; error = null
                    scope.launch {
                        try {
                            val r = Api.authedRpc("equip_player_banner", JSONObject().put("p_banner", id ?: JSONObject.NULL))
                            check(r.optBoolean("ok")) { r.s("error", "Could not equip banner.") }
                            changed(); open = false
                        } catch (e: Exception) { error = e.message }
                        busy = false
                    }
                }
                owned.forEach { item -> Box(Modifier.padding(vertical = 4.dp).clickable(enabled = !busy) { equip(item.s("id")) }) { BannerSwatch(item) } }
                TextButton(enabled = !busy, onClick = { equip(null) }) { Text("Remove banner") }
            }
        }, confirmButton = { TextButton(enabled = !busy, onClick = { open = false }) { Text("Close") } })
}
