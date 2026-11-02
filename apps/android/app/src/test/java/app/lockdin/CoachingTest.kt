package app.lockdin

import app.lockdin.engine.CheckIns
import app.lockdin.engine.QuestionLog
import app.lockdin.engine.Questions
import app.lockdin.engine.Session
import app.lockdin.state.AppState
import app.lockdin.state.CheckInState
import app.lockdin.state.LastChoice
import app.lockdin.state.StateCodec
import app.lockdin.state.Store
import app.lockdin.state.Tone
import app.lockdin.ui.Moment
import app.lockdin.ui.message
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test

class CoachingTest {
    private val m = 60_000L

    @Test fun questionsTimeItemsExcludingPauses() {
        var q = Questions.start(QuestionLog(), 0)
        q = Questions.pause(q, 2 * m)
        q = Questions.resume(q, 5 * m)          // 3 min paused
        q = Questions.next(q, 6 * m)            // Q1 = 3 min of work
        q = Questions.stuck(q, 10 * m)          // Q2 = 4 min, for review
        q = Questions.finish(q, 11 * m)         // Q3 = 1 min
        assertEquals(listOf(3 * m, 4 * m, 1 * m), Questions.durations(q))
        assertEquals(listOf(false, true, false), q.items.map { it.stuck })
        assertNull(q.current)
        assertEquals(3 * m, Questions.median(Questions.durations(q)))
        // A paused item's clock stops.
        val p = Questions.pause(Questions.start(QuestionLog(), 0), m)
        assertEquals(m, Questions.elapsed(p.current!!, p, 9 * m))
    }

    @Test fun longRunningNoteNeedsHistoryAndRespectsTenMoreMinutes() {
        val usual = listOf(3 * m, 4 * m, 3 * m)
        val q = Questions.start(QuestionLog(), 0)
        assertFalse(Questions.runningLong(q, usual.take(2), 20 * m))     // not enough to compare
        assertFalse(Questions.runningLong(q, usual, 4 * m))              // under 5 minutes
        assertFalse(Questions.runningLong(q, usual, 6 * m))              // 6 < 2 × 3
        assertTrue(Questions.runningLong(q, usual, 7 * m))
        val quiet = Questions.addTenMinutes(q, 7 * m)
        assertFalse(Questions.runningLong(quiet, usual, 15 * m))
        assertTrue(Questions.runningLong(quiet, usual, 18 * m))
        assertFalse(Questions.runningLong(Questions.pause(q, 7 * m), usual, 9 * m))
    }

    @Test fun checkInsAreRareAndQuiet() {
        val start = 0L
        fun due(now: Long, last: Long, answered: Long? = null, snoozed: Long? = null, asked: Long? = null, idle: Int = 15, focus: Boolean = true) =
            CheckIns.due(now, idle, focus, start, last, answered, snoozed, asked)
        assertFalse(due(30 * m, 10 * m, idle = 0))          // off
        assertFalse(due(9 * m, 0))                          // first 10 minutes
        assertFalse(due(20 * m, 10 * m))                    // only 10 idle minutes
        assertTrue(due(26 * m, 10 * m))
        assertFalse(due(26 * m, 10 * m, focus = false))     // breaks
        assertFalse(due(26 * m, 10 * m, answered = 20 * m)) // answered recently
        assertFalse(due(26 * m, 10 * m, snoozed = 30 * m))
        assertFalse(due(40 * m, 10 * m, asked = 26 * m))    // once per idle stretch
        assertTrue(due(60 * m, 44 * m, asked = 26 * m))     // they came back, then idled again
    }

    @Test fun customMessagesArePerSessionAndFallBackToCalm() {
        val lines = listOf("A", "B", "C")
        val one = message(Tone.CUSTOM, Moment.FOCUS_START, custom = lines, seed = "s1")
        assertEquals(one, message(Tone.CUSTOM, Moment.FOCUS_START, custom = lines, seed = "s1"))
        assertTrue(one in lines)
        assertEquals(message(Tone.CALM, Moment.FOCUS_START), message(Tone.CUSTOM, Moment.FOCUS_START))
        assertEquals(message(Tone.CALM, Moment.DONE), message(Tone.CUSTOM, Moment.DONE, custom = lines))
    }

    @Test fun coachingStateRoundTripsAndQuestionTimesAreKept() {
        val s = AppState(
            tone = Tone.CUSTOM, customMessages = listOf("Go"), appMessages = mapOf("com.instagram.android" to "Later."),
            questionTimes = listOf(m, 2 * m), checkInMinutes = 15,
        )
        assertEquals(s, StateCodec.decode(StateCodec.encode(s)))
        assertEquals(0, StateCodec.decode(StateCodec.encode(s).replace("\"checkInMinutes\":15", "\"checkInMinutes\":7")).checkInMinutes)
        val store = Store(s) {}
        store.startSession(1_793_300_000_000, Session.roundsPlan(1, 25, 5), null, "study", LastChoice(25, 5, 1, "study"))
        val id = store.state.active!!.id
        store.setQuestions(id) { Questions.next(Questions.start(it, 1_793_300_000_000), 1_793_300_000_000 + 3 * m) }
        store.setCheckIn(id) { CheckInState(askedAt = 5, answers = mapOf("paper" to 1)) }
        assertEquals(store.state, StateCodec.decode(StateCodec.encode(store.state)))
        store.finish()
        assertEquals(listOf(m, 2 * m, 3 * m), store.state.questionTimes)
    }
}
