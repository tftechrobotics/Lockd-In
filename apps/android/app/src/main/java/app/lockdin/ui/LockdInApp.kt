package app.lockdin.ui

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
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
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
    DEVICES("Devices", LIcon.Devices, false),
    SETTINGS("Settings", LIcon.Gear, false),
}

@Composable
fun LockdInApp(store: Store, clock: AppClock = AppClock.System, zone: ZoneId = ZoneId.systemDefault(), dark: Boolean = isDark(store.state.theme)) {
    LockdInTheme(dark) {
        val c = LocalColors.current
        var route by rememberSaveable { mutableStateOf(Route.FOCUS) }
        var lastTab by rememberSaveable { mutableStateOf(Route.FOCUS) }
        fun go(r: Route) { if (r.tab) lastTab = r; route = r }
        BackHandler(enabled = !route.tab || route != Route.FOCUS) { go(if (!route.tab) lastTab else Route.FOCUS) }

        Scaffold(
            containerColor = c.ground,
            contentWindowInsets = WindowInsets.safeDrawing,
            topBar = {
                Box(Modifier.fillMaxWidth().windowInsetsPadding(WindowInsets.safeDrawing.only(WindowInsetsSides.Top + WindowInsetsSides.Horizontal)).height(64.dp)) {
                    if (!route.tab) {
                        IconButton(onClick = { go(lastTab) }, modifier = Modifier.align(Alignment.CenterStart).padding(start = 4.dp)) {
                            LockdIcon(LIcon.Back, description = "Back")
                        }
                    }
                    Image(
                        painterResource(R.drawable.wordmark), contentDescription = "Lockd In",
                        colorFilter = ColorFilter.tint(c.ink),
                        modifier = Modifier.align(Alignment.Center).height(32.dp),
                    )
                    IconButton(onClick = { go(Route.SETTINGS) }, modifier = Modifier.align(Alignment.CenterEnd).padding(end = 4.dp)) {
                        LockdIcon(LIcon.Gear, description = "Settings")
                    }
                }
            },
            bottomBar = {
                Column {
                HorizontalDivider(color = c.line)
                NavigationBar(containerColor = c.ground, contentColor = c.ink) {
                    Route.entries.filter { it.tab }.forEach { r ->
                        NavigationBarItem(
                            selected = (if (route.tab) route else lastTab) == r,
                            onClick = { go(r) },
                            icon = { LockdIcon(r.icon) },
                            label = { Text(r.label, style = Type.caption.copy(fontWeight = if ((if (route.tab) route else lastTab) == r) androidx.compose.ui.text.font.FontWeight.SemiBold else androidx.compose.ui.text.font.FontWeight.Normal)) },
                            modifier = Modifier.testTag("tab-${r.name.lowercase()}"),
                            colors = NavigationBarItemDefaults.colors(
                                selectedIconColor = c.ink, selectedTextColor = c.ink, indicatorColor = c.taupeSoft,
                                unselectedIconColor = c.inkMuted, unselectedTextColor = c.inkMuted,
                            ),
                        )
                    }
                }
                }
            },
        ) { padding ->
            Box(Modifier.fillMaxSize().padding(padding).imePadding(), contentAlignment = Alignment.TopCenter) {
                Column(
                    Modifier
                        .widthIn(max = 560.dp)
                        .fillMaxWidth()
                        .verticalScroll(rememberScrollState())
                        .padding(horizontal = 20.dp, vertical = 8.dp)
                        .padding(bottom = 24.dp)
                        .semantics { contentDescription = "${route.label} screen" },
                ) {
                    when (route) {
                        Route.FOCUS -> FocusScreen(store, clock, zone, openDevices = { go(Route.DEVICES) }, openSchedule = { go(Route.SCHEDULE) })
                        Route.SCHEDULE -> ScheduleScreen(store, clock.now(), zone)
                        Route.PROFILES -> ProfilesScreen(store)
                        Route.DEVICES -> DevicesScreen()
                        Route.SETTINGS -> SettingsScreen(store)
                    }
                }
            }
        }
    }
}
