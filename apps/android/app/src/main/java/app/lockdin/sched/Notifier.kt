package app.lockdin.sched

import android.app.Notification
import android.app.NotificationChannel
import android.app.NotificationManager
import android.app.PendingIntent
import android.content.Context
import android.content.Intent
import app.lockdin.MainActivity
import app.lockdin.R

/**
 * Reminder and session notifications. Each kind has its own Android channel, so people can also
 * silence one kind in system settings; the app's own switches (Settings › Reminders) come first.
 * Nothing is posted without the notification permission; the app says so in Settings.
 */
object Notifier {
    const val CH_REMINDERS = "reminders"
    const val CH_EVENTS = "session_events"
    const val CH_FOLLOW_UP = "follow_up"

    fun channels(ctx: Context) {
        val nm = ctx.getSystemService(NotificationManager::class.java)
        nm.createNotificationChannel(NotificationChannel(CH_REMINDERS, "Session reminders", NotificationManager.IMPORTANCE_DEFAULT).apply {
            description = "Before a planned session, and when one is due to start"
        })
        nm.createNotificationChannel(NotificationChannel(CH_EVENTS, "Focus and breaks", NotificationManager.IMPORTANCE_DEFAULT).apply {
            description = "Focus starting, breaks starting and ending, session complete"
        })
        nm.createNotificationChannel(NotificationChannel(CH_FOLLOW_UP, "Missed sessions", NotificationManager.IMPORTANCE_LOW).apply {
            description = "One gentle follow-up after a planned session was missed"
        })
    }

    fun allowed(ctx: Context): Boolean = ctx.getSystemService(NotificationManager::class.java).areNotificationsEnabled()

    data class Action(val title: String, val intent: PendingIntent)

    /** The person's switches in Settings › Notifications, by channel. */
    fun permitted(ctx: Context, channel: String): Boolean {
        val kind = when (channel) {
            CH_REMINDERS -> app.lockdin.engine.NotifyKind.REMINDER
            CH_EVENTS -> app.lockdin.engine.NotifyKind.SESSION_EVENT
            CH_FOLLOW_UP -> app.lockdin.engine.NotifyKind.FOLLOW_UP
            "alerts" -> app.lockdin.engine.NotifyKind.BLOCKING_ALERT
            else -> return true
        }
        return app.lockdin.state.Store.get(ctx).state.reminders.permits(kind)
    }

    fun post(ctx: Context, id: Int, channel: String, title: String, text: String, actions: List<Action> = emptyList(), openRoute: String? = null) {
        channels(ctx)
        if (!allowed(ctx) || !permitted(ctx, channel)) return
        val open = PendingIntent.getActivity(
            ctx, id, Intent(ctx, MainActivity::class.java).addFlags(Intent.FLAG_ACTIVITY_SINGLE_TOP)
                .apply { if (openRoute != null) putExtra(MainActivity.EXTRA_ROUTE, openRoute) },
            PendingIntent.FLAG_IMMUTABLE or PendingIntent.FLAG_UPDATE_CURRENT,
        )
        val n = Notification.Builder(ctx, channel)
            .setSmallIcon(R.drawable.ic_stat_lock)
            .setContentTitle(title)
            .setContentText(text)
            .setStyle(Notification.BigTextStyle().bigText(text))
            .setContentIntent(open)
            .setAutoCancel(true)
            .apply { actions.forEach { addAction(Notification.Action.Builder(null, it.title, it.intent).build()) } }
            .build()
        runCatching { ctx.getSystemService(NotificationManager::class.java).notify(id, n) }
    }

    fun cancel(ctx: Context, id: Int) {
        ctx.getSystemService(NotificationManager::class.java).cancel(id)
    }

    /** Stable id per occurrence so a reminder and its follow-ups replace each other. */
    fun idFor(key: String): Int = 1000 + (key.hashCode() and 0x3fffffff) % 1_000_000
}
