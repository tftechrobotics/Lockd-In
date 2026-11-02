// Session evaluation — a line-by-line port of core/src/session.rs `evaluate`
// (same as apps/web/src/engine/session.ts). Parity is enforced by
// SessionParityTest against parity-vectors.json generated from the Rust core.
// Instants are epoch milliseconds. Pure functions only.
package app.lockdin.engine

enum class PhaseKind { FOCUS, BREAK }

data class Segment(val kind: PhaseKind, val minutes: Int)

data class Phase(val index: Int, val kind: PhaseKind, val start: Long, val end: Long)

data class Occurrence(val start: Long, val end: Long, val phases: List<Phase>)

enum class OverrideScope { CURRENT_FOCUS_PERIOD, REST_OF_SESSION }

data class OverrideRecord(
    val id: String,
    val scope: OverrideScope,
    val phaseIndex: Int,
    val requestedAt: Long,
    val delaySeconds: Int,
    val cancelledAt: Long? = null,
)

data class SessionControls(
    val overrides: List<OverrideRecord> = emptyList(),
    val emergencyRecoveryAt: Long? = null,
)

enum class SessionStatus { UPCOMING, ACTIVE, COMPLETED, ENDED_BY_OVERRIDE, ENDED_BY_EMERGENCY_RECOVERY }

enum class ReleaseReason {
    NOT_STARTED, SCHEDULED_BREAK, OVERRIDE_CURRENT_FOCUS_PERIOD,
    SESSION_ENDED_BY_OVERRIDE, EMERGENCY_RECOVERY, COMPLETED,
}

sealed interface OverrideState {
    data object None : OverrideState
    data class Pending(val scope: OverrideScope, val effectiveAt: Long) : OverrideState
    data class ActiveForFocusPeriod(val until: Long) : OverrideState
}

data class Snapshot(
    val status: SessionStatus,
    val phase: Phase?,
    val restricting: Boolean,
    val releaseReason: ReleaseReason?,
    val overrideState: OverrideState,
    val nextTransition: Long?,
)

object Session {
    const val DEFAULT_OVERRIDE_DELAY_SECONDS = 90
    const val MAX_OVERRIDE_DELAY_SECONDS = 15 * 60
    const val MIN_FOCUS_MINUTES = 1
    const val MAX_FOCUS_MINUTES = 240
    const val MIN_BREAK_MINUTES = 1
    const val MAX_BREAK_MINUTES = 120
    const val MAX_SESSION_MINUTES = 12 * 60
    const val MAX_SEGMENTS = 41
    private const val MIN = 60_000L

    /** `rounds` focus periods separated by breaks (same shape as SessionPlan::rounds). */
    fun roundsPlan(rounds: Int, focusMinutes: Int, breakMinutes: Int): List<Segment> = buildList {
        repeat(rounds) { i ->
            if (i > 0) add(Segment(PhaseKind.BREAK, breakMinutes))
            add(Segment(PhaseKind.FOCUS, focusMinutes))
        }
    }

    /** Mirrors SessionPlan::validate. Returns a user-facing problem, or null when valid. */
    fun validatePlan(segs: List<Segment>): String? {
        if (segs.isEmpty()) return "Add at least one focus period."
        if (segs.size > MAX_SEGMENTS) return "Use at most ${(MAX_SEGMENTS + 1) / 2} focus periods."
        if (segs.first().kind != PhaseKind.FOCUS || segs.last().kind != PhaseKind.FOCUS) return "A session starts and ends with focus."
        for (i in 1 until segs.size) if (segs[i].kind == segs[i - 1].kind) return "Focus periods and breaks must alternate."
        var total = 0L
        for (s in segs) {
            if (s.kind == PhaseKind.FOCUS && s.minutes !in MIN_FOCUS_MINUTES..MAX_FOCUS_MINUTES)
                return "Focus periods are $MIN_FOCUS_MINUTES–$MAX_FOCUS_MINUTES minutes."
            if (s.kind == PhaseKind.BREAK && s.minutes !in MIN_BREAK_MINUTES..MAX_BREAK_MINUTES)
                return "Breaks are $MIN_BREAK_MINUTES–$MAX_BREAK_MINUTES minutes."
            total += s.minutes
        }
        if (total > MAX_SESSION_MINUTES) return "A session can be at most 12 hours."
        return null
    }

    /** Elapsed-time phases from a start instant (an immediate session). */
    fun occurrenceFrom(start: Long, segs: List<Segment>): Occurrence {
        var t = start
        val phases = segs.mapIndexed { i, s ->
            Phase(i, s.kind, t, t + s.minutes * MIN).also { t = it.end }
        }
        return Occurrence(start, t, phases)
    }

    private fun effectiveAt(o: OverrideRecord) =
        o.requestedAt + minOf(o.delaySeconds, MAX_OVERRIDE_DELAY_SECONDS).coerceAtLeast(0) * 1000L

    private fun cancelledBy(o: OverrideRecord, t: Long) = o.cancelledAt != null && o.cancelledAt <= t

    private fun origin(o: OverrideRecord, occ: Occurrence): Phase? {
        val phase = occ.phases.getOrNull(o.phaseIndex) ?: return null
        val inside = phase.start <= o.requestedAt && o.requestedAt < phase.end
        val kindOk = o.scope == OverrideScope.REST_OF_SESSION || phase.kind == PhaseKind.FOCUS
        return if (inside && kindOk) phase else null
    }

    /** When an override really takes effect (null if it never does); also used for history. */
    fun takesEffect(o: OverrideRecord, occ: Occurrence): Long? {
        val org = origin(o, occ) ?: return null
        val eff = effectiveAt(o)
        val limit = if (o.scope == OverrideScope.CURRENT_FOCUS_PERIOD) org.end else occ.end
        if (eff >= limit || cancelledBy(o, eff)) return null
        return eff
    }

    fun evaluate(occ: Occurrence, controls: SessionControls, now: Long): Snapshot {
        val emergencyAt = controls.emergencyRecoveryAt?.takeIf { it < occ.end }
        val sessionOverrideAt = controls.overrides
            .filter { it.scope == OverrideScope.REST_OF_SESSION }
            .mapNotNull { takesEffect(it, occ) }
            .minOrNull()

        val status = when {
            emergencyAt != null && emergencyAt <= now -> SessionStatus.ENDED_BY_EMERGENCY_RECOVERY
            sessionOverrideAt != null && sessionOverrideAt <= now -> SessionStatus.ENDED_BY_OVERRIDE
            now < occ.start -> SessionStatus.UPCOMING
            now >= occ.end -> SessionStatus.COMPLETED
            else -> SessionStatus.ACTIVE
        }

        val phase = if (status == SessionStatus.ACTIVE) occ.phases.firstOrNull { it.start <= now && now < it.end } else null

        var overrideState: OverrideState = OverrideState.None
        if (status == SessionStatus.ACTIVE) {
            for (o in controls.overrides) {
                if (cancelledBy(o, now)) continue
                val org = origin(o, occ) ?: continue
                val eff = takesEffect(o, occ) ?: continue
                val current = phase != null && phase.index == org.index
                if (o.scope == OverrideScope.CURRENT_FOCUS_PERIOD && current && eff <= now) {
                    overrideState = OverrideState.ActiveForFocusPeriod(org.end)
                    break
                }
                if (eff > now && (current || o.scope == OverrideScope.REST_OF_SESSION)) {
                    overrideState = OverrideState.Pending(o.scope, eff)
                }
            }
        }

        val inFocus = phase?.kind == PhaseKind.FOCUS
        val lifted = overrideState is OverrideState.ActiveForFocusPeriod
        val restricting = status == SessionStatus.ACTIVE && inFocus && !lifted

        val releaseReason = if (restricting) null else when (status) {
            SessionStatus.UPCOMING -> ReleaseReason.NOT_STARTED
            SessionStatus.COMPLETED -> ReleaseReason.COMPLETED
            SessionStatus.ENDED_BY_OVERRIDE -> ReleaseReason.SESSION_ENDED_BY_OVERRIDE
            SessionStatus.ENDED_BY_EMERGENCY_RECOVERY -> ReleaseReason.EMERGENCY_RECOVERY
            SessionStatus.ACTIVE -> if (lifted) ReleaseReason.OVERRIDE_CURRENT_FOCUS_PERIOD else ReleaseReason.SCHEDULED_BREAK
        }

        val candidates = mutableListOf(occ.start, occ.end)
        occ.phases.forEach { candidates += it.end }
        emergencyAt?.let { candidates += it }
        for (o in controls.overrides) {
            if (origin(o, occ) != null) {
                candidates += effectiveAt(o)
                o.cancelledAt?.let { candidates += it }
            }
        }
        val ended = status != SessionStatus.UPCOMING && status != SessionStatus.ACTIVE
        val nextTransition = if (ended) null else candidates.filter { it > now && it <= occ.end }.minOrNull()

        return Snapshot(status, phase, restricting, releaseReason, overrideState, nextTransition)
    }
}
