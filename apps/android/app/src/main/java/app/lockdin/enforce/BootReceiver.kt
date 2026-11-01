package app.lockdin.enforce

import android.content.BroadcastReceiver
import android.content.Context
import android.content.Intent

/**
 * Resumes blocking after a reboot or an app update if a session is still running. Starting a
 * specialUse foreground service from BOOT_COMPLETED is allowed (Android 15 restricts only
 * other types). The session ends at its stored time whether or not the phone was off.
 */
class BootReceiver : BroadcastReceiver() {
    override fun onReceive(context: Context, intent: Intent) {
        when (intent.action) {
            Intent.ACTION_BOOT_COMPLETED, Intent.ACTION_MY_PACKAGE_REPLACED -> FocusService.sync(context)
        }
    }
}
