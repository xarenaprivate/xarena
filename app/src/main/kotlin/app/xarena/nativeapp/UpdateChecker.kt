package app.xarena.nativeapp

import android.content.Intent
import android.net.Uri
import androidx.compose.foundation.background
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
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import org.json.JSONObject
import java.net.URL

/** Native-only update check. Reads xarena-native-version.json (NEVER the WebView app's xarena-version.json). */
object UpdateChecker {
    const val MANIFEST = "https://xarenaprivate.github.io/xarena/xarena-native-version.json"

    data class Info(val code: Int, val name: String, val url: String, val notes: String)

    suspend fun latest(): Info? = withContext(Dispatchers.IO) {
        try {
            val text = URL(MANIFEST).openConnection().apply { connectTimeout = 10000; readTimeout = 10000 }
                .getInputStream().bufferedReader().readText()
            val j = JSONObject(text)
            Info(j.optInt("versionCode"), j.optString("versionName"), j.optString("apkUrl"), j.optString("notes"))
        } catch (e: Exception) { null }
    }
}

@Composable
fun UpdateDialog(info: UpdateChecker.Info, onDismiss: () -> Unit) {
    val ctx = LocalContext.current
    Box(Modifier.fillMaxSize().background(Color(0xAA000000)), contentAlignment = Alignment.Center) {
        Column(Modifier.padding(28.dp).clip(RoundedCornerShape(20.dp)).background(Card)
            .padding(20.dp)) {
            Text("Update available", color = Gold, fontSize = 20.sp, fontWeight = FontWeight.Bold)
            Spacer(Modifier.height(6.dp))
            Text("XARENA Native " + info.name, color = Color.White, fontSize = 15.sp, fontWeight = FontWeight.Bold)
            if (info.notes.isNotBlank()) { Spacer(Modifier.height(4.dp)); Text(info.notes, color = Muted, fontSize = 13.sp) }
            Spacer(Modifier.height(16.dp))
            GoldButton("DOWNLOAD UPDATE") {
                ctx.startActivity(Intent(Intent.ACTION_VIEW, Uri.parse(info.url)))
                onDismiss()
            }
            Spacer(Modifier.height(8.dp))
            Text("Later", color = Muted, fontSize = 13.sp, modifier = Modifier.align(Alignment.CenterHorizontally)
                .clip(RoundedCornerShape(6.dp)).clickable { onDismiss() }.padding(8.dp))
        }
    }
}
