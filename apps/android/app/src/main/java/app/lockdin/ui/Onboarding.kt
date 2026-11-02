// First-run introduction (release readiness). Short and honest: what Lockd In does and doesn't
// do, the one permission blocking needs, and notifications. Skippable on every page; nothing is
// required to start a timer.
package app.lockdin.ui

import android.Manifest
import android.os.Build
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.semantics.heading
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import app.lockdin.state.Store

@Composable
fun Onboarding(store: Store) {
    val c = LocalColors.current
    var page by rememberSaveable { mutableIntStateOf(0) }
    val done = { store.update { it.copy(onboarded = true) } }
    val askNotifications = rememberLauncherForActivityResult(ActivityResultContracts.RequestPermission()) { Resumes.bump(); done() }
    Column(Modifier.fillMaxSize()) {
        Row(Modifier.fillMaxWidth().padding(horizontal = Dim.s2, vertical = Dim.s2), verticalAlignment = Alignment.CenterVertically) {
            Text("${page + 1} of 3", style = UiType.caption, color = c.inkMuted, modifier = Modifier.weight(1f).padding(start = Dim.s3))
            TextAction("Skip", { done() })
        }
        Column(
            Modifier.weight(1f).fillMaxWidth().verticalScroll(rememberScrollState()).padding(horizontal = Dim.gutter),
            horizontalAlignment = Alignment.CenterHorizontally, verticalArrangement = Arrangement.spacedBy(Dim.s4),
        ) {
            Spacer(Modifier.height(Dim.s4))
            when (page) {
                0 -> {
                    ClockLock(TimerState.READY, 0, 1, locked = false, label = "Lockd In", caption = "lockd in", text = "", width = 170.dp)
                    Heading("Start focus. Keep distractions closed.")
                    Body("Pick what you’re working on and how long. During focus, the apps and sites you chose are covered by a block screen, and breaks open them again.")
                    Body("Lockd In never closes your apps or deletes anything. Emergency recovery always ends a session at once.")
                }
                1 -> {
                    Heading("Blocking needs one permission")
                    Body("Android lets an app see which app is in front only with a permission you grant in Settings. Accessibility is quickest and can also cover websites; usage access works too. You can do this now or later. Without it, sessions run as a timer.")
                    BlockingSetup()
                }
                else -> {
                    Heading("Reminders and session notices")
                    Body("Lockd In can remind you before planned sessions and tell you when breaks start and end. Every kind has its own switch in Settings, and all of it stays on this phone.")
                }
            }
        }
        ActionBar {
            when (page) {
                0, 1 -> MainButton("Continue", { page++ })
                else -> MainButton(if (Build.VERSION.SDK_INT >= 33) "Allow notifications" else "Get started", {
                    if (Build.VERSION.SDK_INT >= 33) askNotifications.launch(Manifest.permission.POST_NOTIFICATIONS) else done()
                })
            }
            if (page == 2 && Build.VERSION.SDK_INT >= 33) SecondaryButton("Not now", { done() }, modifier = Modifier.fillMaxWidth())
        }
    }
}

@Composable
private fun Heading(t: String) = Text(t, style = UiType.title, color = LocalColors.current.ink, textAlign = TextAlign.Center, modifier = Modifier.semantics { heading() })

@Composable
private fun Body(t: String) = Text(t, style = UiType.body, color = LocalColors.current.inkMuted, textAlign = TextAlign.Center)
