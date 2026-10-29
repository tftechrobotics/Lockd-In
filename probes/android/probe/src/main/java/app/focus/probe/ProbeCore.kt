package app.focus.probe

import android.app.AppOpsManager
import android.content.Context
import android.content.Intent
import android.content.pm.PackageManager
import android.media.AudioManager
import android.os.BatteryManager
import android.os.Build
import android.os.Handler
import android.os.Looper
import android.os.PowerManager
import android.os.Process
import android.provider.Settings
import org.json.JSONObject
import java.io.File

/** Probe session settings. Persisted so they survive process death. */
object ProbeSession {
    private const val PREFS = "probe"
    const val TARGET_PACKAGE = "app.focus.probetarget"

    enum class Detector { USAGE_STATS, ACCESSIBILITY, NONE_BASELINE }

    /** What happens when a restricted app comes to the front. */
    enum class Cover {
        /** Draw a full-screen overlay. The app stays resumed underneath. */
        OVERLAY,
        /** Bring the probe's own block screen to the front. The app moves to the background. */
        BLOCK_SCREEN,
        /** Send the user to the home screen. The app moves to the background. */
        HOME,
    }

    fun prefs(ctx: Context) = ctx.getSharedPreferences(PREFS, Context.MODE_PRIVATE)

    fun blocked(ctx: Context): Set<String> = prefs(ctx).getStringSet("blocked", emptySet()) ?: emptySet()
    fun endsAtMs(ctx: Context): Long = prefs(ctx).getLong("ends_at", 0L)
    fun pollMs(ctx: Context): Long = prefs(ctx).getLong("poll_ms", 500L)
    fun requestAudioFocus(ctx: Context): Boolean = prefs(ctx).getBoolean("audio_focus", false)

    fun detector(ctx: Context): Detector =
        runCatching { Detector.valueOf(prefs(ctx).getString("detector", null) ?: "") }
            .getOrDefault(Detector.USAGE_STATS)

    fun cover(ctx: Context): Cover =
        runCatching { Cover.valueOf(prefs(ctx).getString("cover", null) ?: "") }
            .getOrDefault(Cover.BLOCK_SCREEN)

    /** Wall-clock end. Probe sessions are capped at 60 minutes. */
    fun isActive(ctx: Context): Boolean = System.currentTimeMillis() < endsAtMs(ctx)

    fun start(
        ctx: Context,
        blocked: Set<String>,
        minutes: Int,
        detector: Detector,
        cover: Cover,
        pollMs: Long,
        audioFocus: Boolean,
    ) {
        prefs(ctx).edit()
            .putStringSet("blocked", blocked)
            .putLong("ends_at", System.currentTimeMillis() + minutes.coerceIn(1, 60) * 60_000L)
            .putString("detector", detector.name)
            .putString("cover", cover.name)
            .putLong("poll_ms", pollMs.coerceIn(100L, 5_000L))
            .putBoolean("audio_focus", audioFocus)
            .apply()
    }

    fun end(ctx: Context) {
        prefs(ctx).edit().putLong("ends_at", 0L).apply()
    }
}

/** Capability checks. Re-verified while running — never assumed from onboarding. */
object Capabilities {
    fun usageAccess(ctx: Context): Boolean {
        val ops = ctx.getSystemService(AppOpsManager::class.java)
        val mode = ops.unsafeCheckOpNoThrow(AppOpsManager.OPSTR_GET_USAGE_STATS, Process.myUid(), ctx.packageName)
        return mode == AppOpsManager.MODE_ALLOWED
    }

    fun overlay(ctx: Context): Boolean = Settings.canDrawOverlays(ctx)

    fun accessibilityEnabled(ctx: Context): Boolean {
        val enabled = Settings.Secure.getString(ctx.contentResolver, Settings.Secure.ENABLED_ACCESSIBILITY_SERVICES)
            ?: return false
        return enabled.split(':').any { it.startsWith(ctx.packageName + "/") }
    }

    fun notifications(ctx: Context): Boolean =
        Build.VERSION.SDK_INT < 33 ||
            ctx.checkSelfPermission(android.Manifest.permission.POST_NOTIFICATIONS) == PackageManager.PERMISSION_GRANTED

    fun batteryUnrestricted(ctx: Context): Boolean =
        ctx.getSystemService(PowerManager::class.java).isIgnoringBatteryOptimizations(ctx.packageName)

    fun homePackage(ctx: Context): String? {
        val home = Intent(Intent.ACTION_MAIN).addCategory(Intent.CATEGORY_HOME)
        return ctx.packageManager.resolveActivity(home, PackageManager.MATCH_DEFAULT_ONLY)?.activityInfo?.packageName
    }

    fun inputMethodPackage(ctx: Context): String? =
        Settings.Secure.getString(ctx.contentResolver, Settings.Secure.DEFAULT_INPUT_METHOD)?.substringBefore('/')

    fun snapshot(ctx: Context): JSONObject = JSONObject()
        .put("usage_access", usageAccess(ctx))
        .put("overlay", overlay(ctx))
        .put("accessibility", accessibilityEnabled(ctx))
        .put("notifications", notifications(ctx))
        .put("battery_unrestricted", batteryUnrestricted(ctx))
}

object Battery {
    fun snapshot(ctx: Context): JSONObject {
        val bm = ctx.getSystemService(BatteryManager::class.java)
        return JSONObject()
            .put("pct", bm.getIntProperty(BatteryManager.BATTERY_PROPERTY_CAPACITY))
            // Finer-grained than percent where the device supports it (µAh; may be 0 or negative if unsupported).
            .put("charge_counter_uah", bm.getIntProperty(BatteryManager.BATTERY_PROPERTY_CHARGE_COUNTER))
            .put("charging", bm.isCharging)
    }
}

/**
 * JSON-lines log in app-private storage. Contains package names and timings,
 * which can reveal which apps someone uses. Nothing leaves the phone unless the
 * user taps "Share log".
 */
object ProbeLog {
    private fun file(ctx: Context) = File(ctx.filesDir, "probe.jsonl")

    @Synchronized
    fun log(ctx: Context, event: String, data: JSONObject = JSONObject()) {
        val line = JSONObject()
            .put("t", System.currentTimeMillis())
            .put("elapsed_ms", android.os.SystemClock.elapsedRealtime())
            .put("evt", event)
            .put("sdk", Build.VERSION.SDK_INT)
            .put("data", data)
        file(ctx).appendText(line.toString() + "\n")
    }

    fun tail(ctx: Context, lines: Int = 30): String =
        file(ctx).takeIf { it.exists() }?.readLines()?.takeLast(lines)?.joinToString("\n") ?: "(empty)"

    fun all(ctx: Context): String = file(ctx).takeIf { it.exists() }?.readText() ?: ""

    fun clear(ctx: Context) {
        file(ctx).delete()
    }
}

/** Decides what to do when an app comes to the front. Main thread only. */
object Enforcer {
    private val handler = Handler(Looper.getMainLooper())
    private val alwaysAllowed = setOf("com.android.systemui", "android")
    private var lastAction: Pair<String, Long>? = null

    fun onForeground(ctx: Context, pkg: String, source: ProbeSession.Detector, latencyMs: Long) {
        val app = ctx.applicationContext
        if (pkg == app.packageName || pkg in alwaysAllowed || pkg == Capabilities.inputMethodPackage(app)) return
        val restricted = ProbeSession.isActive(app) &&
            pkg in ProbeSession.blocked(app) &&
            pkg != Capabilities.homePackage(app)

        if (!restricted) {
            if (Shield.isShowing()) {
                Shield.hide()
                ProbeLog.log(app, "overlay_hidden", JSONObject().put("pkg", pkg).put("source", source.name))
            }
            return
        }

        // Debounce: several window events can arrive for one app switch.
        val now = android.os.SystemClock.elapsedRealtime()
        lastAction?.let { (p, t) -> if (p == pkg && now - t < 800) return }
        lastAction = pkg to now

        val cover = ProbeSession.cover(app)
        val ok = when (cover) {
            ProbeSession.Cover.OVERLAY -> Shield.show(app, pkg)
            ProbeSession.Cover.BLOCK_SCREEN -> launch(app, BlockActivity.intent(app, pkg))
            ProbeSession.Cover.HOME -> launch(
                app,
                Intent(Intent.ACTION_MAIN).addCategory(Intent.CATEGORY_HOME).addFlags(Intent.FLAG_ACTIVITY_NEW_TASK),
            )
        }
        ProbeLog.log(
            app, "restricted_foreground",
            JSONObject().put("pkg", pkg).put("source", source.name).put("cover", cover.name)
                .put("detect_latency_ms", latencyMs).put("cover_ok", ok)
        )
        // Covering is not pausing: check whether any audio is still playing afterwards.
        handler.postDelayed({
            val audio = app.getSystemService(AudioManager::class.java)
            ProbeLog.log(app, "audio_after_cover", JSONObject().put("pkg", pkg).put("music_active", audio.isMusicActive))
        }, 1_500)
    }

    /** Starts an activity from the background using the overlay-permission exemption. */
    private fun launch(ctx: Context, intent: Intent): Boolean = try {
        ctx.startActivity(intent)
        true
    } catch (e: Exception) {
        ProbeLog.log(ctx, "launch_failed", JSONObject().put("error", e.javaClass.simpleName))
        false
    }
}
