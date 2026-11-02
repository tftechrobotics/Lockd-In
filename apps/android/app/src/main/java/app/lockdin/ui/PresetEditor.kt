// Editing a saved preset (roadmap step 2, B): name, task, length, breaks, profile, an optional
// due date and optional steps. Steps show during a session started from the preset.
package app.lockdin.ui

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.IconButton
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.unit.dp
import app.lockdin.state.Preset
import app.lockdin.state.Profile
import app.lockdin.state.Store
import java.time.LocalDate
import java.time.ZoneId

private val PRESET_LENGTHS = listOf(10, 15, 20, 25, 30, 40, 45, 50, 60, 75, 90, 120)

fun presetSummary(p: Preset, profiles: List<Profile>): String = buildList {
    add(if (p.rounds > 1) "${p.rounds} × ${p.focusMinutes} min, ${p.breakMinutes} min breaks" else "${p.focusMinutes} min")
    add(profiles.firstOrNull { it.id == p.profileId }?.name ?: "Profile removed")
    p.due?.let { add("due " + it.format(java.time.format.DateTimeFormatter.ofPattern("MMM d", java.util.Locale.getDefault()))) }
    if (p.steps.isNotEmpty()) add("${p.steps.size} step${if (p.steps.size == 1) "" else "s"}")
}.joinToString(" · ")

@Composable
fun PresetEditor(store: Store, preset: Preset, now: Long, zone: ZoneId, onClose: () -> Unit) {
    val c = LocalColors.current
    val ctx = LocalContext.current
    var p by remember(preset.id) { mutableStateOf(preset) }
    var newStep by remember { mutableStateOf("") }
    var confirmDelete by remember { mutableStateOf(false) }
    val nameOk = p.name.isNotBlank()

    fun save() {
        if (!nameOk) return
        store.update { s -> s.copy(presets = s.presets.map { if (it.id == p.id) p.copy(name = p.name.trim(), task = p.task?.trim()?.ifEmpty { null }) else it }) }
        onClose()
    }

    Column(Modifier.fillMaxSize()) {
        Row(Modifier.fillMaxWidth().heightIn(min = 56.dp).padding(horizontal = 4.dp), verticalAlignment = Alignment.CenterVertically) {
            IconButton(onClick = onClose) { LockdIcon(LIcon.Close, description = "Close without saving") }
            Text("Edit preset", style = UiType.title, color = c.ink, modifier = Modifier.weight(1f))
            TextAction("Delete", { confirmDelete = true }, danger = true)
            TextAction("Save", ::save)
        }
        Column(
            Modifier.fillMaxSize().verticalScroll(rememberScrollState()).padding(horizontal = Dim.gutter).padding(bottom = Dim.s6),
            verticalArrangement = Arrangement.spacedBy(Dim.s4),
        ) {
            OutlinedTextField(
                p.name, { p = p.copy(name = it.take(60)) }, singleLine = true, label = { Text("Name") }, isError = !nameOk,
                shape = RoundedCornerShape(Dim.radius), colors = fieldColors(), modifier = Modifier.fillMaxWidth(),
            )
            OutlinedTextField(
                p.task ?: "", { p = p.copy(task = it.take(120)) }, singleLine = true, label = { Text("Task (optional)") },
                shape = RoundedCornerShape(Dim.radius), colors = fieldColors(), modifier = Modifier.fillMaxWidth(),
            )
            SettingsGroup {
                SettingMenuRow("Focus length", (PRESET_LENGTHS + p.focusMinutes).distinct().sorted().map { it to "$it min" }, p.focusMinutes,
                    { p = p.copy(focusMinutes = it) }, icon = LIcon.Clock)
                GroupDivider()
                SettingMenuRow("Focus rounds", (1..6).map { it to if (it == 1) "1 round" else "$it rounds" }, p.rounds,
                    { r -> p = p.copy(rounds = r, breakMinutes = if (r > 1 && p.breakMinutes == 0) 10 else p.breakMinutes) }, icon = LIcon.Repeat)
                if (p.rounds > 1) {
                    GroupDivider()
                    SettingMenuRow("Breaks", listOf(5, 10, 15, 20).map { it to "$it min" }, p.breakMinutes.coerceAtLeast(5),
                        { p = p.copy(breakMinutes = it) }, icon = LIcon.Coffee)
                }
                GroupDivider()
                SettingMenuRow("Profile", store.state.profiles.map { it.id to it.name }, p.profileId, { p = p.copy(profileId = it) }, icon = LIcon.Book)
                GroupDivider()
                SettingRow("Due date", p.due?.let { Fmt.dueLabel(it, now, zone) } ?: "None", icon = LIcon.Calendar, onClick = {
                    val d = p.due ?: LocalDate.now(zone)
                    android.app.DatePickerDialog(ctx, { _, y, m, day -> p = p.copy(due = LocalDate.of(y, m + 1, day)) }, d.year, d.monthValue - 1, d.dayOfMonth).show()
                })
                if (p.due != null) {
                    GroupDivider()
                    Row(Modifier.padding(horizontal = Dim.s2)) { TextAction("Remove due date", { p = p.copy(due = null) }) }
                }
            }
            Column(verticalArrangement = Arrangement.spacedBy(Dim.s2)) {
                SectionLabel("Steps")
                Text("Optional. They show under Details during a session started from this preset.", style = UiType.caption, color = c.inkMuted)
                if (p.steps.isNotEmpty()) SettingsGroup {
                    p.steps.forEachIndexed { i, step ->
                        if (i > 0) GroupDivider()
                        Row(Modifier.fillMaxWidth().heightIn(min = Dim.control + 4.dp).padding(start = Dim.s4, end = Dim.s2), verticalAlignment = Alignment.CenterVertically) {
                            Text("${i + 1}. $step", style = UiType.body, color = c.ink, modifier = Modifier.weight(1f))
                            TextAction("Remove", { p = p.copy(steps = p.steps.filterIndexed { j, _ -> j != i }) })
                        }
                    }
                }
                if (p.steps.size < 20) Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(Dim.s2)) {
                    OutlinedTextField(
                        newStep, { newStep = it.take(120) }, singleLine = true, placeholder = { Text("e.g. Outline chapter 3") },
                        shape = RoundedCornerShape(Dim.radius), colors = fieldColors(),
                        modifier = Modifier.weight(1f).semantics { contentDescription = "New step" },
                    )
                    SecondaryButton("Add", {
                        newStep.trim().takeIf { it.isNotEmpty() }?.let { p = p.copy(steps = p.steps + it); newStep = "" }
                    })
                }
            }
        }
    }
    if (confirmDelete) AlertDialog(
        onDismissRequest = { confirmDelete = false }, containerColor = c.surface,
        title = { Text("Delete “${preset.name}”?", style = UiType.title, color = c.ink) },
        text = { Text("Your history and schedule aren’t affected.", style = UiType.body, color = c.inkMuted) },
        confirmButton = {
            TextButton(onClick = {
                store.update { s -> s.copy(presets = s.presets.filter { it.id != preset.id }) }
                confirmDelete = false; onClose()
            }) { Text("Delete", style = UiType.label, color = c.danger) }
        },
        dismissButton = { TextButton(onClick = { confirmDelete = false }) { Text("Keep", style = UiType.label, color = c.ink) } },
    )
}
