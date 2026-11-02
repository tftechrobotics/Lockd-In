// Location routines on the phone (roadmap step 7, E). Google Play services geofences notice
// arrival (after the place's grace period) and departure; Lockd In evaluates them on the phone
// and keeps no location history. Needs location "Allow all the time" (Android requires it for
// geofences); without it, routines simply don't trigger and the app says so.
package app.lockdin.loc

import android.Manifest
import android.annotation.SuppressLint
import android.app.PendingIntent
import android.content.BroadcastReceiver
import android.content.Context
import android.content.Intent
import android.content.pm.PackageManager
import android.os.Build
import android.util.Log
import app.lockdin.engine.Arrival
import app.lockdin.engine.Place
import app.lockdin.engine.Places
import app.lockdin.engine.Session
import app.lockdin.state.LastChoice
import app.lockdin.state.Store
import com.google.android.gms.location.Geofence
import com.google.android.gms.location.GeofencingEvent
import com.google.android.gms.location.GeofencingRequest
import com.google.android.gms.location.LocationServices
import java.time.ZoneId

object Geofences {
    private const val TAG = "LockdInPlaces"
    const val ACTION = "app.lockdin.GEOFENCE"

    fun foreground(ctx: Context) = ctx.checkSelfPermission(Manifest.permission.ACCESS_FINE_LOCATION) == PackageManager.PERMISSION_GRANTED ||
        ctx.checkSelfPermission(Manifest.permission.ACCESS_COARSE_LOCATION) == PackageManager.PERMISSION_GRANTED

    fun background(ctx: Context) = ctx.checkSelfPermission(Manifest.permission.ACCESS_BACKGROUND_LOCATION) == PackageManager.PERMISSION_GRANTED

    /** Ready = places can trigger: fine location and "all the time" granted. */
    fun ready(ctx: Context) = ctx.checkSelfPermission(Manifest.permission.ACCESS_FINE_LOCATION) == PackageManager.PERMISSION_GRANTED && background(ctx)

    private fun pending(ctx: Context): PendingIntent = PendingIntent.getBroadcast(
        ctx, 77, Intent(ctx, GeofenceReceiver::class.java).setAction(ACTION),
        // Play services adds the event to this intent, so it must be mutable.
        PendingIntent.FLAG_UPDATE_CURRENT or (if (Build.VERSION.SDK_INT >= 31) PendingIntent.FLAG_MUTABLE else 0),
    )

    /** Replaces all geofences with the current enabled places (none while paused). */
    @SuppressLint("MissingPermission")
    fun sync(ctx: Context) {
        val st = Store.get(ctx).state
        val client = LocationServices.getGeofencingClient(ctx)
        client.removeGeofences(pending(ctx))
        val places = st.places.filter { it.enabled }
        if (st.placesPaused || places.isEmpty() || !ready(ctx)) { Log.i(TAG, "geofences cleared (paused=${st.placesPaused} places=${places.size} ready=${ready(ctx)})"); return }
        val fences = places.map { p ->
            Geofence.Builder().setRequestId(p.id).setCircularRegion(p.lat, p.lon, p.radiusM.toFloat())
                .setExpirationDuration(Geofence.NEVER_EXPIRE)
                .setTransitionTypes(
                    (if (p.arriveGraceMin > 0) Geofence.GEOFENCE_TRANSITION_DWELL else Geofence.GEOFENCE_TRANSITION_ENTER) or Geofence.GEOFENCE_TRANSITION_EXIT,
                )
                .setLoiteringDelay(maxOf(1, p.arriveGraceMin) * 60_000)
                .build()
        }
        val req = GeofencingRequest.Builder()
            .setInitialTrigger(GeofencingRequest.INITIAL_TRIGGER_DWELL or GeofencingRequest.INITIAL_TRIGGER_ENTER)
            .addGeofences(fences).build()
        runCatching {
            client.addGeofences(req, pending(ctx))
                .addOnSuccessListener { Log.i(TAG, "geofences set: ${fences.size}") }
                .addOnFailureListener { Log.w(TAG, "geofences failed: ${it.javaClass.simpleName} ${it.message}") }
        }.onFailure { Log.w(TAG, "geofences error: ${it.javaClass.simpleName}") }
    }

    /** Current location for "Use my current location" (foreground permission only). */
    @SuppressLint("MissingPermission")
    fun current(ctx: Context, onResult: (Triple<Double, Double, Float>?) -> Unit) {
        if (!foreground(ctx)) { onResult(null); return }
        LocationServices.getFusedLocationProviderClient(ctx)
            .getCurrentLocation(com.google.android.gms.location.Priority.PRIORITY_HIGH_ACCURACY, null)
            .addOnSuccessListener { l -> onResult(l?.let { Triple(it.latitude, it.longitude, it.accuracy) }) }
            .addOnFailureListener { onResult(null) }
    }

    /** Arrival after the grace period. */
    fun arrived(ctx: Context, p: Place, now: Long = System.currentTimeMillis(), zone: ZoneId = ZoneId.systemDefault()) {
        val store = Store.get(ctx)
        val st = store.state
        val running = st.active?.let { app.lockdin.enforce.FocusService.shouldRun(st, now) } == true
        val decision = Places.onArrive(p, now, zone, st.placesPaused, running, st.planLog.toSet())
        Log.i(TAG, "place_arrive ${p.name}: $decision")
        when (decision) {
            is Arrival.Skip -> return
            Arrival.Remind -> {
                store.logPlan(listOf(Places.firedKey(p, now, zone)))
                PlaceNotices.remind(ctx, p)
            }
            Arrival.Start -> {
                store.logPlan(listOf(Places.firedKey(p, now, zone)))
                start(ctx, p, now, zone, auto = true)
            }
        }
    }

    fun start(ctx: Context, p: Place, now: Long, zone: ZoneId, auto: Boolean) {
        val store = Store.get(ctx)
        val profile = if (store.state.profiles.any { it.id == p.profileId }) p.profileId else store.state.last.profileId
        if (store.state.active != null) store.finish(dismissed = false)
        store.startSession(now, Session.roundsPlan(1, p.focusMinutes, 0), p.name, profile,
            LastChoice(p.focusMinutes, store.state.last.breakMinutes, 1, profile), occurrenceKey = Places.firedKey(p, now, zone))
        app.lockdin.enforce.FocusService.sync(ctx)
        PlaceNotices.started(ctx, p, auto)
    }

    fun left(ctx: Context, p: Place) {
        val a = Store.get(ctx).state.active ?: return
        Log.i(TAG, "place_leave ${p.name}")
        if (Places.onLeave(p.id, a.occurrenceKey)) PlaceNotices.left(ctx, p)
    }
}

/** Geofence transitions from Play services. */
class GeofenceReceiver : BroadcastReceiver() {
    override fun onReceive(context: Context, intent: Intent) {
        val event = GeofencingEvent.fromIntent(intent) ?: return
        if (event.hasError()) { Log.w("LockdInPlaces", "geofence error ${event.errorCode}"); return }
        val st = Store.get(context).state
        val places = event.triggeringGeofences.orEmpty().mapNotNull { g -> st.places.firstOrNull { it.id == g.requestId } }
        when (event.geofenceTransition) {
            Geofence.GEOFENCE_TRANSITION_DWELL, Geofence.GEOFENCE_TRANSITION_ENTER -> places.forEach { Geofences.arrived(context, it) }
            Geofence.GEOFENCE_TRANSITION_EXIT -> places.forEach { Geofences.left(context, it) }
        }
    }
}
