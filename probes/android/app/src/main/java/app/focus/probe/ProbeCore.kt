package app.focus.probe

import android.app.AppOpsManager
import android.content.Context
import android.content.Intent
import android.content.pm.PackageManager
import android.os.Build
import android.os.Process
import android.provider.Settings
import org.json.JSONObject
import java.io.File

/** Probe session state. Persisted so it survives process death. */
object ProbeSession {
    private const val PREFS = "probe"

    enum class Detector { USAGE_STATS, ACCESSIBILITY }

    fun prefs(ctx: Context) = ctx.getSharedPreferences(PREFS, Context.MODE_PRIVATE)

    fun blocked(ctx: Context): Set<String> =
        prefs(ctx).getStringSet("blocked", emptySet()) ?: emptySet()

    fun endsAtMs(ctx: Context): Long = prefs(ctx).getLong("ends_at", 0L)

    fun detector(ctx: Context): Detector =
        runCatching { Detector.valueOf(prefs(ctx).getString("detector", null) ?: "") }
            .getOrDefault(Detector.USAGE_STATS)

    /** Wall-clock end; the session is never longer than 60 minutes in the probe. */
    fun isActive(ctx: Context): Boolean = System.currentTimeMillis() < endsAtMs(ctx)

    fun start(ctx: Context, blocked: Set<String>, minutes: Int, detector: Detector) {
        val m = minutes.coerceIn(1, 60)
        prefs(ctx).edit()
            .putStringSet("blocked", blocked)
            .putLong("ends_at", System.currentTimeMillis() + m * 60_000L)
            .putString("detector", detector.name)
            .apply()
    }

    fun end(ctx: Context) {
        prefs(ctx).edit().putLong("ends_at", 0L).apply()
    }
}

/** Capability checks. Verified each time — never assumed from onboarding. */
object Capabilities {
    fun usageAccess(ctx: Context): Boolean {
        val ops = ctx.getSystemService(AppOpsManager::class.java)
        val mode = if (Build.VERSION.SDK_INT >= 29) {
            ops.unsafeCheckOpNoThrow(AppOpsManager.OPSTR_GET_USAGE_STATS, Process.myUid(), ctx.packageName)
        } else {
            @Suppress("DEPRECATION")
            ops.checkOpNoThrow(AppOpsManager.OPSTR_GET_USAGE_STATS, Process.myUid(), ctx.packageName)
        }
        return mode == AppOpsManager.MODE_ALLOWED
    }

    fun overlay(ctx: Context): Boolean = Settings.canDrawOverlays(ctx)

    fun accessibilityEnabled(ctx: Context): Boolean {
        val enabled = Settings.Secure.getString(ctx.contentResolver, Settings.Secure.ENABLED_ACCESSIBILITY_SERVICES) ?: return false
        return enabled.split(':').any { it.startsWith(ctx.packageName + "/") }
    }

    fun notifications(ctx: Context): Boolean =
        Build.VERSION.SDK_INT < 33 ||
            ctx.checkSelfPermission(android.Manifest.permission.POST_NOTIFICATIONS) == PackageManager.PERMISSION_GRANTED

    fun homePackage(ctx: Context): String? {
        val home = Intent(Intent.ACTION_MAIN).addCategory(Intent.CATEGORY_HOME)
        return ctx.packageManager.resolveActivity(home, PackageManager.MATCH_DEFAULT_ONLY)?.activityInfo?.packageName
    }

    fun snapshot(ctx: Context) = JSONObject()
        .put("usage_access", usageAccess(ctx))
        .put("overlay", overlay(ctx))
        .put("accessibility", accessibilityEnabled(ctx))
        .put("notifications", notifications(ctx))
}

/** JSON-lines log in app-private storage. Package names and timings only. */
object ProbeLog {
    private fun file(ctx: Context) = File(ctx.filesDir, "probe.jsonl")

    @Synchronized
    fun log(ctx: Context, event: String, data: JSONObject = JSONObject()) {
        val line = JSONObject()
            .put("t", System.currentTimeMillis())
            .put("evt", event)
            .put("sdk", Build.VERSION.SDK_INT)
            .put("data", data)
        file(ctx).appendText(line.toString() + "\n")
    }

    fun tail(ctx: Context, lines: Int = 40): String =
        file(ctx).takeIf { it.exists() }?.readLines()?.takeLast(lines)?.joinToString("\n") ?: "(empty)"

    fun all(ctx: Context): String = file(ctx).takeIf { it.exists() }?.readText() ?: ""

    fun clear(ctx: Context) {
        file(ctx).delete()
    }
}

/** Decides what to do when an app comes to the front. Main thread only. */
object Enforcer {
    private val ignored = setOf("com.android.systemui", "android")

    fun onForeground(ctx: Context, pkg: String, source: ProbeSession.Detector, latencyMs: Long) {
        if (pkg == ctx.packageName || pkg in ignored) return
        val active = ProbeSession.isActive(ctx)
        val restricted = active && pkg in ProbeSession.blocked(ctx) && pkg != Capabilities.homePackage(ctx)
        if (restricted) {
            val shown = Shield.show(ctx, pkg)
            ProbeLog.log(
                ctx, "restricted_foreground",
                JSONObject().put("pkg", pkg).put("source", source.name)
                    .put("detect_latency_ms", latencyMs).put("shield_shown", shown)
            )
        } else if (Shield.isShowing()) {
            Shield.hide()
            ProbeLog.log(ctx, "shield_hidden", JSONObject().put("pkg", pkg).put("source", source.name))
        }
    }
}
