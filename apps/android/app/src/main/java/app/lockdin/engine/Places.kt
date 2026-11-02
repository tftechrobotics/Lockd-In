// Location routines (roadmap step 7, E): places the person chose, with a time window and a
// profile. Pure decisions; geofencing is loc/Geofences.kt. Reminders by default; automatic start
// only when turned on separately for that place. Leaving a place never adds restrictions.
// No location history: only the place itself is stored, and which day a place last fired.
package app.lockdin.engine

import java.time.DayOfWeek
import java.time.Instant
import java.time.LocalTime
import java.time.ZoneId

data class Place(
    val id: String,
    val name: String,
    val lat: Double,
    val lon: Double,
    val radiusM: Int = 150,
    val profileId: String,
    /** Empty = every day. */
    val days: Set<DayOfWeek> = emptySet(),
    val from: LocalTime = LocalTime.of(8, 0),
    val to: LocalTime = LocalTime.of(22, 0),
    val focusMinutes: Int = 40,
    /** Start (and block) on arrival without asking. Off: a reminder asks first. */
    val autoStart: Boolean = false,
    /** Minutes inside the place before anything happens (avoids passing by). */
    val arriveGraceMin: Int = 3,
    val enabled: Boolean = true,
)

sealed class Arrival {
    data class Skip(val reason: String) : Arrival()
    object Remind : Arrival()
    object Start : Arrival()
}

object Places {
    val RADIUS_CHOICES = listOf(100, 150, 250, 500)
    val GRACE_CHOICES = listOf(0, 2, 3, 5, 10)

    /** Window check in local time; a window that ends before it starts runs past midnight. */
    fun inWindow(p: Place, now: Long, zone: ZoneId): Boolean {
        val t = Instant.ofEpochMilli(now).atZone(zone)
        val time = t.toLocalTime()
        val overnight = p.to <= p.from
        val inTime = if (overnight) !time.isBefore(p.from) || time.isBefore(p.to) else !time.isBefore(p.from) && time.isBefore(p.to)
        // For overnight windows after midnight, the day that counts is the day the window began.
        val day = if (overnight && time.isBefore(p.to)) t.dayOfWeek.minus(1) else t.dayOfWeek
        return inTime && (p.days.isEmpty() || day in p.days)
    }

    fun firedKey(p: Place, now: Long, zone: ZoneId) = "PLACE:${p.id}@${Instant.ofEpochMilli(now).atZone(zone).toLocalDate()}"

    /** What to do when the person has arrived (after the grace period). At most once a day per place. */
    fun onArrive(p: Place, now: Long, zone: ZoneId, paused: Boolean, sessionRunning: Boolean, handled: Set<String>): Arrival = when {
        paused -> Arrival.Skip("paused")
        !p.enabled -> Arrival.Skip("disabled")
        !inWindow(p, now, zone) -> Arrival.Skip("outside window")
        sessionRunning -> Arrival.Skip("session running")
        firedKey(p, now, zone) in handled -> Arrival.Skip("already today")
        p.autoStart -> Arrival.Start
        else -> Arrival.Remind
    }

    /** Leaving: only a gentle check-in, and only for a session that this place started. */
    fun onLeave(placeId: String, activeOccurrenceKey: String?): Boolean =
        activeOccurrenceKey?.startsWith("PLACE:$placeId@") == true
}
