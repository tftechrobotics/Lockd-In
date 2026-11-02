package app.lockdin.ui

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
fun ProfilesScreen(store: Store, onEdit: (Profile) -> Unit = {}) {
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
fun SettingsScreen(store: Store) {
    val c = LocalColors.current
    var confirm by remember { mutableStateOf(false) }
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
                    listOf(Tone.CALM to "Calm", Tone.DIRECT to "Direct", Tone.PLAYFUL to "Playful", Tone.QUIET to "Off").forEachIndexed { i, (v, l) ->
                        if (i > 0) GroupDivider()
                        RadioRow(l, store.state.tone == v) { store.update { it.copy(tone = v) } }
                    }
                }
            }
            Spacer(Modifier.height(Dim.s2))
            Text(message(store.state.tone, Moment.BREAK_ENDING, "TAM 212")?.let { "Example: “$it”" } ?: "No messages during sessions.", style = UiType.caption, color = c.inkMuted,
                modifier = Modifier.semantics { liveRegion = LiveRegionMode.Polite })
        }
        RemindersSettings(store)
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
            SectionLabel("Data")
            SettingsGroup {
                SettingRow("Stored on this phone only", "", supporting = "Excluded from backups. Nothing is sent anywhere.", chevron = null)
                GroupDivider()
                Row(Modifier.padding(horizontal = Dim.s2)) { TextAction("Delete Lockd In data", { confirm = true }, danger = true) }
            }
        }
    }
    if (confirm) {
        AlertDialog(
            onDismissRequest = { confirm = false },
            title = { Text("Delete Lockd In data?", style = Type.title) },
            text = { Text("Removes your schedule, custom profiles and any session in progress. This can’t be undone.", style = UiType.body) },
            confirmButton = { TextButton(onClick = { store.clearAll(); confirm = false }) { Text("Delete", style = UiType.label, color = c.danger) } },
            dismissButton = { TextButton(onClick = { confirm = false }) { Text("Cancel", style = UiType.label, color = c.ink) } },
            containerColor = c.surface,
        )
    }
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
private fun SwitchRow(label: String, supporting: String, on: Boolean, onChange: (Boolean) -> Unit) {
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
