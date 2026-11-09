package app.xarena.nativeapp

import org.json.JSONArray
import org.json.JSONObject
import org.junit.Assert.*
import org.junit.Rule
import org.junit.Test
import app.cash.paparazzi.Paparazzi

/** Request/response fixtures run through the same functions the app uses (runs inside the Paparazzi JVM, which provides org.json). */
class AuthNetworkRulesTest {
    @get:Rule val paparazzi = Paparazzi()
    private fun row(u: String, ign: String? = null, uid: String? = null) = JSONObject().put("username", u).apply {
        if (ign != null) put("ign", ign); if (uid != null) put("ff_uid", uid) }

    // --- profile read / gate ---
    @Test fun missingRowNeedsFormAndReadsProvider() {
        var asked = 0
        val i = ProfileGate.setupInfoFrom(null) { asked++; true }
        assertTrue(i.needs); assertTrue(i.strict); assertEquals(1, asked)
    }
    @Test fun placeholderPhoneAccountStaysOptional() {
        val i = ProfileGate.setupInfoFrom(row("user1a2b3c4d")) { false }
        assertTrue(i.needs); assertFalse(i.strict)
    }
    @Test fun completeAccountNeverReadsProviderOrNeedsForm() {
        var asked = 0
        val i = ProfileGate.setupInfoFrom(row("night_hunter", "NH", "123456789")) { asked++; true }
        assertFalse(i.needs); assertEquals(0, asked)
        assertFalse(ProfileGate.setupInfoFrom(row("night_hunter")) { asked++; true }.needs) // old account without IGN/UID is not locked
        assertEquals(0, asked)
    }
    @Test fun providerReadFailureFailsClosedNotOptional() {
        val r = runCatching { ProfileGate.setupInfoFrom(row("user1a2b3c4d")) { ProfileGate.parseIsGoogle(500, "{}") } }
        assertTrue(r.exceptionOrNull() is Api.ApiError)
        assertTrue(runCatching { ProfileGate.parseIsGoogle(401, "{}") }.isFailure)
    }
    @Test fun providerParsing() {
        assertTrue(ProfileGate.parseIsGoogle(200, """{"app_metadata":{"provider":"google"}}"""))
        assertTrue(ProfileGate.parseIsGoogle(200, """{"app_metadata":{"provider":"email"},"identities":[{"provider":"email"},{"provider":"google"}]}"""))
        assertFalse(ProfileGate.parseIsGoogle(200, """{"app_metadata":{"provider":"email"},"identities":[{"provider":"email"}]}"""))
    }
    // --- save verification (re-read) ---
    @Test fun zeroRowUpdateFailsClosed() {
        assertTrue(runCatching { ProfileGate.verifySaved(null, "rahul", "R", "123456789", true) }.isFailure)
        assertTrue(runCatching { ProfileGate.verifySaved(row("user1a2b3c4d"), "rahul", "R", "123456789", true) }.isFailure)
    }
    @Test fun strictMissingIgnOrUidFailsClosed() {
        assertTrue(runCatching { ProfileGate.verifySaved(row("rahul", "R", null), "rahul", "R", "123456789", true) }.isFailure)
        assertTrue(runCatching { ProfileGate.verifySaved(row("rahul", null, "123456789"), "rahul", "R", "123456789", true) }.isFailure)
        assertTrue(runCatching { ProfileGate.verifySaved(row("rahul", "R", "123456789"), "rahul", "", "123456789", true) }.isFailure)
        assertTrue(runCatching { ProfileGate.verifySaved(row("rahul", "R", "999999999"), "rahul", "R", "123456789", true) }.isFailure)
    }
    @Test fun fullySavedPasses() {
        ProfileGate.verifySaved(row("rahul", "R", "123456789"), "Rahul", "R", "123456789", true)   // server lowercases username
        ProfileGate.verifySaved(row("rahul"), "rahul", "", "", false)                              // optional form, phone account
    }
    // --- weak-password guard (signup) ---
    @Test fun weakPasswordRulesMatchSite() {
        assertTrue(ProfileGate.weakPassword("9876543210", "9876543210"))   // password = own phone
        assertTrue(ProfileGate.weakPassword("x9876543210!", "9876543210")) // contains phone
        assertTrue(ProfileGate.weakPassword("aaaaaaaa", "9876543210"))     // one repeated char
        assertTrue(ProfileGate.weakPassword("12345678", "9876543210"))
        assertTrue(ProfileGate.weakPassword("Password99", "9876543210"))   // starts with "password"
        assertTrue(ProfileGate.weakPassword("qwertyuiop", "9876543210"))
        assertFalse(ProfileGate.weakPassword("Kx9#mP2vQ8", "9876543210"))
        assertFalse(ProfileGate.weakPassword("9876543211", "9876543210"))   // different number is fine
    }
    // --- refresh classification ---
    @Test fun onlyInvalidRefreshTokenExpiresSession() {
        assertTrue(ProfileGate.isInvalidRefreshToken(400, """{"error_code":"refresh_token_not_found","msg":"Invalid Refresh Token: Refresh Token Not Found"}"""))
        assertTrue(ProfileGate.isInvalidRefreshToken(400, """{"error":"invalid_grant","error_description":"Invalid Refresh Token: Already Used"}"""))
        assertTrue(ProfileGate.isInvalidRefreshToken(400, """{"error_code":"refresh_token_already_used"}"""))
        assertFalse(ProfileGate.isInvalidRefreshToken(403, """{"error_code":"refresh_token_not_found"}"""))
        assertFalse(ProfileGate.isInvalidRefreshToken(500, "oops"))
        assertFalse(ProfileGate.isInvalidRefreshToken(429, """{"error_code":"over_request_rate_limit"}"""))
        assertFalse(ProfileGate.isInvalidRefreshToken(400, """{"error_code":"validation_failed","msg":"bad body"}"""))
        assertFalse(ProfileGate.isInvalidRefreshToken(400, "not json"))
    }
}
