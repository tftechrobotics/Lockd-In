// Session history on this phone (roadmap step 2, B). Kept in its own file in no-backup storage,
// separate from the app state, so the frequent state writes stay small. Written only when an
// entry is added or deleted. Nothing leaves the phone unless the person exports it.
package app.lockdin.state

import android.content.Context
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.setValue
import app.lockdin.engine.History
import app.lockdin.engine.HistoryEntry
import app.lockdin.engine.Outcome
import org.json.JSONArray
import org.json.JSONException
import org.json.JSONObject
import java.io.File

class HistoryStore(initial: List<HistoryEntry>, private val persist: (String) -> Unit) {
    var entries by mutableStateOf(initial)
        private set

    private fun set(list: List<HistoryEntry>) {
        entries = list
        persist(HistoryCodec.encode(list))
    }

    fun add(e: HistoryEntry, retentionDays: Int, now: Long) = set(History.add(entries, e, retentionDays, now))

    fun prune(retentionDays: Int, now: Long) {
        val kept = History.prune(entries, retentionDays, now)
        if (kept.size != entries.size) set(kept)
    }

    /** Builds the history entry for a session being left and records it. */
    fun record(a: ActiveSession, s: AppState, now: Long) = add(
        History.entryFor(
            a.id, a.start, a.segments, a.controls, now, a.intention, a.profileId,
            s.profiles.firstOrNull { it.id == a.profileId }?.name ?: "Profile", a.occurrenceKey, a.blocked, a.unlocked,
        ),
        s.historyDays, now,
    )

    fun delete(id: String) = set(entries.filter { it.id != id })

    fun deleteDay(ids: Set<String>) = set(entries.filter { it.id !in ids })

    fun clear() = set(emptyList())

    companion object {
        private const val FILE = "history.v1.json"
        @Volatile private var shared: HistoryStore? = null

        /** The one history for this process. The file is small (≤ 3000 entries); read once. */
        fun get(context: Context): HistoryStore = shared ?: synchronized(this) {
            shared ?: run {
                val file = File(context.applicationContext.noBackupFilesDir, FILE)
                val initial = runCatching { if (file.exists()) HistoryCodec.decode(file.readText()) else emptyList() }.getOrDefault(emptyList())
                HistoryStore(initial) { json ->
                    // Write to a temp file and rename, so a crash never leaves a half-written file.
                    runCatching {
                        val tmp = File(file.parentFile, "$FILE.tmp")
                        tmp.writeText(json)
                        if (!tmp.renameTo(file)) { file.delete(); tmp.renameTo(file) }
                    }
                }.also { shared = it }
            }
        }

        fun recordLeft(context: Context, a: ActiveSession, s: AppState, now: Long) = get(context).record(a, s, now)
    }
}

object HistoryCodec {
    const val VERSION = 1

    fun encode(list: List<HistoryEntry>): String = JSONObject().put("version", VERSION).put("entries", JSONArray(list.map { e ->
        JSONObject().put("id", e.id).put("start", e.start).put("end", e.end).put("plannedEnd", e.plannedEnd)
            .put("focusedMs", e.focusedMs).put("plannedFocusMs", e.plannedFocusMs)
            .put("roundsDone", e.roundsDone).put("roundsPlanned", e.roundsPlanned)
            .put("task", e.task ?: JSONObject.NULL).put("profileId", e.profileId).put("profileName", e.profileName)
            .put("outcome", e.outcome.name).put("occurrenceKey", e.occurrenceKey ?: JSONObject.NULL)
            .put("blocked", JSONObject().apply { e.blocked.forEach { (k, v) -> put(k, v) } })
            .put("unlocked", e.unlocked)
    })).toString()

    /** Unknown versions and damaged entries are skipped, never fatal. */
    fun decode(raw: String): List<HistoryEntry> = try {
        val o = JSONObject(raw)
        if (o.optInt("version", 0) != VERSION) emptyList()
        else {
            val a = o.getJSONArray("entries")
            (0 until a.length()).mapNotNull { i -> runCatching { entryOf(a.getJSONObject(i)) }.getOrNull() }
                .sortedByDescending { it.start }.take(History.MAX_ENTRIES)
        }
    } catch (e: JSONException) { emptyList() }

    private fun entryOf(e: JSONObject): HistoryEntry {
        val start = e.getLong("start"); val end = e.getLong("end")
        require(end >= start)
        return HistoryEntry(
            id = e.getString("id").take(64), start = start, end = end, plannedEnd = e.optLong("plannedEnd", end),
            focusedMs = e.getLong("focusedMs").coerceAtLeast(0), plannedFocusMs = e.optLong("plannedFocusMs", 0).coerceAtLeast(0),
            roundsDone = e.optInt("roundsDone", 0).coerceIn(0, 50), roundsPlanned = e.optInt("roundsPlanned", 1).coerceIn(0, 50),
            task = if (e.isNull("task") || !e.has("task")) null else e.getString("task").take(120),
            profileId = e.optString("profileId", ""), profileName = e.optString("profileName", "Profile").take(60),
            outcome = runCatching { Outcome.valueOf(e.getString("outcome")) }.getOrDefault(Outcome.STOPPED),
            occurrenceKey = if (e.isNull("occurrenceKey") || !e.has("occurrenceKey")) null else e.getString("occurrenceKey").take(120),
            blocked = e.optJSONObject("blocked")?.let { b ->
                b.keys().asSequence().take(100).filter { it.length <= 260 }.associateWith { b.optInt(it, 0).coerceIn(0, 100_000) }.filterValues { it > 0 }
            } ?: emptyMap(),
            unlocked = e.optBoolean("unlocked", false),
        )
    }
}
