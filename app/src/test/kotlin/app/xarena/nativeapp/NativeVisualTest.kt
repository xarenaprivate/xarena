package app.xarena.nativeapp

import app.cash.paparazzi.Paparazzi
import app.cash.paparazzi.DeviceConfig
import androidx.compose.material3.MaterialTheme
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.background
import androidx.compose.ui.Modifier
import androidx.compose.ui.unit.dp
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.*
import org.json.JSONArray
import org.json.JSONObject
import org.junit.Rule
import org.junit.Test

/** Local render fixtures only, never bundled into the APK or passed as real account data. */
class NativeVisualTest {
    @get:Rule val paparazzi = Paparazzi(deviceConfig = DeviceConfig.PIXEL_5, theme = "android:Theme.Material.Light.NoActionBar")
    @Test fun moreOptionsList() {
        Api.init(paparazzi.context)
        paparazzi.snapshot {
            MaterialTheme {
                MoreOptionsContent(listOf(
                    GridItem("profile", "Profile", "Your account and settings", Icons.Default.Person),
                    GridItem("settings", "Settings", "App version, activity status, logout", Icons.Default.Settings),
                    GridItem("notifications", "Notifications", "Your alerts and gift mailbox", Icons.Default.Notifications),
                    GridItem("messages", "Messages", "Private end-to-end encrypted chats", Icons.Default.Email),
                    GridItem("squads", "Squads", "Find or build your squad", Icons.Default.AccountBox),
                    GridItem("match", "Match Center", "Rooms, check-in, result proofs, reports", Icons.Default.Place),
                    GridItem("calendar", "Calendar", "Tournament and event dates", Icons.Default.DateRange)
                ), {}, true)
            }
        }
    }
    @Test fun unreadRowsFixture() {
        Api.init(paparazzi.context)
        val rows = JSONArray().put(JSONObject().put("id", "fixture-1").put("user_a", "fixture-peer-1").put("user_b", "fixture-user")
            .put("status", "open").put("created_at", "2026-11-06T15:00:00Z"))
            .put(JSONObject().put("id", "fixture-2").put("user_a", "fixture-peer-2").put("user_b", "fixture-user")
            .put("status", "open").put("created_at", "2026-11-06T14:30:00Z"))
        paparazzi.snapshot {
            MaterialTheme {
                Column(Modifier.fillMaxSize().background(Ink)) {
                    ChatsPane(rows, mapOf("fixture-peer-1" to "Unread row fixture", "fixture-peer-2" to "Read row fixture"),
                        mapOf("fixture-1" to 4), mapOf("fixture-1" to "2026-11-06T15:00:00Z")) { _,_,_,_ -> }
                }
            }
        }
    }
    @Test fun recoveryResetForm() {
        Api.init(paparazzi.context)
        paparazzi.snapshot { MaterialTheme { RecoveryDialog(false) {} } }
    }
    @Test fun bannerFixture() {
        paparazzi.snapshot { MaterialTheme { BannerSwatch(JSONObject().put("title", "Arena Violet").put("color_start", "#24113D").put("color_end", "#7C3AED")) } }
    }

    @Test fun fullDownloadConsentAfterPatchFailure() {
        paparazzi.snapshot { MaterialTheme {
            Column(Modifier.fillMaxSize().background(Ink).padding(20.dp)) {
                androidx.compose.material3.Text("Quick update failed. No full APK has been downloaded.", color = ErrRed)
                FullUpdateConsent(28915084) {}
            }
        } }
    }

    @Test fun manualCoarseLocationForm() {
        paparazzi.snapshot { MaterialTheme { Column(Modifier.fillMaxSize().background(Ink)) {
            MyLocationForm("", "", false, null, {}, {}, {}, {}, {})
        } } }
    }

    @Test fun optionalGpsConsentDefaultOff() {
        paparazzi.snapshot { MaterialTheme { AreaGpsConsent(false, {}, {}, {}) } }
    }
    @Test fun homeClanRoomShortcut() {
        paparazzi.snapshot { MaterialTheme { Column(Modifier.fillMaxSize().background(Ink)) {
            androidx.compose.material3.Text("YOUR STATS", color = Gold, modifier = Modifier.padding(16.dp))
            HomeClanRoomOption {}
        } } }
    }
    @Test fun loginScreenNewDesign() {
        Api.init(paparazzi.context)
        paparazzi.snapshot { MaterialTheme { Box(Modifier.fillMaxSize().background(Ink)) { LoginScreen({}, {}) } } }
    }
    @Test fun homeShellWithNewNav() {
        Api.init(paparazzi.context)
        paparazzi.snapshot { MaterialTheme { Column(Modifier.fillMaxSize().background(Ink)) {
            Box(Modifier.weight(1f).fillMaxWidth()) { HomeScreen({}, {}) }
            MainBottomNav(0) {}
        } } }
    }
    @Test fun homeFirstRunWalkthrough() {
        Api.init(paparazzi.context)
        DataSaver.testOverride = true
        val d = HomeData(JSONArray(), "", JSONArray(), null, null, null, -1)
        paparazzi.snapshot { MaterialTheme { Column(Modifier.fillMaxSize().background(Ink)) {
            Box(Modifier.weight(1f).fillMaxWidth()) { HomeBody(d, {}, {}) }
            MainBottomNav(0) {}
        } } }
    }
    @Test fun homePopulatedFixture() {
        Api.init(paparazzi.context)
        DataSaver.testOverride = true // freeze entrance animations so the single Paparazzi frame shows settled content
        val cup = JSONObject()
            .put("id", "XR-S2").put("name", "XARENA Season 2 Ignition Cup").put("mode", "Squad")
            .put("slots", 48).put("date_text", "Nov 15, 7:00 PM").put("prize", "5,000 XP prize pool")
            .put("status", "upcoming").put("reg_open", true)
        val ts = JSONArray().put(cup)
        val stats = JSONObject().put("ok", true).put("rank", 7).put("wins", 12).put("kd", 2.4).put("xp", 1850)
        val ann = JSONObject().put("text", "Season 2 registrations are open").put("link", "")
        val d = HomeData(ts, "", JSONArray(), stats, ann, cup, 21)
        paparazzi.snapshot { MaterialTheme { Column(Modifier.fillMaxSize().background(Ink)) {
            Box(Modifier.weight(1f).fillMaxWidth()) { HomeBody(d, {}, {}, skipWalkthrough = true) }
            MainBottomNav(0) {}
        } } }
    }
    @Test fun homeEmptyFixture() {
        Api.init(paparazzi.context)
        DataSaver.testOverride = true
        val d = HomeData(JSONArray(), "", JSONArray(), null, null, null, -1)
        paparazzi.snapshot { MaterialTheme { Column(Modifier.fillMaxSize().background(Ink)) {
            Box(Modifier.weight(1f).fillMaxWidth()) { HomeBody(d, {}, {}) }
            MainBottomNav(0) {}
        } } }
    }
    @Test fun homeOfflineError() {
        Api.init(paparazzi.context)
        paparazzi.snapshot { MaterialTheme { Column(Modifier.fillMaxSize().background(Ink)) {
            Box(Modifier.weight(1f).fillMaxWidth()) { LoadState2<Unit>(0, { throw RuntimeException("No internet connection") }) { } }
            MainBottomNav(0) {}
        } } }
    }
    @Test fun bottomNavNewDesign() {
        paparazzi.snapshot { MaterialTheme { Column(Modifier.fillMaxSize().background(Ink)) {
            Spacer(Modifier.weight(1f))
            MainBottomNav(2) {}
        } } }
    }
    @Test fun tournamentCardShowsPerCardRegister() {
        Api.init(paparazzi.context)
        paparazzi.snapshot { MaterialTheme { Column(Modifier.fillMaxSize().background(Ink)) {
            TournamentCard(JSONObject()
                .put("id", "XR - S2").put("name", "Xarena Season 2").put("mode", "Squad").put("slots", 12)
                .put("date_text", "Date to be announced").put("prize", "Prize pool TBA")
                .put("status", "upcoming").put("reg_open", true), false, false) {}
        } } }
    }
    @Test fun registeredReceiptNamesSelectedTournament() {
        paparazzi.snapshot { MaterialTheme { Column(Modifier.fillMaxSize().background(Ink)) {
            RegisteredReceiptCard("Xarena Season 2", JSONObject()
                .put("id", "XR-9f2a41c7-0000-0000-0000-000000000000").put("squad", "UNDEFEATED")
                .put("status", "VERIFIED").put("verified", true)) {}
        } } }
    }
    @Test fun registrationFailClosedWithoutTarget() {
        paparazzi.snapshot { MaterialTheme { Column(Modifier.fillMaxSize().background(Ink).padding(20.dp)) {
            RegisterUnavailable("Tournament registration is not configured right now. Check back soon.") {}
        } } }
    }
    @Test fun regTargetResolutionOpaqueAndFailClosed() {
        org.junit.Assert.assertEquals("XR - S2", Api.regTargetOrThrow("XR - S2"))
        org.junit.Assert.assertEquals("xr-5oar", Api.regTargetOrThrow(" xr-5oar "))
        for (bad in listOf(null, "", "   ")) {
            try { Api.regTargetOrThrow(bad); org.junit.Assert.fail("expected fail-closed for " + bad) }
            catch (e: Api.ApiError) { org.junit.Assert.assertTrue(e.message!!.contains("not configured")) }
        }
    }
    @Test fun regTargetUrlEncoding() {
        org.junit.Assert.assertEquals("XR%20-%20S2", Api.encodeEq("XR - S2"))
        org.junit.Assert.assertEquals("xr-5oar", Api.encodeEq("xr-5oar"))
    }
    @Test fun newRegistrationIdNeutralAndUnique() {
        val a = Api.newRegistrationId(); val b = Api.newRegistrationId()
        org.junit.Assert.assertTrue(a.startsWith("XR-"))
        org.junit.Assert.assertFalse(a.startsWith("XR-S1-"))
        org.junit.Assert.assertNotEquals(a, b)
    }

    @Test fun manualAreaPayloadNeverContainsCoordinates() {
        val p = AreaPayload.manual("  Bihar  ", "  Munger  ")
        org.junit.Assert.assertEquals("Bihar", p.getString("p_state"))
        org.junit.Assert.assertEquals("Munger", p.getString("p_district"))
        org.junit.Assert.assertTrue(p.isNull("p_lat")); org.junit.Assert.assertTrue(p.isNull("p_lon"))
        org.junit.Assert.assertTrue(AreaPayload.manual("Bihar", " ").isNull("p_district"))
    }


    /* Live tab: owner live_thread flag OFF shows a clear disabled state (no fake comments). */
    @Test fun liveCommentsOwnerOffFixture() {
        Api.init(paparazzi.context)
        paparazzi.snapshot { MaterialTheme { Column(Modifier.fillMaxWidth().background(Ink)) { LiveComments("fixture-tournament") } } }
    }
    /* Live tab: registered-players panel honest unavailable state before the roster RPC responds. */
    @Test fun liveRosterUnavailableFixture() {
        Api.init(paparazzi.context)
        paparazzi.snapshot { MaterialTheme { Column(Modifier.fillMaxWidth().background(Ink)) { LiveRoster("fixture-tournament") } } }
    }
    @Test fun profileFormGoogleGate() {
        paparazzi.snapshot { MaterialTheme { CompleteProfileScreen(Api.SetupInfo(true, "", "", "", true), {}, {}) } }
    }
    @Test fun profileFormPlaceholder() {
        paparazzi.snapshot { MaterialTheme { CompleteProfileScreen(Api.SetupInfo(true, "", "", "", false), {}, {}) } }
    }
    @Test fun profileFormPartialPrefilled() {
        paparazzi.snapshot { MaterialTheme { CompleteProfileScreen(Api.SetupInfo(true, "", "FixtureIGN", "123456789", false), {}, {}) } }
    }
}
