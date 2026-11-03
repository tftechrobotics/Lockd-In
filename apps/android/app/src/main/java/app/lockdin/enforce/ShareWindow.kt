// A few minutes in which the apps used to send a partner request (or invite) aren't covered
// (owner 2026-11-03: the Ask message couldn't be sent because Messages was blocked). Opened by the
// app just before the share sheet; the app picked in the sheet is added when Android reports it.
package app.lockdin.enforce

import android.app.PendingIntent
import android.content.BroadcastReceiver
import android.content.Context
import android.content.Intent

object ShareWindow {
    const val MINUTES = 5
    @Volatile private var allowed: Map<String, Long> = emptyMap()

    fun open(pkgs: Collection<String>, now: Long = System.currentTimeMillis()) {
        val until = now + MINUTES * 60_000L
        allowed = allowed.filterValues { it > now } + pkgs.associateWith { until }
    }

    fun allows(pkg: String, now: Long): Boolean = (allowed[pkg] ?: 0L) > now

    /** Opens the share sheet with messaging apps allowed, and the chosen app added once picked. */
    fun share(ctx: Context, text: String, title: String) {
        open(Capabilities.messaging(ctx))
        val send = Intent(Intent.ACTION_SEND).setType("text/plain").putExtra(Intent.EXTRA_TEXT, text)
        val picked = PendingIntent.getBroadcast(
            ctx, 0, Intent(ctx, ShareChosenReceiver::class.java),
            PendingIntent.FLAG_UPDATE_CURRENT or PendingIntent.FLAG_MUTABLE,
        )
        runCatching {
            ctx.startActivity(Intent.createChooser(send, title, picked.intentSender).addFlags(Intent.FLAG_ACTIVITY_NEW_TASK))
        }
    }
}

/** Android tells us which app was picked in the share sheet; that app stays open for the window. */
class ShareChosenReceiver : BroadcastReceiver() {
    override fun onReceive(context: Context, intent: Intent) {
        @Suppress("DEPRECATION")
        val c = intent.getParcelableExtra<android.content.ComponentName>(Intent.EXTRA_CHOSEN_COMPONENT) ?: return
        ShareWindow.open(listOf(c.packageName))
    }
}
