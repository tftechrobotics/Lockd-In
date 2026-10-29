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
import android.os.BatteryManager
import android.os.Build
import android.os.Handler
import android.os.IBinder
import android.os.Looper
import android.os.PowerManager
import org.json.JSONObject

/**
 * Foreground service that keeps the session alive, polls UsageEvents when that
 * detector is selected, re-verifies capabilities, and ends the session on time.
 */
class ProbeService : Service() {
    private val handler = Handler(Looper.getMainLooper())
    private var lastQueryMs = 0L
    private var lastCaps: String? = null
    private var ticks = 0L

    private val tick = object : Runnable {
        override fun run() {
            if (!ProbeSession.isActive(this@ProbeService)) {
                finish("time_up")
                return
            }
            ticks++
            val power = getSystemService(PowerManager::class.java)
            if (power.isInteractive && ProbeSession.detector(this@ProbeService) == ProbeSession.Detector.USAGE_STATS) {
                pollUsage()
            }
            if (ticks % 10 == 0L) checkCapabilities()
            handler.postDelayed(this, POLL_MS)
        }
    }

    override fun onBind(intent: Intent?): IBinder? = null

    override fun onStartCommand(intent: Intent?, flags: Int, startId: Int): Int {
        if (intent?.action == ACTION_STOP) {
            ProbeSession.end(this)
            finish("stopped_by_user")
            return START_NOT_STICKY
        }
        if (!ProbeSession.isActive(this)) {
            finish("not_active")
            return START_NOT_STICKY
        }
        startInForeground()
        // intent == null means the system restarted us after process death.
        ProbeLog.log(this, if (intent == null) "service_restarted" else "service_started",
            JSONObject().put("detector", ProbeSession.detector(this).name)
                .put("battery_pct", batteryPct()).put("caps", Capabilities.snapshot(this)))
        lastQueryMs = System.currentTimeMillis() - 10_000
        handler.removeCallbacks(tick)
        handler.post(tick)
        // Restart after process death while a session is active.
        return START_STICKY
    }

    override fun onTimeout(startId: Int, fgsType: Int) {
        ProbeLog.log(this, "fgs_timeout", JSONObject().put("type", fgsType))
        finish("fgs_timeout")
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
        ProbeLog.log(this, "session_released", JSONObject().put("reason", reason).put("battery_pct", batteryPct()))
        stopForeground(STOP_FOREGROUND_REMOVE)
        stopSelf()
    }

    private fun batteryPct(): Int =
        getSystemService(BatteryManager::class.java).getIntProperty(BatteryManager.BATTERY_PROPERTY_CAPACITY)

    private fun startInForeground() {
        val nm = getSystemService(NotificationManager::class.java)
        nm.createNotificationChannel(NotificationChannel(CHANNEL, "Focus session", NotificationManager.IMPORTANCE_LOW))
        val open = PendingIntent.getActivity(
            this, 0, Intent(this, MainActivity::class.java), PendingIntent.FLAG_IMMUTABLE
        )
        val stop = PendingIntent.getService(
            this, 1, Intent(this, ProbeService::class.java).setAction(ACTION_STOP), PendingIntent.FLAG_IMMUTABLE
        )
        val until = java.text.DateFormat.getTimeInstance(java.text.DateFormat.SHORT)
            .format(java.util.Date(ProbeSession.endsAtMs(this)))
        val n = Notification.Builder(this, CHANNEL)
            .setContentTitle("Focus probe running")
            .setContentText("Restricted apps are paused until $until")
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
        private const val POLL_MS = 500L
        const val ACTION_STOP = "app.focus.probe.STOP"

        fun start(ctx: Context) = ctx.startForegroundService(Intent(ctx, ProbeService::class.java))
        fun stop(ctx: Context) = ctx.startService(Intent(ctx, ProbeService::class.java).setAction(ACTION_STOP))
    }
}
