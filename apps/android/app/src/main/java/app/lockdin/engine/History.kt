// Session history (roadmap step 2, B): what each finished session was, kept on this phone only.
// Pure logic; storage is state/HistoryStore.kt.
package app.lockdin.engine

import java.time.Instant
import java.time.LocalDate
import java.time.ZoneId

enum class Outcome { COMPLETED, ENDED_EARLY, EMERGENCY, STOPPED }

/**
 * One finished session. `focusedMs` counts only focus time actually reached; breaks never count.
 * `blocked` maps an app package or "site:<domain>" to how often its block screen came up.
 */
data class HistoryEntry(
    val id: String,
    val start: Long,
    val end: Long,
    val plannedEnd: Long,
    val focusedMs: Long,
    val plannedFocusMs: Long,
    val roundsDone: Int,
    val roundsPlanned: Int,
    val task: String?,
    val profileId: String,
    val profileName: String,
    val outcome: Outcome,
    /** Set when the session came from a planned occurrence ("KIND-free" occurrence key). */
    val occurrenceKey: String? = null,
    val blocked: Map<String, Int> = emptyMap(),
) {
    val focusedMinutes: Long get() = focusedMs / 60_000
}

data class DayTotal(val date: LocalDate, val focusedMinutes: Long, val sessions: Int)

data class WeekSummary(
    val start: LocalDate,
    val days: List<DayTotal>,
    val focusedMinutes: Long,
    val sessions: Int,
    val completed: Int,
    /** Planned occurrences in this week that have already started (as currently planned). */
    val planned: Int,
    /** Of those, how many have a finished-as-planned session in history. */
    val plannedDone: Int,
)

object History {
    const val MAX_ENTRIES = 3000
    val RETENTION_CHOICES = listOf(30, 90, 365, 0) // 0 = keep everything

    /**
     * Builds the entry for a session that is being left at `now`. A session still running at
     * `now` (left through a test or an unusual path) counts as stopped at `now`.
     */
    fun entryFor(
        id: String, start: Long, segments: List<Segment>, controls: SessionControls, now: Long,
        task: String?, profileId: String, profileName: String, occurrenceKey: String?, blocked: Map<String, Int>,
    ): HistoryEntry {
        val occ = Session.occurrenceFrom(start, segments)
        val snap = Session.evaluate(occ, controls, now)
        val (end, outcome) = when (snap.status) {
            SessionStatus.COMPLETED -> occ.end to Outcome.COMPLETED
            SessionStatus.ENDED_BY_EMERGENCY_RECOVERY -> (controls.emergencyRecoveryAt ?: now).coerceIn(start, occ.end) to Outcome.EMERGENCY
            SessionStatus.ENDED_BY_OVERRIDE -> (controls.overrides
                .filter { it.scope == OverrideScope.REST_OF_SESSION }
                .mapNotNull { Session.takesEffect(it, occ) }.minOrNull() ?: now).coerceIn(start, occ.end) to Outcome.ENDED_EARLY
            else -> now.coerceIn(start, occ.end) to Outcome.STOPPED
        }
        val focus = occ.phases.filter { it.kind == PhaseKind.FOCUS }
        return HistoryEntry(
            id = id, start = start, end = end, plannedEnd = occ.end,
            focusedMs = focus.sumOf { maxOf(0L, minOf(it.end, end) - it.start) },
            plannedFocusMs = focus.sumOf { it.end - it.start },
            roundsDone = focus.count { it.end <= end }, roundsPlanned = focus.size,
            task = task, profileId = profileId, profileName = profileName, outcome = outcome,
            occurrenceKey = occurrenceKey, blocked = blocked,
        )
    }

    /** Adds or replaces (same id), newest first, applying retention and the size cap. */
    fun add(entries: List<HistoryEntry>, e: HistoryEntry, retentionDays: Int, now: Long): List<HistoryEntry> =
        prune((listOf(e) + entries.filter { it.id != e.id }).sortedByDescending { it.start }, retentionDays, now)

    fun prune(entries: List<HistoryEntry>, retentionDays: Int, now: Long): List<HistoryEntry> {
        val kept = if (retentionDays <= 0) entries else entries.filter { it.start >= now - retentionDays * 86_400_000L }
        return kept.take(MAX_ENTRIES)
    }

    fun dateOf(t: Long, zone: ZoneId): LocalDate = Instant.ofEpochMilli(t).atZone(zone).toLocalDate()

    /** Entries grouped by local day, newest day first. */
    fun byDay(entries: List<HistoryEntry>, zone: ZoneId): List<Pair<LocalDate, List<HistoryEntry>>> =
        entries.sortedByDescending { it.start }.groupBy { dateOf(it.start, zone) }.toList()

    /** Monday of the week containing `date`. */
    fun weekStart(date: LocalDate): LocalDate = date.minusDays((date.dayOfWeek.value - 1).toLong())

    fun week(
        entries: List<HistoryEntry>, weekStart: LocalDate, zone: ZoneId,
        schedule: List<ScheduledSession>, now: Long,
    ): WeekSummary {
        val from = weekStart.atStartOfDay(zone).toInstant().toEpochMilli()
        val to = weekStart.plusDays(7).atStartOfDay(zone).toInstant().toEpochMilli()
        val inWeek = entries.filter { it.start in from until to }
        val days = (0L until 7L).map { d ->
            val date = weekStart.plusDays(d)
            val list = inWeek.filter { dateOf(it.start, zone) == date }
            DayTotal(date, list.sumOf { it.focusedMinutes }, list.size)
        }
        val plannedKeys = Planner.occurrences(schedule, from, minOf(to, now + 1), zone)
            .filter { it.start <= now }.map { Planner.key(it) }.toSet()
        val doneKeys = inWeek.filter { it.outcome == Outcome.COMPLETED }.mapNotNull { it.occurrenceKey }.toSet()
        return WeekSummary(
            weekStart, days, inWeek.sumOf { it.focusedMinutes }, inWeek.size,
            inWeek.count { it.outcome == Outcome.COMPLETED }, plannedKeys.size, plannedKeys.count { it in doneKeys },
        )
    }

    /** CSV for export (RFC 4180 quoting). Times in the given zone, ISO-8601. */
    fun csv(entries: List<HistoryEntry>, zone: ZoneId): String {
        fun q(s: String?) = if (s == null) "" else "\"" + s.replace("\"", "\"\"") + "\""
        fun t(ms: Long) = Instant.ofEpochMilli(ms).atZone(zone).toLocalDateTime().withNano(0).toString()
        val head = "start,end,planned_end,focused_minutes,planned_focus_minutes,rounds_done,rounds_planned,task,profile,outcome,planned_session,blocked_total"
        return (listOf(head) + entries.sortedBy { it.start }.map { e ->
            listOf(
                t(e.start), t(e.end), t(e.plannedEnd), e.focusedMinutes.toString(), (e.plannedFocusMs / 60_000).toString(),
                e.roundsDone.toString(), e.roundsPlanned.toString(), q(e.task), q(e.profileName), e.outcome.name.lowercase(),
                if (e.occurrenceKey != null) "yes" else "no", e.blocked.values.sum().toString(),
            ).joinToString(",")
        }).joinToString("\r\n", postfix = "\r\n")
    }
}
