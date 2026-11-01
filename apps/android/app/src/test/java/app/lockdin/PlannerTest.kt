package app.lockdin

import app.lockdin.engine.EventKind
import app.lockdin.engine.Planner
import app.lockdin.engine.ReminderSettings
import app.lockdin.engine.Schedule
import app.lockdin.engine.ScheduledSession
import app.lockdin.engine.StartDecision
import app.lockdin.state.AppState
import app.lockdin.state.StateCodec
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test
import java.time.DayOfWeek
import java.time.LocalDate
import java.time.LocalTime
import java.time.ZoneId
import java.time.ZonedDateTime

class PlannerTest {
    private val zone = ZoneId.of("America/Chicago")
    private fun at(y: Int, m: Int, d: Int, h: Int, min: Int) = ZonedDateTime.of(y, m, d, h, min, 0, 0, zone).toInstant().toEpochMilli()
    private val MIN = 60_000L
    // Tuesday 2026-11-03, 19:00, two 25-minute rounds with a 5-minute break (55 min).
    private val weekly = ScheduledSession("p", "Physics review", setOf(DayOfWeek.TUESDAY, DayOfWeek.THURSDAY), LocalTime.of(19, 0), 25, 5, 2, "study",
        autoStart = true, remindMinutes = 10)
    private val settings = ReminderSettings()

    @Test fun eventsForAnOccurrence() {
        val now = at(2026, 11, 3, 12, 0)
        val ev = Planner.events(listOf(weekly), now, zone, settings).filter { it.at > now }.take(3)
        assertEquals(listOf(EventKind.REMIND, EventKind.START, EventKind.MISSED_CHECK), ev.map { it.kind })
        assertEquals(at(2026, 11, 3, 18, 50), ev[0].at)
        assertEquals(at(2026, 11, 3, 19, 0), ev[1].at)
        assertEquals(at(2026, 11, 3, 19, 55) + Planner.MISSED_DELAY_MS, ev[2].at)
    }

    @Test fun remindersAndFollowUpsCanBeTurnedOff() {
        val now = at(2026, 11, 3, 12, 0)
        val off = settings.copy(remindersOn = false, missedFollowUpOn = false)
        assertTrue(Planner.events(listOf(weekly), now, zone, off).all { it.kind == EventKind.START })
        assertTrue(Planner.events(listOf(weekly.copy(remindMinutes = 0)), now, zone, settings).none { it.kind == EventKind.REMIND })
    }

    @Test fun dueEventsHappenOnceAndNextWakeMovesOn() {
        val now = at(2026, 11, 3, 19, 0)
        val ev = Planner.events(listOf(weekly), now, zone, settings)
        val due = Planner.due(ev, now, emptySet())
        assertEquals(setOf(EventKind.REMIND, EventKind.START), due.filter { it.occ.start == now }.map { it.kind }.toSet())
        val handled = due.map { it.id }.toSet()
        assertTrue(Planner.due(ev, now, handled).isEmpty())
        assertEquals(at(2026, 11, 3, 19, 55) + Planner.MISSED_DELAY_MS, Planner.nextWake(ev, now, handled))
    }

    @Test fun staleEventsAreNotActedOn() {
        val now = at(2026, 11, 4, 12, 0) // 17 h after Tuesday's start
        val due = Planner.due(Planner.events(listOf(weekly), now, zone, settings), now, emptySet())
        assertTrue(due.none { it.kind == EventKind.START })
    }

    @Test fun skippingRemovesOnlyThatOccurrence() {
        val now = at(2026, 11, 3, 12, 0)
        val s = weekly.copy(skipped = setOf(LocalDate.of(2026, 11, 3)))
        val next = Schedule.nextUp(listOf(s), now, zone)!!
        assertEquals(at(2026, 11, 5, 19, 0), next.start)
        assertTrue(Planner.events(listOf(s), now, zone, settings).none { it.occ.start == at(2026, 11, 3, 19, 0) })
    }

    @Test fun startDecisions() {
        val o = Schedule.nextUp(listOf(weekly), at(2026, 11, 3, 12, 0), zone)!!
        assertEquals(StartDecision.Start(o.start, late = false), Planner.decideStart(o, o.start, false))
        assertEquals(StartDecision.AlreadyRunning, Planner.decideStart(o, o.start, true))
        // Phone was off: 35 min late, still in round 2 → join in progress.
        assertEquals(StartDecision.Start(o.start, late = true), Planner.decideStart(o, o.start + 35 * MIN, false))
        // Less than a minute of focus left, or after the end → missed.
        assertEquals(StartDecision.Missed, Planner.decideStart(o, o.end - 30_000, false))
        assertEquals(StartDecision.Missed, Planner.decideStart(o, o.end + MIN, false))
        // During the break with round 2 ahead → start (it joins at the break).
        assertTrue(Planner.decideStart(o, o.start + 27 * MIN, false) is StartDecision.Start)
    }

    @Test fun missedOnlyWithoutASessionStartedForIt() {
        val o = Schedule.nextUp(listOf(weekly), at(2026, 11, 3, 12, 0), zone)!!
        assertTrue(Planner.wasMissed(o, emptyList()))
        assertFalse(Planner.wasMissed(o, listOf(o.start + 5 * MIN)))
        assertFalse(Planner.wasMissed(o, listOf(o.start - 8 * MIN))) // started a little early
        assertTrue(Planner.wasMissed(o, listOf(o.start - 30 * MIN)))
    }

    @Test fun daylightSavingKeepsWallClockTime() {
        // US DST ends Sunday 2026-11-01. A daily 19:00 session is at 19:00 local on both sides.
        val daily = weekly.copy(days = DayOfWeek.entries.toSet())
        val before = Planner.occurrences(listOf(daily), at(2026, 10, 31, 12, 0), at(2026, 11, 2, 23, 0), zone)
        val hours = before.map { java.time.Instant.ofEpochMilli(it.start).atZone(zone).hour }.toSet()
        assertEquals(setOf(19), hours)
        assertEquals(3, before.size)
    }

    @Test fun quietHoursWrapMidnight() {
        val q = settings.copy(quietStart = LocalTime.of(22, 0), quietEnd = LocalTime.of(7, 0))
        assertTrue(Planner.inQuietHours(at(2026, 11, 3, 23, 30), zone, q))
        assertTrue(Planner.inQuietHours(at(2026, 11, 3, 6, 59), zone, q))
        assertFalse(Planner.inQuietHours(at(2026, 11, 3, 7, 0), zone, q))
        assertFalse(Planner.inQuietHours(at(2026, 11, 3, 12, 0), zone, settings))
        assertEquals(at(2026, 11, 4, 7, 0), Planner.quietEndsAt(at(2026, 11, 3, 23, 30), zone, q))
        assertNull(Planner.quietEndsAt(at(2026, 11, 3, 12, 0), zone, q))
    }

    @Test fun rateLimit() {
        val now = at(2026, 11, 3, 12, 0)
        val sent = listOf(now - 70 * MIN, now - 50 * MIN, now - 20 * MIN, now - 10 * MIN, now - MIN)
        assertFalse(Planner.underRateLimit(sent, now, settings)) // 4 in the last hour
        assertTrue(Planner.underRateLimit(sent.drop(2), now, settings))
    }

    @Test fun scheduleFieldsAndLogsRoundTrip() {
        val s = AppState(
            schedule = listOf(weekly.copy(skipped = setOf(LocalDate.of(2026, 11, 3)), remindMinutes = 15)),
            reminders = settings.copy(quietStart = LocalTime.of(22, 0), quietEnd = LocalTime.of(7, 0), maxPerHour = 6),
            planLog = listOf("START:p@1"), recentStarts = listOf(5L), notifyLog = listOf(6L),
        )
        assertEquals(s, StateCodec.decode(StateCodec.encode(s)))
        // Older saves without the new fields still load, with defaults.
        val o = org.json.JSONObject(StateCodec.encode(AppState(schedule = listOf(weekly))))
        o.getJSONArray("schedule").getJSONObject(0).apply { remove("autoStart"); remove("remindMinutes"); remove("skipped") }
        o.remove("reminders"); o.remove("planLog")
        val old = StateCodec.decode(o.toString())
        assertFalse(old.schedule.single().autoStart)
        assertEquals(10, old.schedule.single().remindMinutes)
        assertEquals(ReminderSettings(), old.reminders)
    }
}
