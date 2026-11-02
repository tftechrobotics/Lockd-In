package app.lockdin.ui

import kotlinx.coroutines.launch

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
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
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.liveRegion
import androidx.compose.ui.semantics.semantics
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
        Modifier.fillMaxSize().verticalScroll(rememberScrollState()).padding(horizontal = Dim.gutter).padding(top = Dim.s2, bottom = Dim.s5),
        verticalArrangement = Arrangement.spacedBy(Dim.s5),
    ) { content() }
}

// ---------------- Profiles ----------------

@Composable
fun ProfilesScreen(store: Store, onEdit: (Profile) -> Unit = {}, onEditPreset: (String) -> Unit = {}) {
    val c = LocalColors.current
    var naming by rememberSaveable { mutableStateOf(false) }
    ScrollScreen {
        Column(verticalArrangement = Arrangement.spacedBy(Dim.s1)) {
            ScreenTitle("Profiles")
            Text("What each profile blocks on this phone.", style = UiType.caption, color = c.inkMuted)
        }
        SettingsGroup {
            store.state.profiles.forEachIndexed { i, p ->
                if (i > 0) GroupDivider()
                Row(
                    Modifier.fillMaxWidth().heightIn(min = 64.dp)
                        .clickable(role = Role.Button, onClickLabel = "Edit") { onEdit(p) }
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
private fun NewProfileDialog(onDismiss: () -> Unit, onCreate: (String) -> Unit) {
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
    val c = LocalColors.current
    ScrollScreen {
        ScreenTitle("Devices")
        Column {
            SectionLabel("This phone")
            SettingsGroup {
                SettingRow("Blocking", phoneBlockingValue(), icon = LIcon.Phone, supporting = "Covers blocked apps with a block screen during focus. Apps aren’t closed.", chevron = null)
            }
        }
        Column {
            SectionLabel("Set up blocking")
            BlockingSetup()
        }
        Column {
            SectionLabel("Other devices")
            SettingsGroup {
                SettingRow("Windows PC", "Not paired", icon = LIcon.Laptop, supporting = "Pairing arrives in a later update")
                GroupDivider()
                SettingRow("Browser", "Not paired", icon = LIcon.Devices, supporting = "Plans and times sessions; can’t restrict apps")
            }
        }
    }
}

// ---------------- Settings ----------------

@Composable
fun SettingsScreen(store: Store, history: app.lockdin.state.HistoryStore, zone: java.time.ZoneId = java.time.ZoneId.systemDefault()) {
    val c = LocalColors.current
    val ctx = androidx.compose.ui.platform.LocalContext.current
    var confirm by remember { mutableStateOf(false) }
    var confirmHistory by remember { mutableStateOf(false) }
    var exportError by remember { mutableStateOf<String?>(null) }
    ScrollScreen {
        ScreenTitle("Settings")
        Column {
            SectionLabel("Appearance")
            SegmentedChoice(listOf(ThemeChoice.SYSTEM to "System", ThemeChoice.LIGHT to "Light", ThemeChoice.DARK to "Dark"), store.state.theme,
                { v -> store.update { it.copy(theme = v) } }, label = "Appearance")
        }
        Column {
            SectionLabel("Messages")
            SettingsGroup {
                Column(Modifier.selectableGroup()) {
                    listOf(Tone.CALM to "Calm", Tone.DIRECT to "Direct", Tone.PLAYFUL to "Playful", Tone.CUSTOM to "My own words", Tone.QUIET to "Off").forEachIndexed { i, (v, l) ->
                        if (i > 0) GroupDivider()
                        RadioRow(l, store.state.tone == v) { store.update { it.copy(tone = v) } }
                    }
                }
            }
            Spacer(Modifier.height(Dim.s2))
            Text(message(store.state.tone, Moment.BREAK_ENDING, "TAM 212", custom = store.state.customMessages, seed = "example")?.let { "Example: “$it”" } ?: "No messages during sessions.", style = UiType.caption, color = c.inkMuted,
                modifier = Modifier.semantics { liveRegion = LiveRegionMode.Polite })
            if (store.state.tone == Tone.CUSTOM) { Spacer(Modifier.height(Dim.s3)); MessageListEditor(store) }
            if (store.state.tone != Tone.QUIET) { Spacer(Modifier.height(Dim.s3)); AppMessagesEditor(store) }
        }
        CoachingSettings(store)
        LookSettings(store)
        RemindersSettings(store)
        CalendarSettings(store)
        Column {
            SectionLabel("Starting a session")
            SettingsGroup {
                SwitchRow("Haptic clicks", "The dial clicks as it turns when you start focus. Follows your phone’s touch vibration setting.",
                    store.state.startHaptics) { v -> store.update { it.copy(startHaptics = v) } }
                GroupDivider()
                SwitchRow("Click sound", "A quiet mechanical click. Follows your phone’s touch sounds and silent mode.",
                    store.state.startSound) { v -> store.update { it.copy(startSound = v) } }
            }
        }
        Column {
            SectionLabel("Ending a session early")
            SettingsGroup {
                Column(Modifier.selectableGroup()) {
                    listOf(
                        Commitment.FLEXIBLE to "Flexible: unlock right away",
                        Commitment.DELAYED to "Delayed: a ${Commitment.DELAYED.overrideDelaySeconds}-second countdown first",
                        Commitment.LOCKED to "Locked: no unlocking",
                    ).forEachIndexed { i, (v, l) ->
                        if (i > 0) GroupDivider()
                        RadioRow(l, store.state.commitment == v) { store.update { it.copy(commitment = v) } }
                    }
                }
            }
            Spacer(Modifier.height(Dim.s2))
            Text("Applies to new sessions. Emergency recovery always ends a session immediately, at every level.", style = UiType.caption, color = c.inkMuted)
        }
        Column {
            SectionLabel("History")
            SettingsGroup {
                SettingMenuRow(
                    "Keep history", app.lockdin.engine.History.RETENTION_CHOICES.map { it to if (it == 0) "Forever" else "$it days" },
                    store.state.historyDays, { v ->
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
        }
        Column {
            SectionLabel("Data")
            SettingsGroup {
                SettingRow("Stored on this phone only", "", supporting = "Excluded from backups. Nothing is sent anywhere.", chevron = null)
                GroupDivider()
                Row(Modifier.padding(horizontal = Dim.s2)) { TextAction("Delete Lockd In data", { confirm = true }, danger = true) }
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
        SettingsGroup {
            SettingMenuRow("Background", Backdrop.values().map { it to it.label }, current, { b ->
                if (b == Backdrop.PHOTO && !photoFile(ctx).exists()) pick.launch(androidx.activity.result.PickVisualMediaRequest(androidx.activity.result.contract.ActivityResultContracts.PickVisualMedia.ImageOnly))
                else store.update { it.copy(backdrop = b.name) }
            })
            if (current == Backdrop.PHOTO) {
                GroupDivider()
                SettingRow("Choose another photo", "", onClick = {
                    pick.launch(androidx.activity.result.PickVisualMediaRequest(androidx.activity.result.contract.ActivityResultContracts.PickVisualMedia.ImageOnly))
                })
            }
            GroupDivider()
            SwitchRow("Quotes", "One short line per session, on the session and block screens. It doesn’t change during a round.", store.state.quotesOn) { v ->
                store.update { it.copy(quotesOn = v) }
            }
        }
        Spacer(Modifier.height(Dim.s2))
        Text(photoError ?: "Shown behind the Focus screen only. The lock and text stay the same; photos are dimmed so text stays readable. Your photo stays on this phone.",
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
