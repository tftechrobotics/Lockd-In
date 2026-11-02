// Coaching (roadmap step 6, D): question timing and inactivity check-ins. Pure logic.
// Time spent is never treated as understanding or failure, and nothing here ever changes
// blocking: suggestions are offered, the person decides.
package app.lockdin.engine

/** One timed question or task item. `pausedMs` excludes paused time. */
data class QuestionItem(
    val n: Int,
    val startedAt: Long,
    val endedAt: Long? = null,
    val pausedMs: Long = 0,
    val stuck: Boolean = false,
)

data class QuestionLog(
    val items: List<QuestionItem> = emptyList(),
    /** Set while tracking is paused. */
    val pausedAt: Long? = null,
    /** "Add 10 minutes": no long-running suggestion for the current item before this time. */
    val quietUntil: Long? = null,
    val finished: Boolean = false,
) {
    val current: QuestionItem? get() = items.lastOrNull()?.takeIf { it.endedAt == null && !finished }
}

object Questions {
    fun elapsed(item: QuestionItem, log: QuestionLog, now: Long): Long {
        val end = item.endedAt ?: (log.pausedAt ?: now)
        return (end - item.startedAt - item.pausedMs).coerceAtLeast(0)
    }

    fun start(log: QuestionLog, now: Long): QuestionLog =
        if (log.current != null) log else log.copy(items = log.items + QuestionItem(log.items.size + 1, now), pausedAt = null, finished = false, quietUntil = null)

    /** Ends the current item (if any) and starts the next one. */
    fun next(log: QuestionLog, now: Long): QuestionLog = start(endCurrent(resume(log, now), now, stuck = false), now)

    /** Marks the current item for review and moves on. */
    fun stuck(log: QuestionLog, now: Long): QuestionLog = start(endCurrent(resume(log, now), now, stuck = true), now)

    fun pause(log: QuestionLog, now: Long): QuestionLog = if (log.current == null || log.pausedAt != null) log else log.copy(pausedAt = now)

    fun resume(log: QuestionLog, now: Long): QuestionLog {
        val p = log.pausedAt ?: return log
        val cur = log.current ?: return log.copy(pausedAt = null)
        return log.copy(items = log.items.dropLast(1) + cur.copy(pausedMs = cur.pausedMs + (now - p)), pausedAt = null)
    }

    fun finish(log: QuestionLog, now: Long): QuestionLog = endCurrent(resume(log, now), now, stuck = false).copy(finished = true)

    fun addTenMinutes(log: QuestionLog, now: Long): QuestionLog = log.copy(quietUntil = now + 10 * 60_000L)

    private fun endCurrent(log: QuestionLog, now: Long, stuck: Boolean): QuestionLog {
        val cur = log.current ?: return log
        return log.copy(items = log.items.dropLast(1) + cur.copy(endedAt = now, stuck = cur.stuck || stuck))
    }

    /** Durations of finished items (ms). */
    fun durations(log: QuestionLog): List<Long> = log.items.filter { it.endedAt != null }.map { elapsed(it, log, it.endedAt!!) }

    fun median(xs: List<Long>): Long? = xs.sorted().let { if (it.isEmpty()) null else if (it.size % 2 == 1) it[it.size / 2] else (it[it.size / 2 - 1] + it[it.size / 2]) / 2 }

    /**
     * True when the current item has run well past the person's own usual time: at least
     * 3 earlier items to compare with, more than twice their median and at least 5 minutes.
     * Suppressed after "Add 10 minutes" and while paused.
     */
    fun runningLong(log: QuestionLog, recentMs: List<Long>, now: Long): Boolean {
        val cur = log.current ?: return false
        if (log.pausedAt != null) return false
        if (log.quietUntil != null && now < log.quietUntil) return false
        val base = recentMs.takeLast(30)
        if (base.size < 3) return false
        val med = median(base) ?: return false
        val t = elapsed(cur, log, now)
        return t >= 5 * 60_000L && t > 2 * med
    }
}

object CheckIns {
    /** Choices for the idle period; 0 = off. */
    val IDLE_CHOICES = listOf(0, 10, 15, 20, 30)
    /** No check-in in a session's first minutes. */
    const val GRACE_MS = 10 * 60_000L

    /**
     * Whether to ask "Still working?" now: during focus, after `idleMin` without phone
     * interaction, not in the session's first minutes, not within `idleMin` of the last answer,
     * and not while snoozed.
     */
    fun due(
        now: Long, idleMin: Int, inFocus: Boolean, sessionStart: Long, lastInteraction: Long,
        lastAnswerAt: Long?, snoozedUntil: Long?, alreadyAskedAt: Long?,
    ): Boolean {
        if (idleMin <= 0 || !inFocus) return false
        val idle = idleMin * 60_000L
        if (now - sessionStart < GRACE_MS) return false
        if (now - lastInteraction < idle) return false
        if (lastAnswerAt != null && now - lastAnswerAt < idle) return false
        if (snoozedUntil != null && now < snoozedUntil) return false
        // One question per idle stretch: don't ask again until they've interacted or answered.
        if (alreadyAskedAt != null && alreadyAskedAt > lastInteraction) return false
        return true
    }
}
