package app.xarena.nativeapp

import app.cash.paparazzi.Paparazzi
import org.json.JSONArray
import org.json.JSONObject
import org.junit.Rule
import org.junit.Test

/** v81 Squad Finder: populated and all-empty states. Fixtures are TEST data rendered by the
    same composables; the shipped UI never fabricates players, squads, invites or rooms. */
class SquadFinderVisualTest {
    @get:Rule val paparazzi = Paparazzi()

    @org.junit.Before fun setUp() {
        DataSaver.testOverride = false
        SfSeams.clear()
    }

    private fun player(name: String, role: String, trust: Int?, free: Boolean, mic: Boolean, presence: String): JSONObject {
        val j = JSONObject()
        j.put("id", "u-" + name); j.put("username", name); j.put("role", role)
        j.put("verified", true)
        if (trust != null) j.put("trust_score", trust) else j.put("trust_score", JSONObject.NULL)
        j.put("mic", mic); j.put("primary_language", "Hindi"); j.put("region", "Bihar")
        j.put("free_agent", free); j.put("presence", presence)
        j.put("languages", JSONArray().put("Hindi").put("English"))
        j.put("kd", 2.4); j.put("kd_source", "self_reported")
        return j
    }

    private fun squad(name: String, vis: String, members: Int, captain: Boolean): JSONObject {
        val j = JSONObject()
        j.put("id", "sq-" + name); j.put("name", name); j.put("intro_line", "Serious grind, no toxicity")
        j.put("visibility", vis); j.put("mode", "BR"); j.put("map", "Bermuda"); j.put("region", "IN-East")
        j.put("comms_voice", true); j.put("roles_needed", JSONArray().put("Rusher").put("Sniper"))
        j.put("member_count", members); j.put("max_members", 4)
        j.put("is_member", captain); j.put("is_captain", captain)
        return j
    }

    @Test fun sfPlayerDiscovery_populated() {
        SfSeams.players = listOf(
            sfPlayerFrom(player("shadow_k", "Rusher", 82, true, true, "online")),
            sfPlayerFrom(player("pro_girl", "IGL", null, false, true, "in_match"))
        )
        paparazzi.snapshot { SfPlayerDiscoveryScreen(onBack = {}, onOpenPlayer = {}) }
    }

    @Test fun sfPlayerDiscovery_empty() {
        SfSeams.players = emptyList()
        paparazzi.snapshot { SfPlayerDiscoveryScreen(onBack = {}, onOpenPlayer = {}) }
    }

    @Test fun sfSquadDiscovery_populated() {
        SfSeams.squads = listOf(sfSquadFrom(squad("Patna Wolves", "public", 3, false)), sfSquadFrom(squad("Night Owls", "private", 4, false)))
        paparazzi.snapshot { SquadDiscoveryScreen(onBack = {}, onOpenSquad = {}) }
    }

    @Test fun sfSquadDiscovery_empty() {
        SfSeams.squads = emptyList()
        paparazzi.snapshot { SquadDiscoveryScreen(onBack = {}, onOpenSquad = {}) }
    }

    @Test fun sfInvites_populated() {
        val soon = java.time.OffsetDateTime.now().plusSeconds(25).toString()
        SfSeams.invites = JSONArray()
            .put(JSONObject().put("id", 11).put("squad_name", "Patna Wolves").put("from_name", "shadow_k").put("status", "pending").put("expires_at", soon))
            .put(JSONObject().put("id", 12).put("squad_name", "Night Owls").put("from_name", "pro_girl").put("status", "pending").put("expires_at", "2026-11-12T10:00:00+00:00"))
        paparazzi.snapshot { InvitesInboxScreen(onBack = {}) }
    }

    @Test fun sfInvites_empty() {
        SfSeams.invites = JSONArray()
        paparazzi.snapshot { InvitesInboxScreen(onBack = {}) }
    }

    @Test fun sfTryouts_populated() {
        SfSeams.rooms = JSONArray()
            .put(JSONObject().put("id", "r1").put("mode", "BR").put("map", "Bermuda").put("member_count", 3).put("duration_min", 30).put("host_name", "shadow_k").put("code", "XR-4F2K9A"))
            .put(JSONObject().put("id", "r2").put("mode", "CS").put("map", "Purgatory").put("member_count", 1).put("duration_min", 15).put("host_name", "pro_girl").put("code", "XR-9Q1ZZT"))
        paparazzi.snapshot { TryoutRoomsScreen(onBack = {}, onOpenRoom = {}) }
    }

    @Test fun sfTryouts_empty() {
        SfSeams.rooms = JSONArray()
        paparazzi.snapshot { TryoutRoomsScreen(onBack = {}, onOpenRoom = {}) }
    }

    @Test fun sfIglDashboard_populated() {
        SfSeams.mySquads = listOf(sfSquadFrom(squad("Patna Wolves", "public", 3, true)))
        SfSeams.requests = JSONArray()
            .put(JSONObject().put("id", "q1").put("player_name", "newbie_21").put("note", "Rusher, mic hai"))
            .put(JSONObject().put("id", "q2").put("player_name", " clutchgod").put("note", ""))
        SfSeams.plan = JSONObject().put("body", "Drop Clock Tower, rotate east, save utilities for zone 4.").put("revision", 3)
        paparazzi.snapshot { IglDashboardScreen(onBack = {}) }
    }

    @Test fun sfIglDashboard_notCaptain() {
        SfSeams.mySquads = listOf(sfSquadFrom(squad("Patna Wolves", "public", 3, false)))
        paparazzi.snapshot { IglDashboardScreen(onBack = {}) }
    }
}
