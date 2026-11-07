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
    @Test fun manualAreaPayloadNeverContainsCoordinates() {
        val p = AreaPayload.manual("  Bihar  ", "  Munger  ")
        org.junit.Assert.assertEquals("Bihar", p.getString("p_state"))
        org.junit.Assert.assertEquals("Munger", p.getString("p_district"))
        org.junit.Assert.assertTrue(p.isNull("p_lat")); org.junit.Assert.assertTrue(p.isNull("p_lon"))
        org.junit.Assert.assertTrue(AreaPayload.manual("Bihar", " ").isNull("p_district"))
    }

}
