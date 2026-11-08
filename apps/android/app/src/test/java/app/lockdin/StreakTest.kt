package app.lockdin

import app.lockdin.engine.HistoryEntry
import app.lockdin.engine.Outcome
import app.lockdin.engine.Streak
import app.lockdin.enforce.UnlockedNotes
import app.lockdin.state.HistoryCodec
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test
import java.time.LocalDate
import java.time.ZoneId
import java.time.ZonedDateTime

class StreakTest {
    private val zone = ZoneId.of("America/Chicago")
    private fun at(day: Int, hour: Int = 19) = ZonedDateTime.of(2026, 10, day, hour, 0, 0, 0, zone).toInstant().toEpochMilli()
    private var n = 0
    private fun e(day: Int, outcome: Outcome = Outcome.COMPLETED, blocked: Map<String, Int> = emptyMap(), unlocked: Boolean = false, hour: Int = 19) =
        HistoryEntry("e${n++}", at(day, hour), at(day, hour) + 1_500_000, at(day, hour) + 1_500_000, 1_500_000, 1_500_000, 1, 1,
            null, "p", "Study", outcome, blocked = blocked, unlocked = unlocked)

    @Test fun emptyHistoryIsZero() {
        val r = Streak.compute(emptyList(), zone)
        assertEquals(0, r.days); assertNull(r.lastBreak); assertFalse(r.reachedGoal)
    }

    @Test fun cleanDaysAddUpAndDaysOffPause() {
        // 1, 2, (3 off), 4 clean -> 3 days
        val r = Streak.compute(listOf(e(1), e(2), e(4)), zone)
        assertEquals(3, r.days)
    }

    @Test fun blockedAttemptResetsTheStreak() {
        val r = Streak.compute(listOf(e(1), e(2), e(3, blocked = mapOf("com.x" to 1)), e(4)), zone)
        assertEquals(1, r.days); assertEquals(2, r.best)
        assertEquals(LocalDate.of(2026, 10, 3), r.lastBreak)
    }

    @Test fun endingEarlyOrEmergencyResets() {
        assertEquals(0, Streak.compute(listOf(e(1), e(2, Outcome.ENDED_EARLY)), zone).days)
        assertEquals(0, Streak.compute(listOf(e(1), e(2, Outcome.EMERGENCY)), zone).days)
    }

    @Test fun oneSlipSpoilsTheWholeDay() {
        val r = Streak.compute(listOf(e(1, hour = 9), e(1, Outcome.ENDED_EARLY, hour = 20)), zone)
        assertEquals(0, r.days)
    }

    @Test fun stoppedOnlyDayPauses() {
        assertEquals(2, Streak.compute(listOf(e(1), e(2, Outcome.STOPPED), e(3)), zone).days)
    }

    @Test fun goalAtThirtyDays() {
        val r = Streak.compute((1..30).map { e(it) }, zone)
        assertEquals(30, r.days); assertTrue(r.reachedGoal); assertEquals(1f, r.progress)
    }

    @Test fun unlockedSessionsCountAndSlipsReset() {
        val r = Streak.compute(listOf(e(1), e(2, unlocked = true), e(3, unlocked = true, blocked = mapOf("com.x" to 1))), zone)
        assertEquals(0, r.days); assertEquals(1, r.unlockedClean)
        assertTrue(r.lastBreakReason!!.contains("unlocked"))
    }

    @Test fun unlockedFlagSurvivesTheHistoryCodec() {
        val back = HistoryCodec.decode(HistoryCodec.encode(listOf(e(1, unlocked = true))))
        assertTrue(back.single().unlocked)
    }

    @Test fun unlockedNotesCountEachOpeningOnce() {
        assertTrue(UnlockedNotes.shouldNote("com.a", 1_000_000))
        assertFalse(UnlockedNotes.shouldNote("com.a", 1_030_000))
        assertTrue(UnlockedNotes.shouldNote("com.b", 1_040_000))
        assertTrue(UnlockedNotes.shouldNote("com.a", 1_050_000))
        assertTrue(UnlockedNotes.shouldNote("com.a", 1_050_000 + UnlockedNotes.WINDOW_MS + 1))
    }
}
