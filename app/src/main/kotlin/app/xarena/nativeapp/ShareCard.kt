package app.xarena.nativeapp

import android.graphics.Bitmap
import android.graphics.Canvas
import android.graphics.Color as GColor
import android.graphics.LinearGradient
import android.graphics.Paint
import android.graphics.Shader
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import org.json.JSONObject
import java.io.File
import java.io.FileOutputStream

/** Shareable stats card - drawn on-device from my_ranks / my_header / my_player_card. Never shows UID or phone. */
object ShareCard {
    private const val W = 1080
    private const val H = 1350

    suspend fun build(ctx: android.content.Context): File? = withContext(Dispatchers.IO) {
        runCatching {
            val ranks = runCatching { Api.authedRpc("my_ranks", JSONObject()) }.getOrNull()
            val hdr = runCatching { Api.authedRpc("my_header", JSONObject()) }.getOrNull()
            val card = runCatching { Api.authedRpc("my_player_card", JSONObject()) }.getOrNull()
            val name = card?.s("ign")?.ifBlank { card.s("username") }?.ifBlank { card.s("name") }?.ifBlank { "XARENA Player" } ?: "XARENA Player"
            val kd = card?.let { if (it.isNull("kd")) null else it.optDouble("kd") }
            val wins = card?.optInt("wins") ?: 0

            val bmp = Bitmap.createBitmap(W, H, Bitmap.Config.ARGB_8888)
            val c = Canvas(bmp)
            // background
            val bg = Paint()
            bg.shader = LinearGradient(0f, 0f, 0f, H.toFloat(), GColor.parseColor("#1A0E33"), GColor.parseColor("#05010C"), Shader.TileMode.CLAMP)
            c.drawRect(0f, 0f, W.toFloat(), H.toFloat(), bg)
            // gold border
            val border = Paint(Paint.ANTI_ALIAS_FLAG).apply { style = Paint.Style.STROKE; strokeWidth = 6f; color = GColor.parseColor("#FFD700") }
            c.drawRect(36f, 36f, (W - 36).toFloat(), (H - 36).toFloat(), border)
            val gold = Paint(Paint.ANTI_ALIAS_FLAG).apply { color = GColor.parseColor("#FFD700"); textAlign = Paint.Align.CENTER }
            val white = Paint(Paint.ANTI_ALIAS_FLAG).apply { color = GColor.WHITE; textAlign = Paint.Align.CENTER }
            val muted = Paint(Paint.ANTI_ALIAS_FLAG).apply { color = GColor.parseColor("#B8A8D9"); textAlign = Paint.Align.CENTER }
            // brand
            gold.textSize = 92f; gold.isFakeBoldText = true
            c.drawText("XARENA", W / 2f, 220f, gold)
            muted.textSize = 34f
            c.drawText("E S P O R T S", W / 2f, 272f, muted)
            // player
            white.textSize = 72f; white.isFakeBoldText = true
            c.drawText(name, W / 2f, 430f, white)
            gold.textSize = 44f
            c.drawText("LEVEL " + (hdr?.optInt("level") ?: 1), W / 2f, 500f, gold)
            // stat boxes
            fun box(x: Float, label: String, value: String) {
                val p = Paint(Paint.ANTI_ALIAS_FLAG).apply { color = GColor.parseColor("#22FFD700"); style = Paint.Style.FILL }
                c.drawRoundRect(x, 580f, x + 300f, 760f, 24f, 24f, p)
                val b = Paint(Paint.ANTI_ALIAS_FLAG).apply { color = GColor.parseColor("#FFD700"); style = Paint.Style.STROKE; strokeWidth = 3f }
                c.drawRoundRect(x, 580f, x + 300f, 760f, 24f, 24f, b)
                muted.textSize = 30f
                c.drawText(label, x + 150f, 645f, muted)
                white.textSize = 52f
                c.drawText(value, x + 150f, 715f, white)
            }
            box(70f, "EXP", "%,d".format(hdr?.optInt("exp") ?: ranks?.optInt("xp") ?: 0))
            box(390f, "AP", "%,d".format(hdr?.optInt("ap") ?: 0))
            box(710f, "WINS", wins.toString())
            // ranks
            gold.textSize = 40f
            c.drawText("MY RANKS", W / 2f, 880f, gold)
            white.textSize = 46f; white.isFakeBoldText = false
            val g = ranks?.optInt("global") ?: 0
            val st = ranks?.optInt("state") ?: 0
            val di = ranks?.optInt("district") ?: 0
            c.drawText("Global " + (if (g > 0) "#" + g else "-"), W / 2f, 950f, white)
            if (st > 0) c.drawText((ranks?.s("state_name")?.takeIf { it.isNotBlank() } ?: "State") + " #" + st, W / 2f, 1015f, white)
            if (di > 0) c.drawText((ranks?.s("district_name")?.takeIf { it.isNotBlank() } ?: "District") + " #" + di, W / 2f, 1080f, white)
            kd?.let { muted.textSize = 36f; c.drawText("K/D %.1f".format(it), W / 2f, 1150f, muted) }
            // footer
            muted.textSize = 30f
            c.drawText("xarena - free esports, real ranks", W / 2f, (H - 80).toFloat(), muted)

            val dir = File(ctx.cacheDir, "share").apply { mkdirs() }
            val f = File(dir, "xarena-stats.png")
            FileOutputStream(f).use { bmp.compress(Bitmap.CompressFormat.PNG, 100, it) }
            f
        }.getOrNull()
    }

    fun share(ctx: android.content.Context, f: File) {
        val uri = androidx.core.content.FileProvider.getUriForFile(ctx, "app.xarena.esports.native.fileprovider", f)
        val i = android.content.Intent(android.content.Intent.ACTION_SEND).apply {
            type = "image/png"
            putExtra(android.content.Intent.EXTRA_STREAM, uri)
            putExtra(android.content.Intent.EXTRA_TEXT, "My XARENA stats")
            addFlags(android.content.Intent.FLAG_GRANT_READ_URI_PERMISSION)
        }
        ctx.startActivity(android.content.Intent.createChooser(i, "Share your stats card"))
    }
}
