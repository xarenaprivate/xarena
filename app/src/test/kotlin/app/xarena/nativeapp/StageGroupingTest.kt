package app.xarena.nativeapp

import app.cash.paparazzi.Paparazzi
import org.json.JSONArray
import org.json.JSONObject
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Rule
import org.junit.Test

/** v0.17.62 stage grouping: buildMatchSections contract vs matches_public.stage +
    tournament_stages(stage,label,ord). Fixtures are TEST data; shipped UI never
    fabricates tournaments, stages or matches. */
class StageGroupingTest {
    @get:Rule val paparazzi = Paparazzi()

    @org.junit.Before fun setUp() { DataSaver.testOverride = false }

    private fun m(tid: String, title: String, stage: String?, status: String): JSONObject {
        val j = JSONObject()
        j.put("id", "m-" + title); j.put("tournament_id", tid); j.put("title", title); j.put("status", status)
        j.put("starts_at", "2026-11-16T06:30:00+00:00")
        if (stage == null) j.put("stage", JSONObject.NULL) else j.put("stage", stage)
        return j
    }
    private fun st(tid: String, stage: String, label: String, ord: Int) =
        JSONObject().put("tournament_id", tid).put("stage", stage).put("label", label).put("ord", ord)

    @Test fun noStageRows_flatLegacy() {
        val ms = JSONArray().put(m("t1", "M1", null, "scheduled"))
        assertNull(buildMatchSections(ms, JSONArray()))
    }

    @Test fun unstagedTournament_flatLegacy() {
        val ms = JSONArray().put(m("t1", "M1", null, "scheduled"))
        val rows = JSONArray().put(st("other", "qualifier", "Qualifier lobby", 1))
        assertNull(buildMatchSections(ms, rows))
    }

    @Test fun stagedGrouping_orderAndComingSoon() {
        val ms = JSONArray()
            .put(m("t1", "M1", "qualifier", "done"))
            .put(m("t1", "M2", "qualifier", "scheduled"))
        val rows = JSONArray()
            .put(st("t1", "final", "Final", 3))
            .put(st("t1", "qualifier", "Qualifier lobby", 1))
            .put(st("t1", "semi_final", "Semi Final", 2))
        val secs = buildMatchSections(ms, rows)!!
        assertEquals(listOf("qualifier", "semi_final", "final"), secs.map { it.stageId })
        assertEquals("SCHEDULED", secs[0].status)
        assertEquals("COMING SOON", secs[1].status)
        assertTrue(secs[1].matches.isEmpty())
        assertEquals(2, secs[0].matches.size)
        assertEquals("Qualifier lobby", secs[0].label)
    }

    @Test fun statuses_liveAndDone() {
        val ms = JSONArray()
            .put(m("t1", "M1", "qualifier", "live"))
            .put(m("t1", "M2", "semi_final", "done"))
        val rows = JSONArray()
            .put(st("t1", "qualifier", "Qualifier lobby", 1))
            .put(st("t1", "semi_final", "Semi Final", 2))
        val secs = buildMatchSections(ms, rows)!!
        assertEquals("LIVE", secs[0].status)
        assertEquals("DONE", secs[1].status)
    }

    @Test fun blankStageMatches_appendedFlat() {
        val ms = JSONArray()
            .put(m("t1", "M1", "qualifier", "scheduled"))
            .put(m("t1", "Legacy", null, "scheduled"))
        val rows = JSONArray().put(st("t1", "qualifier", "Qualifier lobby", 1))
        val secs = buildMatchSections(ms, rows)!!
        assertEquals(2, secs.size)
        assertEquals("", secs[1].stageId)
        assertEquals("Legacy", secs[1].matches[0].s("title"))
    }

    @Test fun mixedStagedAndLegacyTournament_legacyStaysVisible() {
        val ms = JSONArray()
            .put(m("old", "Old Cup M1", null, "cancelled"))
            .put(m("t1", "M1", "qualifier", "scheduled"))
        val rows = JSONArray().put(st("t1", "qualifier", "Qualifier lobby", 1))
        val secs = buildMatchSections(ms, rows)!!
        assertEquals(2, secs.size)
        assertEquals("", secs[0].stageId)
        assertEquals("Old Cup M1", secs[0].matches[0].s("title"))
        assertEquals("qualifier", secs[1].stageId)
    }

    @Test fun stageHeader_live() {
        paparazzi.snapshot { StageHeader("Qualifier lobby", "LIVE") }
    }

    @Test fun stageHeader_comingSoon() {
        paparazzi.snapshot { StageHeader("Semi Final", "COMING SOON") }
    }
}
