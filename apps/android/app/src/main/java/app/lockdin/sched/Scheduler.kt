package app.lockdin.sched

import android.app.AlarmManager
import android.app.PendingIntent
import android.content.BroadcastReceiver
import android.content.Context
import android.content.Intent
import android.os.Build
import android.util.Log
import app.lockdin.engine.EventKind
import app.lockdin.engine.PlanEvent
import app.lockdin.engine.Planner
import app.lockdin.engine.StartDecision
import app.lockdin.engine.Upcoming
import app.lockdin.enforce.FocusService
import app.lockdin.state.LastChoice
import app.lockdin.state.Store
import app.lockdin.ui.Fmt
import java.time.ZoneId

/**
 * Runs the schedule: one alarm for the next planned event (reminder, start, missed check). When
 * it fires — or after a reboot, a clock or time-zone change, or when the app opens — every due
 * event is handled once and the next alarm is set.
 *
 * Starts are on time when "Alarms & reminders" is allowed (exact alarm; Android also lets an
 * exact alarm start the blocking service). Without it the alarm is inexact and may be minutes
 * late; the app says so where you turn on automatic starts. A late start joins the session in
 * progress rather than shifting it.
 */
object Scheduler {
    private const val TAG = "LockdInPlan"
    const val ACTION_WAKE = "app.lockdin.PLAN_WAKE"

    fun canExact(ctx: Context): Boolean =
        Build.VERSION.SDK_INT < 31 || ctx.getSystemService(AlarmManager::class.java).canScheduleExactAlarms()

    private fun wakeIntent(ctx: Context) = PendingIntent.getBroadcast(
        ctx, 7, Intent(ctx, PlanReceiver::class.java).setAction(ACTION_WAKE),
        PendingIntent.FLAG_IMMUTABLE or PendingIntent.FLAG_UPDATE_CURRENT,
    )

    /** Handles everything due, then sets the alarm for the next event. Main thread. */
    fun run(ctx: Context, now: Long = System.currentTimeMillis(), zone: ZoneId = ZoneId.systemDefault()) {
        val store = Store.get(ctx)
        val st = store.state
        val events = Planner.events(st.schedule, now, zone, st.reminders)
        val handled = st.planLog.toSet()
        val due = Planner.due(events, now, handled)
        if (due.isNotEmpty()) {
            // Mark first, so a crash mid-way never repeats a start or a notification.
            store.logPlan(due.map { it.id })
            for (e in due) runCatching { handle(ctx, e, now, zone) }.onFailure { Log.w(TAG, "handle ${e.kind}: ${it.javaClass.simpleName}") }
        }
        // Stale ones (phone off for many hours) are marked so they never fire later.
        val stale = events.filter { it.at <= now && it.id !in handled && it !in due }.map { it.id }
        if (stale.isNotEmpty()) store.logPlan(stale)
        schedule(ctx, Planner.nextWake(events, now, Store.get(ctx).state.planLog.toSet()))
    }

    private fun schedule(ctx: Context, at: Long?) {
        val am = ctx.getSystemService(AlarmManager::class.java)
        val pi = wakeIntent(ctx)
        if (at == null) { am.cancel(pi); return }
        try {
            if (canExact(ctx)) am.setExactAndAllowWhileIdle(AlarmManager.RTC_WAKEUP, at, pi)
            else am.setAndAllowWhileIdle(AlarmManager.RTC_WAKEUP, at, pi)
            Log.i(TAG, "next wake in ${(at - System.currentTimeMillis()) / 1000}s exact=${canExact(ctx)}")
        } catch (e: SecurityException) {
            am.setAndAllowWhileIdle(AlarmManager.RTC_WAKEUP, at, pi)
        }
    }

    private fun handle(ctx: Context, e: PlanEvent, now: Long, zone: ZoneId) {
        val store = Store.get(ctx)
        val o = e.occ
        val running = store.state.active?.let { FocusService.shouldRun(store.state, now) } == true
        when (e.kind) {
            EventKind.REMIND -> if (!running) remind(ctx, o, now, zone, starting = false)
            EventKind.START -> if (o.session.autoStart) autoStart(ctx, o, now, zone) else if (!running) remind(ctx, o, now, zone, starting = true)
            EventKind.MISSED_CHECK -> if (Planner.wasMissed(o, store.state.recentStarts)) followUp(ctx, o, now, zone)
        }
    }

    /** Starts a planned session now (from an alarm or a notification action). */
    fun startOccurrence(ctx: Context, o: Upcoming, now: Long, zone: ZoneId, auto: Boolean) {
        val store = Store.get(ctx)
        val running = store.state.active != null && FocusService.shouldRun(store.state, now)
        when (val d = Planner.decideStart(o, now, running)) {
            is StartDecision.Start -> {
                val s = o.session
                // A late automatic start joins the occurrence in progress; a manual "Start now"
                // starts the planned length from now.
                val start = if (auto) d.start else now
                if (store.state.active != null) store.finish(dismissed = false) // a finished recap moves to Home
                store.startSession(start, s.plan, s.title, s.profileId,
                    LastChoice(s.focusMinutes, s.breakMinutes, s.rounds, s.profileId), occurrenceKey = Planner.key(o))
                FocusService.sync(ctx)
                Notifier.cancel(ctx, Notifier.idFor(Planner.key(o)))
                if (auto) Notifier.post(
                    ctx, Notifier.idFor(Planner.key(o)), Notifier.CH_EVENTS,
                    if (d.late) "${s.title} started late" else "${s.title} started",
                    if (d.late) "Your phone wasn’t able to start it at ${Fmt.clock(o.start, zone)}, so it joined the session in progress."
                    else "Started automatically at ${Fmt.clock(o.start, zone)}. Ends at ${Fmt.clock(o.end, zone)}.",
                )
            }
            StartDecision.AlreadyRunning -> Notifier.post(
                ctx, Notifier.idFor(Planner.key(o)), Notifier.CH_REMINDERS, "${o.session.title} didn’t start",
                "Another session is running, so the planned one wasn’t started.",
            )
            StartDecision.Missed -> Unit
        }
    }

    private fun autoStart(ctx: Context, o: Upcoming, now: Long, zone: ZoneId) = startOccurrence(ctx, o, now, zone, auto = true)

    private fun remind(ctx: Context, o: Upcoming, now: Long, zone: ZoneId, starting: Boolean) {
        val st = Store.get(ctx).state
        if (!st.reminders.remindersOn) return
        val key = Planner.key(o)
        if (Planner.inQuietHours(now, zone, st.reminders) || !Planner.underRateLimit(st.notifyLog, now, st.reminders)) {
            Log.i(TAG, "reminder held (quiet hours or limit)"); return
        }
        val title = if (starting) "${o.session.title} starts now" else "${o.session.title} at ${Fmt.clock(o.start, zone)}"
        val text = if (starting) "Ready? It runs until ${Fmt.clock(o.end, zone)}."
        else "In ${((o.start - now + 59_999) / 60_000)} min. ${if (o.session.autoStart) "It starts and blocks automatically." else "Start it when you’re ready."}"
        Notifier.post(ctx, Notifier.idFor(key), Notifier.CH_REMINDERS, title, text, listOf(
            Notifier.Action("Start now", PlanActions.pending(ctx, PlanActions.START_NOW, key)),
            Notifier.Action("In 10 min", PlanActions.pending(ctx, PlanActions.SNOOZE, key)),
            Notifier.Action("Skip this time", PlanActions.pending(ctx, PlanActions.SKIP, key)),
        ))
        Store.get(ctx).noteNotified(now)
    }

    private fun followUp(ctx: Context, o: Upcoming, now: Long, zone: ZoneId) {
        val st = Store.get(ctx).state
        if (!st.reminders.missedFollowUpOn || Planner.inQuietHours(now, zone, st.reminders) ||
            !Planner.underRateLimit(st.notifyLog, now, st.reminders)) return
        val key = Planner.key(o)
        Notifier.post(ctx, Notifier.idFor(key), Notifier.CH_FOLLOW_UP, "${o.session.title} didn’t happen",
            "That’s okay. A short 10-minute start can help.", listOf(
                Notifier.Action("Start 10 min", PlanActions.pending(ctx, PlanActions.QUICK, key)),
                Notifier.Action("Reschedule", PlanActions.pending(ctx, PlanActions.RESCHEDULE, key)),
            ), openRoute = "schedule")
        Store.get(ctx).noteNotified(now)
    }

    /** Finds an occurrence by its key (schedule id + start), if still planned. */
    fun occurrence(ctx: Context, key: String, now: Long, zone: ZoneId): Upcoming? {
        val st = Store.get(ctx).state
        return Planner.occurrences(st.schedule, now - 2 * 24 * 3_600_000L, now + 9 * 24 * 3_600_000L, zone)
            .firstOrNull { Planner.key(it) == key }
    }
}

/** Alarm, boot, clock, time-zone and permission changes all lead to Scheduler.run. */
class PlanReceiver : BroadcastReceiver() {
    override fun onReceive(context: Context, intent: Intent) {
        Log.i("LockdInPlan", "wake: ${intent.action}")
        Scheduler.run(context)
        if (intent.action == Intent.ACTION_BOOT_COMPLETED || intent.action == Intent.ACTION_MY_PACKAGE_REPLACED) FocusService.sync(context)
    }
}
