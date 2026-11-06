package app.xarena.nativeapp

import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import kotlinx.coroutines.launch
import org.json.JSONObject
import java.text.SimpleDateFormat
import java.util.Locale
import java.util.TimeZone

@Composable
fun GiftMailbox() {
    val scope = rememberCoroutineScope()
    var tick by remember { mutableIntStateOf(0) }
    var claiming by remember { mutableStateOf<String?>(null) }
    var status by remember { mutableStateOf<String?>(null) }
    Column {
        status?.let { Text(it, color = Gold2, modifier = Modifier.padding(16.dp)) }
        LoadState2(tick, {
            val r = Api.authedRpc("my_gift_mailbox", JSONObject())
            check(r.optBoolean("ok")) { r.s("error", "Could not load gifts.") }
            val a = r.optJSONArray("gifts") ?: org.json.JSONArray()
            (0 until a.length()).map { a.getJSONObject(it) }
        }) { gifts ->
            LazyColumn {
                if (gifts.isEmpty()) item { Text("No gifts yet. Gifts sent to you will appear here.", color = Muted, modifier = Modifier.padding(24.dp)) }
                items(gifts, key = { it.s("id") }) { gift ->
                    SmallCard {
                        Text(gift.s("title"), color = Gold2, fontSize = 17.sp, fontWeight = FontWeight.Bold)
                        if (gift.s("body").isNotBlank()) Text(gift.s("body"), color = Color.White, fontSize = 13.sp)
                        Text(if (gift.s("reward_kind") == "ap") "${gift.optInt("amount")} AP (cosmetic points, not cash)"
                            else gift.s("reward_title").ifBlank { gift.s("reward_kind") }, color = Gold, fontSize = 14.sp)
                        val claimed = !gift.isNull("claimed_at") && gift.s("claimed_at").isNotBlank()
                        val expiry = gift.s("expires_at").takeUnless { it == "null" || it.isBlank() }
                        val expired = expiry?.let { runCatching { SimpleDateFormat("yyyy-MM-dd'T'HH:mm:ss", Locale.US).apply { timeZone = TimeZone.getTimeZone("UTC") }.parse(it.take(19))!!.time < System.currentTimeMillis() }.getOrDefault(false) } ?: false
                        if (expiry != null) Text("Expires: " + expiry.take(16).replace("T", " ") + " UTC", color = Muted, fontSize = 11.sp)
                        when {
                            claimed -> Text("Claimed", color = Muted, fontSize = 13.sp)
                            expired -> Text("Expired", color = Muted, fontSize = 13.sp)
                            else -> GoldButton(if (claiming == gift.s("id")) "CLAIMING..." else "CLAIM", enabled = claiming == null) {
                                claiming = gift.s("id"); status = null
                                scope.launch {
                                    try {
                                        val r = Api.authedRpc("claim_gift", JSONObject().put("p_gift", gift.s("id")))
                                        check(r.optBoolean("ok")) { r.s("error", "Could not claim gift.") }
                                        status = when { r.optBoolean("already") -> "Already claimed. No duplicate reward added."
                                            r.optBoolean("already_owned") -> "You already own this item. Gift marked claimed, no replacement AP."
                                            else -> "Gift claimed. Your balance or inventory has been updated." }
                                        tick++
                                    } catch (e: Exception) { status = e.message ?: "Could not claim. Try again." }
                                    claiming = null
                                }
                            }
                        }
                    }
                }
            }
        }
    }
}
