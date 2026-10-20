package app.xarena.nativeapp

import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyRow
import androidx.compose.foundation.lazy.items
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
import org.json.JSONArray
import org.json.JSONObject

/* Spotlights: Squad of the Week, Player of the Day, MVP, Clip of the Week - all from the
   spotlights table (newest row per kind). Renders nothing when admin has not published any. */
private val SPOT_LABELS = mapOf(
    "squad_week" to "SQUAD OF THE WEEK",
    "player_day" to "PLAYER OF THE DAY",
    "mvp_match" to "MVP OF THE MATCH",
    "mvp_week" to "MVP OF THE WEEK",
    "clip_week" to "CLIP OF THE WEEK")

@Composable
fun SpotlightsRow(onOpen: (String) -> Unit) {
    var rows by remember { mutableStateOf<List<JSONObject>?>(null) }
    LaunchedEffect(Unit) {
        rows = runCatching {
            val a = Api.publicGet("/rest/v1/spotlights?select=kind,name,detail,logo_url,ref_url,created_at&order=created_at.desc&limit=40")
            val newest = LinkedHashMap<String, JSONObject>()
            for (i in 0 until a.length()) {
                val o = a.getJSONObject(i)
                val k = o.s("kind")
                if (SPOT_LABELS.containsKey(k) && !newest.containsKey(k)) newest[k] = o
            }
            newest.values.toList()
        }.getOrElse { emptyList() }
    }
    val list = rows ?: return
    if (list.isEmpty()) return
    Column(Modifier.fillMaxWidth().padding(vertical = 4.dp)) {
        LazyRow(Modifier.fillMaxWidth(), contentPadding = PaddingValues(horizontal = 12.dp)) {
            items(list) { o ->
                Column(Modifier.width(250.dp).padding(horizontal = 4.dp).clip(RoundedCornerShape(Look.cardRadius))
                    .background(Look.cardBg).border(1.dp, Gold.copy(alpha = .5f), RoundedCornerShape(Look.cardRadius))
                    .clickable(enabled = o.s("ref_url").startsWith("http")) {
                        val u = o.s("ref_url")
                        runCatching { Api.ctx.startActivity(android.content.Intent(android.content.Intent.ACTION_VIEW, android.net.Uri.parse(u))) }
                    }.padding(14.dp)) {
                    Text(SPOT_LABELS[o.s("kind")] ?: "SPOTLIGHT", color = Gold, fontSize = 11.sp, fontWeight = FontWeight.Bold)
                    Spacer(Modifier.height(6.dp))
                    Row(verticalAlignment = Alignment.CenterVertically) {
                        val logo = o.s("logo_url")
                        if (logo.isNotBlank()) {
                            coil.compose.AsyncImage(model = xrImageModel(Api.ctx, logo), contentDescription = null,
                                modifier = Modifier.size(34.dp).clip(RoundedCornerShape(8.dp)).background(Color(0x22FFFFFF)))
                            Spacer(Modifier.width(10.dp))
                        }
                        Column {
                            Text(o.s("name"), color = Color.White, fontSize = 15.sp, fontWeight = FontWeight.Bold, maxLines = 1)
                            if (o.s("detail").isNotBlank()) Text(o.s("detail"), color = Muted, fontSize = 11.sp, maxLines = 2)
                        }
                    }
                }
            }
        }
    }
}
