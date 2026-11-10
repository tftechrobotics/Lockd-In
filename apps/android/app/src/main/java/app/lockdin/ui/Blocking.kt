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
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.selection.selectable
import androidx.compose.foundation.selection.selectableGroup
import androidx.compose.ui.draw.clip
import androidx.compose.foundation.layout.height
import androidx.compose.ui.semantics.liveRegion
import androidx.compose.ui.graphics.asImageBitmap
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.semantics.Role
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.semantics.stateDescription
import androidx.compose.ui.unit.dp
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.sp
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

/** True when this copy came from somewhere other than Google Play (a test APK, say). */
private fun Context.sideloaded(): Boolean = runCatching {
    val from = if (Build.VERSION.SDK_INT >= 30) packageManager.getInstallSourceInfo(packageName).installingPackageName
    else @Suppress("DEPRECATION") packageManager.getInstallerPackageName(packageName)
    from != "com.android.vending"
}.getOrDefault(true)

/**
 * Blocking setup (owner 2026-11-03: shorter). Shows only what's still needed: Accessibility first
 * (instant, and the only way to block websites), notifications and battery. Usage access + display
 * over other apps is tucked behind "Can't use Accessibility?" and hidden once Accessibility is on.
 * Accessibility opens only after a plain-language disclosure of exactly what it reads.
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
    val sideloaded = remember { ctx.sideloaded() }
    val askNotifications = rememberLauncherForActivityResult(ActivityResultContracts.RequestPermission()) { Resumes.bump() }
    var disclose by remember { mutableStateOf(false) }
    var other by remember { mutableStateOf(false) }
    val viaUsage = !a11y && usage && overlay

    SettingsGroup {
        if (a11y || viaUsage) {
            SettingRow(if (a11y) "Accessibility" else "Usage access", "On", supporting = if (a11y) "Blocks apps and websites" else "Blocks apps (not websites)", chevron = null)
        } else {
            // Android 13+ greys out Accessibility for apps installed outside Google Play until
            // "Allow restricted settings" is tapped in App info. Play installs skip this step.
            if (sideloaded && Build.VERSION.SDK_INT >= 33) {
                SettingRow("1. Allow restricted settings", "Open", supporting = "In App info, tap ⋮ › Allow restricted settings. Only needed for test copies not installed from Google Play.",
                    onClick = {
                        Capabilities.open(ctx, android.content.Intent(android.provider.Settings.ACTION_APPLICATION_DETAILS_SETTINGS,
                            android.net.Uri.fromParts("package", ctx.packageName, null)))
                    })
                GroupDivider()
            }
            SettingRow(if (sideloaded && Build.VERSION.SDK_INT >= 33) "2. Turn on Accessibility" else "Turn on Accessibility", "Turn on",
                supporting = "Blocks apps the moment they open, and websites in browsers.", onClick = { disclose = true })
        }
        GroupDivider()
        PermissionRow("Notifications", notifications, if (notifications) null else "Shows a session is running and warns if blocking stops.") {
            if (Build.VERSION.SDK_INT >= 33) askNotifications.launch(Manifest.permission.POST_NOTIFICATIONS)
            else Capabilities.open(ctx, Capabilities.notificationSettings(ctx))
        }
        GroupDivider()
        PermissionRow("Battery: unrestricted", battery, if (battery) null else "Some phones pause blocking without it.", optional = true) {
            Capabilities.open(ctx, Capabilities.batterySettings())
        }
    }
    if (!a11y) {
        if (!other && !viaUsage) TextAction("Can’t use Accessibility?", { other = true })
        if (other || viaUsage) {
            SectionLabel("Without Accessibility")
            SettingsGroup {
                PermissionRow("Usage access", usage, "Sees which app is in front. Can’t block websites.") { ctx.open(Capability.USAGE_ACCESS) }
                GroupDivider()
                PermissionRow("Display over other apps", overlay, "Lets the block screen appear.") { ctx.open(Capability.DISPLAY_OVER_APPS) }
            }
        }
    }

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
private fun PermissionRow(label: String, granted: Boolean, why: String?, optional: Boolean = false, value: String? = null, onGrant: () -> Unit) {
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
                if (!a11y) Text("Website rules need Accessibility. Turn it on in Settings › Blocking.", style = UiType.caption, color = c.ink)
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
            // Polish pass: search and the chosen count first; the longer explanation folds away.
            Column(Modifier.padding(horizontal = Dim.gutter, vertical = Dim.s3), verticalArrangement = Arrangement.spacedBy(Dim.s3)) {
                OutlinedTextField(
                    query, { query = it.take(40) }, singleLine = true, placeholder = { Text("Search apps") },
                    shape = RoundedCornerShape(Dim.radius), colors = fieldColors(),
                    modifier = Modifier.fillMaxWidth().semantics { contentDescription = "Search apps" },
                )
                Row(verticalAlignment = Alignment.CenterVertically) {
                    Text("${chosen.size} chosen", style = UiType.label.copy(fontWeight = FontWeight.SemiBold), color = c.ink,
                        modifier = Modifier.weight(1f).semantics { liveRegion = androidx.compose.ui.semantics.LiveRegionMode.Polite })
                }
                SegmentedChoice(
                    listOf(RuleMode.BLOCK_LIST to "Block these", RuleMode.APPROVED_ONLY to "Allow only these"),
                    mode, { mode = it }, label = "Rule type",
                )
                HowBlockingWorks(
                    if (mode == RuleMode.BLOCK_LIST) "The apps you tick are covered during focus. Everything else stays available."
                    else "During focus, only the apps you tick open normally. Everything else is covered.",
                    "Never blocked, so your phone keeps working: phone and emergency calls, contacts, Settings, keyboard, home screen, Samsung and Google Wallet, clock, camera and Lockd In. They aren’t listed here.",
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
                // Polish pass (owner 2026-11-09): each track is its own row. Choosing it (check + tint) and
                // listening to it (the separate round Preview button) are visibly different actions.
                app.lockdin.audio.AudioGroup.values().forEach { g ->
                    Text(g.label, style = UiType.caption, color = c.inkMuted, modifier = Modifier.padding(top = Dim.s2))
                    Column(Modifier.selectableGroup(), verticalArrangement = Arrangement.spacedBy(Dim.s1)) {
                        app.lockdin.audio.AudioCollection.values().filter { it.group == g }.forEach { k ->
                            TrackRow(k, audio.collection == k, onSelect = { onAudio(audio.copy(collection = k)); app.lockdin.audio.StudyPlayer.stopPreview() }) {
                                val previewing = app.lockdin.audio.StudyPlayer.previewing == k
                                if (previewing) app.lockdin.audio.StudyPlayer.stopPreview() else app.lockdin.audio.StudyPlayer.preview(ctx, k, audio.volume)
                            }
                        }
                    }
                }
                if (audio.collection.development) Text(
                    "Development recordings: made by Lockd In for testing. They’ll be replaced with produced recordings.",
                    style = UiType.caption, color = c.inkMuted,
                )
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
            Text(title, style = UiType.row, color = c.ink)
            Text(detail, style = UiType.caption, color = c.inkMuted)
        }
    }
}

/** "How blocking works": the longer explanation, folded away until asked for. */
@Composable
private fun HowBlockingWorks(vararg lines: String) {
    val c = LocalColors.current
    var open by remember { mutableStateOf(false) }
    Column {
        Row(
            Modifier.fillMaxWidth().heightIn(min = Dim.control).clickable(role = Role.Button, onClickLabel = if (open) "Hide" else "Show") { open = !open },
            verticalAlignment = Alignment.CenterVertically,
        ) {
            Text("How blocking works", style = UiType.label.copy(fontWeight = FontWeight.SemiBold), color = c.ink, modifier = Modifier.weight(1f))
            LockdIcon(if (open) LIcon.ChevronUp else LIcon.ChevronDown, size = 18.dp, tint = c.inkMuted)
        }
        androidx.compose.animation.AnimatedVisibility(open) {
            Column(verticalArrangement = Arrangement.spacedBy(Dim.s2)) { lines.forEach { Text(it, style = UiType.caption, color = c.inkMuted) } }
        }
    }
}

/** One study-audio track: tap the row to choose it; the round button on the right only previews. */
@Composable
private fun TrackRow(k: app.lockdin.audio.AudioCollection, on: Boolean, onSelect: () -> Unit, onPreview: () -> Unit) {
    val c = LocalColors.current
    val previewing = app.lockdin.audio.StudyPlayer.previewing == k
    val bg by androidx.compose.animation.animateColorAsState(if (on) c.taupeSoft else androidx.compose.ui.graphics.Color.Transparent, label = "track")
    Row(
        Modifier.fillMaxWidth().clip(RoundedCornerShape(Dim.radius)).background(bg).padding(start = Dim.s3, end = Dim.s1),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        Row(
            Modifier.weight(1f).heightIn(min = 64.dp)
                .selectable(on, role = Role.RadioButton, onClick = onSelect).padding(vertical = Dim.s2),
            verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(Dim.s3),
        ) {
            Box(Modifier.size(22.dp), contentAlignment = Alignment.Center) {
                if (on) LockdIcon(LIcon.Check, size = 22.dp, tint = c.ink)
            }
            Column(Modifier.weight(1f)) {
                Text(k.label, style = UiType.label.copy(fontWeight = if (on) FontWeight.SemiBold else FontWeight.Medium), color = c.ink)
                Text(k.blurb + " · " + if (k.loop) "seamless loop" else "${k.tracks.size} tracks", style = UiType.caption, color = c.inkMuted)
            }
        }
        // A thin rule separates choosing from listening.
        Box(Modifier.width(1.dp).height(32.dp).background(c.line))
        androidx.compose.material3.IconButton(onClick = onPreview, modifier = Modifier.size(48.dp)) {
            Box(Modifier.size(36.dp).border(1.5.dp, c.ink.copy(alpha = 0.5f), CircleShape), contentAlignment = Alignment.Center) {
                LockdIcon(if (previewing) LIcon.Pause else LIcon.Play, size = 16.dp, tint = c.ink,
                    description = if (previewing) "Stop preview of ${k.label}" else "Preview ${k.label}")
            }
        }
    }
}

@Composable
private fun Chip(label: String, on: Boolean, onClick: () -> Unit) {
    val c = LocalColors.current
    // R55: no box; the chosen option gets the sliding underline.
    UnderlineOption(on, onClick, Modifier.semantics { stateDescription = if (on) "Selected" else "Not selected" }) {
        Text(label, style = UiType.label.copy(fontSize = 16.sp, fontWeight = if (on) FontWeight.SemiBold else FontWeight.Medium), color = if (on) c.ink else c.inkMuted, maxLines = 1)
    }
}

private fun Modifier.androidx_selectable(on: Boolean, onClick: () -> Unit): Modifier =
    this.then(Modifier.clickable(role = Role.RadioButton, onClick = onClick).semantics { stateDescription = if (on) "Selected" else "Not selected" })
