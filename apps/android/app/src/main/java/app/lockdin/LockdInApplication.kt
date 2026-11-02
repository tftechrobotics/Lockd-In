package app.lockdin

import android.app.Application
import app.lockdin.enforce.FocusService
import app.lockdin.state.Store

class LockdInApplication : Application() {
    override fun onCreate() {
        super.onCreate()
        FocusService.createChannels(this)
        // Any change that leaves a running session (start, schedule, remote start later)
        // makes sure the blocking service is up, independent of which screen is open.
        app.lockdin.sched.Notifier.channels(this)
        var lastPlanInputs: Any? = null
        Store.get(this).onChange = { st ->
            FocusService.sync(this)
            app.lockdin.widget.LockdInWidget.update(this)
            // Schedule or reminder settings changed: re-plan alarms.
            val inputs = st.schedule to st.reminders
            if (inputs != lastPlanInputs) {
                val first = lastPlanInputs == null
                lastPlanInputs = inputs
                if (!first) app.lockdin.sched.Scheduler.run(this)
            }
        }
        lastPlanInputs = Store.get(this).state.let { it.schedule to it.reminders }
        // Location routines (E): geofences follow the places and the pause switch.
        var lastPlaces: Any? = Store.get(this).state.let { it.places to it.placesPaused }
        runCatching { app.lockdin.loc.Geofences.sync(this) }
        val prevChange = Store.get(this).onChange
        Store.get(this).onChange = { st ->
            prevChange?.invoke(st)
            val p = st.places to st.placesPaused
            if (p != lastPlaces) { lastPlaces = p; runCatching { app.lockdin.loc.Geofences.sync(this) } }
        }
        // History (roadmap step 2): every session is recorded as it is left, then old entries
        // are pruned to the retention the person chose.
        Store.get(this).onLeft = { a, s, now -> app.lockdin.state.HistoryStore.recordLeft(this, a, s, now) }
        app.lockdin.state.HistoryStore.get(this).prune(Store.get(this).state.historyDays, System.currentTimeMillis())
    }
}
