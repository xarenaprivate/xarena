package app.xarena.nativeapp

import app.cash.paparazzi.Paparazzi
import org.json.JSONArray
import org.junit.Rule
import org.junit.Test

/** v80 match live-chat panel: populated, empty-honest-line, not-live note, signed-out note.
    Fixtures are TEST data rendered by the same composables; the shipped UI never fabricates messages. */
class ChatMatchVisualTest {
    @get:Rule val paparazzi = Paparazzi()

    @org.junit.Before fun setUp() {
        DataSaver.testOverride = false
        matchChatSignedInOverride = null
        matchChatHandleOverride = null
        matchChatLiveOverride = null
        MatchChatCache.msgs = null
    }

    private fun twoMsgs() = JSONArray("""[{"id":2,"handle":"shadow_k","body":"Push left side, zone is closing","created_at":"2026-11-12T10:00:00+00:00"},{"id":1,"handle":"pro_girl","body":"GG that clutch","created_at":"2026-11-12T09:58:00+00:00"}]""")

    @Test fun matchChatPanel_populated() {
        matchChatSignedInOverride = true
        matchChatHandleOverride = "tester"
        matchChatLiveOverride = true
        MatchChatCache.msgs = twoMsgs()
        paparazzi.snapshot { MatchChatPanel("m1", live = true) }
    }
    @Test fun matchChatPanel_emptyHonestLine() {
        matchChatSignedInOverride = true
        matchChatHandleOverride = "tester"
        matchChatLiveOverride = true
        MatchChatCache.msgs = JSONArray()
        paparazzi.snapshot { MatchChatPanel("m1", live = true) }
    }
    @Test fun matchChatPanel_notLiveNote() {
        matchChatSignedInOverride = true
        matchChatHandleOverride = "tester"
        matchChatLiveOverride = false
        paparazzi.snapshot { MatchChatPanel("m1", live = false) }
    }
    @Test fun matchChatPanel_signedOutNote() {
        matchChatSignedInOverride = false
        matchChatLiveOverride = true
        paparazzi.snapshot { MatchChatPanel("m1", live = true) }
    }
}
