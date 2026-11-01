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
            // Schedule or reminder settings changed: re-plan alarms.
            val inputs = st.schedule to st.reminders
            if (inputs != lastPlanInputs) {
                val first = lastPlanInputs == null
                lastPlanInputs = inputs
                if (!first) app.lockdin.sched.Scheduler.run(this)
            }
        }
        lastPlanInputs = Store.get(this).state.let { it.schedule to it.reminders }
    }
}
