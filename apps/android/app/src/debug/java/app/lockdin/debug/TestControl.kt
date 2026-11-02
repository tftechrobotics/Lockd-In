// DEBUG BUILDS ONLY (src/debug): lets CI start and inspect a session from adb, so the emulator
// enforcement test exercises the real store, service and block screen. Protected by the DUMP
// permission, which only the shell (adb) holds. Not compiled into release builds.
package app.lockdin.debug

import android.content.BroadcastReceiver
import android.content.Context
import android.content.Intent
import android.util.Log
import app.lockdin.enforce.AppRules
import app.lockdin.enforce.Commitment
import app.lockdin.enforce.RuleMode
import app.lockdin.engine.Session
import app.lockdin.state.LastChoice
import app.lockdin.state.Store

class TestControl : BroadcastReceiver() {
    override fun onReceive(context: Context, intent: Intent) {
        val store = Store.get(context)
        when (intent.getStringExtra("cmd")) {
            "start" -> {
                val focus = intent.getIntExtra("focus", 1)
                val brk = intent.getIntExtra("break", 1)
                val rounds = intent.getIntExtra("rounds", 1)
                val pkgs = intent.getStringExtra("block")?.split(',')?.filter { it.isNotBlank() }?.toSet() ?: emptySet()
                val mode = if (intent.getStringExtra("mode") == "approved") RuleMode.APPROVED_ONLY else RuleMode.BLOCK_LIST
                val sites = intent.getStringExtra("sites")?.split(',')?.mapNotNull { app.lockdin.enforce.BlockPolicy.domainOf(it) }?.toSet() ?: emptySet()
                store.update { s ->
                    s.copy(
                        profiles = s.profiles.map {
                            if (it.id == "study") it.copy(
                                rules = AppRules(mode, pkgs, sites),
                                audio = app.lockdin.state.ProfileAudio(
                                    ambient = intent.getStringExtra("ambient")?.let { a -> runCatching { app.lockdin.audio.Ambient.valueOf(a) }.getOrNull() } ?: app.lockdin.audio.Ambient.NONE,
                                ),
                            ) else it
                        },
                        commitment = Commitment.DELAYED,
                    )
                }
                store.startSession(
                    System.currentTimeMillis(), Session.roundsPlan(rounds, focus, brk), "CI enforcement test", "study",
                    LastChoice(focus, brk, rounds, "study"),
                )
            }
            // Plans a one-time session today at HH:mm (`at`), optionally starting automatically.
            "plan" -> {
                val at = java.time.LocalTime.parse(intent.getStringExtra("at") ?: return)
                val auto = intent.getBooleanExtra("auto", false)
                val pkgs = intent.getStringExtra("block")?.split(',')?.filter { it.isNotBlank() }?.toSet() ?: emptySet()
                store.update { s ->
                    s.copy(
                        profiles = s.profiles.map { if (it.id == "study") it.copy(rules = AppRules(RuleMode.BLOCK_LIST, pkgs)) else it },
                        schedule = s.schedule + app.lockdin.engine.ScheduledSession(
                            Store.newId(), intent.getStringExtra("title") ?: "E2E plan", emptySet(), at,
                            intent.getIntExtra("focus", 2), 1, 1, "study",
                            date = java.time.LocalDate.now(), autoStart = auto, remindMinutes = 0,
                        ),
                    )
                }
                app.lockdin.sched.Scheduler.run(context)
            }
            // Sets the Study profile's apps (empty = timer only) and makes it the setup default.
            "rules" -> {
                val pkgs = intent.getStringExtra("block")?.split(',')?.filter { it.isNotBlank() }?.toSet() ?: emptySet()
                store.update { s ->
                    s.copy(
                        profiles = s.profiles.map { if (it.id == "study") it.copy(rules = AppRules(RuleMode.BLOCK_LIST, pkgs)) else it },
                        last = s.last.copy(profileId = "study"),
                    )
                }
            }
            // Turns calendar suggestions on and logs what the device calendar returns (C).
            "calendar" -> {
                store.update { it.copy(calendarOn = true) }
                val now = System.currentTimeMillis()
                val ev = app.lockdin.cal.DeviceCalendar.events(context, now, now + 2 * 86_400_000L, emptySet())
                val zone = java.time.ZoneId.systemDefault()
                val today = java.time.LocalDate.now(zone)
                val windows = app.lockdin.engine.CalendarPlan.freeWindows(ev, today, zone, now) + app.lockdin.engine.CalendarPlan.freeWindows(ev, today.plusDays(1), zone, now)
                Log.i("LockdInTest", "calendar permitted=${app.lockdin.cal.DeviceCalendar.permitted(context)} calendars=${app.lockdin.cal.DeviceCalendar.calendars(context).size} events=${ev.size} titles=${ev.joinToString("|") { it.title }} windows=${windows.size}")
            }
            // Adds an all-day place at lat/lon (no grace) and registers geofences (E).
            "place" -> {
                val p = app.lockdin.engine.Place(
                    "ciplace", "CIPlace", intent.getStringExtra("lat")!!.toDouble(), intent.getStringExtra("lon")!!.toDouble(),
                    radiusM = 150, profileId = "study", from = java.time.LocalTime.MIN, to = java.time.LocalTime.of(23, 59),
                    arriveGraceMin = 0,
                )
                store.update { it.copy(places = listOf(p), placesPaused = false) }
                app.lockdin.loc.Geofences.sync(context)
            }
            "emergency" -> store.state.active?.let { a ->
                store.setControls(a.id) { it.copy(emergencyRecoveryAt = System.currentTimeMillis()) }
            }
            "finish" -> store.finish()
        }
        Log.i("LockdInTest", "cmd=${intent.getStringExtra("cmd")} active=${store.state.active?.id}")
    }
}
