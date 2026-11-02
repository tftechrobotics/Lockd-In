// "Still working?" check-ins during focus (roadmap step 6, D). Silent, low-importance, one per
// idle stretch, never punitive. Answers only adjust when the next one may come.
package app.lockdin.sched

import android.app.Notification
import android.app.NotificationChannel
import android.app.NotificationManager
import android.app.PendingIntent
import android.content.BroadcastReceiver
import android.content.Context
import android.content.Intent
import app.lockdin.MainActivity
import app.lockdin.R
import app.lockdin.state.Store

object CheckInNotice {
    const val CHANNEL = "check_ins"
    const val ACTION = "app.lockdin.CHECK_IN"
    private const val ID = 4207

    fun channel(ctx: Context) {
        ctx.getSystemService(NotificationManager::class.java).createNotificationChannel(
            NotificationChannel(CHANNEL, "Still working? check-ins", NotificationManager.IMPORTANCE_LOW).apply {
                description = "A silent question after a stretch without using the phone during focus"
                setSound(null, null); enableVibration(false)
            },
        )
    }

    private fun action(ctx: Context, answer: String, req: Int) = PendingIntent.getBroadcast(
        ctx, req, Intent(ctx, CheckInReceiver::class.java).setAction(ACTION).putExtra("answer", answer),
        PendingIntent.FLAG_IMMUTABLE or PendingIntent.FLAG_UPDATE_CURRENT,
    )

    fun post(ctx: Context) {
        channel(ctx)
        val st = Store.get(ctx).state
        if (!Notifier.allowed(ctx) || !st.reminders.notificationsOn) return
        val n = Notification.Builder(ctx, CHANNEL)
            .setSmallIcon(R.drawable.ic_stat_lock)
            .setContentTitle("Still working?")
            .setContentText("Reading or writing on paper counts. Tap to take a break or change the session.")
            .setStyle(Notification.BigTextStyle().bigText("Reading or writing on paper counts. Tap to take a break or change the session."))
            .setOnlyAlertOnce(true)
            .setAutoCancel(true)
            .setContentIntent(PendingIntent.getActivity(ctx, 4208, Intent(ctx, MainActivity::class.java).addFlags(Intent.FLAG_ACTIVITY_SINGLE_TOP), PendingIntent.FLAG_IMMUTABLE))
            .addAction(Notification.Action.Builder(null, "Still working", action(ctx, "working", 4210)).build())
            .addAction(Notification.Action.Builder(null, "On paper", action(ctx, "paper", 4211)).build())
            .addAction(Notification.Action.Builder(null, "Remind me later", action(ctx, "later", 4212)).build())
            .build()
        runCatching { ctx.getSystemService(NotificationManager::class.java).notify(ID, n) }
    }

    fun cancel(ctx: Context) = ctx.getSystemService(NotificationManager::class.java).cancel(ID)
}

/** Records the answer. "Take a break" lives in the app (session options), not in a notification. */
class CheckInReceiver : BroadcastReceiver() {
    override fun onReceive(context: Context, intent: Intent) {
        val store = Store.get(context)
        val a = store.state.active ?: return CheckInNotice.cancel(context)
        val now = System.currentTimeMillis()
        val answer = intent.getStringExtra("answer") ?: return
        store.setCheckIn(a.id) { c ->
            when (answer) {
                "later" -> c.copy(snoozedUntil = now + 10 * 60_000L)
                else -> c.copy(answeredAt = now, answers = c.answers + (answer to ((c.answers[answer] ?: 0) + 1)))
            }
        }
        CheckInNotice.cancel(context)
    }
}
