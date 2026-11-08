// More home-screen widgets (owner 2026-11-04): a small Quick start lock and a Today summary.
// Both refresh with the main widget (LockdInWidget.update) and every 30 minutes.
package app.lockdin.widget

import android.app.PendingIntent
import android.appwidget.AppWidgetManager
import android.appwidget.AppWidgetProvider
import android.content.ComponentName
import android.content.Context
import android.content.Intent
import android.os.SystemClock
import android.view.View
import android.widget.RemoteViews
import app.lockdin.MainActivity
import app.lockdin.R
import app.lockdin.engine.History
import app.lockdin.state.HistoryStore
import app.lockdin.state.Store
import java.time.ZoneId

internal fun openApp(ctx: Context, route: String, code: Int): PendingIntent = PendingIntent.getActivity(
    ctx, code, Intent(ctx, MainActivity::class.java).putExtra(MainActivity.EXTRA_ROUTE, route)
        .addFlags(Intent.FLAG_ACTIVITY_NEW_TASK or Intent.FLAG_ACTIVITY_CLEAR_TOP or Intent.FLAG_ACTIVITY_SINGLE_TOP),
    PendingIntent.FLAG_IMMUTABLE or PendingIntent.FLAG_UPDATE_CURRENT,
)

private fun ids(ctx: Context, cls: Class<*>): IntArray =
    runCatching { AppWidgetManager.getInstance(ctx).getAppWidgetIds(ComponentName(ctx, cls)) }.getOrNull() ?: IntArray(0)

/** Quick start: the mini lock. Tap to set up a new session, or to return to the running one. */
class QuickStartWidget : AppWidgetProvider() {
    override fun onUpdate(context: Context, manager: AppWidgetManager, ids: IntArray) = update(context)

    companion object {
        fun update(ctx: Context) {
            val ids = ids(ctx, QuickStartWidget::class.java)
            if (ids.isEmpty()) return
            val now = System.currentTimeMillis()
            val c = LockdInWidget.content(Store.get(ctx).state, now)
            val running = c.countdownTo != null
            val v = RemoteViews(ctx.packageName, R.layout.widget_quick)
            v.setImageViewBitmap(R.id.quick_lock, LockdInWidget.miniLock(ctx, c.locked))
            if (running) {
                v.setViewVisibility(R.id.quick_countdown, View.VISIBLE)
                v.setChronometer(R.id.quick_countdown, SystemClock.elapsedRealtime() + (c.countdownTo!! - now), null, true)
                v.setChronometerCountDown(R.id.quick_countdown, true)
                v.setTextViewText(R.id.quick_label, c.label)
            } else {
                v.setChronometer(R.id.quick_countdown, SystemClock.elapsedRealtime(), null, false)
                v.setViewVisibility(R.id.quick_countdown, View.GONE)
                v.setTextViewText(R.id.quick_label, "Start")
            }
            v.setOnClickPendingIntent(R.id.quick_root, openApp(ctx, if (running) "focus" else "new", 42))
            v.setContentDescription(R.id.quick_root, if (running) "${c.label}. Open Lockd In" else "Start a focus session")
            runCatching { AppWidgetManager.getInstance(ctx).updateAppWidget(ids, v) }
        }
    }
}

/** Today: time focused today and this week, from History on this phone. */
class TodayWidget : AppWidgetProvider() {
    override fun onUpdate(context: Context, manager: AppWidgetManager, ids: IntArray) = update(context)

    companion object {
        fun minutes(m: Long): String = when {
            m < 60 -> "$m min"
            m % 60 == 0L -> "${m / 60} h"
            else -> "${m / 60} h ${m % 60} min"
        }

        fun update(ctx: Context) {
            val ids = ids(ctx, TodayWidget::class.java)
            if (ids.isEmpty()) return
            val now = System.currentTimeMillis()
            val zone = ZoneId.systemDefault()
            val st = Store.get(ctx).state
            val entries = HistoryStore.get(ctx).entries
            val today = History.dateOf(now, zone)
            val todays = entries.filter { History.dateOf(it.start, zone) == today }
            val todayMin = todays.sumOf { it.focusedMinutes }
            val week = History.week(entries, History.weekStart(today), zone, st.schedule, now)
            val v = RemoteViews(ctx.packageName, R.layout.widget_today)
            v.setTextViewText(R.id.today_value, minutes(todayMin))
            v.setTextViewText(R.id.today_detail, buildString {
                append("${todays.size} session${if (todays.size == 1) "" else "s"} · ${minutes(week.focusedMinutes)} this week")
                if (st.active != null) append(" · focusing now")
            })
            v.setOnClickPendingIntent(R.id.today_root, openApp(ctx, "history", 43))
            v.setContentDescription(R.id.today_root, "Focused today: ${minutes(todayMin)}. Open history")
            runCatching { AppWidgetManager.getInstance(ctx).updateAppWidget(ids, v) }
        }
    }
}
