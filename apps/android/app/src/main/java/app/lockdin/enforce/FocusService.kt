package app.lockdin.enforce

import android.app.AlarmManager
import android.app.Notification
import android.app.NotificationChannel
import android.app.NotificationManager
import android.app.PendingIntent
import android.app.Service
import android.app.usage.UsageEvents
import android.app.usage.UsageStatsManager
import android.content.BroadcastReceiver
import android.content.Context
import android.content.Intent
import android.content.IntentFilter
import android.content.pm.ServiceInfo
import android.os.Build
import android.os.Handler
import android.os.IBinder
import android.os.Looper
import android.os.PowerManager
import android.os.SystemClock
import android.util.Log
import androidx.core.content.ContextCompat
import app.lockdin.MainActivity
import app.lockdin.R
import app.lockdin.engine.OverrideState
import app.lockdin.engine.PhaseKind
import app.lockdin.engine.Session
import app.lockdin.engine.SessionStatus
import app.lockdin.engine.Snapshot
import app.lockdin.state.ActiveSession
import app.lockdin.state.AppState
import app.lockdin.state.Store
import java.text.DateFormat
import java.util.Date

/**
 * Runs a focus session's blocking independently of the app window.
 *
 * What it does, exactly: while a focus period is due and the session's rules restrict
 * something, it reads which app is in front from usage access (UsageEvents) about twice a
 * second while the screen is on. When that app is restricted it brings Lockd In's block
 * screen to the front (allowed from the background by "Display over other apps"). The
 * restricted app has already opened; it is moved behind the block screen, not closed, and
 * nothing is killed. Breaks, unlocked rounds and the end of the session release it.
 *
 * What it can't do: stop an app from launching; block a site inside an allowed browser;
 * keep working after the user force-stops Lockd In or revokes a permission (both are
 * detected and reported, not prevented).
 *
 * State lives in [Store] (persisted on every change), so after process death or a reboot
 * the service picks the session up again from disk.
 */
class FocusService : Service() {
    private val handler = Handler(Looper.getMainLooper())
    private var lastQueryMs = 0L
    private var currentTop: String? = null
    private var currentTopAt = 0L
    private var lastCoverElapsed = 0L
    private var wasRestricting = false
    private var essentials: Set<String> = emptySet()
    private var essentialsAt = 0L
    private var lastNotice: String? = null
    private var lastMissing: Set<Capability> = emptySet()
    private var screenReceiver: BroadcastReceiver? = null
    private var foreground = false

    private val tick = Runnable { runTick() }

    override fun onBind(intent: Intent?): IBinder? = null

    override fun onCreate() {
        super.onCreate()
        isRunning = true
        createChannels(this)
        val r = object : BroadcastReceiver() {
            override fun onReceive(c: Context, i: Intent) = kick()
        }
        val filter = IntentFilter().apply {
            addAction(Intent.ACTION_SCREEN_ON)
            addAction(Intent.ACTION_USER_PRESENT)
            addAction(Intent.ACTION_TIME_CHANGED)
            addAction(Intent.ACTION_TIMEZONE_CHANGED)
        }
        ContextCompat.registerReceiver(this, r, filter, ContextCompat.RECEIVER_NOT_EXPORTED)
        screenReceiver = r
    }

    override fun onStartCommand(intent: Intent?, flags: Int, startId: Int): Int {
        val store = Store.get(this)
        if (!shouldRun(store.state, System.currentTimeMillis())) {
            stop("not_running")
            return START_NOT_STICKY
        }
        // Called for every start request: Android expects startForeground after each
        // startForegroundService, and it refreshes the notification.
        run {
            try {
                val a = store.state.active!!
                val n = notification(a, evaluate(a, System.currentTimeMillis()), Protection.NOT_RESTRICTING, emptySet())
                // With ambient sound the service also plays media (declared type mediaPlayback).
                val types = ServiceInfo.FOREGROUND_SERVICE_TYPE_SPECIAL_USE or
                    (if (a.audio.ambient != app.lockdin.audio.Ambient.NONE) ServiceInfo.FOREGROUND_SERVICE_TYPE_MEDIA_PLAYBACK else 0)
                if (Build.VERSION.SDK_INT >= 34) startForeground(ONGOING_ID, n, types)
                else startForeground(ONGOING_ID, n)
                foreground = true
            } catch (e: Exception) {
                // Android may refuse a foreground start from the background (for example a
                // restart after the process was killed). Say so instead of failing silently.
                Log.w(TAG, "startForeground refused: ${e.javaClass.simpleName}")
                alert(this, "Blocking stopped", "Android stopped Lockd In. Open it to resume blocking for this session.")
                stopSelf()
                return START_NOT_STICKY
            }
        }
        Log.i(TAG, if (intent == null) "restarted_by_system" else "started")
        kick()
        // Ask Android to restart the service if the process dies while a session is running.
        return START_STICKY
    }

    override fun onTimeout(startId: Int, fgsType: Int) {
        // specialUse has no time limit today; handle the callback anyway so we never hang.
        Log.w(TAG, "fgs_timeout")
        stop("fgs_timeout")
    }

    override fun onDestroy() {
        isRunning = false
        handler.removeCallbacks(tick)
        screenReceiver?.let { runCatching { unregisterReceiver(it) } }
        super.onDestroy()
    }

    private fun kick() {
        handler.removeCallbacks(tick)
        handler.post(tick)
    }

    private fun runTick() {
        val now = System.currentTimeMillis()
        val session = Store.get(this).state.active
        val snap = session?.let { evaluate(it, now) }
        if (session == null || snap == null || !running(snap)) {
            if (session != null && snap?.status == SessionStatus.COMPLETED) sessionEvent(session, "end") {
                val occ = Session.occurrenceFrom(session.start, session.segments)
                val mins = occ.phases.filter { it.kind == PhaseKind.FOCUS }.sumOf { it.end - it.start } / 60_000
                "Session complete" to "${session.intention ?: "Focus session"} · focused $mins min."
            }
            stop(if (session == null) "no_session" else "session_over")
            return
        }
        // Break starting / focus back on: one notice per phase change (not the first phase).
        snap.phase?.takeIf { it.index > 0 }?.let { ph ->
            sessionEvent(session, "phase${ph.index}") {
                val at = DateFormat.getTimeInstance(DateFormat.SHORT).format(Date(ph.end))
                if (ph.kind == PhaseKind.BREAK) "Break until $at" to "Apps are available. Focus resumes at $at."
                else "Focus is back on" to "${session.intention ?: "Your session"} until $at."
            }
        }
        // Study audio (B2) follows the session: on in focus, paused for breaks unless kept.
        app.lockdin.audio.StudyAudio.follow(
            this, session, inFocus = snap.phase?.kind == PhaseKind.FOCUS, inBreak = snap.phase?.kind == PhaseKind.BREAK,
        )
        val (detection, appMissing) = Capabilities.detection(this)
        val missing = BlockPolicy.missingFor(session.rules, detection, appMissing)
        val protection = BlockPolicy.protection(true, snap.restricting, session.rules, missing)
        EnforcerStatus.publish(EnforcerStatus.Report(session.id, protection, missing))
        updateNotification(session, snap, protection, missing)

        val interactive = getSystemService(PowerManager::class.java).isInteractive
        if (protection == Protection.PROTECTING) {
            if (!wasRestricting) {
                // A focus period just began: look back so an app already open is covered too.
                lastQueryMs = now - LOOKBACK_MS
                currentTop = null
                currentTopAt = 0L
            }
            // With the accessibility service connected it covers apps itself, instantly;
            // polling usage events is then only a fallback for when it isn't bound.
            if (interactive && session.rules.restrictsApps && !FocusAccessibilityService.connected) {
                pollForeground(now)
                currentTop?.let { top ->
                    if (BlockPolicy.isRestricted(top, session.rules, essentials(now))) cover(top)
                }
            }
        }
        wasRestricting = protection == Protection.PROTECTING

        scheduleWake(snap.nextTransition)
        handler.postDelayed(tick, if (interactive && protection == Protection.PROTECTING && !FocusAccessibilityService.connected) POLL_MS else IDLE_MS)
    }

    /** Posts a session notice once (survives service restarts via the plan log). */
    private fun sessionEvent(session: ActiveSession, what: String, text: () -> Pair<String, String>) {
        val store = Store.get(this)
        val id = "evt:${session.id}:$what"
        if (id in store.state.planLog) return
        store.logPlan(listOf(id))
        if (!store.state.reminders.sessionEventsOn) return
        val (title, body) = text()
        app.lockdin.sched.Notifier.post(this, EVENT_ID, app.lockdin.sched.Notifier.CH_EVENTS, title, body)
    }

    private fun essentials(now: Long): Set<String> {
        if (now - essentialsAt > 60_000) {
            essentials = Capabilities.essentials(this)
            essentialsAt = now
        }
        return essentials
    }

    private fun pollForeground(now: Long) {
        val usm = getSystemService(UsageStatsManager::class.java)
        // Events are stamped when they happen but can appear in queries a little later. Re-read
        // an overlapping window each time and keep the newest by timestamp, so a late event is
        // never skipped (skipping one left a blocked app uncovered until the next app switch).
        val from = minOf(lastQueryMs, now - OVERLAP_MS)
        val events = runCatching { usm.queryEvents(from, now) }.getOrNull() ?: return
        lastQueryMs = now
        val e = UsageEvents.Event()
        while (events.hasNextEvent()) {
            events.getNextEvent(e)
            if (e.eventType == UsageEvents.Event.ACTIVITY_RESUMED && e.timeStamp >= currentTopAt) {
                currentTop = e.packageName
                currentTopAt = e.timeStamp
            }
        }
    }

    private fun cover(pkg: String) {
        val t = SystemClock.elapsedRealtime()
        if (BlockActivity.showing || t - lastCoverElapsed < COVER_DEBOUNCE_MS) return
        lastCoverElapsed = t
        try {
            startActivity(BlockActivity.intent(this, pkg))
            Store.get(this).state.active?.let { Store.get(this).recordBlocked(it.id, pkg) }
            // Time from the app coming to the front (as Android recorded it) to our request.
            Log.i(TAG, "cover_requested detect_ms=${System.currentTimeMillis() - currentTopAt}")
        } catch (e: Exception) {
            // Background activity starts can be refused; the next tick retries and the
            // permission check reports "Display over other apps" if that is the cause.
            Log.w(TAG, "cover_failed: ${e.javaClass.simpleName}")
        }
    }

    private fun scheduleWake(at: Long?) {
        val am = getSystemService(AlarmManager::class.java)
        val pi = PendingIntent.getForegroundService(
            this, 0, Intent(this, FocusService::class.java).setAction(ACTION_TICK),
            PendingIntent.FLAG_IMMUTABLE or PendingIntent.FLAG_UPDATE_CURRENT,
        )
        if (at == null) { am.cancel(pi); return }
        // Inexact but allowed in Doze, no special permission. Ticks also run on screen-on,
        // so a late alarm only delays the notification text, never protection while in use.
        am.setAndAllowWhileIdle(AlarmManager.RTC_WAKEUP, at + 500, pi)
    }

    private fun updateNotification(session: ActiveSession, snap: Snapshot, p: Protection, missing: Set<Capability>) {
        val key = "${p.name}|${snap.phase?.index}|${snap.overrideState.javaClass.simpleName}|$missing"
        if (key != lastNotice) {
            lastNotice = key
            getSystemService(NotificationManager::class.java).notify(ONGOING_ID, notification(session, snap, p, missing))
        }
        if (p == Protection.PERMISSION_NEEDED && lastMissing.isEmpty()) {
            alert(this, "Blocking paused", "${describe(missing)} is off, so apps aren’t blocked. Tap to fix it.")
        } else if (p != Protection.PERMISSION_NEEDED && lastMissing.isNotEmpty()) {
            getSystemService(NotificationManager::class.java).cancel(ALERT_ID)
        }
        lastMissing = if (p == Protection.PERMISSION_NEEDED) missing else emptySet()
    }

    private fun notification(session: ActiveSession, snap: Snapshot?, p: Protection, missing: Set<Capability>): Notification {
        val until = snap?.phase?.end?.let { DateFormat.getTimeInstance(DateFormat.SHORT).format(Date(it)) }
        val (title, text) = when {
            snap == null || until == null -> "Focus session" to "Starting…"
            p == Protection.PERMISSION_NEEDED -> "Blocking paused" to "Turn on ${describe(missing)} to block apps."
            p == Protection.PROTECTING -> "Focusing until $until" to "Blocked apps are covered by Lockd In."
            snap.phase?.kind == PhaseKind.BREAK -> "Break until $until" to "Apps are available. Blocking returns at $until."
            snap.overrideState is OverrideState.ActiveForFocusPeriod -> "Unlocked until $until" to "Apps are available this round."
            !session.rules.restrictsAnything -> "Focusing until $until" to "Timer only. No apps are blocked on this phone."
            else -> "Focusing until $until" to "Apps are available."
        }
        return Notification.Builder(this, CHANNEL_SESSION)
            .setContentTitle(title)
            .setContentText(text)
            .setSmallIcon(R.drawable.ic_stat_lock)
            .setContentIntent(openApp(this))
            .setOngoing(true)
            .setOnlyAlertOnce(true)
            .setCategory(Notification.CATEGORY_SERVICE)
            .apply { if (Build.VERSION.SDK_INT >= 31) setForegroundServiceBehavior(Notification.FOREGROUND_SERVICE_IMMEDIATE) }
            .build()
    }

    private fun stop(reason: String) {
        Log.i(TAG, "released: $reason")
        app.lockdin.audio.StudyAudio.follow(this, null, inFocus = false, inBreak = false)
        handler.removeCallbacks(tick)
        scheduleWake(null)
        EnforcerStatus.publish(EnforcerStatus.Report(null, Protection.IDLE, emptySet()))
        getSystemService(NotificationManager::class.java).cancel(ALERT_ID)
        if (foreground) stopForeground(STOP_FOREGROUND_REMOVE)
        foreground = false
        stopSelf()
    }

    companion object {
        private const val TAG = "LockdInFocus"
        private const val CHANNEL_SESSION = "session"
        private const val CHANNEL_ALERTS = "alerts"
        private const val ONGOING_ID = 1
        private const val ALERT_ID = 2
        private const val EVENT_ID = 3
        private const val POLL_MS = 500L
        private const val IDLE_MS = 2_000L
        private const val COVER_DEBOUNCE_MS = 400L
        private const val LOOKBACK_MS = 30 * 60_000L
        private const val OVERLAP_MS = 10_000L
        const val ACTION_TICK = "app.lockdin.TICK"

        /** This process's service instance exists. Process death resets it. */
        @Volatile var isRunning = false
            private set

        fun evaluate(s: ActiveSession, now: Long): Snapshot =
            Session.evaluate(Session.occurrenceFrom(s.start, s.segments), s.controls, now)

        private fun running(snap: Snapshot) = snap.status == SessionStatus.ACTIVE || snap.status == SessionStatus.UPCOMING

        /** True while the stored session still has time to run. */
        fun shouldRun(state: AppState, now: Long): Boolean = state.active?.let { running(evaluate(it, now)) } ?: false

        /**
         * Starts the service when a session is running, so blocking doesn't depend on the
         * window. Safe to call repeatedly. Called from the foreground (UI), from boot and from
         * the store's change hook.
         */
        fun sync(ctx: Context) {
            if (isRunning || !shouldRun(Store.get(ctx).state, System.currentTimeMillis())) return
            try {
                ctx.startForegroundService(Intent(ctx, FocusService::class.java))
            } catch (e: Exception) {
                Log.w(TAG, "sync refused: ${e.javaClass.simpleName}")
            }
        }

        fun describe(missing: Set<Capability>): String = missing.joinToString(" and ") {
            when (it) {
                Capability.USAGE_ACCESS -> "Usage access"
                Capability.DISPLAY_OVER_APPS -> "Display over other apps"
                Capability.ACCESSIBILITY -> "Accessibility"
            }
        }.ifEmpty { "a permission" }

        fun createChannels(ctx: Context) {
            val nm = ctx.getSystemService(NotificationManager::class.java)
            nm.createNotificationChannel(NotificationChannel(CHANNEL_SESSION, "Focus session", NotificationManager.IMPORTANCE_LOW).apply {
                description = "Shows while a session is running and what is blocked"
            })
            nm.createNotificationChannel(NotificationChannel(CHANNEL_ALERTS, "Blocking problems", NotificationManager.IMPORTANCE_DEFAULT).apply {
                description = "When blocking stops working, for example after a permission is turned off"
            })
        }

        fun openApp(ctx: Context): PendingIntent = PendingIntent.getActivity(
            ctx, 0, Intent(ctx, MainActivity::class.java).addFlags(Intent.FLAG_ACTIVITY_SINGLE_TOP),
            PendingIntent.FLAG_IMMUTABLE,
        )

        fun alert(ctx: Context, title: String, text: String) {
            createChannels(ctx)
            if (!app.lockdin.sched.Notifier.permitted(ctx, CHANNEL_ALERTS)) return
            val n = Notification.Builder(ctx, CHANNEL_ALERTS)
                .setContentTitle(title)
                .setContentText(text)
                .setStyle(Notification.BigTextStyle().bigText(text))
                .setSmallIcon(R.drawable.ic_stat_lock)
                .setContentIntent(openApp(ctx))
                .setAutoCancel(true)
                .build()
            runCatching { ctx.getSystemService(NotificationManager::class.java).notify(ALERT_ID, n) }
        }
    }
}
