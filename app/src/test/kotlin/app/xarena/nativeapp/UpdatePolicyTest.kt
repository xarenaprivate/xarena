package app.xarena.nativeapp

import org.junit.Assert.*
import org.junit.Test

class UpdatePolicyTest {
    @Test fun skipOnlyAffectsCurrentSession() {
        assertTrue(UpdateChecker.shouldOffer(70, 69, false, false))
        assertFalse(UpdateChecker.shouldOffer(70, 69, true, false))
        assertTrue(UpdateChecker.shouldOffer(70, 69, false, false)) // fresh launch, same pending version
    }
    @Test fun mandatoryCannotSkipAndCurrentNeverOffered() {
        assertTrue(UpdateChecker.shouldOffer(70, 69, true, true))
        assertFalse(UpdateChecker.shouldOffer(69, 69, false, false))
        assertFalse(UpdateChecker.shouldOffer(68, 69, false, true))
    }
}
