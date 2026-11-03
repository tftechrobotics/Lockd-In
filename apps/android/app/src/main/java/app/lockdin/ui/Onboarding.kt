// First-run setup and walkthrough (owner 2026-11-02). Five steps: welcome, the first profile (no
// ready-made ones), the blocking permission, an optional accountability partner, then a short
// tour of the real screens. "End tutorial" is on every step; nothing is required to start a timer.
package app.lockdin.ui

import android.Manifest
import android.os.Build
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.selection.toggleable
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.Checkbox
import androidx.compose.material3.CheckboxDefaults
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.produceState
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.semantics.Role
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.heading
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import app.lockdin.enforce.AppRules
import app.lockdin.enforce.InstalledApps
import app.lockdin.enforce.LaunchableApp
import app.lockdin.enforce.RuleMode
import app.lockdin.state.Profile
import app.lockdin.state.Store
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext

/** Apps people most often want out of the way while working, offered first in setup when installed. */
private val SUGGESTED = listOf(
    "com.instagram.android", "com.zhiliaoapp.musically", "com.ss.android.ugc.trill", "com.google.android.youtube",
    "com.snapchat.android", "com.twitter.android", "com.facebook.katana", "com.reddit.frontpage", "com.discord",
    "com.netflix.mediaclient", "tv.twitch.android.app", "com.pinterest", "com.instagram.barcelona",
)

private const val STEPS = 5

@Composable
fun Onboarding(store: Store) {
    val c = LocalColors.current
    var page by rememberSaveable { mutableIntStateOf(0) }
    // The first profile, created when leaving step 2 (kept if the person goes back and forth).
    var name by rememberSaveable { mutableStateOf("Study") }
    var picked by rememberSaveable { mutableStateOf(setOf<String>()) }
    val endTutorial = { store.update { it.copy(onboarded = true, tourStep = -1) } }
    fun saveProfile() {
        val n = name.trim().take(40)
        if (n.isEmpty() || store.state.profiles.any { it.name == n }) return
        val p = Profile(Store.newId(), n, "Made during setup", false, AppRules(RuleMode.BLOCK_LIST, picked))
        store.update { it.copy(profiles = it.profiles + p, last = it.last.copy(profileId = p.id)) }
    }
    val askNotifications = rememberLauncherForActivityResult(ActivityResultContracts.RequestPermission()) { Resumes.bump() }

    Column(Modifier.fillMaxSize()) {
        Row(Modifier.fillMaxWidth().padding(start = Dim.gutter, end = Dim.s2, top = Dim.s2), verticalAlignment = Alignment.CenterVertically) {
            StepDots(page, Modifier.weight(1f))
            TextAction("End tutorial", { saveProfileIfNamed(page, ::saveProfile); endTutorial() })
        }
        Column(
            Modifier.weight(1f).fillMaxWidth().verticalScroll(rememberScrollState()).padding(horizontal = Dim.gutter),
            verticalArrangement = Arrangement.spacedBy(Dim.s4),
        ) {
            Spacer(Modifier.height(Dim.s2))
            when (page) {
                0 -> Column(Modifier.fillMaxWidth(), horizontalAlignment = Alignment.CenterHorizontally, verticalArrangement = Arrangement.spacedBy(Dim.s4)) {
                    Spacer(Modifier.height(Dim.s4))
                    ClockLock(TimerState.READY, 0, 1, locked = false, label = "Lockd In", caption = "lockd in", text = "", width = 180.dp)
                    Heading("Start focus. Keep distractions closed.", center = true)
                    Body("We’ll set up your first profile, blocking and an optional partner, then show you around. It takes about two minutes.", center = true)
                    Body("Lockd In never closes your apps or deletes anything.", center = true)
                }
                1 -> FirstProfileStep(name, { name = it.take(40) }, picked, { picked = it })
                2 -> {
                    Heading("Turn on blocking")
                    Body("Android only lets an app see which app is open with your permission. Without it, sessions run as a timer. You can do this later in Devices.")
                    BlockingSetup()
                    if (Build.VERSION.SDK_INT >= 33) SecondaryButton("Allow notifications", { askNotifications.launch(Manifest.permission.POST_NOTIFICATIONS) }, modifier = Modifier.fillMaxWidth())
                }
                3 -> {
                    Heading("Add a partner (optional)")
                    Body("Someone you trust holds the code that ends a session early. You never see it unless they give it to you. You can also do this later in Settings › Ending early.")
                    PartnerSetup(store)
                }
                else -> Column(Modifier.fillMaxWidth(), horizontalAlignment = Alignment.CenterHorizontally, verticalArrangement = Arrangement.spacedBy(Dim.s4)) {
                    Spacer(Modifier.height(Dim.s4))
                    Heading("You’re set", center = true)
                    Body("Next, a few tips on the real screens. Tap Next to move on, or End tutorial any time. You can replay them from Settings › Help and tour.", center = true)
                }
            }
            Spacer(Modifier.height(Dim.s4))
        }
        ActionBar {
            when (page) {
                0 -> MainButton("Get started", { page = 1 })
                1 -> MainButton("Continue", { if (name.isNotBlank()) { saveProfile(); page = 2 } })
                2, 3 -> MainButton("Continue", { page++ })
                else -> MainButton("Show me around", { store.update { it.copy(onboarded = true, tourStep = 0) } })
            }
            if (page > 0) TextAction("Back", { page-- }, modifier = Modifier.align(Alignment.CenterHorizontally))
        }
    }
}

private fun saveProfileIfNamed(page: Int, save: () -> Unit) { if (page >= 1) save() }

@Composable
private fun FirstProfileStep(name: String, onName: (String) -> Unit, picked: Set<String>, onPicked: (Set<String>) -> Unit) {
    val c = LocalColors.current
    val ctx = LocalContext.current
    val apps by produceState<List<LaunchableApp>?>(null) {
        value = withContext(Dispatchers.IO) {
            val all = runCatching { InstalledApps.launchable(ctx) }.getOrDefault(emptyList())
            val suggested = all.filter { it.packageName in SUGGESTED }.sortedBy { SUGGESTED.indexOf(it.packageName) }
            suggested + all.filter { it.packageName !in SUGGESTED }.take(maxOf(0, 10 - suggested.size))
        }
    }
    Heading("Make your first profile")
    Body("A profile is what gets blocked for one kind of work. You can make more later in Profiles.")
    OutlinedTextField(
        name, onName, singleLine = true, label = { Text("Name") }, shape = RoundedCornerShape(Dim.radius), colors = fieldColors(),
        modifier = Modifier.fillMaxWidth().semantics { contentDescription = "Profile name" },
    )
    ProfileNameChips(listOf("Study", "Work", "Reading", "Writing"), name, onName)
    Text("Block these apps during focus", style = UiType.label, color = c.ink, modifier = Modifier.semantics { heading() })
    val list = apps
    if (list == null) Text("Loading apps…", style = UiType.caption, color = c.inkMuted)
    else if (list.isEmpty()) Text("No apps found. You can add apps later in Profiles.", style = UiType.caption, color = c.inkMuted)
    else SettingsGroup {
        list.forEachIndexed { i, a ->
            if (i > 0) GroupDivider()
            val on = a.packageName in picked
            Row(
                Modifier.fillMaxWidth().heightIn(min = Dim.control + 4.dp)
                    .toggleable(on, role = Role.Checkbox) { onPicked(if (it) picked + a.packageName else picked - a.packageName) }
                    .padding(horizontal = Dim.s3),
                verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(Dim.s3),
            ) {
                AppIcon(a.packageName, 30.dp)
                Text(a.label, style = UiType.body, color = c.ink, modifier = Modifier.weight(1f))
                Checkbox(on, null, colors = CheckboxDefaults.colors(checkedColor = c.ink, uncheckedColor = c.controlLine, checkmarkColor = c.onInk))
            }
        }
    }
    Text("More apps and websites can be added in Profiles.", style = UiType.caption, color = c.inkMuted)
}

@Composable
private fun ProfileNameChips(names: List<String>, current: String, onPick: (String) -> Unit) {
    val c = LocalColors.current
    androidx.compose.foundation.layout.FlowRow(horizontalArrangement = Arrangement.spacedBy(Dim.s2), verticalArrangement = Arrangement.spacedBy(Dim.s2)) {
        names.forEach { n ->
            val on = n == current.trim()
            Surface(
                shape = androidx.compose.foundation.shape.CircleShape, color = if (on) c.ink else androidx.compose.ui.graphics.Color.Transparent,
                contentColor = if (on) c.onInk else c.ink, border = if (on) null else androidx.compose.foundation.BorderStroke(1.dp, c.line),
                modifier = Modifier.heightIn(min = Dim.control).clickable(role = Role.Button) { onPick(n) },
            ) { Box(Modifier.padding(horizontal = 14.dp, vertical = 10.dp), contentAlignment = Alignment.Center) { Text(n, style = UiType.label) } }
        }
    }
}

@Composable
private fun StepDots(page: Int, modifier: Modifier) {
    val c = LocalColors.current
    Row(modifier.semantics { contentDescription = "Step ${page + 1} of $STEPS" }, horizontalArrangement = Arrangement.spacedBy(6.dp)) {
        repeat(STEPS) { i ->
            Box(Modifier.height(8.dp).width(if (i == page) 20.dp else 8.dp).background(if (i == page) c.ink else c.line, RoundedCornerShape(4.dp)))
        }
    }
}

@Composable
private fun Heading(t: String, center: Boolean = false) = Text(t, style = UiType.title, color = LocalColors.current.ink,
    textAlign = if (center) TextAlign.Center else TextAlign.Start, modifier = Modifier.semantics { heading() })

@Composable
private fun Body(t: String, center: Boolean = false) = Text(t, style = UiType.body, color = LocalColors.current.inkMuted,
    textAlign = if (center) TextAlign.Center else TextAlign.Start)

/** Tour tips over the real screens (after setup, or replayed from Settings). */
data class TourTip(val title: String, val text: String, val tab: String)

val TOUR = listOf(
    TourTip("Start here", "Pick what you’re working on, how long, and a profile. The lock closes once blocking is on.", "focus"),
    TourTip("Plan ahead", "Schedule sessions and weekly routines. Lockd In reminds you, or starts them for you.", "schedule"),
    TourTip("Profiles", "Each profile decides which apps and websites are blocked, and its study audio.", "profiles"),
    TourTip("Your history", "Finished sessions stay here, on this phone only. Settings are behind the gear at the top.", "history"),
)

/** The current tour tip as a card above the bottom navigation. */
@Composable
fun TourCard(store: Store, modifier: Modifier = Modifier) {
    val step = store.state.tourStep
    val tip = TOUR.getOrNull(step) ?: return
    val c = LocalColors.current
    Surface(color = c.ink, contentColor = c.onInk, shape = RoundedCornerShape(16.dp), modifier = modifier.fillMaxWidth().padding(horizontal = Dim.gutter)
        .semantics { androidx.compose.ui.semantics.liveRegion = androidx.compose.ui.semantics.LiveRegionMode.Polite }) {
        Column(Modifier.padding(horizontal = Dim.s4, vertical = Dim.s3), verticalArrangement = Arrangement.spacedBy(Dim.s1)) {
            Text("Tour · ${step + 1} of ${TOUR.size}", style = UiType.caption, color = c.onInk)
            Text(tip.title, style = UiType.title, color = c.onInk, modifier = Modifier.semantics { heading() })
            Text(tip.text, style = UiType.body, color = c.onInk)
            Row(verticalAlignment = Alignment.CenterVertically) {
                androidx.compose.material3.TextButton(onClick = { store.update { it.copy(tourStep = -1) } }, modifier = Modifier.heightIn(min = Dim.control)) {
                    Text("End tutorial", style = UiType.label, color = c.onInk)
                }
                Spacer(Modifier.weight(1f))
                androidx.compose.material3.Button(
                    onClick = { store.update { it.copy(tourStep = if (step + 1 < TOUR.size) step + 1 else -1) } },
                    colors = androidx.compose.material3.ButtonDefaults.buttonColors(containerColor = c.onInk, contentColor = c.ink),
                    shape = RoundedCornerShape(10.dp), modifier = Modifier.heightIn(min = Dim.control),
                ) { Text(if (step + 1 < TOUR.size) "Next" else "Done", style = UiType.label) }
            }
        }
    }
}
