package app.lockdin

import app.lockdin.engine.Arrival
import app.lockdin.engine.Place
import app.lockdin.engine.Places
import app.lockdin.state.AppState
import app.lockdin.state.StateCodec
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test
import java.time.DayOfWeek
import java.time.LocalTime
import java.time.ZoneId
import java.time.ZonedDateTime

class PlacesTest {
    private val zone = ZoneId.of("America/Chicago")
    private fun at(d: Int, h: Int, m: Int = 0) = ZonedDateTime.of(2026, 11, d, h, m, 0, 0, zone).toInstant().toEpochMilli() // Nov 2 2026 = Monday
    private val lib = Place("p1", "Library", 40.1, -88.2, profileId = "study", from = LocalTime.of(9, 0), to = LocalTime.of(21, 0))

    @Test fun windowsAndDays() {
        assertTrue(Places.inWindow(lib, at(2, 9), zone))
        assertFalse(Places.inWindow(lib, at(2, 21), zone))
        val weekdays = lib.copy(days = setOf(DayOfWeek.MONDAY))
        assertTrue(Places.inWindow(weekdays, at(2, 12), zone))
        assertFalse(Places.inWindow(weekdays, at(3, 12), zone))
        val night = lib.copy(from = LocalTime.of(22, 0), to = LocalTime.of(2, 0), days = setOf(DayOfWeek.MONDAY))
        assertTrue(Places.inWindow(night, at(2, 23), zone))
        assertTrue(Places.inWindow(night, at(3, 1), zone))   // Tuesday 1 AM belongs to Monday's window
        assertFalse(Places.inWindow(night, at(3, 23), zone))
    }

    @Test fun arrivalRemindsOnceADayAndRespectsPauseAndSessions() {
        val now = at(2, 10)
        assertEquals(Arrival.Remind, Places.onArrive(lib, now, zone, paused = false, sessionRunning = false, handled = emptySet()))
        assertEquals(Arrival.Start, Places.onArrive(lib.copy(autoStart = true), now, zone, false, false, emptySet()))
        assertTrue(Places.onArrive(lib, now, zone, paused = true, sessionRunning = false, handled = emptySet()) is Arrival.Skip)
        assertTrue(Places.onArrive(lib, now, zone, false, sessionRunning = true, handled = emptySet()) is Arrival.Skip)
        assertTrue(Places.onArrive(lib.copy(enabled = false), now, zone, false, false, emptySet()) is Arrival.Skip)
        assertTrue(Places.onArrive(lib, at(2, 22), zone, false, false, emptySet()) is Arrival.Skip)
        val fired = setOf(Places.firedKey(lib, now, zone))
        assertTrue(Places.onArrive(lib, at(2, 15), zone, false, false, fired) is Arrival.Skip)
        assertEquals(Arrival.Remind, Places.onArrive(lib, at(3, 10), zone, false, false, fired))
    }

    @Test fun leavingOnlyMattersForTheSessionThePlaceStarted() {
        assertTrue(Places.onLeave("p1", "PLACE:p1@2026-11-02"))
        assertFalse(Places.onLeave("p1", "PLACE:p10@2026-11-02"))
        assertFalse(Places.onLeave("p1", "sched@123"))
        assertFalse(Places.onLeave("p1", null))
    }

    @Test fun placesRoundTripAndBadCoordinatesDrop() {
        val s = AppState(places = listOf(lib.copy(days = setOf(DayOfWeek.MONDAY, DayOfWeek.FRIDAY), autoStart = true, arriveGraceMin = 5)), placesPaused = true)
        assertEquals(s, StateCodec.decode(StateCodec.encode(s)))
        val bad = StateCodec.encode(s).replace("40.1", "140.1")
        assertTrue(StateCodec.decode(bad).places.isEmpty())
    }
}
