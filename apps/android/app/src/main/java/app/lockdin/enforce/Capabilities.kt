// Live checks of what the phone currently lets Lockd In do. Re-read on every service tick and
// every time the app resumes: permissions can be revoked at any moment in Settings.
package app.lockdin.enforce

import android.app.AppOpsManager
import android.app.NotificationManager
import android.content.Context
import android.content.Intent
import android.content.pm.PackageManager
import android.net.Uri
import android.os.Build
import android.os.PowerManager
import android.os.Process
import android.provider.Settings
import android.telecom.TelecomManager

object Capabilities {
    fun usageAccess(ctx: Context): Boolean {
        val ops = ctx.getSystemService(AppOpsManager::class.java)
        @Suppress("DEPRECATION") // unsafeCheckOpNoThrow needs API 29 too; this form is stable
        val mode = ops.checkOpNoThrow(AppOpsManager.OPSTR_GET_USAGE_STATS, Process.myUid(), ctx.packageName)
        return mode == AppOpsManager.MODE_ALLOWED
    }

    /** "Display over other apps": the documented exemption that lets the block screen come to the front. */
    fun displayOverApps(ctx: Context): Boolean = Settings.canDrawOverlays(ctx)

    fun notifications(ctx: Context): Boolean =
        ctx.getSystemService(NotificationManager::class.java).areNotificationsEnabled()

    /** Recommended, not required: lets the service restart itself after the system stops it. */
    fun batteryUnrestricted(ctx: Context): Boolean =
        ctx.getSystemService(PowerManager::class.java).isIgnoringBatteryOptimizations(ctx.packageName)

    /** Our accessibility service is switched on in Settings (it may still be connecting). */
    fun accessibility(ctx: Context): Boolean {
        val enabled = Settings.Secure.getString(ctx.contentResolver, Settings.Secure.ENABLED_ACCESSIBILITY_SERVICES) ?: return false
        val ours = android.content.ComponentName(ctx, FocusAccessibilityService::class.java)
        return enabled.split(':').any { android.content.ComponentName.unflattenFromString(it) == ours }
    }

    fun detection(ctx: Context): Pair<Detection, Set<Capability>> =
        BlockPolicy.detection(accessibility(ctx), usageAccess(ctx), displayOverApps(ctx))

    /** What is missing for app blocking (empty when either detection method is ready). */
    fun missing(ctx: Context): Set<Capability> = detection(ctx).second

    /** Device-specific apps that are never covered: home screen, keyboard, dialer, Settings, Lockd In. */
    fun essentials(ctx: Context): Set<String> = buildSet {
        add(ctx.packageName)
        resolve(ctx, Intent(Intent.ACTION_MAIN).addCategory(Intent.CATEGORY_HOME))?.let(::add)
        resolve(ctx, Intent(Settings.ACTION_SETTINGS))?.let(::add)
        Settings.Secure.getString(ctx.contentResolver, Settings.Secure.DEFAULT_INPUT_METHOD)
            ?.substringBefore('/')?.takeIf { it.isNotBlank() }?.let(::add)
        runCatching { ctx.getSystemService(TelecomManager::class.java)?.defaultDialerPackage }.getOrNull()?.let(::add)
    }

    private fun resolve(ctx: Context, intent: Intent): String? =
        ctx.packageManager.resolveActivity(intent, PackageManager.MATCH_DEFAULT_ONLY)?.activityInfo?.packageName
            ?.takeIf { it != "android" }

    // Screens where the user grants each permission. Package-specific pages where Android has them.
    fun usageAccessSettings(ctx: Context): Intent =
        Intent(Settings.ACTION_USAGE_ACCESS_SETTINGS).apply {
            if (Build.VERSION.SDK_INT >= 29) data = Uri.fromParts("package", ctx.packageName, null)
        }

    fun displayOverAppsSettings(ctx: Context): Intent =
        Intent(Settings.ACTION_MANAGE_OVERLAY_PERMISSION, Uri.fromParts("package", ctx.packageName, null))

    fun accessibilitySettings(): Intent = Intent(Settings.ACTION_ACCESSIBILITY_SETTINGS)

    fun notificationSettings(ctx: Context): Intent =
        Intent(Settings.ACTION_APP_NOTIFICATION_SETTINGS).putExtra(Settings.EXTRA_APP_PACKAGE, ctx.packageName)

    /** The battery optimisation list (no special permission or Play declaration needed). */
    fun batterySettings(): Intent = Intent(Settings.ACTION_IGNORE_BATTERY_OPTIMIZATION_SETTINGS)

    /** Opens a settings screen, falling back to the app's own info page if a vendor removed it. */
    fun open(ctx: Context, intent: Intent) {
        val flagged = intent.addFlags(Intent.FLAG_ACTIVITY_NEW_TASK)
        try {
            ctx.startActivity(flagged)
        } catch (e: Exception) {
            if (intent.data != null) {
                try { ctx.startActivity(Intent(intent.action).addFlags(Intent.FLAG_ACTIVITY_NEW_TASK)); return } catch (_: Exception) { }
            }
            ctx.startActivity(
                Intent(Settings.ACTION_APPLICATION_DETAILS_SETTINGS, Uri.fromParts("package", ctx.packageName, null))
                    .addFlags(Intent.FLAG_ACTIVITY_NEW_TASK),
            )
        }
    }
}
