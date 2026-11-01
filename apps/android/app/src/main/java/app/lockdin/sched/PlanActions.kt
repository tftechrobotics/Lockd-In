package app.lockdin.sched

import android.app.AlarmManager
import android.app.PendingIntent
import android.content.BroadcastReceiver
import android.content.Context
import android.content.Intent
import android.util.Log
import app.lockdin.MainActivity
import app.lockdin.engine.Planner
import app.lockdin.engine.Session
import app.lockdin.enforce.FocusService
import app.lockdin.state.LastChoice
import app.lockdin.state.Store
import java.time.ZoneId

/** Notification buttons: Start now, In 10 min, Skip this time, Start 10 min, Reschedule. */
object PlanActions {
    const val START_NOW = "app.lockdin.plan.START_NOW"
    const val SNOOZE = "app.lockdin.plan.SNOOZE"
    const val SNOOZED = "app.lockdin.plan.SNOOZED"
    const val SKIP = "app.lockdin.plan.SKIP"
    const val QUICK = "app.lockdin.plan.QUICK"
    const val RESCHEDULE = "app.lockdin.plan.RESCHEDULE"
    const val EXTRA_KEY = "key"
    const val SNOOZE_MS = 10 * 60_000L
    const val QUICK_MINUTES = 10

    fun pending(ctx: Context, action: String, key: String): PendingIntent {
        val code = (action + key).hashCode()
        if (action == RESCHEDULE) {
            // Opening the app must be an activity intent (Android blocks notification trampolines).
            return PendingIntent.getActivity(
                ctx, code, Intent(ctx, MainActivity::class.java).putExtra(MainActivity.EXTRA_ROUTE, "schedule")
                    .addFlags(Intent.FLAG_ACTIVITY_SINGLE_TOP),
                PendingIntent.FLAG_IMMUTABLE or PendingIntent.FLAG_UPDATE_CURRENT,
            )
        }
        return PendingIntent.getBroadcast(
            ctx, code, Intent(ctx, PlanActionReceiver::class.java).setAction(action).putExtra(EXTRA_KEY, key),
            PendingIntent.FLAG_IMMUTABLE or PendingIntent.FLAG_UPDATE_CURRENT,
        )
    }
}

class PlanActionReceiver : BroadcastReceiver() {
    override fun onReceive(ctx: Context, intent: Intent) {
        val key = intent.getStringExtra(PlanActions.EXTRA_KEY) ?: return
        val now = System.currentTimeMillis()
        val zone = ZoneId.systemDefault()
        val store = Store.get(ctx)
        val o = Scheduler.occurrence(ctx, key, now, zone)
        Log.i("LockdInPlan", "action ${intent.action}")
        when (intent.action) {
            PlanActions.START_NOW -> if (o != null) Scheduler.startOccurrence(ctx, o, now, zone, auto = false)
            PlanActions.SNOOZE -> {
                Notifier.cancel(ctx, Notifier.idFor(key))
                val pi = PendingIntent.getBroadcast(
                    ctx, ("snooze$key").hashCode(),
                    Intent(ctx, PlanActionReceiver::class.java).setAction(PlanActions.SNOOZED).putExtra(PlanActions.EXTRA_KEY, key),
                    PendingIntent.FLAG_IMMUTABLE or PendingIntent.FLAG_UPDATE_CURRENT,
                )
                ctx.getSystemService(AlarmManager::class.java).setAndAllowWhileIdle(AlarmManager.RTC_WAKEUP, now + PlanActions.SNOOZE_MS, pi)
            }
            PlanActions.SNOOZED -> if (o != null && store.state.active == null) {
                Notifier.post(ctx, Notifier.idFor(key), Notifier.CH_REMINDERS, "${o.session.title}: ready now?",
                    "You asked to be reminded again.", listOf(
                        Notifier.Action("Start now", PlanActions.pending(ctx, PlanActions.START_NOW, key)),
                        Notifier.Action("Skip this time", PlanActions.pending(ctx, PlanActions.SKIP, key)),
                    ))
            }
            PlanActions.SKIP -> {
                Notifier.cancel(ctx, Notifier.idFor(key))
                if (o != null) {
                    val day = Planner.dateOf(o, zone)
                    store.update { s -> s.copy(schedule = s.schedule.map { if (it.id == o.session.id) it.copy(skipped = it.skipped + day) else it }) }
                    Scheduler.run(ctx)
                }
            }
            PlanActions.QUICK -> {
                Notifier.cancel(ctx, Notifier.idFor(key))
                if (store.state.active == null || !FocusService.shouldRun(store.state, now)) {
                    if (store.state.active != null) store.finish(dismissed = false)
                    val profile = o?.session?.profileId ?: store.state.last.profileId
                    store.startSession(now, Session.roundsPlan(1, PlanActions.QUICK_MINUTES, 0), o?.session?.title, profile,
                        store.state.last.copy(profileId = profile))
                    FocusService.sync(ctx)
                }
            }
        }
    }
}
