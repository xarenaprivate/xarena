package app.xarena.nativeapp

import org.junit.Assert.*
import org.junit.Test

class ProfileGateTest {
    @Test fun placeholderUsernameAlwaysNeedsForm() {
        assertTrue(ProfileGate.decideSetup("user1a2b3c4d", "", "", false).needs)
        assertTrue(ProfileGate.decideSetup("user1a2b3c4d", "X", "12345", true).needs)
        assertFalse(ProfileGate.decideSetup("user1a2b3c4d", "", "", false).googleGate)
        assertEquals("", ProfileGate.decideSetup("user1a2b3c4d", "", "", false).username)
    }
    @Test fun googleNonPlaceholderWithBlankIgnAndUidNeedsFormWithIgnRequired() {
        val i = ProfileGate.decideSetup("rahul.sharma", "", "", true)
        assertTrue(i.needs); assertTrue(i.googleGate); assertEquals("rahul.sharma", i.username)
    }
    @Test fun googleWithIgnOrUidIsComplete() {
        assertFalse(ProfileGate.decideSetup("rahul", "RahulFF", "", true).needs)
        assertFalse(ProfileGate.decideSetup("rahul", "", "123456789", true).needs)
        assertFalse(ProfileGate.decideSetup("rahul", "RahulFF", "123456789", true).needs)
    }
    @Test fun phoneAndEmailAccountsNeverGatedByGoogleRule() {
        assertFalse(ProfileGate.decideSetup("night_hunter", "", "", false).needs)
    }
    @Test fun placeholderRegexIsExact() {
        assertFalse(ProfileGate.isPlaceholderUsername("user1a2b3c4"))      // 7 hex
        assertFalse(ProfileGate.isPlaceholderUsername("user1a2b3c4dd"))    // 9 hex
        assertFalse(ProfileGate.isPlaceholderUsername("userABCDEF12"))     // uppercase
        assertFalse(ProfileGate.isPlaceholderUsername("user_hunter1"))
    }
}
