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
import app.lockdin.state.Profile
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
fun ProfilesScreen(store: Store) {
    val c = LocalColors.current
    var name by rememberSaveable { mutableStateOf("") }
    ScrollScreen {
        Column(verticalArrangement = Arrangement.spacedBy(Dim.s1)) {
            ScreenTitle("Profiles")
            Text("Apps that stay available during focus. Each device keeps its own list.", style = UiType.caption, color = c.inkMuted)
        }
        SettingsGroup {
            store.state.profiles.forEachIndexed { i, p ->
                if (i > 0) GroupDivider()
                Row(Modifier.fillMaxWidth().heightIn(min = 64.dp).padding(horizontal = Dim.s4, vertical = Dim.s3), verticalAlignment = Alignment.CenterVertically) {
                    Column(Modifier.weight(1f).semantics(mergeDescendants = true) {}) {
                        Text(p.name, style = UiType.label.copy(fontWeight = FontWeight.SemiBold), color = c.ink)
                        Text(p.description, style = UiType.caption, color = c.inkMuted)
                        Text("No apps chosen on this phone yet", style = UiType.caption, color = c.inkMuted)
                    }
                    if (!p.builtIn) TextAction("Delete", { store.update { s -> s.copy(profiles = s.profiles.filter { it.id != p.id }) } }, danger = true)
                }
            }
        }
        Text("Choosing apps arrives with blocking on this phone. Until then, profiles name what you plan to use.", style = UiType.caption, color = c.inkMuted)
        Column(verticalArrangement = Arrangement.spacedBy(Dim.s2)) {
            SectionLabel("New profile")
            Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(Dim.s2)) {
                OutlinedTextField(name, { name = it.take(40) }, placeholder = { Text("e.g. Lab report") },
                    singleLine = true, shape = RoundedCornerShape(Dim.radius), colors = fieldColors(),
                    modifier = Modifier.weight(1f).semantics { contentDescription = "New profile name" })
                SecondaryButton("Add", {
                    val n = name.trim()
                    if (n.isNotEmpty()) { store.update { it.copy(profiles = it.profiles + Profile(Store.newId(), n, "Only what you add", false)) }; name = "" }
                })
            }
        }
    }
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
                SettingRow("Blocking", "Off", icon = LIcon.Phone, supporting = "Not in this version yet. The timer and schedule work; apps stay available.")
            }
        }
        Column {
            SectionLabel("Other devices")
            SettingsGroup {
                SettingRow("Windows PC", "Not paired", icon = LIcon.Laptop, supporting = "Pairing arrives in a later update")
                GroupDivider()
                SettingRow("Browser", "Not paired", icon = LIcon.Devices, supporting = "Plans and times sessions; can’t restrict apps")
            }
        }
        Text("When blocking is available, the Android app covers apps outside your profile with a block screen during focus. Nothing is closed or deleted.",
            style = UiType.caption, color = c.inkMuted)
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
