// Automatic schedules and reminders: which planned occurrences exist, what should happen when,
// and the decisions for starting, skipping and missed sessions. Pure (java.time only), so every
// rule is tested on the JVM; the Android side only sets alarms and posts notifications.
package app.lockdin.engine

import java.time.Instant
import java.time.LocalDate
import java.time.LocalTime
import java.time.ZoneId

enum class EventKind { REMIND, START, MISSED_CHECK }

/** One thing to do for one occurrence. `key` identifies the occurrence; `id` the event. */
data class PlanEvent(val at: Long, val kind: EventKind, val occ: Upcoming) {
    val key: String get() = Planner.key(occ)
    val id: String get() = "${kind.name}:$key"
}

data class ReminderSettings(
    /** Reminders before sessions (each session sets how long before). */
    val remindersOn: Boolean = true,
    /** Focus starting, break starting, break ending, session complete. */
    val sessionEventsOn: Boolean = true,
    /** One follow-up after a missed session, offering a short restart. */
    val missedFollowUpOn: Boolean = true,
    /** Quiet hours (local time); both null = off. Reminders and follow-ups wait until they end. */
    val quietStart: LocalTime? = null,
    val quietEnd: LocalTime? = null,
    /** Reminders + follow-ups per rolling hour. */
    val maxPerHour: Int = 4,
)

sealed interface StartDecision {
    /** Start now. `start` is the occurrence start (earlier than now if late: past phases are skipped). */
    data class Start(val start: Long, val late: Boolean) : StartDecision
    /** Another session is running; the scheduled one doesn't replace it. */
    data object AlreadyRunning : StartDecision
    /** Too late to start: no focus time is left in the occurrence. */
    data object Missed : StartDecision
}

object Planner {
    const val MIN = 60_000L
    /** A session started this long before an occurrence still counts as doing it. */
    const val EARLY_GRACE_MS = 10 * MIN
    /** Missed check runs this long after an occurrence ends. */
    const val MISSED_DELAY_MS = MIN
    /** Events older than this are dropped rather than acted on late (e.g. after days powered off). */
    const val STALE_MS = 12 * 60 * MIN
    val REMIND_CHOICES = listOf(0, 5, 10, 15, 30)

    fun key(o: Upcoming) = "${o.session.id}@${o.start}"

    /** Occurrences (enabled, not skipped) overlapping [from, to). */
    fun occurrences(sessions: List<ScheduledSession>, from: Long, to: Long, zone: ZoneId): List<Upcoming> {
        val first = Instant.ofEpochMilli(from).atZone(zone).toLocalDate().minusDays(1)
        val last = Instant.ofEpochMilli(to).atZone(zone).toLocalDate()
        val out = mutableListOf<Upcoming>()
        var day = first
        while (!day.isAfter(last)) {
            for (s in sessions) {
                if (!s.enabled || !s.occursOn(day)) continue
                val start = Schedule.startOn(s, day, zone)
                val o = Upcoming(s, start, start + s.totalMinutes * MIN)
                if (o.end > from && o.start < to) out += o
            }
            day = day.plusDays(1)
        }
        return out.sortedBy { it.start }
    }

    /** Every event for occurrences from a day ago to eight days ahead. */
    fun events(sessions: List<ScheduledSession>, now: Long, zone: ZoneId, settings: ReminderSettings): List<PlanEvent> =
        occurrences(sessions, now - 24 * 60 * MIN, now + 8 * 24 * 60 * MIN, zone).flatMap { o ->
            buildList {
                val m = o.session.remindMinutes
                if (settings.remindersOn && m > 0) add(PlanEvent(o.start - m * MIN, EventKind.REMIND, o))
                add(PlanEvent(o.start, EventKind.START, o))
                if (settings.missedFollowUpOn) add(PlanEvent(o.end + MISSED_DELAY_MS, EventKind.MISSED_CHECK, o))
            }
        }.sortedBy { it.at }

    /** Events that are due now and not yet handled. Very old ones are skipped (see STALE_MS). */
    fun due(events: List<PlanEvent>, now: Long, handled: Set<String>): List<PlanEvent> =
        events.filter { it.at <= now && now - it.at <= STALE_MS && it.id !in handled }

    /** When to wake next: the earliest event after now. */
    fun nextWake(events: List<PlanEvent>, now: Long, handled: Set<String>): Long? =
        events.filter { it.at > now && it.id !in handled }.minOfOrNull { it.at }

    /**
     * Whether an automatic start should happen. Joins the occurrence in progress when late
     * (phone off, inexact alarm), as long as some focus time is left.
     */
    fun decideStart(o: Upcoming, now: Long, otherSessionRunning: Boolean): StartDecision {
        if (otherSessionRunning) return StartDecision.AlreadyRunning
        val occ = Session.occurrenceFrom(o.start, o.session.plan)
        val focusLeft = occ.phases.any { it.kind == PhaseKind.FOCUS && it.end - maxOf(now, it.start) >= MIN }
        if (now >= o.end || !focusLeft) return StartDecision.Missed
        return StartDecision.Start(o.start, late = now - o.start > MIN)
    }

    /** Missed: no session was started for it (or shortly before it), and it was not skipped. */
    fun wasMissed(o: Upcoming, sessionStarts: List<Long>): Boolean =
        sessionStarts.none { it >= o.start - EARLY_GRACE_MS && it < o.end }

    fun inQuietHours(now: Long, zone: ZoneId, s: ReminderSettings): Boolean {
        val a = s.quietStart ?: return false
        val b = s.quietEnd ?: return false
        if (a == b) return false
        val t = Instant.ofEpochMilli(now).atZone(zone).toLocalTime()
        return if (a < b) t >= a && t < b else t >= a || t < b // overnight window wraps midnight
    }

    /** End of the current quiet period (to deliver the reminder then), or null if not quiet. */
    fun quietEndsAt(now: Long, zone: ZoneId, s: ReminderSettings): Long? {
        if (!inQuietHours(now, zone, s)) return null
        val end = s.quietEnd!!
        val z = Instant.ofEpochMilli(now).atZone(zone)
        var at = z.toLocalDate().atTime(end).atZone(zone)
        if (!at.isAfter(z)) at = at.plusDays(1)
        return at.toInstant().toEpochMilli()
    }

    fun underRateLimit(sent: List<Long>, now: Long, s: ReminderSettings): Boolean =
        sent.count { now - it < 60 * MIN } < s.maxPerHour

    /** Skipping an occurrence: the date it falls on (local). */
    fun dateOf(o: Upcoming, zone: ZoneId): LocalDate = Instant.ofEpochMilli(o.start).atZone(zone).toLocalDate()
}
