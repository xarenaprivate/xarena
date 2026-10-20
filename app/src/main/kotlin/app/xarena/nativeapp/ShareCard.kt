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

    /** Season recap card - season-flavoured variant of the stats card. Same live sources, same privacy rule (no UID/phone). */
    suspend fun buildRecap(ctx: android.content.Context): File? = withContext(Dispatchers.IO) {
        runCatching {
            val gs = runCatching { Api.authedRpc("my_game_stats", JSONObject()) }.getOrNull()
            val card = runCatching { Api.authedRpc("my_player_card", JSONObject()) }.getOrNull()
            val hdr = runCatching { Api.authedRpc("my_header", JSONObject()) }.getOrNull()
            val name = card?.s("ign")?.ifBlank { card.s("username") }?.ifBlank { card.s("name") }?.ifBlank { "XARENA Player" } ?: "XARENA Player"

            val bmp = Bitmap.createBitmap(W, H, Bitmap.Config.ARGB_8888)
            val c = Canvas(bmp)
            val bg = Paint()
            bg.shader = LinearGradient(0f, 0f, W.toFloat(), H.toFloat(), GColor.parseColor("#070707"), GColor.parseColor("#1a1405"), Shader.TileMode.CLAMP)
            c.drawRect(0f, 0f, W.toFloat(), H.toFloat(), bg)
            val border = Paint(Paint.ANTI_ALIAS_FLAG).apply { style = Paint.Style.STROKE; strokeWidth = 6f; color = GColor.parseColor("#D4A017") }
            c.drawRect(36f, 36f, (W - 36).toFloat(), (H - 36).toFloat(), border)
            val gold = Paint(Paint.ANTI_ALIAS_FLAG).apply { color = GColor.parseColor("#F2CF6B"); textAlign = Paint.Align.CENTER }
            val white = Paint(Paint.ANTI_ALIAS_FLAG).apply { color = GColor.WHITE; textAlign = Paint.Align.CENTER }
            val muted = Paint(Paint.ANTI_ALIAS_FLAG).apply { color = GColor.parseColor("#F6EFDC"); textAlign = Paint.Align.CENTER }

            gold.textSize = 84f; gold.isFakeBoldText = true
            c.drawText("XARENA", W / 2f, 200f, gold)
            muted.textSize = 36f; muted.isFakeBoldText = false
            c.drawText("S E A S O N   R E C A P", W / 2f, 258f, muted)
            white.textSize = 68f; white.isFakeBoldText = true
            c.drawText(name, W / 2f, 420f, white)
            gold.textSize = 42f
            c.drawText("LEVEL " + (hdr?.optInt("level") ?: 1), W / 2f, 486f, gold)

            data class Cell(val label: String, val value: String)
            val cells = mutableListOf<Cell>()
            gs?.let {
                if (it.optInt("streak", -1) >= 0) cells.add(Cell("BEST STREAK", it.optInt("streak").toString() + " days"))
                if (it.has("xp")) cells.add(Cell("XP", "%,d".format(it.optInt("xp"))))
            }
            card?.let {
                if (!it.isNull("wins")) cells.add(Cell("WINS", it.optInt("wins").toString()))
                if (!it.isNull("kd")) cells.add(Cell("K/D", "%.1f".format(it.optDouble("kd"))))
                if (!it.isNull("matches")) cells.add(Cell("MATCHES", it.optInt("matches").toString()))
            }
            hdr?.let { if (!it.isNull("ap")) cells.add(Cell("AP", "%,d".format(it.optInt("ap")))) }
            if (cells.isEmpty()) cells.add(Cell("SEASON", "Just getting started"))

            var y = 620f
            cells.take(6).chunked(2).forEach { row ->
                row.forEachIndexed { i, cell ->
                    val x = 90f + i * 460f
                    val p = Paint(Paint.ANTI_ALIAS_FLAG).apply { color = GColor.parseColor("#1A1206"); style = Paint.Style.FILL }
                    c.drawRoundRect(x, y, x + 420f, y + 180f, 24f, 24f, p)
                    val b = Paint(Paint.ANTI_ALIAS_FLAG).apply { color = GColor.parseColor("#D4A017"); style = Paint.Style.STROKE; strokeWidth = 3f }
                    c.drawRoundRect(x, y, x + 420f, y + 180f, 24f, 24f, b)
                    muted.textSize = 30f
                    c.drawText(cell.label, x + 210f, y + 66f, muted)
                    white.textSize = 48f; white.isFakeBoldText = true
                    c.drawText(cell.value, x + 210f, y + 132f, white)
                }
                y += 220f
            }
            muted.textSize = 30f; muted.isFakeBoldText = false
            c.drawText("xarena - free esports, real ranks", W / 2f, (H - 80).toFloat(), muted)

            val dir = File(ctx.cacheDir, "share").apply { mkdirs() }
            val out = File(dir, "xarena-season-recap.png")
            FileOutputStream(out).use { bmp.compress(Bitmap.CompressFormat.PNG, 100, it) }
            out
        }.getOrNull()
    }

    /** IG/WhatsApp story poster (1080x1920) for a tournament - real tournament row data only. */
    suspend fun buildStory(ctx: android.content.Context, t: JSONObject): File? = withContext(Dispatchers.IO) {
        runCatching {
            val SW = 1080; val SH = 1920
            val bmp = Bitmap.createBitmap(SW, SH, Bitmap.Config.ARGB_8888)
            val c = Canvas(bmp)
            val bg = Paint()
            bg.shader = LinearGradient(0f, 0f, 0f, SH.toFloat(), GColor.parseColor("#120A24"), GColor.parseColor("#05010C"), Shader.TileMode.CLAMP)
            c.drawRect(0f, 0f, SW.toFloat(), SH.toFloat(), bg)
            // gold frame
            val border = Paint(Paint.ANTI_ALIAS_FLAG).apply { style = Paint.Style.STROKE; strokeWidth = 8f; color = GColor.parseColor("#F5C451") }
            c.drawRect(40f, 40f, (SW - 40).toFloat(), (SH - 40).toFloat(), border)
            val gold = Paint(Paint.ANTI_ALIAS_FLAG).apply { color = GColor.parseColor("#F5C451"); textAlign = Paint.Align.CENTER }
            val gold2 = Paint(Paint.ANTI_ALIAS_FLAG).apply { color = GColor.parseColor("#FFE9A3"); textAlign = Paint.Align.CENTER }
            val white = Paint(Paint.ANTI_ALIAS_FLAG).apply { color = GColor.WHITE; textAlign = Paint.Align.CENTER }
            val muted = Paint(Paint.ANTI_ALIAS_FLAG).apply { color = GColor.parseColor("#B79CE0"); textAlign = Paint.Align.CENTER }

            gold.textSize = 96f; gold.isFakeBoldText = true
            c.drawText("XARENA", SW / 2f, 320f, gold)
            muted.textSize = 36f; muted.isFakeBoldText = false
            c.drawText("E S P O R T S", SW / 2f, 380f, muted)

            // tournament name, wrapped to 2 lines
            val name = t.s("name").ifBlank { "TOURNAMENT" }
            white.textSize = 76f; white.isFakeBoldText = true
            val words = name.split(" ")
            var line1 = ""; var line2 = ""
            for (w in words) { if ((line1 + " " + w).trim().length <= 14 && line2.isEmpty()) line1 = (line1 + " " + w).trim() else line2 = (line2 + " " + w).trim() }
            c.drawText(line1, SW / 2f, 640f, white)
            if (line2.isNotBlank()) c.drawText(line2, SW / 2f, 740f, white)

            gold2.textSize = 44f
            val date = t.s("date_text")
            if (date.isNotBlank()) c.drawText(date, SW / 2f, 900f, gold2)
            val slots = t.optInt("slots", 0)
            if (slots > 0) { gold.textSize = 120f; c.drawText(slots.toString(), SW / 2f, 1080f, gold); muted.textSize = 34f; c.drawText("SQUAD SLOTS", SW / 2f, 1130f, muted) }
            val prize = t.s("prize")
            if (prize.isNotBlank()) { gold2.textSize = 48f; c.drawText(prize, SW / 2f, 1260f, gold2) }

            white.textSize = 42f; white.isFakeBoldText = true
            c.drawText("FREE ENTRY - REGISTER IN THE APP", SW / 2f, 1500f, white)
            muted.textSize = 32f; muted.isFakeBoldText = false
            c.drawText("xarenaprivate.github.io/xarena", SW / 2f, (SH - 120).toFloat(), muted)

            val dir = File(ctx.cacheDir, "share").apply { mkdirs() }
            val out = File(dir, "xarena-story.png")
            FileOutputStream(out).use { bmp.compress(Bitmap.CompressFormat.PNG, 100, it) }
            out
        }.getOrNull()
    }

    fun share(ctx: android.content.Context, f: File) {
        val uri = androidx.core.content.FileProvider.getUriForFile(ctx, "app.xarena.esports.native.fileprovider", f)
        val i = android.content.Intent(android.content.Intent.ACTION_SEND).apply {
            type = "image/png"
            putExtra(android.content.Intent.EXTRA_STREAM, uri)
            putExtra(android.content.Intent.EXTRA_TEXT, if (f.name.contains("recap")) "My XARENA season recap" else "My XARENA stats")
            addFlags(android.content.Intent.FLAG_GRANT_READ_URI_PERMISSION)
        }
        ctx.startActivity(android.content.Intent.createChooser(i, "Share your stats card"))
    }
}
