package app.xarena.nativeapp

import org.junit.Assert.*
import org.junit.Test

class ProfileGateTest {
    @Test fun missingRowOrPlaceholderNeedsForm() {
        assertTrue(ProfileGate.needsForm(null))
        assertTrue(ProfileGate.needsForm("user1a2b3c4d"))
    }
    @Test fun realUsernameDoesNotNeedForm() {
        assertFalse(ProfileGate.needsForm("night_hunter"))
        assertFalse(ProfileGate.needsForm("rahul.sharma"))
    }
    @Test fun placeholderRegexIsExact() {
        assertFalse(ProfileGate.isPlaceholderUsername("user1a2b3c4"))
        assertFalse(ProfileGate.isPlaceholderUsername("user1a2b3c4dd"))
        assertFalse(ProfileGate.isPlaceholderUsername("userABCDEF12"))
        assertFalse(ProfileGate.isPlaceholderUsername("user_hunter1"))
    }
    @Test fun usernameRule() {
        assertTrue(ProfileGate.usernameOk("abc")); assertTrue(ProfileGate.usernameOk("Night_Hunter.1"))
        assertFalse(ProfileGate.usernameOk("ab")); assertFalse(ProfileGate.usernameOk("a".repeat(17))); assertFalse(ProfileGate.usernameOk("bad name"))
    }
    @Test fun strictRequiresIgnAndUid() {
        assertFalse(ProfileGate.ignOk("", true)); assertTrue(ProfileGate.ignOk("FF_Player", true))
        assertFalse(ProfileGate.uidOk("", true)); assertFalse(ProfileGate.uidOk("1234", true)); assertTrue(ProfileGate.uidOk("123456789", true))
    }
    @Test fun nonStrictKeepsOptionalContract() {
        assertTrue(ProfileGate.ignOk("", false)); assertTrue(ProfileGate.uidOk("", false))
        assertFalse(ProfileGate.uidOk("12ab5", false)); assertFalse(ProfileGate.ignOk("x".repeat(25), false))
    }
}
