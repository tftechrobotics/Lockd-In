// Home-screen widget (roadmap step 4, C): the session running now with a live countdown that
// Android ticks itself (no per-second updates), or the next planned session. Tapping opens
// Lockd In; nothing starts from the widget without the app.
package app.lockdin.widget

import android.app.PendingIntent
import android.appwidget.AppWidgetManager
import android.appwidget.AppWidgetProvider
import android.content.ComponentName
import android.content.Context
import android.content.Intent
import android.os.SystemClock
import android.util.Log
import android.view.View
import android.widget.RemoteViews
import app.lockdin.MainActivity
import app.lockdin.R
import app.lockdin.engine.PhaseKind
import app.lockdin.engine.Schedule
import app.lockdin.engine.Session
import app.lockdin.engine.SessionStatus
import app.lockdin.state.Store
import java.text.DateFormat
import java.time.ZoneId
import java.util.Date

class LockdInWidget : AppWidgetProvider() {
    override fun onUpdate(context: Context, manager: AppWidgetManager, ids: IntArray) = update(context, force = true)

    companion object {
        private var lastKey: String? = null

        /** What the widget shows right now; changes only when its text would. */
        data class Content(val label: String, val title: String, val detail: String, val countdownTo: Long?, val action: String?)

        fun content(st: app.lockdin.state.AppState, now: Long, zone: ZoneId = ZoneId.systemDefault()): Content {
            val time = DateFormat.getTimeInstance(DateFormat.SHORT)
            st.active?.let { a ->
                val occ = Session.occurrenceFrom(a.start, a.segments)
                val snap = Session.evaluate(occ, a.controls, now)
                val ph = snap.phase
                if ((snap.status == SessionStatus.ACTIVE) && ph != null) {
                    val brk = ph.kind == PhaseKind.BREAK
                    return Content(
                        if (brk) "On a break" else "Focusing", a.intention ?: "Focus session",
                        if (brk) "Focus resumes at ${time.format(Date(ph.end))}" else "Ends at ${time.format(Date(occ.end))}",
                        ph.end, null,
                    )
                }
            }
            val next = Schedule.nextUp(st.schedule, now, zone)
            return if (next == null) Content("Next session", "Nothing planned", "Plan one in Lockd In", null, "New session")
            else Content(
                if (next.start <= now) "Scheduled now" else "Next session", next.session.title,
                "${app.lockdin.ui.Fmt.dayLabel(next.start, now, zone)} · ${time.format(Date(next.start))}–${time.format(Date(next.end))}" +
                    if (next.session.autoStart) " · starts automatically" else "",
                null, "New session",
            )
        }

        /** Refreshes every placed widget if what it shows changed (or `force`). Main thread. */
        fun update(ctx: Context, force: Boolean = false) {
            val mgr = AppWidgetManager.getInstance(ctx) ?: return
            val ids = runCatching { mgr.getAppWidgetIds(ComponentName(ctx, LockdInWidget::class.java)) }.getOrNull() ?: return
            if (ids.isEmpty()) return
            val now = System.currentTimeMillis()
            val c = content(Store.get(ctx).state, now)
            val key = c.copy(countdownTo = c.countdownTo?.div(1000)).toString()
            if (!force && key == lastKey) return
            lastKey = key
            val v = views(ctx, c, now)
            runCatching { mgr.updateAppWidget(ids, v) }.onFailure { Log.w("LockdInWidget", "update failed", it) }
            Log.i("LockdInWidget", "updated label=${c.label}")
        }

        /** The widget's views for `c` (also used by the screenshot test). */
        fun views(ctx: Context, c: Content, now: Long): RemoteViews {
            val v = RemoteViews(ctx.packageName, R.layout.widget_lockdin)
            v.setTextViewText(R.id.widget_label, c.label)
            v.setTextViewText(R.id.widget_title, c.title)
            v.setTextViewText(R.id.widget_detail, c.detail)
            v.setInt(R.id.widget_lock, "setColorFilter", ctx.getColor(R.color.widget_ink))
            if (c.countdownTo != null) {
                v.setViewVisibility(R.id.widget_countdown, View.VISIBLE)
                v.setChronometer(R.id.widget_countdown, SystemClock.elapsedRealtime() + (c.countdownTo - now), null, true)
                v.setChronometerCountDown(R.id.widget_countdown, true)
            } else {
                v.setChronometer(R.id.widget_countdown, SystemClock.elapsedRealtime(), null, false)
                v.setViewVisibility(R.id.widget_countdown, View.GONE)
            }
            v.setViewVisibility(R.id.widget_action, if (c.action != null) View.VISIBLE else View.GONE)
            c.action?.let { v.setTextViewText(R.id.widget_action, it) }
            val open = PendingIntent.getActivity(
                ctx, 41, Intent(ctx, MainActivity::class.java).putExtra(MainActivity.EXTRA_ROUTE, "focus")
                    .addFlags(Intent.FLAG_ACTIVITY_NEW_TASK or Intent.FLAG_ACTIVITY_CLEAR_TOP or Intent.FLAG_ACTIVITY_SINGLE_TOP),
                PendingIntent.FLAG_IMMUTABLE or PendingIntent.FLAG_UPDATE_CURRENT,
            )
            v.setOnClickPendingIntent(R.id.widget_root, open)
            v.setOnClickPendingIntent(R.id.widget_action, open)
            v.setContentDescription(R.id.widget_root, "${c.label}: ${c.title}. ${c.detail}")
            return v
        }
    }
}
