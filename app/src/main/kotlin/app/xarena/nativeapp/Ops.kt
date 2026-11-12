package app.xarena.nativeapp

import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.Text
import androidx.compose.runtime.*
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import org.json.JSONObject

/* Server kill switch (xr_ops_status): when the backend sheds load, hide the affected
   social feature with an honest message instead of hammering it. Cached ~30s and
   refreshed on app resume. Never blocks Home shell, Match Center, Live, login,
   check-in, room, chat or registration - only the shed social sections. */
object Ops {
    @Volatile private var cached: JSONObject? = null
    @Volatile private var fetchedAt: Long = 0

    suspend fun refresh(force: Boolean = false) {
        val now = System.currentTimeMillis()
        if (!force && cached != null && now - fetchedAt < 30_000) return
        val r = runCatching { Api.publicRpc("xr_ops_status", JSONObject()) }.getOrNull()
        if (r != null && r.optBoolean("ok")) { cached = r; fetchedAt = now }
    }

    fun shed(kind: String): Boolean = cached?.optBoolean(kind + "_shed") == true
    fun textOnlyHome(): Boolean = cached?.optBoolean("text_only_home") == true
    fun message(): String = cached?.s("message") ?: ""
    fun retryAfter(): Long = cached?.optLong("retry_after_seconds") ?: 0
}

/* Composable gate: renders the busy panel and returns true when the feature is shed. */
@Composable
fun OpsShed(kind: String): Boolean {
    var shed by remember { mutableStateOf(Ops.shed(kind)) }
    LaunchedEffect(Unit) { Ops.refresh(); shed = Ops.shed(kind) }
    if (!shed) return false
    Column(Modifier.fillMaxWidth().padding(16.dp).clip(RoundedCornerShape(14.dp)).background(Look.cardBg).border(1.dp, Look.cardBorder, RoundedCornerShape(14.dp)).padding(16.dp)) {
        Text("Busy right now", color = Color.White, fontSize = 15.sp, fontWeight = FontWeight.Bold)
        Spacer(Modifier.height(4.dp))
        Text(if (Ops.message().isNotBlank()) Ops.message() else "This section is taking a short break - try again shortly.", color = Muted, fontSize = 12.sp)
        val ra = Ops.retryAfter()
        if (ra > 0) { Spacer(Modifier.height(4.dp)); Text("Try again in about $ra seconds", color = Muted, fontSize = 11.sp) }
    }
    return true
}
