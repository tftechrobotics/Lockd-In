// Calendar suggestions (roadmap step 4, C): free study windows between the person's calendar
// events, and overlaps between planned sessions and events. Pure logic; reading the calendar is
// cal/DeviceCalendar.kt. Suggestions are only ever offered; nothing is planned without the person.
package app.lockdin.engine

import java.time.LocalDate
import java.time.LocalTime
import java.time.ZoneId

data class CalEvent(val id: Long, val title: String, val start: Long, val end: Long, val allDay: Boolean, val busy: Boolean = true)

data class FreeWindow(val start: Long, val end: Long) {
    val minutes: Long get() = (end - start) / 60_000
}

object CalendarPlan {
    /**
     * Free windows on `date` between `dayStart` and `dayEnd` (local time), at least `minMinutes`
     * long, after keeping `bufferMinutes` clear around each busy event. All-day and "free"
     * events don't block time. Windows already in the past are trimmed to start at `now`
     * (rounded up to 5 minutes).
     */
    fun freeWindows(
        events: List<CalEvent>, date: LocalDate, zone: ZoneId, now: Long,
        dayStart: LocalTime = LocalTime.of(8, 0), dayEnd: LocalTime = LocalTime.of(22, 0),
        minMinutes: Int = 25, bufferMinutes: Int = 10,
    ): List<FreeWindow> {
        val from0 = date.atTime(dayStart).atZone(zone).toInstant().toEpochMilli()
        val to = date.atTime(dayEnd).atZone(zone).toInstant().toEpochMilli()
        val from = maxOf(from0, roundUp(now, 5))
        if (from >= to) return emptyList()
        val buf = bufferMinutes * 60_000L
        val busy = events.filter { it.busy && !it.allDay && it.end > from - buf && it.start < to + buf }
            .map { (it.start - buf) to (it.end + buf) }.sortedBy { it.first }
        val out = mutableListOf<FreeWindow>()
        var cursor = from
        for ((s, e) in busy) {
            if (s > cursor) out += FreeWindow(cursor, minOf(s, to))
            cursor = maxOf(cursor, e)
            if (cursor >= to) break
        }
        if (cursor < to) out += FreeWindow(cursor, to)
        return out.filter { it.end > it.start && it.minutes >= minMinutes }
    }

    /** A suggested focus length that fits a window: the longest of the usual lengths that fits. */
    fun suggestedMinutes(w: FreeWindow): Int = listOf(90, 60, 50, 45, 40, 30, 25).firstOrNull { it <= w.minutes } ?: w.minutes.toInt()

    /** Busy events that overlap [start, end). */
    fun overlaps(events: List<CalEvent>, start: Long, end: Long): List<CalEvent> =
        events.filter { it.busy && !it.allDay && it.start < end && it.end > start }

    private fun roundUp(t: Long, minutes: Int): Long {
        val step = minutes * 60_000L
        return ((t + step - 1) / step) * step
    }
}
