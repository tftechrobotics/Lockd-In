package app.lockdin.enforce

/**
 * In an unlocked session (R62) nothing is covered, so the usual "block screen is showing" guard can't
 * tell one opening from the next. Both enforcement paths call this to note each opening once: the
 * same app or site again within [WINDOW_MS] counts as the same opening.
 */
object UnlockedNotes {
    const val WINDOW_MS = 5 * 60_000L
    private var last: Pair<String, Long>? = null

    @Synchronized
    fun shouldNote(key: String, elapsed: Long): Boolean {
        val l = last
        if (l != null && l.first == key && elapsed - l.second < WINDOW_MS) { last = key to elapsed; return false }
        last = key to elapsed
        return true
    }
}
