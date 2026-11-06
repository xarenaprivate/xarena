package app.xarena.nativeapp

import androidx.compose.runtime.*
import kotlinx.coroutines.sync.Mutex
import kotlinx.coroutines.sync.withLock
import org.json.JSONObject

/** Server-owned per-account read cursors. Receiving a push never marks anything read. */
object DmUnread {
    private val lock = Mutex()
    var total by mutableIntStateOf(0); private set
    var perThread by mutableStateOf<Map<String, Int>>(emptyMap()); private set
    var newestAt by mutableStateOf<Map<String, String>>(emptyMap()); private set
    var summaries by mutableStateOf<List<JSONObject>>(emptyList()); private set
    private var owner: String? = null
    fun clear() { owner = null; total = 0; perThread = emptyMap(); newestAt = emptyMap(); summaries = emptyList() }
    suspend fun refresh(): List<JSONObject> = lock.withLock {
        val uid = Api.uid ?: run { clear(); return@withLock emptyList() }
        if (owner != uid) { clear(); owner = uid }
        val r = Api.authedRpc("dm_unread_summary", JSONObject())
        check(r.optBoolean("ok")) { "Could not load unread messages." }
        if (Api.uid != uid) return@withLock emptyList()
        val a = r.optJSONArray("threads") ?: org.json.JSONArray()
        val rows = (0 until a.length()).map { a.getJSONObject(it) }
        perThread = rows.associate { it.s("thread_id") to it.optInt("unread_count", 0) }
        newestAt = rows.associate { it.s("thread_id") to it.s("last_message_at") }
        total = r.optInt("total_unread", 0); summaries = rows
        rows
    }
    suspend fun markRead(threadId: String, displayedId: String) {
        if (displayedId.isBlank()) return
        val uid = Api.uid ?: return
        val r = Api.authedRpc("dm_mark_read", JSONObject().put("p_thread", threadId).put("p_message", displayedId))
        check(r.optBoolean("ok")) { r.optString("error", "Could not mark messages read.") }
        if (Api.uid == uid) refresh()
    }
}
