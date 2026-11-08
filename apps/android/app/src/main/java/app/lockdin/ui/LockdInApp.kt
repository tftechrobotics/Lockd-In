package app.lockdin.ui

import androidx.compose.animation.togetherWith

import androidx.compose.material3.HorizontalDivider
import androidx.activity.compose.BackHandler
import androidx.compose.foundation.Image
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.WindowInsets
import androidx.compose.foundation.layout.WindowInsetsSides
import androidx.compose.foundation.layout.only
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.imePadding
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.safeDrawing
import androidx.compose.foundation.layout.widthIn
import androidx.compose.foundation.layout.windowInsetsPadding
import androidx.compose.material3.IconButton
import androidx.compose.material3.NavigationBar
import androidx.compose.material3.NavigationBarItem
import androidx.compose.material3.NavigationBarItemDefaults
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.ui.Alignment
import app.lockdin.engine.ScheduledSession
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.runtime.remember
import androidx.compose.foundation.layout.consumeWindowInsets
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.background
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.ColorFilter
import androidx.compose.ui.res.painterResource
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.unit.dp
import app.lockdin.R
import app.lockdin.state.AppClock
import app.lockdin.state.Store
import java.time.ZoneId

enum class Route(val label: String, val icon: LIcon, val tab: Boolean) {
    FOCUS("Focus", LIcon.Focus, true),
    SCHEDULE("Schedule", LIcon.Calendar, true),
    PROFILES("Profiles", LIcon.Book, true),
    HISTORY("History", LIcon.History, true),
    DEVICES("Blocking", LIcon.Shield, false),
    SETTINGS("Settings", LIcon.Gear, false),
}

@OptIn(androidx.compose.foundation.layout.ExperimentalLayoutApi::class)
@Composable
fun LockdInApp(
    store: Store, clock: AppClock = AppClock.System, zone: ZoneId = ZoneId.systemDefault(), dark: Boolean = isDark(store.state.theme),
    history: app.lockdin.state.HistoryStore? = null,
    /** Play the opening splash (the app's own launch only; tests leave it off). */
    splash: Boolean = false,
) {
    val ctx = androidx.compose.ui.platform.LocalContext.current
    val hist = history ?: remember { app.lockdin.state.HistoryStore.get(ctx) }
    LockdInTheme(dark) {
        val c = LocalColors.current
        var showSplash by remember { mutableStateOf(splash && !Splash.played) }
        if (showSplash) {
            Box(Modifier.fillMaxSize().background(c.ground)) { SplashScreen { Splash.played = true; showSplash = false } }
            return@LockdInTheme
        }
        var route by rememberSaveable { mutableStateOf(Route.FOCUS) }
        var lastTab by rememberSaveable { mutableStateOf(Route.FOCUS) }
        var editing by remember { mutableStateOf<ScheduledSession?>(null) }
        var editingProfile by remember { mutableStateOf<String?>(null) }
        var editingPreset by remember { mutableStateOf<String?>(null) }
        var editingPlace by remember { mutableStateOf<app.lockdin.engine.Place?>(null) }
        fun go(r: Route) { if (r.tab) lastTab = r; route = r }
        // The tour points at parts of the Focus home screen (owner 2026-11-03), so it runs there.
        val touring = store.state.tourStep >= 0
        androidx.compose.runtime.LaunchedEffect(touring) { if (touring) go(Route.FOCUS) }
        // A notification asked for a screen (e.g. Reschedule → Schedule).
        val asked = Resumes.route
        androidx.compose.runtime.LaunchedEffect(asked) {
            when (asked) { "schedule" -> go(Route.SCHEDULE); "focus" -> go(Route.FOCUS) }
            if (asked != null) Resumes.route = null
        }
        BackHandler(enabled = editing != null || editingProfile != null || editingPreset != null || editingPlace != null || !route.tab || route != Route.FOCUS) {
            when {
                editingPlace != null -> editingPlace = null
                editing != null -> editing = null
                editingProfile != null -> editingProfile = null
                editingPreset != null -> editingPreset = null
                !route.tab -> go(lastTab)
                else -> go(Route.FOCUS)
            }
        }

        // First run: a short introduction (skippable).
        if (!store.state.onboarded) {
            Box(
                Modifier.fillMaxSize().background(c.ground)
                    .windowInsetsPadding(WindowInsets.safeDrawing).semantics { contentDescription = "Welcome" },
            ) { Onboarding(store) }
            return@LockdInTheme
        }
                // Schedule create/edit is its own full screen: no tabs, its own close and save.
        editing?.let { e ->
            Box(
                Modifier.fillMaxSize().background(c.ground)
                    .windowInsetsPadding(WindowInsets.safeDrawing).semantics { contentDescription = "Schedule editor" },
            ) { ScheduleEditor(store, e, zone, clock.now(), onClose = { editing = null }) }
            return@LockdInTheme
        }
        // Choosing a profile's apps is also its own full screen.
        store.state.profiles.firstOrNull { it.id == editingProfile }?.let { p ->
            Box(
                Modifier.fillMaxSize().background(c.ground)
                    .windowInsetsPadding(WindowInsets.safeDrawing).semantics { contentDescription = "Profile apps" },
            ) { ProfileEditor(store, p, onClose = { editingProfile = null }) }
            return@LockdInTheme
        }
        editingPlace?.let { pl ->
            Box(
                Modifier.fillMaxSize().background(c.ground)
                    .windowInsetsPadding(WindowInsets.safeDrawing).semantics { contentDescription = "Place editor" },
            ) { PlaceEditor(store, pl, onClose = { editingPlace = null }) }
            return@LockdInTheme
        }
        store.state.presets.firstOrNull { it.id == editingPreset }?.let { p ->
            Box(
                Modifier.fillMaxSize().background(c.ground)
                    .windowInsetsPadding(WindowInsets.safeDrawing).semantics { contentDescription = "Preset editor" },
            ) { PresetEditor(store, p, clock.now(), zone, onClose = { editingPreset = null }) }
            return@LockdInTheme
        }

        // The chosen background (C2) fills the Focus screen edge to edge, behind the header too.
        Box(Modifier.fillMaxSize()) {
        // Owner 2026-11-03: the chosen background shows on the Focus screen all the time, not only while locked.
        // R58: Settings shows the same background, blurred and dimmed, behind its glass plates.
        val backdrop = if (route == Route.FOCUS || route == Route.SETTINGS) runCatching { Backdrop.valueOf(store.state.backdrop) }.getOrDefault(Backdrop.NONE) else Backdrop.NONE
        BackdropBox(backdrop, store.state.backdropWords, frosted = route == Route.SETTINGS) {
        Scaffold(
            containerColor = if (backdrop != Backdrop.NONE) androidx.compose.ui.graphics.Color.Transparent else c.ground,
            contentWindowInsets = WindowInsets.safeDrawing,
            topBar = {
                Row(
                    Modifier.fillMaxWidth().windowInsetsPadding(WindowInsets.safeDrawing.only(WindowInsetsSides.Top + WindowInsetsSides.Horizontal))
                        .height(56.dp).padding(horizontal = 4.dp),
                    verticalAlignment = Alignment.CenterVertically,
                ) {
                    if (!route.tab) {
                        IconButton(onClick = { go(lastTab) }) { LockdIcon(LIcon.Back, description = "Back") }
                    } else Spacer(Modifier.width(16.dp))
                    Image(
                        painterResource(R.drawable.wordmark), contentDescription = "Lockd In",
                        colorFilter = ColorFilter.tint(c.ink),
                        modifier = Modifier.height(24.dp),
                    )
                    Spacer(Modifier.weight(1f))
                    IconButton(onClick = { go(Route.SETTINGS) }, modifier = Modifier.tourTarget("settings")) { LockdIcon(LIcon.Gear, description = "Settings") }
                }
            },
            bottomBar = {
                // R58 smoked-glass bar (owner's pick: "Lens", without a lens on the active tab):
                // the background shows through a dark tint that deepens toward the bottom, with a
                // light edge along the top. The active tab is marked by ink and weight only.
                val darkBar = c == DarkColors
                Column(Modifier.background(androidx.compose.ui.graphics.Brush.verticalGradient(
                    // Owner 2026-11-08: always readable over any background, so the smoke is nearly solid.
                    if (darkBar) listOf(c.ground.copy(alpha = 0.86f), c.ground.copy(alpha = 0.97f))
                    else listOf(c.ground.copy(alpha = 0.88f), c.ground.copy(alpha = 0.97f)),
                ))) {
                    HorizontalDivider(color = if (darkBar) androidx.compose.ui.graphics.Color(0x47FFF0DC) else androidx.compose.ui.graphics.Color(0xE6FFFFFF))
                    // Tab labels grow up to 150% of normal; beyond that "Schedule" breaks mid-word in the
                    // wider LiterataText (pass 5). Screen content still follows the full system text size.
                    val dens = androidx.compose.ui.platform.LocalDensity.current
                    androidx.compose.runtime.CompositionLocalProvider(
                        androidx.compose.ui.platform.LocalDensity provides androidx.compose.ui.unit.Density(dens.density, dens.fontScale.coerceAtMost(1.5f)),
                    ) {
                    NavigationBar(containerColor = androidx.compose.ui.graphics.Color.Transparent, contentColor = c.ink, tonalElevation = 0.dp) {
                        Route.entries.filter { it.tab }.forEach { r ->
                            val selected = (if (route.tab) route else lastTab) == r
                            NavigationBarItem(
                                selected = selected,
                                onClick = { go(r) },
                                icon = { LockdIcon(r.icon) },
                                label = { Text(r.label, style = UiType.caption.copy(fontWeight = if (selected) FontWeight.SemiBold else FontWeight.Normal)) },
                                modifier = Modifier.testTag("tab-${r.name.lowercase()}").tourTarget("tab-${r.name.lowercase()}"),
                                colors = NavigationBarItemDefaults.colors(
                                    selectedIconColor = c.ink, selectedTextColor = c.ink, indicatorColor = androidx.compose.ui.graphics.Color.Transparent,
                                    unselectedIconColor = c.inkMuted, unselectedTextColor = c.inkMuted,
                                ),
                            )
                        }
                    }
                    }
                }
            },
        ) { padding ->
            // Each screen owns its scrolling and pinned actions; the IME pushes the content up.
            Box(
                Modifier.fillMaxSize().padding(padding).consumeWindowInsets(padding).imePadding(),
                contentAlignment = Alignment.TopCenter,
            ) {
                Box(Modifier.widthIn(max = 600.dp).fillMaxSize().semantics { contentDescription = "${route.label} screen" }) {
                    // R55: screens slide a little toward the tab you chose and fade in.
                    val still = reducedMotion()
                    androidx.compose.animation.AnimatedContent(
                        targetState = route, label = "tabs",
                        transitionSpec = {
                            if (still) androidx.compose.animation.EnterTransition.None togetherWith androidx.compose.animation.ExitTransition.None
                            else {
                                val dir = if (targetState.ordinal >= initialState.ordinal) 1 else -1
                                (androidx.compose.animation.fadeIn(androidx.compose.animation.core.tween(220, delayMillis = 40)) +
                                    androidx.compose.animation.slideInHorizontally(androidx.compose.animation.core.tween(260)) { w -> dir * w / 14 }) togetherWith
                                    androidx.compose.animation.fadeOut(androidx.compose.animation.core.tween(120))
                            }
                        },
                    ) { route ->
                    when (route) {
                        Route.FOCUS -> FocusScreen(store, clock, zone, openDevices = { go(Route.DEVICES) }, openSchedule = { go(Route.SCHEDULE) }, openProfile = { editingProfile = it })
                        Route.SCHEDULE -> ScheduleScreen(store, clock.now(), zone, onEdit = { editing = it })
                        Route.PROFILES -> ProfilesScreen(store, onEdit = { editingProfile = it.id }, onEditPreset = { editingPreset = it })
                        Route.DEVICES -> DevicesScreen()
                        Route.SETTINGS -> SettingsScreen(store, hist, zone, onEditPlace = { editingPlace = it })
                        Route.HISTORY -> HistoryScreen(store, hist, clock, zone)
                    }
                    }
                }
            }
        }
        }
        if (touring) TourOverlay(store)
        if (ReadyMoment.showing) ReadyScreen(store.state.startHaptics) { ReadyMoment.showing = false }
        }
    }
}
