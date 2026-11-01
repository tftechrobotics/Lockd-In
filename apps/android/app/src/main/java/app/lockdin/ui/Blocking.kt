// Blocking on this phone: permission setup, live status, and choosing apps for a profile.
package app.lockdin.ui

import android.Manifest
import android.content.Context
import android.os.Build
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.Checkbox
import androidx.compose.material3.CheckboxDefaults
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.IconButton
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.State
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.produceState
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.semantics.Role
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.semantics.stateDescription
import androidx.compose.ui.unit.dp
import app.lockdin.enforce.AppRules
import app.lockdin.enforce.Capabilities
import app.lockdin.enforce.Capability
import app.lockdin.enforce.EnforcerStatus
import app.lockdin.enforce.FocusService
import app.lockdin.enforce.InstalledApps
import app.lockdin.enforce.LaunchableApp
import app.lockdin.enforce.RuleMode
import app.lockdin.state.Profile
import app.lockdin.state.Store
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.delay
import kotlinx.coroutines.withContext

/** Bumped when the app returns to the foreground, so permission checks re-run. */
object Resumes {
    var tick by mutableIntStateOf(0)
        private set
    fun bump() { tick++ }
}

/** Required permissions that are missing right now; re-checked on resume and every few seconds. */
@Composable
fun rememberMissing(): Set<Capability> {
    val ctx = LocalContext.current
    val tick = Resumes.tick
    val missing by produceState(Capabilities.missing(ctx), tick) {
        while (true) {
            value = Capabilities.missing(ctx)
            delay(5_000)
        }
    }
    return missing
}

@Composable
fun liveReport(): State<EnforcerStatus.Report?> = EnforcerStatus.reports.collectAsState()

/** One line describing a profile's rules on this phone. */
fun rulesSummary(rules: AppRules): String = when {
    rules.mode == RuleMode.APPROVED_ONLY && rules.packages.isEmpty() -> "Only essential apps (phone, Settings, keyboard) during focus"
    rules.mode == RuleMode.APPROVED_ONLY -> "Only ${rules.packages.size} approved app${if (rules.packages.size == 1) "" else "s"} plus essentials during focus"
    rules.packages.isEmpty() -> "No apps chosen on this phone yet"
    else -> "${rules.packages.size} app${if (rules.packages.size == 1) "" else "s"} blocked during focus"
}

private fun Context.open(cap: Capability) = Capabilities.open(
    this,
    when (cap) {
        Capability.USAGE_ACCESS -> Capabilities.usageAccessSettings(this)
        Capability.DISPLAY_OVER_APPS -> Capabilities.displayOverAppsSettings(this)
    },
)

/** Permission checklist for blocking on this phone. Each row says why it's needed. */
@Composable
fun BlockingSetup() {
    val ctx = LocalContext.current
    val c = LocalColors.current
    val missing = rememberMissing()
    val tick = Resumes.tick
    val notifications = remember(tick) { Capabilities.notifications(ctx) }
    val battery = remember(tick) { Capabilities.batteryUnrestricted(ctx) }
    val askNotifications = rememberLauncherForActivityResult(ActivityResultContracts.RequestPermission()) { Resumes.bump() }

    SettingsGroup {
        PermissionRow(
            "Usage access", Capability.USAGE_ACCESS !in missing,
            "Lets Lockd In see which app is in front, so it can cover blocked apps. App names only; nothing inside apps.",
        ) { ctx.open(Capability.USAGE_ACCESS) }
        GroupDivider()
        PermissionRow(
            "Display over other apps", Capability.DISPLAY_OVER_APPS !in missing,
            "Lets the block screen come to the front when a blocked app opens.",
        ) { ctx.open(Capability.DISPLAY_OVER_APPS) }
        GroupDivider()
        PermissionRow("Notifications", notifications, "Shows that a session is running and warns you if blocking stops.") {
            if (Build.VERSION.SDK_INT >= 33) askNotifications.launch(Manifest.permission.POST_NOTIFICATIONS)
            else Capabilities.open(ctx, Capabilities.notificationSettings(ctx))
        }
        GroupDivider()
        PermissionRow(
            "Battery: unrestricted", battery,
            "Recommended. Without it, some phones stop Lockd In in the background and blocking can pause.",
            optional = true,
        ) { Capabilities.open(ctx, Capabilities.batterySettings()) }
    }
    Text(
        if (missing.isEmpty()) "Ready. During focus, apps you block are covered by a block screen. They aren’t closed, and Settings, calls and your keyboard always work."
        else "Until both required permissions are on, sessions run as a timer only.",
        style = UiType.caption, color = c.inkMuted,
    )
}

@Composable
private fun PermissionRow(label: String, granted: Boolean, why: String, optional: Boolean = false, onGrant: () -> Unit) {
    val value = when {
        granted -> "On"
        optional -> "Recommended"
        else -> "Turn on"
    }
    SettingRow(label, value, supporting = why, onClick = if (granted) null else onGrant)
}

/** Live status of this phone for the Devices screen. */
@Composable
fun phoneBlockingValue(): String {
    val missing = rememberMissing()
    val report by liveReport()
    return when {
        missing.isNotEmpty() -> "Permission needed"
        report?.protection == app.lockdin.enforce.Protection.PROTECTING && report?.fresh() == true -> "Protecting"
        FocusService.isRunning -> "Session running"
        else -> "Ready"
    }
}

/** Full-screen editor: block list or approved-only, and which apps, for one profile on this phone. */
@Composable
fun ProfileEditor(store: Store, profile: Profile, onClose: () -> Unit) {
    val ctx = LocalContext.current
    val c = LocalColors.current
    var mode by remember { mutableStateOf(profile.rules.mode) }
    var chosen by remember { mutableStateOf(profile.rules.packages) }
    var query by remember { mutableStateOf("") }
    val apps by produceState<List<LaunchableApp>?>(null) {
        value = withContext(Dispatchers.IO) { runCatching { InstalledApps.launchable(ctx) }.getOrDefault(emptyList()) }
    }
    val running = store.state.active?.profileId == profile.id

    fun save() {
        store.update { s -> s.copy(profiles = s.profiles.map { if (it.id == profile.id) it.copy(rules = AppRules(mode, chosen)) else it }) }
        onClose()
    }

    Column(Modifier.fillMaxSize()) {
        Row(Modifier.fillMaxWidth().heightIn(min = 56.dp).padding(horizontal = 4.dp), verticalAlignment = Alignment.CenterVertically) {
            IconButton(onClick = onClose) { LockdIcon(LIcon.Close, description = "Close without saving") }
            Text(profile.name, style = UiType.title, color = c.ink, modifier = Modifier.weight(1f))
            TextAction("Save", ::save)
        }
        Column(Modifier.padding(horizontal = Dim.gutter), verticalArrangement = Arrangement.spacedBy(Dim.s3)) {
            SegmentedChoice(
                listOf(RuleMode.BLOCK_LIST to "Block these", RuleMode.APPROVED_ONLY to "Allow only these"),
                mode, { mode = it }, label = "Rule type",
            )
            Text(
                if (mode == RuleMode.BLOCK_LIST) "The apps you tick are covered during focus. Everything else stays available."
                else "During focus, only the apps you tick open normally. Phone, Settings, your keyboard and Lockd In always work.",
                style = UiType.caption, color = c.inkMuted,
            )
            if (running) Text("A session with this profile is running. Changes apply from the next session.", style = UiType.caption, color = c.ink)
            OutlinedTextField(
                query, { query = it.take(40) }, singleLine = true, placeholder = { Text("Search apps") },
                shape = RoundedCornerShape(Dim.radius), colors = fieldColors(),
                modifier = Modifier.fillMaxWidth().semantics { contentDescription = "Search apps" },
            )
        }
        val list = apps
        when {
            list == null -> Box(Modifier.fillMaxWidth().padding(Dim.s6), contentAlignment = Alignment.Center) {
                CircularProgressIndicator(color = c.ink, modifier = Modifier.semantics { contentDescription = "Loading apps" })
            }
            list.isEmpty() -> Text("No apps found. Apps without a home-screen icon can’t be chosen.", style = UiType.body, color = c.inkMuted, modifier = Modifier.padding(Dim.gutter))
            else -> {
                val shown = list.filter { query.isBlank() || it.label.contains(query, ignoreCase = true) }
                Text(
                    "${chosen.size} chosen", style = UiType.caption, color = c.inkMuted,
                    modifier = Modifier.padding(horizontal = Dim.gutter, vertical = Dim.s2),
                )
                LazyColumn(Modifier.fillMaxWidth().weight(1f)) {
                    items(shown, key = { it.packageName }) { app ->
                        val on = app.packageName in chosen
                        Row(
                            Modifier.fillMaxWidth().heightIn(min = Dim.control + 4.dp)
                                .clickable(role = Role.Checkbox) { chosen = if (on) chosen - app.packageName else chosen + app.packageName }
                                .semantics(mergeDescendants = true) { stateDescription = if (on) "Chosen" else "Not chosen" }
                                .padding(horizontal = Dim.gutter),
                            verticalAlignment = Alignment.CenterVertically,
                        ) {
                            Text(app.label, style = UiType.body, color = c.ink, modifier = Modifier.weight(1f))
                            Checkbox(on, onCheckedChange = null, colors = CheckboxDefaults.colors(checkedColor = c.ink, uncheckedColor = c.controlLine, checkmarkColor = c.onInk))
                        }
                        HorizontalDivider(color = c.line, modifier = Modifier.padding(start = Dim.gutter))
                    }
                }
            }
        }
    }
}
