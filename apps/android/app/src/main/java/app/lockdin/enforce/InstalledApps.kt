package app.lockdin.enforce

import android.content.Context
import android.content.Intent
import android.content.pm.PackageManager

/** An app the user can choose in a profile: anything with a launcher icon. */
data class LaunchableApp(val packageName: String, val label: String)

object InstalledApps {
    /**
     * Apps with a launcher entry, via the manifest's narrow <queries> (MAIN/LAUNCHER) — no
     * QUERY_ALL_PACKAGES. Lockd In and apps that are never blocked are left out. Slow on
     * phones with many apps: call off the main thread.
     */
    fun launchable(ctx: Context): List<LaunchableApp> {
        val pm = ctx.packageManager
        val essentials = Capabilities.essentials(ctx) + BlockPolicy.ALWAYS_ALLOWED
        val intent = Intent(Intent.ACTION_MAIN).addCategory(Intent.CATEGORY_LAUNCHER)
        return pm.queryIntentActivities(intent, PackageManager.MATCH_ALL)
            .map { it.activityInfo.packageName to it }
            .distinctBy { it.first }
            .filter { (pkg, _) -> pkg !in essentials }
            .map { (pkg, ri) -> LaunchableApp(pkg, ri.loadLabel(pm).toString().ifBlank { pkg }) }
            .sortedBy { it.label.lowercase() }
    }

    fun label(ctx: Context, pkg: String): String = runCatching {
        val pm = ctx.packageManager
        pm.getApplicationLabel(pm.getApplicationInfo(pkg, 0)).toString()
    }.getOrDefault(pkg)
}
