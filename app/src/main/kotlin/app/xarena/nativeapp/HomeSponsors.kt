package app.xarena.nativeapp

import android.content.Intent
import android.net.Uri
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyRow
import androidx.compose.foundation.lazy.rememberLazyListState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.Text
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import coil.compose.AsyncImage
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.delay
import kotlinx.coroutines.withContext
import org.json.JSONObject

/** Sponsor poster carousel for Home. Source: the same banner_list_public feed the website home
    carousel uses; falls back to approved Sponsor Wall posters (sponsors table) when no home
    banners are live - so posters the owner already uploaded still show. Renders nothing when
    both are empty: no dummy sponsors, no placeholder. Posters load from the network, never bundled. */
@Composable
fun HomeSponsorCarousel() {
    var banners by remember { mutableStateOf<List<JSONObject>?>(null) }
    LaunchedEffect(Unit) {
        banners = withContext(Dispatchers.IO) {
            runCatching {
                val list = mutableListOf<JSONObject>()
                val arr = runCatching { Api.publicRpc("banner_list_public", JSONObject()).optJSONArray("banners") }.getOrNull()
                if (arr != null) for (i in 0 until arr.length()) {
                    val b = arr.getJSONObject(i)
                    val img = b.s("image").ifBlank { b.s("image_url") }
                    if (img.isNotBlank()) list.add(JSONObject()
                        .put("image", img).put("title", b.s("title"))
                        .put("link", b.s("link").ifBlank { b.s("url") }))
                }
                if (list.isEmpty()) {
                    val rows = Api.publicGet("/rest/v1/sponsors?active=eq.true&image_approved=eq.true&select=name,image_url,url&order=id.asc")
                    for (i in 0 until rows.length()) {
                        val b = rows.getJSONObject(i)
                        if (b.s("image_url").isNotBlank()) list.add(JSONObject()
                            .put("image", b.s("image_url")).put("title", b.s("name")).put("link", b.s("url")))
                    }
                }
                list.toList()
            }.getOrNull()
        }
    }
    val list = banners
    if (!list.isNullOrEmpty()) SponsorCarouselContent(list)
}

@Composable
internal fun SponsorCarouselContent(banners: List<JSONObject>) {
    if (banners.isEmpty()) return
    val ctx = LocalContext.current
    val listState = rememberLazyListState()
    if (banners.size > 1) {
        LaunchedEffect(banners.size) {
            while (true) {
                delay(4000)
                val next = (listState.firstVisibleItemIndex + 1) % banners.size
                runCatching { listState.animateScrollToItem(next) }
            }
        }
    }
    Box(Modifier.fillMaxWidth().padding(horizontal = 16.dp, vertical = 6.dp)
        .clip(RoundedCornerShape(Look.cardRadius))
        .border(1.dp, Look.cardBorder, RoundedCornerShape(Look.cardRadius))) {
        LazyRow(state = listState, modifier = Modifier.fillMaxWidth()) {
            items(banners.size) { i ->
                val b = banners[i]
                Box(Modifier.fillParentMaxWidth().height(150.dp)
                    .clickable(enabled = b.s("link").isNotBlank()) {
                        val l = b.s("link")
                        val url = if (l.startsWith("http")) l else "https://xarenaprivate.github.io/xarena/" + l
                        runCatching { ctx.startActivity(Intent(Intent.ACTION_VIEW, Uri.parse(url))) }
                    }) {
                    AsyncImage(model = xrImageModel(ctx, b.s("image")), contentDescription = b.s("title").ifBlank { "Sponsor poster" },
                        modifier = Modifier.fillMaxSize().background(Ink), contentScale = ContentScale.Fit)
                    if (b.s("title").isNotBlank()) Text(b.s("title"), color = Color.White, fontSize = 13.sp, fontWeight = FontWeight.Bold,
                        modifier = Modifier.align(Alignment.BottomStart).fillMaxWidth().background(Color(0x73000000)).padding(horizontal = 12.dp, vertical = 6.dp))
                }
            }
        }
        if (banners.size > 1) Row(Modifier.align(Alignment.BottomEnd).padding(10.dp), horizontalArrangement = Arrangement.spacedBy(5.dp)) {
            banners.forEachIndexed { i, _ ->
                Box(Modifier.size(6.dp).clip(CircleShape).background(if (i == listState.firstVisibleItemIndex) Color.White else Color(0x66FFFFFF)))
            }
        }
    }
}
