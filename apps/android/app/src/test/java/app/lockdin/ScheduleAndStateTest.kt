package app.lockdin

import app.lockdin.engine.OverrideRecord
import app.lockdin.engine.OverrideScope
import app.lockdin.engine.Schedule
import app.lockdin.engine.ScheduledSession
import app.lockdin.engine.Session
import app.lockdin.engine.SessionControls
import app.lockdin.state.ActiveSession
import app.lockdin.state.AppState
import app.lockdin.state.StateCodec
import app.lockdin.state.ThemeChoice
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test
import java.time.DayOfWeek.FRIDAY
import java.time.DayOfWeek.MONDAY
import java.time.DayOfWeek.SATURDAY
import java.time.DayOfWeek.THURSDAY
import java.time.DayOfWeek.TUESDAY
import java.time.DayOfWeek.WEDNESDAY
import java.time.LocalDate
import java.time.LocalTime
import java.time.ZoneId
import java.time.ZonedDateTime

class ScheduleAndStateTest {
    private val chicago = ZoneId.of("America/Chicago")
    private fun mk(id: String = "x", start: String = "18:00", days: Set<java.time.DayOfWeek> = setOf(MONDAY, TUESDAY, WEDNESDAY, THURSDAY), rounds: Int = 2) =
        ScheduledSession(id, "Study", days, LocalTime.parse(start), 40, 10, rounds, "study")
    private fun at(y: Int, m: Int, d: Int, h: Int, min: Int) = ZonedDateTime.of(y, m, d, h, min, 0, 0, chicago).toInstant().toEpochMilli()

    @Test fun totals() {
        assertEquals(90, mk().totalMinutes)
        assertEquals(40, mk(rounds = 1).totalMinutes)
    }

    @Test fun nextUpTodayThenLaterDays() {
        val now = at(2026, 11, 3, 17, 0) // Tuesday
        assertEquals(at(2026, 11, 3, 18, 0), Schedule.nextUp(listOf(mk()), now, chicago)!!.start)
        assertEquals(at(2026, 11, 6, 18, 0), Schedule.nextUp(listOf(mk(days = setOf(FRIDAY))), now, chicago)!!.start)
        assertEquals(at(2026, 11, 3, 18, 0), Schedule.nextUp(listOf(mk()), at(2026, 11, 3, 18, 30), chicago)!!.start) // in progress
        assertNull(Schedule.nextUp(emptyList(), now, chicago))
    }

    @Test fun overlapRules() {
        val a = mk("a")
        assertEquals(1, Schedule.overlaps(a, listOf(mk("b", "19:00"))).size)
        assertEquals(0, Schedule.overlaps(a, listOf(mk("b", "19:30"))).size) // adjacent
        assertEquals(0, Schedule.overlaps(a, listOf(mk("b", "18:30", setOf(SATURDAY)))).size)
        assertEquals(1, Schedule.overlaps(mk("c", "23:30", setOf(MONDAY)), listOf(mk("d", "00:15", setOf(TUESDAY)))).size)
    }

    @Test fun dstMatchesCoreRule() {
        val gap = mk(start = "02:30")
        assertEquals(at(2027, 3, 14, 3, 30), Schedule.startOn(gap, LocalDate.of(2027, 3, 14), chicago)) // shifted later
        val repeat = mk(start = "01:30")
        val first = java.time.Instant.parse("2026-11-01T06:30:00Z").toEpochMilli() // 01:30 CDT, the earlier one
        assertEquals(first, Schedule.startOn(repeat, LocalDate.of(2026, 11, 1), chicago))
    }

    @Test fun stateRoundTripsAndRejectsGarbage() {
        val s = AppState(
            theme = ThemeChoice.DARK,
            schedule = listOf(mk()),
            active = ActiveSession("a", 1_000L, Session.roundsPlan(2, 25, 5), "TAM 212", "study",
                SessionControls(listOf(OverrideRecord("o", OverrideScope.CURRENT_FOCUS_PERIOD, 0, 2_000L, 90, 3_000L)), null)),
            recentIntentions = listOf("TAM 212"),
        )
        assertEquals(s, StateCodec.decode(StateCodec.encode(s)))
        assertEquals(AppState(), StateCodec.decode("not json"))
        assertEquals(AppState(), StateCodec.decode("""{"version":99}"""))
        assertEquals(AppState(), StateCodec.decode(null))
        val bad = StateCodec.decode("""{"version":1,"schedule":[{"id":"x","days":[9]}],"active":{"start":"soon"},"theme":"NEON"}""")
        assertTrue(bad.schedule.isEmpty())
        assertNull(bad.active)
        assertEquals(ThemeChoice.SYSTEM, bad.theme)
    }
}
