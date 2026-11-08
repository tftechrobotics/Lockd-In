package app.lockdin.engine

import java.time.LocalDate
import java.time.ZoneId

/**
 * Focus streak (R62, owner 2026-11-08): consecutive days on which every session went clean.
 *
 * A day is **clean** when at least one session on it finished as planned and no session on it ended
 * early (including emergency recovery) or had a blocked app or site come up. A day is **broken** when
 * any session on it ended early or had a blocked attempt. Days with no sessions (or only sessions that
 * were stopped some other way) **pause** the streak: they neither add to it nor break it.
 *
 * Unlocked sessions (nothing blocked, attempts only noted) count the same way: opening a blocked
 * app during one breaks the streak just like a blocked attempt would.
 *
 * This measures consistency in Lockd In sessions; it is not proof of a habit, and browser tabs only
 * count where Lockd In already watches websites.
 */
object Streak {
    /** Days of streak before History offers an unlocked session. */
    const val GOAL = 30

    enum class Day { CLEAN, BROKEN, PAUSE }

    data class Result(
        val days: Int,
        val best: Int,
        /** The most recent day that reset the streak, if any. */
        val lastBreak: LocalDate?,
        val lastBreakReason: String?,
        /** Clean unlocked sessions so far (the self-control part). */
        val unlockedClean: Int,
    ) {
        val reachedGoal get() = days >= GOAL
        val progress get() = (days.toFloat() / GOAL).coerceIn(0f, 1f)
    }

    fun slipped(e: HistoryEntry): Boolean = e.outcome == Outcome.ENDED_EARLY || e.outcome == Outcome.EMERGENCY || e.blocked.isNotEmpty()

    fun classify(entries: List<HistoryEntry>): Day = when {
        entries.any(::slipped) -> Day.BROKEN
        entries.any { it.outcome == Outcome.COMPLETED } -> Day.CLEAN
        else -> Day.PAUSE
    }

    private fun reason(entries: List<HistoryEntry>): String = when {
        entries.any { it.blocked.isNotEmpty() && it.unlocked } -> "a blocked app was opened in an unlocked session"
        entries.any { it.blocked.isNotEmpty() } -> "a blocked app came up"
        entries.any { it.outcome == Outcome.EMERGENCY } -> "a session ended with emergency recovery"
        else -> "a session ended early"
    }

    fun compute(history: List<HistoryEntry>, zone: ZoneId): Result {
        val byDay = history.groupBy { History.dateOf(it.start, zone) }.toSortedMap()
        var run = 0; var best = 0
        var lastBreak: LocalDate? = null; var why: String? = null
        for ((day, list) in byDay) when (classify(list)) {
            Day.CLEAN -> { run += 1; best = maxOf(best, run) }
            Day.BROKEN -> { run = 0; lastBreak = day; why = reason(list) }
            Day.PAUSE -> Unit
        }
        val unlockedClean = history.count { it.unlocked && it.outcome == Outcome.COMPLETED && it.blocked.isEmpty() }
        return Result(run, best, lastBreak, why, unlockedClean)
    }
}
