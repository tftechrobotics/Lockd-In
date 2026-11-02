// Calendar suggestions on the Schedule tab and calendar settings (roadmap step 4, C).
// Read-only and opt-in. A suggestion only opens the session editor, filled in; the person saves.
package app.lockdin.ui

import android.Manifest
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.selection.toggleable
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.Checkbox
import androidx.compose.material3.CheckboxDefaults
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.produceState
import androidx.compose.runtime.remember
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.semantics.Role
import androidx.compose.ui.semantics.heading
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import app.lockdin.cal.DeviceCalendar
import app.lockdin.engine.CalEvent
import app.lockdin.engine.CalendarPlan
import app.lockdin.engine.FreeWindow
import app.lockdin.engine.ScheduledSession
import app.lockdin.state.Store
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import java.time.Instant
import java.time.LocalDate
import java.time.ZoneId

/** Calendar events from `from` to `to`, read off the main thread; null while loading or off. */
@Composable
fun rememberCalendarEvents(store: Store, from: Long, to: Long): List<CalEvent>? {
    val ctx = LocalContext.current
    val on = store.state.calendarOn
    val hidden = store.state.calendarHidden
    val events by produceState<List<CalEvent>?>(null, on, hidden, from / 300_000, to / 300_000, Resumes.tick) {
        value = if (!on || !DeviceCalendar.permitted(ctx)) null
        else withContext(Dispatchers.IO) { DeviceCalendar.events(ctx, from, to, hidden) }
    }
    return events
}

/** "Free time" suggestions for today and tomorrow, or the offer to turn them on. */
@Composable
fun CalendarSuggestions(store: Store, now: Long, zone: ZoneId, onPlan: (ScheduledSession) -> Unit) {
    val c = LocalColors.current
    val ctx = LocalContext.current
    val today = Instant.ofEpochMilli(now).atZone(zone).toLocalDate()
    val ask = rememberLauncherForActivityResult(ActivityResultContracts.RequestPermission()) { granted ->
        if (granted) store.update { it.copy(calendarOn = true) }
        Resumes.bump()
    }
    val permitted = remember(Resumes.tick) { DeviceCalendar.permitted(ctx) }
    if (!store.state.calendarOn || !permitted) {
        Surface(shape = RoundedCornerShape(Dim.radius), color = c.surface, border = BorderStroke(1.dp, c.line), modifier = Modifier.fillMaxWidth()) {
            Column(Modifier.padding(Dim.s4), verticalArrangement = Arrangement.spacedBy(Dim.s2)) {
                Text("Find free time in your calendar", style = UiType.label.copy(fontWeight = FontWeight.SemiBold), color = c.ink)
                Text(
                    "Lockd In can read the calendars on this phone, including Google Calendar, to suggest study times. It never changes your calendar, and nothing is planned unless you save it.",
                    style = UiType.caption, color = c.inkMuted,
                )
                SecondaryButton(if (store.state.calendarOn && !permitted) "Allow calendar access" else "Use my calendar", {
                    if (permitted) store.update { it.copy(calendarOn = true) } else ask.launch(Manifest.permission.READ_CALENDAR)
                })
            }
        }
        return
    }
    val from = today.atStartOfDay(zone).toInstant().toEpochMilli()
    val to = today.plusDays(2).atStartOfDay(zone).toInstant().toEpochMilli()
    val events = rememberCalendarEvents(store, from, to)
    Column(verticalArrangement = Arrangement.spacedBy(Dim.s2)) {
        Text("Free time", style = UiType.section, color = c.inkMuted, modifier = Modifier.semantics { heading() })
        when {
            events == null -> Text("Reading your calendar…", style = UiType.caption, color = c.inkMuted)
            else -> {
                val days = listOf(today, today.plusDays(1)).map { d ->
                    d to CalendarPlan.freeWindows(events, d, zone, now).take(3)
                }
                if (days.all { it.second.isEmpty() }) Text("No free blocks of 25 minutes or more between 8 AM and 10 PM today or tomorrow.", style = UiType.caption, color = c.inkMuted)
                else SettingsGroup {
                    var first = true
                    days.forEach { (d, windows) ->
                        windows.forEach { w ->
                            if (!first) GroupDivider()
                            first = false
                            WindowRow(w, d, today, zone) { onPlan(suggested(store, w, d, zone)) }
                        }
                    }
                }
                Text("From the calendars on this phone. Tap one to plan it; you can change anything before saving.", style = UiType.caption, color = c.inkMuted)
            }
        }
    }
}

private fun suggested(store: Store, w: FreeWindow, d: LocalDate, zone: ZoneId): ScheduledSession {
    val start = Instant.ofEpochMilli(w.start).atZone(zone).toLocalTime().withSecond(0).withNano(0)
    return ScheduledSession("", "Study", emptySet(), start, CalendarPlan.suggestedMinutes(w), 10, 1, store.state.last.profileId, date = d)
}

@Composable
private fun WindowRow(w: FreeWindow, d: LocalDate, today: LocalDate, zone: ZoneId, onClick: () -> Unit) {
    val c = LocalColors.current
    Row(
        Modifier.fillMaxWidth().heightIn(min = 56.dp).clickable(role = Role.Button, onClickLabel = "Plan a session", onClick = onClick)
            .semantics(mergeDescendants = true) {}.padding(horizontal = Dim.s4, vertical = Dim.s2),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        Column(Modifier.weight(1f)) {
            Text("${if (d == today) "Today" else "Tomorrow"} · ${Fmt.clock(w.start, zone)}–${Fmt.clock(w.end, zone)}", style = UiType.label, color = c.ink)
            Text("${minutesLabel(w.minutes)} free", style = UiType.caption, color = c.inkMuted)
        }
        LockdIcon(LIcon.Plus, size = 18.dp, tint = c.inkMuted)
    }
}

/** Settings › Calendar: on/off and which calendars count. */
@Composable
fun CalendarSettings(store: Store) {
    val c = LocalColors.current
    val ctx = LocalContext.current
    val permitted = remember(Resumes.tick) { DeviceCalendar.permitted(ctx) }
    val ask = rememberLauncherForActivityResult(ActivityResultContracts.RequestPermission()) { granted ->
        if (granted) store.update { it.copy(calendarOn = true) }
        Resumes.bump()
    }
    val cals by produceState(emptyList<app.lockdin.cal.DeviceCal>(), permitted, store.state.calendarOn) {
        value = if (permitted && store.state.calendarOn) withContext(Dispatchers.IO) { DeviceCalendar.calendars(ctx) } else emptyList()
    }
    Column {
        SectionLabel("Calendar")
        SettingsGroup {
            SwitchRow("Suggest times from my calendar", "Reads the calendars on this phone, including Google Calendar. Never changes them.",
                store.state.calendarOn && permitted) { on ->
                if (!on) store.update { it.copy(calendarOn = false) }
                else if (permitted) store.update { it.copy(calendarOn = true) }
                else ask.launch(Manifest.permission.READ_CALENDAR)
            }
            if (store.state.calendarOn && permitted && cals.isNotEmpty()) {
                cals.forEach { cal ->
                    GroupDivider()
                    val on = cal.id !in store.state.calendarHidden
                    Row(
                        Modifier.fillMaxWidth().heightIn(min = Dim.control + 4.dp)
                            .toggleable(on, role = Role.Checkbox) { v ->
                                store.update { s -> s.copy(calendarHidden = if (v) s.calendarHidden - cal.id else s.calendarHidden + cal.id) }
                            }.padding(horizontal = Dim.s4),
                        verticalAlignment = Alignment.CenterVertically,
                    ) {
                        Column(Modifier.weight(1f)) {
                            Text(cal.name, style = UiType.body, color = c.ink)
                            if (cal.account.isNotBlank()) Text(cal.account, style = UiType.caption, color = c.inkMuted)
                        }
                        Checkbox(on, onCheckedChange = null, colors = CheckboxDefaults.colors(checkedColor = c.ink, uncheckedColor = c.controlLine, checkmarkColor = c.onInk))
                    }
                }
            }
        }
        Spacer(Modifier.height(Dim.s2))
        Text(
            when {
                store.state.calendarOn && !permitted -> "Calendar access is off in Android, so suggestions can’t be shown."
                store.state.calendarOn && cals.isEmpty() -> "No calendars found on this phone. Add your Google account in Android settings to sync Google Calendar."
                else -> "Event details stay on this phone. Only busy times are used; all-day events and events marked free don’t count."
            },
            style = UiType.caption, color = c.inkMuted,
        )
    }
}
