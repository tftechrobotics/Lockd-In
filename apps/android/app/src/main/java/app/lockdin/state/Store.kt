// Holds AppState for the UI and persists every change to the app's private
// SharedPreferences (excluded from backup; see AndroidManifest).
package app.lockdin.state

import android.content.Context
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.setValue
import app.lockdin.engine.Segment
import app.lockdin.engine.SessionControls
import java.util.UUID

/** Time source; tests substitute a controllable clock. */
fun interface AppClock {
    fun now(): Long

    /** False for test clocks: the UI then re-reads time only when the clock's state changes. */
    val ticks: Boolean get() = true

    companion object {
        val System = AppClock { java.lang.System.currentTimeMillis() }
    }
}

class Store(initial: AppState, private val persist: (String) -> Unit) {
    var state by mutableStateOf(initial)
        private set

    /** Called after every change (main thread). The app uses it to start the blocking service. */
    var onChange: ((AppState) -> Unit)? = null

    fun update(fn: (AppState) -> AppState) {
        state = fn(state)
        persist(StateCodec.encode(state))
        onChange?.invoke(state)
    }

    /**
     * Called with each session as it is left (finished, replaced or cleared) and the time it was
     * left, before it disappears from the state. The app records it in history.
     */
    var onLeft: ((ActiveSession, AppState, Long) -> Unit)? = null

    /** Time source for history entries; tests substitute their clock. */
    var clock: () -> Long = { java.lang.System.currentTimeMillis() }

    private fun leaving(s: AppState) { s.active?.let { a -> runCatching { onLeft?.invoke(a, s, clock()) } } }

    fun startSession(
        now: Long, segments: List<Segment>, intention: String?, profileId: String, last: LastChoice,
        occurrenceKey: String? = null, presetId: String? = null,
    ) = update { s0 ->
        leaving(s0)
        val s = keepQuestionTimes(s0)
        val text = intention?.trim()?.take(120)?.takeIf { it.isNotEmpty() }
        s.copy(
            last = last,
            active = ActiveSession(
                newId(), now, segments, text, profileId, SessionControls(),
                rules = sessionRules(s.profiles.firstOrNull { it.id == profileId }),
                audio = s.profiles.firstOrNull { it.id == profileId }?.audio ?: ProfileAudio(),
                commitment = s.commitment,
                occurrenceKey = occurrenceKey,
                presetId = presetId,
            ),
            recentStarts = (s.recentStarts + now).takeLast(40),
            lastSession = null,
            recentIntentions = if (text == null) s.recentIntentions else (listOf(text) + s.recentIntentions.filter { it != text }).take(8),
        )
    }

    fun setControls(sessionId: String, fn: (SessionControls) -> SessionControls) = update { s ->
        val a = s.active
        if (a == null || a.id != sessionId) s else s.copy(active = a.copy(controls = fn(a.controls)))
    }

    /**
     * Leaves the finished session. `dismissed` = the person tapped Done; otherwise (the recap
     * timed out) Home keeps a "Last session" card until they dismiss it.
     */
    fun finish(dismissed: Boolean = true) = update { leaving(it); keepQuestionTimes(it).copy(active = null, lastSession = if (dismissed) null else it.active) }

    /** Saves a leaving session's question durations for later "longer than usual" comparisons. */
    private fun keepQuestionTimes(s: AppState): AppState {
        val d = s.active?.let { app.lockdin.engine.Questions.durations(it.questions) } ?: return s
        return if (d.isEmpty()) s else s.copy(questionTimes = (s.questionTimes + d).takeLast(50))
    }

    fun setQuestions(sessionId: String, fn: (app.lockdin.engine.QuestionLog) -> app.lockdin.engine.QuestionLog) = update { s ->
        val a = s.active
        if (a == null || a.id != sessionId) s else s.copy(active = a.copy(questions = fn(a.questions)))
    }

    fun setCheckIn(sessionId: String, fn: (CheckInState) -> CheckInState) = update { s ->
        val a = s.active
        if (a == null || a.id != sessionId) s else s.copy(active = a.copy(checkIn = fn(a.checkIn)))
    }

    fun dismissLastSession() = update { it.copy(lastSession = null) }

    /** Marks planner events / notices as handled. */
    fun logPlan(ids: Collection<String>) = update { s -> s.copy(planLog = (s.planLog + ids.filter { it !in s.planLog }).takeLast(300)) }

    fun noteNotified(at: Long) = update { s -> s.copy(notifyLog = (s.notifyLog + at).takeLast(40)) }

    /** Counts one covered attempt for the session's recap. Ignored if that session isn't running. */
    fun recordBlocked(sessionId: String, key: String) = update { s ->
        val a = s.active
        if (a == null || a.id != sessionId || (key !in a.blocked && a.blocked.size >= 100)) s
        else s.copy(active = a.copy(blocked = a.blocked + (key to ((a.blocked[key] ?: 0) + 1))))
    }

    fun clearAll() = update { AppState() }

    /**
     * The rules a session runs with: the profile's, except that its own music app (if it has a
     * playlist) is never blocked during the session, so the music can be opened and controlled.
     */
    private fun sessionRules(p: Profile?): app.lockdin.enforce.AppRules {
        val r = p?.rules ?: return app.lockdin.enforce.AppRules()
        val pkg = p.audio.musicProvider?.pkg ?: return r
        return when (r.mode) {
            app.lockdin.enforce.RuleMode.BLOCK_LIST -> r.copy(packages = r.packages - pkg)
            app.lockdin.enforce.RuleMode.APPROVED_ONLY -> r.copy(packages = r.packages + pkg)
        }
    }

    companion object {
        private const val PREFS = "lockdin"
        private const val KEY_V1 = "state.v1"
        private const val KEY_V2 = "state.v2"

        @Volatile private var shared: Store? = null

        /**
         * The one store for this process, shared by the UI and the blocking service, so a change
         * in either is seen by the other at once. Writes are committed synchronously: a session
         * must survive the process being killed right after it starts. Main thread only.
         */
        fun get(context: Context): Store = shared ?: synchronized(this) {
            shared ?: run {
                val prefs = context.applicationContext.getSharedPreferences(PREFS, Context.MODE_PRIVATE)
                val raw = prefs.getString(KEY_V2, null) ?: prefs.getString(KEY_V1, null)
                Store(StateCodec.decode(raw)) { prefs.edit().putString(KEY_V2, it).commit() }.also { shared = it }
            }
        }


        fun newId(): String = UUID.randomUUID().toString()
    }
}
