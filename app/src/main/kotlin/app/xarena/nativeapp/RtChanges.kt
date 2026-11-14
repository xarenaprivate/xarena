package app.xarena.nativeapp

import java.io.DataOutputStream
import java.net.HttpURLConnection
import java.net.URL
import java.net.URLEncoder
import java.util.zip.GZIPInputStream
import org.json.JSONArray
import org.json.JSONObject

/* Minimal Supabase Realtime (Phoenix) client for postgres_changes, authenticated with the
   logged-in user's JWT (backend contract 2026-11-14: publication has chat_messages,
   dm_messages, squad_room_messages; subscribe INSERT with a filter and treat events as
   invalidate+refetch through the existing authorized RPCs - payloads are never trusted
   as permission). Slow polling stays as fallback, so worst case is today's behavior. */
class RtChanges(
    private val topic: String,
    private val table: String,
    private val filter: String,
    private val onInvalidate: () -> Unit
) {
    private var out: DataOutputStream? = null
    @Volatile private var closed = false
    @Volatile private var joined = false
    private var ws: HttpURLConnection? = null
    private var ref = 0

    fun connect() {
        val t = Thread({
            var attempt = 0
            while (!closed) {
                try {
                    open()
                    attempt = 0
                } catch (e: Exception) {
                    if (!closed) Thread.sleep(if (attempt < 4) 2000L * (attempt + 1) else 15000L)
                    attempt++
                }
            }
        }, "rt-changes")
        t.isDaemon = true
        t.start()
    }

    fun close() {
        closed = true
        try { ws?.disconnect() } catch (e: Exception) { }
    }

    private fun open() {
        val tok = URLEncoder.encode(Api.access ?: "", "UTF-8")
        val u = URL(Api.BASE + "/realtime/v1/websocket?apikey=" + Api.KEY + "&vsn=2.0.0" + (if (tok.isEmpty()) "" else "&access_token=" + tok))
        val c = u.openConnection() as HttpURLConnection
        ws = c
        c.requestMethod = "GET"
        c.setRequestProperty("Connection", "Upgrade")
        c.setRequestProperty("Upgrade", "websocket")
        c.setRequestProperty("Sec-WebSocket-Version", "13")
        val keyBytes = ByteArray(16)
        java.security.SecureRandom().nextBytes(keyBytes)
        c.setRequestProperty("Sec-WebSocket-Key", android.util.Base64.encodeToString(keyBytes, android.util.Base64.NO_WRAP))
        c.connectTimeout = 15000
        c.readTimeout = 0
        c.connect()
        if (c.responseCode != 101) throw Exception("realtime handshake " + c.responseCode)
        val inp = c.inputStream
        out = DataOutputStream(c.outputStream)
        val change = JSONObject().put("event", "INSERT").put("schema", "public").put("table", table).put("filter", filter).put("id", 1)
        send("phx_join", JSONObject().put("config", JSONObject()
            .put("broadcast", JSONObject().put("ack", false).put("self", false))
            .put("presence", JSONObject().put("key", ""))
            .put("postgres_changes", JSONArray().put(change))
            .put("private", false)))
        val hb = Thread({
            while (!closed) {
                Thread.sleep(25000)
                if (closed) break
                try { send("heartbeat", JSONObject()) } catch (e: Exception) { break }
            }
        }, "rt-hb")
        hb.isDaemon = true
        hb.start()
        readLoop(inp)
        try { c.disconnect() } catch (e: Exception) { }
        joined = false
        throw Exception("realtime closed")
    }

    private fun readLoop(inp: java.io.InputStream) {
        var extended: JSONObject? = null
        while (!closed) {
            val b1 = inp.read(); if (b1 < 0) break
            val b2 = inp.read(); if (b2 < 0) break
            val opcode = b1 and 0x0f
            val fin = (b1 and 0x80) != 0
            var len = (b2 and 0x7f).toLong()
            if (len == 126L) { len = ((inp.read() shl 8) or inp.read()).toLong() }
            else if (len == 127L) { len = 0; for (i in 0 until 8) len = (len shl 8) or inp.read().toLong() }
            val payload = ByteArray(len.toInt())
            var off = 0
            while (off < payload.size) {
                val n = inp.read(payload, off, payload.size - off)
                if (n < 0) throw Exception("eof")
                off += n
            }
            if (opcode == 9) { sendFrame(10, payload); continue }
            if (opcode == 8) break
            if (opcode == 1 || opcode == 0) {
                val chunk = if (opcode == 0) payload else GZIPInputStream(payload.inputStream()).readBytes()
                val frag = String(chunk, Charsets.UTF_8)
                if (fin && extended == null) { handle(JSONObject(frag)); extended = null }
                else { extended = (extended ?: JSONObject()).put((extended?.length() ?: 0).toString(), frag); if (fin) { handle(extended!!); extended = null } }
            }
        }
    }

    private fun handle(m: JSONObject) {
        val ev = m.optString("event")
        if (ev == "phx_reply" && m.optJSONObject("payload")?.optString("status") == "ok") {
            if (m.optString("topic") == topic && !joined) { joined = true; onInvalidate() }
        } else if (ev == "postgres_changes") {
            onInvalidate()
        } else if (ev == "phx_error" || ev == "phx_close") {
            try { ws?.disconnect() } catch (e: Exception) { }
        }
    }

    private fun send(event: String, payload: JSONObject) {
        ref++
        val msg = JSONObject().put("topic", if (event == "heartbeat") "phoenix" else topic).put("event", event)
            .put("payload", payload).put("ref", ref.toString())
        val raw = msg.toString().toByteArray(Charsets.UTF_8)
        val bos = java.io.ByteArrayOutputStream()
        val gz = java.util.zip.GZIPOutputStream(bos)
        gz.write(raw); gz.close()
        synchronized(this) { sendFrame(2, bos.toByteArray()) }
    }

    private fun sendFrame(opcode: Int, payload: ByteArray) {
        val o = out ?: return
        o.writeByte(0x80 or opcode)
        val mask = ByteArray(4)
        java.security.SecureRandom().nextBytes(mask)
        if (payload.size < 126) o.writeByte(0x80 or payload.size)
        else if (payload.size < 65536) { o.writeByte(0x80 or 126); o.writeByte((payload.size shr 8) and 0xff); o.writeByte(payload.size and 0xff) }
        else { o.writeByte(0x80 or 127); for (i in 7 downTo 0) o.writeByte(((payload.size.toLong() shr (8 * i)) and 0xff).toInt()) }
        o.write(mask)
        val masked = ByteArray(payload.size)
        for (i in payload.indices) masked[i] = (payload[i].toInt() xor mask[i % 4].toInt()).toByte()
        o.write(masked); o.flush()
    }
}
