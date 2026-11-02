// Study audio (R48): what a profile asks for, and the pure rules around it. Nothing here touches
// Android, so all of it is unit tested.
//
// Two independent settings per profile:
//  - built-in study audio, played by Lockd In from recordings bundled in the app;
//  - access to external music apps during focus: normal blocking, a limited browsing allowance
//    shared across chosen apps, or always allowed.
package app.lockdin.audio

/** A recording bundled with the app. `res` is the raw resource name (res/raw/<res>.ogg). */
data class Track(val title: String, val res: String)

/**
 * A small curated collection. Instrumental only, no lyrics. `loop` collections are one seamless
 * ambient file repeated; the others play their tracks in order and start again.
 *
 * DEVELOPMENT RECORDINGS: synthesised by design/audio/gen.py (owned by the project). See
 * docs/audio-assets.md for each file's source, licence and the content still needed for release.
 */
enum class AudioCollection(val label: String, val blurb: String, val tracks: List<Track>, val loop: Boolean) {
    PIANO("Piano", "Slow solo piano", listOf(Track("Piano I", "piano_1"), Track("Piano II", "piano_2"), Track("Piano III", "piano_3")), loop = false),
    STRINGS("Strings", "Sustained string chords", listOf(Track("Strings I", "strings_1"), Track("Strings II", "strings_2"), Track("Strings III", "strings_3")), loop = false),
    RAIN("Rain", "Steady rain, no thunder", listOf(Track("Rain", "rain_1")), loop = true),
    ;
    /** Shown with the collections until produced recordings replace the generated ones. */
    val development: Boolean get() = true
}

enum class BreakAudio { PAUSE, CONTINUE }

/** External music apps during focus. */
enum class MusicAccess(val label: String) {
    NORMAL("Follow blocking rules"),
    LIMITED("Limited browsing"),
    ALWAYS("Always allow"),
}

data class ProfileAudio(
    /** Built-in study audio is off by default. */
    val builtIn: Boolean = false,
    val collection: AudioCollection = AudioCollection.PIANO,
    val volume: Float = 0.6f,
    val playOnStart: Boolean = false,
    val breaks: BreakAudio = BreakAudio.PAUSE,
    val access: MusicAccess = MusicAccess.NORMAL,
    /** Music apps the person chose for LIMITED or ALWAYS (package names). */
    val musicApps: Set<String> = emptySet(),
    /** LIMITED: browsing allowance for a whole session, shared by the chosen apps. */
    val allowanceMin: Int = MusicAllowance.DEFAULT_MIN,
) {
    val accessApps: Set<String> get() = if (access == MusicAccess.NORMAL) emptySet() else musicApps
}

object MusicAllowance {
    const val DEFAULT_MIN = 5
    val CHOICES = listOf(2, 5, 10, 15)
    const val WARN_MS = 30_000L
    /** Longest gap between two checks that still counts as continuous use (a missed check never adds more). */
    const val MAX_STEP_MS = 3_000L

    fun allowanceMs(a: ProfileAudio): Long = a.allowanceMin.coerceIn(1, 60) * 60_000L

    /**
     * One accounting step. `deltaMs` comes from elapsedRealtime, so changing the wall clock can't
     * refill the allowance. It only counts while a chosen app is in front, the screen is on, and
     * a focus period is restricting. Background playback, screen-off time, breaks and other apps
     * don't count.
     */
    fun step(usedMs: Long, deltaMs: Long, counting: Boolean, allowanceMs: Long): Long {
        if (!counting || deltaMs <= 0) return usedMs
        return (usedMs + minOf(deltaMs, MAX_STEP_MS)).coerceAtMost(allowanceMs)
    }

    fun remainingMs(a: ProfileAudio, usedMs: Long): Long = (allowanceMs(a) - usedMs).coerceAtLeast(0)
    fun spent(a: ProfileAudio, usedMs: Long): Boolean = a.access == MusicAccess.LIMITED && usedMs >= allowanceMs(a)
    fun shouldWarn(a: ProfileAudio, usedMs: Long, warned: Boolean): Boolean =
        a.access == MusicAccess.LIMITED && !warned && remainingMs(a, usedMs) in 1..WARN_MS

    /**
     * The decision for a chosen music app during focus, or null to fall back to the profile's
     * normal rules. ALWAYS: never covered. LIMITED: allowed until the allowance is spent.
     */
    fun decide(pkg: String, a: ProfileAudio, usedMs: Long): Boolean? {
        if (pkg !in a.accessApps) return null
        return when (a.access) {
            MusicAccess.NORMAL -> null
            MusicAccess.ALWAYS -> false
            MusicAccess.LIMITED -> spent(a, usedMs)
        }
    }

    /** "3:05" for the session screen. */
    fun format(ms: Long): String { val s = (ms + 999) / 1000; return "%d:%02d".format(s / 60, s % 60) }
}

/** When the built-in player should start or stop by itself (the person's own taps always win). */
object AudioRules {
    /** Only a focus period that has just begun starts audio by itself, so a restart after a reboot or recovery never does. */
    const val FRESH_MS = 60_000L

    fun autoStart(a: ProfileAudio, focusPhaseStart: Long, now: Long, alreadyFor: Boolean): Boolean =
        a.builtIn && a.playOnStart && !alreadyFor && now - focusPhaseStart in 0..FRESH_MS

    /** Resume after a break only if it was playing when the break began and the profile pauses for breaks. */
    fun resumeAfterBreak(a: ProfileAudio, wasPlayingAtBreak: Boolean, focusPhaseStart: Long, now: Long): Boolean =
        a.builtIn && a.breaks == BreakAudio.PAUSE && wasPlayingAtBreak && now - focusPhaseStart in 0..FRESH_MS
}
