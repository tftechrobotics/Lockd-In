package app.lockdin.ui

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
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
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.OutlinedTextFieldDefaults
import androidx.compose.material3.RadioButton
import androidx.compose.material3.RadioButtonDefaults
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableLongStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.semantics.LiveRegionMode
import androidx.compose.ui.semantics.Role
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.heading
import androidx.compose.ui.semantics.liveRegion
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.text.input.ImeAction
import androidx.compose.ui.text.input.KeyboardType
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import app.lockdin.engine.OverrideRecord
import app.lockdin.engine.OverrideScope
import app.lockdin.engine.OverrideState
import app.lockdin.engine.PhaseKind
import app.lockdin.engine.Schedule
import app.lockdin.engine.Session
import app.lockdin.engine.SessionStatus
import app.lockdin.engine.Snapshot
import app.lockdin.state.ActiveSession
import app.lockdin.state.AppClock
import app.lockdin.state.LastChoice
import app.lockdin.state.Store
import kotlinx.coroutines.delay
import java.time.ZoneId

/** Re-reads the clock every second while something counts; never the source of truth. */
@Composable
fun rememberNow(clock: AppClock, ticking: Boolean): Long {
    if (!clock.ticks) return clock.now()
    var now by remember { mutableLongStateOf(clock.now()) }
    LaunchedEffect(ticking, clock) {
        now = clock.now()
        while (ticking) {
            delay(1000)
            now = clock.now()
        }
    }
    return if (ticking) now else clock.now()
}

@Composable
fun FocusScreen(store: Store, clock: AppClock, zone: ZoneId, openDevices: () -> Unit, openSchedule: () -> Unit) {
    val active = store.state.active
    if (active == null) Setup(store, clock, zone, openDevices, openSchedule) else SessionView(store, active, clock, zone)
}

private val LENGTHS = listOf(25, 40, 60)

@Composable
private fun Setup(store: Store, clock: AppClock, zone: ZoneId, openDevices: () -> Unit, openSchedule: () -> Unit) {
    val c = LocalColors.current
    val s = store.state
    var intention by rememberSaveable { mutableStateOf("") }
    var focus by rememberSaveable { mutableStateOf(s.last.focusMinutes) }
    var custom by rememberSaveable { mutableStateOf(s.last.focusMinutes !in LENGTHS) }
    var customText by rememberSaveable { mutableStateOf(s.last.focusMinutes.toString()) }
    var breakMin by rememberSaveable { mutableStateOf(if (s.last.rounds > 1) s.last.breakMinutes else 0) }
    var rounds by rememberSaveable { mutableStateOf(maxOf(2, s.last.rounds)) }
    var profileId by rememberSaveable { mutableStateOf(s.last.profileId) }
    var error by remember { mutableStateOf<String?>(null) }
    val now = clock.now()
    val upcoming = Schedule.nextUp(s.schedule, now, zone)

    val segments = if (breakMin > 0) Session.roundsPlan(rounds, focus, breakMin) else Session.roundsPlan(1, focus, 0)
    val total = segments.sumOf { it.minutes }

    Column(verticalArrangement = Arrangement.spacedBy(14.dp), horizontalAlignment = Alignment.CenterHorizontally) {
        TaskTitle(intention, { intention = it.take(120) })
        LockTimer(focus * 60_000L, "Focus time", locked = false, maxWidth = 196.dp)
        Pills(
            LENGTHS.map { it to "$it min" } + (-1 to "Custom"),
            if (custom) -1 else focus,
            { v -> if (v == -1) custom = true else { custom = false; focus = v; error = null } },
        )
        if (custom) {
            OutlinedTextField(
                value = customText,
                onValueChange = { t -> customText = t.filter(Char::isDigit).take(3); focus = customText.toIntOrNull() ?: 0; error = null },
                label = { Text("Minutes") },
                singleLine = true,
                keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Number, imeAction = ImeAction.Done),
                shape = RoundedCornerShape(12.dp),
                colors = fieldColors(),
                modifier = Modifier.fillMaxWidth(),
            )
        }
        val breakOptions = listOf(0 to "No break") + listOf(5, 10, 15, 20).map { it to "Break · $it min" }
        if (breakMin > 0) {
            Row(horizontalArrangement = Arrangement.spacedBy(10.dp)) {
                IconSelect(LIcon.Coffee, "Break", breakOptions, breakMin, { breakMin = it; error = null }, Modifier.weight(1.35f))
                IconSelect(LIcon.Focus, "Focus periods", (2..6).map { it to "× $it" }, rounds, { rounds = it }, Modifier.weight(1f))
            }
        } else {
            IconSelect(LIcon.Coffee, "Break", breakOptions, breakMin, { breakMin = it; error = null }, Modifier.fillMaxWidth())
        }
        IconSelect(LIcon.Book, "Profile", s.profiles.map { it.id to "${it.name} profile" }, profileId, { profileId = it }, Modifier.fillMaxWidth())
        error?.let {
            Surface(color = c.dangerSoft, shape = RoundedCornerShape(12.dp), modifier = Modifier.fillMaxWidth().semantics { liveRegion = LiveRegionMode.Assertive }) {
                Text(it, style = Type.label, color = c.danger, modifier = Modifier.padding(12.dp))
            }
        }
        PrimaryButton("Lock in", onClick = {
            val problem = Session.validatePlan(segments)
            if (problem != null) error = problem
            else store.startSession(
                clock.now(), segments, intention, profileId,
                LastChoice(focus, if (breakMin > 0) breakMin else s.last.breakMinutes, if (breakMin > 0) rounds else 1, profileId),
            )
        })
        DeviceStrip(onOpen = openDevices)
        upcoming?.let { NextLink("Next: ${it.session.title} · ${Fmt.dayLabel(it.start, now, zone)} ${Fmt.clock(it.start, zone)}", openSchedule) }
    }
}

/** The task, edited in place as the screen's title (the reference's "TAM 212 · Practice problems" line). */
@Composable
private fun TaskTitle(value: String, onChange: (String) -> Unit) {
    val c = LocalColors.current
    androidx.compose.foundation.text.BasicTextField(
        value = value,
        onValueChange = onChange,
        singleLine = true,
        textStyle = Type.taskTitle.copy(color = c.ink),
        cursorBrush = androidx.compose.ui.graphics.SolidColor(c.ink),
        keyboardOptions = KeyboardOptions(imeAction = ImeAction.Done),
        modifier = Modifier.fillMaxWidth().heightIn(min = 48.dp).semantics { contentDescription = "Task" },
        decorationBox = { inner ->
            Box(Modifier.fillMaxWidth().padding(vertical = 8.dp), contentAlignment = Alignment.Center) {
                if (value.isEmpty()) Text("What are you working on?", style = Type.taskTitle, color = c.inkMuted, modifier = Modifier.fillMaxWidth())
                inner()
            }
        },
    )
}

@Composable
private fun NextLink(text: String, onClick: () -> Unit) {
    val c = LocalColors.current
    Surface(onClick = onClick, color = c.ground, modifier = Modifier.fillMaxWidth().heightIn(min = 48.dp)) {
        Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(8.dp)) {
            LockdIcon(LIcon.Calendar, size = 16.dp, tint = c.inkMuted)
            Text(text, style = Type.caption, color = c.ink, modifier = Modifier.weight(1f))
            LockdIcon(LIcon.Chevron, size = 16.dp, tint = c.inkMuted)
        }
    }
}

@Composable
fun fieldColors() = LocalColors.current.let { c ->
    OutlinedTextFieldDefaults.colors(
        focusedContainerColor = c.surface, unfocusedContainerColor = c.surface,
        focusedBorderColor = c.ink, unfocusedBorderColor = c.controlLine,
        focusedLabelColor = c.ink, unfocusedLabelColor = c.inkMuted, cursorColor = c.ink,
        focusedTextColor = c.ink, unfocusedTextColor = c.ink,
        focusedPlaceholderColor = c.inkMuted, unfocusedPlaceholderColor = c.inkMuted,
    )
}

@Composable
private fun SessionView(store: Store, session: ActiveSession, clock: AppClock, zone: ZoneId) {
    val c = LocalColors.current
    val occ = Session.occurrenceFrom(session.start, session.segments)
    val first = Session.evaluate(occ, session.controls, clock.now())
    val running = first.status == SessionStatus.ACTIVE || first.status == SessionStatus.UPCOMING
    val now = rememberNow(clock, running)
    val snap = Session.evaluate(occ, session.controls, now)
    if (snap.status != SessionStatus.ACTIVE && snap.status != SessionStatus.UPCOMING) {
        Recap(store, session, snap, clock, zone)
        return
    }
    val phase = snap.phase ?: return
    val remaining = phase.end - now
    val lifted = snap.overrideState is OverrideState.ActiveForFocusPeriod
    val focusCount = session.segments.count { it.kind == PhaseKind.FOCUS }
    val focusIndex = session.segments.take(phase.index + 1).count { it.kind == PhaseKind.FOCUS }
    val next = occ.phases.getOrNull(phase.index + 1)
    val tone = store.state.tone
    val task = session.intention

    val status = when { lifted -> "Unlocked for now."; snap.restricting -> "Locked in."; else -> "On a break." }
    val caption = if (phase.kind == PhaseKind.FOCUS) (if (focusCount > 1) "Focus $focusIndex of $focusCount" else "Focus time") else "Break"
    val nudge = when {
        phase.kind == PhaseKind.BREAK && remaining <= 60_000 -> message(tone, Moment.BREAK_ENDING, task)
        phase.kind == PhaseKind.BREAK -> message(tone, Moment.BREAK_START, time = Fmt.clock(phase.end, zone))
        lifted -> "Unlocked until ${Fmt.clock((snap.overrideState as OverrideState.ActiveForFocusPeriod).until, zone)}. Later focus periods lock again."
        else -> message(tone, Moment.FOCUS_START, task)
    }
    val profile = store.state.profiles.firstOrNull { it.id == session.profileId }?.name
    val meta = listOfNotNull(
        (if (phase.kind == PhaseKind.FOCUS) (if (next != null) "Break at" else "Ends") else "Focus at") + " " + Fmt.clock(phase.end, zone),
        if (next != null) "Session ends ${Fmt.clock(occ.end, zone)}" else null,
        profile,
    ).joinToString(" · ")

    Column(verticalArrangement = Arrangement.spacedBy(14.dp), horizontalAlignment = Alignment.CenterHorizontally) {
        Column(horizontalAlignment = Alignment.CenterHorizontally) {
            Text(status, style = Type.display, color = c.ink, textAlign = TextAlign.Center,
                modifier = Modifier.semantics { heading(); liveRegion = LiveRegionMode.Polite })
            task?.let { Text(it, style = Type.body.copy(fontSize = 18.sp), color = c.ink, textAlign = TextAlign.Center) }
        }
        LockTimer(remaining, caption, locked = snap.restricting, maxWidth = 196.dp)
        Text(meta, style = Type.caption, color = c.inkMuted, textAlign = TextAlign.Center)
        nudge?.let { Text(it, style = Type.body, color = c.inkMuted, textAlign = TextAlign.Center, modifier = Modifier.semantics { liveRegion = LiveRegionMode.Polite }) }
        HorizontalDivider(color = c.line)
        OutPanel(store, session, snap, now, clock)
        HonestLine()
    }
}

/** One line, never a claim: blocking on this phone isn't built yet. */
@Composable
private fun HonestLine() {
    val c = LocalColors.current
    Row(Modifier.fillMaxWidth().heightIn(min = 40.dp), verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(6.dp, Alignment.CenterHorizontally)) {
        LockdIcon(LIcon.Info, size = 16.dp, tint = c.inkMuted)
        Text("Not blocking: blocking isn’t set up on this phone", style = Type.caption, color = c.inkMuted)
    }
}

@Composable
private fun OutPanel(store: Store, session: ActiveSession, snap: Snapshot, now: Long, clock: AppClock) {
    val c = LocalColors.current
    var open by rememberSaveable { mutableStateOf(false) }
    var scope by rememberSaveable { mutableStateOf(OverrideScope.CURRENT_FOCUS_PERIOD) }
    var confirmEmergency by rememberSaveable { mutableStateOf(false) }
    val pending = snap.overrideState as? OverrideState.Pending
    val inFocus = snap.phase?.kind == PhaseKind.FOCUS && snap.overrideState !is OverrideState.ActiveForFocusPeriod

    Column(Modifier.fillMaxWidth(), verticalArrangement = Arrangement.spacedBy(8.dp)) {
        when {
            pending != null -> {
                Text(
                    "Unlocking in ${Fmt.countdown(pending.effectiveAt - now)}" +
                        if (pending.scope == OverrideScope.CURRENT_FOCUS_PERIOD) " · this focus period" else " · ends the session",
                    style = Type.bodyStrong, color = c.ink,
                )
                OutlinedButton(onClick = {
                    val t = clock.now()
                    store.setControls(session.id) { ctl ->
                        ctl.copy(overrides = ctl.overrides.map { o ->
                            if (o.cancelledAt == null && o.requestedAt + o.delaySeconds * 1000L > t) o.copy(cancelledAt = t) else o
                        })
                    }
                }, modifier = Modifier.heightIn(min = 48.dp)) { Text("Cancel unlock", style = Type.label, color = c.ink) }
            }
            open -> {
                Text("Unlock after a ${Session.DEFAULT_OVERRIDE_DELAY_SECONDS}-second wait", style = Type.caption, color = c.inkMuted)
                Column(Modifier.fillMaxWidth().selectableGroup()) {
                    if (inFocus) ScopeOption("This focus period", scope == OverrideScope.CURRENT_FOCUS_PERIOD) { scope = OverrideScope.CURRENT_FOCUS_PERIOD }
                    ScopeOption("Rest of this session", !inFocus || scope == OverrideScope.REST_OF_SESSION) { scope = OverrideScope.REST_OF_SESSION }
                }
                PrimaryButton("Start unlock", arrow = false, onClick = {
                    val phase = snap.phase ?: return@PrimaryButton
                    val t = clock.now()
                    store.setControls(session.id) { ctl ->
                        ctl.copy(overrides = ctl.overrides + OverrideRecord(Store.newId(), if (inFocus) scope else OverrideScope.REST_OF_SESSION, phase.index, t, Session.DEFAULT_OVERRIDE_DELAY_SECONDS))
                    }
                    open = false
                })
                QuietButton("Cancel", { open = false })
            }
            else -> Row(Modifier.fillMaxWidth(), verticalAlignment = Alignment.CenterVertically) {
                OutlinedButton(
                    onClick = { open = true }, shape = RoundedCornerShape(12.dp),
                    border = androidx.compose.foundation.BorderStroke(1.dp, c.controlLine), modifier = Modifier.heightIn(min = 48.dp),
                ) { Text("Need out?", style = Type.label, color = c.ink) }
                Spacer(Modifier.weight(1f))
                QuietButton("End now", { confirmEmergency = true }, danger = true)
            }
        }
        if (open || pending != null) QuietButton("End now", { confirmEmergency = true }, danger = true)
    }
    if (confirmEmergency) {
        AlertDialog(
            onDismissRequest = { confirmEmergency = false },
            title = { Text("End the session now?", style = Type.title) },
            text = { Text("Emergency recovery ends it immediately, with no countdown.", style = Type.body) },
            confirmButton = {
                TextButton(onClick = { store.setControls(session.id) { it.copy(emergencyRecoveryAt = clock.now()) }; confirmEmergency = false }) {
                    Text("End session now", style = Type.label, color = c.danger)
                }
            },
            dismissButton = { TextButton(onClick = { confirmEmergency = false }) { Text("Keep going", style = Type.label, color = c.ink) } },
            containerColor = c.surface,
        )
    }
}

@Composable
private fun ScopeOption(title: String, selected: Boolean, onClick: () -> Unit) {
    val c = LocalColors.current
    Row(
        Modifier.fillMaxWidth().heightIn(min = 48.dp).selectable(selected, role = Role.RadioButton, onClick = onClick),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        RadioButton(selected = selected, onClick = null, colors = RadioButtonDefaults.colors(selectedColor = c.ink, unselectedColor = c.controlLine))
        Spacer(Modifier.width(12.dp))
        Text(title, style = Type.body, color = c.ink)
    }
}

@Composable
private fun Recap(store: Store, session: ActiveSession, snap: Snapshot, clock: AppClock, zone: ZoneId) {
    val c = LocalColors.current
    val occ = Session.occurrenceFrom(session.start, session.segments)
    val endedAt = when (snap.status) {
        SessionStatus.COMPLETED -> occ.end
        SessionStatus.ENDED_BY_EMERGENCY_RECOVERY -> session.controls.emergencyRecoveryAt ?: occ.end
        else -> session.controls.overrides
            .filter { it.scope == OverrideScope.REST_OF_SESSION && it.cancelledAt == null }
            .minOfOrNull { it.requestedAt + it.delaySeconds * 1000L }?.coerceAtMost(occ.end) ?: occ.end
    }
    val focusedMin = occ.phases.filter { it.kind == PhaseKind.FOCUS }.sumOf { maxOf(0L, minOf(it.end, endedAt) - it.start) } / 60_000
    val how = when (snap.status) {
        SessionStatus.COMPLETED -> "Finished as planned."
        SessionStatus.ENDED_BY_OVERRIDE -> "Ended early with an unlock."
        else -> "Ended with emergency recovery."
    }
    Column(verticalArrangement = Arrangement.spacedBy(14.dp), horizontalAlignment = Alignment.CenterHorizontally) {
        Column(horizontalAlignment = Alignment.CenterHorizontally) {
            Text("Session complete.", style = Type.display, color = c.ink, textAlign = TextAlign.Center, modifier = Modifier.semantics { heading() })
            session.intention?.let { Text(it, style = Type.body.copy(fontSize = 18.sp), color = c.ink, textAlign = TextAlign.Center) }
        }
        LockTimer(0, "Done", locked = false, maxWidth = 170.dp)
        Text("$focusedMin min focused · ${Fmt.clock(session.start, zone)}–${Fmt.clock(endedAt, zone)}", style = Type.caption, color = c.inkMuted, textAlign = TextAlign.Center)
        Text(how, style = Type.bodyStrong, color = c.ink, textAlign = TextAlign.Center)
        if (snap.status == SessionStatus.COMPLETED) message(store.state.tone, Moment.DONE)?.let { Text(it, style = Type.body, color = c.inkMuted, textAlign = TextAlign.Center) }
        PrimaryButton("Same again", arrow = false, onClick = {
            store.startSession(clock.now(), session.segments, session.intention, session.profileId, store.state.last)
        })
        QuietButton("Done", { store.finish() })
    }
}
