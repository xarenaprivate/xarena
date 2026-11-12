package app.xarena.nativeapp

import app.cash.paparazzi.Paparazzi
import org.json.JSONArray
import org.json.JSONObject
import org.junit.Rule
import org.junit.Test

/** v82: Arena Live, Trust & Safety, Rewards and the Logo Maker preview. Fixtures are TEST
    data rendered by the same composables; the shipped UI never fabricates polls, kills,
    ratings or balances. */
class ArenaVisualTest {
    @get:Rule val paparazzi = Paparazzi()

    @org.junit.Before fun setUp() {
        DataSaver.testOverride = false
        AlSeams.clear(); TsSeams.clear(); RwSeams.clear(); LmSeams.clear()
    }

    private fun liveMatch(): JSONObject = JSONObject()
        .put("id", "m-live-1").put("title", "Ignition Cup - Finals").put("tournament_id", "xr-5oar")
        .put("status", "live").put("starts_at", "2026-11-12T11:30:00+00:00")

    @Test fun arenaLive_populated() {
        AlSeams.match = liveMatch()
        AlSeams.poll = JSONObject().put("id", "p1").put("title", "Who wins the finals?")
        AlSeams.pollResults = JSONObject().put("ok", true).put("total", 128).put("my_vote", "o1")
            .put("options", JSONArray()
                .put(JSONObject().put("id", "o1").put("label", "Patna Wolves").put("votes", 79))
                .put(JSONObject().put("id", "o2").put("label", "Night Owls").put("votes", 49)))
        AlSeams.kills = JSONArray()
            .put(JSONObject().put("id", 3).put("killer", "shadow_k").put("victim", "rush_b").put("weapon", "AKM"))
            .put(JSONObject().put("id", 2).put("killer", "pro_girl").put("victim", "noob_99").put("weapon", "AWM"))
        AlSeams.clip = JSONObject().put("name", "shadow_k").put("caption", "1v4 clutch on Bermuda").put("likes", 214)
        paparazzi.snapshot { ArenaLiveScreen(onBack = {}, onOpenMvp = {}, onOpenFeed = {}) }
    }

    @Test fun arenaLive_empty() {
        AlSeams.noMatch = true
        paparazzi.snapshot { ArenaLiveScreen(onBack = {}, onOpenMvp = {}, onOpenFeed = {}) }
    }

    @Test fun trustSafety_verified() {
        TsSeams.verification = JSONObject().put("verified", true)
        TsSeams.trustScore = 82; TsSeams.trustLoaded = true; TsSeams.backupOptIn = true
        paparazzi.snapshot { TrustSafetyScreen(onBack = {}, onOpenVerify = {}, onOpenSupport = {}, onOpenFeedback = {}) }
    }

    @Test fun trustSafety_unrated() {
        TsSeams.verification = JSONObject().put("verified", false).put("submission", JSONObject().put("status", "pending"))
        TsSeams.trustScore = null; TsSeams.trustLoaded = true; TsSeams.backupOptIn = false
        paparazzi.snapshot { TrustSafetyScreen(onBack = {}, onOpenVerify = {}, onOpenSupport = {}, onOpenFeedback = {}) }
    }

    @Test fun rewards_loaded() {
        RwSeams.balance = 1450
        RwSeams.stats = JSONObject().put("ok", true).put("streak", 6).put("checked_in_today", false)
        RwSeams.season = JSONObject().put("ok", true).put("level", 12).put("xp", 8450)
            .put("tiers", JSONArray().put(JSONObject().put("level", 12).put("unlocked", true).put("claimed", false)))
        RwSeams.loaded = true
        paparazzi.snapshot { RewardsScreen(onBack = {}, onOpenPlay = {}, onOpenSeason = {}, onOpenStore = {}) }
    }

    @Test fun rewards_loggedOut() {
        RwSeams.loaded = true
        paparazzi.snapshot { RewardsScreen(onBack = {}, onOpenPlay = {}, onOpenSeason = {}, onOpenStore = {}) }
    }

    @Test fun logoMaker_preview() {
        paparazzi.snapshot { LmPreview("PW", 1, 0, 1) }
    }
}
