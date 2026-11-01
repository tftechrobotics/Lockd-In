package app.lockdin.ui

import androidx.activity.compose.BackHandler
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.semantics.stateDescription
import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.ExperimentalLayoutApi
import androidx.compose.foundation.layout.FlowRow
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.text.BasicTextField
import androidx.compose.material3.HorizontalDivider
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.SolidColor
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.text.SpanStyle
import androidx.compose.ui.text.buildAnnotatedString
import androidx.compose.ui.text.style.TextDecoration
import androidx.compose.ui.text.withStyle
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
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
import androidx.compose.ui.semantics.clearAndSetSemantics
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.heading
import androidx.compose.ui.semantics.liveRegion
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.input.ImeAction
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
import app.lockdin.enforce.AppRules
import app.lockdin.enforce.EnforcerStatus
import app.lockdin.enforce.FocusService
import app.lockdin.enforce.Protection
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
    // Approved idle view (R36): the lock shows the time and the next session; setup opens on demand.
    var composing by rememberSaveable { mutableStateOf(false) }
    var viewingLast by rememberSaveable { mutableStateOf(false) }
    LaunchedEffect(active?.id) { if (active != null) composing = false }
    BackHandler(enabled = active == null && composing) { composing = false }
    when {
        active != null -> SessionView(store, active, clock, zone, openDevices)
        composing -> Setup(store, clock, zone, openDevices, openSchedule, onBack = { composing = false })
        viewingLast && store.state.lastSession != null -> {
            val last = store.state.lastSession!!
            BackHandler { viewingLast = false }
            Recap(store, last, FocusService.evaluate(last, clock.now()), clock, zone, onClose = { viewingLast = false })
        }
        else -> Home(store, clock, zone, onNew = { composing = true }, openSchedule = openSchedule, onViewLast = { viewingLast = true })
    }
}

/** The idle lock: the time of day on its face, the next planned session below, one way to begin. */
@Composable
private fun Home(store: Store, clock: AppClock, zone: ZoneId, onNew: () -> Unit, openSchedule: () -> Unit, onViewLast: () -> Unit = {}) {
    val c = LocalColors.current
    val now = rememberNow(clock, true)
    val upcoming = Schedule.nextUp(store.state.schedule, now, zone)
    val at = java.time.Instant.ofEpochMilli(now).atZone(zone)
    val hm = at.format(java.time.format.DateTimeFormatter.ofPattern("h:mm"))
    val period = at.format(java.time.format.DateTimeFormatter.ofPattern("a", java.util.Locale.getDefault()))
    val weekday = at.dayOfWeek.getDisplayName(java.time.format.TextStyle.FULL, java.util.Locale.getDefault())
    val inProgress = upcoming != null && upcoming.start <= now
    WithActionBar(
        content = {
            Column(Modifier.fillMaxWidth().padding(top = Dim.s5), horizontalAlignment = Alignment.CenterHorizontally, verticalArrangement = Arrangement.spacedBy(Dim.s5)) {
                ClockLock(
                    TimerState.READY, 0, 1, locked = false, label = "It's ${Fmt.clock(now, zone)}, $weekday",
                    caption = "$period · $weekday", text = hm, width = 230.dp,
                )
                androidx.compose.material3.Surface(
                    shape = RoundedCornerShape(Dim.radius), color = c.surface, border = BorderStroke(1.dp, c.line),
                    modifier = Modifier.fillMaxWidth().clickable(role = Role.Button, onClickLabel = "Open schedule", onClick = openSchedule),
                ) {
                    Column(Modifier.padding(horizontal = Dim.s4, vertical = Dim.s3)) {
                        Text(if (inProgress) "Scheduled now" else "Next session", style = UiType.caption, color = c.inkMuted)
                        Text(upcoming?.session?.title ?: "Nothing planned", style = TimerType.task, color = c.ink)
                        Text(
                            if (upcoming == null) "Plan one on Schedule" else buildString {
                                append("${Fmt.dayLabel(upcoming.start, now, zone)} · ${Fmt.clock(upcoming.start, zone)}–${Fmt.clock(upcoming.end, zone)}")
                                if (!inProgress) append(" · in ${durationLabel(maxOf(1, ((upcoming.start - now) / 60_000).toInt()))}")
                            },
                            style = UiType.caption, color = c.inkMuted,
                        )
                        if (upcoming?.session?.autoStart == true) Text("Starts automatically", style = UiType.caption.copy(fontWeight = FontWeight.SemiBold), color = c.ink)
                    }
                }
                // The last finished session stays here until dismissed.
                store.state.lastSession?.let { last ->
                    val blockedTimes = last.blocked.values.sum()
                    Row(
                        Modifier.fillMaxWidth().clickable(role = Role.Button, onClickLabel = "View summary", onClick = onViewLast)
                            .padding(start = Dim.s4, top = Dim.s2, bottom = Dim.s2),
                        verticalAlignment = Alignment.CenterVertically,
                    ) {
                        Column(Modifier.weight(1f)) {
                            Text("Last session · ${last.intention ?: "Focus session"}", style = UiType.label, color = c.ink)
                            Text(
                                "${Fmt.dayLabel(last.start, now, zone)} ${Fmt.clock(last.start, zone)}" +
                                    (if (blockedTimes > 0) " · blocked $blockedTimes ${if (blockedTimes == 1) "time" else "times"}" else "") + " · View summary",
                                style = UiType.caption, color = c.inkMuted,
                            )
                        }
                        androidx.compose.material3.IconButton(onClick = { store.dismissLastSession() }) {
                            LockdIcon(LIcon.Close, size = 18.dp, tint = c.inkMuted, description = "Dismiss last session")
                        }
                    }
                }
            }
        },
        actions = { MainButton("New session", onNew) },
    )
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

/** "Question" heading in the composer. */
@Composable
private fun Question(text: String) =
    Text(text, style = TimerType.question, color = LocalColors.current.ink, modifier = Modifier.semantics { heading() })

@Composable
private fun Setup(store: Store, clock: AppClock, zone: ZoneId, openDevices: () -> Unit, openSchedule: () -> Unit, onBack: (() -> Unit)? = null) {
    val c = LocalColors.current
    val s = store.state
    var intention by rememberSaveable { mutableStateOf("") }
    var focus by rememberSaveable { mutableStateOf(s.last.focusMinutes) }
    var breakMin by rememberSaveable { mutableStateOf(if (s.last.rounds > 1) s.last.breakMinutes else 0) }
    var rounds by rememberSaveable { mutableStateOf(maxOf(2, s.last.rounds)) }
    var profileId by rememberSaveable { mutableStateOf(s.last.profileId) }
    var customOpen by rememberSaveable { mutableStateOf(s.last.focusMinutes !in LENGTHS) }
    var roundsOpen by rememberSaveable { mutableStateOf(false) }
    var error by remember { mutableStateOf<String?>(null) }
    val focusManager = LocalFocusManager.current
    val now = clock.now()
    val upcoming = Schedule.nextUp(s.schedule, now, zone)

    val effectiveRounds = if (breakMin > 0) rounds else 1
    val segments = Session.roundsPlan(effectiveRounds, focus, if (breakMin > 0) breakMin else 0)
    val total = segments.sumOf { it.minutes }
    val valid = Session.validatePlan(segments) == null
    val planText = if (effectiveRounds > 1) "$effectiveRounds rounds · $breakMin min break between" else "One round, no breaks"
    val profile = s.profiles.firstOrNull { it.id == profileId }
    val profileName = profile?.name ?: "Profile"
    val rules = profile?.rules ?: AppRules()
    val appMissing = rememberMissing()
    val ctx = androidx.compose.ui.platform.LocalContext.current
    // A profile that only blocks websites needs Accessibility (usage access can't see addresses).
    val missing = if (!rules.restrictsApps && rules.restrictsSites && !app.lockdin.enforce.Capabilities.accessibility(ctx))
        setOf(app.lockdin.enforce.Capability.ACCESSIBILITY) else appMissing
    val blockingReady = rules.restrictsAnything && missing.isEmpty()

    WithActionBar(
        content = {
            onBack?.let { back ->
                Row(Modifier.fillMaxWidth().padding(top = Dim.s1), verticalAlignment = Alignment.CenterVertically) {
                    TextButton(onClick = back, modifier = Modifier.heightIn(min = Dim.control)) {
                        LockdIcon(LIcon.Back, size = 18.dp, tint = c.ink)
                        Spacer(Modifier.width(Dim.s1))
                        Text("Back", style = UiType.label, color = c.ink)
                    }
                }
            }
            Column(verticalArrangement = Arrangement.spacedBy(Dim.s1)) {
                Question("What are you working on?")
                TaskField(intention, { intention = it.take(120) }, onDone = { focusManager.clearFocus() })
            }

            Column(verticalArrangement = Arrangement.spacedBy(Dim.s3)) {
                Question("How long?")
                LengthTiles(if (focus in LENGTHS) focus else -1) { m ->
                    if (m == -1) customOpen = true else { customOpen = false; focus = m; error = null }
                }
                if (customOpen || focus !in LENGTHS) CustomLengthRow(focus, { focus = it; error = null })
                RoundsLine(planText, roundsOpen) { roundsOpen = !roundsOpen }
                if (roundsOpen) {
                    SettingsGroup {
                        SettingMenuRow(
                            "Focus rounds", (1..6).map { it to if (it == 1) "1 round" else "$it rounds" }, effectiveRounds,
                            { rounds = it; breakMin = if (it > 1) (if (breakMin == 0) 10 else breakMin) else 0 }, icon = LIcon.Repeat,
                        )
                        GroupDivider()
                        SettingMenuRow(
                            "Breaks", listOf(0 to "None") + listOf(5, 10, 15, 20).map { it to "$it min" }, if (effectiveRounds > 1) breakMin else 0,
                            { breakMin = it; if (it > 0) rounds = maxOf(2, rounds); error = null }, icon = LIcon.Coffee,
                        )
                    }
                }
            }

            Column(verticalArrangement = Arrangement.spacedBy(Dim.s3)) {
                Question("Which profile?")
                ProfileChips(s.profiles.map { it.id to it.name }, profileId) { profileId = it }
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
            when {
                !rules.restrictsAnything -> ProtectionLine("Timer only. Apps stay available on this phone.", false, "Set up blocking", openDevices)
                missing.isNotEmpty() -> ProtectionLine("Blocking needs ${FocusService.describe(missing)}. Until then, it’s a timer only.", false, "Set up blocking", openDevices)
                else -> ProtectionLine("${profileName}: ${rulesSummary(rules)}", true, null) {}
            }
        },
        actions = {
            if (valid) {
                Text(
                    buildAnnotatedString {
                        withStyle(SpanStyle(fontWeight = FontWeight.SemiBold, color = c.ink)) { append("Ends at ${Fmt.clock(now + total * 60_000L, zone)}") }
                        append(" · ${durationLabel(total)}${if (effectiveRounds > 1) " with breaks" else ""}")
                    },
                    style = UiType.caption.copy(fontFeatureSettings = "tnum"), color = c.inkMuted,
                )
            }
            MainButton(if (blockingReady) "Start focus" else "Start timer", onClick = {
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

private val LENGTHS = listOf(25, 40, 60)

/** The task as a large underlined field: the setup's first, most personal answer. */
@Composable
private fun TaskField(value: String, onChange: (String) -> Unit, onDone: () -> Unit) {
    val c = LocalColors.current
    BasicTextField(
        value = value, onValueChange = onChange, singleLine = true,
        textStyle = TimerType.bigField.copy(color = c.ink),
        cursorBrush = SolidColor(c.ink),
        keyboardOptions = KeyboardOptions(imeAction = ImeAction.Done),
        keyboardActions = KeyboardActions(onDone = { onDone() }),
        modifier = Modifier.fillMaxWidth().semantics { contentDescription = "What are you working on?" },
        decorationBox = { inner ->
            Column {
                Box(Modifier.fillMaxWidth().heightIn(min = Dim.control).padding(vertical = Dim.s2), contentAlignment = Alignment.CenterStart) {
                    if (value.isEmpty()) Text("Name it, or leave it blank", style = TimerType.bigField, color = c.inkMuted, maxLines = 1)
                    inner()
                }
                HorizontalDivider(color = c.controlLine, thickness = 1.5.dp)
            }
        },
    )
}

/** 25 / 40 / 60 / Custom as tiles (radio group); 2 × 2 at large text so nothing squeezes. */
@Composable
private fun LengthTiles(selected: Int, onSelect: (Int) -> Unit) {
    val c = LocalColors.current
    val large = LocalDensity.current.fontScale > 1.3f
    Column(Modifier.selectableGroup(), verticalArrangement = Arrangement.spacedBy(10.dp)) {
        (LENGTHS + (-1)).chunked(if (large) 2 else 4).forEach { row ->
            Row(horizontalArrangement = Arrangement.spacedBy(10.dp)) {
                row.forEach { m ->
                    val on = m == selected
                    Surface(
                        shape = RoundedCornerShape(14.dp), color = if (on) c.ink else Color.Transparent, contentColor = if (on) c.onInk else c.ink,
                        border = if (on) null else BorderStroke(1.dp, c.line),
                        modifier = Modifier.weight(1f).heightIn(min = 68.dp)
                            .selectable(on, role = Role.RadioButton, onClick = { onSelect(m) })
                            .semantics(mergeDescendants = true) {},
                    ) {
                        Column(
                            Modifier.padding(horizontal = 12.dp, vertical = 10.dp)
                                .clearAndSetSemantics { contentDescription = if (m > 0) "$m minutes" else "Custom minutes" },
                            verticalArrangement = Arrangement.Bottom,
                        ) {
                            if (m > 0) {
                                Text("$m", style = TimerType.numerals.copy(letterSpacing = 0.sp, fontSize = 26.sp, lineHeight = 28.sp))
                                Text("min", style = UiType.caption)
                            } else {
                                Text("Custom", style = UiType.label)
                            }
                        }
                    }
                }
            }
        }
    }
}

/** "2 rounds · 10 min break between  Change" — wraps instead of squeezing at large text. */
@OptIn(ExperimentalLayoutApi::class)
@Composable
private fun RoundsLine(text: String, open: Boolean, onToggle: () -> Unit) {
    val c = LocalColors.current
    FlowRow {
        Text(text, style = UiType.caption.copy(fontSize = 14.sp, lineHeight = 20.sp), color = c.inkMuted, modifier = Modifier.align(Alignment.CenterVertically).padding(end = Dim.s3))
        Box(
            Modifier.heightIn(min = Dim.control).clickable(role = Role.Button, onClick = onToggle)
                .clearAndSetSemantics { contentDescription = if (open) "Done with rounds and breaks" else "Change rounds and breaks" },
            contentAlignment = Alignment.Center,
        ) {
            Text(if (open) "Done" else "Change", style = UiType.label.copy(fontWeight = FontWeight.SemiBold, textDecoration = TextDecoration.Underline), color = c.ink)
        }
    }
}

@OptIn(ExperimentalLayoutApi::class)
@Composable
private fun ProfileChips(profiles: List<Pair<String, String>>, selected: String, onSelect: (String) -> Unit) {
    val c = LocalColors.current
    FlowRow(Modifier.selectableGroup(), horizontalArrangement = Arrangement.spacedBy(Dim.s2), verticalArrangement = Arrangement.spacedBy(Dim.s2)) {
        profiles.forEach { (id, name) ->
            val on = id == selected
            Surface(
                shape = CircleShape, color = if (on) c.ink else Color.Transparent, contentColor = if (on) c.onInk else c.ink,
                border = if (on) null else BorderStroke(1.dp, c.line),
                modifier = Modifier.heightIn(min = Dim.control).selectable(on, role = Role.RadioButton, onClick = { onSelect(id) }),
            ) {
                Box(contentAlignment = Alignment.Center, modifier = Modifier.padding(horizontal = 18.dp, vertical = 10.dp)) {
                    Text(name, style = UiType.label)
                }
            }
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
        focusedSuffixColor = c.inkMuted, unfocusedSuffixColor = c.inkMuted,
    )
}

@Composable
private fun SessionView(store: Store, session: ActiveSession, clock: AppClock, zone: ZoneId, openDevices: () -> Unit) {
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
    val c = LocalColors.current
    val remaining = phase.end - now
    val lifted = snap.overrideState is OverrideState.ActiveForFocusPeriod
    val focusCount = session.segments.count { it.kind == PhaseKind.FOCUS }
    val focusIndex = session.segments.take(phase.index + 1).count { it.kind == PhaseKind.FOCUS }
    val next = occ.phases.getOrNull(phase.index + 1)
    val tone = store.state.tone
    val task = session.intention
    val report by liveReport()
    // Closed lock and "blocking" wording only from a fresh, verified report by the service.
    val protected = snap.restricting && EnforcerStatus.protecting(session.id, report)
    val permissionNeeded = report?.sessionId == session.id && report?.protection == Protection.PERMISSION_NEEDED
    val round = if (focusCount > 1) " · round $focusIndex of $focusCount" else ""
    val isBreak = phase.kind == PhaseKind.BREAK
    val state = if (isBreak) TimerState.BREAK else TimerState.FOCUS

    val status = when {
        isBreak -> "On a break$round"
        lifted -> "Unlocked$round"
        else -> "Focusing$round"
    }
    val minutesLeft = ((remaining + 59_999) / 60_000).coerceAtLeast(0)
    val label = "${if (isBreak) "Break" else "Focus"}: $minutesLeft minute${if (minutesLeft == 1L) "" else "s"} left"
    val nudge = when {
        isBreak && remaining <= 60_000 -> message(tone, Moment.BREAK_ENDING, task)
        isBreak -> message(tone, Moment.BREAK_START, time = Fmt.clock(phase.end, zone))
        lifted -> "Unlocked until ${Fmt.clock((snap.overrideState as OverrideState.ActiveForFocusPeriod).until, zone)}. Later rounds start as planned."
        else -> message(tone, Moment.FOCUS_START, task)
    }
    val facts = buildList {
        when {
            isBreak -> add("Focus resumes" to Fmt.clock(phase.end, zone))
            next != null -> add("Next break" to Fmt.clock(phase.end, zone))
            else -> add("Ends at" to Fmt.clock(phase.end, zone))
        }
        if (next != null) add("Session ends" to Fmt.clock(occ.end, zone))
        store.state.profiles.firstOrNull { it.id == session.profileId }?.let { add("Profile" to it.name) }
    }
    val blocks = session.rules.restrictsAnything
    val protection = when {
        protected -> "Blocking on this phone · ${rulesSummary(session.rules).replace(" during focus", "")}"
        !blocks -> "Timer only. Apps stay available on this phone."
        isBreak -> "Apps are available until focus resumes"
        lifted -> "Apps are available this round"
        permissionNeeded -> "Blocking paused: turn on ${FocusService.describe(report?.missing ?: emptySet())}"
        else -> "Blocking isn’t running on this phone right now"
    }

    val note: @Composable () -> Unit = {
        ProtectionLine(protection, protected, if (protected || (blocks && (isBreak || lifted))) null else "Set up blocking", openDevices)
    }
    // Phone (approved R36): the task, the lock and what's next; everything else is one tap away.
    var more by rememberSaveable { mutableStateOf(false) }
    val pending = snap.overrideState is OverrideState.Pending
    Column(
        Modifier.fillMaxSize().verticalScroll(rememberScrollState()).padding(horizontal = Dim.gutter).padding(top = Dim.s4, bottom = Dim.s6),
        horizontalAlignment = Alignment.CenterHorizontally,
    ) {
        Text(task ?: if (isBreak) "Break" else "Focus session", style = TimerType.task, color = c.ink, textAlign = TextAlign.Center,
            modifier = Modifier.semantics { heading() })
        Spacer(Modifier.height(Dim.s1))
        Text(status, style = TimerType.status, color = c.inkMuted, modifier = Modifier.semantics { liveRegion = LiveRegionMode.Polite })
        if (protected) {
            Spacer(Modifier.height(Dim.s2))
            BlockingBadge()
        }
        Spacer(Modifier.height(Dim.s4))
        ClockLock(
            state, remaining, phase.end - phase.start, locked = protected, label = label,
            caption = if (isBreak) "left in break" else "left this round", width = 220.dp,
        )
        Spacer(Modifier.height(Dim.s4))
        facts.firstOrNull()?.let { (k, v) ->
            Text(
                buildAnnotatedString {
                    append("$k ")
                    withStyle(SpanStyle(fontWeight = FontWeight.SemiBold, color = c.ink)) { append(v) }
                },
                style = UiType.body.copy(fontFeatureSettings = "tnum"), color = c.inkMuted,
            )
        }
        Spacer(Modifier.height(Dim.s3))
        MoreToggle("Details and options", more || pending) { more = !more }
        if (more || pending) {
            Column(Modifier.fillMaxWidth().padding(top = Dim.s3), verticalArrangement = Arrangement.spacedBy(Dim.s4)) {
                SessionRail(occ.phases, now, zone)
                FactList(facts)
                nudge?.let { Text(it, style = UiType.body, color = c.inkMuted) }
                note()
                OutPanel(store, session, snap, now, clock)
            }
        }
    }
}

/** "Details and options" / "Summary and options": a quiet disclosure under the lock. */
@Composable
private fun MoreToggle(text: String, open: Boolean, onToggle: () -> Unit) {
    val c = LocalColors.current
    Row(
        Modifier.heightIn(min = Dim.control).clickable(role = Role.Button, onClick = onToggle)
            .semantics { stateDescription = if (open) "Expanded" else "Collapsed" }.padding(horizontal = Dim.s3),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        Text(text, style = UiType.label.copy(fontWeight = FontWeight.SemiBold), color = c.ink)
        Spacer(Modifier.width(Dim.s1))
        LockdIcon(LIcon.ChevronDown, size = 16.dp, tint = c.inkMuted, modifier = Modifier.graphicsLayer { rotationZ = if (open) 180f else 0f })
    }
}

/** Shown only while restriction is verified and applied on this phone. */
@Composable
private fun BlockingBadge() {
    val c = LocalColors.current
    androidx.compose.material3.Surface(shape = RoundedCornerShape(50), color = c.taupeSoft, border = BorderStroke(1.dp, c.line)) {
        Row(Modifier.padding(horizontal = Dim.s3, vertical = 6.dp).semantics(mergeDescendants = true) {}, verticalAlignment = Alignment.CenterVertically) {
            MiniLock(closed = true, tint = c.ink, size = 14.dp)
            Spacer(Modifier.width(6.dp))
            Text("Blocking active · this phone", style = UiType.caption.copy(fontWeight = FontWeight.SemiBold), color = c.ink)
        }
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
    // The session's commitment, fixed when it started: null = locked (emergency recovery only).
    val unlockDelay = session.commitment.overrideDelaySeconds

    Column(Modifier.fillMaxWidth(), verticalArrangement = Arrangement.spacedBy(Dim.s1)) {
        when {
            pending != null -> {
                Text(
                    "Unlocking in ${Fmt.countdown(pending.effectiveAt - now)}" +
                        if (pending.scope == OverrideScope.CURRENT_FOCUS_PERIOD) " · this round" else " · ends the session",
                    style = UiType.label, color = c.ink, modifier = Modifier.semantics { liveRegion = LiveRegionMode.Polite },
                )
                Row(verticalAlignment = Alignment.CenterVertically) {
                    SecondaryButton("Cancel unlock", onClick = {
                        val t = clock.now()
                        store.setControls(session.id) { ctl ->
                            ctl.copy(overrides = ctl.overrides.map { o ->
                                if (o.cancelledAt == null && o.requestedAt + o.delaySeconds * 1000L > t) o.copy(cancelledAt = t) else o
                            })
                        }
                    })
                    Spacer(Modifier.weight(1f))
                    TextAction("End now", { confirmEmergency = true }, danger = true)
                }
            }
            open -> {
                Text(if (unlockDelay == 0) "Unlocks right away" else "Unlock after a ${unlockDelay ?: 0}-second wait", style = UiType.caption, color = c.inkMuted)
                Column(Modifier.fillMaxWidth().selectableGroup()) {
                    if (inFocus) ScopeOption("This round", scope == OverrideScope.CURRENT_FOCUS_PERIOD) { scope = OverrideScope.CURRENT_FOCUS_PERIOD }
                    ScopeOption("Rest of this session", !inFocus || scope == OverrideScope.REST_OF_SESSION) { scope = OverrideScope.REST_OF_SESSION }
                }
                MainButton("Start unlock", onClick = {
                    val phase = snap.phase ?: return@MainButton
                    val t = clock.now()
                    store.setControls(session.id) { ctl ->
                        ctl.copy(overrides = ctl.overrides + OverrideRecord(Store.newId(), if (inFocus) scope else OverrideScope.REST_OF_SESSION, phase.index, t, unlockDelay ?: Session.DEFAULT_OVERRIDE_DELAY_SECONDS))
                    }
                    open = false
                })
                Row(verticalAlignment = Alignment.CenterVertically) {
                    TextAction("Cancel", { open = false })
                    Spacer(Modifier.weight(1f))
                    TextAction("End now", { confirmEmergency = true }, danger = true)
                }
            }
            // Locked: no unlock; emergency recovery stays one deliberate step away.
            unlockDelay == null -> {
                Text("Locked session: it ends early only with emergency recovery.", style = UiType.caption, color = c.inkMuted)
                SecondaryButton("Emergency recovery", { confirmEmergency = true }, modifier = Modifier.fillMaxWidth())
            }
            // Default: one quiet action. End now lives inside, so it never competes with the timer.
            else -> SecondaryButton("Need out?", { open = true }, modifier = Modifier.fillMaxWidth())
        }
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
private fun Recap(
    store: Store, session: ActiveSession, snap: Snapshot, clock: AppClock, zone: ZoneId,
    /** null = the live recap (auto-returns home); otherwise a saved one viewed from Home. */
    onClose: (() -> Unit)? = null,
) {
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
    val facts = buildList {
        add("Focused" to "$focusedMin min")
        add("Time" to "${Fmt.clock(session.start, zone)}–${Fmt.clock(endedAt, zone)}")
        store.state.profiles.firstOrNull { it.id == session.profileId }?.let { add("Profile" to it.name) }
    }
    val focusPhases = occ.phases.filter { it.kind == PhaseKind.FOCUS }
    val roundsDone = focusPhases.count { it.end <= endedAt }
    val ctx = androidx.compose.ui.platform.LocalContext.current
    // Blocked attempts, most frequent first, with readable names.
    val blocked = remember(session.blocked) {
        session.blocked.entries.sortedByDescending { it.value }.map { (k, n) ->
            (if (k.startsWith("site:")) k.removePrefix("site:") else app.lockdin.enforce.InstalledApps.label(ctx, k)) to n
        }
    }
    val totalBlocked = blocked.sumOf { it.second }
    // Back to the idle lock 30 seconds after the recap appears, unless the person moves on first.
    val shownAt = remember(session.id) { clock.now() }
    val now = rememberNow(clock, true)
    val left = ((AUTO_HOME_MS - (now - shownAt)) / 1000).coerceAtLeast(0)
    LaunchedEffect(left) { if (left == 0L && onClose == null) store.finish(dismissed = false) }

    WithActionBar(
        content = {
            Column(Modifier.fillMaxWidth().padding(top = Dim.s3), horizontalAlignment = Alignment.CenterHorizontally) {
                Text(session.intention ?: "Focus session", style = TimerType.task, color = c.ink, textAlign = TextAlign.Center,
                    modifier = Modifier.semantics { heading() })
                Spacer(Modifier.height(Dim.s1))
                Text("Session complete", style = TimerType.status, color = c.inkMuted)
                Spacer(Modifier.height(Dim.s4))
                ClockLock(TimerState.DONE, 0, 1, locked = false, label = "Session complete", caption = "complete", width = 150.dp)
                Spacer(Modifier.height(Dim.s3))
                Text(
                    buildAnnotatedString {
                        append("Focused ")
                        withStyle(SpanStyle(fontWeight = FontWeight.SemiBold, color = c.ink)) { append("$focusedMin min") }
                    },
                    style = UiType.body, color = c.inkMuted,
                )
            }
            Column(verticalArrangement = Arrangement.spacedBy(Dim.s2)) {
                SectionLabel("Blocked during this session")
                when {
                    !session.rules.restrictsAnything -> Text("Timer only: nothing was blocked on this phone.", style = UiType.body, color = c.inkMuted)
                    blocked.isEmpty() -> Text("You didn’t open any blocked apps or sites.", style = UiType.body, color = c.inkMuted)
                    else -> {
                        FactList(blocked.take(8).map { (name, n) -> name to "$n ${if (n == 1) "time" else "times"}" })
                        if (blocked.size > 8) Text("and ${blocked.size - 8} more", style = UiType.caption, color = c.inkMuted)
                        Text("The block screen came up $totalBlocked ${if (totalBlocked == 1) "time" else "times"} in all.",
                            style = UiType.caption, color = c.inkMuted)
                    }
                }
            }
            Column(verticalArrangement = Arrangement.spacedBy(Dim.s2)) {
                SectionLabel("Activity")
                SessionRail(occ.phases, endedAt, zone)
                FactList(buildList {
                    add("Time" to "${Fmt.clock(session.start, zone)}–${Fmt.clock(endedAt, zone)}")
                    add("Focus rounds" to "$roundsDone of ${focusPhases.size}")
                    store.state.profiles.firstOrNull { it.id == session.profileId }?.let { add("Profile" to it.name) }
                })
                Text(how, style = UiType.label.copy(fontWeight = FontWeight.SemiBold), color = c.ink)
                if (snap.status == SessionStatus.COMPLETED) message(store.state.tone, Moment.DONE)?.let { Text(it, style = UiType.body, color = c.inkMuted) }
            }
        },
        actions = {
            if (onClose == null) Text("Back to home in $left s", style = UiType.caption, color = c.inkMuted)
            MainButton("Done", { if (onClose == null) store.finish() else { store.dismissLastSession(); onClose() } })
            if (onClose != null) SecondaryButton("Back", onClose, modifier = Modifier.fillMaxWidth())
            else SecondaryButton("Same again", {
                store.startSession(clock.now(), session.segments, session.intention, session.profileId, store.state.last)
            }, modifier = Modifier.fillMaxWidth())
        },
    )
}

private const val AUTO_HOME_MS = 30_000L
