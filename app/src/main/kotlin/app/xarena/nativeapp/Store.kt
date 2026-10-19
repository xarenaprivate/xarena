package app.xarena.nativeapp

import androidx.compose.foundation.background
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

private data class StoreData(val balance: Int?, val xp: Int?, val catalog: JSONArray, val mine: JSONArray)

private suspend fun loadStore(): StoreData {
    val balance = try {
        val b = Api.authedRpc("points_balance", JSONObject())
        if (b.optBoolean("ok")) b.optInt("balance") else null
    } catch (e: Exception) { null }
    val catalog = try { Api.publicGet("/rest/v1/rewards_catalog?active=eq.true&select=id,title,description,cost,kind,image_url,logo_url&order=sort.asc,cost.asc") } catch (e: Exception) { JSONArray() }
    val mine = try { Api.authedRpc("my_redemptions", JSONObject()).optJSONArray("rows") ?: JSONArray() } catch (e: Exception) { JSONArray() }
    val xp = try {
        val g = Api.authedRpc("my_game_stats", JSONObject())
        if (g.optBoolean("ok")) g.optInt("xp") else null
    } catch (e: Exception) { null }
    return StoreData(balance, xp, catalog, mine)
}

private fun kindLabel(k: String) = when (k) {
    "avatar" -> "AVATAR"
    "team_logo" -> "TEAM LOGO"
    "avatar_frame" -> "AVATAR FRAME"
    "name_badge" -> "NAME BADGE"
    "title" -> "TITLE"
    else -> "REWARD"
}

/** Rewards Store - AP (Arena Points) are spendable here; XP is rank-only and shown read-only. Claims go to the owner for review. */
@Composable
fun StoreScreen(onBack: (() -> Unit)? = null) {
    val scope = rememberCoroutineScope()
    var notice by remember { mutableStateOf<String?>(null) }
    var claiming by remember { mutableStateOf<Long?>(null) }
    var tab by remember { mutableStateOf("All") }
    Column(Modifier.fillMaxSize()) {
        if (onBack != null) PageHeader("Store", "Spend AP on real rewards", onBack) else Brand()
        LoadState({ loadStore() }) { d ->
            LazyColumn {
                item {
                    PanelCard {
                        Text("FREE - NO CASH", color = Color(0xFF4ADE80), fontSize = 11.sp, fontWeight = FontWeight.Bold)
                        Text("YOUR AP BALANCE", color = Muted, fontSize = 11.sp)
                        Text(d.balance?.let { "$it AP" } ?: "-", color = Gold2, fontSize = 28.sp, fontWeight = FontWeight.Black)
                        Text("Arena Points are spendable here. Earn them with check-ins, spins and quiz wins. The owner reviews every claim.", color = Muted, fontSize = 12.sp)
                        if (d.xp != null) Text("Your XP: " + d.xp + " - rank points, not spendable", color = Gold, fontSize = 12.sp, fontWeight = FontWeight.Bold, modifier = Modifier.padding(top = 4.dp))
                    }
                }
                if (d.catalog.length() == 0) item {
                    EmptyNote("Catalog opens soon", "The owner is adding real rewards. Keep stacking XP - every check-in, spin and quiz win counts. XP is never cash.")
                }
                item {
                    val tabs = listOf("All", "Avatars", "Team Logos", "Avatar Frames", "Name Badges", "Titles")
                    Row(Modifier.fillMaxWidth().padding(horizontal = 12.dp, vertical = 4.dp)) {
                        tabs.forEach { t ->
                            val on = tab == t
                            Text(t, color = if (on) Ink else Muted, fontSize = 12.sp, fontWeight = FontWeight.Bold,
                                modifier = Modifier.padding(horizontal = 4.dp).clip(RoundedCornerShape(8.dp))
                                    .background(if (on) Gold else Color(0x22FFFFFF))
                                    .clickable { tab = t }.padding(horizontal = 10.dp, vertical = 6.dp))
                        }
                    }
                }
                val tabKinds = mapOf("Avatars" to "avatar", "Team Logos" to "team_logo", "Avatar Frames" to "avatar_frame", "Name Badges" to "name_badge", "Titles" to "title")
                val shownItems = (0 until d.catalog.length()).map { d.catalog.getJSONObject(it) }
                    .filter { tab == "All" || it.s("kind") == tabKinds[tab] }
                if (d.catalog.length() > 0 && shownItems.isEmpty()) item {
                    Text("Nothing in this shelf yet.", color = Muted, fontSize = 13.sp, modifier = Modifier.padding(horizontal = 16.dp, vertical = 8.dp))
                }
                items(shownItems) { it ->
                    PanelCard {
                        Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.SpaceBetween, verticalAlignment = Alignment.CenterVertically) {
                            val img = it.s("image_url").ifBlank { it.s("logo_url") }
                            if (img.isNotBlank()) {
                                coil.compose.AsyncImage(model = xrImageModel(Api.ctx, img), contentDescription = it.s("title"),
                                    contentScale = androidx.compose.ui.layout.ContentScale.Crop,
                                    modifier = Modifier.size(56.dp).clip(RoundedCornerShape(10.dp)).background(Color(0xFF2A1450)))
                                Spacer(Modifier.width(10.dp))
                            }
                            Column(Modifier.weight(1f)) {
                                Text(kindLabel(it.s("kind")), color = Gold, fontSize = 10.sp, fontWeight = FontWeight.Bold)
                                Text(it.s("title"), color = Color.White, fontSize = 16.sp, fontWeight = FontWeight.Bold)
                                if (it.s("description").isNotBlank()) Text(it.s("description"), color = Muted, fontSize = 12.sp)
                            }
                            Spacer(Modifier.width(10.dp))
                            Column(horizontalAlignment = Alignment.End) {
                                Text(it.optInt("cost").toString() + " AP", color = Gold2, fontWeight = FontWeight.Bold)
                                Spacer(Modifier.height(6.dp))
                                val canAfford = d.balance != null && d.balance >= it.optInt("cost")
                                Text(if (claiming == it.optLong("id")) "SENDING..." else "CLAIM",
                                    color = if (canAfford) Color(0xFF2A1400) else Color(0x66FFFFFF),
                                    fontSize = 12.sp, fontWeight = FontWeight.Bold,
                                    modifier = Modifier.clip(RoundedCornerShape(8.dp))
                                        .background(if (canAfford) Gold else Color(0x22FFFFFF))
                                        .clickable(enabled = canAfford && claiming == null) {
                                            claiming = it.optLong("id")
                                            scope.launch {
                                                try {
                                                    val r = Api.authedRpc("redeem_reward", JSONObject().put("p_item", it.optLong("id")))
                                                    notice = if (r.optBoolean("ok")) "Claim sent! The owner will review it. New balance: " + r.optInt("balance") + " AP."
                                                    else r.s("error").ifBlank { "Could not claim right now." }
                                                } catch (e: Exception) { notice = e.message ?: "Network error - try again." }
                                                claiming = null
                                            }
                                        }.padding(horizontal = 14.dp, vertical = 8.dp))
                            }
                        }
                    }
                }
                if (d.mine.length() > 0) {
                    item { Text("Your claims", color = Gold, fontWeight = FontWeight.Bold, fontSize = 16.sp, modifier = Modifier.padding(horizontal = 16.dp, vertical = 6.dp)) }
                    items((0 until d.mine.length()).map { d.mine.getJSONObject(it) }) { m ->
                        PanelCard {
                            Text(m.s("title").ifBlank { "Reward #" + m.optLong("item") }, color = Color.White, fontWeight = FontWeight.Bold, fontSize = 14.sp)
                            Text(m.s("status").ifBlank { "pending" }.uppercase(), color = Gold, fontSize = 12.sp)
                        }
                    }
                }
                item { Spacer(Modifier.height(16.dp)) }
            }
        }
        notice?.let { Text(it, color = Gold2, fontSize = 13.sp, modifier = Modifier.padding(horizontal = 16.dp, vertical = 4.dp)) }
    }
}
