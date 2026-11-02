// Blocking on this phone: permission setup, live status, and choosing apps for a profile.
package app.lockdin.ui

import android.Manifest
import android.content.Context
import android.os.Build
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.foundation.clickable
import androidx.compose.foundation.selection.toggleable
import androidx.compose.foundation.verticalScroll
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.Checkbox
import androidx.compose.material3.CheckboxDefaults
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.IconButton
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
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
import androidx.compose.ui.graphics.asImageBitmap
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.semantics.Role
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.semantics.stateDescription
import androidx.compose.ui.unit.dp
import app.lockdin.enforce.AppRules
import app.lockdin.enforce.BlockPolicy
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

    /** A screen asked for from outside (a notification), consumed by the app's navigation. */
    var route by androidx.compose.runtime.mutableStateOf<String?>(null)
    fun openRoute(r: String) { route = r }
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

private const val MAX_SITES = 300

/** One line describing a profile's rules on this phone. */
fun rulesSummary(rules: AppRules): String {
    val n = rules.packages.size
    val apps = "$n app${if (n == 1) "" else "s"}"
    val appPart = when {
        rules.mode == RuleMode.APPROVED_ONLY && n == 0 -> "Only essential apps"
        rules.mode == RuleMode.APPROVED_ONLY -> "Only $apps plus essentials"
        n == 0 -> null
        else -> "$apps blocked"
    }
    val m = rules.sites.size
    val sites = "$m website${if (m == 1) "" else "s"}"
    val sitePart = when {
        rules.siteMode == RuleMode.APPROVED_ONLY && m == 0 -> "all websites blocked"
        rules.siteMode == RuleMode.APPROVED_ONLY -> "only $sites allowed"
        m == 0 -> null
        else -> "$sites blocked"
    }
    return when {
        appPart == null && sitePart == null -> "Nothing blocked yet"
        else -> listOfNotNull(appPart, sitePart).joinToString(" · ").replaceFirstChar { it.uppercase() } + " during focus"
    }
}

private fun Context.open(cap: Capability) = Capabilities.open(
    this,
    when (cap) {
        Capability.USAGE_ACCESS -> Capabilities.usageAccessSettings(this)
        Capability.DISPLAY_OVER_APPS -> Capabilities.displayOverAppsSettings(this)
        Capability.ACCESSIBILITY -> Capabilities.accessibilitySettings()
    },
)

/**
 * Permission checklist for blocking on this phone. Accessibility is offered first (instant, and
 * the only way to block websites); usage access + display over other apps is the alternative.
 * Accessibility is opened only after a plain-language disclosure of exactly what it reads.
 */
@Composable
fun BlockingSetup() {
    val ctx = LocalContext.current
    val c = LocalColors.current
    val missing = rememberMissing()
    val tick = Resumes.tick
    val a11y = remember(tick, missing) { Capabilities.accessibility(ctx) }
    val usage = remember(tick, missing) { Capabilities.usageAccess(ctx) }
    val overlay = remember(tick, missing) { Capabilities.displayOverApps(ctx) }
    val notifications = remember(tick) { Capabilities.notifications(ctx) }
    val battery = remember(tick) { Capabilities.batteryUnrestricted(ctx) }
    val askNotifications = rememberLauncherForActivityResult(ActivityResultContracts.RequestPermission()) { Resumes.bump() }
    var disclose by remember { mutableStateOf(false) }

    SettingsGroup {
        PermissionRow(
            "Accessibility (recommended)", a11y,
            "Blocks apps the moment they open, and blocks websites in Chrome, Samsung Internet, Edge, Firefox, Brave and Opera.",
        ) { disclose = true }
    }
    if (!a11y) Text(
        "If Android says this setting is restricted: open Settings › Apps › Lockd In, tap ⋮ › Allow restricted settings, then try again.",
        style = UiType.caption, color = c.inkMuted,
    )
    SectionLabel(if (a11y) "Without Accessibility (not needed now)" else "Or, without Accessibility")
    SettingsGroup {
        PermissionRow(
            "Usage access", usage || a11y,
            "Lets Lockd In see which app is in front, about twice a second. App names only. Can’t block websites.",
            value = if (a11y && !usage) "Not needed" else null,
        ) { ctx.open(Capability.USAGE_ACCESS) }
        GroupDivider()
        PermissionRow(
            "Display over other apps", overlay || a11y,
            "Lets the block screen come to the front when a blocked app opens.",
            value = if (a11y && !overlay) "Not needed" else null,
        ) { ctx.open(Capability.DISPLAY_OVER_APPS) }
    }
    SettingsGroup {
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
        if (missing.isEmpty()) "Ready. During focus, blocked apps and sites are covered by a block screen. Nothing is closed, and Settings, calls and your keyboard always work."
        else "Until blocking is set up, sessions run as a timer only.",
        style = UiType.caption, color = c.inkMuted,
    )

    if (disclose) {
        androidx.compose.material3.AlertDialog(
            onDismissRequest = { disclose = false },
            title = { Text("Use Accessibility for blocking?", style = Type.title) },
            text = {
                Text(
                    "Lockd In uses Android’s Accessibility service only during focus sessions, to:\n\n" +
                        "• see which app comes to the front, so it can cover apps you chose to block;\n" +
                        "• read the web address in supported browsers, so it can cover sites you chose to block.\n\n" +
                        "It doesn’t read what you type, your messages or page content, doesn’t store or send anything, and never taps or acts in other apps. You can turn it off any time in Settings.",
                    style = UiType.body,
                )
            },
            confirmButton = {
                androidx.compose.material3.TextButton(onClick = { disclose = false; ctx.open(Capability.ACCESSIBILITY) }) {
                    Text("Continue to Settings", style = UiType.label, color = c.ink)
                }
            },
            dismissButton = {
                androidx.compose.material3.TextButton(onClick = { disclose = false }) { Text("Not now", style = UiType.label, color = c.ink) }
            },
            containerColor = c.surface,
        )
    }
}

@Composable
private fun PermissionRow(label: String, granted: Boolean, why: String, optional: Boolean = false, value: String? = null, onGrant: () -> Unit) {
    val shown = value ?: when {
        granted -> "On"
        optional -> "Recommended"
        else -> "Turn on"
    }
    SettingRow(label, shown, supporting = why, onClick = if (granted) null else onGrant)
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
    var sites by remember { mutableStateOf(profile.rules.sites) }
    var siteMode by remember { mutableStateOf(profile.rules.siteMode) }
    var audio by remember { mutableStateOf(profile.audio) }
    var audioError by remember { mutableStateOf<String?>(null) }
    var tab by remember { mutableStateOf(0) }
    var siteText by remember { mutableStateOf("") }
    var siteError by remember { mutableStateOf<String?>(null) }
    var query by remember { mutableStateOf("") }
    var confirmDelete by remember { mutableStateOf(false) }
    val a11y = remember(Resumes.tick) { Capabilities.accessibility(ctx) }
    val apps by produceState<List<LaunchableApp>?>(null) {
        value = withContext(Dispatchers.IO) { runCatching { InstalledApps.launchable(ctx) }.getOrDefault(emptyList()) }
    }
    val running = store.state.active?.profileId == profile.id
    val allowSites = siteMode == RuleMode.APPROVED_ONLY

    fun save() {
        if (audio.access != app.lockdin.audio.MusicAccess.NORMAL && audio.musicApps.isEmpty()) {
            tab = 2; audioError = "Choose at least one music app, or pick “Follow blocking rules”."; return
        }
        val a = audio
        app.lockdin.audio.StudyPlayer.stopPreview()
        store.update { s -> s.copy(profiles = s.profiles.map { if (it.id == profile.id) it.copy(rules = AppRules(mode, chosen, sites, siteMode), audio = a) else it }) }
        onClose()
    }

    Column(Modifier.fillMaxSize()) {
        Row(Modifier.fillMaxWidth().heightIn(min = 56.dp).padding(horizontal = 4.dp), verticalAlignment = Alignment.CenterVertically) {
            IconButton(onClick = onClose) { LockdIcon(LIcon.Close, description = "Close without saving") }
            Text(profile.name, style = UiType.title, color = c.ink, modifier = Modifier.weight(1f))
            if (!profile.builtIn) TextAction("Delete", { confirmDelete = true }, danger = true)
            TextAction("Save", ::save)
        }
        Column(Modifier.padding(horizontal = Dim.gutter), verticalArrangement = Arrangement.spacedBy(Dim.s3)) {
            SegmentedChoice(listOf(0 to "Apps", 1 to "Websites", 2 to "Audio"), tab, { tab = it }, label = "Edit apps, websites or sound")
            if (running) Text("A session with this profile is running. Changes apply from the next session.", style = UiType.caption, color = c.ink)
        }
        if (tab == 2) {
            AudioTab(audio, { audio = it; audioError = null }, audioError)
        } else if (tab == 1) {
            Column(Modifier.padding(horizontal = Dim.gutter, vertical = Dim.s3), verticalArrangement = Arrangement.spacedBy(Dim.s3)) {
                SegmentedChoice(
                    listOf(RuleMode.BLOCK_LIST to "Block these", RuleMode.APPROVED_ONLY to "Allow only these"),
                    siteMode, { siteMode = it }, label = "Website rule",
                )
                Text(
                    if (!allowSites) "The sites you add are covered during focus, including their subdomains (youtube.com covers m.youtube.com). Every other site works."
                    else "During focus, only the sites you add open. Every other site is covered. Add google.com if you need search.",
                    style = UiType.caption, color = c.inkMuted,
                )
                Text(
                    if (!allowSites) "Works in Chrome, Samsung Internet, Edge, Firefox, Brave and Opera. Other browsers can be blocked as apps."
                    else "Works in Chrome, Samsung Internet, Edge, Firefox, Brave and Opera. Other browsers are blocked completely during focus, because Lockd In can’t see which site they show.",
                    style = UiType.caption, color = c.inkMuted,
                )
                if (!a11y) Text("Website rules need Accessibility. Turn it on in Devices › Set up blocking.", style = UiType.caption, color = c.ink)
                Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(Dim.s2)) {
                    OutlinedTextField(
                        siteText, { siteText = it.take(253); siteError = null }, singleLine = true,
                        placeholder = { Text(if (allowSites) "wikipedia.org" else "youtube.com") },
                        isError = siteError != null, shape = RoundedCornerShape(Dim.radius), colors = fieldColors(),
                        modifier = Modifier.weight(1f).semantics { contentDescription = if (allowSites) "Website to allow" else "Website to block" },
                    )
                    SecondaryButton("Add", {
                        val d = BlockPolicy.domainOf(siteText)
                        if (d == null) siteError = "Enter a site like youtube.com"
                        else { sites = (sites + d).take(MAX_SITES).toSet(); siteText = "" }
                    })
                }
                siteError?.let { Text(it, style = UiType.caption, color = c.danger) }
                if (sites.isEmpty()) Text(
                    if (allowSites) "No sites allowed yet, so every website is covered during focus." else "No websites blocked yet.",
                    style = UiType.body, color = c.inkMuted,
                )
            }
            LazyColumn(Modifier.fillMaxWidth().weight(1f)) {
                items(sites.sorted(), key = { it }) { d ->
                    Row(Modifier.fillMaxWidth().heightIn(min = Dim.control + 4.dp).padding(start = Dim.gutter, end = Dim.s2), verticalAlignment = Alignment.CenterVertically) {
                        Text(d, style = UiType.body, color = c.ink, modifier = Modifier.weight(1f))
                        TextAction("Remove", { sites = sites - d })
                    }
                    HorizontalDivider(color = c.line, modifier = Modifier.padding(start = Dim.gutter))
                }
            }
        } else {
            Column(Modifier.padding(horizontal = Dim.gutter, vertical = Dim.s3), verticalArrangement = Arrangement.spacedBy(Dim.s3)) {
                SegmentedChoice(
                    listOf(RuleMode.BLOCK_LIST to "Block these", RuleMode.APPROVED_ONLY to "Allow only these"),
                    mode, { mode = it }, label = "Rule type",
                )
                Text(
                    if (mode == RuleMode.BLOCK_LIST) "The apps you tick are covered during focus. Everything else stays available."
                    else "During focus, only the apps you tick open normally. Everything else is covered.",
                    style = UiType.caption, color = c.inkMuted,
                )
                Text(
                    "Never blocked, so your phone keeps working: phone and emergency calls, contacts, Settings, keyboard, home screen, Samsung and Google Wallet, clock, camera and Lockd In. They aren’t listed here.",
                    style = UiType.caption, color = c.inkMuted,
                )
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
                                Modifier.fillMaxWidth().heightIn(min = 56.dp)
                                    .clickable(role = Role.Checkbox) { chosen = if (on) chosen - app.packageName else chosen + app.packageName }
                                    .semantics(mergeDescendants = true) { stateDescription = if (on) "Chosen" else "Not chosen" }
                                    .padding(horizontal = Dim.gutter),
                                verticalAlignment = Alignment.CenterVertically,
                                horizontalArrangement = Arrangement.spacedBy(Dim.s3),
                            ) {
                                AppIcon(app.packageName)
                                Text(app.label, style = UiType.body, color = c.ink, modifier = Modifier.weight(1f))
                                Checkbox(on, onCheckedChange = null, colors = CheckboxDefaults.colors(checkedColor = c.ink, uncheckedColor = c.controlLine, checkmarkColor = c.onInk))
                            }
                            HorizontalDivider(color = c.line, modifier = Modifier.padding(start = Dim.gutter + 36.dp + Dim.s3))
                        }
                    }
                }
            }
        }
    }

    if (confirmDelete) {
        AlertDialog(
            onDismissRequest = { confirmDelete = false },
            containerColor = c.surface,
            title = { Text("Delete “${profile.name}”?", style = UiType.title, color = c.ink) },
            text = { Text("Planned sessions that use it switch to timer only. A running session keeps its rules until it ends.", style = UiType.body, color = c.inkMuted) },
            confirmButton = {
                TextButton(onClick = {
                    store.update { s -> s.copy(profiles = s.profiles.filter { it.id != profile.id }) }
                    confirmDelete = false; onClose()
                }) { Text("Delete", style = UiType.label, color = c.danger) }
            },
            dismissButton = { TextButton(onClick = { confirmDelete = false }) { Text("Keep", style = UiType.label, color = c.ink) } },
        )
    }
}

/** The app's own launcher icon, loaded off the main thread. Decorative: the label is read instead. */
@Composable
fun AppIcon(pkg: String, size: androidx.compose.ui.unit.Dp = 36.dp) {
    val ctx = LocalContext.current
    val px = with(androidx.compose.ui.platform.LocalDensity.current) { size.roundToPx() }
    val bmp by produceState<android.graphics.Bitmap?>(null, pkg, px) {
        value = withContext(Dispatchers.IO) { InstalledApps.icon(ctx, pkg, px) }
    }
    Box(Modifier.size(size), contentAlignment = Alignment.Center) {
        bmp?.let { androidx.compose.foundation.Image(it.asImageBitmap(), contentDescription = null, modifier = Modifier.size(size)) }
    }
}


/** Study sound for a profile (B2): generated ambient sound, and/or a playlist in the person's own music app. */
/**
 * Audio for one profile (R48): built-in study audio and access to external music apps, two
 * independent settings. Either works without the other.
 */
@Composable
private fun AudioTab(audio: app.lockdin.state.ProfileAudio, onAudio: (app.lockdin.state.ProfileAudio) -> Unit, error: String?) {
    val c = LocalColors.current
    val ctx = LocalContext.current
    Column(
        Modifier.fillMaxSize().verticalScroll(androidx.compose.foundation.rememberScrollState()).padding(horizontal = Dim.gutter, vertical = Dim.s3),
        verticalArrangement = Arrangement.spacedBy(Dim.s4),
    ) {
        // ---- Built-in study audio ----
        Column(verticalArrangement = Arrangement.spacedBy(Dim.s2)) {
            SectionLabel("Built-in study audio")
            SettingsGroup {
                SwitchRow("Play study audio", "Lockd In plays it from recordings on this phone. Works offline.", audio.builtIn) {
                    onAudio(audio.copy(builtIn = it)); if (!it) app.lockdin.audio.StudyPlayer.stopPreview()
                }
            }
            if (audio.builtIn) {
                androidx.compose.foundation.layout.FlowRow(horizontalArrangement = Arrangement.spacedBy(Dim.s2), verticalArrangement = Arrangement.spacedBy(Dim.s2)) {
                    app.lockdin.audio.AudioCollection.values().forEach { k ->
                        Chip(k.label, audio.collection == k) { onAudio(audio.copy(collection = k)); app.lockdin.audio.StudyPlayer.stopPreview() }
                    }
                }
                Text(audio.collection.blurb + " · ${audio.collection.tracks.size} " + if (audio.collection.loop) "seamless loop" else "tracks, played in order", style = UiType.caption, color = c.inkMuted)
                if (audio.collection.development) Text(
                    "Development recordings: made by Lockd In for testing. They’ll be replaced with produced recordings.",
                    style = UiType.caption, color = c.inkMuted,
                )
                val previewing = app.lockdin.audio.StudyPlayer.previewing == audio.collection
                SecondaryButton(if (previewing) "Stop preview" else "Preview", {
                    if (previewing) app.lockdin.audio.StudyPlayer.stopPreview() else app.lockdin.audio.StudyPlayer.preview(ctx, audio.collection, audio.volume)
                })
                Text("Volume", style = UiType.caption, color = c.inkMuted)
                androidx.compose.material3.Slider(
                    audio.volume, { onAudio(audio.copy(volume = it)) }, valueRange = 0f..1f,
                    colors = androidx.compose.material3.SliderDefaults.colors(thumbColor = c.ink, activeTrackColor = c.ink, inactiveTrackColor = c.line),
                    modifier = Modifier.semantics { contentDescription = "Study audio volume" },
                )
                SettingsGroup {
                    SwitchRow("Play when focus starts", "Off: start it yourself from the session screen.", audio.playOnStart) { onAudio(audio.copy(playOnStart = it)) }
                    GroupDivider()
                    SwitchRow(
                        "Keep playing during breaks", "Off: it pauses for breaks and comes back with focus. It always fades out when the session ends.",
                        audio.breaks == app.lockdin.audio.BreakAudio.CONTINUE,
                    ) { onAudio(audio.copy(breaks = if (it) app.lockdin.audio.BreakAudio.CONTINUE else app.lockdin.audio.BreakAudio.PAUSE)) }
                }
            }
        }
        // ---- External music apps ----
        Column(verticalArrangement = Arrangement.spacedBy(Dim.s2)) {
            SectionLabel("Music apps during focus")
            app.lockdin.audio.MusicAccess.values().forEach { m ->
                RadioRow(m.label, when (m) {
                    app.lockdin.audio.MusicAccess.NORMAL -> "Music apps are blocked or allowed like any other app in this profile."
                    app.lockdin.audio.MusicAccess.LIMITED -> "The music apps you choose share a few minutes of browsing per session. Time counts only while one is on screen during focus; music playing in the background doesn’t use it."
                    app.lockdin.audio.MusicAccess.ALWAYS -> "The music apps you choose stay open during focus."
                }, audio.access == m) { onAudio(audio.copy(access = m)) }
            }
            if (audio.access == app.lockdin.audio.MusicAccess.LIMITED) {
                Text("Browsing per session", style = UiType.caption, color = c.inkMuted)
                androidx.compose.foundation.layout.FlowRow(horizontalArrangement = Arrangement.spacedBy(Dim.s2), verticalArrangement = Arrangement.spacedBy(Dim.s2)) {
                    app.lockdin.audio.MusicAllowance.CHOICES.forEach { m ->
                        Chip(if (m == app.lockdin.audio.MusicAllowance.DEFAULT_MIN) "$m min (suggested)" else "$m min", audio.allowanceMin == m) { onAudio(audio.copy(allowanceMin = m)) }
                    }
                }
            }
            if (audio.access != app.lockdin.audio.MusicAccess.NORMAL) {
                Text(
                    "Allowing an app allows everything in it, including videos, feeds and other content, not just music.",
                    style = UiType.caption, color = c.ink,
                )
                val eligible by produceState<List<LaunchableApp>?>(null) {
                    value = withContext(Dispatchers.IO) {
                        val pk = app.lockdin.audio.MusicApps.eligiblePackages(ctx)
                        runCatching { InstalledApps.launchable(ctx) }.getOrDefault(emptyList()).filter { it.packageName in pk }
                    }
                }
                val list = eligible
                when {
                    list == null -> CircularProgressIndicator(color = c.ink, modifier = Modifier.semantics { contentDescription = "Finding music apps" })
                    list.isEmpty() -> Text("No music apps found on this phone. Apps qualify if they offer Android’s media controls.", style = UiType.body, color = c.inkMuted)
                    else -> list.forEach { app ->
                        val on = app.packageName in audio.musicApps
                        Row(
                            Modifier.fillMaxWidth().heightIn(min = Dim.control + 4.dp)
                                .toggleable(on, role = Role.Checkbox) { onAudio(audio.copy(musicApps = if (it) audio.musicApps + app.packageName else audio.musicApps - app.packageName)) },
                            verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(Dim.s3),
                        ) {
                            AppIcon(app.packageName, 32.dp)
                            Text(app.label, style = UiType.body, color = c.ink, modifier = Modifier.weight(1f))
                            androidx.compose.material3.Checkbox(on, null, colors = androidx.compose.material3.CheckboxDefaults.colors(checkedColor = c.ink, checkmarkColor = c.onInk))
                        }
                    }
                }
            }
            error?.let { Text(it, style = UiType.caption, color = c.danger) }
            Text("Changes apply from the next session. A running session keeps the audio settings it started with.", style = UiType.caption, color = c.inkMuted)
        }
    }
}

@Composable
private fun RadioRow(title: String, detail: String, on: Boolean, onClick: () -> Unit) {
    val c = LocalColors.current
    Row(
        Modifier.fillMaxWidth().clickable(role = Role.RadioButton, onClick = onClick).semantics { stateDescription = if (on) "Selected" else "Not selected" }.padding(vertical = Dim.s1),
        verticalAlignment = Alignment.Top, horizontalArrangement = Arrangement.spacedBy(Dim.s2),
    ) {
        androidx.compose.material3.RadioButton(on, null, colors = androidx.compose.material3.RadioButtonDefaults.colors(selectedColor = c.ink))
        Column(Modifier.weight(1f)) {
            Text(title, style = UiType.label, color = c.ink)
            Text(detail, style = UiType.caption, color = c.inkMuted)
        }
    }
}

@Composable
private fun Chip(label: String, on: Boolean, onClick: () -> Unit) {
    val c = LocalColors.current
    androidx.compose.material3.Surface(
        shape = androidx.compose.foundation.shape.CircleShape, color = if (on) c.ink else androidx.compose.ui.graphics.Color.Transparent,
        contentColor = if (on) c.onInk else c.ink, border = if (on) null else androidx.compose.foundation.BorderStroke(1.dp, c.line),
        modifier = Modifier.heightIn(min = Dim.control).androidx_selectable(on, onClick),
    ) { Box(Modifier.padding(horizontal = 16.dp, vertical = 10.dp), contentAlignment = Alignment.Center) { Text(label, style = UiType.label, maxLines = 1) } }
}

private fun Modifier.androidx_selectable(on: Boolean, onClick: () -> Unit): Modifier =
    this.then(Modifier.clickable(role = Role.RadioButton, onClick = onClick).semantics { stateDescription = if (on) "Selected" else "Not selected" })
