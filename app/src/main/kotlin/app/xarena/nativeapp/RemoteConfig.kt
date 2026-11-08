package app.xarena.nativeapp

import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import org.json.JSONObject

/* Remote config, Free Fire style: content/data changes without an APK install.
   Source = site_settings key "native" (same table the web already uses for site/luna/youtube_live):
   {"announcement": {"active": false, "text": "", "link": ""},
    "min_version_code": 0,          // below this code the update becomes mandatory
    "features": {"luna": false}}    // grid items hidden only when explicitly false
   Cached 5 min in memory + persisted in prefs so a cold start offline still works. */
object RemoteConfig {
    private var cached: JSONObject? = null
    private var cachedAt = 0L

    suspend fun get(): JSONObject = withContext(Dispatchers.IO) {
        val now = System.currentTimeMillis()
        cached?.let { if (now - cachedAt < 5 * 60_000) return@withContext it }
        val fresh = runCatching {
            val arr = Api.publicGet("/rest/v1/site_settings?key=eq.native&select=value")
            if (arr.length() > 0) arr.getJSONObject(0).optJSONObject("value") ?: JSONObject() else JSONObject()
        }.getOrNull()
        if (fresh != null) {
            cached = fresh; cachedAt = now
            Api.prefs.edit().putString("remote_config", fresh.toString()).apply()
            return@withContext fresh
        }
        cached ?: runCatching { JSONObject(Api.prefs.getString("remote_config", "{}") ?: "{}") }.getOrElse { JSONObject() }
            .also { cached = it; cachedAt = now }
    }

    fun announcement(): JSONObject? =
        cached?.optJSONObject("announcement")?.takeIf { it.optBoolean("active") && it.s("text").isNotBlank() }
    fun minVersionCode(): Int = cached?.optInt("min_version_code", 0) ?: 0
    fun featureEnabled(id: String): Boolean = cached?.optJSONObject("features")?.optBoolean(id, true) ?: true
    /** True only when the server features map explicitly enables this flag - for dark-launched features. */
    fun flagOn(id: String): Boolean = cached?.optJSONObject("features")?.optBoolean(id, false) ?: false

    /** Web OAuth client ID for native Google sign-in (Supabase Google provider). Verified from the live
     *  provider config 2026-11-08; overridable via site_settings "native" {google_web_client_id}. Empty = not set up. */
    fun googleWebClientId(): String = cached?.optString("google_web_client_id", "")
        ?.ifBlank { "4204930429-kiejfmrbohqhhan328hb7as596k2rlbf.apps.googleusercontent.com" }
        ?: "4204930429-kiejfmrbohqhhan328hb7as596k2rlbf.apps.googleusercontent.com"
}
