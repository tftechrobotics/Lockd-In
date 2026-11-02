package app.lockdin

import app.lockdin.engine.History
import app.lockdin.engine.HistoryEntry
import app.lockdin.engine.OverrideRecord
import app.lockdin.engine.OverrideScope
import app.lockdin.engine.Outcome
import app.lockdin.engine.ScheduledSession
import app.lockdin.engine.Session
import app.lockdin.engine.SessionControls
import app.lockdin.state.AppState
import app.lockdin.state.HistoryCodec
import app.lockdin.state.LastChoice
import app.lockdin.state.Preset
import app.lockdin.state.StateCodec
import app.lockdin.state.Store
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test
import java.time.LocalDate
import java.time.LocalTime
import java.time.ZoneId
import java.time.ZonedDateTime

class HistoryTest {
    private val zone = ZoneId.of("America/Chicago")
    private val t0 = ZonedDateTime.of(2026, 11, 2, 9, 0, 0, 0, zone).toInstant().toEpochMilli() // Monday
    private val min = 60_000L
    private val plan = Session.roundsPlan(2, 25, 5) // 25 focus, 5 break, 25 focus

    private fun entry(controls: SessionControls, now: Long) =
        History.entryFor("s", t0, plan, controls, now, "Read", "study", "Study", null, mapOf("com.x" to 2))

    @Test fun completedCountsOnlyFocusTime() {
        val e = entry(SessionControls(), t0 + 60 * min)
        assertEquals(Outcome.COMPLETED, e.outcome)
        assertEquals(t0 + 55 * min, e.end)
        assertEquals(50L, e.focusedMinutes)
        assertEquals(2, e.roundsDone); assertEquals(2, e.roundsPlanned)
    }

    @Test fun emergencyEndsAtRecovery() {
        val e = entry(SessionControls(emergencyRecoveryAt = t0 + 10 * min), t0 + 20 * min)
        assertEquals(Outcome.EMERGENCY, e.outcome)
        assertEquals(10L, e.focusedMinutes)
        assertEquals(0, e.roundsDone)
    }

    @Test fun unlockForRestEndsEarly() {
        val o = OverrideRecord("o", OverrideScope.REST_OF_SESSION, 1, t0 + 28 * min, 90, null)
        val e = entry(SessionControls(listOf(o)), t0 + 40 * min)
        assertEquals(Outcome.ENDED_EARLY, e.outcome)
        assertEquals(25L, e.focusedMinutes) // first round only; the unlock took effect in the break
    }

    @Test fun leftWhileRunningCountsAsStopped() {
        val e = entry(SessionControls(), t0 + 12 * min)
        assertEquals(Outcome.STOPPED, e.outcome)
        assertEquals(12L, e.focusedMinutes)
    }

    private fun simple(id: String, start: Long, focusMin: Long, outcome: Outcome = Outcome.COMPLETED, key: String? = null) =
        HistoryEntry(id, start, start + focusMin * min, start + focusMin * min, focusMin * min, focusMin * min, 1, 1, null, "study", "Study", outcome, key)

    @Test fun retentionAndReplace() {
        val now = t0
        var list = listOf(simple("old", now - 100L * 86_400_000, 20), simple("new", now - 86_400_000, 30))
        list = History.add(list, simple("new", now - 86_400_000, 35), 90, now)
        assertEquals(listOf("new"), list.map { it.id })
        assertEquals(35L, list.single().focusedMinutes)
        assertEquals(2, History.prune(listOf(simple("a", now - 400L * 86_400_000, 1), simple("b", now, 1)), 0, now).size)
    }

    @Test fun weekTotalsAndPlannedVsDone() {
        val sched = listOf(ScheduledSession("w", "Gym", emptySet(), LocalTime.of(9, 0), 25, 1, 1, "study", date = LocalDate.of(2026, 11, 3)))
        val tue = ZonedDateTime.of(2026, 11, 3, 9, 0, 0, 0, zone).toInstant().toEpochMilli()
        val key = app.lockdin.engine.Planner.key(app.lockdin.engine.Planner.occurrences(sched, tue - 1, tue + 1, zone).single())
        val entries = listOf(simple("a", t0, 40), simple("b", tue, 25, key = key), simple("c", tue + 3_600_000, 10, Outcome.EMERGENCY))
        val w = History.week(entries, LocalDate.of(2026, 11, 2), zone, sched, tue + 7_200_000)
        assertEquals(75L, w.focusedMinutes)
        assertEquals(3, w.sessions); assertEquals(2, w.completed)
        assertEquals(1, w.planned); assertEquals(1, w.plannedDone)
        assertEquals(listOf(40L, 35L, 0L, 0L, 0L, 0L, 0L), w.days.map { it.focusedMinutes })
        assertEquals(LocalDate.of(2026, 11, 2), History.weekStart(LocalDate.of(2026, 11, 8)))
    }

    @Test fun csvQuotesText() {
        val e = simple("a", t0, 25).copy(task = "Read \"Ch 1\", notes")
        val csv = History.csv(listOf(e), zone)
        assertTrue(csv.startsWith("start,end,"))
        assertTrue(csv.contains("\"Read \"\"Ch 1\"\", notes\""))
        assertTrue(csv.contains("2026-11-02T09:00"))
    }

    @Test fun historyCodecRoundTripsAndSkipsDamage() {
        val list = listOf(simple("a", t0, 25).copy(blocked = mapOf("site:youtube.com" to 3), task = "Read"), simple("b", t0 - 10 * min, 5, Outcome.STOPPED))
        assertEquals(list, HistoryCodec.decode(HistoryCodec.encode(list)))
        val raw = """{"version":1,"entries":[{"id":"x","start":10,"end":5},{"id":"y","start":1,"end":2,"focusedMs":1,"outcome":"NOPE"}]}"""
        val d = HistoryCodec.decode(raw)
        assertEquals(listOf("y"), d.map { it.id })
        assertEquals(Outcome.STOPPED, d.single().outcome)
        assertTrue(HistoryCodec.decode("""{"version":9,"entries":[]}""").isEmpty())
        assertTrue(HistoryCodec.decode("not json").isEmpty())
    }

    @Test fun presetsRoundTripAndBadOnesDrop() {
        val s = AppState(presets = listOf(Preset("p", "Essay", 40, 10, 2, "study", "Essay", LocalDate.of(2026, 11, 20), listOf("Outline"))), historyDays = 90)
        assertEquals(s, StateCodec.decode(StateCodec.encode(s)))
        val raw = StateCodec.encode(s).replace("\"historyDays\":90", "\"historyDays\":7")
        assertEquals(365, StateCodec.decode(raw).historyDays)
    }

    @Test fun storeReportsEachSessionAsItIsLeft() {
        val left = mutableListOf<String>()
        val store = Store(AppState()) {}
        store.onLeft = { a, _, _ -> left += a.id }
        store.startSession(t0, plan, null, "study", LastChoice(25, 5, 2, "study"))
        val first = store.state.active!!.id
        store.startSession(t0 + min, plan, null, "study", LastChoice(25, 5, 2, "study"), presetId = "p")
        assertEquals("p", store.state.active!!.presetId)
        store.finish()
        assertEquals(2, left.size); assertEquals(first, left[0])
        store.clearAll()
        assertEquals(2, left.size)
    }
}
