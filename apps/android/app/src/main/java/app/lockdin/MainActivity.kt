package app.lockdin

import android.os.Bundle
import androidx.activity.ComponentActivity
import androidx.activity.SystemBarStyle
import androidx.activity.compose.setContent
import androidx.activity.enableEdgeToEdge
import androidx.compose.runtime.LaunchedEffect
import app.lockdin.enforce.FocusService
import app.lockdin.state.Store
import app.lockdin.ui.LockdInApp
import app.lockdin.ui.Resumes
import app.lockdin.ui.isDark

class MainActivity : ComponentActivity() {
    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        val store = Store.get(this)
        intent?.getStringExtra(EXTRA_ROUTE)?.let { Resumes.openRoute(it) }
        setContent {
            val dark = isDark(store.state.theme)
            // System bar icons follow the app's own theme choice, not only the system's.
            LaunchedEffect(dark) {
                val style = if (dark) SystemBarStyle.dark(android.graphics.Color.TRANSPARENT)
                else SystemBarStyle.light(android.graphics.Color.TRANSPARENT, android.graphics.Color.TRANSPARENT)
                enableEdgeToEdge(statusBarStyle = style, navigationBarStyle = style)
            }
            LockdInApp(store, dark = dark)
        }
    }

    override fun onNewIntent(intent: android.content.Intent) {
        super.onNewIntent(intent)
        intent.getStringExtra(EXTRA_ROUTE)?.let { Resumes.openRoute(it) }
    }

    override fun onResume() {
        super.onResume()
        // Permissions may have changed in Settings; a session may need its service restarted
        // (for example after the user force-stopped the app).
        Resumes.bump()
        FocusService.sync(this)
        // Catch up on anything the schedule missed while the phone was off, and re-arm alarms.
        app.lockdin.sched.Scheduler.run(this)
    }

    companion object {
        const val EXTRA_ROUTE = "route"
    }
}
