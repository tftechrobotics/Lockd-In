// Location routines UI (roadmap step 7, E): Settings › Places and the place editor.
// Plain about what is used: one saved point per place, nothing else; arrival reminds by
// default; automatic start is a separate switch with its own explanation.
package app.lockdin.ui

import android.Manifest
import android.os.Build
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.foundation.clickable
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
import androidx.compose.ui.semantics.Role
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import app.lockdin.engine.Place
import app.lockdin.engine.Places
import app.lockdin.loc.Geofences
import app.lockdin.state.Store
import java.time.LocalTime
import java.time.format.DateTimeFormatter

private val hm = DateTimeFormatter.ofPattern("h:mm a")

fun blankPlace(profileId: String) = Place("", "", Double.NaN, Double.NaN, profileId = profileId)

@Composable
fun PlacesSettings(store: Store, onEdit: (Place) -> Unit) {
    val c = LocalColors.current
    val ctx = LocalContext.current
    val ready = remember(Resumes.tick) { Geofences.ready(ctx) }
    val askBackground = rememberLauncherForActivityResult(ActivityResultContracts.RequestPermission()) { Resumes.bump() }
    Column {
        SectionLabel("Places")
        SettingsGroup {
            if (store.state.places.isNotEmpty()) {
                SwitchRow("Pause location routines", "Nothing happens at your places until you turn this off", store.state.placesPaused) { v ->
                    store.update { it.copy(placesPaused = v) }
                }
                store.state.places.forEach { p ->
                    GroupDivider()
                    Row(
                        Modifier.fillMaxWidth().heightIn(min = 64.dp).clickable(role = Role.Button, onClickLabel = "Edit") { onEdit(p) }
                            .semantics(mergeDescendants = true) {}.padding(horizontal = Dim.s4, vertical = Dim.s3),
                        verticalAlignment = Alignment.CenterVertically,
                    ) {
                        Column(Modifier.weight(1f), verticalArrangement = Arrangement.spacedBy(2.dp)) {
                            Text(p.name, style = UiType.label.copy(fontWeight = FontWeight.SemiBold), color = c.ink)
                            Text(
                                "${if (p.days.isEmpty()) "Every day" else daysLabel(p.days)} · ${p.from.format(hm)}–${p.to.format(hm)} · " +
                                    (if (p.autoStart) "starts automatically" else "reminds you") + if (!p.enabled) " · off" else "",
                                style = UiType.caption, color = c.inkMuted,
                            )
                        }
                        LockdIcon(LIcon.Chevron, size = 18.dp, tint = c.inkMuted)
                    }
                }
                GroupDivider()
            }
            SettingRow("Add a place", "", icon = LIcon.Plus, onClick = { onEdit(blankPlace(store.state.last.profileId)) })
        }
        Spacer(Modifier.height(Dim.s2))
        if (store.state.places.isNotEmpty() && !ready) {
            Text("Places can’t notice when you arrive until location is allowed “All the time” for Lockd In.", style = UiType.caption, color = c.ink)
            TextAction("Allow location all the time", {
                if (Build.VERSION.SDK_INT >= 29) askBackground.launch(Manifest.permission.ACCESS_BACKGROUND_LOCATION)
            })
        }
        Text(
            "When you arrive somewhere you’ve saved, during its hours, Lockd In can remind you to start. It stores only the places you save, keeps no location history, and works it out on this phone. Leaving never adds restrictions. Indoors or with weak signal, arrival can be noticed late or not at all.",
            style = UiType.caption, color = c.inkMuted,
        )
    }
}

@Composable
fun PlaceEditor(store: Store, initial: Place, onClose: () -> Unit) {
    val c = LocalColors.current
    val ctx = LocalContext.current
    var p by remember(initial.id) { mutableStateOf(initial) }
    var accuracy by remember { mutableStateOf<Float?>(null) }
    var locating by remember { mutableStateOf(false) }
    var error by remember { mutableStateOf<String?>(null) }
    var explainAuto by remember { mutableStateOf(false) }
    var explainBackground by remember { mutableStateOf(false) }
    var confirmDelete by remember { mutableStateOf(false) }
    val isNew = initial.id.isEmpty()
    val hasLocation = !p.lat.isNaN() && !p.lon.isNaN()

    fun locate() {
        locating = true; error = null
        Geofences.current(ctx) { r ->
            locating = false
            if (r == null) error = "Couldn’t get your location. Check that location is on, then try again."
            else { p = p.copy(lat = r.first, lon = r.second); accuracy = r.third }
        }
    }
    val askLocation = rememberLauncherForActivityResult(ActivityResultContracts.RequestMultiplePermissions()) { granted ->
        if (granted.values.any { it }) locate() else error = "Location is off for Lockd In, so this place can’t be saved."
    }
    val askBackground = rememberLauncherForActivityResult(ActivityResultContracts.RequestPermission()) { Resumes.bump(); onClose() }

    fun save() {
        when {
            p.name.isBlank() -> { error = "Give the place a name."; return }
            !hasLocation -> { error = "Save your current location first."; return }
        }
        val saved = p.copy(id = p.id.ifEmpty { Store.newId() }, name = p.name.trim())
        store.update { s -> s.copy(places = if (s.places.any { it.id == saved.id }) s.places.map { if (it.id == saved.id) saved else it } else (s.places + saved).take(20)) }
        if (!Geofences.background(ctx) && Build.VERSION.SDK_INT >= 29) explainBackground = true else onClose()
    }

    Column(Modifier.fillMaxSize()) {
        Row(Modifier.fillMaxWidth().heightIn(min = 56.dp).padding(horizontal = 4.dp), verticalAlignment = Alignment.CenterVertically) {
            IconButton(onClick = onClose) { LockdIcon(LIcon.Close, description = "Close without saving") }
            Text(if (isNew) "New place" else "Edit place", style = UiType.title, color = c.ink, modifier = Modifier.weight(1f))
            if (!isNew) TextAction("Delete", { confirmDelete = true }, danger = true)
            TextAction("Save", ::save)
        }
        Column(
            Modifier.fillMaxSize().verticalScroll(rememberScrollState()).padding(horizontal = Dim.gutter).padding(bottom = Dim.s6),
            verticalArrangement = Arrangement.spacedBy(Dim.s4),
        ) {
            OutlinedTextField(p.name, { p = p.copy(name = it.take(40)) }, singleLine = true, label = { Text("Name, e.g. Library") },
                shape = RoundedCornerShape(Dim.radius), colors = fieldColors(), modifier = Modifier.fillMaxWidth())
            Column(verticalArrangement = Arrangement.spacedBy(Dim.s2)) {
                SectionLabel("Location")
                Text(
                    when {
                        locating -> "Finding your location…"
                        hasLocation -> "Saved: %.4f, %.4f".format(p.lat, p.lon) + (accuracy?.let { " (within about ${it.toInt()} m)" } ?: "")
                        else -> "Go to the place, then save your current location. Only this point is stored."
                    },
                    style = UiType.caption, color = if (hasLocation) c.ink else c.inkMuted,
                )
                SecondaryButton(if (hasLocation) "Use my current location again" else "Use my current location", {
                    if (Geofences.foreground(ctx)) locate()
                    else askLocation.launch(arrayOf(Manifest.permission.ACCESS_FINE_LOCATION, Manifest.permission.ACCESS_COARSE_LOCATION))
                }, modifier = Modifier.fillMaxWidth())
            }
            SettingsGroup {
                SettingMenuRow("Area", Places.RADIUS_CHOICES.map { it to "Within $it m" }, p.radiusM, { p = p.copy(radiusM = it) })
                GroupDivider()
                SettingMenuRow("After arriving", Places.GRACE_CHOICES.map { it to if (it == 0) "Right away" else "After $it min there" }, p.arriveGraceMin, { p = p.copy(arriveGraceMin = it) })
                GroupDivider()
                SettingRow("From", p.from.format(hm), icon = LIcon.Clock, onClick = {
                    android.app.TimePickerDialog(ctx, { _, h, m -> p = p.copy(from = LocalTime.of(h, m)) }, p.from.hour, p.from.minute, false).show()
                })
                GroupDivider()
                SettingRow("Until", p.to.format(hm), icon = LIcon.Clock, onClick = {
                    android.app.TimePickerDialog(ctx, { _, h, m -> p = p.copy(to = LocalTime.of(h, m)) }, p.to.hour, p.to.minute, false).show()
                })
                GroupDivider()
                SettingMenuRow("Profile", store.state.profiles.map { it.id to it.name }, p.profileId, { p = p.copy(profileId = it) }, icon = LIcon.Book)
                GroupDivider()
                SettingMenuRow("Length", listOf(25, 30, 40, 45, 50, 60, 90).map { it to "$it min" }, p.focusMinutes, { p = p.copy(focusMinutes = it) }, icon = LIcon.Focus)
            }
            Column(verticalArrangement = Arrangement.spacedBy(Dim.s2)) {
                SectionLabel("Days")
                DayPicker(p.days) { d -> p = p.copy(days = if (d in p.days) p.days - d else p.days + d) }
                Text(if (p.days.isEmpty()) "No days picked: every day." else daysLabel(p.days), style = UiType.caption, color = c.inkMuted)
            }
            SettingsGroup {
                SwitchRow("Start automatically", "Off: a reminder asks first. On: focus starts and blocks when you arrive.", p.autoStart) { v ->
                    if (v) explainAuto = true else p = p.copy(autoStart = false)
                }
                if (!isNew) {
                    GroupDivider()
                    SwitchRow("On", "Turn this place off without deleting it", p.enabled) { v -> p = p.copy(enabled = v) }
                }
            }
            error?.let { Text(it, style = UiType.label, color = c.danger) }
        }
    }
    if (explainAuto) AlertDialog(
        onDismissRequest = { explainAuto = false }, containerColor = c.surface,
        title = { Text("Start automatically at ${p.name.ifBlank { "this place" }}?", style = UiType.title, color = c.ink) },
        text = { Text("When you’ve been here ${if (p.arriveGraceMin == 0) "a moment" else "${p.arriveGraceMin} minutes"} during its hours, a ${p.focusMinutes}-minute focus session starts and blocks what the ${store.state.profiles.firstOrNull { it.id == p.profileId }?.name ?: "chosen"} profile blocks. Once a day at most. You can end it as usual, and emergency recovery always works.", style = UiType.body, color = c.inkMuted) },
        confirmButton = { TextButton(onClick = { p = p.copy(autoStart = true); explainAuto = false }) { Text("Turn on", style = UiType.label, color = c.ink) } },
        dismissButton = { TextButton(onClick = { explainAuto = false }) { Text("Keep reminders", style = UiType.label, color = c.ink) } },
    )
    if (explainBackground) AlertDialog(
        onDismissRequest = { explainBackground = false; onClose() }, containerColor = c.surface,
        title = { Text("Allow location all the time?", style = UiType.title, color = c.ink) },
        text = { Text("Android only lets apps notice arriving at a place when location is allowed “All the time”. Lockd In uses it just for your saved places, keeps no history, and nothing leaves your phone. Without it, your places stay saved but can’t trigger.", style = UiType.body, color = c.inkMuted) },
        confirmButton = { TextButton(onClick = { explainBackground = false; askBackground.launch(Manifest.permission.ACCESS_BACKGROUND_LOCATION) }) { Text("Continue", style = UiType.label, color = c.ink) } },
        dismissButton = { TextButton(onClick = { explainBackground = false; onClose() }) { Text("Not now", style = UiType.label, color = c.ink) } },
    )
    if (confirmDelete) AlertDialog(
        onDismissRequest = { confirmDelete = false }, containerColor = c.surface,
        title = { Text("Delete “${initial.name}”?", style = UiType.title, color = c.ink) },
        text = { Text("The saved location is removed from this phone.", style = UiType.body, color = c.inkMuted) },
        confirmButton = { TextButton(onClick = { store.update { s -> s.copy(places = s.places.filter { it.id != initial.id }) }; confirmDelete = false; onClose() }) { Text("Delete", style = UiType.label, color = c.danger) } },
        dismissButton = { TextButton(onClick = { confirmDelete = false }) { Text("Keep", style = UiType.label, color = c.ink) } },
    )
}
