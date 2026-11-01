package app.lockdin.enforce

import android.os.SystemClock
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow

/**
 * What the blocking service last observed, for the UI. The service publishes a report on
 * every tick; a report older than [FRESH_MS] means the service is not running (crashed,
 * stopped by the system), so the UI must not show protection from it.
 */
object EnforcerStatus {
    data class Report(
        val sessionId: String?,
        val protection: Protection,
        val missing: Set<Capability>,
        val atElapsed: Long = SystemClock.elapsedRealtime(),
    ) {
        fun fresh(nowElapsed: Long = SystemClock.elapsedRealtime()) = nowElapsed - atElapsed <= FRESH_MS
    }

    const val FRESH_MS = 4_000L

    private val state = MutableStateFlow<Report?>(null)
    val reports: StateFlow<Report?> = state

    fun publish(r: Report) { state.value = r }
    fun clear() { state.value = null }

    /** True only for a fresh report of verified protection for this session. */
    fun protecting(sessionId: String, r: Report? = state.value): Boolean =
        r != null && r.sessionId == sessionId && r.protection == Protection.PROTECTING && r.fresh()
}
