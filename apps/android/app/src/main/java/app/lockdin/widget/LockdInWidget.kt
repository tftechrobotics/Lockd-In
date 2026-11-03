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
        data class Content(val label: String, val title: String, val detail: String, val countdownTo: Long?, val action: String?, val locked: Boolean = false)

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
                        ph.end, null, locked = snap.restricting && a.rules.restrictsAnything,
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

        /**
         * A small drawing of the main screen's lock: shackle (shut while blocking, open otherwise)
         * and a pale face with a ring of ticks. The countdown or clock sits on top as live text.
         */
        fun miniLock(ctx: Context, shut: Boolean): android.graphics.Bitmap {
            val d = ctx.resources.displayMetrics.density
            val w = (92 * d).toInt(); val h = (116 * d).toInt()
            val bmp = android.graphics.Bitmap.createBitmap(w, h, android.graphics.Bitmap.Config.ARGB_8888)
            val cv = android.graphics.Canvas(bmp)
            val dark = (ctx.resources.configuration.uiMode and android.content.res.Configuration.UI_MODE_NIGHT_MASK) == android.content.res.Configuration.UI_MODE_NIGHT_YES
            val metal = if (dark) 0xFF8A7F73.toInt() else 0xFFA79C90.toInt()
            val face = if (dark) 0xFF2E2823.toInt() else 0xFFF1ECE3.toInt()
            val rim = if (dark) 0xFF6E655B.toInt() else 0xFFBDB3A7.toInt()
            val tick = if (dark) 0xFF9C9083.toInt() else 0xFF8B8074.toInt()
            val p = android.graphics.Paint(android.graphics.Paint.ANTI_ALIAS_FLAG)
            val cx = w / 2f; val r = w * 0.46f; val cy = h - r - 2 * d
            // Shackle: a thick arc; open = raised and shifted, like the app's lock.
            p.style = android.graphics.Paint.Style.STROKE; p.strokeWidth = w * 0.12f; p.color = metal; p.strokeCap = android.graphics.Paint.Cap.ROUND
            val sw = r * 0.62f; val lift = if (shut) 0f else r * 0.28f
            val top = cy - r - r * 0.55f - lift
            val path = android.graphics.Path().apply {
                moveTo(cx - sw, cy - r * 0.55f - lift * (if (shut) 0f else 0.2f))
                lineTo(cx - sw, top + sw)
                arcTo(android.graphics.RectF(cx - sw, top, cx + sw, top + 2 * sw), 180f, 180f)
                lineTo(cx + sw, if (shut) cy - r * 0.55f else top + sw + r * 0.18f)
            }
            cv.drawPath(path, p)
            // Body: rim, face, ticks.
            p.style = android.graphics.Paint.Style.FILL; p.color = rim; cv.drawCircle(cx, cy, r, p)
            p.color = face; cv.drawCircle(cx, cy, r * 0.88f, p)
            p.style = android.graphics.Paint.Style.STROKE; p.color = tick
            for (i in 0 until 60) {
                val a = Math.toRadians(i * 6.0)
                val long = i % 5 == 0
                p.strokeWidth = if (long) 1.6f * d else 0.9f * d
                val r1 = r * 0.80f; val r2 = r * (if (long) 0.66f else 0.72f)
                cv.drawLine(cx + (r1 * Math.sin(a)).toFloat(), cy - (r1 * Math.cos(a)).toFloat(), cx + (r2 * Math.sin(a)).toFloat(), cy - (r2 * Math.cos(a)).toFloat(), p)
            }
            return bmp
        }

        /** The widget's views for `c` (also used by the screenshot test). */
        fun views(ctx: Context, c: Content, now: Long): RemoteViews {
            val v = RemoteViews(ctx.packageName, R.layout.widget_lockdin)
            v.setTextViewText(R.id.widget_label, c.label)
            v.setTextViewText(R.id.widget_title, c.title)
            v.setTextViewText(R.id.widget_detail, c.detail)
            v.setImageViewBitmap(R.id.widget_lock, miniLock(ctx, c.locked))
            v.setViewVisibility(R.id.widget_clock, if (c.countdownTo == null) View.VISIBLE else View.GONE)
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
