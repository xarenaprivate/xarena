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
    @Test fun failedPatchNeverStartsFullDownload() = kotlinx.coroutines.runBlocking {
        var fullCalls = 0
        val result = UpdateTransfer.run(true, false, { "network failure" }, { fullCalls++; null })
        assertTrue(result.needsFullConsent); assertEquals(0, fullCalls)
    }
    @Test fun explicitFullSelectionSkipsPatchAndCallsFullOnlyOnce() = kotlinx.coroutines.runBlocking {
        var patchCalls = 0; var fullCalls = 0
        val result = UpdateTransfer.run(true, true, { patchCalls++; null }, { fullCalls++; null })
        assertNull(result.error); assertFalse(result.needsFullConsent)
        assertEquals(0, patchCalls); assertEquals(1, fullCalls)
    }
    @Test fun successfulPatchDoesNotCallFull() = kotlinx.coroutines.runBlocking {
        var fullCalls = 0
        val result = UpdateTransfer.run(true, false, { null }, { fullCalls++; null })
        assertNull(result.error); assertEquals(0, fullCalls)
    }
    @Test fun noPatchUsesExplicitInitialFullUpdateChoice() = kotlinx.coroutines.runBlocking {
        var patchCalls = 0; var fullCalls = 0
        UpdateTransfer.run(false, false, { patchCalls++; null }, { fullCalls++; "offline" })
        assertEquals(0, patchCalls); assertEquals(1, fullCalls)
    }

}
