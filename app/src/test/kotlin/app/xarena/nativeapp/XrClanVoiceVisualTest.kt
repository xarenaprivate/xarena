package app.xarena.nativeapp

import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.padding
import androidx.compose.material3.Text
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import app.cash.paparazzi.Paparazzi
import org.json.JSONObject
import org.junit.Rule
import org.junit.Test

/** v0.17.61 regression gate: clan/voice new-look screens must render real Material vector
    icons (font-independent ImageVector path data) and safe avatar initials. On Android 10
    (Realme C20) the v0.17.60 hand-drawn Canvas icons rendered as raw letter glyphs.
    Fixtures are TEST data rendered by the same composables; the shipped UI never
    fabricates clans, members, messages or voice peers. */
class XrClanVoiceVisualTest {
    @get:Rule val paparazzi = Paparazzi()

    @org.junit.Before fun setUp() {
        DataSaver.testOverride = false
    }

    private fun clanInfo(): JSONObject {
        val j = JSONObject()
        j.put("squad_id", "sq-test"); j.put("name", "Patna Wolves"); j.put("tag", "PW")
        j.put("members", 27)
        return j
    }

    private fun msg(id: Long, by: String, body: String, kind: String = "text"): JSONObject {
        val j = JSONObject()
        j.put("id", id); j.put("user_id", "u-" + by); j.put("by", by); j.put("body", body)
        j.put("kind", kind); j.put("created_at", "2026-11-16T07:30:00+00:00")
        if (kind == "voice") { j.put("media_path", "voice/test.m4a"); j.put("duration_ms", 42000) }
        return j
    }

    @Test fun clanHome_populated() {
        paparazzi.snapshot {
            RoomHomeNewLook(clanInfo(), null, {}, {}, {}, {}, {}, {}, {})
        }
    }

    @Test fun clanChat_messages() {
        val msgs = listOf(
            msg(1, "shadow_k", "Scrim at 9 tonight, full squad"),
            msg(2, "pro_girl", "Check this link https://xarena.gg/t/season2"),
            msg(3, "\u00E0b\u00E7_user", "mic test done"),
            msg(4, "shadow_k", "", "voice")
        )
        paparazzi.snapshot {
            RoomNewLook(clanInfo(), null, msgs, "", {}, null, {}, {}, {}, {}, "sq-test", { _, _, _, _ -> }, {})
        }
    }

    @Test fun voicePtt_peers() {
        val peers = listOf(
            VoicePeer("u1", "shadow_k", muted = false, conn = "connected", speaking = true),
            VoicePeer("u2", "pro_girl", muted = true, conn = "connected"),
            VoicePeer("u3", "\u00E0b\u00E7_user", muted = false, conn = "connected"),
            VoicePeer("u4", "listener_one", muted = true, listener = true, conn = "connected")
        )
        paparazzi.snapshot {
            VoicePttLook("Patna Wolves - Clan Voice", peers, null, muted = true, inLobby = false, handRaised = false, {}, {}, {}, {}, {})
        }
    }

    @Test fun iconsAndInitials_direct() {
        /* The exact components that broke on Android 10: every XrIcon + xrInitial samples. */
        paparazzi.snapshot {
            Column(Modifier.fillMaxSize().background(Ink).padding(16.dp)) {
                Row(verticalAlignment = Alignment.CenterVertically) {
                    XrIconMic(Gold, 28.dp); XrIconChat(Gold, 28.dp); XrIconPerson(Gold, 28.dp)
                    XrIconGroup(Gold, 28.dp); XrIconCard(Gold, 28.dp); XrIconSearch(Gold, 28.dp)
                }
                Text("initials: " + listOf("shadow_k", "\u00E0b\u00E7_user", "\uD83C\uDFAEgamer", "  spaces", "", "9ine").joinToString(" ") { xrInitial(it) }, color = Color.White, fontSize = 14.sp)
            }
        }
    }
}
