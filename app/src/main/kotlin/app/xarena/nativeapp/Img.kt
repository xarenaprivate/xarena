package app.xarena.nativeapp

import android.graphics.Bitmap
import android.graphics.BitmapFactory
import java.io.ByteArrayOutputStream

/* Memory-safe image decode for 2 GB phones: never decodes a full-size camera bitmap.
   Bounds pass first, inSampleSize (powers of 2) to get near the cap, exact scale to
   finish, JPEG out. Use this for every user-picked image before upload. */
object Img {
    fun bounds(raw: ByteArray): Pair<Int, Int> {
        val o = BitmapFactory.Options().apply { inJustDecodeBounds = true }
        BitmapFactory.decodeByteArray(raw, 0, raw.size, o)
        return o.outWidth to o.outHeight
    }

    /** Decodes raw image bytes capped at maxDim on the long edge and re-encodes as JPEG.
        Returns null when the bytes are not a decodable image. */
    fun downscaleJpeg(raw: ByteArray, maxDim: Int = 1280, quality: Int = 82): ByteArray? {
        val (w, h) = bounds(raw)
        if (w <= 0 || h <= 0) return null
        var sample = 1
        while (maxOf(w, h) / sample > maxDim) sample *= 2
        val bmp = BitmapFactory.decodeByteArray(raw, 0, raw.size,
            BitmapFactory.Options().apply { inSampleSize = sample }) ?: return null
        val scaled = if (maxOf(bmp.width, bmp.height) > maxDim) {
            val f = maxDim.toFloat() / maxOf(bmp.width, bmp.height)
            Bitmap.createScaledBitmap(bmp, Math.round(bmp.width * f), Math.round(bmp.height * f), true)
        } else bmp
        val bos = ByteArrayOutputStream()
        scaled.compress(Bitmap.CompressFormat.JPEG, quality, bos)
        return bos.toByteArray()
    }
}
