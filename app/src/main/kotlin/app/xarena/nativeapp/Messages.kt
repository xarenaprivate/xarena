package app.xarena.nativeapp

import android.graphics.BitmapFactory
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.foundation.Image
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.lazy.rememberLazyListState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.text.BasicTextField
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.asImageBitmap
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.compose.ui.window.Dialog
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.delay
import kotlinx.coroutines.isActive
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import org.json.JSONArray
import org.json.JSONObject
import java.text.SimpleDateFormat
import java.util.Date
import java.util.Locale
import java.util.TimeZone

fun dmTime(iso: String): String { return try {
    val p = SimpleDateFormat("yyyy-MM-dd'T'HH:mm:ss", Locale.US).apply { timeZone = TimeZone.getTimeZone("UTC") }
    val d = p.parse(iso.substring(0, 19)) ?: return ""
    val now = System.currentTimeMillis()
    val diff = now - d.time
    val out = SimpleDateFormat(if (diff > 20 * 3600_000L) "d MMM" else "h:mm a", Locale.getDefault())
    out.format(Date(d.time))
} catch (e: Exception) { "" } }

@Composable
private fun DmHeader(title: String, sub: String, onBack: () -> Unit) {
    Row(Modifier.fillMaxWidth().padding(horizontal = 8.dp, vertical = 8.dp), verticalAlignment = Alignment.CenterVertically) {
        Text("< Back", color = Gold, fontSize = 14.sp, modifier = Modifier.clip(RoundedCornerShape(6.dp)).clickable { onBack() }.padding(8.dp))
        Spacer(Modifier.width(8.dp))
        Column {
            Text(title, color = Gold2, fontSize = 18.sp, fontWeight = FontWeight.Bold)
            Text(sub, color = Muted, fontSize = 11.sp)
        }
    }
}

@Composable
private fun DmAvatar(name: String) {
    Box(Modifier.size(42.dp).clip(CircleShape).background(Card).border(1.dp, Gold.copy(alpha = .4f), CircleShape), contentAlignment = Alignment.Center) {
        Text(name.firstOrNull()?.uppercase() ?: "?", color = Gold, fontWeight = FontWeight.Bold, fontSize = 16.sp)
    }
}

/* ============ Messages home: Chats / Friends / Requests ============ */
@Composable
fun MessagesScreen(onBack: () -> Unit, onOpenConv: (String, String, String, String, Boolean) -> Unit) {
    var seg by remember { mutableIntStateOf(0) }
    var tick by remember { mutableIntStateOf(0) }
    var threads by remember { mutableStateOf<JSONArray?>(null) }
    var msgReqs by remember { mutableStateOf<JSONArray?>(null) }
    var friends by remember { mutableStateOf<JSONArray?>(null) }
    var incReqs by remember { mutableStateOf<JSONArray?>(null) }
    var outReqs by remember { mutableStateOf<JSONArray?>(null) }
    var names by remember { mutableStateOf<Map<String, String>>(emptyMap()) }
    var err by remember { mutableStateOf<String?>(null) }
    val scope = rememberCoroutineScope()

    fun reload() {
        scope.launch {
            err = null
            try {
                Api.dmPublishKey(DmCrypto.myPublicJwk())
                val th = Api.dmThreads()
                val mr = Api.dmMessageRequests()
                val fr = Api.dmFriends()
                val inc = Api.dmRequestsIncoming()
                val out = Api.dmRequestsOutgoing()
                val ids = LinkedHashSet<String>()
                for (i in 0 until th.length()) { val t = th.getJSONObject(i); ids.add(if (t.getString("user_a") == Api.uid) t.getString("user_b") else t.getString("user_a")) }
                for (i in 0 until fr.length()) ids.add(fr.getJSONObject(i).getString("friend_id"))
                for (i in 0 until inc.length()) ids.add(inc.getJSONObject(i).getString("from_user"))
                for (i in 0 until out.length()) ids.add(out.getJSONObject(i).getString("to_user"))
                for (i in 0 until mr.length()) ids.add(mr.getJSONObject(i).getString("requested_by"))
                names = Api.dmNames(ids.toList())
                threads = th; msgReqs = mr; friends = fr; incReqs = inc; outReqs = out
            } catch (e: Exception) { err = e.message ?: "Could not load." }
        }
    }
    LaunchedEffect(tick) { reload() }
    LaunchedEffect(Unit) { while (isActive) { delay(15000); reload() } }

    val reqCount = (incReqs?.length() ?: 0) + (msgReqs?.length() ?: 0)
    Column(Modifier.fillMaxSize().background(Ink)) {
        DmHeader("Messages", "End-to-end encrypted - only you two can read", onBack)
        Row(Modifier.fillMaxWidth().padding(horizontal = 16.dp)) {
            listOf("Chats", "Friends", "Requests" + if (reqCount > 0) " (" + reqCount + ")" else "").forEachIndexed { i, label ->
                val on = seg == i
                Text(label, color = if (on) Ink else Muted, fontSize = 13.sp, fontWeight = if (on) FontWeight.Bold else FontWeight.Medium,
                    modifier = Modifier.padding(end = 8.dp).clip(RoundedCornerShape(8.dp))
                        .background(if (on) Gold else Card).clickable { seg = i }.padding(horizontal = 14.dp, vertical = 7.dp))
            }
        }
        Spacer(Modifier.height(8.dp))
        err?.let { Text(it, color = ErrRed, fontSize = 13.sp, modifier = Modifier.padding(16.dp)) }
        when (seg) {
            0 -> ChatsPane(threads, names) { id, peer, st, reqByMe -> onOpenConv(id, peer, names[peer] ?: "Player", st, reqByMe) }
            1 -> FriendsPane(friends, names, { reload() }, { fid ->
                scope.launch {
                    try {
                        val r = Api.dmOpen(fid)
                        if (r.optBoolean("ok")) onOpenConv(r.getString("thread"), fid, names[fid] ?: "Player", "open", false)
                        else err = r.optString("error").ifBlank { "Could not open chat." }
                    } catch (e: Exception) { err = e.message }
                } })
            else -> RequestsPane(msgReqs, incReqs, outReqs, names, { reload() }, { id, peer -> onOpenConv(id, peer, names[peer] ?: "Player", "requested", false) })
        }
    }
}

@Composable
private fun ChatsPane(threads: JSONArray?, names: Map<String, String>, open: (String, String, String, Boolean) -> Unit) {
    if (threads == null) { Box(Modifier.fillMaxSize(), contentAlignment = Alignment.Center) { CircularProgressIndicator(color = Gold) }; return }
    val me = Api.uid
    val open2 = (0 until threads.length()).map { threads.getJSONObject(it) }.filter { it.optString("status") != "requested" }
    val mine = (0 until threads.length()).map { threads.getJSONObject(it) }.filter { it.optString("status") == "requested" && it.optString("requested_by") == me }
    LazyColumn(Modifier.fillMaxSize().padding(horizontal = 16.dp)) {
        if (open2.isEmpty() && mine.isEmpty()) item { Text("No chats yet. Add a friend and say hello.", color = Muted, fontSize = 14.sp, modifier = Modifier.padding(24.dp)) }
        items(open2) { t ->
            val peer = if (t.getString("user_a") == me) t.getString("user_b") else t.getString("user_a")
            val nm = names[peer] ?: "Player"
            Row(Modifier.fillMaxWidth().padding(vertical = 5.dp).clip(RoundedCornerShape(14.dp)).background(Card.copy(alpha = .85f))
                .border(1.dp, Gold.copy(alpha = .25f), RoundedCornerShape(14.dp)).clickable { open(t.getString("id"), peer, "open", false) }.padding(12.dp),
                verticalAlignment = Alignment.CenterVertically) {
                DmAvatar(nm); Spacer(Modifier.width(12.dp))
                Column(Modifier.weight(1f)) {
                    Text(nm, color = Color.White, fontSize = 15.sp, fontWeight = FontWeight.SemiBold)
                    Text("Encrypted" + if (t.optBoolean("locked")) " - locked" else "", color = Muted, fontSize = 12.sp)
                }
                Text(dmTime(t.optString("created_at")), color = Muted, fontSize = 11.sp)
            }
        }
        if (mine.isNotEmpty()) {
            item { Text("REQUESTS SENT", color = Muted, fontSize = 11.sp, fontWeight = FontWeight.Bold, modifier = Modifier.padding(vertical = 10.dp)) }
            items(mine) { t ->
                val peer = if (t.getString("user_a") == me) t.getString("user_b") else t.getString("user_a")
                val nm = names[peer] ?: "Player"
                Row(Modifier.fillMaxWidth().padding(vertical = 5.dp).clip(RoundedCornerShape(14.dp)).background(Card.copy(alpha = .6f))
                    .clickable { open(t.getString("id"), peer, "requested", true) }.padding(12.dp), verticalAlignment = Alignment.CenterVertically) {
                    DmAvatar(nm); Spacer(Modifier.width(12.dp))
                    Column {
                        Text(nm, color = Color.White, fontSize = 15.sp)
                        Text("Request sent - waiting for them to accept", color = Muted, fontSize = 12.sp)
                    }
                }
            }
        }
    }
}

@Composable
private fun FriendsPane(friends: JSONArray?, names: Map<String, String>, changed: () -> Unit, openFriend: (String) -> Unit) {
    var q by remember { mutableStateOf("") }
    var results by remember { mutableStateOf<JSONArray?>(null) }
    var msg by remember { mutableStateOf<String?>(null) }
    val scope = rememberCoroutineScope()
    LazyColumn(Modifier.fillMaxSize().padding(horizontal = 16.dp)) {
        item {
            Row(Modifier.fillMaxWidth().padding(vertical = 8.dp), verticalAlignment = Alignment.CenterVertically) {
                BasicTextField(q, { q = it }, modifier = Modifier.weight(1f).clip(RoundedCornerShape(10.dp)).background(Card).padding(12.dp),
                    textStyle = androidx.compose.ui.text.TextStyle(color = Color.White, fontSize = 14.sp),
                    decorationBox = { inner -> if (q.isEmpty()) Text("Search players by name or IGN", color = Muted, fontSize = 14.sp); inner() })
                Spacer(Modifier.width(8.dp))
                Text("Search", color = Ink, fontWeight = FontWeight.Bold, fontSize = 13.sp,
                    modifier = Modifier.clip(RoundedCornerShape(10.dp)).background(Gold).clickable {
                        if (q.trim().length < 2) { msg = "Type at least 2 letters."; return@clickable }
                        scope.launch {
                            try { results = Api.dmSearchPlayers(q.trim()); msg = null }
                            catch (e: Exception) { msg = e.message }
                        }
                    }.padding(horizontal = 14.dp, vertical = 10.dp))
            }
            msg?.let { Text(it, color = ErrRed, fontSize = 12.sp) }
        }
        results?.let { rs ->
            item { Text("RESULTS", color = Muted, fontSize = 11.sp, fontWeight = FontWeight.Bold, modifier = Modifier.padding(vertical = 6.dp)) }
            items((0 until rs.length()).map { rs.getJSONObject(it) }.filter { it.getString("id") != Api.uid }) { x ->
                val id = x.getString("id")
                val nm = x.optString("ign").ifBlank { x.optString("username") }
                val isF = (0 until (friends?.length() ?: 0)).any { friends!!.getJSONObject(it).getString("friend_id") == id }
                Row(Modifier.fillMaxWidth().padding(vertical = 4.dp).clip(RoundedCornerShape(12.dp)).background(Card.copy(alpha = .85f)).padding(12.dp), verticalAlignment = Alignment.CenterVertically) {
                    DmAvatar(nm); Spacer(Modifier.width(10.dp))
                    Text(nm + if (x.optBoolean("verified")) " ✓" else "", color = Color.White, fontSize = 14.sp, modifier = Modifier.weight(1f))
                    if (isF) Text("friend", color = Muted, fontSize = 12.sp)
                    else Text("+ Add", color = Gold, fontSize = 13.sp, fontWeight = FontWeight.Bold, modifier = Modifier.clip(RoundedCornerShape(8.dp)).clickable {
                        scope.launch {
                            try {
                                val r = Api.dmFriendRequest(id)
                                msg = if (r.optBoolean("accepted")) "You are friends now." else "Request sent."
                                changed()
                            } catch (e: Exception) { msg = e.message }
                        }
                    }.padding(8.dp))
                }
            }
        }
        item { Text("YOUR FRIENDS (" + (friends?.length() ?: 0) + ")", color = Muted, fontSize = 11.sp, fontWeight = FontWeight.Bold, modifier = Modifier.padding(vertical = 10.dp)) }
        if (friends == null) item { CircularProgressIndicator(color = Gold) }
        else if (friends.length() == 0) item { Text("No friends yet. Search a player above and send a request.", color = Muted, fontSize = 13.sp, modifier = Modifier.padding(bottom = 20.dp)) }
        else items((0 until friends.length()).map { friends.getJSONObject(it).getString("friend_id") }) { fid ->
            val nm = names[fid] ?: "Player"
            Row(Modifier.fillMaxWidth().padding(vertical = 4.dp).clip(RoundedCornerShape(12.dp)).background(Card.copy(alpha = .85f))
                .border(1.dp, Gold.copy(alpha = .2f), RoundedCornerShape(12.dp)).padding(12.dp), verticalAlignment = Alignment.CenterVertically) {
                DmAvatar(nm); Spacer(Modifier.width(10.dp))
                Text(nm, color = Color.White, fontSize = 14.sp, modifier = Modifier.weight(1f))
                Text("Message", color = Ink, fontSize = 12.sp, fontWeight = FontWeight.Bold, modifier = Modifier.clip(RoundedCornerShape(8.dp)).background(Gold).clickable { openFriend(fid) }.padding(horizontal = 12.dp, vertical = 7.dp))
                Spacer(Modifier.width(8.dp))
                Text("Remove", color = ErrRed, fontSize = 12.sp, modifier = Modifier.clip(RoundedCornerShape(8.dp)).clickable {
                    scope.launch { try { Api.dmFriendRemove(fid); changed() } catch (e: Exception) { msg = e.message } }
                }.padding(8.dp))
            }
        }
    }
}

@Composable
private fun RequestsPane(msgReqs: JSONArray?, inc: JSONArray?, out: JSONArray?, names: Map<String, String>, changed: () -> Unit, viewThread: (String, String) -> Unit) {
    val scope = rememberCoroutineScope()
    var msg by remember { mutableStateOf<String?>(null) }
    LazyColumn(Modifier.fillMaxSize().padding(horizontal = 16.dp)) {
        msg?.let { item { Text(it, color = ErrRed, fontSize = 12.sp, modifier = Modifier.padding(4.dp)) } }
        if (msgReqs != null && msgReqs.length() > 0) {
            item { Text("MESSAGE REQUESTS", color = Muted, fontSize = 11.sp, fontWeight = FontWeight.Bold, modifier = Modifier.padding(vertical = 8.dp)) }
            items((0 until msgReqs.length()).map { msgReqs.getJSONObject(it) }) { t ->
                val from = t.getString("requested_by")
                val nm = names[from] ?: "Player"
                Row(Modifier.fillMaxWidth().padding(vertical = 4.dp).clip(RoundedCornerShape(12.dp)).background(Card.copy(alpha = .85f)).padding(12.dp), verticalAlignment = Alignment.CenterVertically) {
                    DmAvatar(nm); Spacer(Modifier.width(10.dp))
                    Column(Modifier.weight(1f)) { Text(nm, color = Color.White, fontSize = 14.sp); Text("wants to message you", color = Muted, fontSize = 12.sp) }
                    Text("View", color = Gold, fontSize = 12.sp, modifier = Modifier.clickable { viewThread(t.getString("id"), from) }.padding(6.dp))
                    Text("Accept", color = Ink, fontSize = 12.sp, fontWeight = FontWeight.Bold, modifier = Modifier.clip(RoundedCornerShape(8.dp)).background(Gold).clickable {
                        scope.launch { try { Api.dmRespondRequest(t.getString("id"), true); changed() } catch (e: Exception) { msg = e.message } }
                    }.padding(horizontal = 10.dp, vertical = 6.dp))
                    Spacer(Modifier.width(6.dp))
                    Text("Decline", color = ErrRed, fontSize = 12.sp, modifier = Modifier.clickable {
                        scope.launch { try { Api.dmRespondRequest(t.getString("id"), false); changed() } catch (e: Exception) { msg = e.message } }
                    }.padding(6.dp))
                }
            }
        }
        item { Text("FRIEND REQUESTS RECEIVED", color = Muted, fontSize = 11.sp, fontWeight = FontWeight.Bold, modifier = Modifier.padding(vertical = 8.dp)) }
        if (inc == null) item { CircularProgressIndicator(color = Gold) }
        else if (inc.length() == 0) item { Text("No incoming requests.", color = Muted, fontSize = 13.sp) }
        else items((0 until inc.length()).map { inc.getJSONObject(it) }) { r ->
            val from = r.getString("from_user")
            val nm = names[from] ?: "Player"
            Row(Modifier.fillMaxWidth().padding(vertical = 4.dp).clip(RoundedCornerShape(12.dp)).background(Card.copy(alpha = .85f)).padding(12.dp), verticalAlignment = Alignment.CenterVertically) {
                DmAvatar(nm); Spacer(Modifier.width(10.dp))
                Text(nm, color = Color.White, fontSize = 14.sp, modifier = Modifier.weight(1f))
                Text("Accept", color = Ink, fontSize = 12.sp, fontWeight = FontWeight.Bold, modifier = Modifier.clip(RoundedCornerShape(8.dp)).background(Gold).clickable {
                    scope.launch { try { Api.dmFriendRespond(r.getString("id"), true); changed() } catch (e: Exception) { msg = e.message } }
                }.padding(horizontal = 10.dp, vertical = 6.dp))
                Spacer(Modifier.width(6.dp))
                Text("Decline", color = ErrRed, fontSize = 12.sp, modifier = Modifier.clickable {
                    scope.launch { try { Api.dmFriendRespond(r.getString("id"), false); changed() } catch (e: Exception) { msg = e.message } }
                }.padding(6.dp))
            }
        }
        item { Text("SENT", color = Muted, fontSize = 11.sp, fontWeight = FontWeight.Bold, modifier = Modifier.padding(vertical = 8.dp)) }
        if (out == null) item { CircularProgressIndicator(color = Gold) }
        else if (out.length() == 0) item { Text("No outgoing requests.", color = Muted, fontSize = 13.sp, modifier = Modifier.padding(bottom = 20.dp)) }
        else items((0 until out.length()).map { out.getJSONObject(it) }) { r ->
            val to = r.getString("to_user")
            Row(Modifier.fillMaxWidth().padding(vertical = 4.dp).clip(RoundedCornerShape(12.dp)).background(Card.copy(alpha = .6f)).padding(12.dp), verticalAlignment = Alignment.CenterVertically) {
                DmAvatar(names[to] ?: "Player"); Spacer(Modifier.width(10.dp))
                Text(names[to] ?: "Player", color = Color.White, fontSize = 14.sp, modifier = Modifier.weight(1f))
                Text("pending", color = Muted, fontSize = 12.sp)
            }
        }
    }
}

/* ============ Conversation ============ */
private data class DmMsg(val id: String, val mine: Boolean, val kind: String, val text: String, val mediaPath: String?, val iv: String, val meta: JSONObject, val at: String)

@Composable
fun ConversationScreen(threadId: String, peerId: String, peerName: String, status: String, reqByMe: Boolean, onBack: () -> Unit) {
    val scope = rememberCoroutineScope()
    var key by remember { mutableStateOf<javax.crypto.spec.SecretKeySpec?>(null) }
    var keyErr by remember { mutableStateOf<String?>(null) }
    var msgs by remember { mutableStateOf<List<DmMsg>>(emptyList()) }
    var input by remember { mutableStateOf("") }
    var busy by remember { mutableStateOf(false) }
    var err by remember { mutableStateOf<String?>(null) }
    var viewImage by remember { mutableStateOf<android.graphics.Bitmap?>(null) }
    var videoFile by remember { mutableStateOf<java.io.File?>(null) }
    var showReport by remember { mutableStateOf(false) }
    var locked by remember { mutableStateOf(false) }
    val listState = rememberLazyListState()

    fun decode(rows: JSONArray, k: javax.crypto.spec.SecretKeySpec): List<DmMsg> {
        val out = ArrayList<DmMsg>()
        for (i in 0 until rows.length()) {
            val m = rows.getJSONObject(i)
            val kind = m.optString("kind", "text")
            val mine = m.optString("sender_id") == Api.uid
            var text = ""
            if (kind == "text") text = try { String(DmCrypto.decrypt(k, m.getString("ciphertext"), m.getString("iv")), Charsets.UTF_8) } catch (e: Exception) { "[cannot decrypt - different device key]" }
            out.add(DmMsg(m.optString("id"), mine, kind, text, if (kind == "text") null else m.optString("ciphertext"), m.optString("iv"), m.optJSONObject("meta") ?: JSONObject(), m.optString("created_at")))
        }
        return out
    }

    LaunchedEffect(threadId) {
        try {
            val jwk = Api.dmPeerKey(peerId) ?: throw Exception(peerName + " has not set up messages on their device yet. They need to open Messages once.")
            key = DmCrypto.threadKey(peerId, jwk)
        } catch (e: Exception) { keyErr = e.message }
    }
    LaunchedEffect(key) {
        val k = key ?: return@LaunchedEffect
        while (isActive) {
            try {
                msgs = decode(Api.dmMessages(threadId), k)
                if (listState.layoutInfo.totalItemsCount > 0) listState.scrollToItem(listState.layoutInfo.totalItemsCount - 1)
            } catch (e: Exception) { }
            delay(4000)
        }
    }

    val picker = rememberLauncherForActivityResult(ActivityResultContracts.OpenDocument()) { uri ->
        if (uri == null || key == null) return@rememberLauncherForActivityResult
        busy = true
        scope.launch {
            try {
                val cr = Api.ctx.contentResolver
                val mime = cr.getType(uri) ?: "image/jpeg"
                val isVideo = mime.startsWith("video/")
                var bytes = withContext(Dispatchers.IO) { cr.openInputStream(uri)!!.use { it.readBytes() } }
                var meta: JSONObject
                var kind: String
                if (isVideo) {
                    if (bytes.size > 8 * 1024 * 1024) throw Exception("Video too large - keep it under 8 MB.")
                    kind = "video"
                    meta = JSONObject().put("mime", mime).put("size", bytes.size)
                } else {
                    val opts = BitmapFactory.Options().apply { inJustDecodeBounds = true }
                    BitmapFactory.decodeByteArray(bytes, 0, bytes.size, opts)
                    var w = opts.outWidth; var h = opts.outHeight
                    if (w <= 0 || h <= 0) throw Exception("Could not read that image.")
                    val max = 1280
                    var bmp = BitmapFactory.decodeByteArray(bytes, 0, bytes.size)
                    if (maxOf(w, h) > max) {
                        val f = max.toFloat() / maxOf(w, h)
                        w = Math.round(w * f); h = Math.round(h * f)
                        bmp = android.graphics.Bitmap.createScaledBitmap(bmp, w, h, true)
                    }
                    val baos = java.io.ByteArrayOutputStream()
                    bmp.compress(android.graphics.Bitmap.CompressFormat.JPEG, 82, baos)
                    bytes = baos.toByteArray()
                    kind = "image"
                    meta = JSONObject().put("mime", "image/jpeg").put("width", w).put("height", h).put("size", bytes.size)
                }
                val enc = DmCrypto.encrypt(key!!, bytes)
                val encBytes = android.util.Base64.decode(enc.ciphertext, android.util.Base64.NO_WRAP)
                val path = Api.dmMediaUpload(threadId, encBytes)
                val r = Api.dmSend(threadId, kind, path, enc.iv, meta)
                if (!r.optBoolean("ok")) throw Exception(r.optString("error").ifBlank { "Could not send." })
                msgs = decode(Api.dmMessages(threadId), key!!)
            } catch (e: Exception) { err = if (e.message?.contains("never") == true) "Upload error." else e.message }
            busy = false
        }
    }

    Column(Modifier.fillMaxSize().background(Ink)) {
        Row(Modifier.fillMaxWidth().padding(horizontal = 8.dp, vertical = 8.dp), verticalAlignment = Alignment.CenterVertically) {
            Text("< Back", color = Gold, fontSize = 14.sp, modifier = Modifier.clip(RoundedCornerShape(6.dp)).clickable { onBack() }.padding(8.dp))
            DmAvatar(peerName); Spacer(Modifier.width(8.dp))
            Column(Modifier.weight(1f)) {
                Text(peerName, color = Gold2, fontSize = 16.sp, fontWeight = FontWeight.Bold)
                Text("End-to-end encrypted", color = Muted, fontSize = 11.sp)
            }
            Text("Report", color = ErrRed, fontSize = 12.sp, modifier = Modifier.clickable { showReport = true }.padding(8.dp))
            Text("Block", color = ErrRed, fontSize = 12.sp, modifier = Modifier.clickable {
                scope.launch { try { Api.dmBlock(peerId); onBack() } catch (e: Exception) { err = e.message } }
            }.padding(8.dp))
        }
        if (status == "requested") {
            if (reqByMe) Text("Message request sent - they can read your messages and accept from Messages > Requests.", color = Muted, fontSize = 12.sp, modifier = Modifier.padding(horizontal = 16.dp, vertical = 4.dp))
            else Row(Modifier.fillMaxWidth().padding(horizontal = 16.dp, vertical = 4.dp), verticalAlignment = Alignment.CenterVertically) {
                Text("They asked to message you.", color = Muted, fontSize = 12.sp, modifier = Modifier.weight(1f))
                Text("Accept", color = Ink, fontSize = 12.sp, fontWeight = FontWeight.Bold, modifier = Modifier.clip(RoundedCornerShape(8.dp)).background(Gold).clickable {
                    scope.launch { try { Api.dmRespondRequest(threadId, true); onBack() } catch (e: Exception) { err = e.message } }
                }.padding(horizontal = 10.dp, vertical = 6.dp))
                Spacer(Modifier.width(6.dp))
                Text("Decline", color = ErrRed, fontSize = 12.sp, modifier = Modifier.clickable {
                    scope.launch { try { Api.dmRespondRequest(threadId, false); onBack() } catch (e: Exception) { err = e.message } }
                }.padding(6.dp))
            }
        }
        keyErr?.let {
            Column(Modifier.fillMaxWidth().padding(24.dp)) {
                Text(it, color = ErrRed, fontSize = 14.sp)
                Text("They need to open Messages once on their device to set up encryption.", color = Muted, fontSize = 12.sp)
            }
        }
        err?.let { Text(it, color = ErrRed, fontSize = 12.sp, modifier = Modifier.padding(horizontal = 16.dp)) }
        if (locked) Text("This conversation is locked pending owner review.", color = ErrRed, fontSize = 12.sp, modifier = Modifier.padding(horizontal = 16.dp, vertical = 4.dp))
        LazyColumn(state = listState, modifier = Modifier.weight(1f).fillMaxWidth().padding(horizontal = 12.dp)) {
            if (key != null && msgs.isEmpty()) item { Text("No messages yet. Say hello.", color = Muted, fontSize = 13.sp, modifier = Modifier.padding(20.dp)) }
            items(msgs) { m ->
                Row(Modifier.fillMaxWidth(), horizontalArrangement = if (m.mine) Arrangement.End else Arrangement.Start) {
                    Column(Modifier.padding(vertical = 3.dp).clip(RoundedCornerShape(14.dp))
                        .background(if (m.mine) Gold.copy(alpha = .9f) else Card)
                        .padding(horizontal = 12.dp, vertical = 8.dp).widthIn(max = 280.dp)) {
                        if (m.kind == "text") Text(m.text, color = if (m.mine) Ink else Color.White, fontSize = 14.sp)
                        else {
                            val label = if (m.kind == "image") "Photo - tap to view" else "Video" + (if (m.meta.optInt("duration") > 0) " (" + m.meta.optInt("duration") + "s)" else "") + " - tap to play"
                            Text(label, color = if (m.mine) Ink else Gold, fontSize = 13.sp, fontWeight = FontWeight.Bold, modifier = Modifier.clickable {
                                val k = key ?: return@clickable
                                scope.launch {
                                    try {
                                        val signed = Api.dmMediaSign(m.mediaPath!!)
                                        val enc = Api.dmMediaDownload(signed)
                                        val plain = DmCrypto.decrypt(k, android.util.Base64.encodeToString(enc, android.util.Base64.NO_WRAP), m.iv)
                                        if (m.kind == "image") viewImage = BitmapFactory.decodeByteArray(plain, 0, plain.size)
                                        else {
                                            val f = java.io.File(Api.ctx.cacheDir, "dmv_" + m.id + ".mp4")
                                            f.writeBytes(plain); videoFile = f
                                        }
                                    } catch (e: Exception) { err = "Could not open media." }
                                }
                            }.padding(vertical = 4.dp))
                        }
                        Text(dmTime(m.at), color = if (m.mine) Ink.copy(alpha = .6f) else Muted, fontSize = 10.sp, modifier = Modifier.align(Alignment.End))
                    }
                }
            }
        }
        Row(Modifier.fillMaxWidth().padding(10.dp), verticalAlignment = Alignment.CenterVertically) {
            Text("+", color = Gold, fontSize = 22.sp, fontWeight = FontWeight.Bold, modifier = Modifier.clip(CircleShape).clickable { picker.launch(arrayOf("image/*", "video/*")) }.padding(horizontal = 10.dp, vertical = 2.dp))
            BasicTextField(input, { input = it }, modifier = Modifier.weight(1f).clip(RoundedCornerShape(20.dp)).background(Card).padding(horizontal = 14.dp, vertical = 10.dp),
                textStyle = androidx.compose.ui.text.TextStyle(color = Color.White, fontSize = 14.sp), maxLines = 3,
                decorationBox = { inner -> if (input.isEmpty()) Text("Message...", color = Muted, fontSize = 14.sp); inner() })
            Spacer(Modifier.width(8.dp))
            Text(if (busy) "..." else "Send", color = Ink, fontWeight = FontWeight.Bold, fontSize = 13.sp,
                modifier = Modifier.clip(RoundedCornerShape(20.dp)).background(Gold).clickable {
                    val v = input.trim()
                    val k = key
                    if (v.isEmpty() || k == null || busy) return@clickable
                    input = ""; busy = true
                    scope.launch {
                        try {
                            val enc = DmCrypto.encrypt(k, v.toByteArray(Charsets.UTF_8))
                            val r = Api.dmSend(threadId, "text", enc.ciphertext, enc.iv, JSONObject())
                            if (!r.optBoolean("ok")) throw Exception(r.optString("error").ifBlank { "Could not send." })
                            msgs = decode(Api.dmMessages(threadId), k)
                            if (msgs.isNotEmpty()) listState.scrollToItem(msgs.size - 1)
                        } catch (e: Exception) { err = e.message }
                        busy = false
                    }
                }.padding(horizontal = 16.dp, vertical = 10.dp))
        }
    }

    viewImage?.let { bmp ->
        Dialog(onDismissRequest = { viewImage = null }) {
            Image(bmp.asImageBitmap(), contentDescription = "photo", modifier = Modifier.fillMaxWidth().clip(RoundedCornerShape(12.dp)).clickable { viewImage = null })
        }
    }
    videoFile?.let { f ->
        Dialog(onDismissRequest = { videoFile = null }) {
            Column(Modifier.clip(RoundedCornerShape(12.dp)).background(Ink).padding(8.dp)) {
                androidx.compose.ui.viewinterop.AndroidView(factory = { ctx ->
                    android.widget.VideoView(ctx).apply {
                        setVideoPath(f.absolutePath)
                        setOnPreparedListener { mp -> mp.start() }
                    }
                }, modifier = Modifier.fillMaxWidth().height(360.dp))
                Text("Close", color = Gold, fontSize = 13.sp, modifier = Modifier.align(Alignment.CenterHorizontally).clickable { videoFile = null }.padding(10.dp))
            }
        }
    }
    if (showReport) ReportDialog(peerName, onDone = { ok -> if (ok) locked = true; showReport = false }, onSubmit = { reason, note, done ->
        val k = key
        scope.launch {
            try {
                val rows = Api.dmMessages(threadId)
                val last = JSONArray()
                val start = maxOf(0, rows.length() - 10)
                for (i in start until rows.length()) {
                    val m = rows.getJSONObject(i)
                    val o = JSONObject().put("from", if (m.optString("sender_id") == Api.uid) "me" else "them").put("type", m.optString("kind", "text")).put("at", m.optString("created_at"))
                    if (m.optString("kind") == "text" && k != null) o.put("text", try { String(DmCrypto.decrypt(k, m.getString("ciphertext"), m.getString("iv")), Charsets.UTF_8) } catch (e: Exception) { "[undecryptable]" })
                    last.put(o)
                }
                val r = Api.dmReport(threadId, peerId, reason, note, last)
                done(r.optBoolean("ok"), r.optString("error"))
            } catch (e: Exception) { done(false, e.message ?: "Report failed.") }
        }
    })
}

@Composable
private fun ReportDialog(peerName: String, onDone: (Boolean) -> Unit, onSubmit: (String, String, (Boolean, String) -> Unit) -> Unit) {
    val reasons = listOf("abuse" to "Abuse", "spam" to "Spam", "harassment" to "Harassment", "fake" to "Fake account", "other" to "Other")
    var sel by remember { mutableStateOf("abuse") }
    var note by remember { mutableStateOf("") }
    var busy by remember { mutableStateOf(false) }
    var msg by remember { mutableStateOf<String?>(null) }
    Dialog(onDismissRequest = { onDone(false) }) {
        Column(Modifier.fillMaxWidth().clip(RoundedCornerShape(16.dp)).background(Card).padding(18.dp)) {
            Text("Report " + peerName, color = Gold2, fontSize = 17.sp, fontWeight = FontWeight.Bold)
            Spacer(Modifier.height(6.dp))
            Text("Pick a reason. Your last 10 messages (decrypted on YOUR device) go to the owner for review - nothing else is readable. The conversation locks for them until review.", color = Muted, fontSize = 12.sp)
            Spacer(Modifier.height(8.dp))
            reasons.forEach { (v, label) ->
                Row(Modifier.fillMaxWidth().clickable { sel = v }.padding(vertical = 4.dp), verticalAlignment = Alignment.CenterVertically) {
                    RadioButton(selected = sel == v, onClick = { sel = v }, colors = RadioButtonDefaults.colors(selectedColor = Gold))
                    Text(label, color = Color.White, fontSize = 14.sp)
                }
            }
            BasicTextField(note, { note = it }, modifier = Modifier.fillMaxWidth().clip(RoundedCornerShape(10.dp)).background(Ink).padding(10.dp),
                textStyle = androidx.compose.ui.text.TextStyle(color = Color.White, fontSize = 13.sp),
                decorationBox = { inner -> if (note.isEmpty()) Text("Optional note for the owner", color = Muted, fontSize = 13.sp); inner() })
            Spacer(Modifier.height(12.dp))
            msg?.let { Text(it, color = ErrRed, fontSize = 12.sp) }
            Row {
                Text(if (busy) "Sending..." else "Send report & lock", color = Color.White, fontSize = 13.sp, fontWeight = FontWeight.Bold,
                    modifier = Modifier.clip(RoundedCornerShape(8.dp)).background(ErrRed.copy(alpha = .6f)).clickable {
                        if (busy) return@clickable
                        busy = true
                        onSubmit(sel, note.trim()) { ok, e ->
                            busy = false
                            if (ok) onDone(true) else msg = e.ifBlank { "Report failed." }
                        }
                    }.padding(horizontal = 12.dp, vertical = 9.dp))
                Spacer(Modifier.width(10.dp))
                Text("Cancel", color = Muted, fontSize = 13.sp, modifier = Modifier.clickable { onDone(false) }.padding(9.dp))
            }
        }
    }
}
