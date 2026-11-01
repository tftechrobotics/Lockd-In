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
        Store.get(this).onChange = { FocusService.sync(this) }
    }
}
