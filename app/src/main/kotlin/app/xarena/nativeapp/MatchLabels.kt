package app.xarena.nativeapp

import org.json.JSONObject

/** Ignition Cup postponement display contract (docs/NATIVE_CONTRACTS.md, Oct 24 2026).
    The four xr-5oar matches are postponed from Sat Oct 24 to Sun Oct 25, but the matches
    table CHECK constraint has no 'postponed' value, so they sit at status='cancelled'.
    For exactly those IDs render POSTPONED (never CANCELLED), keep the original starts_at
    as the old slot, and show no countdown / check-in / room-reveal UI.
    Any other cancelled match keeps normal cancelled rendering.
    Goes inert automatically when the matches flip back to 'scheduled' on reschedule. */
object MatchLabels {
    private val postponedIds = setOf(
        "d35aecce-e27b-4d81-9176-4cfd59825071", // Qualifier Lobby A
        "729dbb37-b9dd-4574-babc-7e9974aa4ce1", // Qualifier Lobby B
        "5f365c25-96e4-4dac-8f66-f05dc5d7768f", // Qualifier Lobby C
        "e7a09d84-cced-4755-b08a-e619e5c0dbe5"  // Grand Final
    )

    fun isPostponed(m: JSONObject): Boolean =
        m.s("status") == "cancelled" && m.s("id") in postponedIds

    fun label(m: JSONObject): String =
        if (isPostponed(m)) "POSTPONED" else m.s("status").uppercase()
}
