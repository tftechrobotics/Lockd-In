package app.focus.probe

import android.app.Notification
import android.app.NotificationChannel
import android.app.NotificationManager
import android.app.PendingIntent
import android.app.Service
import android.app.usage.UsageEvents
import android.app.usage.UsageStatsManager
import android.content.Context
import android.content.Intent
import android.content.pm.ServiceInfo
import android.os.Build
import android.os.Handler
import android.os.IBinder
import android.os.Looper
import android.os.PowerManager
import org.json.JSONObject

/**
 * Foreground service that keeps a probe session alive, polls UsageEvents when
 * that detector is selected, re-verifies capabilities every few seconds, logs
 * battery for baseline comparison, and always releases on time.
 */
class ProbeService : Service() {
    private val handler = Handler(Looper.getMainLooper())
    private var lastQueryMs = 0L
    private var lastCaps: String? = null
    private var ticks = 0L
    private var lastTickWallMs = 0L

    private val tick = object : Runnable {
        override fun run() {
            val ctx = this@ProbeService
            if (!ProbeSession.isActive(ctx)) {
                finish("time_up")
                return
            }
            ticks++
            val now = System.currentTimeMillis()
            if (lastTickWallMs != 0L && now - lastTickWallMs > 30_000) {
                // The device slept or the process was frozen longer than expected.
                ProbeLog.log(ctx, "tick_gap", JSONObject().put("gap_ms", now - lastTickWallMs))
            }
            lastTickWallMs = now
            val interactive = getSystemService(PowerManager::class.java).isInteractive
            if (interactive && ProbeSession.detector(ctx) == ProbeSession.Detector.USAGE_STATS) pollUsage()
            val pollMs = ProbeSession.pollMs(ctx)
            if (ticks % (5_000 / pollMs).coerceAtLeast(1) == 0L) checkCapabilities()
            if (ticks % (300_000 / pollMs).coerceAtLeast(1) == 0L) ProbeLog.log(ctx, "battery", Battery.snapshot(ctx))
            handler.postDelayed(this, pollMs)
        }
    }

    override fun onBind(intent: Intent?): IBinder? = null

    override fun onStartCommand(intent: Intent?, flags: Int, startId: Int): Int {
        if (intent?.action == ACTION_STOP) {
            ProbeSession.end(this)
            finish("ended_by_user")
            return START_NOT_STICKY
        }
        if (!ProbeSession.isActive(this)) {
            finish("not_active")
            return START_NOT_STICKY
        }
        startInForeground()
        ProbeLog.log(
            this, if (intent == null) "service_restarted_by_system" else "service_started",
            JSONObject()
                .put("detector", ProbeSession.detector(this).name)
                .put("cover", ProbeSession.cover(this).name)
                .put("poll_ms", ProbeSession.pollMs(this))
                .put("battery", Battery.snapshot(this))
                .put("caps", Capabilities.snapshot(this))
        )
        lastQueryMs = System.currentTimeMillis() - 10_000
        handler.removeCallbacks(tick)
        handler.post(tick)
        return START_STICKY // ask to be restarted after process death while a session is active
    }

    override fun onTimeout(startId: Int, fgsType: Int) {
        ProbeLog.log(this, "fgs_timeout", JSONObject().put("type", fgsType))
        finish("fgs_timeout")
    }

    override fun onTaskRemoved(rootIntent: Intent?) {
        ProbeLog.log(this, "task_removed")
        super.onTaskRemoved(rootIntent)
    }

    override fun onDestroy() {
        handler.removeCallbacks(tick)
        Shield.hide()
        super.onDestroy()
    }

    private fun pollUsage() {
        val usm = getSystemService(UsageStatsManager::class.java)
        val now = System.currentTimeMillis()
        val events = usm.queryEvents(lastQueryMs, now) ?: return
        lastQueryMs = now
        val e = UsageEvents.Event()
        var pkg: String? = null
        var ts = 0L
        while (events.hasNextEvent()) {
            events.getNextEvent(e)
            if (e.eventType == UsageEvents.Event.ACTIVITY_RESUMED) {
                pkg = e.packageName
                ts = e.timeStamp
            }
        }
        pkg?.let { Enforcer.onForeground(this, it, ProbeSession.Detector.USAGE_STATS, now - ts) }
    }

    private fun checkCapabilities() {
        val caps = Capabilities.snapshot(this).toString()
        if (caps != lastCaps) {
            ProbeLog.log(this, "capabilities_changed", JSONObject(caps))
            lastCaps = caps
        }
    }

    private fun finish(reason: String) {
        handler.removeCallbacks(tick)
        Shield.hide()
        ProbeLog.log(this, "session_released", JSONObject().put("reason", reason).put("battery", Battery.snapshot(this)))
        stopForeground(STOP_FOREGROUND_REMOVE)
        stopSelf()
    }

    private fun startInForeground() {
        val nm = getSystemService(NotificationManager::class.java)
        nm.createNotificationChannel(NotificationChannel(CHANNEL, "Focus session", NotificationManager.IMPORTANCE_LOW))
        val open = PendingIntent.getActivity(this, 0, Intent(this, MainActivity::class.java), PendingIntent.FLAG_IMMUTABLE)
        val stop = PendingIntent.getService(
            this, 1, Intent(this, ProbeService::class.java).setAction(ACTION_STOP), PendingIntent.FLAG_IMMUTABLE
        )
        val until = java.text.DateFormat.getTimeInstance(java.text.DateFormat.SHORT)
            .format(java.util.Date(ProbeSession.endsAtMs(this)))
        val baseline = ProbeSession.detector(this) == ProbeSession.Detector.NONE_BASELINE
        val n = Notification.Builder(this, CHANNEL)
            .setContentTitle(if (baseline) "Focus probe baseline run" else "Focus probe session")
            .setContentText(if (baseline) "Measuring battery only, nothing is blocked. Ends $until" else "Restricted apps are blocked until $until")
            .setSmallIcon(android.R.drawable.ic_lock_idle_lock)
            .setContentIntent(open)
            .addAction(Notification.Action.Builder(null, "End now", stop).build())
            .setOngoing(true)
            .build()
        if (Build.VERSION.SDK_INT >= 34) {
            startForeground(NOTIFICATION_ID, n, ServiceInfo.FOREGROUND_SERVICE_TYPE_SPECIAL_USE)
        } else {
            startForeground(NOTIFICATION_ID, n)
        }
    }

    companion object {
        private const val CHANNEL = "session"
        private const val NOTIFICATION_ID = 1
        const val ACTION_STOP = "app.focus.probe.STOP"

        fun start(ctx: Context) {
            ctx.startForegroundService(Intent(ctx, ProbeService::class.java))
        }

        fun stop(ctx: Context) {
            ctx.startService(Intent(ctx, ProbeService::class.java).setAction(ACTION_STOP))
        }
    }
}
