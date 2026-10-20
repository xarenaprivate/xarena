package app.xarena.nativeapp

import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.OutlinedTextField
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
import org.json.JSONArray
import org.json.JSONObject

/* Creator board (creator_board, rows carry user_id) -> creator profile (creator_profile {p_id}).
   Same data the site's more.html#creators page shows. */

@Composable
fun CreatorBoardScreen(onBack: () -> Unit, onOpenProfile: (String) -> Unit) {
    var tick by remember { mutableIntStateOf(0) }
    Column(Modifier.fillMaxSize().background(Ink)) {
        PageHeader("Creators", "Verified XARENA creators", onBack)
        LoadState2(tick, { runCatching { Api.publicRpc("creator_board", JSONObject()) }.getOrNull() }) { r ->
            val rows = r?.optJSONArray("creators") ?: r?.optJSONArray("rows") ?: JSONArray()
            if (rows.length() == 0) {
                SmallCard { Text("No creators yet", color = Color.White, fontSize = 15.sp, fontWeight = FontWeight.Bold)
                    Text("Creators appear here as the owner verifies them.", color = Muted, fontSize = 12.sp) }
            } else LazyColumn(contentPadding = PaddingValues(16.dp), verticalArrangement = Arrangement.spacedBy(6.dp)) {
                items((0 until rows.length()).map { rows.getJSONObject(it) }) { c ->
                    val uid = c.s("user_id")
                    Row(Modifier.fillMaxWidth().clip(RoundedCornerShape(12.dp)).background(Look.cardBg)
                        .border(1.dp, Look.cardBorder, RoundedCornerShape(12.dp))
                        .clickable(enabled = uid.isNotBlank()) { onOpenProfile(uid) }.padding(12.dp),
                        verticalAlignment = Alignment.CenterVertically) {
                        Column(Modifier.weight(1f)) {
                            Text(c.s("name") + if (c.optBoolean("verified")) " ✓" else "", color = Color.White, fontSize = 15.sp, fontWeight = FontWeight.Bold)
                            Text(listOf(c.s("tier").uppercase().takeIf { it.isNotBlank() } ?: "", if (c.s("joined").isNotBlank()) "joined via " + c.s("joined") else "")
                                .filter { it.isNotBlank() }.joinToString(" · "), color = Muted, fontSize = 11.sp)
                        }
                        if (uid.isNotBlank()) Text("VIEW", color = Gold, fontSize = 12.sp, fontWeight = FontWeight.Bold)
                    }
                }
            }
        }
    }
}

@Composable
fun CreatorProfileScreen(userId: String, onBack: () -> Unit) {
    var tick by remember { mutableIntStateOf(0) }
    val ctx = androidx.compose.ui.platform.LocalContext.current
    Column(Modifier.fillMaxSize().background(Ink)) {
        PageHeader("Creator", "", onBack)
        LoadState2(tick, { runCatching { Api.publicRpc("creator_profile", JSONObject().put("p_id", userId)) }.getOrNull() }) { c ->
            if (c == null || c.optBoolean("ok") == false && c.has("ok")) {
                SmallCard { Text("This creator profile is not available.", color = Muted, fontSize = 13.sp) }
                return@LoadState2
            }
            LazyColumn(contentPadding = PaddingValues(16.dp), verticalArrangement = Arrangement.spacedBy(10.dp)) {
                item { SmallCard {
                    Row(verticalAlignment = Alignment.CenterVertically) {
                        if (c.s("avatar_url").isNotBlank()) coil.compose.AsyncImage(model = xrImageModel(Api.ctx, c.s("avatar_url")), contentDescription = c.s("name"),
                            modifier = Modifier.width(64.dp).height(64.dp).clip(RoundedCornerShape(32.dp)), contentScale = androidx.compose.ui.layout.ContentScale.Crop)
                        Spacer(Modifier.width(12.dp))
                        Column {
                            Text(c.s("name") + " ✓", color = Gold2, fontSize = 20.sp, fontWeight = FontWeight.Bold)
                            Text(listOf(c.s("tier").uppercase().takeIf { it.isNotBlank() }?.let { "Tier: $it" } ?: "",
                                if (c.s("joined").isNotBlank()) "Joined via " + c.s("joined") else "").filter { it.isNotBlank() }.joinToString(" · "),
                                color = Muted, fontSize = 12.sp)
                        }
                    }
                    if (c.s("bio").isNotBlank()) { Spacer(Modifier.height(8.dp)); Text(c.s("bio"), color = Color.White, fontSize = 13.sp) }
                    if (c.s("channel_url").startsWith("http")) {
                        Spacer(Modifier.height(10.dp))
                        GoldButton("OPEN CHANNEL") {
                            runCatching { ctx.startActivity(android.content.Intent(android.content.Intent.ACTION_VIEW, android.net.Uri.parse(c.s("channel_url")))) }
                        }
                    }
                } }
                val posts = c.optJSONArray("posts") ?: JSONArray()
                item { Text("APPROVED POSTS", color = Gold, fontSize = 12.sp, fontWeight = FontWeight.Bold) }
                if (posts.length() == 0) item { SmallCard { Text("No approved posts yet.", color = Muted, fontSize = 12.sp) } }
                else items((0 until posts.length()).map { posts.getJSONObject(it) }) { p ->
                    SmallCard { Text(p.s("caption").ifBlank { "(no caption)" }, color = Color.White, fontSize = 13.sp)
                        if (p.s("at").isNotBlank()) Text(p.s("at").take(10), color = Muted, fontSize = 10.sp) }
                }
            }
        }
    }
}

/** Own creator bio edit (creator_set_bio, max 300 chars) - only rendered for verified creators. */
@Composable
fun CreatorBioEditor(onDone: (String?) -> Unit) {
    val scope = rememberCoroutineScope()
    var bio by remember { mutableStateOf("") }
    var busy by remember { mutableStateOf(false) }
    PanelCard {
        Text("YOUR CREATOR BIO", color = Gold2, fontSize = 13.sp, fontWeight = FontWeight.Bold)
        Text("Shows on your public creator profile - 300 characters max.", color = Muted, fontSize = 11.sp)
        OutlinedTextField(bio, { bio = it.take(300) }, placeholder = { Text("Tell players who you are...") },
            modifier = Modifier.fillMaxWidth())
        Text("${bio.length} / 300", color = Muted, fontSize = 10.sp)
        Spacer(Modifier.height(8.dp))
        GoldButton(if (busy) "SAVING..." else "SAVE BIO", enabled = !busy) {
            busy = true
            scope.launch {
                val r = runCatching { Api.authedRpc("creator_set_bio", JSONObject().put("p_bio", bio.trim())) }.getOrNull()
                busy = false
                onDone(if (r?.optBoolean("ok") == true) null else r?.s("error") ?: "Could not save.")
            }
        }
    }
}
