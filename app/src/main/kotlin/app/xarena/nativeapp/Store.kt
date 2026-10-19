package app.xarena.nativeapp

import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.shape.CircleShape
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

private data class StoreData(
    val balance: Int?, val xp: Int?, val catalog: JSONArray, val mine: JSONArray,
    val avatars: JSONArray, val avOwned: Set<String>, val avEquipped: String?,
    val logos: JSONArray, val logosOwned: Set<String>
)

private fun idSet(arr: JSONArray?): Set<String> {
    val out = HashSet<String>()
    if (arr != null) for (i in 0 until arr.length()) {
        val v = arr.get(i)
        out.add(if (v is JSONObject) v.optString("id", v.optString("avatar_id", v.optString("logo_id"))) else v.toString())
    }
    return out
}

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
    val avatars = try { Api.publicGet("/rest/v1/avatar_catalog?select=id,title,theme,tier,cost_ap,asset&order=sort.asc") } catch (e: Exception) { JSONArray() }
    var avOwned = emptySet<String>(); var avEquipped: String? = null
    try {
        val ma = Api.authedRpc("my_avatars", JSONObject())
        if (ma.optBoolean("ok")) {
            avOwned = idSet(ma.optJSONArray("owned"))
            avEquipped = ma.optString("equipped").takeIf { it.isNotBlank() && it != "null" }
            // free avatars are always owned
            for (i in 0 until avatars.length()) {
                val a = avatars.getJSONObject(i)
                if (a.optInt("cost_ap", 1) == 0) avOwned = avOwned + a.s("id")
            }
        }
    } catch (e: Exception) { }
    val logos = try { Api.publicGet("/rest/v1/squad_logos?active=eq.true&select=id,title,image_url,tier,cost_ap&order=sort.asc") } catch (e: Exception) { JSONArray() }
    var logosOwned = emptySet<String>()
    try {
        val ml = Api.authedRpc("my_squad_logos", JSONObject())
        if (ml.optBoolean("ok")) logosOwned = idSet(ml.optJSONArray("logos"))
    } catch (e: Exception) { }
    return StoreData(balance, xp, catalog, mine, avatars, avOwned, avEquipped, logos, logosOwned)
}

private fun kindLabel(k: String) = when (k) {
    "avatar" -> "AVATAR"
    "team_logo" -> "TEAM LOGO"
    "avatar_frame" -> "AVATAR FRAME"
    "name_badge" -> "NAME BADGE"
    "title" -> "TITLE"
    else -> "REWARD"
}

/** Rewards Store - AP (Arena Points) are spendable here; XP is rank-only and shown read-only. Cosmetics are AP only, never cash. */
@Composable
fun StoreScreen(onBack: (() -> Unit)? = null) {
    val scope = rememberCoroutineScope()
    var notice by remember { mutableStateOf<String?>(null) }
    var claiming by remember { mutableStateOf<String?>(null) }
    var tab by remember { mutableStateOf("All") }
    var data by remember { mutableStateOf<Result<StoreData>?>(null) }
    var nonce by remember { mutableIntStateOf(0) }
    LaunchedEffect(nonce) { data = null; data = runCatching { loadStore() } }
    Column(Modifier.fillMaxSize()) {
        if (onBack != null) PageHeader("Store", "Spend AP on real rewards and cosmetics", onBack) else Brand()
        val res = data
        when {
            res == null -> Box(Modifier.fillMaxSize(), contentAlignment = Alignment.Center) { CircularProgressIndicator(color = Gold) }
            res.isFailure -> Column(Modifier.fillMaxSize().padding(24.dp), verticalArrangement = Arrangement.Center, horizontalAlignment = Alignment.CenterHorizontally) {
                Text(res.exceptionOrNull()?.message ?: "Could not load.", color = ErrRed); Spacer(Modifier.height(12.dp))
                GoldButton("Retry") { nonce++ }
            }
            else -> {
                val d = res.getOrThrow()
                LazyColumn {
                    item {
                        PanelCard {
                            Text("FREE - NO CASH", color = Color(0xFF4ADE80), fontSize = 11.sp, fontWeight = FontWeight.Bold)
                            Text("YOUR AP BALANCE", color = Muted, fontSize = 11.sp)
                            Text(d.balance?.let { "$it AP" } ?: "-", color = Gold2, fontSize = 28.sp, fontWeight = FontWeight.Black)
                            Text("Arena Points are spendable here. Earn them with check-ins, spins and quiz wins. Cosmetics are visual only.", color = Muted, fontSize = 12.sp)
                            if (d.xp != null) Text("Your XP: " + d.xp + " - rank points, not spendable", color = Gold, fontSize = 12.sp, fontWeight = FontWeight.Bold, modifier = Modifier.padding(top = 4.dp))
                        }
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

                    // ---- Avatars ----
                    if ((tab == "All" || tab == "Avatars") && d.avatars.length() > 0) {
                        item { Text("Avatars - equip on your profile", color = Gold, fontWeight = FontWeight.Bold, fontSize = 16.sp, modifier = Modifier.padding(horizontal = 16.dp, vertical = 6.dp)) }
                        val avList = (0 until d.avatars.length()).map { d.avatars.getJSONObject(it) }
                        items(avList.chunked(3)) { rowItems ->
                            Row(Modifier.fillMaxWidth().padding(horizontal = 10.dp), horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                                rowItems.forEach { a ->
                                    val id = a.s("id")
                                    val owned = d.avOwned.contains(id)
                                    val equipped = d.avEquipped == id
                                    val cost = a.optInt("cost_ap")
                                    Column(Modifier.weight(1f).clip(RoundedCornerShape(12.dp)).background(Color(0xFF1E0A3C))
                                        .padding(8.dp), horizontalAlignment = Alignment.CenterHorizontally) {
                                        coil.compose.AsyncImage(
                                            model = "https://xarenaprivate.github.io/xarena/assets/avatars/" + a.s("asset") + ".webp",
                                            contentDescription = a.s("title"),
                                            modifier = Modifier.size(64.dp).clip(CircleShape).background(Color(0xFF2A1450)))
                                        Spacer(Modifier.height(4.dp))
                                        Text(a.s("title"), color = Color.White, fontSize = 11.sp, fontWeight = FontWeight.Bold, maxLines = 1)
                                        Text(if (cost == 0) "FREE" else cost.toString() + " AP", color = Gold2, fontSize = 11.sp)
                                        Spacer(Modifier.height(4.dp))
                                        val label = when {
                                            equipped -> "EQUIPPED"
                                            owned -> "EQUIP"
                                            claiming == id -> "..."
                                            else -> "BUY"
                                        }
                                        val enabled = !equipped && claiming == null && (owned || d.balance == null || d.balance >= cost)
                                        Text(label, color = if (equipped) Color(0xFF4ADE80) else if (enabled) Color(0xFF2A1400) else Color(0x66FFFFFF),
                                            fontSize = 11.sp, fontWeight = FontWeight.Bold,
                                            modifier = Modifier.clip(RoundedCornerShape(8.dp))
                                                .background(if (equipped) Color(0x224ADE80) else if (enabled) Gold else Color(0x22FFFFFF))
                                                .clickable(enabled = enabled) {
                                                    claiming = id
                                                    scope.launch {
                                                        try {
                                                            val r = if (owned) Api.authedRpc("equip_avatar", JSONObject().put("p_id", id))
                                                            else Api.authedRpc("buy_avatar", JSONObject().put("p_id", id))
                                                            if (r.optBoolean("ok")) {
                                                                notice = if (owned) "Avatar equipped on your profile." else "Avatar is yours - tap EQUIP to wear it. Balance: " + r.optInt("balance") + " AP."
                                                                nonce++
                                                            } else notice = r.s("error").ifBlank { "Could not do that right now." }
                                                        } catch (e: Exception) { notice = e.message ?: "Network error - try again." }
                                                        claiming = null
                                                    }
                                                }.padding(horizontal = 12.dp, vertical = 5.dp))
                                    }
                                }
                                repeat(3 - rowItems.size) { Spacer(Modifier.weight(1f)) }
                            }
                            Spacer(Modifier.height(8.dp))
                        }
                    }

                    // ---- Team logos ----
                    if ((tab == "All" || tab == "Team Logos") && d.logos.length() > 0) {
                        item { Text("Team Logos - your captain equips one for the squad", color = Gold, fontWeight = FontWeight.Bold, fontSize = 16.sp, modifier = Modifier.padding(horizontal = 16.dp, vertical = 6.dp)) }
                        items((0 until d.logos.length()).map { d.logos.getJSONObject(it) }) { l ->
                            val id = l.s("id")
                            val owned = d.logosOwned.contains(id)
                            val cost = l.optInt("cost_ap")
                            PanelCard {
                                Row(Modifier.fillMaxWidth(), verticalAlignment = Alignment.CenterVertically) {
                                    coil.compose.AsyncImage(model = l.s("image_url"), contentDescription = l.s("title"),
                                        modifier = Modifier.size(56.dp).clip(RoundedCornerShape(10.dp)).background(Color(0xFF2A1450)))
                                    Spacer(Modifier.width(10.dp))
                                    Column(Modifier.weight(1f)) {
                                        Text(l.s("title"), color = Color.White, fontSize = 15.sp, fontWeight = FontWeight.Bold)
                                        Text(l.s("tier").uppercase(), color = Muted, fontSize = 11.sp)
                                    }
                                    Column(horizontalAlignment = Alignment.End) {
                                        Text(if (cost == 0) "FREE" else cost.toString() + " AP", color = Gold2, fontWeight = FontWeight.Bold)
                                        Spacer(Modifier.height(4.dp))
                                        val canAfford = d.balance == null || d.balance >= cost
                                        Text(if (owned) "OWNED" else if (claiming == id) "..." else "BUY",
                                            color = if (owned) Color(0xFF4ADE80) else if (canAfford) Color(0xFF2A1400) else Color(0x66FFFFFF),
                                            fontSize = 12.sp, fontWeight = FontWeight.Bold,
                                            modifier = Modifier.clip(RoundedCornerShape(8.dp))
                                                .background(if (owned) Color(0x224ADE80) else if (canAfford) Gold else Color(0x22FFFFFF))
                                                .clickable(enabled = !owned && canAfford && claiming == null) {
                                                    claiming = id
                                                    scope.launch {
                                                        try {
                                                            val r = Api.authedRpc("buy_squad_logo", JSONObject().put("p_logo", id))
                                                            notice = if (r.optBoolean("ok")) "Logo is yours - the squad captain can equip it in Squads. Balance: " + r.optInt("balance") + " AP."
                                                            else r.s("error").ifBlank { "Could not buy right now." }
                                                            if (r.optBoolean("ok")) nonce++
                                                        } catch (e: Exception) { notice = e.message ?: "Network error - try again." }
                                                        claiming = null
                                                    }
                                                }.padding(horizontal = 14.dp, vertical = 7.dp))
                                    }
                                }
                            }
                        }
                    }

                    // ---- rewards catalog (admin-fulfilled claims) ----
                    val tabKinds = mapOf("Avatar Frames" to "avatar_frame", "Name Badges" to "name_badge", "Titles" to "title")
                    val showCatalog = tab == "All" || tabKinds.containsKey(tab)
                    val shownItems = (0 until d.catalog.length()).map { d.catalog.getJSONObject(it) }
                        .filter { tab == "All" || it.s("kind") == tabKinds[tab] }
                    if (showCatalog && shownItems.isNotEmpty()) {
                        item { Text("Rewards - the owner reviews every claim", color = Gold, fontWeight = FontWeight.Bold, fontSize = 16.sp, modifier = Modifier.padding(horizontal = 16.dp, vertical = 6.dp)) }
                        items(shownItems) { it ->
                            PanelCard {
                                Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.SpaceBetween, verticalAlignment = Alignment.CenterVertically) {
                                    val img = it.s("image_url").ifBlank { it.s("logo_url") }
                                    if (img.isNotBlank()) {
                                        coil.compose.AsyncImage(model = xrImageModel(Api.ctx, img), contentDescription = it.s("title"),
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
                                        val cid = it.optLong("id").toString()
                                        val canAfford = d.balance != null && d.balance >= it.optInt("cost")
                                        Text(if (claiming == cid) "SENDING..." else "CLAIM",
                                            color = if (canAfford) Color(0xFF2A1400) else Color(0x66FFFFFF),
                                            fontSize = 12.sp, fontWeight = FontWeight.Bold,
                                            modifier = Modifier.clip(RoundedCornerShape(8.dp))
                                                .background(if (canAfford) Gold else Color(0x22FFFFFF))
                                                .clickable(enabled = canAfford && claiming == null) {
                                                    claiming = cid
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
                    }
                    if (showCatalog && d.catalog.length() == 0 && d.avatars.length() == 0 && d.logos.length() == 0) item {
                        EmptyNote("Catalog opens soon", "The owner is adding real rewards. Keep stacking XP - every check-in, spin and quiz win counts. XP is never cash.")
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
        }
        notice?.let { Text(it, color = Gold2, fontSize = 13.sp, modifier = Modifier.padding(horizontal = 16.dp, vertical = 4.dp)) }
    }
}
