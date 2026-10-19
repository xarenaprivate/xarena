package app.xarena.nativeapp

import org.apache.commons.compress.compressors.bzip2.BZip2CompressorInputStream
import java.io.ByteArrayInputStream
import java.io.EOFException
import java.io.File
import java.io.InputStream

/* Pure-Kotlin bspatch (BSDIFF40 format) - applies a binary delta to the installed APK
   to reconstruct the new signed APK. No NDK needed. */
object BsPatch {
    private fun le64(b: ByteArray, off: Int): Long {
        var v = 0L
        for (i in 7 downTo 0) v = (v shl 8) or (b[off + i].toLong() and 0xff)
        return v
    }

    private fun readFully(s: InputStream, b: ByteArray, n: Int) {
        var off = 0
        while (off < n) {
            val r = s.read(b, off, n - off)
            if (r <= 0) throw EOFException()
            off += r
        }
    }

    fun apply(oldFile: File, patchFile: File, outFile: File) {
        val patch = patchFile.readBytes()
        require(patch.size > 32 && String(patch, 0, 8) == "BSDIFF40") { "not a bsdiff patch" }
        val ctrlLen = le64(patch, 8).toInt()
        val diffLen = le64(patch, 16).toInt()
        val newSize = le64(patch, 24).toInt()
        val ctrlEnd = 32 + ctrlLen
        val diffEnd = ctrlEnd + diffLen
        require(diffEnd <= patch.size) { "truncated patch" }
        val ctrl = BZip2CompressorInputStream(ByteArrayInputStream(patch, 32, ctrlLen))
        val diff = BZip2CompressorInputStream(ByteArrayInputStream(patch, ctrlEnd, diffLen))
        val extra = BZip2CompressorInputStream(ByteArrayInputStream(patch, diffEnd, patch.size - diffEnd))
        val old = oldFile.readBytes()
        val new = ByteArray(newSize)
        val cbuf = ByteArray(24)
        val buf = ByteArray(32768)
        var oldpos = 0
        var newpos = 0
        while (newpos < newSize) {
            readFully(ctrl, cbuf, 24)
            val addLen = le64(cbuf, 0).toInt()
            val copyLen = le64(cbuf, 8).toInt()
            val seek = le64(cbuf, 16)
            var remaining = addLen
            var off = 0
            while (remaining > 0) {
                val r = diff.read(buf, 0, minOf(buf.size, remaining))
                if (r <= 0) throw EOFException()
                for (j in 0 until r) {
                    val ov = if (oldpos + off + j < old.size) old[oldpos + off + j].toInt() and 0xff else 0
                    new[newpos + off + j] = (((buf[j].toInt() and 0xff) + ov) and 0xff).toByte()
                }
                off += r; remaining -= r
            }
            newpos += addLen; oldpos += addLen
            remaining = copyLen; off = 0
            while (remaining > 0) {
                val r = extra.read(buf, 0, minOf(buf.size, remaining))
                if (r <= 0) throw EOFException()
                System.arraycopy(buf, 0, new, newpos + off, r)
                off += r; remaining -= r
            }
            newpos += copyLen
            oldpos = (oldpos + seek).toInt()
        }
        ctrl.close(); diff.close(); extra.close()
        outFile.writeBytes(new)
    }
}
