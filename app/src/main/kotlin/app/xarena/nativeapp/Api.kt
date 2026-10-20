package app.xarena.nativeapp

import android.content.Context
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import org.json.JSONArray
import org.json.JSONObject
import java.net.HttpURLConnection
import java.net.URL
import java.net.URLEncoder

/** Thin client for the SAME Supabase backend the website and the WebView app use. */
object Api {
    const val BASE = "https://oskccuajgbyabogbvedj.supabase.co"
    const val KEY = "sb_publishable_RltZjW7bXLT9DssLJVHkDA_cuHlcJo7"
    lateinit var ctx: Context
    fun init(c: Context) { ctx = c.applicationContext }
    val prefs get() = ctx.getSharedPreferences("xr_native_session", Context.MODE_PRIVATE)

    var access: String? = null; private set
    var uid: String? = null; private set
    val loggedIn get() = access != null

    fun restore() { access = prefs.getString("access", null); uid = prefs.getString("uid", null); restoreChat() }

    class ApiError(msg: String) : Exception(msg)

    private fun call(method: String, path: String, body: JSONObject?, token: String?, headers: Map<String, String> = emptyMap()): Pair<Int, String> {
        val c = URL(BASE + path).openConnection() as HttpURLConnection
        c.requestMethod = method
        c.connectTimeout = 15000; c.readTimeout = 20000
        c.setRequestProperty("apikey", KEY)
        c.setRequestProperty("Authorization", "Bearer " + (token ?: KEY))
        c.setRequestProperty("Content-Type", "application/json")
        headers.forEach { (k, v) -> c.setRequestProperty(k, v) }
        if (body != null) { c.doOutput = true; c.outputStream.use { it.write(body.toString().toByteArray()) } }
        val code = c.responseCode
        val text = (if (code in 200..299) c.inputStream else c.errorStream)?.bufferedReader()?.readText() ?: ""
        return code to text
    }

    private fun saveSession(j: JSONObject) {
        access = j.getString("access_token")
        uid = j.getJSONObject("user").getString("id")
        prefs.edit().putString("access", access).putString("refresh", j.s("refresh_token"))
            .putString("uid", uid).apply()
    }

    /** Same rule as the website's normPhone(): 10 digits -> "91"+digits, or already 91XXXXXXXXXX. */
    private fun normPhone(v: String): String? {
        val d = v.filter { it.isDigit() }
        if (Regex("^[6-9][0-9]{9}$").matches(d)) return "91" + d
        if (Regex("^91[6-9][0-9]{9}$").matches(d)) return d
        return null
    }

    suspend fun login(identifier: String, password: String) = try { loginInner(identifier, password) }
        catch (e: java.io.IOException) { throw ApiError("Network problem - check your internet and try again.") }

    private suspend fun loginInner(identifier: String, password: String) = withContext(Dispatchers.IO) {
        val id = identifier.trim()
        val email = if (id.contains("@")) id else {
            val ph = normPhone(id) ?: throw ApiError("Enter your 10-digit phone number or email.")
            "p$ph@phone.xarena"
        }
        val (code, text) = call("POST", "/auth/v1/token?grant_type=password",
            JSONObject().put("email", email).put("password", password), null)
        if (code !in 200..299) throw ApiError(if (code == 400) "Wrong phone/email or password." else "Login failed ($code).")
        saveSession(JSONObject(text))
        bindDevice()
    }

    /** Phone signup identical to the site: phone_available check, then Supabase signup with phone metadata. */
    suspend fun signup(phone: String, password: String) = withContext(Dispatchers.IO) {
        if (password.length < 8) throw ApiError("Password must be at least 8 characters.")
        val ph = normPhone(phone) ?: throw ApiError("Enter a valid 10-digit Indian mobile number.")
        val (c1, t1) = call("POST", "/rest/v1/rpc/phone_available", JSONObject().put("p_phone", ph), null)
        if (c1 !in 200..299) throw ApiError("Could not check right now. Try again.")
        val chk = JSONObject(t1)
        if (!chk.optBoolean("ok")) throw ApiError(chk.s("error").ifBlank { "Could not check right now. Try again." })
        if (!chk.optBoolean("available")) throw ApiError("An account already exists with this number. Please log in.")
        // Anti-fake-account gate: rate limits, one-phone-many-accounts and 2-accounts-per-device blocks. Show its reason plainly.
        val (cp, tp) = call("POST", "/rest/v1/rpc/signup_precheck",
            JSONObject().put("p_phone", ph).put("p_device_id", DeviceIds.id).put("p_fp", DeviceIds.fp), null)
        if (cp in 200..299) {
            val pc = JSONObject(tp)
            if (!pc.optBoolean("ok") || (pc.has("available") && !pc.optBoolean("available")))
                throw ApiError(pc.s("error").ifBlank { pc.s("reason") }.ifBlank { "Sign-up is not allowed from this device right now." })
        }
        val fail = "We could not create an account with these details. If you already signed up, please log in instead."
        val (c2, t2) = try {
            call("POST", "/auth/v1/signup",
                JSONObject().put("email", "p$ph@phone.xarena").put("password", password)
                    .put("data", JSONObject().put("phone", ph)), null)
        } catch (e: java.io.IOException) { throw ApiError("Network problem - check your internet and try again.") }
        if (c2 !in 200..299) throw ApiError(fail)
        val j = JSONObject(t2)
        if (j.has("access_token")) { saveSession(j); bindDevice() }
        else throw ApiError(fail)
    }

    /** One-time profile setup (same RPC the site's account page uses). */
    suspend fun completeProfile(username: String, ign: String, ffUid: String) = withContext(Dispatchers.IO) {
        val r = JSONObject(authed("POST", "/rest/v1/rpc/complete_profile",
            JSONObject().put("p_username", username.trim()).put("p_ign", ign.trim()).put("p_ff_uid", ffUid.trim())))
        if (!r.optBoolean("ok")) throw ApiError(r.s("error").ifBlank { "Could not save. Try again." })
    }

    private fun refresh(): Boolean {
        val r = prefs.getString("refresh", null) ?: return false
        val (code, text) = call("POST", "/auth/v1/token?grant_type=refresh_token", JSONObject().put("refresh_token", r), null)
        if (code !in 200..299) return false
        saveSession(JSONObject(text)); return true
    }

    fun logout() { access = null; uid = null; prefs.edit().clear().apply() }

    /** Binds this install to the account (anti-fake-account). Best-effort; no-op when logged out. */
    fun bindDevice() {
        if (access == null) return
        runCatching {
            authed("POST", "/rest/v1/rpc/bind_device",
                JSONObject().put("p_device_id", DeviceIds.id).put("p_fp", DeviceIds.fp).put("p_platform", "android-native"))
        }
    }

    /** Authenticated GET/RPC with one automatic token refresh. */
    fun authed(method: String, path: String, body: JSONObject?): String {
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
        Regex("^user[0-9a-f]{8}$").matches(arr.getJSONObject(0).s("username"))
    }

    suspend fun myProfile(): JSONObject? = withContext(Dispatchers.IO) {
        val u = uid ?: return@withContext null
        val arr = JSONArray(authed("GET", "/rest/v1/profiles?id=eq.$u&select=username,ign,ff_uid,verified,role,ff_rank,matches,wins,phone,phone_verified,phone_code,avatar_id,avatar_url", null))
        if (arr.length() > 0) arr.getJSONObject(0) else null
    }






    /* ---- Cloudinary signed upload (match proofs), same as the site's uploadImage() ---- */

    suspend fun cloudinaryUploadVideo(bytes: ByteArray, mime: String): String = cloudinaryUploadRaw(bytes, mime, "video")
    suspend fun cloudinaryUpload(jpeg: ByteArray): String = cloudinaryUploadRaw(jpeg, "image/jpeg", "image")
    private suspend fun cloudinaryUploadRaw(bytes: ByteArray, mime: String, resource: String): String = withContext(Dispatchers.IO) {
        val sig = JSONObject(authed("POST", "/functions/v1/cloudinary-sign", JSONObject()))
        val cloud = sig.s("cloud")
        if (cloud.isBlank()) throw ApiError("Upload unavailable right now - try again shortly.")
        val boundary = "----xr" + System.currentTimeMillis()
        val c = java.net.URL("https://api.cloudinary.com/v1_1/$cloud/$resource/upload").openConnection() as java.net.HttpURLConnection
        c.requestMethod = "POST"; c.connectTimeout = 15000; c.readTimeout = 30000
        c.setRequestProperty("Content-Type", "multipart/form-data; boundary=$boundary")
        c.doOutput = true
        val w = java.io.DataOutputStream(c.outputStream)
        val crlf = "\r\n"
        fun field(name: String, value: String) {
            w.writeBytes("--$boundary" + crlf + "Content-Disposition: form-data; name=\"" + name + "\"" + crlf + crlf + value + crlf)
        }
        field("api_key", sig.s("key"))
        field("timestamp", sig.s("timestamp"))
        field("folder", sig.s("folder"))
        field("signature", sig.s("signature"))
        val ext = if (mime.startsWith("video/")) ".mp4" else ".jpg"
        w.writeBytes("--$boundary" + crlf + "Content-Disposition: form-data; name=\"file\"; filename=\"upload" + ext + "\"" + crlf + "Content-Type: " + mime + crlf + crlf)
        w.write(bytes)
        w.writeBytes(crlf + "--$boundary--" + crlf)
        w.flush()
        val code = c.responseCode
        val text = (if (code in 200..299) c.inputStream else c.errorStream)?.bufferedReader()?.readText() ?: ""
        if (code !in 200..299) throw ApiError("Upload failed ($code) - try again.")
        val url = JSONObject(text).s("secure_url")
        if (url.isBlank()) throw ApiError("Upload failed - try again.")
        url
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

    suspend fun registerSquadAp(id: String, squad: String, captain: String, uid: String, phone: String, proofPath: String?): JSONObject =
        withContext(Dispatchers.IO) {
            authedRpc("enter_tournament_with_ap", JSONObject()
                .put("p_id", id).put("p_tournament", "xr-s1").put("p_squad", squad)
                .put("p_captain", captain).put("p_uid", uid).put("p_ign", captain)
                .put("p_phone", phone).put("p_proof", proofPath ?: JSONObject.NULL)
                .put("p_client", deviceId()).put("p_ip", JSONObject.NULL))
        }

    suspend fun tournamentEntryCost(id: String): Int = withContext(Dispatchers.IO) {
        try {
            val a = publicGet("/rest/v1/tournaments?select=entry_cost_ap&id=eq." + id)
            if (a.length() > 0) a.getJSONObject(0).optInt("entry_cost_ap", 0) else 0
        } catch (e: Exception) { 0 }
    }

    fun myRegsLoad(): JSONArray = try { JSONArray(prefs.getString("my_regs", "[]")) } catch (e: Exception) { JSONArray() }
    fun myRegsAdd(id: String, squad: String) {
        val a = myRegsLoad()
        a.put(JSONObject().put("id", id).put("squad", squad).put("at", System.currentTimeMillis()))
        prefs.edit().putString("my_regs", a.toString()).apply()
    }

    /* ---- Squads (same RPCs/views the site uses) ---- */

    fun authedRaw(method: String, path: String, body: JSONObject?, headers: Map<String, String> = emptyMap()): Pair<Int, String> {
        val t = access ?: throw ApiError("Not logged in.")
        var r = call(method, path, body, t, headers)
        if (r.first == 401 && refresh()) r = call(method, path, body, access, headers)
        return r
    }

    suspend fun referralApply(code: String): JSONObject = withContext(Dispatchers.IO) {
        authedRpc("referral_apply", JSONObject().put("p_code", code))
    }

    suspend fun authedGetArray(path: String): JSONArray = withContext(Dispatchers.IO) {
        JSONArray(authed("GET", path, null))
    }

    suspend fun authedRpc(name: String, args: JSONObject): JSONObject = withContext(Dispatchers.IO) {
        JSONObject(authed("POST", "/rest/v1/rpc/$name", args))
    }

    /* Some RPCs return a bare JSONArray (e.g. active_polls drifted from object to array).
       This tolerant variant hands the raw body back so callers can branch. */
    suspend fun authedRpcRaw(name: String, args: JSONObject): String = withContext(Dispatchers.IO) {
        authed("POST", "/rest/v1/rpc/$name", args)
    }

    /* update_player_card requires the full param set (site profile.html sends all five);
       sending only p_role fails with PGRST202. Carry current values, change only the role. */
    suspend fun saveRole(role: String): JSONObject = withContext(Dispatchers.IO) {
        val uidNow = uid ?: throw ApiError("Log in first.")
        val prof = authedGetArray("/rest/v1/profiles?id=eq.$uidNow&select=device,ff_rank,kd,instagram&limit=1")
        val p = if (prof.length() > 0) prof.getJSONObject(0) else JSONObject()
        authedRpc("update_player_card", JSONObject()
            .put("p_role", role)
            .put("p_device", p.s("device"))
            .put("p_ff_rank", p.s("ff_rank"))
            .put("p_kd", if (p.opt("kd") == null || p.opt("kd") == JSONObject.NULL) JSONObject.NULL else p.optDouble("kd"))
            .put("p_instagram", p.s("instagram")))
    }

    /* squads_public (join metadata, has id) is often empty while public_squads (verified display
       view the site's Search uses) has the live rows. Merge so the list matches the site. */
    suspend fun squadsPublic(): JSONArray = withContext(Dispatchers.IO) {
        val base = publicGet("/rest/v1/squads_public?select=*")
        val extra = publicGet("/rest/v1/public_squads?select=*")
        if (extra.length() == 0) return@withContext base
        val byName = HashMap<String, JSONObject>()
        for (i in 0 until base.length()) {
            val o = base.getJSONObject(i)
            byName[o.s("name").ifBlank { o.s("squad") }.lowercase()] = o
        }
        val out = JSONArray()
        for (i in 0 until extra.length()) {
            val p = extra.getJSONObject(i)
            val key = p.s("squad").lowercase()
            val hit = byName.remove(key)
            if (hit != null) {
                hit.put("verified", p.optBoolean("verified"))
                if (p.s("logo_url").isNotBlank() && hit.s("logo_url").isBlank()) hit.put("logo_url", p.s("logo_url"))
                out.put(hit)
            } else {
                out.put(JSONObject()
                    .put("name", p.s("squad"))
                    .put("bio", if (p.optBoolean("verified")) "Verified squad" else "")
                    .put("verified", p.optBoolean("verified"))
                    .put("logo_url", p.s("logo_url")))
            }
        }
        for (o in byName.values) out.put(o)
        out
    }

    /* ---- Luna (same luna-chat Edge Function the site uses) ---- */

    class LunaOffline(msg: String) : Exception(msg)

    suspend fun lunaSend(message: String, history: JSONArray): String = withContext(Dispatchers.IO) {
        val sid = "native-" + (uid ?: "anon")
        val body = JSONObject().put("session_id", sid).put("message", message).put("history", history)
        val (code, text) = call("POST", "/functions/v1/luna-chat", body, null)
        if (code == 404 || code == 503) throw LunaOffline("Luna is offline right now. The team is bringing her back - try again later.")
        if (code !in 200..299) throw ApiError("Luna could not reply just now ($code). Try again in a bit.")
        val r = JSONObject(text).s("reply")
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
            chatHandle = j.s("handle").ifBlank { handle }
            chatToken = j.s("token")
            prefs.edit().putString("chat_handle", chatHandle).putString("chat_token", chatToken).apply()
        }
        j
    }

    fun clearChat() { chatHandle = null; chatToken = null; prefs.edit().remove("chat_handle").remove("chat_token").apply() }

    suspend fun chatSend(body: String): JSONObject = withContext(Dispatchers.IO) {
        publicRpc("chat_send", JSONObject().put("p_handle", chatHandle).put("p_token", chatToken).put("p_body", body))
    }

    suspend fun chatHistory(room: String): JSONArray = withContext(Dispatchers.IO) {
        publicGet("/rest/v1/chat_messages?select=id,handle,body,created_at&room=eq." + URLEncoder.encode(room, "UTF-8") + "&order=id.desc&limit=80")
    }

    suspend fun chatSince(lastId: Long, room: String): JSONArray = withContext(Dispatchers.IO) {
        publicGet("/rest/v1/chat_messages?select=id,handle,body,created_at&order=id.asc&limit=80&id=gt." + lastId + "&room=eq." + URLEncoder.encode(room, "UTF-8"))
    }

    /** Server-derived world-chat room from the account's coarse state: "general" or "st-<slug>". Cached so voice can reuse it; any failure falls back to General. */
    suspend fun chatMyRoom(): Pair<String, String> = withContext(Dispatchers.IO) {
        val h = chatHandle; val t = chatToken
        if (h.isNullOrBlank() || t.isNullOrBlank()) return@withContext ("general" to "General")
        try {
            val j = publicRpc("chat_my_room", JSONObject().put("p_handle", h).put("p_token", t))
            val d = j.optJSONObject("data") ?: j
            val room = d.optString("room").ifBlank { "general" }
            val label = d.optString("label").ifBlank { if (room == "general") "General" else room }
            prefs.edit().putString("chat_room", room).putString("chat_room_label", label).apply()
            room to label
        } catch (e: Exception) {
            (prefs.getString("chat_room", null) ?: "general") to (prefs.getString("chat_room_label", null) ?: "General")
        }
    }

    /** Consent-based area set: coarse STATE ONLY - no district, no exact location (district/lat/lon stay null). */
    suspend fun setMyArea(state: String): JSONObject = withContext(Dispatchers.IO) {
        JSONObject(authed("POST", "/rest/v1/rpc/set_my_area",
            JSONObject().put("p_state", state).put("p_district", JSONObject.NULL)
                .put("p_lat", JSONObject.NULL).put("p_lon", JSONObject.NULL)))
    }

    /** Same reverse lookup the site uses (Nominatim zoom 10), so the state string matches the web room slugs. Returns the state name or null. */
    suspend fun reverseGeocodeState(lat: Double, lon: Double): String? = withContext(Dispatchers.IO) {
        try {
            val u = "https://nominatim.openstreetmap.org/reverse?format=jsonv2&zoom=10&addressdetails=1&accept-language=en&lat=$lat&lon=$lon"
            val c = URL(u).openConnection() as HttpURLConnection
            c.setRequestProperty("User-Agent", "XARENA-Android")
            c.connectTimeout = 10000; c.readTimeout = 10000
            val j = JSONObject(c.inputStream.bufferedReader().use { it.readText() })
            val a = j.optJSONObject("address") ?: return@withContext null
            a.optString("state").ifBlank { a.optString("region") }.ifBlank { null }
        } catch (e: Exception) { null }
    }

    /* ---------- Creator Mode (free entry only, no money anywhere) ---------- */
    suspend fun creatorStatus(): JSONObject? = try { authedRpc("creator_status", JSONObject()) } catch (e: Exception) { null }
    suspend fun creatorDashboard(): JSONObject? = try { authedRpc("creator_dashboard", JSONObject()) } catch (e: Exception) { null }
    suspend fun creatorEventsPublic(): JSONArray = try {
        publicGet("/rest/v1/creator_events_public?select=*&status=in.(scheduled,live)&order=starts_at.asc&limit=30")
    } catch (e: Exception) { JSONArray() }
    suspend fun sponsorsPublic(): JSONArray = try {
        publicGet("/rest/v1/sponsors_public?select=id,name,tier,url,image_url&order=id.asc")
    } catch (e: Exception) { JSONArray() }
    suspend fun citySquadsOpen(): JSONArray = try {
        publicGet("/rest/v1/city_squads_public?select=*&status=eq.open&order=id.desc&limit=30")
    } catch (e: Exception) { JSONArray() }
    suspend fun myCitySquads(): JSONArray = try {
        publicGet("/rest/v1/city_squads_public?select=*&creator=eq." + uid + "&order=id.desc")
    } catch (e: Exception) { JSONArray() }
    suspend fun isVerifiedCreator(userId: String): Boolean = try {
        publicGet("/rest/v1/creators_public?select=user_id&user_id=eq." + userId + "&limit=1").length() > 0
    } catch (e: Exception) { false }
    suspend fun playerStatsCard(username: String): JSONObject? = try {
        val r = authedRpc("player_stats_card", JSONObject().put("p_username", username))
        if (r.has("username")) r else null
    } catch (e: Exception) { null }

    /* ---------- Support the Team (voluntary donation QR + optional ack) ---------- */
    /** site_settings key "support": {image_url: https..., text?: ...} or null when not configured. */
    suspend fun supportConfig(): JSONObject? = try {
        val a = publicGet("/rest/v1/site_settings?key=eq.support&select=value")
        if (a.length() == 0) null else a.getJSONObject(0).optJSONObject("value")
    } catch (e: Exception) { null }

    /** Screenshot to the private support-proofs bucket with the user's JWT (bucket policy enforces 3/day). Returns the stored path. */
    suspend fun uploadSupportProof(jpeg: ByteArray): String = withContext(Dispatchers.IO) {
        val path = uid + "/" + System.currentTimeMillis() + ".jpg"
        val c = java.net.URL(BASE + "/storage/v1/object/support-proofs/" + path).openConnection() as java.net.HttpURLConnection
        c.requestMethod = "POST"
        c.connectTimeout = 15000; c.readTimeout = 30000
        c.setRequestProperty("apikey", KEY)
        c.setRequestProperty("Authorization", "Bearer " + (access ?: KEY))
        c.setRequestProperty("Content-Type", "image/jpeg")
        c.setRequestProperty("x-upsert", "false")
        c.doOutput = true
        c.outputStream.use { it.write(jpeg) }
        val code = c.responseCode
        if (code == 400 || code == 403) throw ApiError("Limit reached (3 per day) or upload not allowed.")
        if (code !in 200..299) throw ApiError("Upload failed ($code). Check your connection and try again.")
        path
    }

    suspend fun supportAckSubmit(path: String, txn: String, name: String, consent: Boolean): JSONObject = withContext(Dispatchers.IO) {
        authedRpc("support_ack_submit", JSONObject().put("p_path", path).put("p_txn", txn).put("p_name", name).put("p_consent", consent))
    }

    /** Public supporters wall: [{name, date}] newest first (amount intentionally not shown in-app). */
    suspend fun supportSupporters(): JSONArray = withContext(Dispatchers.IO) {
        val (code, text) = call("POST", "/rest/v1/rpc/support_supporters", JSONObject(), null)
        if (code !in 200..299) throw ApiError("Could not load data ($code).")
        JSONArray(text)
    }

    suspend fun myStats(): JSONObject? = withContext(Dispatchers.IO) {
        try { JSONObject(authed("POST", "/rest/v1/rpc/my_game_stats", JSONObject())) } catch (e: Exception) { null }
    }

    /* ---------- Feed & Stories ---------- */
    suspend fun feedList(before: Long?): JSONArray = withContext(Dispatchers.IO) {
        val args = JSONObject()
        if (before != null) args.put("p_before", before)
        authedRpc("feed_list", args).optJSONArray("posts") ?: JSONArray()
    }
    suspend fun feedSaved(): JSONArray = withContext(Dispatchers.IO) {
        authedRpc("feed_saved", JSONObject()).optJSONArray("posts") ?: JSONArray()
    }
    suspend fun feedCreate(kind: String, mediaPath: String?, caption: String?): JSONObject = withContext(Dispatchers.IO) {
        val a = JSONObject().put("p_kind", kind)
        if (mediaPath != null) a.put("p_media_path", mediaPath)
        if (!caption.isNullOrBlank()) a.put("p_caption", caption.trim())
        authedRpc("feed_create", a)
    }
    suspend fun feedToggleLike(post: Long): JSONObject = withContext(Dispatchers.IO) { authedRpc("feed_toggle_like", JSONObject().put("p_post", post)) }
    suspend fun feedToggleSave(post: Long): JSONObject = withContext(Dispatchers.IO) { authedRpc("feed_toggle_save", JSONObject().put("p_post", post)) }
    suspend fun feedComments(post: Long): JSONArray = withContext(Dispatchers.IO) {
        val r = authedRpc("feed_comments_list", JSONObject().put("p_post", post))
        r.optJSONArray("comments") ?: r.optJSONArray("items") ?: JSONArray()
    }
    suspend fun feedComment(post: Long, body: String): JSONObject = withContext(Dispatchers.IO) {
        authedRpc("feed_comment", JSONObject().put("p_post", post).put("p_body", body.trim()))
    }
    suspend fun feedMediaUpload(bytes: ByteArray, ext: String, mime: String): String = withContext(Dispatchers.IO) {
        val clean = ext.lowercase().replace(Regex("[^a-z0-9]"), "").ifBlank { "jpg" }
        val path = (uid ?: "anon") + "/" + System.currentTimeMillis() + "." + clean
        val tok = access ?: throw ApiError("Log in to post media.")
        val c = java.net.URL(BASE + "/storage/v1/object/feed-media/" + path).openConnection() as java.net.HttpURLConnection
        c.requestMethod = "POST"
        c.connectTimeout = 15000; c.readTimeout = 60000
        c.setRequestProperty("apikey", KEY)
        c.setRequestProperty("Authorization", "Bearer " + tok)
        c.setRequestProperty("Content-Type", mime)
        c.setRequestProperty("x-upsert", "false")
        c.doOutput = true
        c.outputStream.use { it.write(bytes) }
        val code = c.responseCode
        if (code !in 200..299) throw ApiError("Upload failed (" + code + "). Check your connection and try again.")
        path
    }
    suspend fun storyTrays(): JSONArray = withContext(Dispatchers.IO) {
        authedRpc("story_trays", JSONObject()).optJSONArray("trays") ?: JSONArray()
    }
    suspend fun storyList(author: String): JSONObject = withContext(Dispatchers.IO) {
        authedRpc("story_list", JSONObject().put("p_author", author))
    }
    suspend fun storyCreate(kind: String, mediaPath: String?, caption: String?): JSONObject = withContext(Dispatchers.IO) {
        val a = JSONObject().put("p_kind", kind)
        if (mediaPath != null) a.put("p_media_path", mediaPath)
        if (!caption.isNullOrBlank()) a.put("p_caption", caption.trim())
        authedRpc("story_create", a)
    }
    suspend fun storyMarkSeen(story: Long) = withContext(Dispatchers.IO) { runCatching { authedRpc("story_mark_seen", JSONObject().put("p_story", story)) } }
    suspend fun storyDelete(story: Long): JSONObject = withContext(Dispatchers.IO) { authedRpc("story_delete", JSONObject().put("p_story", story)) }


    /* ---------- Private Messages (E2E: DmCrypto) ---------- */
    suspend fun dmPublishKey(jwk: JSONObject) = withContext(Dispatchers.IO) {
        val tok = access ?: return@withContext
        val c = java.net.URL(BASE + "/rest/v1/dm_public_keys").openConnection() as java.net.HttpURLConnection
        c.requestMethod = "POST"
        c.connectTimeout = 15000; c.readTimeout = 15000
        c.setRequestProperty("apikey", KEY)
        c.setRequestProperty("Authorization", "Bearer " + tok)
        c.setRequestProperty("Content-Type", "application/json")
        c.setRequestProperty("Prefer", "return=minimal,resolution=merge-duplicates")
        c.doOutput = true
        val body = JSONObject().put("user_id", uid).put("public_key", jwk).toString()
        c.outputStream.use { it.write(body.toByteArray(Charsets.UTF_8)) }
        c.responseCode
        c.disconnect()
    }
    suspend fun dmMyPublishedJwk(): JSONObject? = withContext(Dispatchers.IO) {
        val rows = authedGetArray("/rest/v1/dm_public_keys?user_id=eq." + uid + "&select=public_key")
        if (rows.length() == 0) null else rows.getJSONObject(0).optJSONObject("public_key")
    }
    suspend fun dmPublishDeviceKey(jwk: JSONObject, keyId: String) = withContext(Dispatchers.IO) {
        val tok = access ?: return@withContext
        val c = java.net.URL(BASE + "/rest/v1/dm_device_keys").openConnection() as java.net.HttpURLConnection
        c.requestMethod = "POST"
        c.connectTimeout = 15000; c.readTimeout = 15000
        c.setRequestProperty("apikey", KEY)
        c.setRequestProperty("Authorization", "Bearer " + tok)
        c.setRequestProperty("Content-Type", "application/json")
        c.setRequestProperty("Prefer", "return=minimal,resolution=merge-duplicates")
        c.doOutput = true
        val body = JSONObject().put("user_id", uid).put("device_id", DmCrypto.deviceId())
            .put("public_key", jwk).put("key_id", keyId).toString()
        c.outputStream.use { it.write(body.toByteArray(Charsets.UTF_8)) }
        c.responseCode
        c.disconnect()
    }
    suspend fun dmDeviceKeys(userId: String): JSONArray = withContext(Dispatchers.IO) {
        authedGetArray("/rest/v1/dm_device_keys?user_id=eq." + userId + "&select=device_id,public_key,key_id&order=updated_at.desc&limit=10")
    }
    suspend fun dmPeerKey(peerId: String): JSONObject? = withContext(Dispatchers.IO) {
        val rows = authedGetArray("/rest/v1/dm_public_keys?user_id=eq." + peerId + "&select=public_key")
        if (rows.length() == 0) null else rows.getJSONObject(0).optJSONObject("public_key")
    }
    suspend fun dmThreads(): JSONArray = withContext(Dispatchers.IO) {
        authedGetArray("/rest/v1/dm_threads?or=(user_a.eq." + uid + ",user_b.eq." + uid + ")&select=*&order=created_at.desc")
    }
    suspend fun dmMessageRequests(): JSONArray = withContext(Dispatchers.IO) {
        authedGetArray("/rest/v1/dm_threads?or=(user_a.eq." + uid + ",user_b.eq." + uid + ")&status=eq.requested&requested_by=neq." + uid + "&select=*")
    }
    suspend fun dmMessages(threadId: String): JSONArray = withContext(Dispatchers.IO) {
        authedGetArray("/rest/v1/dm_messages?thread_id=eq." + threadId + "&select=*&order=created_at.asc&limit=100")
    }
    suspend fun dmFriends(): JSONArray = withContext(Dispatchers.IO) {
        authedGetArray("/rest/v1/friends?user_id=eq." + uid + "&select=friend_id")
    }
    suspend fun dmRequestsIncoming(): JSONArray = withContext(Dispatchers.IO) {
        authedGetArray("/rest/v1/friend_requests?to_user=eq." + uid + "&status=eq.pending&select=*")
    }
    suspend fun dmRequestsOutgoing(): JSONArray = withContext(Dispatchers.IO) {
        authedGetArray("/rest/v1/friend_requests?from_user=eq." + uid + "&status=eq.pending&select=*")
    }
    suspend fun dmSearchPlayers(q: String): JSONArray = withContext(Dispatchers.IO) {
        val e = java.net.URLEncoder.encode(q, "UTF-8")
        authedGetArray("/rest/v1/dm_player_search?or=(username.ilike.*" + e + "*,ign.ilike.*" + e + "*)&select=id,username,ign,verified&limit=20")
    }
    suspend fun dmNames(ids: List<String>): Map<String, String> = withContext(Dispatchers.IO) {
        if (ids.isEmpty()) return@withContext emptyMap()
        val rows = authedGetArray("/rest/v1/dm_player_search?id=in.(" + ids.joinToString(",") + ")&select=id,username,ign,verified")
        val out = HashMap<String, String>()
        for (i in 0 until rows.length()) {
            val r = rows.getJSONObject(i)
            out[r.getString("id")] = r.s("ign").ifBlank { r.s("username") }.ifBlank { "Player" }
        }
        out
    }
    suspend fun dmOpen(friendId: String): JSONObject = withContext(Dispatchers.IO) { authedRpc("dm_open", JSONObject().put("p_friend", friendId)) }
    suspend fun dmSend(threadId: String, kind: String, ciphertext: String, iv: String, meta: JSONObject): JSONObject = withContext(Dispatchers.IO) {
        authedRpc("dm_send", JSONObject().put("p_thread", threadId).put("p_kind", kind).put("p_ciphertext", ciphertext).put("p_iv", iv).put("p_meta", meta))
    }
    suspend fun dmRespondRequest(threadId: String, accept: Boolean): JSONObject = withContext(Dispatchers.IO) {
        authedRpc("dm_respond_request", JSONObject().put("p_thread", threadId).put("p_accept", accept))
    }
    suspend fun dmFriendRequest(toId: String): JSONObject = withContext(Dispatchers.IO) { authedRpc("friend_request", JSONObject().put("p_to", toId)) }
    suspend fun dmFriendRespond(requestId: String, accept: Boolean): JSONObject = withContext(Dispatchers.IO) {
        authedRpc("friend_respond", JSONObject().put("p_request", requestId).put("p_accept", accept))
    }
    suspend fun dmFriendRemove(friendId: String): JSONObject = withContext(Dispatchers.IO) { authedRpc("friend_remove", JSONObject().put("p_friend", friendId)) }
    suspend fun dmBlock(userId: String): JSONObject = withContext(Dispatchers.IO) { authedRpc("block_user", JSONObject().put("p_user", userId)) }
    suspend fun dmReport(threadId: String, reported: String, reason: String, note: String, messages: JSONArray): JSONObject = withContext(Dispatchers.IO) {
        authedRpc("dm_report", JSONObject().put("p_thread", threadId).put("p_reported", reported).put("p_reason", reason).put("p_note", note).put("p_messages", messages))
    }
    suspend fun dmMediaUpload(threadId: String, encrypted: ByteArray): String = withContext(Dispatchers.IO) {
        val tok = access ?: throw ApiError("Log in to send media.")
        val path = threadId + "/" + java.util.UUID.randomUUID().toString()
        val c = java.net.URL(BASE + "/storage/v1/object/dm-media/" + path).openConnection() as java.net.HttpURLConnection
        c.requestMethod = "POST"
        c.connectTimeout = 15000; c.readTimeout = 120000
        c.setRequestProperty("apikey", KEY)
        c.setRequestProperty("Authorization", "Bearer " + tok)
        c.setRequestProperty("Content-Type", "application/octet-stream")
        c.setRequestProperty("x-upsert", "false")
        c.doOutput = true
        c.outputStream.use { it.write(encrypted) }
        val code = c.responseCode
        if (code !in 200..299) throw ApiError("Upload failed (" + code + "). Check your connection and try again.")
        path
    }
    suspend fun dmMediaSign(path: String): String = withContext(Dispatchers.IO) {
        val r = JSONObject(authed("POST", "/storage/v1/object/sign/dm-media/" + path, JSONObject().put("expiresIn", 300)))
        r.getString("signedURL")
    }
    suspend fun dmMediaDownload(signedUrl: String): ByteArray = withContext(Dispatchers.IO) {
        val c = java.net.URL(BASE + "/storage/v1" + signedUrl).openConnection() as java.net.HttpURLConnection
        c.connectTimeout = 15000; c.readTimeout = 120000
        if (c.responseCode !in 200..299) throw ApiError("Media download failed (" + c.responseCode + ").")
        c.inputStream.use { it.readBytes() }
    }

    /* Players directory + public player profiles (player_profiles_public view: safe columns only). */
    suspend fun playerProfiles(): JSONArray = withContext(Dispatchers.IO) {
        publicGet("/rest/v1/player_profiles_public?select=id,username,ign,verified,role,kd,matches,wins,avatar_url,last_seen_at,xp&order=username.asc&limit=500")
    }
    suspend fun playerBadges(userId: String): JSONArray = withContext(Dispatchers.IO) {
        publicGet("/rest/v1/badges?select=badge,earned_at&user_id=eq." + userId + "&order=earned_at.desc&limit=50")
    }
    suspend fun playerHighlights(userId: String): JSONArray = withContext(Dispatchers.IO) {
        publicGet("/rest/v1/feed_posts?select=id,kind,media_path,caption,created_at&user_id=eq." + userId + "&status=eq.approved&kind=eq.video&order=created_at.desc&limit=20")
    }
    suspend fun friendStateWith(userId: String): JSONObject = withContext(Dispatchers.IO) {
        val me = uid
        val out = JSONObject().put("state", "none")
        if (me.isNullOrBlank() || me == userId) return@withContext out.put("state", "self")
        val fr = authedGetArray("/rest/v1/friends?or=(and(user_id.eq." + me + ",friend_id.eq." + userId + "),and(user_id.eq." + userId + ",friend_id.eq." + me + "))&select=user_id&limit=1")
        if (fr.length() > 0) return@withContext out.put("state", "friends")
        val outReq = authedGetArray("/rest/v1/friend_requests?from_user=eq." + me + "&to_user=eq." + userId + "&status=eq.pending&select=id&limit=1")
        if (outReq.length() > 0) return@withContext out.put("state", "requested")
        val inReq = authedGetArray("/rest/v1/friend_requests?from_user=eq." + userId + "&to_user=eq." + me + "&status=eq.pending&select=id&limit=1")
        if (inReq.length() > 0) return@withContext out.put("state", "incoming").put("request_id", inReq.getJSONObject(0).getString("id"))
        out
    }
    suspend fun presencePing(): JSONObject = withContext(Dispatchers.IO) { authedRpc("presence_ping", JSONObject()) }

}

// Display helper: my_stats values can be arrays/objects (e.g. xp_days) - never print raw JSON.
fun statDisplay(v: Any?): String = when (v) {
    null, JSONObject.NULL -> "-"
    is JSONArray -> v.length().toString()
    is JSONObject -> v.length().toString()
    else -> v.toString()
}

// Safe external link open - never crash the app on a bad/unresolvable intent.
fun openExternalUrl(u: String) {
    try {
        val i = android.content.Intent(android.content.Intent.ACTION_VIEW, android.net.Uri.parse(u))
        i.addFlags(android.content.Intent.FLAG_ACTIVITY_NEW_TASK)
        Api.ctx.startActivity(i)
    } catch (t: Throwable) {
        android.widget.Toast.makeText(Api.ctx, "No app on this device can open that link.", android.widget.Toast.LENGTH_SHORT).show()
    }
}

/* Android's org.json renders JSONObject.NULL as the literal string "null" via optString.
   Use s() for display/logic so SQL NULL becomes "" instead. */
fun JSONObject.s(k: String, def: String = ""): String {
    val v = opt(k)
    return if (v == null || v == JSONObject.NULL) def else v.toString()
}
