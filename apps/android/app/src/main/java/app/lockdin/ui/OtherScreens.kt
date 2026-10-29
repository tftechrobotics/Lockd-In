package app.lockdin.ui

import android.app.TimePickerDialog
import android.text.format.DateFormat
import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.ExperimentalLayoutApi
import androidx.compose.foundation.layout.FlowRow
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.selection.selectable
import androidx.compose.foundation.selection.selectableGroup
import androidx.compose.foundation.selection.toggleable
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.IconButton
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.RadioButton
import androidx.compose.material3.RadioButtonDefaults
import androidx.compose.material3.Surface
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
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.semantics.LiveRegionMode
import androidx.compose.ui.semantics.Role
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.heading
import androidx.compose.ui.semantics.liveRegion
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.unit.dp
import app.lockdin.engine.Schedule
import app.lockdin.engine.ScheduledSession
import app.lockdin.engine.Session
import app.lockdin.state.Profile
import app.lockdin.state.Store
import app.lockdin.state.ThemeChoice
import app.lockdin.state.Tone
import java.time.DayOfWeek
import java.time.LocalTime
import java.time.ZoneId
import java.time.format.TextStyle as JTextStyle
import java.util.Locale

@Composable
private fun SectionTitle(text: String) {
    Text(text, style = Type.title, color = LocalColors.current.ink, modifier = Modifier.semantics { heading() })
}

// ---------------- Schedule ----------------

@Composable
fun ScheduleScreen(store: Store, now: Long, zone: ZoneId) {
    val c = LocalColors.current
    var editing by remember { mutableStateOf<ScheduledSession?>(null) }
    val list = store.state.schedule.sortedBy { it.startTime }
    val next = Schedule.nextUp(list, now, zone)

    editing?.let { e ->
        Editor(store, e, onClose = { editing = null })
        return
    }
    Column(verticalArrangement = Arrangement.spacedBy(16.dp)) {
        Heading("Schedule", center = false)
        Text("Sessions you plan here live on this phone. Paired devices will get them once pairing is available.", style = Type.caption, color = c.inkMuted)
        PrimaryButton("Plan a session", arrow = false, onClick = {
            editing = ScheduledSession("", "", setOf(DayOfWeek.MONDAY, DayOfWeek.TUESDAY, DayOfWeek.WEDNESDAY, DayOfWeek.THURSDAY),
                LocalTime.of(18, 0), 40, 10, 2, store.state.profiles.first().id)
        })
        if (list.isEmpty()) {
            Surface(shape = RoundedCornerShape(16.dp), color = c.ground, border = BorderStroke(1.dp, c.controlLine), modifier = Modifier.fillMaxWidth()) {
                Row(Modifier.padding(18.dp), horizontalArrangement = Arrangement.spacedBy(14.dp)) {
                    LockdIcon(LIcon.Calendar, tint = c.inkMuted)
                    Text("No sessions planned. Plan a regular time — like weeknights at 6 — and it shows up as “Next up” on Focus.", style = Type.body, color = c.inkMuted)
                }
            }
        }
        list.forEach { s ->
            HorizontalDivider(color = c.line)
            Row(Modifier.fillMaxWidth(), verticalAlignment = Alignment.CenterVertically) {
                Column(Modifier.weight(1f).semantics(mergeDescendants = true) {}) {
                    Text(Fmt.clock(LocalTime.of(s.startTime.hour, s.startTime.minute).atDate(java.time.LocalDate.now()).atZone(zone).toInstant().toEpochMilli(), zone), style = Type.fact, color = c.ink)
                    Text(s.title, style = Type.bodyStrong, color = c.ink)
                    Text(
                        s.days.sorted().joinToString(", ") { it.getDisplayName(JTextStyle.SHORT, Locale.getDefault()) } + " · " +
                            (if (s.rounds > 1) "${s.rounds} × ${Fmt.minutes(s.focusMinutes)}, ${s.breakMinutes}-min breaks" else Fmt.minutes(s.focusMinutes)),
                        style = Type.caption, color = c.inkMuted,
                    )
                    if (next?.session?.id == s.id) Text("Next: ${Fmt.dayLabel(next.start, now, zone)} ${Fmt.clock(next.start, zone)}", style = Type.label, color = c.ink)
                }
                TextButton(onClick = { editing = s }, modifier = Modifier.semantics { contentDescription = "Edit ${s.title}" }) { Text("Edit", style = Type.label, color = c.ink) }
                TextButton(onClick = { store.update { st -> st.copy(schedule = st.schedule.filter { it.id != s.id }) } }, modifier = Modifier.semantics { contentDescription = "Delete ${s.title}" }) {
                    Text("Delete", style = Type.label, color = c.ink)
                }
            }
        }
    }
}

@OptIn(ExperimentalLayoutApi::class)
@Composable
private fun Editor(store: Store, initial: ScheduledSession, onClose: () -> Unit) {
    val c = LocalColors.current
    val context = LocalContext.current
    var s by remember { mutableStateOf(initial) }
    var error by remember { mutableStateOf<String?>(null) }
    fun patch(f: (ScheduledSession) -> ScheduledSession) { s = f(s); error = null }

    Column(verticalArrangement = Arrangement.spacedBy(16.dp)) {
        Heading(if (initial.id.isEmpty()) "Plan a session" else "Edit session", center = false)
        OutlinedTextField(s.title, { t -> patch { it.copy(title = t.take(80)) } }, label = { Text("Name or intention") },
            placeholder = { Text("e.g. Physics review") }, singleLine = true, shape = RoundedCornerShape(12.dp), colors = fieldColors(), modifier = Modifier.fillMaxWidth())
        Text("Days", style = Type.label, color = c.ink)
        FlowRow(horizontalArrangement = Arrangement.spacedBy(8.dp), verticalArrangement = Arrangement.spacedBy(8.dp)) {
            DayOfWeek.entries.sortedBy { (it.value % 7) }.forEach { d ->
                val on = d in s.days
                Surface(
                    shape = CircleShape, color = if (on) c.ink else c.taupeSoft, contentColor = if (on) c.onInk else c.ink,
                    border = BorderStroke(1.dp, if (on) c.ink else c.line),
                    modifier = Modifier.heightIn(min = 48.dp).toggleable(on, role = Role.Checkbox) { patch { x -> x.copy(days = if (on) x.days - d else x.days + d) } },
                ) {
                    Text(d.getDisplayName(JTextStyle.SHORT, Locale.getDefault()), style = Type.label, modifier = Modifier.padding(horizontal = 16.dp, vertical = 13.dp))
                }
            }
        }
        OutlinedButton(
            onClick = {
                TimePickerDialog(context, { _, h, m -> patch { it.copy(startTime = LocalTime.of(h, m)) } },
                    s.startTime.hour, s.startTime.minute, DateFormat.is24HourFormat(context)).show()
            },
            shape = RoundedCornerShape(12.dp), border = BorderStroke(1.dp, c.controlLine),
            modifier = Modifier.fillMaxWidth().heightIn(min = 52.dp),
        ) { Text("Starts at ${s.startTime.format(java.time.format.DateTimeFormatter.ofLocalizedTime(java.time.format.FormatStyle.SHORT))}", style = Type.label, color = c.ink) }
        Stepper("Focus length", s.focusMinutes, 5, Session.MIN_FOCUS_MINUTES, Session.MAX_FOCUS_MINUTES, " min") { v -> patch { it.copy(focusMinutes = v) } }
        Stepper("Focus periods", s.rounds, 1, 1, 21, "") { v -> patch { it.copy(rounds = v) } }
        if (s.rounds > 1) Stepper("Break length", s.breakMinutes, 5, Session.MIN_BREAK_MINUTES, Session.MAX_BREAK_MINUTES, " min") { v -> patch { it.copy(breakMinutes = v) } }
        SelectField(LIcon.Book, "Profile", store.state.profiles.map { it.id to it.name }, s.profileId, { v -> patch { it.copy(profileId = v) } }, Modifier.fillMaxWidth())
        Text("Runs ${Fmt.minutes(s.totalMinutes)}.", style = Type.caption, color = c.inkMuted)
        error?.let {
            Surface(color = c.dangerSoft, shape = RoundedCornerShape(12.dp), modifier = Modifier.fillMaxWidth().semantics { liveRegion = LiveRegionMode.Assertive }) {
                Text(it, style = Type.label, color = c.danger, modifier = Modifier.padding(12.dp))
            }
        }
        PrimaryButton("Save session", arrow = false, onClick = {
            val candidate = s.copy(id = s.id.ifEmpty { Store.newId() }, title = s.title.trim().ifEmpty { "Focus session" })
            val clash = Schedule.overlaps(candidate, store.state.schedule)
            error = when {
                candidate.days.isEmpty() -> "Pick at least one day."
                Session.validatePlan(candidate.plan) != null -> Session.validatePlan(candidate.plan)
                clash.isNotEmpty() -> "Overlaps “${clash.first().title}” at ${clash.first().startTime}. Change the time or days."
                else -> null
            }
            if (error == null) {
                store.update { st -> st.copy(schedule = st.schedule.filter { it.id != candidate.id } + candidate) }
                onClose()
            }
        })
        QuietButton("Cancel", onClose)
    }
}

@Composable
private fun Stepper(label: String, value: Int, step: Int, min: Int, max: Int, unit: String, onChange: (Int) -> Unit) {
    val c = LocalColors.current
    Row(Modifier.fillMaxWidth().semantics(mergeDescendants = false) {}, verticalAlignment = Alignment.CenterVertically) {
        Text(label, style = Type.label, color = c.ink, modifier = Modifier.weight(1f))
        IconButton(onClick = { onChange((value - step).coerceAtLeast(min)) }, enabled = value > min) {
            LockdIcon(LIcon.Minus, description = "Less $label", tint = if (value > min) c.ink else c.line)
        }
        Text("$value$unit", style = Type.fact, color = c.ink, modifier = Modifier.semantics { liveRegion = LiveRegionMode.Polite })
        IconButton(onClick = { onChange((value + step).coerceAtMost(max)) }, enabled = value < max) {
            LockdIcon(LIcon.Plus, description = "More $label", tint = if (value < max) c.ink else c.line)
        }
    }
}

// ---------------- Profiles ----------------

@Composable
fun ProfilesScreen(store: Store) {
    val c = LocalColors.current
    var name by rememberSaveable { mutableStateOf("") }
    Column(verticalArrangement = Arrangement.spacedBy(14.dp)) {
        Heading("Profiles", center = false)
        Text("A profile is the set of apps that stay available while you focus. Each device keeps its own list, because the apps on your phone and PC differ.", style = Type.caption, color = c.inkMuted)
        store.state.profiles.forEach { p -> ProfileRow(store, p) }
        OutlinedTextField(name, { name = it.take(40) }, label = { Text("New profile") }, placeholder = { Text("e.g. Lab report") },
            singleLine = true, shape = RoundedCornerShape(12.dp), colors = fieldColors(), modifier = Modifier.fillMaxWidth())
        OutlinedButton(
            onClick = {
                val n = name.trim()
                if (n.isNotEmpty()) { store.update { it.copy(profiles = it.profiles + Profile(Store.newId(), n, "Only what you add", false)) }; name = "" }
            },
            enabled = name.isNotBlank(), modifier = Modifier.heightIn(min = 48.dp),
        ) { Text("Add profile", style = Type.label) }
    }
}

@Composable
private fun ProfileRow(store: Store, p: Profile) {
    val c = LocalColors.current
    HorizontalDivider(color = c.line)
    Column(Modifier.fillMaxWidth(), verticalArrangement = Arrangement.spacedBy(6.dp)) {
        Text(p.name, style = Type.title, color = c.ink)
        Text(p.description, style = Type.caption, color = c.inkMuted)
        Text("No apps chosen yet. You’ll pick them from this phone’s installed apps when blocking is set up.", style = Type.caption, color = c.inkMuted)
        if (!p.builtIn) QuietButton("Delete ${p.name}", { store.update { s -> s.copy(profiles = s.profiles.filter { it.id != p.id }) } })
    }
}

// ---------------- Devices ----------------

@Composable
fun DevicesScreen() {
    val c = LocalColors.current
    Column(verticalArrangement = Arrangement.spacedBy(14.dp)) {
        Heading("Devices", center = false)
        PHONE_DEVICE_ROWS.forEach { d ->
            Row(Modifier.fillMaxWidth().heightIn(min = 56.dp).semantics(mergeDescendants = true) {}, verticalAlignment = Alignment.CenterVertically) {
                LockdIcon(d.icon)
                Spacer(Modifier.width(12.dp))
                Text(d.name, style = Type.bodyStrong, color = c.ink, modifier = Modifier.weight(1f))
                Text(d.status, style = Type.caption, color = c.inkMuted)
            }
            HorizontalDivider(color = c.line)
        }
        SectionTitle("What each device does")
        Text("Android app: covers apps you didn’t approve with a block screen during focus and moves them to the background. Nothing is closed.", style = Type.body, color = c.ink)
        Text("Windows app: keeps approved apps available and restricts the rest during focus.", style = Type.body, color = c.ink)
        Text("Web app: plans and times sessions. A website can’t restrict other apps, so it never claims to.", style = Type.body, color = c.ink)
        SectionTitle("Pairing")
        Text("Pairing links your phone, PC and browser so they share session timing. Each device keeps running the schedule it received while offline. It isn’t available yet, so nothing here is connected.", style = Type.body, color = c.inkMuted)
    }
}

// ---------------- Settings ----------------

@Composable
fun SettingsScreen(store: Store) {
    val c = LocalColors.current
    var confirm by remember { mutableStateOf(false) }
    Column(verticalArrangement = Arrangement.spacedBy(12.dp)) {
        Heading("Settings", center = false)
        SectionTitle("Appearance")
        Column(Modifier.selectableGroup()) {
            listOf(ThemeChoice.SYSTEM to "Match system", ThemeChoice.LIGHT to "Light", ThemeChoice.DARK to "Dark").forEach { (v, l) ->
                RadioRow(l, store.state.theme == v) { store.update { it.copy(theme = v) } }
            }
        }
        HorizontalDivider(color = c.line)
        SectionTitle("Messages")
        Text("Short lines at the start of focus and around breaks. Quiet shows none.", style = Type.caption, color = c.inkMuted)
        Column(Modifier.selectableGroup()) {
            listOf(Tone.CALM to "Calm", Tone.DIRECT to "Direct", Tone.PLAYFUL to "Playful", Tone.QUIET to "Quiet").forEach { (v, l) ->
                RadioRow(l, store.state.tone == v) { store.update { it.copy(tone = v) } }
            }
        }
        Text(message(store.state.tone, Moment.BREAK_ENDING, "TAM 212") ?: "No messages.", style = Type.body, color = c.inkMuted,
            modifier = Modifier.semantics { liveRegion = LiveRegionMode.Polite })
        HorizontalDivider(color = c.line)
        SectionTitle("Data on this phone")
        Text("Your schedule, profiles and current session are stored only on this phone and aren’t included in backups. Nothing is sent anywhere.", style = Type.caption, color = c.inkMuted)
        QuietButton("Delete Lockd In data from this phone", { confirm = true }, danger = true)
    }
    if (confirm) {
        AlertDialog(
            onDismissRequest = { confirm = false },
            title = { Text("Delete Lockd In data?", style = Type.title) },
            text = { Text("This removes your schedule, custom profiles, recent intentions and any session in progress on this phone. It can’t be undone.", style = Type.body) },
            confirmButton = { TextButton(onClick = { store.clearAll(); confirm = false }) { Text("Delete", style = Type.label, color = c.danger) } },
            dismissButton = { TextButton(onClick = { confirm = false }) { Text("Cancel", style = Type.label, color = c.ink) } },
            containerColor = c.surface,
        )
    }
}

@Composable
private fun RadioRow(label: String, selected: Boolean, onClick: () -> Unit) {
    val c = LocalColors.current
    Row(
        Modifier.fillMaxWidth().heightIn(min = 52.dp).selectable(selected, role = Role.RadioButton, onClick = onClick),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        RadioButton(selected = selected, onClick = null, colors = RadioButtonDefaults.colors(selectedColor = c.ink, unselectedColor = c.controlLine))
        Spacer(Modifier.width(12.dp))
        Text(label, style = Type.body, color = c.ink)
    }
}
