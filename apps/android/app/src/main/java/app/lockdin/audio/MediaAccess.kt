// Control of the person's music app (B2, owner decision R47: notification access).
//
// Android only lets an app see and control other apps' media players (MediaSessionManager) if
// the person grants notification access to one of its NotificationListenerServices. Lockd In's
// listener exists for that alone: it overrides no notification callbacks, so it never reads,
// stores or acts on notifications.
package app.lockdin.audio

import android.content.ComponentName
import android.content.Context
import android.content.Intent
import android.media.session.MediaController
import android.media.session.MediaSessionManager
import android.os.Build
import android.provider.Settings
import android.service.notification.NotificationListenerService

/** Granting notification access to this service lets Lockd In control the music app's player. */
class MediaAccessService : NotificationListenerService()

object MediaAccess {
    fun component(ctx: Context) = ComponentName(ctx, MediaAccessService::class.java)

    fun granted(ctx: Context): Boolean = runCatching {
        val flat = Settings.Secure.getString(ctx.contentResolver, "enabled_notification_listeners") ?: return false
        val me = component(ctx)
        flat.split(':').mapNotNull { ComponentName.unflattenFromString(it) }.any { it == me }
    }.getOrDefault(false)

    /** Settings page where the person turns access on or off (this app's own page on Android 11+). */
    fun settingsIntent(ctx: Context): Intent =
        (if (Build.VERSION.SDK_INT >= 30) Intent(Settings.ACTION_NOTIFICATION_LISTENER_DETAIL_SETTINGS)
            .putExtra(Settings.EXTRA_NOTIFICATION_LISTENER_COMPONENT_NAME, component(ctx).flattenToString())
        else Intent(Settings.ACTION_NOTIFICATION_LISTENER_SETTINGS)).addFlags(Intent.FLAG_ACTIVITY_NEW_TASK)

    /** The music app's player, if access is granted and that app has one right now. */
    fun controller(ctx: Context, pkg: String): MediaController? = runCatching {
        if (!granted(ctx)) return null
        ctx.getSystemService(MediaSessionManager::class.java).getActiveSessions(component(ctx)).firstOrNull { it.packageName == pkg }
    }.getOrNull()
}
