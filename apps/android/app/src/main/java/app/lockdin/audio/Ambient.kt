// Study audio (roadmap step 3, B2): ambient sounds generated on the phone, so there are no
// recordings to license. Pure sample generation here; playback is AmbientPlayer.
package app.lockdin.audio

import kotlin.math.PI
import kotlin.math.exp
import kotlin.math.sin

enum class Ambient(val label: String) {
    NONE("Off"),
    RAIN("Rain"),
    WAVES("Waves"),
    BROWN("Deep noise"),
    PINK("Soft noise"),
}

/**
 * Streams one ambient sound as 16-bit mono PCM. Deterministic for a given seed (tests).
 * Levels stay well below full scale; the player applies the person's level on top.
 */
class AmbientSynth(private val kind: Ambient, val sampleRate: Int = 22_050, seed: Long = 0x10CD1L) {
    private var rnd = seed xor 0x5DEECE66DL
    private var t = 0L
    // Pink (Paul Kellet's economy filter) and brown (leaky integrator) state.
    private var b0 = 0.0; private var b1 = 0.0; private var b2 = 0.0
    private var brown = 0.0
    private var lp = 0.0
    // Rain drops: a short decaying burst at random times.
    private var drop = 0.0
    private var dropDecay = 0.0

    private fun white(): Double {
        rnd = (rnd * 0x5DEECE66DL + 0xBL) and ((1L shl 48) - 1)
        return ((rnd ushr 16).toInt().toDouble() / Int.MAX_VALUE).coerceIn(-1.0, 1.0)
    }

    private fun pink(w: Double): Double {
        b0 = 0.99765 * b0 + w * 0.0990460
        b1 = 0.96300 * b1 + w * 0.2965164
        b2 = 0.57000 * b2 + w * 1.0526913
        return (b0 + b1 + b2 + w * 0.1848) * 0.11
    }

    private fun brownStep(w: Double): Double {
        brown = (brown + 0.02 * w) / 1.02
        return brown * 3.5
    }

    fun next(): Double {
        val w = white()
        val s = when (kind) {
            Ambient.NONE -> 0.0
            Ambient.PINK -> pink(w) * 0.9
            Ambient.BROWN -> brownStep(w)
            Ambient.WAVES -> {
                // Slow swell every ~9 s over soft brown noise.
                val phase = 2 * PI * t / (sampleRate * 9.0)
                val swell = 0.5 + 0.5 * sin(phase)
                val env = 0.25 + 0.75 * swell * swell
                lp += 0.08 * (brownStep(w) - lp)
                lp * env * 1.6
            }
            Ambient.RAIN -> {
                // A soft bed (pink with its lows taken out) plus random drops.
                val p = pink(w)
                lp += 0.02 * (p - lp)
                val bed = (p - lp) * 1.1
                if (drop < 0.002 && white() > 0.9985) { drop = 0.25 + 0.5 * (white() * 0.5 + 0.5); dropDecay = exp(-1.0 / (sampleRate * (0.004 + 0.01 * (white() * 0.5 + 0.5)))) }
                val d = drop * w
                drop *= dropDecay
                bed + d * 0.6
            }
        }
        t++
        return (s * 0.6).coerceIn(-0.95, 0.95)
    }

    /** Fills `out` with the next samples. */
    fun fill(out: ShortArray, n: Int = out.size) {
        for (i in 0 until n) out[i] = (next() * Short.MAX_VALUE).toInt().toShort()
    }
}

/** Pure decisions for study audio (testable without a phone). */
object AudioPlan {
    /** Should ambient sound be on now? The person's pause wins; auto-play off needs their start. */
    fun ambientWanted(
        audio: app.lockdin.state.ProfileAudio, sessionId: String, inFocus: Boolean, inBreak: Boolean,
        pausedFor: String?, startedFor: String?,
    ): Boolean {
        if (audio.ambient == Ambient.NONE) return false
        if (pausedFor == sessionId) return false
        if (!audio.autoPlay && startedFor != sessionId) return false
        return inFocus || (inBreak && audio.duringBreaks)
    }
}
