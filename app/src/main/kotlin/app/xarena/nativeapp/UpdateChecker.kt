package app.xarena.nativeapp

import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import org.json.JSONObject
import java.io.File
import java.io.RandomAccessFile
import java.net.HttpURLConnection
import java.net.URL
import java.security.MessageDigest

/** Native-only update check + in-app download/install. Reads xarena-native-version.json (NEVER the WebView app's xarena-version.json). */
object UpdateChecker {
    const val MANIFEST = "https://xarenaprivate.github.io/xarena/xarena-native-version.json"

    data class Info(val code: Int, val name: String, val url: String, val notes: String, val sha256: String, val mandatory: Boolean)

    suspend fun latest(): Info? = withContext(Dispatchers.IO) {
        try {
            val text = URL(MANIFEST).openConnection().apply { connectTimeout = 10000; readTimeout = 10000 }
                .getInputStream().bufferedReader().readText()
            val j = JSONObject(text)
            Info(j.optInt("versionCode"), j.optString("versionName"), j.optString("apkUrl"),
                j.optString("notes"), j.optString("sha256"), j.optBoolean("mandatory", false))
        } catch (e: Exception) { null }
    }

    fun updateFile(ctx: android.content.Context): File = File(File(ctx.cacheDir, "updates"), "xarena-update.apk")

    /** HEAD request for the real APK size in bytes; -1 if unknown. */
    suspend fun apkSize(url: String): Long = withContext(Dispatchers.IO) {
        try {
            val c = URL(url).openConnection() as HttpURLConnection
            c.requestMethod = "HEAD"; c.connectTimeout = 10000; c.readTimeout = 10000
            c.instanceFollowRedirects = true
            val n = c.getHeaderFieldLong("Content-Length", -1)
            c.disconnect(); n
        } catch (e: Exception) { -1L }
    }

    /** Downloads with resume. onProgress(doneBytes, totalBytes, bytesPerSec). Returns null on success, else an error message. */
    suspend fun download(ctx: android.content.Context, info: Info, onProgress: (Long, Long, Long) -> Unit): String? = withContext(Dispatchers.IO) {
        try {
            val f = updateFile(ctx)
            f.parentFile?.mkdirs()
            var have = if (f.exists()) f.length() else 0L
            val c = URL(info.url).openConnection() as HttpURLConnection
            c.connectTimeout = 15000; c.readTimeout = 20000; c.instanceFollowRedirects = true
            if (have > 0) c.setRequestProperty("Range", "bytes=$have-")
            val code = c.responseCode
            if (code == 200 && have > 0) have = 0 // server ignored Range - start over
            if (code != 200 && code != 206) { c.disconnect(); return@withContext "Server error $code - tap RETRY." }
            var total = c.getHeaderFieldLong("Content-Length", -1)
            if (total > 0) total += have
            val sha = MessageDigest.getInstance("SHA-256")
            val raf = RandomAccessFile(f, "rw")
            raf.seek(have)
            // hash the already-downloaded prefix so resume still verifies
            if (have > 0) {
                raf.seek(0)
                val pre = ByteArray(65536)
                var left = have
                while (left > 0) {
                    val r = raf.read(pre, 0, minOf(pre.size.toLong(), left).toInt())
                    if (r <= 0) break
                    sha.update(pre, 0, r); left -= r
                }
                raf.seek(have)
            }
            val input = c.inputStream
            val buf = ByteArray(32768)
            var done = have
            val startedAt = System.nanoTime()
            var lastTick = 0L
            while (true) {
                val r = input.read(buf)
                if (r < 0) break
                raf.write(buf, 0, r); sha.update(buf, 0, r); done += r
                val now = System.currentTimeMillis()
                if (now - lastTick > 300) {
                    lastTick = now
                    val secs = (System.nanoTime() - startedAt) / 1_000_000_000.0
                    val bps = if (secs > 0.5) ((done - have) / secs).toLong() else 0L
                    withContext(Dispatchers.Main) { onProgress(done, total, bps) }
                }
            }
            raf.close(); input.close(); c.disconnect()
            withContext(Dispatchers.Main) { onProgress(done, total, 0L) }
            if (total > 0 && done != total) return@withContext "Download incomplete - tap RETRY, it will resume."
            if (info.sha256.isNotBlank()) {
                val hex = sha.digest().joinToString("") { "%02x".format(it) }
                if (!hex.equals(info.sha256, true)) { f.delete(); return@withContext "Download was corrupted - tap RETRY." }
            }
            null
        } catch (e: Exception) {
            "Network problem - tap RETRY, the download will resume where it stopped."
        }
    }

    /** Launches the system installer. Returns null if the install flow was launched, else a user-visible message. */
    fun install(ctx: android.content.Context): String? {
        return try {
            val f = updateFile(ctx)
            if (!f.exists() || f.length() == 0L) return "Update file missing - download again."
            if (android.os.Build.VERSION.SDK_INT >= 26 && !ctx.packageManager.canRequestPackageInstalls()) {
                try {
                    val s = android.content.Intent(android.provider.Settings.ACTION_MANAGE_UNKNOWN_APP_SOURCES,
                        android.net.Uri.parse("package:" + ctx.packageName)).addFlags(android.content.Intent.FLAG_ACTIVITY_NEW_TASK)
                    ctx.startActivity(s)
                    return "One-time step: allow XARENA to install apps in the screen that opened, come back and tap INSTALL."
                } catch (e: Exception) {
                    return "Open Android Settings > Apps > XARENA > Install unknown apps, allow it, then tap INSTALL."
                }
            }
            val uri = androidx.core.content.FileProvider.getUriForFile(ctx, "app.xarena.esports.native.fileprovider", f)
            val i = android.content.Intent(android.content.Intent.ACTION_VIEW)
                .setDataAndType(uri, "application/vnd.android.package-archive")
                .addFlags(android.content.Intent.FLAG_GRANT_READ_URI_PERMISSION or android.content.Intent.FLAG_ACTIVITY_NEW_TASK)
            ctx.startActivity(i)
            null
        } catch (e: Exception) {
            "Installer could not open - use the browser link instead."
        }
    }
}

private fun mb(b: Long): String = if (b <= 0) "?" else String.format("%.1f", b / 1048576.0)

@Composable
fun UpdateDialog(info: UpdateChecker.Info, onDismiss: () -> Unit) {
    val ctx = LocalContext.current
    val scope = rememberCoroutineScope()
    var downloading by remember { mutableStateOf(false) }
    var done by remember { mutableStateOf(0L) }
    var total by remember { mutableStateOf(-1L) }
    var bps by remember { mutableStateOf(0L) }
    var err by remember { mutableStateOf<String?>(null) }
    var installing by remember { mutableStateOf(false) }
    var sizeKnown by remember { mutableStateOf(-1L) }
    LaunchedEffect(info.url) { sizeKnown = UpdateChecker.apkSize(info.url) }

    Box(Modifier.fillMaxSize().background(Color(0xCC000000)), contentAlignment = Alignment.Center) {
        Column(Modifier.padding(20.dp).fillMaxWidth().clip(RoundedCornerShape(20.dp)).background(Card)
            .border(1.dp, Gold.copy(alpha = .45f), RoundedCornerShape(20.dp)).padding(20.dp),
            horizontalAlignment = Alignment.CenterHorizontally) {
            // Header: brand + version pill
            Row(verticalAlignment = Alignment.CenterVertically) {
                Text("XARENA", color = Gold, fontSize = 22.sp, fontWeight = FontWeight.Bold)
                Spacer(Modifier.width(8.dp))
                Text(info.name, color = Ink, fontSize = 11.sp, fontWeight = FontWeight.Bold,
                    modifier = Modifier.clip(RoundedCornerShape(6.dp)).background(Gold).padding(horizontal = 8.dp, vertical = 2.dp))
            }
            Spacer(Modifier.height(14.dp))

            if (!downloading) {
                Text(if (info.mandatory) "Mandatory Update" else "Update Available", color = Gold2, fontSize = 22.sp, fontWeight = FontWeight.Bold)
                Spacer(Modifier.height(6.dp))
                Text(if (info.mandatory) "To continue using XARENA, please update to the latest version for the best experience."
                     else "A new XARENA version is ready. Update for the best experience.",
                    color = Muted, fontSize = 13.sp, textAlign = TextAlign.Center)
                Spacer(Modifier.height(14.dp))
                if (info.notes.isNotBlank()) {
                    Column(Modifier.fillMaxWidth().clip(RoundedCornerShape(12.dp)).background(Color(0x22FFFFFF)).padding(14.dp)) {
                        Text("New in " + info.name, color = Gold2, fontSize = 15.sp, fontWeight = FontWeight.Bold)
                        Spacer(Modifier.height(4.dp))
                        Text(info.notes, color = Color.White, fontSize = 13.sp)
                    }
                    Spacer(Modifier.height(14.dp))
                }
                err?.let { Text(it, color = ErrRed, fontSize = 13.sp, textAlign = TextAlign.Center); Spacer(Modifier.height(8.dp)) }
                GoldButton("UPDATE NOW" + if (sizeKnown > 0) " (" + mb(sizeKnown) + " MB)" else "") {
                    downloading = true; err = null
                    scope.launch {
                        val e = UpdateChecker.download(ctx, info) { d, t, s -> done = d; total = t; bps = s }
                        if (e != null) { err = e; downloading = false }
                        else {
                            installing = true
                            val ie = UpdateChecker.install(ctx)
                            if (ie != null) { err = ie; downloading = false; installing = false }
                            else onDismiss()
                        }
                    }
                }
                Spacer(Modifier.height(8.dp))
                if (!info.mandatory) Text("Skip for now", color = Muted, fontSize = 13.sp,
                    modifier = Modifier.clip(RoundedCornerShape(6.dp)).clickable { onDismiss() }.padding(8.dp))
                Spacer(Modifier.height(4.dp))
                Text("Version " + info.name + (if (sizeKnown > 0) " • " + mb(sizeKnown) + " MB" else ""), color = Muted, fontSize = 11.sp)
            } else {
                Text("Update in Progress", color = Gold2, fontSize = 20.sp, fontWeight = FontWeight.Bold)
                Text("please wait • Downloading & Installing", color = Muted, fontSize = 12.sp)
                Spacer(Modifier.height(16.dp))
                val pct = if (total > 0) ((done * 100) / total).toInt().coerceIn(0, 100) else 0
                Row(Modifier.fillMaxWidth(), verticalAlignment = Alignment.CenterVertically) {
                    Text("$pct%", color = Gold2, fontSize = 22.sp, fontWeight = FontWeight.Bold)
                    Spacer(Modifier.width(10.dp))
                    LinearProgressIndicator(progress = { pct / 100f }, color = Gold, trackColor = Color(0x33FFFFFF),
                        modifier = Modifier.weight(1f).height(8.dp).clip(RoundedCornerShape(4.dp)))
                    Spacer(Modifier.width(10.dp))
                    Text((100 - pct).toString() + "%", color = Muted, fontSize = 13.sp)
                }
                Spacer(Modifier.height(12.dp))
                Column(Modifier.fillMaxWidth().clip(RoundedCornerShape(12.dp)).background(Color(0x22FFFFFF)).padding(12.dp)) {
                    Text(if (installing) "Installing..." else "Downloading... $pct%", color = Color.White, fontSize = 14.sp, fontWeight = FontWeight.Bold)
                    if (!installing) Text(mb(done) + " MB of " + mb(total) + " MB" + (if (bps > 0) " • " + String.format("%.1f", bps / 1048576.0) + " MB/s" else ""),
                        color = Muted, fontSize = 12.sp)
                    if (installing) Text("Preparing install", color = Muted, fontSize = 12.sp)
                }
                if (info.notes.isNotBlank()) {
                    Spacer(Modifier.height(12.dp))
                    Column(Modifier.fillMaxWidth().clip(RoundedCornerShape(12.dp)).background(Color(0x22FFFFFF)).padding(12.dp)) {
                        Text("What's New in " + info.name + ":", color = Gold2, fontSize = 13.sp, fontWeight = FontWeight.Bold)
                        Spacer(Modifier.height(4.dp))
                        Text(info.notes, color = Color.White, fontSize = 12.sp)
                    }
                }
                err?.let {
                    Spacer(Modifier.height(10.dp)); Text(it, color = ErrRed, fontSize = 12.sp, textAlign = TextAlign.Center)
                    Spacer(Modifier.height(6.dp))
                    GoldButton("RETRY") {
                        err = null
                        scope.launch {
                            val e = UpdateChecker.download(ctx, info) { d, t, s -> done = d; total = t; bps = s }
                            if (e != null) err = e
                            else { installing = true; val ie = UpdateChecker.install(ctx); if (ie != null) { err = ie; installing = false } else onDismiss() }
                        }
                    }
                }
                Spacer(Modifier.height(12.dp))
                Text("Do not close the app while the update is in progress", color = Muted, fontSize = 11.sp, textAlign = TextAlign.Center)
                if (err != null) {
                    Spacer(Modifier.height(6.dp))
                    Text("Open in browser instead", color = Muted, fontSize = 12.sp,
                        modifier = Modifier.clickable { openExternalUrl(info.url); onDismiss() }.padding(6.dp))
                }
            }
        }
    }
}
