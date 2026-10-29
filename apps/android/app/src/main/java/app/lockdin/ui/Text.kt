// Formatting and the optional messages (same wording and rules as the web app).
package app.lockdin.ui

import app.lockdin.state.Tone
import java.time.Instant
import java.time.ZoneId
import java.time.format.DateTimeFormatter
import java.time.format.FormatStyle
import kotlin.math.ceil

object Fmt {
    fun countdown(ms: Long): String {
        val total = maxOf(0L, ceil(ms / 1000.0).toLong())
        val h = total / 3600; val m = (total % 3600) / 60; val s = total % 60
        return if (h > 0) "%d:%02d:%02d".format(h, m, s) else "%02d:%02d".format(m, s)
    }

    fun spokenRemaining(ms: Long): String {
        val min = ceil(ms / 60_000.0).toInt()
        return when {
            min <= 1 -> "Less than a minute left"
            min < 60 -> "$min minutes left"
            else -> {
                val h = min / 60; val m = min % 60
                "$h hour${if (h > 1) "s" else ""}${if (m > 0) " $m minutes" else ""} left"
            }
        }
    }

    private val time = DateTimeFormatter.ofLocalizedTime(FormatStyle.SHORT)
    fun clock(t: Long, zone: ZoneId = ZoneId.systemDefault()): String = time.format(Instant.ofEpochMilli(t).atZone(zone))

    fun minutes(m: Int) = if (m >= 60 && m % 60 == 0) "${m / 60} h" else "$m min"

    fun dayLabel(t: Long, now: Long, zone: ZoneId = ZoneId.systemDefault()): String {
        val d = Instant.ofEpochMilli(t).atZone(zone).toLocalDate()
        val n = Instant.ofEpochMilli(now).atZone(zone).toLocalDate()
        return when (d.toEpochDay() - n.toEpochDay()) {
            0L -> "Today"
            1L -> "Tomorrow"
            else -> d.dayOfWeek.getDisplayName(java.time.format.TextStyle.FULL, java.util.Locale.getDefault())
        }
    }
}

enum class Moment { FOCUS_START, BREAK_START, BREAK_ENDING, DONE }

/** Never shaming, never claims to know feelings. QUIET shows none. */
fun message(tone: Tone, moment: Moment, task: String? = null, time: String? = null): String? = when (tone) {
    Tone.QUIET -> null
    Tone.CALM -> when (moment) {
        Moment.FOCUS_START -> "Start with one small step."
        Moment.BREAK_START -> "Break until $time. Step away for a bit."
        Moment.BREAK_ENDING -> "Focus resumes in about a minute. One step at a time."
        Moment.DONE -> "That’s the session. Take a breath before the next thing."
    }
    Tone.DIRECT -> when (moment) {
        Moment.FOCUS_START -> if (task != null) "You planned $task. Go." else "Locked in. Go."
        Moment.BREAK_START -> "Break. Back at $time."
        Moment.BREAK_ENDING -> if (task != null) "Back to $task in a minute." else "Back to it in a minute."
        Moment.DONE -> "Done. Session saved on this phone."
    }
    Tone.PLAYFUL -> when (moment) {
        Moment.FOCUS_START -> "The feed will still be there after this."
        Moment.BREAK_START -> "Break unlocked until $time. Stretch, snack, hydrate."
        Moment.BREAK_ENDING -> "One minute left. Wrap up the snack."
        Moment.DONE -> "Session cleared. The lock says hi."
    }
}
