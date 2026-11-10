package app.xarena.nativeapp

import org.json.JSONObject
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test

class VoicePresenceTest {
    @Test
    fun presenceStateDirectMapIsUsedAsIs() {
        // v2 realtime server sends presence_state payload as the presences map directly
        val payload = JSONObject("""{"user-a": {"metas": [{"handle": "A"}]}, "user-b": {"metas": [{"handle": "B"}]}}""")
        val out = extractPresences(payload)
        assertTrue(out.has("user-a"))
        assertTrue(out.has("user-b"))
    }

    @Test
    fun presenceStateWrappedFormStillWorks() {
        // defensive: if a server ever wraps in "presences", that object is used
        val payload = JSONObject("""{"presences": {"user-a": {"metas": [{"handle": "A"}]}}}""")
        val out = extractPresences(payload)
        assertEquals(1, out.length())
        assertTrue(out.has("user-a"))
    }
}
