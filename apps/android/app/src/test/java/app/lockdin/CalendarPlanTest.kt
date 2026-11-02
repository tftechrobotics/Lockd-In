package app.lockdin

import app.lockdin.engine.CalEvent
import app.lockdin.engine.CalendarPlan
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test
import java.time.LocalDate
import java.time.LocalTime
import java.time.ZoneId
import java.time.ZonedDateTime

class CalendarPlanTest {
    private val zone = ZoneId.of("America/Chicago")
    private val day = LocalDate.of(2026, 11, 3)
    private fun at(h: Int, m: Int = 0) = ZonedDateTime.of(day, LocalTime.of(h, m), zone).toInstant().toEpochMilli()
    private val early = at(6)

    @Test fun windowsAvoidBusyEventsWithBuffers() {
        val events = listOf(
            CalEvent(1, "Class", at(9), at(10, 30), false),
            CalEvent(2, "Lunch", at(12), at(13), false),
            CalEvent(3, "Holiday", at(0), at(23, 59), allDay = true),
            CalEvent(4, "Tentative", at(15), at(16), false, busy = false),
        )
        val w = CalendarPlan.freeWindows(events, day, zone, early)
        // 8:00–8:50, 10:40–11:50, 13:10–22:00 (the all-day and free events don't block).
        assertEquals(listOf(at(8) to at(8, 50), at(10, 40) to at(11, 50), at(13, 10) to at(22)), w.map { it.start to it.end })
        assertEquals(90, CalendarPlan.suggestedMinutes(w.last()))
        assertEquals(50, CalendarPlan.suggestedMinutes(w.first()))
    }

    @Test fun shortGapsAndThePastAreLeftOut() {
        val events = listOf(CalEvent(1, "A", at(8), at(9), false), CalEvent(2, "B", at(9, 30), at(21, 50), false))
        assertTrue(CalendarPlan.freeWindows(events, day, zone, early).isEmpty()) // 9:10–9:20 is too short
        val later = CalendarPlan.freeWindows(emptyList(), day, zone, at(20, 51))
        assertEquals(at(20, 55), later.single().start) // rounded up to 5 minutes
        assertTrue(CalendarPlan.freeWindows(emptyList(), day, zone, at(21, 40)).isEmpty())
    }

    @Test fun overlapsIgnoreFreeAndAllDay() {
        val events = listOf(CalEvent(1, "Lab", at(14), at(15), false), CalEvent(2, "Trip", at(0), at(23), true), CalEvent(3, "Maybe", at(14), at(15), false, busy = false))
        assertEquals(listOf("Lab"), CalendarPlan.overlaps(events, at(14, 30), at(16)).map { it.title })
        assertTrue(CalendarPlan.overlaps(events, at(15), at(16)).isEmpty())
    }
}
