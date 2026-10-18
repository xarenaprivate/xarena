package app.xarena.nativeapp

import android.content.Context
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import org.json.JSONArray
import org.json.JSONObject
import java.net.HttpURLConnection
import java.net.URL

/** Thin client for the SAME Supabase backend the website and the WebView app use. */
object Api {
    const val BASE = "https://oskccuajgbyabogbvedj.supabase.co"
    const val KEY = "sb_publishable_RltZjW7bXLT9DssLJVHkDA_cuHlcJo7"
    private lateinit var ctx: Context
    fun init(c: Context) { ctx = c.applicationContext }
    private val prefs get() = ctx.getSharedPreferences("xr_native_session", Context.MODE_PRIVATE)

    var access: String? = null; private set
    var uid: String? = null; private set
    val loggedIn get() = access != null

    fun restore() { access = prefs.getString("access", null); uid = prefs.getString("uid", null); restoreChat() }

    class ApiError(msg: String) : Exception(msg)

    private fun call(method: String, path: String, body: JSONObject?, token: String?): Pair<Int, String> {
        val c = URL(BASE + path).openConnection() as HttpURLConnection
        c.requestMethod = method
        c.connectTimeout = 15000; c.readTimeout = 20000
        c.setRequestProperty("apikey", KEY)
        c.setRequestProperty("Authorization", "Bearer " + (token ?: KEY))
        c.setRequestProperty("Content-Type", "application/json")
        if (body != null) { c.doOutput = true; c.outputStream.use { it.write(body.toString().toByteArray()) } }
        val code = c.responseCode
        val text = (if (code in 200..299) c.inputStream else c.errorStream)?.bufferedReader()?.readText() ?: ""
        return code to text
    }

    private fun saveSession(j: JSONObject) {
        access = j.getString("access_token")
        uid = j.getJSONObject("user").getString("id")
        prefs.edit().putString("access", access).putString("refresh", j.optString("refresh_token"))
            .putString("uid", uid).apply()
    }

    /** Same rule as the website's normPhone(): 10 digits -> "91"+digits, or already 91XXXXXXXXXX. */
    private fun normPhone(v: String): String? {
        val d = v.filter { it.isDigit() }
        if (Regex("^[0-9]{10}$").matches(d)) return "91" + d
        if (Regex("^91[0-9]{10}$").matches(d)) return d
        return null
    }

    suspend fun login(identifier: String, password: String) = withContext(Dispatchers.IO) {
        val id = identifier.trim()
        val email = if (id.contains("@")) id else {
            val ph = normPhone(id) ?: throw ApiError("Enter your 10-digit phone number or email.")
            "p$ph@phone.xarena"
        }
        val (code, text) = call("POST", "/auth/v1/token?grant_type=password",
            JSONObject().put("email", email).put("password", password), null)
        if (code !in 200..299) throw ApiError(if (code == 400) "Wrong phone/email or password." else "Login failed ($code).")
        saveSession(JSONObject(text))
    }

    /** Phone signup identical to the site: phone_available check, then Supabase signup with phone metadata. */
    suspend fun signup(phone: String, password: String) = withContext(Dispatchers.IO) {
        val ph = normPhone(phone) ?: throw ApiError("Phone number must be 10 digits.")
        val (c1, t1) = call("POST", "/rest/v1/rpc/phone_available", JSONObject().put("p_phone", ph), null)
        if (c1 !in 200..299) throw ApiError("Could not check right now. Try again.")
        val chk = JSONObject(t1)
        if (!chk.optBoolean("ok")) throw ApiError(chk.optString("error").ifBlank { "Could not check right now. Try again." })
        if (!chk.optBoolean("available")) throw ApiError("An account already exists with this number. Please log in.")
        val (c2, t2) = call("POST", "/auth/v1/signup",
            JSONObject().put("email", "p$ph@phone.xarena").put("password", password)
                .put("data", JSONObject().put("phone", ph)), null)
        if (c2 !in 200..299) {
            val msg = try { JSONObject(t2).optString("msg").ifBlank { JSONObject(t2).optString("message") } } catch (e: Exception) { "" }
            throw ApiError(if (msg.isNotBlank()) msg else "Signup failed ($c2).")
        }
        val j = JSONObject(t2)
        if (j.has("access_token")) saveSession(j)
        else throw ApiError("Account created. Please log in.")
    }

    /** One-time profile setup (same RPC the site's account page uses). */
    suspend fun completeProfile(username: String, ign: String, ffUid: String) = withContext(Dispatchers.IO) {
        val r = JSONObject(authed("POST", "/rest/v1/rpc/complete_profile",
            JSONObject().put("p_username", username.trim()).put("p_ign", ign.trim()).put("p_ff_uid", ffUid.trim())))
        if (!r.optBoolean("ok")) throw ApiError(r.optString("error").ifBlank { "Could not save. Try again." })
    }

    private fun refresh(): Boolean {
        val r = prefs.getString("refresh", null) ?: return false
        val (code, text) = call("POST", "/auth/v1/token?grant_type=refresh_token", JSONObject().put("refresh_token", r), null)
        if (code !in 200..299) return false
        saveSession(JSONObject(text)); return true
    }

    fun logout() { access = null; uid = null; prefs.edit().clear().apply() }

    /** Authenticated GET/RPC with one automatic token refresh. */
    private fun authed(method: String, path: String, body: JSONObject?): String {
        var (code, text) = call(method, path, body, access)
        if (code == 401 && refresh()) { val r = call(method, path, body, access); code = r.first; text = r.second }
        if (code !in 200..299) throw ApiError("Request failed ($code).")
        return text
    }

    suspend fun publicGet(path: String): JSONArray = withContext(Dispatchers.IO) {
        val (code, text) = call("GET", path, null, null)
        if (code !in 200..299) throw ApiError("Could not load data ($code).")
        JSONArray(text)
    }

    suspend fun publicRpc(name: String, args: JSONObject): JSONObject = withContext(Dispatchers.IO) {
        val (code, text) = call("POST", "/rest/v1/rpc/$name", args, null)
        if (code !in 200..299) throw ApiError("Could not load data ($code).")
        JSONObject(text)
    }

    /** True when the account has no real profile yet (site shows the complete-profile form in that case). */
    suspend fun needsProfileSetup(): Boolean = withContext(Dispatchers.IO) {
        val u = uid ?: return@withContext false
        val arr = JSONArray(authed("GET", "/rest/v1/profiles?id=eq.$u&select=username", null))
        if (arr.length() == 0) return@withContext true
        Regex("^user[0-9a-f]{8}$").matches(arr.getJSONObject(0).optString("username"))
    }

    suspend fun myProfile(): JSONObject? = withContext(Dispatchers.IO) {
        val u = uid ?: return@withContext null
        val arr = JSONArray(authed("GET", "/rest/v1/profiles?id=eq.$u&select=username,ign,ff_uid,verified,role,ff_rank,matches,wins,phone,phone_verified,phone_code", null))
        if (arr.length() > 0) arr.getJSONObject(0) else null
    }





    /* ---- Tournament registration (same flow as the site's reg-form) ---- */

    fun deviceId(): String {
        var d = prefs.getString("device_id", null)
        if (d == null) {
            d = "dev-" + java.util.UUID.randomUUID().toString().replace("-", "").take(16)
            prefs.edit().putString("device_id", d).apply()
        }
        return d
    }

    /** Upload the UID proof image to the public proofs bucket, exactly like the site's uploadProof(). Returns the stored path. */
    suspend fun uploadProof(regId: String, jpeg: ByteArray): String = withContext(Dispatchers.IO) {
        val path = "$regId.jpg"
        val c = java.net.URL(BASE + "/storage/v1/object/proofs/" + path).openConnection() as java.net.HttpURLConnection
        c.requestMethod = "POST"
        c.connectTimeout = 15000; c.readTimeout = 30000
        c.setRequestProperty("apikey", KEY)
        c.setRequestProperty("Authorization", "Bearer $KEY")
        c.setRequestProperty("Content-Type", "image/jpeg")
        c.doOutput = true
        c.outputStream.use { it.write(jpeg) }
        val code = c.responseCode
        if (code !in 200..299) throw ApiError("Proof upload failed ($code). Check your connection and try again.")
        path
    }

    suspend fun registerSquad(id: String, squad: String, captain: String, uid: String, phone: String, proofPath: String?): JSONObject =
        withContext(Dispatchers.IO) {
            authedRpc("register_squad", JSONObject()
                .put("p_id", id).put("p_tournament", "xr-s1").put("p_squad", squad)
                .put("p_captain", captain).put("p_uid", uid).put("p_ign", captain)
                .put("p_phone", phone).put("p_proof", proofPath ?: JSONObject.NULL)
                .put("p_client", deviceId()).put("p_ip", JSONObject.NULL))
        }

    fun myRegsLoad(): JSONArray = try { JSONArray(prefs.getString("my_regs", "[]")) } catch (e: Exception) { JSONArray() }
    fun myRegsAdd(id: String, squad: String) {
        val a = myRegsLoad()
        a.put(JSONObject().put("id", id).put("squad", squad).put("at", System.currentTimeMillis()))
        prefs.edit().putString("my_regs", a.toString()).apply()
    }

    /* ---- Squads (same RPCs/views the site uses) ---- */

    suspend fun authedGetArray(path: String): JSONArray = withContext(Dispatchers.IO) {
        JSONArray(authed("GET", path, null))
    }

    suspend fun authedRpc(name: String, args: JSONObject): JSONObject = withContext(Dispatchers.IO) {
        JSONObject(authed("POST", "/rest/v1/rpc/$name", args))
    }

    suspend fun squadsPublic(): JSONArray = withContext(Dispatchers.IO) {
        publicGet("/rest/v1/squads_public?select=*")
    }

    /* ---- Luna (same luna-chat Edge Function the site uses) ---- */

    class LunaOffline(msg: String) : Exception(msg)

    suspend fun lunaSend(message: String, history: JSONArray): String = withContext(Dispatchers.IO) {
        val sid = "native-" + (uid ?: "anon")
        val body = JSONObject().put("session_id", sid).put("message", message).put("history", history)
        val (code, text) = call("POST", "/functions/v1/luna-chat", body, null)
        if (code == 404 || code == 503) throw LunaOffline("Luna is offline right now. The team is bringing her back - try again later.")
        if (code !in 200..299) throw ApiError("Luna could not reply just now ($code). Try again in a bit.")
        val r = JSONObject(text).optString("reply")
        if (r.isBlank()) throw ApiError("Luna could not reply just now. Try again in a bit.")
        r
    }

    suspend fun lunaAlive(): Boolean = withContext(Dispatchers.IO) {
        try {
            val sid = "native-" + (uid ?: "anon")
            val (code, _) = call("POST", "/functions/v1/luna-chat", JSONObject().put("session_id", sid).put("ping", true), null)
            code != 404 && code != 503
        } catch (e: Exception) { false }
    }

    fun lunaHistoryLoad(): JSONArray = try { JSONArray(prefs.getString("luna_hist", "[]")) } catch (e: Exception) { JSONArray() }
    fun lunaHistorySave(h: JSONArray) { prefs.edit().putString("luna_hist", h.toString()).apply() }

    /* ---- World chat (same RPCs/table the website uses) ---- */

    var chatHandle: String? = null; private set
    var chatToken: String? = null; private set
    fun restoreChat() { chatHandle = prefs.getString("chat_handle", null); chatToken = prefs.getString("chat_token", null) }

    suspend fun chatClaim(handle: String): JSONObject = withContext(Dispatchers.IO) {
        val j = JSONObject(authed("POST", "/rest/v1/rpc/chat_claim_handle_account", JSONObject().put("p_handle", handle)))
        if (j.optBoolean("ok")) {
            chatHandle = j.optString("handle").ifBlank { handle }
            chatToken = j.optString("token")
            prefs.edit().putString("chat_handle", chatHandle).putString("chat_token", chatToken).apply()
        }
        j
    }

    fun clearChat() { chatHandle = null; chatToken = null; prefs.edit().remove("chat_handle").remove("chat_token").apply() }

    suspend fun chatSend(body: String): JSONObject = withContext(Dispatchers.IO) {
        publicRpc("chat_send", JSONObject().put("p_handle", chatHandle).put("p_token", chatToken).put("p_body", body))
    }

    suspend fun chatHistory(): JSONArray = withContext(Dispatchers.IO) {
        publicGet("/rest/v1/chat_messages?select=id,handle,body,created_at&order=id.desc&limit=80")
    }

    suspend fun chatSince(lastId: Long): JSONArray = withContext(Dispatchers.IO) {
        publicGet("/rest/v1/chat_messages?select=id,handle,body,created_at&order=id.asc&limit=80&id=gt." + lastId)
    }

    suspend fun myStats(): JSONObject? = withContext(Dispatchers.IO) {
        try { JSONObject(authed("POST", "/rest/v1/rpc/my_game_stats", JSONObject())) } catch (e: Exception) { null }
    }
}
