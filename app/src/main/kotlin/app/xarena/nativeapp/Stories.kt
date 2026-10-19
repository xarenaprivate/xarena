package app.xarena.nativeapp

import android.graphics.BitmapFactory
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.foundation.background
import androidx.compose.foundation.border
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
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.compose.ui.viewinterop.AndroidView
import coil.compose.AsyncImage
import kotlinx.coroutines.delay
import kotlinx.coroutines.isActive
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import org.json.JSONArray
import org.json.JSONObject

private fun storyMediaUrl(st: JSONObject): String {
    val p = st.s("media_path").ifBlank { st.s("media") }
    if (p.startsWith("https://res.cloudinary.com/")) {
        val t = if (st.s("kind") == "clip") "/upload/q_auto/" else "/upload/q_auto,f_auto,w_1080/"
        return p.replace("/upload/", t)
    }
    return Api.BASE + "/storage/v1/object/public/feed-media/" + p
}

@Composable
fun StoriesScreen(onBack: () -> Unit) {
    val scope = rememberCoroutineScope()
    var tick by remember { mutableIntStateOf(0) }
    var trays by remember { mutableStateOf<JSONArray?>(null) }
    var err by remember { mutableStateOf<String?>(null) }
    var busy by remember { mutableStateOf(false) }
    var viewing by remember { mutableStateOf<Pair<String, String>?>(null) } // authorId, authorName

    LaunchedEffect(tick) { trays = runCatching { Api.storyTrays() }.getOrElse { err = it.message; null } }

    val picker = rememberLauncherForActivityResult(ActivityResultContracts.OpenDocument()) { uri ->
        if (uri == null) return@rememberLauncherForActivityResult
        busy = true
        scope.launch {
            try {
                val cr = Api.ctx.contentResolver
                val mime = cr.getType(uri) ?: "image/jpeg"
                var bytes = withContext(Dispatchers.IO) { cr.openInputStream(uri)!!.use { it.readBytes() } }
                val kind: String
                val url: String
                if (mime.startsWith("video/")) {
                    if (bytes.size > 8 * 1024 * 1024) throw Exception("Video too large - keep it under 8 MB.")
                    kind = "clip"
                    url = Api.cloudinaryUploadVideo(bytes, mime)
                } else {
                    var bmp = BitmapFactory.decodeByteArray(bytes, 0, bytes.size) ?: throw Exception("Could not read that image.")
                    if (maxOf(bmp.width, bmp.height) > 1280) {
                        val f = 1280f / maxOf(bmp.width, bmp.height)
                        bmp = android.graphics.Bitmap.createScaledBitmap(bmp, Math.round(bmp.width * f), Math.round(bmp.height * f), true)
                    }
                    val baos = java.io.ByteArrayOutputStream()
                    bmp.compress(android.graphics.Bitmap.CompressFormat.JPEG, 82, baos)
                    kind = "photo"
                    url = Api.cloudinaryUpload(baos.toByteArray())
                }
                val r = Api.storyCreate(kind, url, null)
                if (!r.optBoolean("ok")) throw Exception(r.s("error").ifBlank { "Could not post story." })
                tick++
            } catch (e: Exception) { err = e.message }
            busy = false
        }
    }

    Column(Modifier.fillMaxSize().background(Ink)) {
        PageHeader("Stories", "24-hour highlights from players", onBack)
        err?.let { Text(it, color = ErrRed, fontSize = 13.sp, modifier = Modifier.padding(horizontal = 16.dp)) }
        Box(Modifier.padding(horizontal = 16.dp, vertical = 4.dp)) {
            GoldButton(if (busy) "POSTING..." else "+ ADD YOUR STORY", enabled = !busy) { picker.launch(arrayOf("image/*", "video/*")) }
        }
        if (trays == null) Box(Modifier.fillMaxWidth().padding(24.dp), contentAlignment = Alignment.Center) { CircularProgressIndicator(color = Gold) }
        else if (trays!!.length() == 0) Text("No active stories. Be the first.", color = Muted, fontSize = 14.sp, modifier = Modifier.padding(24.dp))
        else LazyColumn {
            items((0 until trays!!.length()).map { trays!!.getJSONObject(it) }) { t ->
                val author = t.s("author").ifBlank { "Player" }
                Row(Modifier.fillMaxWidth().padding(horizontal = 16.dp, vertical = 5.dp).clip(RoundedCornerShape(14.dp))
                    .background(Card.copy(alpha = .85f)).border(1.dp, if (t.optBoolean("unseen")) Gold else Gold.copy(alpha = .2f), RoundedCornerShape(14.dp))
                    .clickable { viewing = t.s("author_id") to author }.padding(12.dp), verticalAlignment = Alignment.CenterVertically) {
                    Box(Modifier.size(44.dp).clip(CircleShape).background(Ink).border(2.dp, if (t.optBoolean("unseen")) Gold else Muted, CircleShape), contentAlignment = Alignment.Center) {
                        Text(author.firstOrNull()?.uppercase() ?: "?", color = Gold, fontWeight = FontWeight.Bold)
                    }
                    Spacer(Modifier.width(12.dp))
                    Column {
                        Text(author + if (t.optBoolean("mine")) " (you)" else "", color = Color.White, fontSize = 15.sp, fontWeight = FontWeight.SemiBold)
                        Text(if (t.optBoolean("unseen")) "New story" else "Seen", color = Muted, fontSize = 12.sp)
                    }
                }
            }
        }
    }
    viewing?.let { (authorId, authorName) ->
        StoryViewer(authorId, authorName) { viewing = null; tick++ }
    }
}

@Composable
private fun StoryViewer(authorId: String, authorName: String, onClose: () -> Unit) {
    androidx.activity.compose.BackHandler { onClose() }
    var stories by remember { mutableStateOf<JSONArray?>(null) }
    var idx by remember { mutableIntStateOf(0) }
    var err by remember { mutableStateOf<String?>(null) }
    var deleting by remember { mutableStateOf(false) }
    val scope = rememberCoroutineScope()
    LaunchedEffect(deleting) {
        if (!deleting) return@LaunchedEffect
        val st = stories?.getJSONObject(idx) ?: return@LaunchedEffect
        runCatching { Api.storyDelete(st.getLong("id")) }
        deleting = false
        onClose()
    }
    LaunchedEffect(authorId) {
        val r = runCatching { Api.storyList(authorId) }.getOrElse { err = it.message; null }
        val arr = r?.optJSONArray("stories")
        if (arr == null || arr.length() == 0) { err = "No active stories."; return@LaunchedEffect }
        stories = arr
    }
    val count = stories?.length() ?: 0
    LaunchedEffect(idx, count) {
        if (count == 0) return@LaunchedEffect
        val st = stories!!.getJSONObject(idx)
        runCatching { Api.storyMarkSeen(st.getLong("id")) }
        delay(if (st.s("kind") == "clip") 15000 else 5000)
        if (isActive) { if (idx + 1 < count) idx++ else onClose() }
    }
    Box(Modifier.fillMaxSize().background(Color.Black).clickable { if (idx + 1 < count) idx++ else onClose() }) {
        stories?.let { arr ->
            val st = arr.getJSONObject(idx)
            if (st.s("kind") == "clip") {
                AndroidView(factory = { ctx ->
                    android.widget.VideoView(ctx).apply {
                        setVideoPath(storyMediaUrl(st))
                        setOnPreparedListener { mp -> mp.start() }
                        setOnErrorListener { _, _, _ -> err = "Could not play this clip."; true }
                    }
                }, modifier = Modifier.fillMaxSize())
            } else {
                AsyncImage(model = storyMediaUrl(st), contentDescription = "story", modifier = Modifier.fillMaxSize(), contentScale = ContentScale.Fit)
            }
            Row(Modifier.fillMaxWidth().padding(8.dp).align(Alignment.TopCenter)) {
                for (i in 0 until count) Box(Modifier.weight(1f).height(3.dp).padding(horizontal = 2.dp).clip(RoundedCornerShape(2.dp)).background(if (i <= idx) Gold else Color.White.copy(alpha = .3f)))
            }
            Column(Modifier.align(Alignment.TopStart).padding(top = 20.dp, start = 14.dp)) {
                Text(authorName, color = Color.White, fontSize = 15.sp, fontWeight = FontWeight.Bold)
                Text(st.s("caption"), color = Color.White.copy(alpha = .85f), fontSize = 13.sp)
            }
            if (authorId == Api.uid) Column(Modifier.align(Alignment.BottomStart).padding(14.dp)) {
                var viewers by remember { mutableStateOf<JSONArray?>(null) }
                var showViewers by remember { mutableStateOf(false) }
                Text("Viewers", color = Gold, fontSize = 13.sp, fontWeight = FontWeight.Bold, modifier = Modifier.clip(RoundedCornerShape(6.dp)).background(Color.Black.copy(alpha = .5f)).clickable {
                    showViewers = !showViewers
                    if (showViewers && viewers == null) {
                        runCatching { }
                        viewers = null
                    }
                }.padding(8.dp))
                LaunchedEffect(showViewers, idx) {
                    if (showViewers) viewers = runCatching {
                        Api.authedRpc("story_viewers", JSONObject().put("p_story", st.getLong("id"))).optJSONArray("viewers")
                    }.getOrNull()
                }
                if (showViewers) {
                    if (viewers == null) Text("Loading...", color = Color.White, fontSize = 12.sp)
                    else if (viewers!!.length() == 0) Text("No views yet.", color = Color.White, fontSize = 12.sp)
                    else for (i in 0 until viewers!!.length()) {
                        val v = viewers!!.getJSONObject(i)
                        Text(v.s("name").ifBlank { v.s("viewer").ifBlank { "Player" } }, color = Color.White, fontSize = 12.sp)
                    }
                }
                Text("Delete story", color = ErrRed, fontSize = 13.sp, fontWeight = FontWeight.Bold, modifier = Modifier.clip(RoundedCornerShape(6.dp)).background(Color.Black.copy(alpha = .5f)).clickable {
                    runCatching { }
                    deleting = true
                }.padding(8.dp))
            }
        }
        err?.let { Text(it, color = ErrRed, fontSize = 14.sp, modifier = Modifier.align(Alignment.Center).padding(24.dp)) }
        if (stories == null && err == null) CircularProgressIndicator(color = Gold, modifier = Modifier.align(Alignment.Center))
        Text("Close", color = Color.White, fontSize = 13.sp, modifier = Modifier.align(Alignment.TopEnd).padding(14.dp).clip(RoundedCornerShape(6.dp)).background(Color.Black.copy(alpha = .5f)).clickable { onClose() }.padding(8.dp))
    }
}
