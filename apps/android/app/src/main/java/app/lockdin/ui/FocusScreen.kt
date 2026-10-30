package app.lockdin.ui

import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.layout.widthIn
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.selection.selectable
import androidx.compose.foundation.selection.selectableGroup
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.text.KeyboardActions
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.AlertDialog
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
import androidx.compose.ui.platform.LocalFocusManager
import androidx.compose.ui.semantics.LiveRegionMode
import androidx.compose.ui.semantics.Role
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.heading
import androidx.compose.ui.semantics.liveRegion
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.input.ImeAction
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
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

/**
 * Whether this build can restrict apps on this phone. False until the blocking service
 * from the probe is built into the app: the UI then says "Start timer" and never claims
 * protection. Flip only with verified enforcement (see docs/device-test-checklist.md).
 */
const val PHONE_BLOCKING_AVAILABLE = false

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
    if (active == null) Setup(store, clock, zone, openDevices, openSchedule) else SessionView(store, active, clock, zone, openDevices)
}

/** Scrolling content with the action area pinned below it (above the bottom navigation). */
@Composable
fun WithActionBar(content: @Composable () -> Unit, actions: @Composable () -> Unit, note: (@Composable () -> Unit)? = null) {
    // With large text the pinned bar would eat the screen, so the status note moves into
    // the scrolling content and only the actions stay pinned.
    val large = androidx.compose.ui.platform.LocalDensity.current.fontScale > 1.3f
    Column(Modifier.fillMaxSize()) {
        Column(
            Modifier.weight(1f).fillMaxWidth().verticalScroll(rememberScrollState())
                .padding(horizontal = Dim.gutter).padding(top = Dim.s2, bottom = Dim.s5),
            verticalArrangement = Arrangement.spacedBy(Dim.s5),
        ) { content(); if (large) note?.invoke() }
        ActionBar { if (!large) note?.invoke(); actions() }
    }
}

@Composable
private fun Setup(store: Store, clock: AppClock, zone: ZoneId, openDevices: () -> Unit, openSchedule: () -> Unit) {
    val c = LocalColors.current
    val s = store.state
    var intention by rememberSaveable { mutableStateOf("") }
    var focus by rememberSaveable { mutableStateOf(s.last.focusMinutes) }
    var breakMin by rememberSaveable { mutableStateOf(if (s.last.rounds > 1) s.last.breakMinutes else 0) }
    var rounds by rememberSaveable { mutableStateOf(maxOf(2, s.last.rounds)) }
    var profileId by rememberSaveable { mutableStateOf(s.last.profileId) }
    var error by remember { mutableStateOf<String?>(null) }
    val focusManager = LocalFocusManager.current
    val now = clock.now()
    val upcoming = Schedule.nextUp(s.schedule, now, zone)

    val effectiveRounds = if (breakMin > 0) rounds else 1
    val segments = Session.roundsPlan(effectiveRounds, focus, if (breakMin > 0) breakMin else 0)
    val total = segments.sumOf { it.minutes }
    val planText = if (effectiveRounds > 1) "$effectiveRounds rounds of $focus min, $breakMin min breaks" else "One $focus-minute round, no breaks"

    WithActionBar(
        content = {
            OutlinedTextField(
                value = intention,
                onValueChange = { intention = it.take(120) },
                label = { Text("Task (optional)") },
                placeholder = { Text("What are you working on?") },
                singleLine = true,
                keyboardOptions = KeyboardOptions(imeAction = ImeAction.Done),
                keyboardActions = KeyboardActions(onDone = { focusManager.clearFocus() }),
                shape = RoundedCornerShape(Dim.radius),
                colors = fieldColors(),
                modifier = Modifier.fillMaxWidth().semantics { contentDescription = "Task" },
            )

            // Lock-and-timer, small, beside a plain summary of the plan.
            Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(Dim.s4)) {
                LockTimer(focus * 60_000L, "Focus length", locked = false, maxWidth = 92.dp, compact = true, modifier = Modifier.width(92.dp))
                Column(Modifier.weight(1f).semantics(mergeDescendants = true) {}) {
                    Text(durationLabel(total), style = UiType.figure, color = c.ink)
                    Text("Ends at ${Fmt.clock(now + total * 60_000L, zone)}", style = UiType.body, color = c.ink)
                    Text(planText, style = UiType.caption, color = c.inkMuted)
                }
            }

            Column {
                SectionLabel("Focus length")
                LengthControl(focus, { focus = it; error = null })
            }

            Column {
                SectionLabel("Session")
                SettingsGroup {
                    SettingMenuRow("Profile", s.profiles.map { it.id to it.name }, profileId, { profileId = it }, icon = LIcon.Book)
                    GroupDivider()
                    SettingMenuRow(
                        "Breaks", listOf(0 to "None") + listOf(5, 10, 15, 20).map { it to "$it min" }, breakMin,
                        { breakMin = it; error = null }, icon = LIcon.Coffee,
                    )
                    GroupDivider()
                    SettingMenuRow(
                        "Focus rounds", (1..6).map { it to if (it == 1) "1 round" else "$it rounds" }, effectiveRounds,
                        { rounds = it; if (it > 1 && breakMin == 0) breakMin = 10 }, icon = LIcon.Repeat,
                        supporting = if (breakMin == 0) "Add a break to split into rounds" else null,
                    )
                }
            }

            error?.let {
                Surface(color = c.dangerSoft, shape = RoundedCornerShape(Dim.radius), modifier = Modifier.fillMaxWidth().semantics { liveRegion = LiveRegionMode.Assertive }) {
                    Text(it, style = UiType.label, color = c.danger, modifier = Modifier.padding(Dim.s3))
                }
            }

            upcoming?.let { u ->
                Row(
                    Modifier.fillMaxWidth().heightIn(min = Dim.control).clickable(role = Role.Button, onClick = openSchedule),
                    verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(Dim.s3),
                ) {
                    LockdIcon(LIcon.Calendar, size = Dim.icon, tint = c.inkMuted)
                    Text("Next: ${u.session.title} · ${Fmt.dayLabel(u.start, now, zone)} ${Fmt.clock(u.start, zone)}", style = UiType.caption, color = c.ink, modifier = Modifier.weight(1f))
                    LockdIcon(LIcon.Chevron, size = 18.dp, tint = c.inkMuted)
                }
            }
        },
        note = {
            if (PHONE_BLOCKING_AVAILABLE) ProtectionLine("Apps outside ${s.profiles.firstOrNull { it.id == profileId }?.name ?: "your profile"} are blocked during focus", true, null) {}
            else ProtectionLine("Timer only. Apps stay available on this phone.", false, "Set up blocking", openDevices)
        },
        actions = {
            MainButton(if (PHONE_BLOCKING_AVAILABLE) "Start focus" else "Start timer", icon = LIcon.Arrow, onClick = {
                val problem = Session.validatePlan(segments)
                if (problem != null) error = problem
                else {
                    focusManager.clearFocus()
                    store.startSession(
                        clock.now(), segments, intention, profileId,
                        LastChoice(focus, if (breakMin > 0) breakMin else s.last.breakMinutes, effectiveRounds, profileId),
                    )
                }
            })
        },
    )
}

@Composable
fun fieldColors() = LocalColors.current.let { c ->
    OutlinedTextFieldDefaults.colors(
        focusedContainerColor = c.surface, unfocusedContainerColor = c.surface,
        focusedBorderColor = c.ink, unfocusedBorderColor = c.controlLine,
        focusedLabelColor = c.ink, unfocusedLabelColor = c.inkMuted, cursorColor = c.ink,
        focusedTextColor = c.ink, unfocusedTextColor = c.ink,
        focusedPlaceholderColor = c.inkMuted, unfocusedPlaceholderColor = c.inkMuted,
        focusedSuffixColor = c.inkMuted, unfocusedSuffixColor = c.inkMuted,
    )
}

@Composable
private fun SessionView(store: Store, session: ActiveSession, clock: AppClock, zone: ZoneId, openDevices: () -> Unit) {
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
    val protected = PHONE_BLOCKING_AVAILABLE && snap.restricting
    val round = if (focusCount > 1) " · round $focusIndex of $focusCount" else ""

    val tag = when {
        phase.kind == PhaseKind.BREAK -> "Break$round"
        lifted -> "Unlocked$round"
        else -> "Focus$round"
    }
    val caption = if (phase.kind == PhaseKind.BREAK) "left in break" else "left to focus"
    val nudge = when {
        phase.kind == PhaseKind.BREAK && remaining <= 60_000 -> message(tone, Moment.BREAK_ENDING, task)
        phase.kind == PhaseKind.BREAK -> message(tone, Moment.BREAK_START, time = Fmt.clock(phase.end, zone))
        lifted -> "Unlocked until ${Fmt.clock((snap.overrideState as OverrideState.ActiveForFocusPeriod).until, zone)}. Later rounds start as planned."
        else -> message(tone, Moment.FOCUS_START, task)
    }
    val facts = buildList {
        when {
            phase.kind == PhaseKind.BREAK -> add("Focus resumes" to Fmt.clock(phase.end, zone))
            next != null -> add("Next break" to Fmt.clock(phase.end, zone))
            else -> add("Ends at" to Fmt.clock(phase.end, zone))
        }
        if (next != null) add("Session ends" to Fmt.clock(occ.end, zone))
    }
    val protection = when {
        protected -> "Apps outside your profile are blocked"
        phase.kind == PhaseKind.BREAK && PHONE_BLOCKING_AVAILABLE -> "Apps are available until focus resumes"
        else -> "Timer only. Apps stay available on this phone."
    }

    WithActionBar(
        content = {
            Column(Modifier.fillMaxWidth(), horizontalAlignment = Alignment.CenterHorizontally, verticalArrangement = Arrangement.spacedBy(Dim.s3)) {
                Spacer(Modifier.heightIn(min = Dim.s2))
                Row(Modifier.semantics(mergeDescendants = true) { liveRegion = LiveRegionMode.Polite }) { PhaseTag(tag, phase.kind == PhaseKind.FOCUS && !lifted) }
                Text(task ?: "Focus session", style = UiType.title, color = c.ink, textAlign = TextAlign.Center, modifier = Modifier.semantics { heading() })
                LockTimer(remaining, caption, locked = protected, maxWidth = 176.dp, modifier = Modifier.widthIn(max = 176.dp))
                Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.SpaceEvenly) {
                    facts.forEach { (k, v) ->
                        Column(Modifier.semantics(mergeDescendants = true) {}, horizontalAlignment = Alignment.CenterHorizontally) {
                            Text(k, style = UiType.caption, color = c.inkMuted)
                            Text(v, style = Type.fact, color = c.ink)
                        }
                    }
                }
                nudge?.let { Text(it, style = UiType.body, color = c.inkMuted, textAlign = TextAlign.Center) }
            }
        },
        note = { ProtectionLine(protection, protected, if (protected) null else "Set up blocking", openDevices) },
        actions = {
            OutPanel(store, session, snap, now, clock)
        },
    )
}

@Composable
private fun OutPanel(store: Store, session: ActiveSession, snap: Snapshot, now: Long, clock: AppClock) {
    val c = LocalColors.current
    var open by rememberSaveable { mutableStateOf(false) }
    var scope by rememberSaveable { mutableStateOf(OverrideScope.CURRENT_FOCUS_PERIOD) }
    var confirmEmergency by rememberSaveable { mutableStateOf(false) }
    val pending = snap.overrideState as? OverrideState.Pending
    val inFocus = snap.phase?.kind == PhaseKind.FOCUS && snap.overrideState !is OverrideState.ActiveForFocusPeriod

    Column(Modifier.fillMaxWidth(), verticalArrangement = Arrangement.spacedBy(Dim.s1)) {
        when {
            pending != null -> Row(verticalAlignment = Alignment.CenterVertically) {
                Text(
                    "Unlocking in ${Fmt.countdown(pending.effectiveAt - now)}" +
                        if (pending.scope == OverrideScope.CURRENT_FOCUS_PERIOD) " · this round" else " · ends the session",
                    style = UiType.label, color = c.ink, modifier = Modifier.weight(1f),
                )
                SecondaryButton("Cancel unlock", onClick = {
                    val t = clock.now()
                    store.setControls(session.id) { ctl ->
                        ctl.copy(overrides = ctl.overrides.map { o ->
                            if (o.cancelledAt == null && o.requestedAt + o.delaySeconds * 1000L > t) o.copy(cancelledAt = t) else o
                        })
                    }
                })
            }
            open -> {
                Text("Unlock after a ${Session.DEFAULT_OVERRIDE_DELAY_SECONDS}-second wait", style = UiType.caption, color = c.inkMuted)
                Column(Modifier.fillMaxWidth().selectableGroup()) {
                    if (inFocus) ScopeOption("This round", scope == OverrideScope.CURRENT_FOCUS_PERIOD) { scope = OverrideScope.CURRENT_FOCUS_PERIOD }
                    ScopeOption("Rest of this session", !inFocus || scope == OverrideScope.REST_OF_SESSION) { scope = OverrideScope.REST_OF_SESSION }
                }
                Row(horizontalArrangement = Arrangement.spacedBy(Dim.s2), verticalAlignment = Alignment.CenterVertically) {
                    MainButton("Start unlock", modifier = Modifier.weight(1f), onClick = {
                        val phase = snap.phase ?: return@MainButton
                        val t = clock.now()
                        store.setControls(session.id) { ctl ->
                            ctl.copy(overrides = ctl.overrides + OverrideRecord(Store.newId(), if (inFocus) scope else OverrideScope.REST_OF_SESSION, phase.index, t, Session.DEFAULT_OVERRIDE_DELAY_SECONDS))
                        }
                        open = false
                    })
                    TextAction("Cancel", { open = false })
                }
            }
            else -> Row(verticalAlignment = Alignment.CenterVertically) {
                SecondaryButton("Need out?", { open = true })
                Spacer(Modifier.weight(1f))
                TextAction("End now", { confirmEmergency = true }, danger = true)
            }
        }
        if (open || pending != null) Row { Spacer(Modifier.weight(1f)); TextAction("End now", { confirmEmergency = true }, danger = true) }
    }
    if (confirmEmergency) {
        AlertDialog(
            onDismissRequest = { confirmEmergency = false },
            title = { Text("End the session now?", style = Type.title) },
            text = { Text("Emergency recovery ends it immediately, with no countdown.", style = UiType.body) },
            confirmButton = {
                TextButton(onClick = { store.setControls(session.id) { it.copy(emergencyRecoveryAt = clock.now()) }; confirmEmergency = false }) {
                    Text("End session now", style = UiType.label, color = c.danger)
                }
            },
            dismissButton = { TextButton(onClick = { confirmEmergency = false }) { Text("Keep going", style = UiType.label, color = c.ink) } },
            containerColor = c.surface,
        )
    }
}

@Composable
private fun ScopeOption(title: String, selected: Boolean, onClick: () -> Unit) {
    val c = LocalColors.current
    Row(
        Modifier.fillMaxWidth().heightIn(min = Dim.control).selectable(selected, role = Role.RadioButton, onClick = onClick),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        RadioButton(selected = selected, onClick = null, colors = RadioButtonDefaults.colors(selectedColor = c.ink, unselectedColor = c.controlLine))
        Spacer(Modifier.width(Dim.s3))
        Text(title, style = UiType.body, color = c.ink)
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
    WithActionBar(
        content = {
            Column(Modifier.fillMaxWidth(), horizontalAlignment = Alignment.CenterHorizontally, verticalArrangement = Arrangement.spacedBy(Dim.s3)) {
                Spacer(Modifier.heightIn(min = Dim.s2))
                PhaseTag("Session complete", false)
                Text(session.intention ?: "Focus session", style = UiType.title, color = c.ink, textAlign = TextAlign.Center, modifier = Modifier.semantics { heading() })
                LockTimer(0, "Done", locked = false, maxWidth = 120.dp, compact = true, modifier = Modifier.widthIn(max = 120.dp))
                Text("$focusedMin min focused · ${Fmt.clock(session.start, zone)}–${Fmt.clock(endedAt, zone)}", style = UiType.body, color = c.ink, textAlign = TextAlign.Center)
                Text(how, style = UiType.label.copy(fontWeight = FontWeight.SemiBold), color = c.ink, textAlign = TextAlign.Center)
                if (snap.status == SessionStatus.COMPLETED) message(store.state.tone, Moment.DONE)?.let { Text(it, style = UiType.body, color = c.inkMuted, textAlign = TextAlign.Center) }
            }
        },
        actions = {
            MainButton("Same again", onClick = {
                store.startSession(clock.now(), session.segments, session.intention, session.profileId, store.state.last)
            })
            Row { Spacer(Modifier.weight(1f)); TextAction("Done", { store.finish() }); Spacer(Modifier.weight(1f)) }
        },
    )
}
