package app.xarena.nativeapp

import android.graphics.Bitmap
import android.graphics.Canvas
import android.graphics.Color as GColor
import android.graphics.Paint
import android.graphics.Path
import android.graphics.RectF
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Text
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.nativeCanvas
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import kotlinx.coroutines.launch
import org.json.JSONObject
import java.io.ByteArrayOutputStream

/* Squad Logo Maker (v82): captains design a simple custom logo (letters + colors + shape),
   it renders to a real 512px image, uploads through the existing signed Cloudinary route and
   saves via xr_squad_custom_logo_set (captain-gated, Cloudinary URLs only). Free - no AP charge.
   Catalog logos (buy/equip) are untouched. */

private val LM_BG = listOf(0xFF1E1338, 0xFF3B1B5A, 0xFF0A2440, 0xFF3A0A14, 0xFF0A3A24, 0xFF2A2A2A)
private val LM_FG = listOf(0xFFF5C451, 0xFFFFFFFF, 0xFF4ADE80, 0xFFF87171)
private val LM_SHAPES = listOf("Circle", "Shield", "Square")

internal object LmSeams {
    var letters = "PW"
    var bgIdx = 0
    var fgIdx = 0
    var shapeIdx = 0
    fun clear() { letters = "PW"; bgIdx = 0; fgIdx = 0; shapeIdx = 0 }
}

private fun lmDraw(size: Int, letters: String, bg: Long, fg: Long, shape: Int): Bitmap {
    val bmp = Bitmap.createBitmap(size, size, Bitmap.Config.ARGB_8888)
    val c = Canvas(bmp)
    val p = Paint(Paint.ANTI_ALIAS_FLAG)
    p.color = bg.toInt(); p.style = Paint.Style.FILL
    val m = size * 0.06f
    when (shape) {
        0 -> c.drawCircle(size / 2f, size / 2f, size / 2f - m, p)
        1 -> {
            val path = Path()
            path.moveTo(m, m); path.lineTo(size - m, m); path.lineTo(size - m, size * 0.62f)
            path.lineTo(size / 2f, size - m); path.lineTo(m, size * 0.62f); path.close()
            c.drawPath(path, p)
        }
        else -> c.drawRoundRect(RectF(m, m, size - m, size - m), size * 0.12f, size * 0.12f, p)
    }
    val t = Paint(Paint.ANTI_ALIAS_FLAG)
    t.color = fg.toInt(); t.textAlign = Paint.Align.CENTER; t.isFakeBoldText = true
    t.textSize = size * (if (letters.length <= 1) 0.52f else if (letters.length == 2) 0.38f else 0.28f)
    val y = size / 2f - (t.descent() + t.ascent()) / 2f
    c.drawText(letters, size / 2f, y, t)
    return bmp
}

@Composable
internal fun LmPreview(letters: String, bgIdx: Int, fgIdx: Int, shapeIdx: Int) {
    androidx.compose.foundation.Canvas(Modifier.size(150.dp).clip(RoundedCornerShape(18.dp)).background(Color(0xFF120A24))) {
        val size = this.size.minDimension
        drawContext.canvas.nativeCanvas.apply {
            val bmp = lmDraw(size.toInt(), letters.ifBlank { "X" }.uppercase(), LM_BG[bgIdx], LM_FG[fgIdx], shapeIdx)
            drawBitmap(bmp, (this@Canvas.size.width - size) / 2f, (this@Canvas.size.height - size) / 2f, null)
        }
    }
}

@Composable
fun SquadLogoMakerSheet(squadId: String, squadName: String, onDone: (String?) -> Unit, onClose: () -> Unit) {
    val scope = rememberCoroutineScope()
    var letters by remember { mutableStateOf(LmSeams.letters) }
    var bgIdx by remember { mutableIntStateOf(LmSeams.bgIdx) }
    var fgIdx by remember { mutableIntStateOf(LmSeams.fgIdx) }
    var shapeIdx by remember { mutableIntStateOf(LmSeams.shapeIdx) }
    var busy by remember { mutableStateOf(false) }
    var msg by remember { mutableStateOf<String?>(null) }

    LaunchedEffect(squadName) {
        if (letters == "PW" && squadName.isNotBlank()) {
            val parts = squadName.split(" ").filter { it.isNotBlank() }
            letters = (if (parts.size > 1) parts.take(3).map { it.first() }.joinToString("") else squadName.take(3)).uppercase()
        }
    }

    androidx.compose.ui.window.Dialog(onDismissRequest = { if (!busy) onClose() },
        properties = androidx.compose.ui.window.DialogProperties(usePlatformDefaultWidth = false)) {
        Column(Modifier.fillMaxSize().background(Ink).verticalScroll(rememberScrollState()).padding(horizontal = 16.dp, vertical = 12.dp),
            horizontalAlignment = Alignment.CenterHorizontally) {
            Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.SpaceBetween, verticalAlignment = Alignment.CenterVertically) {
                Text("Team Logo Maker", color = Gold2, fontSize = 17.sp, fontWeight = FontWeight.Bold)
                Text("Close", color = Muted, fontSize = 13.sp, modifier = Modifier.clickable(enabled = !busy) { onClose() }.padding(8.dp))
            }
            Text("Free - letters, colors and a shape. Sets your squad logo instantly.", color = Muted, fontSize = 12.sp, modifier = Modifier.fillMaxWidth())
            Spacer(Modifier.height(14.dp))
            LmPreview(letters, bgIdx, fgIdx, shapeIdx)
            Spacer(Modifier.height(14.dp))
            OutlinedTextField(letters, { letters = it.take(3).uppercase() }, label = { Text("Letters (1-3)") }, singleLine = true, modifier = Modifier.fillMaxWidth())
            Spacer(Modifier.height(10.dp))
            Text("SHAPE", color = Muted, fontSize = 11.sp, fontWeight = FontWeight.Bold, modifier = Modifier.fillMaxWidth())
            Row(horizontalArrangement = Arrangement.spacedBy(8.dp), modifier = Modifier.fillMaxWidth().padding(top = 4.dp)) {
                LM_SHAPES.forEachIndexed { i, s ->
                    val on = shapeIdx == i
                    Text(s, color = if (on) Ink else Gold, fontSize = 12.sp, fontWeight = FontWeight.Bold,
                        modifier = Modifier.clip(RoundedCornerShape(8.dp)).background(if (on) Gold else Color(0x22FFFFFF))
                            .clickable { shapeIdx = i }.padding(horizontal = 14.dp, vertical = 8.dp))
                }
            }
            Spacer(Modifier.height(10.dp))
            Text("BACKGROUND", color = Muted, fontSize = 11.sp, fontWeight = FontWeight.Bold, modifier = Modifier.fillMaxWidth())
            Row(horizontalArrangement = Arrangement.spacedBy(8.dp), modifier = Modifier.fillMaxWidth().padding(top = 4.dp)) {
                LM_BG.forEachIndexed { i, b ->
                    Box(Modifier.size(34.dp).clip(RoundedCornerShape(8.dp)).background(Color(b))
                        .border(if (bgIdx == i) 2.dp else 0.dp, if (bgIdx == i) Gold else Color.Transparent, RoundedCornerShape(8.dp))
                        .clickable { bgIdx = i })
                }
            }
            Spacer(Modifier.height(10.dp))
            Text("LETTER COLOR", color = Muted, fontSize = 11.sp, fontWeight = FontWeight.Bold, modifier = Modifier.fillMaxWidth())
            Row(horizontalArrangement = Arrangement.spacedBy(8.dp), modifier = Modifier.fillMaxWidth().padding(top = 4.dp)) {
                LM_FG.forEachIndexed { i, f ->
                    Box(Modifier.size(34.dp).clip(RoundedCornerShape(8.dp)).background(Color(f))
                        .border(if (fgIdx == i) 2.dp else 0.dp, if (fgIdx == i) Gold else Color(0x22FFFFFF), RoundedCornerShape(8.dp))
                        .clickable { fgIdx = i })
                }
            }
            Spacer(Modifier.height(16.dp))
            GoldButton(if (busy) "UPLOADING..." else "SET AS SQUAD LOGO") {
                if (busy) return@GoldButton
                if (letters.isBlank()) { msg = "Add at least one letter."; return@GoldButton }
                busy = true; msg = null
                scope.launch {
                    try {
                        val bmp = lmDraw(512, letters.uppercase(), LM_BG[bgIdx], LM_FG[fgIdx], shapeIdx)
                        val bos = ByteArrayOutputStream()
                        bmp.compress(Bitmap.CompressFormat.JPEG, 92, bos)
                        val url = Api.cloudinaryUpload(bos.toByteArray())
                        if (!url.startsWith("https://res.cloudinary.com")) throw Exception("Upload returned an unexpected address - logo not saved.")
                        val r = Api.authedRpc("xr_squad_custom_logo_set", JSONObject().put("p_squad", squadId).put("p_logo_url", url))
                        if (r.optBoolean("ok")) { onDone("Logo updated for your squad."); onClose() }
                        else msg = when (r.s("error")) {
                            "not_found_or_not_captain" -> "Only the squad captain can change the logo."
                            "invalid_logo_url" -> "The uploaded image address was rejected - try again."
                            "account_restricted" -> "Your account cannot change logos right now."
                            else -> r.s("error").ifBlank { "Could not save the logo." }
                        }
                    } catch (e: Exception) { msg = e.message ?: "Upload failed - try again." }
                    busy = false
                }
            }
            msg?.let { Text(it, color = if (it.contains("updated")) Color(0xFF4ADE80) else ErrRed, fontSize = 12.sp, modifier = Modifier.padding(top = 8.dp)) }
            Spacer(Modifier.height(20.dp))
        }
    }
}
