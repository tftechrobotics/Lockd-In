// Schedule: a view of plans first (next up, weekly routines, one-time sessions), with
// creation and editing on their own screen. Runs on this phone; paired devices join
// once pairing exists.
package app.lockdin.ui

import android.app.DatePickerDialog
import android.app.TimePickerDialog
import android.text.format.DateFormat
import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.aspectRatio
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.layout.widthIn
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.selection.toggleable
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.IconButton
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Surface
import androidx.compose.material3.Switch
import androidx.compose.material3.SwitchDefaults
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
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.semantics.LiveRegionMode
import androidx.compose.ui.semantics.Role
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.liveRegion
import androidx.compose.ui.semantics.heading
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.semantics.stateDescription
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.compose.ui.semantics.clearAndSetSemantics
import app.lockdin.engine.Schedule
import app.lockdin.engine.ScheduledSession
import app.lockdin.engine.Session
import app.lockdin.state.Store
import java.time.DayOfWeek
import java.time.Instant
import java.time.LocalDate
import java.time.LocalTime
import java.time.ZoneId
import java.time.format.DateTimeFormatter
import java.time.format.FormatStyle
import java.time.format.TextStyle as JTextStyle
import java.time.temporal.WeekFields
import java.util.Locale

private val timeFmt: DateTimeFormatter = DateTimeFormatter.ofLocalizedTime(FormatStyle.SHORT)
private val dateFmt: DateTimeFormatter = DateTimeFormatter.ofPattern("EEE, MMM d")

/** Weekdays in the user's locale order (Sunday-first in the US). */
fun localWeek(): List<DayOfWeek> {
    val first = WeekFields.of(Locale.getDefault()).firstDayOfWeek
    return (0L until 7L).map { first.plus(it) }
}

/** "Weekdays", "Every day", "Monday to Thursday", or "Mon, Wed, Fri". */
fun daysLabel(days: Set<DayOfWeek>): String {
    val loc = Locale.getDefault()
    val weekdays = setOf(DayOfWeek.MONDAY, DayOfWeek.TUESDAY, DayOfWeek.WEDNESDAY, DayOfWeek.THURSDAY, DayOfWeek.FRIDAY)
    if (days.size == 7) return "Every day"
    if (days == weekdays) return "Weekdays"
    if (days == setOf(DayOfWeek.SATURDAY, DayOfWeek.SUNDAY)) return "Weekends"
    val ordered = localWeek().filter { it in days }
    val contiguous = ordered.size >= 3 && ordered.zipWithNext().all { (a, b) -> a.plus(1) == b }
    return if (contiguous) "${ordered.first().getDisplayName(JTextStyle.FULL, loc)} to ${ordered.last().getDisplayName(JTextStyle.FULL, loc)}"
    else ordered.joinToString(", ") { it.getDisplayName(JTextStyle.SHORT, loc) }
}

fun planLabel(s: ScheduledSession): String =
    if (s.rounds > 1) "${s.rounds} × ${s.focusMinutes} min, ${s.breakMinutes} min breaks" else "${s.focusMinutes} min, no breaks"

/** A blank session for "Add session": weeknights at 7, two 40-minute rounds. */
fun blankSession(profileId: String) = ScheduledSession(
    "", "", setOf(DayOfWeek.MONDAY, DayOfWeek.TUESDAY, DayOfWeek.WEDNESDAY, DayOfWeek.THURSDAY),
    LocalTime.of(19, 0), 40, 10, 2, profileId,
)

@Composable
fun ScheduleScreen(store: Store, now: Long, zone: ZoneId, onEdit: (ScheduledSession) -> Unit) {
    val c = LocalColors.current
    val all = store.state.schedule
    val routines = all.filter { !it.oneTime }.sortedBy { it.startTime }
    val today = Instant.ofEpochMilli(now).atZone(zone).toLocalDate()
    val once = all.filter { it.oneTime && !it.date!!.isBefore(today) }.sortedWith(compareBy({ it.date }, { it.startTime }))
    val past = all.filter { it.oneTime && it.date!!.isBefore(today) }
    val add = { onEdit(blankSession(store.state.last.profileId)) }
    val profileName = { id: String -> store.state.profiles.firstOrNull { it.id == id }?.name ?: "Study" }

    Column(
        Modifier.fillMaxSize().screenScroll().padding(top = LocalTopInset.current).padding(horizontal = Dim.gutter).padding(top = Dim.s2, bottom = Dim.s5),
        verticalArrangement = Arrangement.spacedBy(Dim.s5),
    ) {
        Row(verticalAlignment = Alignment.CenterVertically) {
            ScreenTitle("Schedule", Modifier.weight(1f))
            if (all.isNotEmpty()) SecondaryButton("Add session", add)
        }
        // R59: a real month calendar; the chosen day's plans and free time show under it.
        var month by androidx.compose.runtime.saveable.rememberSaveable { mutableStateOf(java.time.YearMonth.from(today)) }
        var day by androidx.compose.runtime.saveable.rememberSaveable { mutableStateOf(today) }
        MonthCalendar(
            month = month, onMonth = { month = it }, selected = day, today = today,
            // Dots only from today on: past days show what's planned now, not what happened.
            planned = { d -> if (d.isBefore(today)) 0 else all.count { it.enabled && it.occursOn(d) } },
            onSelect = { day = it },
        )
        // Owner 2026-11-08: three clearly separated parts (calendar, the chosen day, your plans), and
        // no "Next up" here (Focus shows it), so a plan isn't listed three times.
        SectionRule()
        DayAgenda(store, day, today, now, zone, profileName, onEdit)
        SectionRule()
        if (all.isEmpty()) {
            Column(verticalArrangement = Arrangement.spacedBy(Dim.s3)) {
                Text("No sessions planned yet", style = Type.title, color = c.ink, modifier = Modifier.semantics { heading() })
                Text("Plan a weekly routine, like weeknights at 7, or a one-time study block. The next one shows on Focus.",
                    style = UiType.caption, color = c.inkMuted)
                GlassButton("Add session", add)
            }
            return@Column
        }
        Column(verticalArrangement = Arrangement.spacedBy(Dim.s4)) {
            Text("Your plans", style = Type.title, color = c.ink, modifier = Modifier.semantics { heading() })
            if (routines.isNotEmpty()) Column {
                SectionLabel("Every week")
                routines.forEachIndexed { i, s ->
                    if (i > 0) androidx.compose.material3.HorizontalDivider(color = c.line)
                    PlanRow(store, s, daysLabel(s.days), profileName(s.profileId), onEdit)
                }
            }
            if (once.isNotEmpty()) Column {
                SectionLabel("One time")
                once.forEachIndexed { i, s ->
                    if (i > 0) androidx.compose.material3.HorizontalDivider(color = c.line)
                    PlanRow(store, s, s.date!!.format(dateFmt), profileName(s.profileId), onEdit)
                }
            }
            if (past.isNotEmpty()) TextAction("Clear ${past.size} past one-time session${if (past.size > 1) "s" else ""}", {
                store.update { st -> st.copy(schedule = st.schedule.filterNot { it in past }) }
            })
        }
    }
}

/** A clear break between the parts of a screen. */
@Composable
fun SectionRule() = androidx.compose.material3.HorizontalDivider(thickness = 1.5.dp, color = LocalColors.current.inkMuted.copy(alpha = 0.45f))

/** The chosen day: its planned sessions (tap to edit), a way to plan one, then free time. */
@Composable
private fun DayAgenda(
    store: Store, day: LocalDate, today: LocalDate, now: Long, zone: ZoneId,
    profileName: (String) -> String, onEdit: (ScheduledSession) -> Unit,
) {
    val c = LocalColors.current
    val past = day.isBefore(today)
    val plans = if (past) emptyList() else store.state.schedule.filter { it.enabled && it.occursOn(day) }.sortedBy { it.startTime }
    Column(verticalArrangement = Arrangement.spacedBy(Dim.s4)) {
        Text(dayTitle(day, today), style = Type.title, color = c.ink, modifier = Modifier.semantics { heading() })
        if (plans.isEmpty()) Text(if (past) "This day has passed. Finished sessions are in History." else "Nothing planned.", style = UiType.body, color = c.inkMuted)
        else Column {
            plans.forEachIndexed { i, s ->
                if (i > 0) androidx.compose.material3.HorizontalDivider(color = c.line)
                val st = Schedule.startOn(s, day, zone)
                Row(
                    Modifier.fillMaxWidth().heightIn(min = 72.dp).clickable(role = Role.Button, onClickLabel = "Edit") { onEdit(s) }
                        .semantics(mergeDescendants = true) {}.padding(vertical = Dim.s3),
                    verticalAlignment = Alignment.CenterVertically,
                ) {
                    Column(Modifier.weight(1f), verticalArrangement = Arrangement.spacedBy(2.dp)) {
                        Text("${Fmt.clock(st, zone)} – ${Fmt.clock(st + s.totalMinutes * 60_000L, zone)}", style = UiType.time, color = c.ink)
                        Text(s.title.ifBlank { "Study" }, style = UiType.label.copy(fontWeight = FontWeight.SemiBold), color = c.ink)
                        Text("${planLabel(s)} · ${profileName(s.profileId)}", style = UiType.caption, color = c.inkMuted)
                    }
                    // Skip just this day (the plan stays); only before it starts.
                    if (st > now) TextAction("Skip", {
                        store.update { x -> x.copy(schedule = x.schedule.map { if (it.id == s.id) it.copy(skipped = it.skipped + day) else it }) }
                    })
                }
            }
        }
        if (!past) {
            TextAction("Plan a session ${if (day == today) "today" else if (day == today.plusDays(1)) "tomorrow" else "this day"}", {
                onEdit(ScheduledSession("", "Study", emptySet(), LocalTime.of(19, 0), 40, 10, 2, store.state.last.profileId, date = day))
            })
            DayFreeTime(store, day, now, zone, onPlan = onEdit)
        }
    }
}

@Composable
private fun PlanRow(store: Store, s: ScheduledSession, whenText: String, profile: String, onEdit: (ScheduledSession) -> Unit) {
    val c = LocalColors.current
    val muted = !s.enabled
    Row(Modifier.fillMaxWidth().heightIn(min = 64.dp), verticalAlignment = Alignment.CenterVertically) {
        Row(
            Modifier.weight(1f).clickable(role = Role.Button, onClickLabel = "Edit ${s.title}") { onEdit(s) }.padding(start = plateSide(Dim.s4), top = Dim.s4, bottom = Dim.s4),
            verticalAlignment = Alignment.CenterVertically,
        ) {
            Column(Modifier.weight(1f), verticalArrangement = Arrangement.spacedBy(2.dp)) {
                Text(s.startTime.format(timeFmt),
                    style = UiType.time, color = if (muted) c.inkMuted else c.ink)
                Text(s.title.ifBlank { "Study" }, style = UiType.label.copy(fontWeight = FontWeight.SemiBold), color = if (muted) c.inkMuted else c.ink)
                Text(whenText, style = UiType.caption, color = c.ink)
                Text("${planLabel(s)} · $profile", style = UiType.caption, color = c.inkMuted)
                val notes = listOfNotNull(
                    if (s.autoStart) "Starts automatically" else null,
                    if (s.remindMinutes > 0) "Reminder ${s.remindMinutes} min before" else null,
                    s.skipped.filter { !it.isBefore(java.time.LocalDate.now()) }.minOrNull()?.let { "Skipping ${it.format(dateFmt)}" },
                )
                if (notes.isNotEmpty()) Text(notes.joinToString(" · "), style = UiType.caption, color = c.inkMuted)
                if (muted) Text("Off", style = UiType.caption.copy(fontWeight = FontWeight.SemiBold), color = c.inkMuted)
            }
        }
        Switch(
            checked = s.enabled,
            onCheckedChange = { on -> store.update { st -> st.copy(schedule = st.schedule.map { if (it.id == s.id) it.copy(enabled = on) else it }) } },
            colors = SwitchDefaults.colors(checkedTrackColor = c.ink, checkedThumbColor = c.onInk, uncheckedTrackColor = c.taupeSoft, uncheckedBorderColor = c.controlLine, uncheckedThumbColor = c.controlLine),
            modifier = Modifier.padding(horizontal = Dim.s3).semantics { contentDescription = "${s.title} on" },
        )
    }
}

/** Full-screen create/edit. Opened from Schedule; closes back to it. */
@Composable
fun ScheduleEditor(store: Store, initial: ScheduledSession, zone: ZoneId, now: Long, onClose: () -> Unit) {
    val c = LocalColors.current
    val context = LocalContext.current
    var s by remember { mutableStateOf(initial) }
    var error by remember { mutableStateOf<String?>(null) }
    var confirmDelete by remember { mutableStateOf(false) }
    var explainAuto by remember { mutableStateOf(false) }
    val isNew = initial.id.isEmpty()
    val today = Instant.ofEpochMilli(now).atZone(zone).toLocalDate()
    fun patch(f: (ScheduledSession) -> ScheduledSession) { s = f(s); error = null }
    val profileName = store.state.profiles.firstOrNull { it.id == s.profileId }?.name ?: "Study"
    val endTime = s.startTime.plusMinutes(s.totalMinutes.toLong())

    val summary = buildList {
        val whenText = if (s.oneTime) "On ${s.date!!.format(DateTimeFormatter.ofLocalizedDate(FormatStyle.FULL))}"
        else if (s.days.isEmpty()) "No days picked yet" else "${daysLabel(s.days)}, every week"
        add("$whenText at ${s.startTime.format(timeFmt)}")
        add("${if (s.rounds > 1) "${s.rounds} rounds of ${s.focusMinutes} min with ${s.breakMinutes}-minute breaks" else "One ${s.focusMinutes}-minute round"}, ending at ${endTime.format(timeFmt)} (${durationLabel(s.totalMinutes)})")
        add("$profileName profile · on this phone")
        if (s.autoStart) add("Starts and blocks automatically")
    }

    Column(Modifier.fillMaxSize()) {
        Row(Modifier.fillMaxWidth().height(56.dp).padding(horizontal = Dim.s1), verticalAlignment = Alignment.CenterVertically) {
            IconButton(onClick = onClose) { LockdIcon(LIcon.Close, description = "Close without saving") }
            Text(if (isNew) "New session" else "Edit session", style = UiType.title, color = c.ink, modifier = Modifier.weight(1f).padding(start = Dim.s1))
            if (!isNew) TextAction("Delete", { confirmDelete = true }, danger = true)
        }
        Column(
            Modifier.weight(1f).fillMaxWidth().verticalScroll(rememberScrollState()).padding(horizontal = Dim.gutter).padding(top = Dim.s2, bottom = Dim.s5),
            verticalArrangement = Arrangement.spacedBy(Dim.s5),
        ) {
            OutlinedTextField(
                s.title, { t -> patch { it.copy(title = t.take(80)) } }, label = { Text("Name") },
                placeholder = { Text("e.g. Physics review") }, singleLine = true, shape = RoundedCornerShape(Dim.radius),
                colors = fieldColors(), modifier = Modifier.fillMaxWidth(),
            )
            Column {
                SectionLabel("Repeats")
                SegmentedChoice(listOf(false to "Weekly", true to "One time"), s.oneTime, { one ->
                    patch { if (one) it.copy(date = it.date ?: today) else it.copy(date = null, days = it.days.ifEmpty { setOf(today.dayOfWeek) }) }
                }, label = "Repeats")
                Spacer(Modifier.height(Dim.s3))
                if (s.oneTime) {
                    SettingsGroup {
                        SettingRow("Date", s.date!!.format(dateFmt), icon = LIcon.Calendar, onClick = {
                            val d = s.date ?: today
                            DatePickerDialog(context, { _, y, m, day -> patch { it.copy(date = LocalDate.of(y, m + 1, day)) } }, d.year, d.monthValue - 1, d.dayOfMonth).apply {
                                datePicker.minDate = today.atStartOfDay(zone).toInstant().toEpochMilli()
                            }.show()
                        })
                    }
                } else DayPicker(s.days) { d -> patch { x -> x.copy(days = if (d in x.days) x.days - d else x.days + d) } }
            }
            Column {
                SectionLabel("Time")
                SettingsGroup {
                    SettingRow("Starts at", s.startTime.format(timeFmt), icon = LIcon.Clock, onClick = {
                        TimePickerDialog(context, { _, h, m -> patch { it.copy(startTime = LocalTime.of(h, m)) } },
                            s.startTime.hour, s.startTime.minute, DateFormat.is24HourFormat(context)).show()
                    })
                }
            }
            Column {
                SectionLabel("Focus length")
                LengthControl(s.focusMinutes, { v -> patch { it.copy(focusMinutes = v) } })
            }
            Column {
                SectionLabel("Session")
                SettingsGroup {
                    SettingMenuRow("Breaks", listOf(0 to "None") + listOf(5, 10, 15, 20).map { it to "$it min" }, if (s.rounds > 1) s.breakMinutes else 0, { b ->
                        patch { if (b == 0) it.copy(rounds = 1) else it.copy(breakMinutes = b, rounds = maxOf(2, it.rounds)) }
                    }, icon = LIcon.Coffee)
                    GroupDivider()
                    SettingMenuRow("Focus rounds", (1..6).map { it to if (it == 1) "1 round" else "$it rounds" }, s.rounds, { r ->
                        patch { it.copy(rounds = r) }
                    }, icon = LIcon.Repeat)
                    GroupDivider()
                    SettingMenuRow("Profile", store.state.profiles.map { it.id to it.name }, s.profileId, { p -> patch { it.copy(profileId = p) } }, icon = LIcon.Book)
                    GroupDivider()
                    SettingRow("Runs on", "This phone", icon = LIcon.Phone, supporting = "Paired devices join once pairing is available")
                }
            }
            Column {
                SectionLabel("Reminder and start")
                SettingsGroup {
                    SettingMenuRow("Reminder", app.lockdin.engine.Planner.REMIND_CHOICES.map { it to if (it == 0) "None" else "$it min before" }, s.remindMinutes, { m ->
                        patch { it.copy(remindMinutes = m) }
                    }, icon = LIcon.Clock)
                    GroupDivider()
                    AutoStartRow(s.autoStart, onChange = { on -> if (on) explainAuto = true else patch { it.copy(autoStart = false) } })
                    if (s.autoStart && !app.lockdin.sched.Scheduler.canExact(context)) {
                        GroupDivider()
                        SettingRow("On-time starts", "Allow", supporting = "Without “Alarms & reminders”, Android may start it a few minutes late.", onClick = {
                            runCatching {
                                context.startActivity(android.content.Intent(android.provider.Settings.ACTION_REQUEST_SCHEDULE_EXACT_ALARM,
                                    android.net.Uri.fromParts("package", context.packageName, null)))
                            }
                        })
                    }
                }
                if (!app.lockdin.sched.Notifier.allowed(context)) Text(
                    "Notifications are off for Lockd In, so reminders can’t be shown. Turn them on in Devices › Set up blocking.",
                    style = UiType.caption, color = c.inkMuted, modifier = Modifier.padding(top = Dim.s2),
                )
            }
            Column {
                SectionLabel("Summary")
                SummaryText(summary)
            }
            error?.let {
                Surface(color = c.dangerSoft, shape = RoundedCornerShape(Dim.radius), modifier = Modifier.fillMaxWidth().semantics { liveRegion = LiveRegionMode.Assertive }) {
                    Text(it, style = UiType.label, color = c.danger, modifier = Modifier.padding(Dim.s3))
                }
            }
        }
        ActionBar {
            CalendarClash(store, s, now, zone)
            MainButton(if (isNew) "Save session" else "Save changes", onClick = {
                val candidate = s.copy(id = s.id.ifEmpty { Store.newId() }, title = s.title.trim().ifEmpty { "Focus session" },
                    breakMinutes = if (s.rounds > 1) s.breakMinutes.coerceAtLeast(5) else s.breakMinutes)
                val clash = Schedule.overlaps(candidate, store.state.schedule)
                error = when {
                    !candidate.oneTime && candidate.days.isEmpty() -> "Pick at least one day."
                    candidate.oneTime && candidate.date!!.isBefore(today) -> "Pick today or a later date."
                    Session.validatePlan(candidate.plan) != null -> Session.validatePlan(candidate.plan)
                    clash.isNotEmpty() -> "This overlaps “${clash.first().title}” at ${clash.first().startTime.format(timeFmt)}. Change the time or days."
                    else -> null
                }
                if (error == null) {
                    store.update { st -> st.copy(schedule = st.schedule.filter { it.id != candidate.id } + candidate) }
                    onClose()
                }
            })
        }
    }
    if (explainAuto) {
        val rules = store.state.profiles.firstOrNull { it.id == s.profileId }?.rules
        AlertDialog(
            onDismissRequest = { explainAuto = false },
            title = { Text("Start this session automatically?", style = Type.title) },
            text = {
                Text(
                    "At ${s.startTime.format(timeFmt)} Lockd In will start “${s.title.ifBlank { "this session" }}” without asking" +
                        (if (rules != null && rules.restrictsAnything) " and block ${rulesSummary(rules).replace(" during focus", "").lowercase()}." else ". This profile blocks nothing yet, so it runs as a timer.") +
                        "\n\nYou’ll get a notification when it starts. Ending early follows your commitment setting, and emergency recovery always works." +
                        " If a session is already running, the planned one doesn’t replace it.",
                    style = UiType.body,
                )
            },
            confirmButton = { TextButton(onClick = { patch { it.copy(autoStart = true) }; explainAuto = false }) { Text("Turn on", style = UiType.label, color = c.ink) } },
            dismissButton = { TextButton(onClick = { explainAuto = false }) { Text("Cancel", style = UiType.label, color = c.ink) } },
            containerColor = c.surface,
        )
    }
    if (confirmDelete) {
        AlertDialog(
            onDismissRequest = { confirmDelete = false },
            title = { Text("Delete “${initial.title}”?", style = Type.title) },
            text = { Text("It disappears from your schedule. Sessions you already finished aren’t affected.", style = UiType.body) },
            confirmButton = {
                TextButton(onClick = { store.update { st -> st.copy(schedule = st.schedule.filter { it.id != initial.id }) }; confirmDelete = false; onClose() }) {
                    Text("Delete", style = UiType.label, color = c.danger)
                }
            },
            dismissButton = { TextButton(onClick = { confirmDelete = false }) { Text("Keep", style = UiType.label, color = c.ink) } },
            containerColor = c.surface,
        )
    }
}

/** Seven evenly spaced day toggles; each announces its full name and state. */
@Composable
fun DayPicker(days: Set<DayOfWeek>, onToggle: (DayOfWeek) -> Unit) {
    val c = LocalColors.current
    val loc = Locale.getDefault()
    val large = LocalDensity.current.fontScale > 1.4f
    val week = localWeek()
    Column(verticalArrangement = Arrangement.spacedBy(Dim.s2)) {
        (if (large) week.chunked(4) else listOf(week)).forEach { row ->
            Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.spacedBy(6.dp)) {
                row.forEach { d ->
                    val on = d in days
                    val full = d.getDisplayName(JTextStyle.FULL, loc)
                    Box(
                        Modifier.weight(1f).heightIn(min = Dim.control)
                            .background(if (on) c.ink else c.taupeSoft, CircleShape)
                            .toggleable(on, role = Role.Checkbox) { onToggle(d) }
                            .semantics { contentDescription = full; stateDescription = if (on) "Included" else "Not included" },
                        contentAlignment = Alignment.Center,
                    ) {
                        Text(d.getDisplayName(JTextStyle.SHORT, loc), style = UiType.label.copy(fontSize = 14.sp), color = if (on) c.onInk else c.ink, maxLines = 1)
                    }
                }
                if (large && row.size < 4) repeat(4 - row.size) { Spacer(Modifier.weight(1f)) }
            }
        }
    }
}


@Composable
private fun AutoStartRow(on: Boolean, onChange: (Boolean) -> Unit) {
    val c = LocalColors.current
    Row(
        Modifier.fillMaxWidth().heightIn(min = Dim.control + 4.dp)
            .toggleable(on, role = Role.Switch, onValueChange = onChange)
            .padding(horizontal = Dim.s4, vertical = Dim.s2),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        LockdIcon(LIcon.Focus, size = Dim.icon, tint = c.ink)
        Spacer(Modifier.width(Dim.s3))
        Column(Modifier.weight(1f)) {
            Text("Start automatically", style = UiType.label, color = c.ink)
            Text("Starts and blocks at the set time, without opening the app", style = UiType.caption, color = c.inkMuted)
        }
        Switch(
            checked = on, onCheckedChange = null,
            colors = SwitchDefaults.colors(checkedTrackColor = c.ink, checkedThumbColor = c.onInk, uncheckedTrackColor = c.taupeSoft, uncheckedBorderColor = c.controlLine, uncheckedThumbColor = c.controlLine),
        )
    }
}


/** Warns (never blocks) when the next occurrence overlaps a busy calendar event. */
@Composable
private fun CalendarClash(store: Store, s: ScheduledSession, now: Long, zone: ZoneId) {
    if (!store.state.calendarOn) return
    val c = LocalColors.current
    val probe = s.copy(id = s.id.ifEmpty { "probe" }, enabled = true)
    val next = remember(probe, now / 60_000) { app.lockdin.engine.Planner.occurrences(listOf(probe), now, now + 8 * 86_400_000L, zone).firstOrNull() } ?: return
    val events = rememberCalendarEvents(store, next.start - 3_600_000, next.end + 3_600_000) ?: return
    val clash = app.lockdin.engine.CalendarPlan.overlaps(events, next.start, next.end).firstOrNull() ?: return
    Text(
        "Overlaps “${clash.title}” (${Fmt.clock(clash.start, zone)}–${Fmt.clock(clash.end, zone)}) in your calendar on ${Fmt.dayLabel(next.start, now, zone).lowercase().replaceFirstChar { it.uppercase() }}. You can still save it.",
        style = UiType.caption, color = c.ink,
    )
}
