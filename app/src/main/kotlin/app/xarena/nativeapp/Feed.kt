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
import androidx.compose.foundation.text.BasicTextField
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.text.TextStyle
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import coil.compose.AsyncImage
import kotlinx.coroutines.launch
import org.json.JSONArray
import org.json.JSONObject

fun feedMediaUrl(p: JSONObject): String? {
    val path = p.optString("media_path")
    if (path.isBlank()) return null
    if (path.startsWith("https://res.cloudinary.com/")) {
        val t = if (p.optString("kind") == "clip") "/upload/q_auto/" else "/upload/q_auto,f_auto,w_1080/"
        return path.replace("/upload/", t)
    }
    return Api.BASE + "/storage/v1/object/feed-media/" + path
}

@Composable
fun FeedScreen(onBack: () -> Unit) {
    val scope = rememberCoroutineScope()
    var posts by remember { mutableStateOf<List<JSONObject>?>(null) }
    var err by remember { mutableStateOf<String?>(null) }
    var savedMode by remember { mutableStateOf(false) }
    var loadingMore by remember { mutableStateOf(false) }
    var hasMore by remember { mutableStateOf(false) }
    var tick by remember { mutableIntStateOf(0) }

    fun load(reset: Boolean) {
        scope.launch {
            try {
                val before = if (reset) null else posts?.lastOrNull()?.optLong("id")?.takeIf { it > 0 }
                val arr = if (savedMode) Api.feedSaved() else Api.feedList(before)
                val list = (0 until arr.length()).map { arr.getJSONObject(it) }
                posts = if (reset || savedMode) list else (posts ?: emptyList()) + list
                hasMore = !savedMode && list.size >= 20
                err = null
            } catch (e: Exception) { err = e.message }
        }
    }
    LaunchedEffect(tick, savedMode) { load(true) }

    var showCompose by remember { mutableStateOf(false) }

    Column(Modifier.fillMaxSize().background(Ink)) {
        PageHeader("Feed", "Player highlights", onBack)
        Row(Modifier.fillMaxWidth().padding(horizontal = 16.dp), verticalAlignment = Alignment.CenterVertically) {
            Text(if (savedMode) "Saved" else "Latest", color = Muted, fontSize = 13.sp, modifier = Modifier.weight(1f))
            Text(if (savedMode) "Show latest" else "Saved posts", color = Gold, fontSize = 13.sp, fontWeight = FontWeight.Bold,
                modifier = Modifier.clip(RoundedCornerShape(8.dp)).clickable { savedMode = !savedMode }.padding(8.dp))
        }
        Box(Modifier.padding(horizontal = 16.dp, vertical = 4.dp)) { GoldButton("+ POST A HIGHLIGHT") { showCompose = true } }
        err?.let { Text(it, color = ErrRed, fontSize = 13.sp, modifier = Modifier.padding(16.dp)) }
        if (posts == null) Box(Modifier.fillMaxWidth().padding(24.dp), contentAlignment = Alignment.Center) { CircularProgressIndicator(color = Gold) }
        else if (posts!!.isEmpty()) Text(if (savedMode) "Nothing saved yet. Tap the bookmark on any post." else "No highlights yet. Be the first - post your best Free Fire moment.", color = Muted, fontSize = 14.sp, modifier = Modifier.padding(24.dp))
        else LazyColumn {
            items(posts!!) { p -> PostCard(p, onChanged = { tick++ }) }
            if (hasMore) item {
                Text(if (loadingMore) "Loading..." else "Load more", color = Gold, fontSize = 14.sp, fontWeight = FontWeight.Bold,
                    modifier = Modifier.fillMaxWidth().clickable { loadingMore = true; load(false); loadingMore = false }.padding(16.dp))
            }
        }
    }
    if (showCompose) ComposePostDialog(onDone = { posted -> showCompose = false; if (posted) tick++ })
}

@Composable
private fun PostCard(p: JSONObject, onChanged: () -> Unit) {
    val scope = rememberCoroutineScope()
    var liked by remember { mutableStateOf(p.optBoolean("liked")) }
    var likes by remember { mutableIntStateOf(p.optInt("likes")) }
    var saved by remember { mutableStateOf(p.optBoolean("saved")) }
    var comments by remember { mutableStateOf<JSONArray?>(null) }
    var commentsOpen by remember { mutableStateOf(false) }
    var commentCount by remember { mutableIntStateOf(p.optInt("comments")) }
    var input by remember { mutableStateOf("") }
    var err by remember { mutableStateOf<String?>(null) }

    SmallCard {
        Row(verticalAlignment = Alignment.CenterVertically) {
            Box(Modifier.size(36.dp).clip(CircleShape).background(Ink).border(1.dp, Gold.copy(alpha = .4f), CircleShape), contentAlignment = Alignment.Center) {
                Text(p.optString("author").firstOrNull()?.uppercase() ?: "?", color = Gold, fontWeight = FontWeight.Bold, fontSize = 14.sp)
            }
            Spacer(Modifier.width(10.dp))
            Column {
                Text(p.optString("author"), color = Gold2, fontSize = 14.sp, fontWeight = FontWeight.SemiBold)
                Text(dmTime(p.optString("at")) + if (p.optString("kind") == "clip") " - clip" else if (p.optString("kind") == "photo") " - photo" else "", color = Muted, fontSize = 11.sp)
            }
        }
        feedMediaUrl(p)?.let { url ->
            Spacer(Modifier.height(8.dp))
            if (p.optString("kind") == "clip") {
                Box(Modifier.fillMaxWidth().height(220.dp).clip(RoundedCornerShape(12.dp)).background(Color.Black), contentAlignment = Alignment.Center) {
                    androidx.compose.ui.viewinterop.AndroidView(factory = { ctx ->
                        android.widget.VideoView(ctx).apply { setVideoPath(url); setOnPreparedListener { it.start() } }
                    }, modifier = Modifier.fillMaxSize())
                }
            } else {
                AsyncImage(model = url, contentDescription = p.optString("caption"), modifier = Modifier.fillMaxWidth().heightIn(max = 420.dp).clip(RoundedCornerShape(12.dp)), contentScale = ContentScale.Crop)
            }
        }
        if (likes > 0) Text(likes.toString() + if (likes == 1) " like" else " likes", color = Color.White, fontSize = 13.sp, fontWeight = FontWeight.SemiBold, modifier = Modifier.padding(top = 8.dp))
        Row(Modifier.padding(top = 6.dp), verticalAlignment = Alignment.CenterVertically) {
            Text(if (liked) "❤️" else "🤍", fontSize = 18.sp, modifier = Modifier.clickable {
                liked = !liked; likes += if (liked) 1 else -1
                scope.launch { try { val r = Api.feedToggleLike(p.getLong("id")); if (r.has("liked")) { liked = r.getBoolean("liked"); likes = r.optInt("likes", likes) } } catch (e: Exception) { } }
            }.padding(4.dp))
            Spacer(Modifier.width(10.dp))
            Text("💬 " + commentCount, fontSize = 15.sp, modifier = Modifier.clickable {
                commentsOpen = !commentsOpen
                if (commentsOpen && comments == null) scope.launch { comments = runCatching { Api.feedComments(p.getLong("id")) }.getOrNull() }
            }.padding(4.dp))
            Spacer(Modifier.width(10.dp))
            Text(if (saved) "🔖" else "📑", fontSize = 15.sp, modifier = Modifier.clickable {
                saved = !saved
                scope.launch { try { val r = Api.feedToggleSave(p.getLong("id")); if (r.has("saved")) saved = r.getBoolean("saved") } catch (e: Exception) { } }
            }.padding(4.dp))
        }
        if (p.optString("caption").isNotBlank()) {
            Text(p.optString("caption"), color = Color.White, fontSize = 13.sp, modifier = Modifier.padding(top = 2.dp))
        }
        if (commentCount > 0 && !commentsOpen) Text("View all " + commentCount + " comments", color = Muted, fontSize = 12.sp, modifier = Modifier.clickable {
            commentsOpen = true
            if (comments == null) scope.launch { comments = runCatching { Api.feedComments(p.getLong("id")) }.getOrNull() }
        }.padding(top = 4.dp))
        if (commentsOpen) {
            Spacer(Modifier.height(6.dp))
            if (comments == null) CircularProgressIndicator(color = Gold, modifier = Modifier.size(20.dp))
            else {
                if (comments!!.length() == 0) Text("No comments yet - say something nice.", color = Muted, fontSize = 12.sp)
                for (i in 0 until comments!!.length()) {
                    val c = comments!!.getJSONObject(i)
                    Row(Modifier.padding(vertical = 2.dp)) {
                        Text(c.optString("author") + "  ", color = Gold2, fontSize = 12.5.sp, fontWeight = FontWeight.Bold)
                        Text(c.optString("body"), color = Color.White, fontSize = 12.5.sp)
                    }
                }
            }
            Row(Modifier.fillMaxWidth().padding(top = 6.dp), verticalAlignment = Alignment.CenterVertically) {
                BasicTextField(input, { input = it }, modifier = Modifier.weight(1f).clip(RoundedCornerShape(8.dp)).background(Ink).padding(8.dp),
                    textStyle = TextStyle(color = Color.White, fontSize = 12.5.sp),
                    decorationBox = { inner -> if (input.isEmpty()) Text("Add a comment...", color = Muted, fontSize = 12.5.sp); inner() })
                Spacer(Modifier.width(6.dp))
                Text("Post", color = Gold, fontSize = 12.5.sp, fontWeight = FontWeight.Bold, modifier = Modifier.clickable {
                    val v = input.trim(); if (v.isEmpty()) return@clickable
                    input = ""
                    scope.launch {
                        try {
                            val r = Api.feedComment(p.getLong("id"), v)
                            if (r.optBoolean("ok")) { commentCount++; comments = Api.feedComments(p.getLong("id")) }
                            else err = r.optString("error")
                        } catch (e: Exception) { err = e.message }
                    }
                }.padding(6.dp))
            }
        }
        err?.let { Text(it, color = ErrRed, fontSize = 11.sp) }
    }
}

@Composable
private fun ComposePostDialog(onDone: (Boolean) -> Unit) {
    val scope = rememberCoroutineScope()
    var caption by remember { mutableStateOf("") }
    var picked by remember { mutableStateOf<android.net.Uri?>(null) }
    var busy by remember { mutableStateOf(false) }
    var msg by remember { mutableStateOf<String?>(null) }
    val picker = rememberLauncherForActivityResult(ActivityResultContracts.OpenDocument()) { picked = it }

    androidx.compose.ui.window.Dialog(onDismissRequest = { if (!busy) onDone(false) }) {
        Column(Modifier.fillMaxWidth().clip(RoundedCornerShape(16.dp)).background(Card).padding(18.dp)) {
            Text("Post a highlight", color = Gold2, fontSize = 17.sp, fontWeight = FontWeight.Bold)
            Spacer(Modifier.height(10.dp))
            BasicTextField(caption, { caption = it }, modifier = Modifier.fillMaxWidth().clip(RoundedCornerShape(10.dp)).background(Ink).padding(12.dp),
                textStyle = TextStyle(color = Color.White, fontSize = 14.sp), maxLines = 4,
                decorationBox = { inner -> if (caption.isEmpty()) Text("Caption (optional)...", color = Muted, fontSize = 14.sp); inner() })
            Spacer(Modifier.height(10.dp))
            Text(if (picked == null) "+ Add photo or short video" else "Media attached ✓", color = Gold, fontSize = 13.sp, fontWeight = FontWeight.Bold,
                modifier = Modifier.clickable { picker.launch(arrayOf("image/*", "video/*")) }.padding(6.dp))
            msg?.let { Text(it, color = ErrRed, fontSize = 12.sp) }
            Spacer(Modifier.height(10.dp))
            Row {
                Text(if (busy) "Posting..." else "POST", color = Ink, fontWeight = FontWeight.Bold, fontSize = 14.sp,
                    modifier = Modifier.clip(RoundedCornerShape(10.dp)).background(Gold).clickable {
                        if (busy) return@clickable
                        if (picked == null && caption.isBlank()) { msg = "Add a photo, video or caption first."; return@clickable }
                        busy = true
                        scope.launch {
                            try {
                                var kind = "text"
                                var mediaPath: String? = null
                                picked?.let { uri ->
                                    val cr = Api.ctx.contentResolver
                                    val mime = cr.getType(uri) ?: "image/jpeg"
                                    var bytes = cr.openInputStream(uri)!!.use { it.readBytes() }
                                    if (mime.startsWith("video/")) {
                                        if (bytes.size > 8 * 1024 * 1024) throw Exception("Video too large - keep it under 8 MB.")
                                        kind = "clip"
                                        mediaPath = Api.cloudinaryUploadVideo(bytes, mime)
                                    } else {
                                        if (bytes.size > 5 * 1024 * 1024) {
                                            var bmp = BitmapFactory.decodeByteArray(bytes, 0, bytes.size) ?: throw Exception("Could not read that image.")
                                            if (maxOf(bmp.width, bmp.height) > 1280) {
                                                val f = 1280f / maxOf(bmp.width, bmp.height)
                                                bmp = android.graphics.Bitmap.createScaledBitmap(bmp, Math.round(bmp.width * f), Math.round(bmp.height * f), true)
                                            }
                                            val baos = java.io.ByteArrayOutputStream()
                                            bmp.compress(android.graphics.Bitmap.CompressFormat.JPEG, 82, baos)
                                            bytes = baos.toByteArray()
                                        }
                                        kind = "photo"
                                        val ext = mime.substringAfter("/", "jpg")
                                        mediaPath = Api.feedMediaUpload(bytes, ext, mime)
                                    }
                                }
                                val r = Api.feedCreate(kind, mediaPath, caption)
                                if (!r.optBoolean("ok")) throw Exception(r.optString("error").ifBlank { "Could not post." })
                                r.optLong("id").takeIf { it > 0 }?.let { pid ->
                                    scope.launch { runCatching { Api.authed("POST", "/functions/v1/feed-moderate", JSONObject().put("post_id", pid)) } }
                                }
                                onDone(true)
                            } catch (e: Exception) { msg = e.message; busy = false }
                        }
                    }.padding(horizontal = 18.dp, vertical = 10.dp))
                Spacer(Modifier.width(12.dp))
                Text("Cancel", color = Muted, fontSize = 14.sp, modifier = Modifier.clickable { if (!busy) onDone(false) }.padding(10.dp))
            }
        }
    }
}
