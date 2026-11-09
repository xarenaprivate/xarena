package app.xarena.nativeapp

import android.content.SharedPreferences
import org.json.JSONObject
import org.junit.Assert.*
import org.junit.After
import org.junit.Before
import org.junit.Rule
import org.junit.Test
import app.cash.paparazzi.Paparazzi
import kotlinx.coroutines.runBlocking

/** In-memory SharedPreferences so Api.session storage works without Android. */
private class FakePrefs : SharedPreferences {
    val map = mutableMapOf<String, Any?>()
    override fun getAll() = map
    override fun getString(k: String, d: String?) = map[k] as? String ?: d
    @Suppress("UNCHECKED_CAST") override fun getStringSet(k: String, d: Set<String>?) = map[k] as? Set<String> ?: d
    override fun getInt(k: String, d: Int) = map[k] as? Int ?: d
    override fun getLong(k: String, d: Long) = map[k] as? Long ?: d
    override fun getFloat(k: String, d: Float) = map[k] as? Float ?: d
    override fun getBoolean(k: String, d: Boolean) = map[k] as? Boolean ?: d
    override fun contains(k: String) = map.containsKey(k)
    override fun edit() = object : SharedPreferences.Editor {
        override fun putString(k: String, v: String?) = apply { map[k] = v }
        override fun putStringSet(k: String, v: Set<String>?) = apply { map[k] = v }
        override fun putInt(k: String, v: Int) = apply { map[k] = v }
        override fun putLong(k: String, v: Long) = apply { map[k] = v }
        override fun putFloat(k: String, v: Float) = apply { map[k] = v }
        override fun putBoolean(k: String, v: Boolean) = apply { map[k] = v }
        override fun remove(k: String) = apply { map.remove(k) }
        override fun clear() = apply { map.clear() }
        override fun commit() = true
        override fun apply() {}
    }
    override fun registerOnSharedPreferenceChangeListener(l: SharedPreferences.OnSharedPreferenceChangeListener) {}
    override fun unregisterOnSharedPreferenceChangeListener(l: SharedPreferences.OnSharedPreferenceChangeListener) {}
}

/** Scripted HTTP transport: handlers keyed by (method, path-prefix); each returns a queue of responses. */
private class Script {
    val routes = linkedMapOf<String, ArrayDeque<Pair<Int, String>>>()
    var refreshCalls = 0; var userCalls = 0
    fun on(key: String, vararg responses: Pair<Int, String>) { routes[key] = ArrayDeque(responses.toList()) }
    fun handle(method: String, path: String, body: JSONObject?, token: String?): Pair<Int, String> {
        if (path.contains("grant_type=refresh_token")) refreshCalls++
        if (path == "/auth/v1/user") userCalls++
        val key = routes.keys.firstOrNull { path.startsWith(it) } ?: throw AssertionError("unstubbed $method $path")
        val q = routes[key]!!
        return if (q.size > 1) q.removeFirst() else q.first()
    }
}

private fun sessionJson(token: String) = """{"access_token":"$token","refresh_token":"rt-$token","user":{"id":"u1","identities":[{"provider":"email"}]}}"""

/** Api.login/signup/authed driven through the scripted transport (Paparazzi JVM provides org.json + compose runtime). */
class AuthTransportTest {
    @get:Rule val paparazzi = Paparazzi()
    private lateinit var prefs: FakePrefs
    private lateinit var script: Script

    @Before fun setUp() {
        prefs = FakePrefs(); script = Script()
        Api.prefsOverride = prefs
        Api.httpTransport = script::handle
        Api.logout() // clears session state + prefs
        Api.sessionExpired = false
    }
    @After fun tearDown() { Api.prefsOverride = null; Api.httpTransport = null; Api.logout() }

    private fun stubLogin() = script.on("/auth/v1/token?grant_type=password", 200 to sessionJson("a1"))
    private fun stubBind() = script.on("/rest/v1/rpc/bind_device", 200 to """{"ok":true}""")
    private fun login() { stubLogin(); stubBind(); runBlocking { Api.login("9876543210", "password123") } }

    @Test fun freshLoginClearsStaleExpiredFlag() {
        login()
        Api.sessionExpired = true // stale flag left over (old bug: survived logout+login)
        Api.logout(); assertFalse(Api.sessionExpired) // logout clears it
        Api.sessionExpired = true // even if set again...
        login() // ...a fresh successful login clears it
        assertFalse(Api.sessionExpired)
        assertTrue(Api.loggedIn)
    }

    @Test fun reloginAfterLogoutWorks() {
        login(); Api.logout(); assertFalse(Api.loggedIn)
        login(); assertTrue(Api.loggedIn); assertFalse(Api.sessionExpired)
    }

    @Test fun providerReadRefreshesOnceOn401() {
        login()
        script.on("/rest/v1/profiles", 200 to """[{"username":"user1a2b3c4d","ign":null,"ff_uid":null}]""")
        script.on("/auth/v1/user",
            401 to """{"error":"invalid_token"}""",
            200 to """{"app_metadata":{"provider":"google"}}""")
        script.on("/auth/v1/token?grant_type=refresh_token", 200 to sessionJson("a2"))
        val info = runBlocking { Api.profileSetupInfo() }
        assertTrue(info.needs); assertTrue(info.strict)
        assertEquals(1, script.refreshCalls); assertEquals(2, script.userCalls)
        assertFalse(Api.sessionExpired)
    }

    @Test fun invalidRefreshTokenMarksSessionExpired() {
        login()
        script.on("/rest/v1/profiles", 401 to """{"msg":"jwt expired"}""")
        script.on("/auth/v1/token?grant_type=refresh_token",
            400 to """{"error_code":"refresh_token_not_found","msg":"Invalid Refresh Token: Refresh Token Not Found"}""")
        runBlocking { runCatching { Api.profileSetupInfo() } }
        assertTrue(Api.sessionExpired)
    }

    @Test fun refreshServerErrorDoesNotExpireSession() {
        login()
        script.on("/rest/v1/profiles", 401 to """{"msg":"jwt expired"}""")
        script.on("/auth/v1/token?grant_type=refresh_token", 500 to "oops")
        runBlocking { runCatching { Api.profileSetupInfo() } }
        assertFalse(Api.sessionExpired)
    }

    @Test fun friendlyNetworkErrorOnLoginAndSignup() {
        Api.httpTransport = { _, _, _, _ -> throw java.io.IOException("boom") }
        val e1 = runBlocking { runCatching { Api.login("9876543210", "password123") }.exceptionOrNull() }
        assertEquals("Network problem - check your internet and try again.", e1?.message)
        val e2 = runBlocking { runCatching { Api.signup("9876543210", "Kx9#mP2vQ8") }.exceptionOrNull() }
        assertEquals("Network problem - check your internet and try again.", e2?.message)
    }

    @Test fun signupPrecheckFailsClosed() {
        script.on("/rest/v1/rpc/signup_precheck", 500 to "db down")
        val e = runBlocking { runCatching { Api.signup("9876543210", "Kx9#mP2vQ8") }.exceptionOrNull() }
        assertEquals("Could not check right now. Try again.", e?.message)
    }

    @Test fun signupBlockReasonShown() {
        script.on("/rest/v1/rpc/signup_precheck", 200 to """{"ok":false,"error":"Too many accounts from this device."}""")
        val e = runBlocking { runCatching { Api.signup("9876543210", "Kx9#mP2vQ8") }.exceptionOrNull() }
        assertEquals("Too many accounts from this device.", e?.message)
    }

    @Test fun duplicateSignupStaysNeutral() {
        script.on("/rest/v1/rpc/signup_precheck", 200 to """{"ok":true}""")
        // GoTrue duplicate: 200, no session, empty identities
        script.on("/auth/v1/signup", 200 to """{"user":{"id":"u9","identities":[]}}""")
        val e = runBlocking { runCatching { Api.signup("9876543210", "Kx9#mP2vQ8") }.exceptionOrNull() }
        assertEquals("We could not create an account with these details. If you already signed up, please log in instead.", e?.message)
        assertFalse(e!!.message!!.contains("already exists"))
    }

    @Test fun weakPasswordRejectedBeforeNetwork() {
        Api.httpTransport = { _, _, _, _ -> throw AssertionError("network must not be called") }
        val e = runBlocking { runCatching { Api.signup("9876543210", "9876543210") }.exceptionOrNull() }
        assertEquals("Choose a password that is harder to guess (not your phone number or a simple pattern).", e?.message)
    }

    @Test fun authedRefreshesOnceThenRetries() { // the path recovery-key enroll now uses
        login()
        var enrollCalls = 0
        Api.httpTransport = { _, path, _, _ ->
            when {
                path.contains("grant_type=refresh_token") -> { script.refreshCalls++; 200 to sessionJson("a2") }
                path == "/functions/v1/player-recovery" -> {
                    enrollCalls++
                    if (enrollCalls == 1) 401 to """{"msg":"jwt expired"}""" else 200 to """{"ok":true,"recovery_key":"ab12"}"""
                }
                else -> throw AssertionError("unstubbed $path")
            }
        }
        val r = Api.authed("POST", "/functions/v1/player-recovery", JSONObject().put("action", "enroll"))
        assertTrue(JSONObject(r).optBoolean("ok"))
        assertEquals(2, enrollCalls); assertEquals(1, script.refreshCalls)
    }
}
