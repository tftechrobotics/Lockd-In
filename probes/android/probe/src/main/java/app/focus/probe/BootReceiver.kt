package app.focus.probe

import android.content.BroadcastReceiver
import android.content.Context
import android.content.Intent
import org.json.JSONObject

/** Records what happens when the phone reboots during a probe session, and tries to resume. */
class BootReceiver : BroadcastReceiver() {
    override fun onReceive(context: Context, intent: Intent) {
        if (intent.action != Intent.ACTION_BOOT_COMPLETED) return
        val active = ProbeSession.isActive(context)
        ProbeLog.log(context, "boot_completed", JSONObject().put("session_active", active))
        if (!active) return
        try {
            ProbeService.start(context)
            ProbeLog.log(context, "resume_after_boot", JSONObject().put("started", true))
        } catch (e: Exception) {
            ProbeLog.log(context, "resume_after_boot", JSONObject().put("started", false).put("error", e.javaClass.simpleName))
        }
    }
}
