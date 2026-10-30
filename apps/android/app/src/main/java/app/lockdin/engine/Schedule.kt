// Weekly sessions in the phone's time zone (java.time). Local start times are
// resolved with java.time's rules, which match the core's RFC 5545 behaviour:
// a time inside a spring-forward gap moves later by the gap length, and a
// repeated fall-back time uses the earlier offset. Covered by ScheduleTest.
package app.lockdin.engine

import java.time.DayOfWeek
import java.time.Instant
import java.time.LocalDate
import java.time.LocalTime
import java.time.ZoneId
import java.time.ZonedDateTime

/**
 * A planned session. Weekly routines have `days` and no `date`; a one-time session has a
 * `date` (its `days` are ignored). Disabled sessions stay listed but never come up.
 */
data class ScheduledSession(
    val id: String,
    val title: String,
    val days: Set<DayOfWeek>,
    val startTime: LocalTime,
    val focusMinutes: Int,
    val breakMinutes: Int,
    val rounds: Int,
    val profileId: String,
    val date: LocalDate? = null,
    val enabled: Boolean = true,
) {
    val oneTime: Boolean get() = date != null
    val totalMinutes: Int get() = rounds * focusMinutes + (rounds - 1) * breakMinutes
    val plan: List<Segment> get() = Session.roundsPlan(rounds, focusMinutes, breakMinutes)
    fun occursOn(day: LocalDate): Boolean = if (date != null) day == date else day.dayOfWeek in days
}

data class Upcoming(val session: ScheduledSession, val start: Long, val end: Long)

object Schedule {
    fun startOn(s: ScheduledSession, day: LocalDate, zone: ZoneId): Long =
        ZonedDateTime.ofLocal(day.atTime(s.startTime), zone, null).toInstant().toEpochMilli()

    /** Next occurrence starting after `now` or in progress, within 8 days. */
    fun nextUp(sessions: List<ScheduledSession>, now: Long, zone: ZoneId): Upcoming? {
        val today = Instant.ofEpochMilli(now).atZone(zone).toLocalDate()
        for (d in 0..7) {
            val day = today.plusDays(d.toLong())
            val best = sessions.filter { it.enabled && it.occursOn(day) }
                .map { s -> startOn(s, day, zone).let { Upcoming(s, it, it + s.totalMinutes * 60_000L) } }
                .filter { it.end > now }
                .minByOrNull { it.start }
            if (best != null) return best
        }
        return null
    }

    /**
     * Enabled sessions in `others` that would run at the same time as `s` on some day
     * (including sessions that run past midnight). A one-time session counts on its date only.
     */
    fun overlaps(s: ScheduledSession, others: List<ScheduledSession>): List<ScheduledSession> {
        fun mins(x: ScheduledSession) = x.startTime.hour * 60 + x.startTime.minute
        fun daysOf(x: ScheduledSession) = x.date?.let { setOf(it.dayOfWeek) } ?: x.days
        val a0 = mins(s); val a1 = a0 + s.totalMinutes
        return others.filter { o ->
            if (o.id == s.id || !o.enabled) return@filter false
            // Two one-time sessions can only meet on the same or adjacent dates.
            if (s.date != null && o.date != null && kotlin.math.abs(o.date.toEpochDay() - s.date.toEpochDay()) > 1) return@filter false
            val b0 = mins(o); val b1 = b0 + o.totalMinutes
            daysOf(s).any { d ->
                daysOf(o).any { e ->
                    val shift = (((e.value - d.value) % 7 + 7) % 7) * 1440
                    listOf(shift, shift - 7 * 1440).any { off -> a0 < b1 + off && b0 + off < a1 }
                }
            }
        }
    }
}
