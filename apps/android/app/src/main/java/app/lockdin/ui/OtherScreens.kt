package app.lockdin.ui

import androidx.compose.animation.togetherWith

import kotlinx.coroutines.launch
import androidx.compose.ui.platform.testTag

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.selection.selectable
import androidx.compose.foundation.selection.selectableGroup
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.RadioButton
import androidx.compose.material3.RadioButtonDefaults
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.semantics.LiveRegionMode
import androidx.compose.ui.semantics.Role
import androidx.compose.ui.semantics.stateDescription
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.liveRegion
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.semantics.heading
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import app.lockdin.enforce.Commitment
import app.lockdin.state.Profile
import androidx.compose.foundation.clickable
import androidx.compose.foundation.selection.toggleable
import app.lockdin.state.Store
import app.lockdin.state.ThemeChoice
import app.lockdin.state.Tone

/** A screen whose content scrolls on its own (no nesting inside another scroller). */
@Composable
fun ScrollScreen(content: @Composable () -> Unit) {
    Column(
        Modifier.fillMaxSize().verticalScroll(rememberScrollState()).padding(top = LocalTopInset.current).padding(horizontal = Dim.gutter).padding(top = Dim.s2, bottom = Dim.s5),
        verticalArrangement = Arrangement.spacedBy(Dim.s5),
    ) { content() }
}

// ---------------- Profiles ----------------

@Composable
fun ProfilesScreen(store: Store, onOpen: (Profile) -> Unit = {}, onEdit: (Profile) -> Unit = {}, onEditPreset: (String) -> Unit = {}) {
    val c = LocalColors.current
    var naming by rememberSaveable { mutableStateOf(false) }
    ScrollScreen {
        Column(verticalArrangement = Arrangement.spacedBy(Dim.s1)) {
            ScreenTitle("Profiles")
            Text("What each profile blocks on this phone.", style = UiType.caption, color = c.inkMuted)
        }
        if (store.state.profiles.isEmpty()) Text(
            "No profiles yet. A profile is what gets blocked for one kind of work, like Study or Work.",
            style = UiType.body, color = c.inkMuted,
        ) else SettingsGroup {
            store.state.profiles.forEachIndexed { i, p ->
                if (i > 0) GroupDivider()
                Row(
                    Modifier.fillMaxWidth().heightIn(min = 64.dp)
                        .clickable(role = Role.Button, onClickLabel = "Open") { onOpen(p) }
                        .semantics(mergeDescendants = true) {}
                        .padding(horizontal = Dim.s4, vertical = Dim.s3),
                    verticalAlignment = Alignment.CenterVertically,
                ) {
                    Column(Modifier.weight(1f), verticalArrangement = Arrangement.spacedBy(2.dp)) {
                        Text(p.name, style = UiType.label.copy(fontWeight = FontWeight.SemiBold), color = c.ink)
                        Text(rulesSummary(p.rules), style = UiType.caption, color = c.inkMuted, maxLines = 2)
                    }
                    LockdIcon(LIcon.Chevron, size = 18.dp, tint = c.inkMuted)
                }
            }
        }
        SecondaryButton("New profile", { naming = true }, modifier = Modifier.fillMaxWidth())
        Column {
            SectionLabel("Presets")
            if (store.state.presets.isEmpty()) Text(
                "Save a session setup as a preset from New session › Save as preset. Presets start in one tap from Home.",
                style = UiType.caption, color = c.inkMuted,
            ) else SettingsGroup {
                store.state.presets.forEachIndexed { i, p ->
                    if (i > 0) GroupDivider()
                    Row(
                        Modifier.fillMaxWidth().heightIn(min = 64.dp)
                            .clickable(role = Role.Button, onClickLabel = "Edit") { onEditPreset(p.id) }
                            .semantics(mergeDescendants = true) {}
                            .padding(horizontal = Dim.s4, vertical = Dim.s3),
                        verticalAlignment = Alignment.CenterVertically,
                    ) {
                        Column(Modifier.weight(1f), verticalArrangement = Arrangement.spacedBy(2.dp)) {
                            Text(p.name, style = UiType.label.copy(fontWeight = FontWeight.SemiBold), color = c.ink)
                            Text(presetSummary(p, store.state.profiles), style = UiType.caption, color = c.inkMuted, maxLines = 2)
                        }
                        LockdIcon(LIcon.Chevron, size = 18.dp, tint = c.inkMuted)
                    }
                }
            }
        }
    }
    if (naming) NewProfileDialog(onDismiss = { naming = false }) { n ->
        val p = Profile(Store.newId(), n, "Only what you add", false)
        store.update { it.copy(profiles = it.profiles + p) }
        naming = false
        onEdit(p)
    }
}

@Composable
fun NewProfileDialog(onDismiss: () -> Unit, onCreate: (String) -> Unit) {
    val c = LocalColors.current
    var name by rememberSaveable { mutableStateOf("") }
    AlertDialog(
        onDismissRequest = onDismiss,
        containerColor = c.surface,
        title = { Text("New profile", style = Type.title, color = c.ink) },
        text = {
            OutlinedTextField(name, { name = it.take(40) }, placeholder = { Text("e.g. Lab report") },
                singleLine = true, shape = RoundedCornerShape(Dim.radius), colors = fieldColors(),
                modifier = Modifier.fillMaxWidth().semantics { contentDescription = "New profile name" })
        },
        confirmButton = {
            TextButton(onClick = { name.trim().takeIf { it.isNotEmpty() }?.let(onCreate) }, enabled = name.isNotBlank()) {
                Text("Create", style = UiType.label, color = if (name.isNotBlank()) c.ink else c.inkMuted)
            }
        },
        dismissButton = { TextButton(onClick = onDismiss) { Text("Cancel", style = UiType.label, color = c.ink) } },
    )
}

// ---------------- Devices ----------------

@Composable
fun DevicesScreen() {
    ScrollScreen {
        ScreenTitle("Blocking")
        BlockingStatusCard()
        BlockingSetup()
    }
}

/** One line on whether this phone can block right now. */
@Composable
fun BlockingStatusCard() {
    val c = LocalColors.current
    val missing = rememberMissing()
    val ready = missing.isEmpty()
    SettingsGroup {
        Row(Modifier.fillMaxWidth().padding(Dim.s4), verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(Dim.s3)) {
            LockdIcon(LIcon.Phone, size = Dim.icon, tint = c.ink)
            Column(Modifier.weight(1f)) {
                Text(if (ready) "This phone is ready" else "Blocking isn’t set up", style = UiType.label, color = c.ink)
                Text(if (ready) "During focus, blocked apps are covered. Nothing is closed." else "Until then, sessions run as a timer only.",
                    style = UiType.caption, color = c.inkMuted)
            }
            Text(phoneBlockingValue(), style = UiType.value, color = c.ink)
        }
    }
}

// ---------------- Settings ----------------

/** A collapsible settings category: a row with its current value, opening to its controls. */
@Composable
fun SettingsSection(title: String, value: String?, open: Boolean, onToggle: () -> Unit, content: @Composable () -> Unit) {
    val c = LocalColors.current
    Column {
        Row(
            Modifier.fillMaxWidth().heightIn(min = 56.dp)
                .clickable(role = Role.Button, onClickLabel = if (open) "Close $title" else "Open $title", onClick = onToggle)
                .semantics { stateDescription = if (open) "Open" else "Closed" }
                .testTag("section-$title")
                .padding(horizontal = Dim.s3),
            verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(Dim.s2),
        ) {
            Text(title, style = UiType.label, color = c.ink, modifier = Modifier.weight(1f))
            if (!open && value != null) Text(value, style = UiType.caption, color = c.inkMuted, maxLines = 1)
            LockdIcon(if (open) LIcon.ChevronUp else LIcon.ChevronDown, size = 18.dp, tint = c.inkMuted)
        }
        if (open) Column(Modifier.fillMaxWidth().padding(start = Dim.s3, end = Dim.s3, bottom = Dim.s4), verticalArrangement = Arrangement.spacedBy(Dim.s3)) { content() }
    }
}

@Composable
private fun SubHead(text: String) {
    Text(text, style = UiType.section, color = LocalColors.current.inkMuted, modifier = Modifier.padding(top = Dim.s2).testTag("section-$text").semantics { heading() })
}

fun commitmentLabel(c: Commitment): String = when (c) {
    Commitment.FLEXIBLE -> "Flexible"
    Commitment.DELAYED -> "Delayed"
    Commitment.LOCKED -> "Locked"
    else -> "Partner"
}

/** Ending early (also offered per session in setup). Partner details show only when Partner is chosen. */
@Composable
fun EndingEarlySettings(store: Store) {
    val c = LocalColors.current
    val st = store.state
    SettingsGroup {
        Column(Modifier.selectableGroup()) {
            commitmentChoices(st, includeUnsetPartner = true).forEachIndexed { i, (v, l) ->
                if (i > 0) GroupDivider()
                RadioRow(l, st.commitment == v) { store.update { it.copy(commitment = v) } }
            }
        }
    }
    Text(endingEarlyNote(st.commitment), style = UiType.caption, color = c.inkMuted)
    if (st.commitment == Commitment.PARTNER) {
        Spacer(Modifier.height(Dim.s2))
        Text("Your partner", style = UiType.label, color = c.ink, modifier = Modifier.semantics { heading() })
        PartnerSetup(store)
    }
}

fun commitmentChoices(s: app.lockdin.state.AppState, includeUnsetPartner: Boolean = false): List<Pair<Commitment, String>> = buildList {
    add(Commitment.FLEXIBLE to "Flexible: unlock right away")
    add(Commitment.DELAYED to "Delayed: a ${Commitment.DELAYED.overrideDelaySeconds}-second countdown first")
    add(Commitment.LOCKED to "Locked: no unlocking")
    if (s.partner?.claimed == true) add(Commitment.PARTNER to "Partner: ${s.partner.name} approves")
    else if (includeUnsetPartner) add(Commitment.PARTNER to "Partner: someone you trust approves")
}

fun endingEarlyNote(c: Commitment): String = when (c) {
    Commitment.PARTNER -> "To end early, you send your partner a request and they tap Allow. Without them, an emergency end waits ¾ of the time left; calls and messages unlock straight away."
    else -> "Applies to new sessions. Emergency recovery always ends a session immediately."
}

/** Settings categories (owner 2026-11-03: a home list opening to pages, like the Freedom reference). */
enum class SettingsPage(val title: String, val icon: LIcon) {
    ACCOUNT("Account", LIcon.Person), BLOCKING("Blocking", LIcon.Shield), ENDING("Ending early", LIcon.Focus),
    NOTIFICATIONS("Notifications", LIcon.Bell), SESSIONS("Sessions", LIcon.Clock), LOOK("Look and messages", LIcon.Brush),
    CALENDAR("Calendar and places", LIcon.Calendar), DATA("History and data", LIcon.History), HELP("Help and tour", LIcon.Info),
}

/** The open settings page, kept while a full-screen editor (a place, say) is on top. */
object SettingsNav { var page by mutableStateOf<SettingsPage?>(null) }

/** Settings keeps its boxed plates (owner 2026-11-07, R55); the rest of the app is box-free. */
@Composable
fun SettingsScreen(
    store: Store, history: app.lockdin.state.HistoryStore, zone: java.time.ZoneId = java.time.ZoneId.systemDefault(),
    onEditPlace: (app.lockdin.engine.Place) -> Unit = {},
) = androidx.compose.runtime.CompositionLocalProvider(LocalBoxed provides true) { SettingsScreenBody(store, history, zone, onEditPlace) }

@Composable
private fun SettingsScreenBody(
    store: Store, history: app.lockdin.state.HistoryStore, zone: java.time.ZoneId = java.time.ZoneId.systemDefault(),
    onEditPlace: (app.lockdin.engine.Place) -> Unit = {},
) {
    val c = LocalColors.current
    val ctx = androidx.compose.ui.platform.LocalContext.current
    var confirm by remember { mutableStateOf(false) }
    var confirmHistory by remember { mutableStateOf(false) }
    var exportError by remember { mutableStateOf<String?>(null) }
    val st = store.state
    val page = SettingsNav.page
    androidx.activity.compose.BackHandler(enabled = page != null) { SettingsNav.page = null }

    fun summary(p: SettingsPage): String? = when (p) {
        SettingsPage.ACCOUNT -> st.account?.email ?: "Not signed in"
        SettingsPage.BLOCKING -> null
        SettingsPage.ENDING -> commitmentLabel(st.commitment)
        SettingsPage.NOTIFICATIONS -> if (st.reminders.notificationsOn) "On" else "Off"
        SettingsPage.SESSIONS -> "${st.last.focusMinutes} min"
        SettingsPage.LOOK -> when (st.theme) { ThemeChoice.SYSTEM -> "System"; ThemeChoice.LIGHT -> "Light"; ThemeChoice.DARK -> "Dark" }
        SettingsPage.CALENDAR -> if (st.calendarOn) "Calendar on" else null
        SettingsPage.DATA, SettingsPage.HELP -> null
    }

    // R55: pages slide in from the right and back out to the list.
    val stillPages = reducedMotion()
    androidx.compose.animation.AnimatedContent(
        targetState = page, label = "settings",
        transitionSpec = {
            if (stillPages) androidx.compose.animation.EnterTransition.None togetherWith androidx.compose.animation.ExitTransition.None
            else {
                val dir = if (targetState != null) 1 else -1
                (androidx.compose.animation.slideInHorizontally(androidx.compose.animation.core.tween(260)) { w -> dir * w / 5 } +
                    androidx.compose.animation.fadeIn(androidx.compose.animation.core.tween(200))) togetherWith
                    (androidx.compose.animation.slideOutHorizontally(androidx.compose.animation.core.tween(200)) { w -> -dir * w / 8 } +
                        androidx.compose.animation.fadeOut(androidx.compose.animation.core.tween(150)))
            }
        },
    ) { page ->
    if (page == null) {
        ScrollScreen {
            ScreenTitle("Settings")
            val groups = listOf(
                listOf(SettingsPage.ACCOUNT, SettingsPage.BLOCKING, SettingsPage.ENDING, SettingsPage.NOTIFICATIONS),
                listOf(SettingsPage.SESSIONS, SettingsPage.LOOK, SettingsPage.CALENDAR),
                listOf(SettingsPage.DATA, SettingsPage.HELP),
            )
            groups.forEach { g ->
                SettingsGroup {
                    g.forEachIndexed { i, p ->
                        if (i > 0) GroupDivider()
                        Box(Modifier.testTag("settings-${p.title}")) {
                            SettingRow(p.title, if (p == SettingsPage.BLOCKING) phoneBlockingValue() else summary(p) ?: "", icon = p.icon, onClick = { SettingsNav.page = p })
                        }
                    }
                }
            }
        }
    } else ScrollScreen {
        Row(verticalAlignment = Alignment.CenterVertically, modifier = Modifier.padding(top = Dim.s1)) {
            androidx.compose.material3.IconButton(onClick = { SettingsNav.page = null }) { LockdIcon(LIcon.Back, description = "Back to Settings") }
            ScreenTitle(page.title)
        }
        when (page) {
            SettingsPage.ACCOUNT -> AccountSettings(store)
            SettingsPage.BLOCKING -> { BlockingStatusCard(); BlockingSetup() }
            SettingsPage.ENDING -> EndingEarlySettings(store)
            SettingsPage.NOTIFICATIONS -> RemindersSettings(store)
            SettingsPage.SESSIONS -> {
                SubHead("Starting a session")
                SettingsGroup {
                    SwitchRow("Haptic clicks", "The dial clicks as it turns when you start focus. Follows your phone’s touch vibration setting.",
                        st.startHaptics) { v -> store.update { it.copy(startHaptics = v) } }
                    GroupDivider()
                    SwitchRow("Click sound", "A quiet mechanical click. Follows your phone’s touch sounds and silent mode.",
                        st.startSound) { v -> store.update { it.copy(startSound = v) } }
                }
                SubHead("New session defaults")
                DefaultsSettings(store)
                SubHead("Check-ins and coaching")
                CoachingSettings(store)
            }
            SettingsPage.LOOK -> {
                SubHead("Appearance")
                SegmentedChoice(listOf(ThemeChoice.SYSTEM to "System", ThemeChoice.LIGHT to "Light", ThemeChoice.DARK to "Dark"), st.theme,
                    { v -> store.update { it.copy(theme = v) } }, label = "Appearance")
                SubHead("Look and background")
                LookSettings(store)
                SubHead("Messages")
                SettingsGroup {
                    Column(Modifier.selectableGroup()) {
                        listOf(Tone.CALM to "Calm", Tone.DIRECT to "Direct", Tone.PLAYFUL to "Playful", Tone.CUSTOM to "My own words", Tone.QUIET to "Off").forEachIndexed { i, (v, l) ->
                            if (i > 0) GroupDivider()
                            RadioRow(l, st.tone == v) { store.update { it.copy(tone = v) } }
                        }
                    }
                }
                Text(message(st.tone, Moment.BREAK_ENDING, "TAM 212", custom = st.customMessages, seed = "example")?.let { "Example: “$it”" } ?: "No messages during sessions.", style = UiType.caption, color = c.inkMuted,
                    modifier = Modifier.semantics { liveRegion = LiveRegionMode.Polite })
                if (st.tone == Tone.CUSTOM) MessageListEditor(store)
                if (st.tone != Tone.QUIET) AppMessagesEditor(store)
            }
            SettingsPage.CALENDAR -> {
                SubHead("Calendar")
                CalendarSettings(store)
                SubHead("Places")
                PlacesSettings(store, onEditPlace)
            }
            SettingsPage.DATA -> {
                SettingsGroup {
                    SettingMenuRow(
                        "Keep history", app.lockdin.engine.History.RETENTION_CHOICES.map { it to if (it == 0) "Forever" else "$it days" },
                        st.historyDays, { v ->
                            store.update { it.copy(historyDays = v) }
                            history.prune(v, System.currentTimeMillis())
                        },
                    )
                    GroupDivider()
                    SettingRow("Export as CSV", "", supporting = "${history.entries.size} session${if (history.entries.size == 1) "" else "s"}. Opens Android’s share sheet.",
                        enabled = history.entries.isNotEmpty(), onClick = {
                            exportError = runCatching { shareHistoryCsv(ctx, history, zone) }.exceptionOrNull()?.let { "Couldn’t create the file. Try again." }
                        })
                    GroupDivider()
                    Row(Modifier.padding(horizontal = Dim.s2)) { TextAction("Delete all history", { confirmHistory = true }, danger = true) }
                }
                exportError?.let { Text(it, style = UiType.caption, color = c.danger) }
                PrivacySettings()
                SettingsGroup {
                    SettingRow("Stored on this phone", "", supporting = "Excluded from backups. Your account and a partner setup use the internet.", chevron = null)
                    GroupDivider()
                    Row(Modifier.padding(horizontal = Dim.s2)) { TextAction("Delete Lockd In data", { confirm = true }, danger = true) }
                }
            }
            SettingsPage.HELP -> SettingsGroup {
                SettingRow("Replay the tour", "", supporting = "A few tips on the main screen. You can end it any time.", onClick = {
                    SettingsNav.page = null
                    store.update { it.copy(tourStep = 0) }
                    Resumes.openRoute("focus")
                })
            }
        }
    }
    }
    if (confirmHistory) {
        AlertDialog(
            onDismissRequest = { confirmHistory = false },
            title = { Text("Delete all history?", style = Type.title) },
            text = { Text("Removes every saved session from this phone. Your schedule, profiles and presets stay. This can’t be undone.", style = UiType.body) },
            confirmButton = { TextButton(onClick = { history.clear(); confirmHistory = false }) { Text("Delete", style = UiType.label, color = c.danger) } },
            dismissButton = { TextButton(onClick = { confirmHistory = false }) { Text("Cancel", style = UiType.label, color = c.ink) } },
            containerColor = c.surface,
        )
    }
    if (confirm) {
        AlertDialog(
            onDismissRequest = { confirm = false },
            title = { Text("Delete Lockd In data?", style = Type.title) },
            text = { Text("Removes your schedule, custom profiles, presets, history and any session in progress. This can’t be undone.", style = UiType.body) },
            confirmButton = { TextButton(onClick = { store.clearAll(); history.clear(); confirm = false }) { Text("Delete", style = UiType.label, color = c.danger) } },
            dismissButton = { TextButton(onClick = { confirm = false }) { Text("Cancel", style = UiType.label, color = c.ink) } },
            containerColor = c.surface,
        )
    }
}

/** Writes the history CSV to app cache and opens the share sheet (FileProvider; read permission only). */
private fun shareHistoryCsv(ctx: android.content.Context, history: app.lockdin.state.HistoryStore, zone: java.time.ZoneId) {
    val dir = java.io.File(ctx.cacheDir, "exports").apply { mkdirs() }
    val file = java.io.File(dir, "lockd-in-history.csv")
    file.writeText(app.lockdin.engine.History.csv(history.entries, zone))
    val uri = androidx.core.content.FileProvider.getUriForFile(ctx, "${ctx.packageName}.files", file)
    val send = android.content.Intent(android.content.Intent.ACTION_SEND).setType("text/csv")
        .putExtra(android.content.Intent.EXTRA_STREAM, uri)
        .addFlags(android.content.Intent.FLAG_GRANT_READ_URI_PERMISSION)
    ctx.startActivity(android.content.Intent.createChooser(send, "Export history"))
}

@Composable
private fun RadioRow(label: String, selected: Boolean, onClick: () -> Unit) {
    val c = LocalColors.current
    Row(
        Modifier.fillMaxWidth().heightIn(min = Dim.control + 4.dp).selectable(selected, role = Role.RadioButton, onClick = onClick).padding(horizontal = Dim.s4),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        Text(label, style = UiType.body, color = c.ink, modifier = Modifier.weight(1f))
        RadioButton(selected = selected, onClick = null, colors = RadioButtonDefaults.colors(selectedColor = c.ink, unselectedColor = c.controlLine))
    }
}

/** Reminders and notifications: what Lockd In may tell you, and when not to. */
@Composable
private fun RemindersSettings(store: Store) {
    val c = LocalColors.current
    val ctx = androidx.compose.ui.platform.LocalContext.current
    val r = store.state.reminders
    fun set(f: (app.lockdin.engine.ReminderSettings) -> app.lockdin.engine.ReminderSettings) = store.update { it.copy(reminders = f(it.reminders)) }
    val allowed = remember(Resumes.tick) { app.lockdin.sched.Notifier.allowed(ctx) }
    val fmt = java.time.format.DateTimeFormatter.ofPattern("h:mm a")
    val systemSettings = {
        ctx.startActivity(android.content.Intent(android.provider.Settings.ACTION_APP_NOTIFICATION_SETTINGS)
            .putExtra(android.provider.Settings.EXTRA_APP_PACKAGE, ctx.packageName))
    }
    // Android 13+: ask once in the app; if Android won't ask again, open its settings page.
    val ask = androidx.activity.compose.rememberLauncherForActivityResult(
        androidx.activity.result.contract.ActivityResultContracts.RequestPermission(),
    ) { granted -> Resumes.bump(); if (!granted) systemSettings() }
    Column {
        SectionLabel("Notifications")
        SettingsGroup {
            SwitchRow("Allow notifications", "Reminders, focus and break notices, and blocking alerts from Lockd In", r.notificationsOn) { v -> set { it.copy(notificationsOn = v) } }
            if (r.notificationsOn) {
                GroupDivider()
                SwitchRow("Reminders before sessions", "Each planned session sets how long before", r.remindersOn) { v -> set { it.copy(remindersOn = v) } }
                GroupDivider()
                SwitchRow("Focus and break notices", "Break starting, focus back on, session complete", r.sessionEventsOn) { v -> set { it.copy(sessionEventsOn = v) } }
                GroupDivider()
                SwitchRow("Follow-up after a missed session", "One gentle note offering a 10-minute start", r.missedFollowUpOn) { v -> set { it.copy(missedFollowUpOn = v) } }
                GroupDivider()
                SwitchRow("Blocking problems", "If blocking stops during focus, for example when a permission is turned off", r.blockingAlertsOn) { v -> set { it.copy(blockingAlertsOn = v) } }
                GroupDivider()
                SwitchRow(
                    "Quiet hours", r.quietStart?.let { "${it.format(fmt)} – ${r.quietEnd!!.format(fmt)}: reminders and follow-ups wait" } ?: "Off",
                    r.quietStart != null,
                ) { v -> set { if (v) it.copy(quietStart = java.time.LocalTime.of(22, 0), quietEnd = java.time.LocalTime.of(7, 0)) else it.copy(quietStart = null, quietEnd = null) } }
                if (r.quietStart != null) {
                    GroupDivider()
                    SettingRow("Quiet from", r.quietStart.format(fmt), onClick = {
                        android.app.TimePickerDialog(ctx, { _, h, m -> set { it.copy(quietStart = java.time.LocalTime.of(h, m)) } }, r.quietStart.hour, r.quietStart.minute, false).show()
                    })
                    GroupDivider()
                    SettingRow("Quiet until", r.quietEnd!!.format(fmt), onClick = {
                        android.app.TimePickerDialog(ctx, { _, h, m -> set { it.copy(quietEnd = java.time.LocalTime.of(h, m)) } }, r.quietEnd.hour, r.quietEnd.minute, false).show()
                    })
                }
                GroupDivider()
                SettingMenuRow("At most", listOf(2, 4, 6).map { it to "$it per hour" }, r.maxPerHour, { v -> set { it.copy(maxPerHour = v) } })
            }
            GroupDivider()
            if (!allowed) SettingRow("Turn on notifications", "Off in Android", supporting = "Android is blocking Lockd In’s notifications", onClick = {
                if (android.os.Build.VERSION.SDK_INT >= 33) ask.launch(android.Manifest.permission.POST_NOTIFICATIONS) else systemSettings()
            })
            else SettingRow("Android notification settings", "", supporting = "Sound, vibration and each kind’s own switch", onClick = systemSettings)
        }
        Spacer(Modifier.height(Dim.s2))
        Text(
            when {
                !allowed -> "Notifications are off for Lockd In in Android, so none of these can be shown."
                !r.notificationsOn -> "All Lockd In notifications are off. While blocking runs, Android still shows the small ongoing “Focusing” notice; you can minimise it in Android’s settings."
                else -> "While blocking runs, Android requires an ongoing “Focusing” notice. It can’t be turned off, but you can minimise it in Android’s settings."
            },
            style = UiType.caption, color = c.inkMuted,
        )
    }
}

@Composable
fun SwitchRow(label: String, supporting: String, on: Boolean, onChange: (Boolean) -> Unit) {
    val c = LocalColors.current
    Row(
        Modifier.fillMaxWidth().heightIn(min = Dim.control + 4.dp)
            .toggleable(on, role = Role.Switch, onValueChange = onChange)
            .padding(horizontal = Dim.s4, vertical = Dim.s2),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        Column(Modifier.weight(1f)) {
            Text(label, style = UiType.label, color = c.ink)
            Text(supporting, style = UiType.caption, color = c.inkMuted)
        }
        androidx.compose.material3.Switch(
            checked = on, onCheckedChange = null,
            colors = androidx.compose.material3.SwitchDefaults.colors(checkedTrackColor = c.ink, checkedThumbColor = c.onInk, uncheckedTrackColor = c.taupeSoft, uncheckedBorderColor = c.controlLine, uncheckedThumbColor = c.controlLine),
        )
    }
}


/** Settings › Look (C2): optional background and quotes, each with its own switch. Off by default. */
@Composable
private fun LookSettings(store: Store) {
    val c = LocalColors.current
    val ctx = androidx.compose.ui.platform.LocalContext.current
    val scope = androidx.compose.runtime.rememberCoroutineScope()
    var photoError by remember { mutableStateOf<String?>(null) }
    val pick = androidx.activity.compose.rememberLauncherForActivityResult(androidx.activity.result.contract.ActivityResultContracts.PickVisualMedia()) { uri ->
        if (uri != null) scope.launch {
            val ok = kotlinx.coroutines.withContext(kotlinx.coroutines.Dispatchers.IO) { savePhoto(ctx, uri) }
            if (ok) { photoError = null; store.update { it.copy(backdrop = "PHOTO") } } else photoError = "Couldn’t use that photo. Try another."
        }
    }
    val current = runCatching { Backdrop.valueOf(store.state.backdrop) }.getOrDefault(Backdrop.NONE)
    Column {
        SectionLabel("Look")
        Text("Background", style = UiType.label, color = c.ink)
        Spacer(Modifier.height(Dim.s2))
        // Picker with real previews of each background (the same drawing, scaled down).
        androidx.compose.foundation.lazy.LazyRow(horizontalArrangement = Arrangement.spacedBy(Dim.s2)) {
            items(Backdrop.values().size) { i ->
                val b = Backdrop.values()[i]
                val on = b == current
                Column(
                    Modifier.width(92.dp).testTag("bg-${b.name}").selectable(on, role = Role.RadioButton, onClick = {
                        if (b == Backdrop.PHOTO && !photoFile(ctx).exists()) pick.launch(androidx.activity.result.PickVisualMediaRequest(androidx.activity.result.contract.ActivityResultContracts.PickVisualMedia.ImageOnly))
                        else store.update { it.copy(backdrop = b.name) }
                    }),
                    horizontalAlignment = Alignment.CenterHorizontally,
                ) {
                    androidx.compose.material3.Surface(
                        shape = RoundedCornerShape(12.dp), color = c.ground,
                        border = androidx.compose.foundation.BorderStroke(if (on) 2.dp else 1.dp, if (on) c.ink else c.line),
                        modifier = Modifier.width(92.dp).height(164.dp),
                    ) {
                        Box(contentAlignment = Alignment.Center) {
                            BackdropLayer(b, store.state.backdropWords, Modifier.fillMaxSize())
                            if (b == Backdrop.NONE) Text("Plain", style = UiType.caption, color = c.inkMuted)
                            if (b == Backdrop.PHOTO && !photoFile(ctx).exists()) LockdIcon(LIcon.Plus, tint = c.inkMuted, description = null)
                        }
                    }
                    Spacer(Modifier.height(Dim.s1))
                    Text(b.label, style = UiType.caption, color = if (on) c.ink else c.inkMuted, maxLines = 2,
                        textAlign = androidx.compose.ui.text.style.TextAlign.Center)
                }
            }
        }
        Spacer(Modifier.height(Dim.s3))
        SettingsGroup {
            if (current == Backdrop.WORDS) {
                var words by remember { mutableStateOf(store.state.backdropWords) }
                Column(Modifier.padding(horizontal = Dim.s4, vertical = Dim.s3)) {
                    OutlinedTextField(words, { words = it.take(40); store.update { s -> s.copy(backdropWords = words.trim().ifEmpty { "One thing at a time" }) } },
                        singleLine = true, label = { Text("Your words") }, shape = RoundedCornerShape(Dim.radius), colors = fieldColors(),
                        modifier = Modifier.fillMaxWidth())
                }
            }
            if (current == Backdrop.PHOTO) {
                SettingRow("Choose another photo", "", onClick = {
                    pick.launch(androidx.activity.result.PickVisualMediaRequest(androidx.activity.result.contract.ActivityResultContracts.PickVisualMedia.ImageOnly))
                })
            }
            if (current == Backdrop.WORDS || current == Backdrop.PHOTO) GroupDivider()
            SwitchRow("Quotes", "One short line per session, on the session and block screens. It doesn’t change during a round.", store.state.quotesOn) { v ->
                store.update { it.copy(quotesOn = v) }
            }
        }
        Spacer(Modifier.height(Dim.s2))
        Text(photoError ?: "Shown behind the Focus screen, locked or not. The lock and text stay the same; photos are dimmed so text stays readable. Your photo stays on this phone.",
            style = UiType.caption, color = if (photoError != null) c.danger else c.inkMuted)
    }
}

/** Copies a picked photo into app storage, scaled to at most 1600 px. */
private fun savePhoto(ctx: android.content.Context, uri: android.net.Uri): Boolean = runCatching {
    val bounds = android.graphics.BitmapFactory.Options().apply { inJustDecodeBounds = true }
    ctx.contentResolver.openInputStream(uri)?.use { android.graphics.BitmapFactory.decodeStream(it, null, bounds) }
    var sample = 1
    while (maxOf(bounds.outWidth, bounds.outHeight) / sample > 1600) sample *= 2
    val bmp = ctx.contentResolver.openInputStream(uri)?.use {
        android.graphics.BitmapFactory.decodeStream(it, null, android.graphics.BitmapFactory.Options().apply { inSampleSize = sample })
    } ?: return false
    photoFile(ctx).outputStream().use { bmp.compress(android.graphics.Bitmap.CompressFormat.JPEG, 85, it) }
    true
}.getOrDefault(false)


/** "My own words": up to 10 lines; one is shown per session (D). */
@Composable
private fun MessageListEditor(store: Store) {
    val c = LocalColors.current
    var text by remember { mutableStateOf("") }
    Column(verticalArrangement = Arrangement.spacedBy(Dim.s2)) {
        Text("Your lines. One is used per session, on the session and block screens.", style = UiType.caption, color = c.inkMuted)
        if (store.state.customMessages.isNotEmpty()) SettingsGroup {
            store.state.customMessages.forEachIndexed { i, m ->
                if (i > 0) GroupDivider()
                Row(Modifier.fillMaxWidth().heightIn(min = Dim.control + 4.dp).padding(start = Dim.s4, end = Dim.s2), verticalAlignment = Alignment.CenterVertically) {
                    Text(m, style = UiType.body, color = c.ink, modifier = Modifier.weight(1f))
                    TextAction("Remove", { store.update { s -> s.copy(customMessages = s.customMessages.filterIndexed { j, _ -> j != i }) } })
                }
            }
        } else Text("No lines yet: Calm messages are used until you add one.", style = UiType.caption, color = c.inkMuted)
        if (store.state.customMessages.size < 10) Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(Dim.s2)) {
            OutlinedTextField(text, { text = it.take(140) }, singleLine = true, placeholder = { Text("e.g. Finish the problem set, then rest.") },
                shape = RoundedCornerShape(Dim.radius), colors = fieldColors(), modifier = Modifier.weight(1f).semantics { contentDescription = "New message" })
            SecondaryButton("Add", {
                text.trim().takeIf { it.isNotEmpty() }?.let { m -> store.update { s -> s.copy(customMessages = (s.customMessages + m).take(10)) }; text = "" }
            })
        }
    }
}

/** A message for a specific app's block screen (D), e.g. "Instagram can wait until 9." */
@Composable
private fun AppMessagesEditor(store: Store) {
    val c = LocalColors.current
    val ctx = androidx.compose.ui.platform.LocalContext.current
    var adding by remember { mutableStateOf(false) }
    Column(verticalArrangement = Arrangement.spacedBy(Dim.s2)) {
        Text("Messages for specific apps", style = UiType.label.copy(fontWeight = FontWeight.SemiBold), color = c.ink)
        if (store.state.appMessages.isNotEmpty()) SettingsGroup {
            store.state.appMessages.entries.sortedBy { it.key }.forEachIndexed { i, (pkg, m) ->
                if (i > 0) GroupDivider()
                Row(Modifier.fillMaxWidth().heightIn(min = 56.dp).padding(start = Dim.s4, end = Dim.s2, top = Dim.s2, bottom = Dim.s2), verticalAlignment = Alignment.CenterVertically) {
                    Column(Modifier.weight(1f)) {
                        Text(remember(pkg) { app.lockdin.enforce.InstalledApps.label(ctx, pkg) }, style = UiType.label, color = c.ink)
                        Text(m, style = UiType.caption, color = c.inkMuted)
                    }
                    TextAction("Remove", { store.update { s -> s.copy(appMessages = s.appMessages - pkg) } })
                }
            }
        }
        if (store.state.appMessages.size < 50) SecondaryButton("Add a message for an app", { adding = true }, modifier = Modifier.fillMaxWidth())
    }
    if (adding) AppMessageDialog(onDismiss = { adding = false }) { pkg, m ->
        store.update { s -> s.copy(appMessages = s.appMessages + (pkg to m)) }
        adding = false
    }
}

@Composable
private fun AppMessageDialog(onDismiss: () -> Unit, onSave: (String, String) -> Unit) {
    val c = LocalColors.current
    val ctx = androidx.compose.ui.platform.LocalContext.current
    val apps by androidx.compose.runtime.produceState<List<app.lockdin.enforce.LaunchableApp>>(emptyList()) {
        value = kotlinx.coroutines.withContext(kotlinx.coroutines.Dispatchers.IO) { runCatching { app.lockdin.enforce.InstalledApps.launchable(ctx) }.getOrDefault(emptyList()) }
    }
    var pkg by remember { mutableStateOf<String?>(null) }
    var text by remember { mutableStateOf("") }
    AlertDialog(
        onDismissRequest = onDismiss, containerColor = c.surface,
        title = { Text("Message for an app", style = Type.title, color = c.ink) },
        text = {
            Column(verticalArrangement = Arrangement.spacedBy(Dim.s3)) {
                if (apps.isEmpty()) Text("Loading apps…", style = UiType.caption, color = c.inkMuted)
                else SettingMenuRow("App", apps.map { it.packageName to it.label }, pkg ?: "", { pkg = it })
                OutlinedTextField(text, { text = it.take(140) }, placeholder = { Text("e.g. Instagram can wait until 9.") },
                    shape = RoundedCornerShape(Dim.radius), colors = fieldColors(), modifier = Modifier.fillMaxWidth().semantics { contentDescription = "App message" })
            }
        },
        confirmButton = {
            TextButton(onClick = { val p = pkg; if (p != null && text.isNotBlank()) onSave(p, text.trim()) }, enabled = pkg != null && text.isNotBlank()) {
                Text("Save", style = UiType.label, color = c.ink)
            }
        },
        dismissButton = { TextButton(onClick = onDismiss) { Text("Cancel", style = UiType.label, color = c.ink) } },
    )
}

/** Settings › Coaching (D): question timing is offered in each session; check-ins are opt-in. */
@Composable
private fun CoachingSettings(store: Store) {
    val c = LocalColors.current
    Column {
        SectionLabel("Check-ins")
        SettingsGroup {
            SettingMenuRow("Still working? check-in", app.lockdin.engine.CheckIns.IDLE_CHOICES.map { it to if (it == 0) "Off" else "After $it min idle" },
                store.state.checkInMinutes, { v -> store.update { it.copy(checkInMinutes = v) } })
        }
        Spacer(Modifier.height(Dim.s2))
        Text(
            "During focus, if the phone hasn’t been used for that long, a silent notification asks if you’re still working. Working on paper counts. It only looks at when the screen was last used, never what’s on it, and isn’t sent in a session’s first 10 minutes.",
            style = UiType.caption, color = c.inkMuted,
        )
    }
}


/** Settings › New session defaults: what setup starts with (release readiness). */
@Composable
private fun DefaultsSettings(store: Store) {
    val l = store.state.last
    fun set(f: (app.lockdin.state.LastChoice) -> app.lockdin.state.LastChoice) = store.update { it.copy(last = f(it.last)) }
    Column {
        SectionLabel("New session defaults")
        SettingsGroup {
            SettingMenuRow("Focus length", (listOf(15, 20, 25, 30, 40, 45, 50, 60, 90) + l.focusMinutes).distinct().sorted().map { it to "$it min" }, l.focusMinutes, { v -> set { it.copy(focusMinutes = v) } }, icon = LIcon.Clock)
            GroupDivider()
            SettingMenuRow("Rounds", (1..6).map { it to if (it == 1) "1 round" else "$it rounds" }, l.rounds, { v -> set { it.copy(rounds = v) } }, icon = LIcon.Repeat)
            GroupDivider()
            SettingMenuRow("Breaks", listOf(5, 10, 15, 20).map { it to "$it min" }, l.breakMinutes.coerceIn(5, 20), { v -> set { it.copy(breakMinutes = v) } }, icon = LIcon.Coffee)
            GroupDivider()
            SettingMenuRow("Profile", store.state.profiles.map { it.id to it.name }, l.profileId, { v -> set { it.copy(profileId = v) } }, icon = LIcon.Book)
        }
    }
}

/** Settings › Privacy: every permission Lockd In can use, whether it's on, and what it's for. */
@Composable
private fun PrivacySettings() {
    val c = LocalColors.current
    val ctx = androidx.compose.ui.platform.LocalContext.current
    val tick = Resumes.tick
    fun granted(p: String) = ctx.checkSelfPermission(p) == android.content.pm.PackageManager.PERMISSION_GRANTED
    val rows = remember(tick) {
        listOf(
            Triple("Accessibility", app.lockdin.enforce.Capabilities.accessibility(ctx), "Sees which app is in front and, in supported browsers, the web address, during focus only"),
            Triple("Usage access", app.lockdin.enforce.Capabilities.usageAccess(ctx), "Which app is in front, as a fallback to Accessibility"),
            Triple("Display over other apps", android.provider.Settings.canDrawOverlays(ctx), "Lets the block screen appear over a blocked app"),
            Triple("Notifications", app.lockdin.sched.Notifier.allowed(ctx), "Reminders, session notices and blocking problems"),
            Triple("Calendar", granted(android.Manifest.permission.READ_CALENDAR), "Read-only, for free-time suggestions"),
            Triple("Location", granted(android.Manifest.permission.ACCESS_FINE_LOCATION), "Only for places you save; no history"),
        )
    }
    Column {
        SectionLabel("Privacy")
        SettingsGroup {
            rows.forEachIndexed { i, (name, on, what) ->
                if (i > 0) GroupDivider()
                SettingRow(name, if (on) "On" else "Off", supporting = what, onClick = {
                    ctx.startActivity(android.content.Intent(android.provider.Settings.ACTION_APPLICATION_DETAILS_SETTINGS, android.net.Uri.fromParts("package", ctx.packageName, null)))
                })
            }
        }
        Spacer(Modifier.height(Dim.s2))
        Text(
            "Everything Lockd In knows stays on this phone and is excluded from backups: your settings, schedule, profiles, presets, history and saved places. Only two things use the internet. With an account, the Lockd In service keeps your email, first name and a scrambled copy of your password. With a partner, it keeps your partner’s first name, an encrypted key, requests to end a session early, and messages you send to admin. Tap a permission to change it in Android.",
            style = UiType.caption, color = c.inkMuted,
        )
    }
}
