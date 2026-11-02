// Notices for location routines (E). Arrival: a reminder with Start now / Not today, or a notice
// that an automatic start happened. Leaving: a check-in only; the session keeps its rules.
package app.lockdin.loc

import android.app.PendingIntent
import android.content.BroadcastReceiver
import android.content.Context
import android.content.Intent
import app.lockdin.sched.Notifier
import app.lockdin.state.Store
import java.time.ZoneId

object PlaceNotices {
    const val ACTION_START = "app.lockdin.PLACE_START"
    const val ACTION_DISMISS = "app.lockdin.PLACE_DISMISS"

    private fun id(placeId: String) = 5000 + Math.floorMod(placeId.hashCode(), 900)

    private fun action(ctx: Context, action: String, placeId: String, req: Int) = PendingIntent.getBroadcast(
        ctx, req, Intent(ctx, PlaceActionReceiver::class.java).setAction(action).putExtra("place", placeId),
        PendingIntent.FLAG_IMMUTABLE or PendingIntent.FLAG_UPDATE_CURRENT,
    )

    fun remind(ctx: Context, p: app.lockdin.engine.Place) {
        val profile = Store.get(ctx).state.profiles.firstOrNull { it.id == p.profileId }?.name ?: "Focus"
        Notifier.post(ctx, id(p.id), Notifier.CH_REMINDERS, "You’re at ${p.name}",
            "Start $profile for ${p.focusMinutes} min?", listOf(
                Notifier.Action("Start now", action(ctx, ACTION_START, p.id, id(p.id) * 10 + 1)),
                Notifier.Action("Not today", action(ctx, ACTION_DISMISS, p.id, id(p.id) * 10 + 2)),
            ))
    }

    fun started(ctx: Context, p: app.lockdin.engine.Place, auto: Boolean) {
        Notifier.cancel(ctx, id(p.id))
        if (auto) Notifier.post(ctx, id(p.id), Notifier.CH_EVENTS, "${p.name}: focus started",
            "Started automatically because you arrived. End it any time from Lockd In.")
    }

    fun left(ctx: Context, p: app.lockdin.engine.Place) {
        Notifier.post(ctx, id(p.id), Notifier.CH_REMINDERS, "You left ${p.name}",
            "Your session keeps going as planned. If you’re done, you can end it in Lockd In.")
    }
}

class PlaceActionReceiver : BroadcastReceiver() {
    override fun onReceive(context: Context, intent: Intent) {
        val p = Store.get(context).state.places.firstOrNull { it.id == intent.getStringExtra("place") } ?: return
        when (intent.action) {
            PlaceNotices.ACTION_START -> Geofences.start(context, p, System.currentTimeMillis(), ZoneId.systemDefault(), auto = false)
            PlaceNotices.ACTION_DISMISS -> Notifier.cancel(context, 5000 + Math.floorMod(p.id.hashCode(), 900))
        }
    }
}
