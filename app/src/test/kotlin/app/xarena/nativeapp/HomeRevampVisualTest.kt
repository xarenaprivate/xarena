package app.xarena.nativeapp

import app.cash.paparazzi.Paparazzi
import org.json.JSONArray
import org.json.JSONObject
import org.junit.Rule
import org.junit.Test

/** v79 home revamp sections: populated fixture renders + all-empty hiding.
    Fixtures here are TEST data rendered by the same composables; the shipped UI never fabricates
    viewers, clips, friends, messages or stats - sections hide when the real source is empty. */
class HomeRevampVisualTest {
    @get:Rule val paparazzi = Paparazzi()

    @org.junit.Before fun setUp() { DataSaver.testOverride = false }

    private fun liveMatches() = JSONArray("""[{"id":"m1","tournament_id":"t1","title":"Grand Finals - Map 3","status":"live","starts_at":"2026-11-12T09:00:00+00:00"},{"id":"m2","tournament_id":"t2","title":"Semi Final B","status":"live","starts_at":"2026-11-12T10:30:00+00:00"}]""")
    private fun noLiveMatches() = JSONArray("""[{"id":"m9","tournament_id":"t1","title":"Qualifier Lobby A","status":"cancelled","starts_at":"2026-10-25T14:30:00+00:00"}]""")
    private fun tournaments() = JSONArray("""[{"id":"t1","name":"XARENA Season 2","status":"upcoming","slots":12,"prize":"INR 21,000","date_text":"Friday, November 14, 2026 at 8:00 PM IST","reg_open":true,"mode":"Squad BR"},{"id":"t2","name":"BR Lord","status":"upcoming","slots":48,"prize":"INR 5,000","date_text":"Saturday, November 15, 2026 at 9:00 PM IST","reg_open":false,"mode":"Squad BR"}]""")

    @Test fun liveMatchesRail_populated() {
        paparazzi.snapshot { LiveMatchesRail(liveMatches(), tournaments()) {} }
    }
    @Test fun liveMatchesRail_noLiveHides() {
        paparazzi.snapshot { LiveMatchesRail(noLiveMatches(), tournaments()) {} }
    }
    @Test fun upcomingTournamentGrid_populated() {
        paparazzi.snapshot { UpcomingTournamentGrid(tournaments()) {} }
    }
    @Test fun upcomingTournamentGrid_emptyHides() {
        paparazzi.snapshot { UpcomingTournamentGrid(JSONArray()) {} }
    }
    @Test fun highlightsWall_populated() {
        HighlightsCache.clips = JSONArray("""[{"id":1,"kind":"video","media_path":"","caption":"Final circle clutch","created_at":"2026-11-12T10:00:00+00:00"},{"id":2,"kind":"video","media_path":"","caption":"Squad wipe","created_at":"2026-11-12T09:00:00+00:00"}]""")
        paparazzi.snapshot { HighlightsWall {} }
    }
    @Test fun highlightsWall_emptyHides() {
        HighlightsCache.clips = JSONArray()
        paparazzi.snapshot { HighlightsWall {} }
    }
    @Test fun friendsActivity_populated() {
        friendsRailSignedInOverride = true
        FriendsRailCache.data = JSONObject("""{"ok":true,"friends":[{"id":"u1","username":"shadow_k","presence":"online","last_seen":""},{"id":"u2","username":"night_hunter","presence":"in_match","last_seen":""},{"id":"u3","username":"astra_lyn","presence":"offline","last_seen":"2026-11-12T08:00:00+00:00"}]}""")
        paparazzi.snapshot { FriendsActivityStrip {} }
        friendsRailSignedInOverride = null
    }
    @Test fun friendsActivity_emptyHides() {
        friendsRailSignedInOverride = true
        FriendsRailCache.data = JSONObject("""{"ok":true,"friends":[]}""")
        paparazzi.snapshot { FriendsActivityStrip {} }
        friendsRailSignedInOverride = null
    }
    @Test fun friendsActivity_signedOutHides() {
        friendsRailSignedInOverride = false
        paparazzi.snapshot { FriendsActivityStrip {} }
        friendsRailSignedInOverride = null
    }
    @Test fun liveChatStrip_populated() {
        HomeChatCache.msgs = JSONArray("""[{"id":2,"handle":"pro_girl","body":"Who is joining the 9pm tourney?","created_at":"2026-11-12T11:00:00+00:00"},{"id":1,"handle":"gamerX","body":"GG that was insane","created_at":"2026-11-12T10:58:00+00:00"}]""")
        paparazzi.snapshot { LiveChatStrip {} }
    }
    @Test fun liveChatStrip_emptyHonestLine() {
        HomeChatCache.msgs = JSONArray()
        paparazzi.snapshot { LiveChatStrip {} }
    }
}
