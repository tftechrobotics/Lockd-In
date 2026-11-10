package app.lockdin.ui

import kotlinx.coroutines.launch

import androidx.compose.runtime.rememberCoroutineScope

import androidx.compose.animation.togetherWith

import androidx.activity.compose.BackHandler
import androidx.compose.foundation.selection.toggleable
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.semantics.stateDescription
import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.ExperimentalLayoutApi
import androidx.compose.foundation.layout.FlowRow
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.border
import androidx.compose.foundation.layout.size
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
import androidx.compose.foundation.background
import androidx.compose.ui.platform.testTag
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
import app.lockdin.state.Profile
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

/** Setup left open to add apps to a profile: reopened with the same choices when the editor closes. */
private object SetupReturn {
    var pending: Triple<String, String, Int>? = null // profile id, task, focus minutes
}

/** Set by History's "Try an unlocked session" (R62); the next setup opens as an unlocked session. */
object UnlockedTry {
    var pending by androidx.compose.runtime.mutableStateOf(false)
}

@Composable
fun FocusScreen(store: Store, clock: AppClock, zone: ZoneId, openDevices: () -> Unit, openSchedule: () -> Unit, openProfile: (String) -> Unit = {}) {
    val active = store.state.active
    // Approved idle view (R36): the lock shows the time and the next session; setup opens on demand.
    var composing by rememberSaveable { mutableStateOf(SetupReturn.pending != null || UnlockedTry.pending) }
    var viewingLast by rememberSaveable { mutableStateOf(false) }
    LaunchedEffect(active?.id) { if (active != null) composing = false }
    LaunchedEffect(UnlockedTry.pending) { if (UnlockedTry.pending && active == null) composing = true }
    BackHandler(enabled = active == null && composing) { composing = false }
    when {
        active != null -> SessionView(store, active, clock, zone, openDevices)
        composing -> Setup(store, clock, zone, openDevices, openSchedule, openProfile, onBack = { composing = false })
        viewingLast && store.state.lastSession != null -> {
            val last = store.state.lastSession!!
            BackHandler { viewingLast = false }
            Recap(store, last, FocusService.evaluate(last, clock.now()), clock, zone, onClose = { viewingLast = false })
        }
        else -> Home(store, clock, zone, onNew = { composing = true }, openSchedule = openSchedule, onViewLast = { viewingLast = true }, openDevices = openDevices)
    }
}

/** The idle lock: the time of day on its face, the next planned session below, one way to begin. */
@OptIn(ExperimentalLayoutApi::class)
@Composable
private fun Home(store: Store, clock: AppClock, zone: ZoneId, onNew: () -> Unit, openSchedule: () -> Unit, onViewLast: () -> Unit = {}, openDevices: () -> Unit = {}) {
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
            // Owner 2026-11-07: lock and words a bit lower on the screen.
            val still = reducedMotion()
            val haptics = androidx.compose.ui.platform.LocalHapticFeedback.current
            val rattle = remember { androidx.compose.animation.core.Animatable(0f) }
            val scope = rememberCoroutineScope()
            var shown by remember { mutableStateOf(still) }
            LaunchedEffect(Unit) { shown = true }
            val shortScreen = LocalShortScreen.current
            Column(Modifier.fillMaxWidth().padding(top = if (shortScreen) Dim.s4 else 56.dp), horizontalAlignment = Alignment.CenterHorizontally, verticalArrangement = Arrangement.spacedBy(if (shortScreen) Dim.s4 else Dim.s6)) {
                // R55: tap the lock and it rattles on its shackle (a small haptic tick too).
                ClockLock(
                    TimerState.READY, 0, 1, locked = false, label = "It's ${Fmt.clock(now, zone)}, $weekday",
                    caption = "$period · $weekday", text = hm, width = if (shortScreen) 150.dp else 210.dp,
                    modifier = Modifier
                        .graphicsLayer { rotationZ = rattle.value; transformOrigin = androidx.compose.ui.graphics.TransformOrigin(0.5f, 0.05f) }
                        .clickable(interactionSource = remember { androidx.compose.foundation.interaction.MutableInteractionSource() }, indication = null, onClickLabel = "Rattle the lock") {
                            haptics.performHapticFeedback(androidx.compose.ui.hapticfeedback.HapticFeedbackType.TextHandleMove)
                            if (!still) scope.launch {
                                rattle.snapTo(0f)
                                for ((deg, ms) in listOf(-6f to 70, 5f to 80, -3.5f to 80, 2f to 90, 0f to 110)) rattle.animateTo(deg, androidx.compose.animation.core.tween(ms))
                            }
                        },
                )
                // Owner 2026-11-07 (R54): a greeting under the lock that says what's next, like Freedom's
                // header, instead of a "Next session" box. Tapping it opens Schedule.
                val hour = at.hour
                val hello = when (hour) { in 5..11 -> "Good morning"; in 12..16 -> "Good afternoon"; in 17..21 -> "Good evening"; else -> "Good night" }
                val first = store.state.account?.name?.trim()?.split(" ")?.firstOrNull()?.takeIf { it.isNotBlank() }
                val line = when {
                    upcoming == null -> "Nothing planned ${if (hour >= 17 || hour < 5) "tonight" else "today"}."
                    inProgress -> "${upcoming.session.title} is on now, until ${Fmt.clock(upcoming.end, zone)}."
                    else -> {
                        val mins = maxOf(1, ((upcoming.start - now) / 60_000).toInt())
                        val whenText = if (mins <= 180) "in ${durationLabel(mins)}" else "${Fmt.dayLabel(upcoming.start, now, zone).replaceFirstChar { it.lowercase() }} at ${Fmt.clock(upcoming.start, zone)}"
                        "${upcoming.session.title} $whenText."
                    }
                }
                // R55: the greeting rises in when Home opens.
                androidx.compose.animation.AnimatedVisibility(
                    visible = shown,
                    enter = if (still) androidx.compose.animation.EnterTransition.None else
                        androidx.compose.animation.fadeIn(androidx.compose.animation.core.tween(420, delayMillis = 120)) +
                            androidx.compose.animation.slideInVertically(androidx.compose.animation.core.tween(480, delayMillis = 120, easing = androidx.compose.animation.core.FastOutSlowInEasing)) { it / 3 },
                ) {
                Column(
                    Modifier.fillMaxWidth().tourTarget("next").clickable(role = Role.Button, onClickLabel = "Open schedule", onClick = openSchedule)
                        .padding(vertical = Dim.s1),
                    verticalArrangement = Arrangement.spacedBy(6.dp),
                ) {
                    Text(if (first != null) "$hello, $first" else hello, style = Type.greeting, color = c.ink, modifier = Modifier.semantics { heading() })
                    Text(line, style = UiType.body.copy(fontSize = 17.sp, lineHeight = 24.sp), color = c.inkMuted)
                    if (upcoming?.session?.autoStart == true && !inProgress) Text("Starts automatically.", style = UiType.body.copy(fontSize = 17.sp, lineHeight = 24.sp), color = c.inkMuted)
                    if (upcoming == null) Text("Plan one", style = UiType.label.copy(fontSize = 16.sp, fontWeight = FontWeight.SemiBold, textDecoration = TextDecoration.Underline), color = c.ink)
                }
                }
                // Polish pass (owner 2026-11-09): onboarding now ends here, so if blocking still needs a
                // permission Home says exactly what, with a direct way to finish.
                val homeMissing = rememberMissing()
                val homeRules = store.state.profiles.firstOrNull { it.id == store.state.last.profileId }?.rules
                if (homeMissing.isNotEmpty() && homeRules?.restrictsAnything == true) {
                    Row(Modifier.fillMaxWidth(), verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(Dim.s2)) {
                        Box(Modifier.size(9.dp).border(1.5.dp, c.inkMuted, CircleShape))
                        Text("Blocking needs ${FocusService.describe(homeMissing)}. Until then, sessions are timer only.",
                            style = UiType.caption, color = c.inkMuted, modifier = Modifier.weight(1f))
                        TextAction("Finish setup", openDevices)
                    }
                }
                // Presets: one tap starts the session (a manual start, so the start sequence plays).
                val homeCtx = androidx.compose.ui.platform.LocalContext.current
                if (store.state.presets.isNotEmpty()) {
                    Column(Modifier.fillMaxWidth(), verticalArrangement = Arrangement.spacedBy(Dim.s2)) {
                        Text("Start a preset", style = UiType.section, color = c.inkMuted, modifier = Modifier.semantics { heading() })
                        FlowRow(horizontalArrangement = Arrangement.spacedBy(Dim.s2), verticalArrangement = Arrangement.spacedBy(Dim.s2)) {
                            store.state.presets.take(6).forEach { p ->
                                val due = p.due?.let { " · due ${Fmt.dueLabel(it, now, zone)}" } ?: ""
                                Surface(
                                    shape = RoundedCornerShape(Dim.radius), color = Color.Transparent, contentColor = c.ink,
                                    modifier = Modifier.heightIn(min = Dim.control).clickable(role = Role.Button, onClickLabel = "Start ${p.name}") {
                                        startPreset(store, p, clock.now(), homeCtx)
                                    },
                                ) {
                                    Box(contentAlignment = Alignment.Center, modifier = Modifier.padding(vertical = 10.dp)) {
                                        Text("${p.name} · ${durationLabel(p.focusMinutes * p.rounds)}$due", style = UiType.label.copy(fontWeight = FontWeight.SemiBold, textDecoration = TextDecoration.Underline))
                                    }
                                }
                            }
                        }
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
        actions = {
            val firstTime = store.state.recentStarts.isEmpty() && store.state.lastSession == null
            GlassButton(if (firstTime) "Start your first session" else "Start a session", onNew, modifier = Modifier.tourTarget("new").testTag("start-session"))
        },
    )
}

/** Starts a preset now as a manual start (the start sequence plays; the playlist opens). */
fun startPreset(store: Store, p: app.lockdin.state.Preset, now: Long, ctx: android.content.Context? = null) {
    val profile = if (store.state.profiles.any { it.id == p.profileId }) p.profileId else store.state.last.profileId
    store.startSession(
        now, Session.roundsPlan(p.rounds, p.focusMinutes, if (p.rounds > 1) p.breakMinutes else 0), p.task, profile,
        LastChoice(p.focusMinutes, if (p.breakMinutes > 0) p.breakMinutes else store.state.last.breakMinutes, p.rounds, profile),
        presetId = p.id,
    )
    store.state.active?.let { StartIntro.request(it.id) }
}

/** Scrolling content with the action area pinned below it (above the bottom navigation). */
@Composable
fun WithActionBar(content: @Composable () -> Unit, actions: @Composable () -> Unit, note: (@Composable () -> Unit)? = null) {
    // With large text, or on a short screen (under 680 dp of room, e.g. 360 × 640 dp phones), the pinned
    // bar would eat the screen, so the status note moves into the scrolling content and only the
    // actions stay pinned.
    androidx.compose.foundation.layout.BoxWithConstraints(Modifier.fillMaxSize()) {
    val large = androidx.compose.ui.platform.LocalDensity.current.fontScale > 1.3f || maxHeight < 680.dp
    androidx.compose.runtime.CompositionLocalProvider(LocalShortScreen provides (maxHeight < 680.dp)) {
    Column(Modifier.fillMaxSize()) {
        Column(
            Modifier.weight(1f).fillMaxWidth().screenScroll().padding(top = LocalTopInset.current)
                .padding(horizontal = Dim.gutter).padding(top = Dim.s2, bottom = Dim.s5),
            verticalArrangement = Arrangement.spacedBy(Dim.s5),
        ) { content(); if (large) note?.invoke() }
        ActionBar { if (!large) note?.invoke(); actions() }
    }
    }
    }
}

/** True on short screens (under 680 dp of room), where the Home lock shrinks so the greeting fits. */
val LocalShortScreen = androidx.compose.runtime.staticCompositionLocalOf { false }

/**
 * The lock's size on the session screen: as large as before on tall phones, smaller on short ones,
 * so the session fits one screen without scrolling (owner 2026-11-02). Large text may still scroll.
 */
@Composable
private fun sessionLockWidth(): androidx.compose.ui.unit.Dp {
    val h = androidx.compose.ui.platform.LocalConfiguration.current.screenHeightDp
    return (h * 0.26f).coerceIn(150f, 220f).dp
}

/** One line about this session's audio, with the way to choose music first (R48). */
/** Short audio label for the Session options summary. */
private fun audioShort(a: app.lockdin.state.ProfileAudio): String = if (a.builtIn) a.collection.label.lowercase().replaceFirstChar { it.uppercase() } else "no study audio"

/** One expandable row for the less-used setup choices; collapsed it shows what is actually chosen. */
@Composable
private fun SessionOptions(summary: String, open: Boolean, onToggle: () -> Unit, content: @Composable () -> Unit) {
    val c = LocalColors.current
    val turn by androidx.compose.animation.core.animateFloatAsState(if (open) 90f else 0f, label = "chev")
    Column(Modifier.fillMaxWidth()) {
        HorizontalDivider(color = c.line)
        Row(
            Modifier.fillMaxWidth().heightIn(min = 64.dp).clickable(role = Role.Button, onClickLabel = if (open) "Hide options" else "Show options", onClick = onToggle)
                .semantics(mergeDescendants = true) { stateDescription = if (open) "Expanded" else "Collapsed" }
                .padding(vertical = Dim.s3),
            verticalAlignment = Alignment.CenterVertically,
        ) {
            Column(Modifier.weight(1f), verticalArrangement = Arrangement.spacedBy(2.dp)) {
                Text("Session options", style = UiType.label.copy(fontWeight = FontWeight.SemiBold), color = c.ink)
                if (!open) Text(summary.replaceFirstChar { it.uppercase() }, style = UiType.caption, color = c.inkMuted)
            }
            LockdIcon(LIcon.Chevron, size = 18.dp, tint = c.inkMuted, modifier = Modifier.graphicsLayer { rotationZ = turn })
        }
        androidx.compose.animation.AnimatedVisibility(open) { Column(Modifier.padding(bottom = Dim.s3)) { content() } }
        HorizontalDivider(color = c.line)
    }
}

@Composable
private fun AudioSummary(a: app.lockdin.state.ProfileAudio, onChoose: () -> Unit) {
    val c = LocalColors.current
    val parts = buildList {
        add(if (a.builtIn) "${a.collection.label}${if (a.playOnStart) ", plays when focus starts" else ""}" else "No study audio")
        when (a.access) {
            app.lockdin.audio.MusicAccess.LIMITED -> add("music apps ${a.allowanceMin} min")
            app.lockdin.audio.MusicAccess.ALWAYS -> add("music apps allowed")
            app.lockdin.audio.MusicAccess.NORMAL -> Unit
        }
    }
    Row(Modifier.fillMaxWidth(), verticalAlignment = Alignment.CenterVertically) {
        Text("Audio: " + parts.joinToString(" · "), style = UiType.caption, color = c.inkMuted, modifier = Modifier.weight(1f))
        TextAction("Choose music", onChoose)
    }
}

/**
 * Choose music before starting: the built-in collection (with a preview) and, for limited
 * access, this session's allowance. The music apps can be opened from here too, before the
 * countdown starts, so picking a playlist there costs nothing.
 */
@Composable
private fun ChooseMusicDialog(initial: app.lockdin.state.ProfileAudio, onDismiss: () -> Unit, onChange: (app.lockdin.state.ProfileAudio) -> Unit) {
    val c = LocalColors.current
    val ctx = androidx.compose.ui.platform.LocalContext.current
    var a by remember { mutableStateOf(initial) }
    fun set(n: app.lockdin.state.ProfileAudio) { a = n; onChange(n) }
    androidx.compose.material3.AlertDialog(
        onDismissRequest = onDismiss,
        containerColor = c.surface,
        title = { Text("Music for this session", style = UiType.title, color = c.ink) },
        text = {
            Column(Modifier.verticalScroll(rememberScrollState()), verticalArrangement = Arrangement.spacedBy(Dim.s3)) {
                Text("Nothing starts until you start focus.", style = UiType.caption, color = c.inkMuted)
                Text("Study audio", style = UiType.label, color = c.ink)
                ProfileChips(listOf("off" to "Off"), if (a.builtIn) "" else "off") { app.lockdin.audio.StudyPlayer.stopPreview(); set(a.copy(builtIn = false)) }
                app.lockdin.audio.AudioGroup.values().forEach { g ->
                    Text(g.label, style = UiType.caption, color = c.inkMuted)
                    ProfileChips(app.lockdin.audio.AudioCollection.values().filter { it.group == g }.map { it.name to it.label }, if (a.builtIn) a.collection.name else "") { k ->
                        app.lockdin.audio.StudyPlayer.stopPreview()
                        set(a.copy(builtIn = true, collection = app.lockdin.audio.AudioCollection.valueOf(k)))
                    }
                }
                if (a.builtIn) {
                    val previewing = app.lockdin.audio.StudyPlayer.previewing == a.collection
                    TextAction(if (previewing) "Stop preview" else "Preview ${a.collection.label}", {
                        if (previewing) app.lockdin.audio.StudyPlayer.stopPreview() else app.lockdin.audio.StudyPlayer.preview(ctx, a.collection, a.volume)
                    })
                    androidx.compose.foundation.layout.Row(verticalAlignment = Alignment.CenterVertically) {
                        Text("Play when focus starts", style = UiType.body, color = c.ink, modifier = Modifier.weight(1f))
                        androidx.compose.material3.Switch(a.playOnStart, { set(a.copy(playOnStart = it)) },
                            colors = androidx.compose.material3.SwitchDefaults.colors(checkedTrackColor = c.ink, checkedThumbColor = c.onInk),
                            modifier = Modifier.semantics { contentDescription = "Play when focus starts" })
                    }
                }
                // Music apps (R48 + owner 2026-11-02): this only turns access on for the session and
                // sets the browsing time. The music itself is chosen inside the music app.
                if (initial.musicApps.isNotEmpty() && initial.access != app.lockdin.audio.MusicAccess.NORMAL) {
                    val on = a.access != app.lockdin.audio.MusicAccess.NORMAL
                    val names = initial.musicApps.sorted().joinToString(", ") { app.lockdin.enforce.InstalledApps.label(ctx, it) }
                    androidx.compose.foundation.layout.Row(verticalAlignment = Alignment.CenterVertically) {
                        Column(Modifier.weight(1f)) {
                            Text("Music apps during focus", style = UiType.body, color = c.ink)
                            Text(names, style = UiType.caption, color = c.inkMuted)
                        }
                        androidx.compose.material3.Switch(on, { set(a.copy(access = if (it) initial.access else app.lockdin.audio.MusicAccess.NORMAL)) },
                            colors = androidx.compose.material3.SwitchDefaults.colors(checkedTrackColor = c.ink, checkedThumbColor = c.onInk),
                            modifier = Modifier.semantics { contentDescription = "Music apps during focus" })
                    }
                    if (on && a.access == app.lockdin.audio.MusicAccess.LIMITED) {
                        Text("Browsing time this session", style = UiType.caption, color = c.inkMuted)
                        ProfileChips(app.lockdin.audio.MusicAllowance.CHOICES.map { it.toString() to if (it == app.lockdin.audio.MusicAllowance.DEFAULT_MIN) "$it min (suggested)" else "$it min" }, a.allowanceMin.toString()) {
                            set(a.copy(allowanceMin = it.toInt()))
                        }
                    }
                    if (on) Text("You choose what to play inside the music app.", style = UiType.caption, color = c.inkMuted)
                }
            }
        },
        confirmButton = { TextAction("Done", onDismiss) },
    )
}

/** "Question" heading in the composer. */
@Composable
private fun Question(text: String) =
    Text(text, style = TimerType.question, color = LocalColors.current.ink, modifier = Modifier.semantics { heading() })

@Composable
private fun Setup(store: Store, clock: AppClock, zone: ZoneId, openDevices: () -> Unit, openSchedule: () -> Unit, openProfile: (String) -> Unit, onBack: (() -> Unit)? = null) {
    val c = LocalColors.current
    val s = store.state
    val back = remember { SetupReturn.pending.also { SetupReturn.pending = null } }
    var unlocked by rememberSaveable { mutableStateOf(UnlockedTry.pending.also { UnlockedTry.pending = false }) }
    var intention by rememberSaveable { mutableStateOf(back?.second ?: "") }
    var focus by rememberSaveable { mutableStateOf(back?.third ?: s.last.focusMinutes) }
    var breakMin by rememberSaveable { mutableStateOf(if (s.last.rounds > 1) s.last.breakMinutes else 0) }
    var rounds by rememberSaveable { mutableStateOf(maxOf(2, s.last.rounds)) }
    var profileId by rememberSaveable { mutableStateOf(back?.first?.takeIf { id -> s.profiles.any { it.id == id } } ?: s.last.profileId.takeIf { id -> s.profiles.any { it.id == id } } ?: s.profiles.firstOrNull()?.id ?: "") }
    var namingProfile by remember { mutableStateOf(false) }
    var commitment by remember { mutableStateOf(s.commitment.takeIf { it != app.lockdin.enforce.Commitment.PARTNER || s.partner?.claimed == true } ?: app.lockdin.enforce.Commitment.LOCKED) }
    var pickingCommitment by remember { mutableStateOf(false) }
    var customOpen by rememberSaveable { mutableStateOf(s.last.focusMinutes !in LENGTHS) }
    var roundsOpen by rememberSaveable { mutableStateOf(false) }
    var optionsOpen by rememberSaveable { mutableStateOf(false) }
    var presetId by rememberSaveable { mutableStateOf<String?>(null) }
    var savingPreset by remember { mutableStateOf(false) }
    var error by remember { mutableStateOf<String?>(null) }
    // Audio for this session only (R48): chosen before starting, never spends the allowance.
    var audioOverride by remember { mutableStateOf<app.lockdin.state.ProfileAudio?>(null) }
    var choosingMusic by remember { mutableStateOf(false) }
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
    val sessionAudio = audioOverride ?: profile?.audio ?: app.lockdin.state.ProfileAudio()
    val rules = profile?.rules ?: AppRules()
    val appMissing = rememberMissing()
    val ctx = androidx.compose.ui.platform.LocalContext.current
    // A profile that only blocks websites needs Accessibility (usage access can't see addresses).
    val missing = if (!rules.restrictsApps && rules.restrictsSites && !app.lockdin.enforce.Capabilities.accessibility(ctx))
        setOf(app.lockdin.enforce.Capability.ACCESSIBILITY) else appMissing
    val blockingReady = rules.restrictsAnything && missing.isEmpty()

    // Polish pass: over a photo background the lower half darkens gradually, so the lamp and desk
    // in the library scene don't compete with the controls and their text.
    Box(Modifier.fillMaxSize()) {
    if (s.backdrop != "NONE") Box(Modifier.fillMaxSize().background(androidx.compose.ui.graphics.Brush.verticalGradient(
        0f to androidx.compose.ui.graphics.Color.Transparent, 0.35f to c.ground.copy(alpha = 0.15f), 0.7f to c.ground.copy(alpha = 0.72f), 1f to c.ground.copy(alpha = 0.9f),
    )))
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
            // Polish pass (owner 2026-11-09): the three things people set every time come first: the
            // task, the length and the profile. Everything else sits in one "Session options" row.
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
            }

            Column(verticalArrangement = Arrangement.spacedBy(Dim.s3)) {
                Question("Which profile?")
                ProfileChips(s.profiles.map { it.id to it.name } + ("+new" to "+ New profile"), profileId) {
                    if (it == "+new") namingProfile = true else { profileId = it; audioOverride = null }
                }
            }

            SessionOptions(
                summary = listOf(planText, audioShort(sessionAudio), "ending early: " + commitmentLabel(commitment).lowercase()).joinToString(" · "),
                open = optionsOpen, onToggle = { optionsOpen = !optionsOpen },
            ) {
                Column(verticalArrangement = Arrangement.spacedBy(Dim.s3)) {
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
                    AudioSummary(sessionAudio) { choosingMusic = true }
                    // How this session can end early (owner 2026-11-02: Locked and Partner code here too).
                    Row(Modifier.fillMaxWidth(), verticalAlignment = Alignment.CenterVertically) {
                        Text("Ending early: " + commitmentLabel(commitment).lowercase().replaceFirstChar { it.uppercase() },
                            style = UiType.caption, color = c.inkMuted, modifier = Modifier.weight(1f))
                        Box {
                            TextAction("Change", { pickingCommitment = true })
                            androidx.compose.material3.DropdownMenu(pickingCommitment, { pickingCommitment = false }, containerColor = c.surface) {
                                commitmentChoices(s).forEach { (v, l) ->
                                    androidx.compose.material3.DropdownMenuItem(
                                        text = { Text(l + if (v == commitment) " ✓" else "", style = UiType.body, color = c.ink) },
                                        onClick = { commitment = v; pickingCommitment = false },
                                    )
                                }
                            }
                        }
                    }
                }
            }

            if (s.presets.isNotEmpty()) {
                Column(verticalArrangement = Arrangement.spacedBy(Dim.s2)) {
                    Text("Presets", style = UiType.section, color = c.inkMuted, modifier = Modifier.semantics { heading() })
                    ProfileChips(s.presets.map { it.id to it.name }, presetId ?: "") { id ->
                        s.presets.firstOrNull { it.id == id }?.let { p ->
                            presetId = p.id
                            intention = p.task ?: ""
                            focus = p.focusMinutes
                            customOpen = p.focusMinutes !in LENGTHS
                            rounds = maxOf(2, p.rounds)
                            breakMin = if (p.rounds > 1) p.breakMinutes else 0
                            if (s.profiles.any { it.id == p.profileId }) profileId = p.profileId
                            error = null
                        }
                    }
                }
            }
            TextAction("Save as preset", { savingPreset = true })

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
                // Owner 2026-11-03: once blocking is set up, an empty profile needs apps, not setup again.
                profile == null && appMissing.isEmpty() -> ProtectionLine("Timer only. Make a profile to choose what’s blocked.", false, "New profile") { namingProfile = true }
                profile != null && !rules.restrictsAnything && appMissing.isEmpty() ->
                    ProtectionLine("Timer only. $profileName doesn’t block any apps yet.", false, "Add apps") {
                        SetupReturn.pending = Triple(profile.id, intention, focus); openProfile(profile.id)
                    }
                !rules.restrictsAnything -> ProtectionLine("Timer only. Apps stay available on this phone.", false, "Set up blocking", openDevices)
                missing.isNotEmpty() -> ProtectionLine("Blocking needs ${FocusService.describe(missing)}. Until then, it’s a timer only.", false, "Set up blocking", openDevices)
                else -> ProtectionLine("${profileName}: ${rulesSummary(rules)}", true, null) {}
            }
        },
        actions = {
            if (valid) {
                // R55: the end time rolls up or down as the length changes.
                androidx.compose.animation.AnimatedContent(
                    targetState = total, label = "ends",
                    transitionSpec = {
                        val up = targetState > initialState
                        (androidx.compose.animation.slideInVertically(androidx.compose.animation.core.tween(220)) { h -> if (up) h else -h } + androidx.compose.animation.fadeIn(androidx.compose.animation.core.tween(220))) togetherWith
                            (androidx.compose.animation.slideOutVertically(androidx.compose.animation.core.tween(180)) { h -> if (up) -h else h } + androidx.compose.animation.fadeOut(androidx.compose.animation.core.tween(150)))
                    },
                ) { total ->
                Text(
                    buildAnnotatedString {
                        withStyle(SpanStyle(fontWeight = FontWeight.SemiBold, color = c.ink)) { append("Ends at ${Fmt.clock(now + total * 60_000L, zone)}") }
                        append(" · ${durationLabel(total)}${if (effectiveRounds > 1) " with breaks" else ""}")
                    },
                    style = UiType.caption.copy(fontFeatureSettings = "tnum"), color = c.inkMuted,
                )
                }
            }
            if (unlocked) Column(verticalArrangement = Arrangement.spacedBy(Dim.s1)) {
                Text("Unlocked session", style = UiType.label.copy(fontWeight = FontWeight.SemiBold), color = c.ink)
                Text("Nothing is blocked this time. If you open an app this profile would block, Lockd In notes it and your streak starts over.",
                    style = UiType.caption, color = c.inkMuted)
                TextAction("Lock apps instead", { unlocked = false })
            }
            MainButton(if (unlocked) "Start unlocked" else if (blockingReady) "Start focus" else "Start timer", onClick = {
                val problem = Session.validatePlan(segments)
                if (problem != null) error = problem
                else {
                    focusManager.clearFocus()
                    app.lockdin.audio.StudyPlayer.stopPreview()
                    store.startSession(
                        clock.now(), segments, intention, profileId,
                        LastChoice(focus, if (breakMin > 0) breakMin else s.last.breakMinutes, effectiveRounds, profileId),
                        presetId = presetId, audio = audioOverride, commitment = commitment, unlocked = unlocked,
                    )
                    // The start sequence plays once, for this manual start only.
                    store.state.active?.let { StartIntro.request(it.id) }
                }
            })
        },
    )
    }
    if (namingProfile) NewProfileDialog(onDismiss = { namingProfile = false }) { n ->
        val p = Profile(Store.newId(), n, "Only what you add", false)
        store.update { it.copy(profiles = it.profiles + p) }
        profileId = p.id; audioOverride = null; namingProfile = false
    }
    if (choosingMusic) ChooseMusicDialog(sessionAudio, onDismiss = { app.lockdin.audio.StudyPlayer.stopPreview(); choosingMusic = false }) {
        audioOverride = it
    }
    if (savingPreset) NamePresetDialog(
        initial = intention.trim().ifEmpty { profileName },
        onDismiss = { savingPreset = false },
    ) { name ->
        val p = app.lockdin.state.Preset(
            Store.newId(), name, focus, if (effectiveRounds > 1) breakMin else 0, effectiveRounds, profileId,
            task = intention.trim().ifEmpty { null },
        )
        store.update { it.copy(presets = (it.presets + p).takeLast(30)) }
        presetId = p.id
        savingPreset = false
    }
}

@Composable
fun NamePresetDialog(initial: String, onDismiss: () -> Unit, onSave: (String) -> Unit) {
    val c = LocalColors.current
    var name by rememberSaveable { mutableStateOf(initial.take(60)) }
    androidx.compose.material3.AlertDialog(
        onDismissRequest = onDismiss, containerColor = c.surface,
        title = { Text("Save as preset", style = UiType.title, color = c.ink) },
        text = {
            Column(verticalArrangement = Arrangement.spacedBy(Dim.s2)) {
                Text("Saves the task, length, breaks and profile. Start it in one tap from Home.", style = UiType.body, color = c.inkMuted)
                androidx.compose.material3.OutlinedTextField(
                    name, { name = it.take(60) }, singleLine = true, shape = RoundedCornerShape(Dim.radius), colors = fieldColors(),
                    modifier = Modifier.fillMaxWidth().semantics { contentDescription = "Preset name" },
                )
            }
        },
        confirmButton = { TextButton(onClick = { name.trim().takeIf { it.isNotEmpty() }?.let(onSave) }, enabled = name.isNotBlank()) { Text("Save", style = UiType.label, color = c.ink) } },
        dismissButton = { TextButton(onClick = onDismiss) { Text("Cancel", style = UiType.label, color = c.ink) } },
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

/** 25 / 40 / 60 / Custom (R55): big numbers, no tiles; an ink bar slides under the chosen one. */
@Composable
private fun LengthTiles(selected: Int, onSelect: (Int) -> Unit) {
    val c = LocalColors.current
    val large = LocalDensity.current.fontScale > 1.3f
    Column(Modifier.selectableGroup(), verticalArrangement = Arrangement.spacedBy(Dim.s1)) {
        (LENGTHS + (-1)).chunked(if (large) 2 else 4).forEach { row ->
            Row(horizontalArrangement = Arrangement.spacedBy(Dim.s4)) {
                row.forEach { m ->
                    val on = m == selected
                    UnderlineOption(on, { onSelect(m) }, Modifier.weight(1f).semantics {
                        contentDescription = if (m > 0) "$m minutes" else "Custom minutes"
                    }) {
                        if (m > 0) Row(verticalAlignment = Alignment.Bottom) {
                            Text("$m", style = Type.display.copy(fontSize = 30.sp, lineHeight = 32.sp, letterSpacing = 0.sp), color = if (on) c.ink else c.inkMuted)
                            Text(" min", style = UiType.caption, color = c.inkMuted, modifier = Modifier.padding(bottom = 3.dp))
                        } else Text("Custom", style = UiType.label.copy(fontSize = 16.sp, fontWeight = FontWeight.SemiBold), color = if (on) c.ink else c.inkMuted,
                            modifier = Modifier.padding(top = 8.dp, bottom = 3.dp))
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
    // R55: names with the sliding underline instead of boxed chips.
    FlowRow(Modifier.selectableGroup(), horizontalArrangement = Arrangement.spacedBy(Dim.s5), verticalArrangement = Arrangement.spacedBy(Dim.s1)) {
        profiles.forEach { (id, name) ->
            val on = id == selected
            UnderlineOption(on, { onSelect(id) }) {
                Text(name, style = UiType.label.copy(fontSize = 17.sp, fontWeight = if (on) FontWeight.SemiBold else FontWeight.Medium), color = if (on) c.ink else c.inkMuted)
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
    // Manual start only; consumed once, so reopening, rotation and scheduled starts never play it.
    // Only a screen that is in front may claim it (a hidden copy must never use it up).
    val lifecycle = androidx.lifecycle.compose.LocalLifecycleOwner.current.lifecycle
    val intro = remember(session.id) {
        if (lifecycle.currentState.isAtLeast(androidx.lifecycle.Lifecycle.State.STARTED) && StartIntro.consume(session.id)) session.id else null
    }
    // Closed lock and "blocking" wording only from a fresh, verified report by the service.
    val protected = snap.restricting && EnforcerStatus.protecting(session.id, report)
    androidx.compose.runtime.SideEffect { BackdropGate.locked = protected }
    androidx.compose.runtime.DisposableEffect(Unit) { onDispose { BackdropGate.locked = false } }
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
        isBreak && remaining <= 60_000 -> message(tone, Moment.BREAK_ENDING, task, custom = store.state.customMessages, seed = session.id)
        isBreak -> message(tone, Moment.BREAK_START, time = Fmt.clock(phase.end, zone))
        lifted -> "Unlocked until ${Fmt.clock((snap.overrideState as OverrideState.ActiveForFocusPeriod).until, zone)}. Later rounds start as planned."
        else -> message(tone, Moment.FOCUS_START, task, custom = store.state.customMessages, seed = session.id)
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
        session.unlocked -> "Unlocked session · nothing is blocked. Opening a blocked app is noted and resets your streak."
        protected -> "Blocking on this phone · ${rulesSummary(session.rules).replace(" during focus", "")}"
        !blocks -> "Timer only. Apps stay available on this phone."
        isBreak -> "Apps are available until focus resumes"
        lifted -> "Apps are available this round"
        permissionNeeded -> "Blocking paused: turn on ${FocusService.describe(report?.missing ?: emptySet())}"
        // Just started and not yet confirmed: say so, distinct from "timer only" (polish pass 2026-11-09).
        now - session.start < 20_000 -> "Confirming blocking on this phone…"
        else -> "Blocking isn’t running on this phone right now"
    }

    val setUpAlready = rememberMissing().isEmpty()
    // Phone (approved R36): the task, the lock and what's next; everything else is one tap away.
    var more by rememberSaveable { mutableStateOf(false) }
    val pending = snap.overrideState is OverrideState.Pending
    Column(
        Modifier.fillMaxSize().screenScroll().padding(top = LocalTopInset.current).padding(horizontal = Dim.gutter).padding(top = Dim.s4, bottom = Dim.s6),
        horizontalAlignment = Alignment.CenterHorizontally,
    ) {
        // Polish pass (owner 2026-11-09): the task, then this phone's real blocking status, then the
        // lock and countdown with the finish time right under it. "Focusing" only appears when it adds
        // something (a round count, a break, an unlocked round).
        Text(task ?: if (isBreak) "Break" else "Focus session", style = TimerType.task, color = c.ink, textAlign = TextAlign.Center,
            modifier = Modifier.semantics { heading() })
        Spacer(Modifier.height(Dim.s2))
        SessionStatusLine(protection, when {
            protected -> StatusKind.ON
            session.unlocked || !blocks || isBreak || lifted -> StatusKind.OFF
            else -> StatusKind.PENDING
        })
        if (isBreak || lifted || focusCount > 1) {
            Spacer(Modifier.height(Dim.s1))
            Text(if (isBreak || lifted) status else "Round $focusIndex of $focusCount", style = TimerType.status, color = c.inkMuted,
                modifier = Modifier.semantics { liveRegion = LiveRegionMode.Polite })
        }
        if (store.state.quotesOn) {
            Spacer(Modifier.height(Dim.s1))
            Text(Quotes.forSession(session.id), style = UiType.caption.copy(fontStyle = androidx.compose.ui.text.font.FontStyle.Italic), color = c.inkMuted, textAlign = TextAlign.Center)
        }
        Spacer(Modifier.height(Dim.s4))
        ClockLock(
            state, remaining, phase.end - phase.start, locked = protected, label = label,
            caption = if (isBreak) "left in break" else "left this round", width = sessionLockWidth(),
            introFor = intro, haptics = store.state.startHaptics, sound = store.state.startSound,
        )
        Spacer(Modifier.height(Dim.s3))
        Text(
            buildAnnotatedString {
                if (isBreak) { append("Focus resumes "); withStyle(SpanStyle(fontWeight = FontWeight.SemiBold, color = c.ink)) { append(Fmt.clock(phase.end, zone)) } }
                else {
                    append("Ends at "); withStyle(SpanStyle(fontWeight = FontWeight.SemiBold, color = c.ink)) { append(Fmt.clock(occ.end, zone)) }
                    if (next != null) append(" · break at ${Fmt.clock(phase.end, zone)}")
                }
            },
            style = UiType.body.copy(fontFeatureSettings = "tnum"), color = c.inkMuted, textAlign = TextAlign.Center,
        )
        if (session.audio.builtIn || session.audio.access == app.lockdin.audio.MusicAccess.LIMITED) {
            Spacer(Modifier.height(Dim.s3))
            AudioControl(session, isBreak)
        }
        Spacer(Modifier.height(Dim.s3))
        QuestionTimer(store, session, now)
        Spacer(Modifier.height(Dim.s3))
        MoreToggle("Details and options", more || pending) { more = !more }
        if (more || pending) {
            Column(Modifier.fillMaxWidth().padding(top = Dim.s3), verticalArrangement = Arrangement.spacedBy(Dim.s4)) {
                store.state.presets.firstOrNull { it.id == session.presetId }?.steps?.takeIf { it.isNotEmpty() }?.let { StepsList(session.id, it) }
                SessionRail(occ.phases, now, zone)
                FactList(facts)
                nudge?.let { Text(it, style = UiType.body, color = c.inkMuted) }
                // The fix-it action lives here; the status itself is shown under the task.
                if (!protected && !(blocks && (isBreak || lifted)) && !(!blocks && setUpAlready)) TextAction("Set up blocking", openDevices)
                OutPanel(store, session, snap, now, clock)
            }
        }
    }
}

/**
 * Study audio during a session (R48): one compact line under the timer (play/pause, what's
 * playing, next), which opens to volume and the other collections. The remaining music
 * browsing time shows only for a profile with limited music access. The lock stays the main visual.
 */
enum class StatusKind { ON, PENDING, OFF }

/** This phone's blocking status under the task: filled dot = verified, ring = waiting, none = off. */
@Composable
private fun SessionStatusLine(text: String, kind: StatusKind) {
    val c = LocalColors.current
    Row(
        Modifier.semantics(mergeDescendants = true) { liveRegion = LiveRegionMode.Polite },
        verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(Dim.s2),
    ) {
        when (kind) {
            StatusKind.ON -> Box(Modifier.size(9.dp).background(c.statusDot, CircleShape))
            StatusKind.PENDING -> Box(Modifier.size(9.dp).border(1.5.dp, c.inkMuted, CircleShape))
            StatusKind.OFF -> Unit
        }
        Text(text, style = UiType.caption, color = if (kind == StatusKind.ON) c.ink else c.inkMuted, textAlign = TextAlign.Center)
    }
}

@Composable
private fun AudioControl(session: ActiveSession, isBreak: Boolean) {
    val c = LocalColors.current
    val ctx = androidx.compose.ui.platform.LocalContext.current
    val a = session.audio
    val player = app.lockdin.audio.StudyPlayer
    var open by rememberSaveable { mutableStateOf(false) }
    Surface(shape = RoundedCornerShape(Dim.plate), color = androidx.compose.ui.graphics.Color.Transparent, modifier = Modifier.fillMaxWidth().plate()) {
        Column(Modifier.padding(horizontal = plateSide(Dim.s2), vertical = Dim.s1)) {
            if (a.builtIn) {
                val playing = player.isPlaying
                Row(verticalAlignment = Alignment.CenterVertically) {
                    androidx.compose.material3.IconButton(onClick = { player.toggle(ctx, a) }) {
                        LockdIcon(if (playing) LIcon.Pause else LIcon.Play, tint = c.ink, description = if (playing) "Pause study audio" else "Play study audio")
                    }
                    Column(Modifier.weight(1f)) {
                        Text(a.collection.label, style = UiType.label, color = c.ink, maxLines = 1)
                        Text(
                            if (playing) player.trackTitle ?: "Playing" else if (isBreak && a.breaks == app.lockdin.audio.BreakAudio.PAUSE) "Paused for the break" else "Paused",
                            style = UiType.caption, color = c.inkMuted, maxLines = 1,
                        )
                    }
                    if (player.hasNext(a.collection) && player.loaded != null) androidx.compose.material3.IconButton(onClick = { player.next() }) {
                        LockdIcon(LIcon.Next, tint = c.ink, description = "Next track")
                    }
                    androidx.compose.material3.IconButton(onClick = { open = !open }) {
                        LockdIcon(if (open) LIcon.ChevronUp else LIcon.ChevronDown, tint = c.inkMuted, description = if (open) "Hide audio options" else "Show audio options")
                    }
                }
                if (open) {
                    val v = player.sessionVolume.let { if (it >= 0f) it else a.volume }
                    androidx.compose.material3.Slider(
                        v, { player.setVolume(it) }, valueRange = 0f..1f,
                        colors = androidx.compose.material3.SliderDefaults.colors(thumbColor = c.ink, activeTrackColor = c.ink, inactiveTrackColor = c.line),
                        modifier = Modifier.padding(horizontal = Dim.s2).semantics { contentDescription = "Study audio volume" },
                    )
                    var picking by remember { mutableStateOf(false) }
                    Box(Modifier.padding(start = Dim.s2)) {
                        TextAction("Change sound", { picking = true })
                        androidx.compose.material3.DropdownMenu(picking, { picking = false }, containerColor = c.surface) {
                            app.lockdin.audio.AudioGroup.values().forEach { g ->
                                Text(g.label, style = UiType.caption, color = c.inkMuted, modifier = Modifier.padding(horizontal = Dim.s3, vertical = Dim.s1))
                                app.lockdin.audio.AudioCollection.values().filter { it.group == g }.forEach { k ->
                                    androidx.compose.material3.DropdownMenuItem(
                                        text = { Text(k.label + if (k == a.collection) " · playing" else "", style = UiType.body, color = c.ink) },
                                        onClick = {
                                            picking = false
                                            Store.get(ctx).update { s -> s.copy(active = s.active?.takeIf { it.id == session.id }?.let { it.copy(audio = it.audio.copy(collection = k)) } ?: s.active) }
                                            player.switchTo(ctx, a.copy(collection = k))
                                        },
                                    )
                                }
                            }
                        }
                    }
                }
            }
            if (a.access == app.lockdin.audio.MusicAccess.LIMITED) {
                val left = app.lockdin.audio.MusicAllowance.remainingMs(a, session.musicUsedMs)
                Text(
                    if (left > 0) "Music apps: ${app.lockdin.audio.MusicAllowance.format(left)} of browsing left" else "Music browsing time is up for this session. Your music can keep playing.",
                    style = UiType.caption, color = c.inkMuted,
                    modifier = Modifier.padding(horizontal = Dim.s2, vertical = Dim.s1),
                )
            }
        }
    }
}

/**
 * Question timing (D): optional and explicit. Time per item is just information; nothing here
 * changes blocking. A gentle note appears only when an item runs well past the person's own usual.
 */
@Composable
private fun QuestionTimer(store: Store, session: ActiveSession, now: Long) {
    val c = LocalColors.current
    val q = session.questions
    fun set(f: (app.lockdin.engine.QuestionLog) -> app.lockdin.engine.QuestionLog) = store.setQuestions(session.id) { f(it) }
    val cur = q.current
    if (cur == null) {
        val done = app.lockdin.engine.Questions.durations(q)
        Row(Modifier.fillMaxWidth(), verticalAlignment = Alignment.CenterVertically) {
            Text(
                if (done.isEmpty()) "Working through questions?" else "${done.size} question${if (done.size == 1) "" else "s"} timed",
                style = UiType.caption, color = c.inkMuted, modifier = Modifier.weight(1f),
            )
            TextAction(if (done.isEmpty()) "Time questions" else "Start another", { set { app.lockdin.engine.Questions.start(it, now) } })
        }
        return
    }
    val t = app.lockdin.engine.Questions.elapsed(cur, q, now)
    val long = app.lockdin.engine.Questions.runningLong(q, store.state.questionTimes + app.lockdin.engine.Questions.durations(q), now)
    Surface(shape = RoundedCornerShape(Dim.plate), color = androidx.compose.ui.graphics.Color.Transparent, modifier = Modifier.fillMaxWidth().plate()) {
        Column(Modifier.padding(horizontal = plateSide(Dim.s3), vertical = Dim.s3), verticalArrangement = Arrangement.spacedBy(Dim.s2)) {
            Row(verticalAlignment = Alignment.CenterVertically) {
                Text("Question ${cur.n}", style = UiType.label.copy(fontWeight = FontWeight.SemiBold), color = c.ink, modifier = Modifier.weight(1f))
                Text(
                    (if (q.pausedAt != null) "Paused · " else "") + "%d:%02d".format(t / 60_000, (t / 1000) % 60),
                    style = UiType.label.copy(fontFeatureSettings = "tnum"), color = c.ink,
                    modifier = Modifier.semantics { contentDescription = "Question ${cur.n}: ${t / 60_000} minutes" + if (q.pausedAt != null) ", paused" else "" },
                )
            }
            if (long) Text(
                "This one is taking longer than your usual. Keep going, mark it for review, or give it 10 more minutes.",
                style = UiType.caption, color = c.ink, modifier = Modifier.semantics { liveRegion = LiveRegionMode.Polite },
            )
            FlowRowCompat {
                TextAction("Next question", { set { app.lockdin.engine.Questions.next(it, now) } })
                TextAction("Mark for review", { set { app.lockdin.engine.Questions.stuck(it, now) } })
                if (long) TextAction("10 more minutes", { set { app.lockdin.engine.Questions.addTenMinutes(it, now) } })
                TextAction(if (q.pausedAt != null) "Resume" else "Pause", {
                    set { if (it.pausedAt != null) app.lockdin.engine.Questions.resume(it, now) else app.lockdin.engine.Questions.pause(it, now) }
                })
                TextAction("Finish", { set { app.lockdin.engine.Questions.finish(it, now) } })
            }
        }
    }
}

@OptIn(ExperimentalLayoutApi::class)
@Composable
internal fun FlowRowCompat(content: @Composable () -> Unit) =
    FlowRow(horizontalArrangement = Arrangement.spacedBy(Dim.s1)) { content() }

/** The preset's steps, ticked off during this session only (not saved). */
@Composable
private fun StepsList(sessionId: String, steps: List<String>) {
    val c = LocalColors.current
    var done by rememberSaveable(sessionId) { mutableStateOf(emptyList<Int>()) }
    Column {
        SectionLabel("Steps")
        SettingsGroup {
            steps.forEachIndexed { i, step ->
                if (i > 0) GroupDivider()
                val on = i in done
                Row(
                    Modifier.fillMaxWidth().heightIn(min = Dim.control + 4.dp)
                        .toggleable(on, role = Role.Checkbox) { done = if (on) done - i else done + i }
                        .padding(horizontal = Dim.s4),
                    verticalAlignment = Alignment.CenterVertically,
                ) {
                    Text(step, style = UiType.body, color = if (on) c.inkMuted else c.ink, modifier = Modifier.weight(1f))
                    androidx.compose.material3.Checkbox(on, onCheckedChange = null,
                        colors = androidx.compose.material3.CheckboxDefaults.colors(checkedColor = c.ink, uncheckedColor = c.controlLine, checkmarkColor = c.onInk))
                }
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
    androidx.compose.material3.Surface(shape = RoundedCornerShape(50), color = c.taupeSoft) {
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
            // Partner code (R49): code, admin, or an emergency end that waits 3/4 of the time left.
            session.commitment == app.lockdin.enforce.Commitment.PARTNER -> PartnerSessionOptions(store, session, now)
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
                val outro = remember(session.id) { if (onClose == null && CompleteOutro.consume(session.id)) session.id else null }
                ClockLock(TimerState.DONE, 0, 1, locked = false, label = "Session complete", caption = "complete", width = 150.dp,
                    outroFor = outro, haptics = store.state.startHaptics, sound = store.state.startSound)
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
                val qd = app.lockdin.engine.Questions.durations(session.questions)
                if (qd.isNotEmpty()) {
                    SectionLabel("Questions")
                    val review = session.questions.items.count { it.stuck }
                    Text(
                        "${qd.size} timed · typical ${(app.lockdin.engine.Questions.median(qd)!! + 30_000) / 60_000} min" +
                            if (review > 0) " · $review marked for review" else "",
                        style = UiType.body, color = c.ink,
                    )
                    Spacer(Modifier.height(Dim.s3))
                }
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
                store.state.active?.let { StartIntro.request(it.id) }
            }, modifier = Modifier.fillMaxWidth())
        },
    )
}

private const val AUTO_HOME_MS = 30_000L
