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

    fun startSession(now: Long, segments: List<Segment>, intention: String?, profileId: String, last: LastChoice) = update { s ->
        val text = intention?.trim()?.take(120)?.takeIf { it.isNotEmpty() }
        s.copy(
            last = last,
            active = ActiveSession(
                newId(), now, segments, text, profileId, SessionControls(),
                rules = s.profiles.firstOrNull { it.id == profileId }?.rules ?: app.lockdin.enforce.AppRules(),
                commitment = s.commitment,
            ),
            recentIntentions = if (text == null) s.recentIntentions else (listOf(text) + s.recentIntentions.filter { it != text }).take(8),
        )
    }

    fun setControls(sessionId: String, fn: (SessionControls) -> SessionControls) = update { s ->
        val a = s.active
        if (a == null || a.id != sessionId) s else s.copy(active = a.copy(controls = fn(a.controls)))
    }

    fun finish() = update { it.copy(active = null) }

    fun clearAll() = update { AppState() }

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
