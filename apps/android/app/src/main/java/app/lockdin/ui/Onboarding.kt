// First run (owner 2026-11-03): a short slideshow on why Lockd In helps, then "let's get started"
// with the account, the first profile (no ready-made ones), blocking, an optional partner, and a
// tour of the real screen. Skipping is always possible; nothing is required to start a timer.
package app.lockdin.ui

import android.Manifest
import android.os.Build
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.selection.toggleable
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.Checkbox
import androidx.compose.material3.CheckboxDefaults
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.produceState
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.semantics.Role
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.heading
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.semantics.LiveRegionMode
import androidx.compose.ui.semantics.liveRegion
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import androidx.compose.foundation.layout.offset
import androidx.compose.runtime.remember
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.ui.layout.boundsInWindow
import androidx.compose.ui.layout.onGloballyPositioned
import app.lockdin.enforce.AppRules
import app.lockdin.enforce.InstalledApps
import app.lockdin.enforce.LaunchableApp
import app.lockdin.enforce.RuleMode
import app.lockdin.state.Profile
import app.lockdin.state.Store
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext

/** Apps people most often want out of the way while working, offered first in setup when installed. */
private val SUGGESTED = listOf(
    "com.instagram.android", "com.zhiliaoapp.musically", "com.ss.android.ugc.trill", "com.google.android.youtube",
    "com.snapchat.android", "com.twitter.android", "com.facebook.katana", "com.reddit.frontpage", "com.discord",
    "com.netflix.mediaclient", "tv.twitch.android.app", "com.pinterest", "com.instagram.barcelona",
)

/** Why-slides before setup. */
private data class Slide(val title: String, val text: String, val icon: LIcon?)

private val SLIDES = listOf(
    Slide("Start focus. Keep distractions closed.", "Pick a task and a time. During focus, the apps that pull you away are covered until you’re done.", null),
    Slide("Plan it, then just show up.", "Schedule study sessions with breaks built in. Lockd In reminds you, or starts them for you.", LIcon.Calendar),
    Slide("Someone in your corner.", "Add a partner who approves early exits, so stopping early takes a real reason. Emergencies always have a way out.", LIcon.Person),
    Slide("So let’s get started.", "First, make your account. Then choose what to block and turn blocking on. It takes about two minutes.", LIcon.Focus),
)

/** Setup steps after the slides: account, profile, blocking, partner, done. */
private const val SETUP_STEPS = 4

@OptIn(androidx.compose.foundation.ExperimentalFoundationApi::class)
@Composable
fun Onboarding(store: Store) {
    val c = LocalColors.current
    // -1 = slides; 0 account, 1 profile, 2 blocking, 3 partner, 4 done.
    var step by rememberSaveable { mutableIntStateOf(-1) }
    val pager = androidx.compose.foundation.pager.rememberPagerState { SLIDES.size }
    val scope = androidx.compose.runtime.rememberCoroutineScope()
    // The first profile, created when leaving the profile step (kept if the person goes back and forth).
    var name by rememberSaveable { mutableStateOf("Study") }
    var picked by rememberSaveable { mutableStateOf(setOf<String>()) }
    var startSignIn by rememberSaveable { mutableStateOf(false) }
    val endTutorial = { store.update { it.copy(onboarded = true, tourStep = -1) } }
    fun saveProfile() {
        val n = name.trim().take(40)
        if (n.isEmpty() || store.state.profiles.any { it.name == n }) return
        val p = Profile(Store.newId(), n, "Made during setup", false, AppRules(RuleMode.BLOCK_LIST, picked))
        store.update { it.copy(profiles = it.profiles + p, last = it.last.copy(profileId = p.id)) }
    }
    androidx.activity.compose.BackHandler(enabled = step >= 0 || pager.currentPage > 0) {
        if (step > 0) step-- else if (step == 0) step = -1 else scope.launch { pager.animateScrollToPage(pager.currentPage - 1) }
    }

    if (step < 0) {
        val last = pager.currentPage == SLIDES.lastIndex
        Column(Modifier.fillMaxSize()) {
            Row(Modifier.fillMaxWidth().padding(start = Dim.gutter, end = Dim.s2, top = Dim.s2), verticalAlignment = Alignment.CenterVertically) {
                Dots(pager.currentPage, SLIDES.size, "Slide", Modifier.weight(1f))
                if (!last) TextAction("Skip", { scope.launch { pager.animateScrollToPage(SLIDES.lastIndex) } })
            }
            androidx.compose.foundation.pager.HorizontalPager(pager, Modifier.weight(1f).fillMaxWidth()) { i ->
                val sl = SLIDES[i]
                Column(
                    Modifier.fillMaxSize().verticalScroll(rememberScrollState()).padding(horizontal = Dim.gutter),
                    horizontalAlignment = Alignment.CenterHorizontally, verticalArrangement = Arrangement.spacedBy(Dim.s4, Alignment.CenterVertically),
                ) {
                    if (sl.icon == null) ClockLock(TimerState.READY, 0, 1, locked = false, label = "Lockd In", caption = "lockd in", text = "", width = 180.dp)
                    else Surface(color = c.taupeSoft, shape = androidx.compose.foundation.shape.CircleShape, modifier = Modifier.size(120.dp)) {
                        Box(contentAlignment = Alignment.Center) { LockdIcon(sl.icon, size = 52.dp, tint = c.ink) }
                    }
                    Heading(sl.title, center = true)
                    Body(sl.text, center = true)
                }
            }
            ActionBar {
                if (last) MainButton("Make my account", { step = 0 })
                else MainButton("Next", { scope.launch { pager.animateScrollToPage(pager.currentPage + 1) } })
                if (last) TextAction("I already have an account", { step = 0; startSignIn = true }, modifier = Modifier.align(Alignment.CenterHorizontally))
            }
        }
        return
    }

    Column(Modifier.fillMaxSize()) {
        Row(Modifier.fillMaxWidth().padding(start = Dim.gutter, end = Dim.s2, top = Dim.s2), verticalAlignment = Alignment.CenterVertically) {
            if (step < SETUP_STEPS) Dots(step, SETUP_STEPS, "Step", Modifier.weight(1f)) else Spacer(Modifier.weight(1f))
            TextAction("End tutorial", { saveProfileIfNamed(step, ::saveProfile); endTutorial() })
        }
        Column(
            Modifier.weight(1f).fillMaxWidth().verticalScroll(rememberScrollState()).padding(horizontal = Dim.gutter),
            verticalArrangement = Arrangement.spacedBy(Dim.s4),
        ) {
            Spacer(Modifier.height(Dim.s2))
            when (step) {
                0 -> {
                    val acct = store.state.account
                    Heading(if (acct == null) "Make your account" else "You’re signed in")
                    if (acct == null) {
                        Body("So this phone connects to Lockd In online, and to your computer once that’s ready.")
                        AccountForm(store, startWithSignIn = startSignIn, onSignedIn = { step = 1 })
                    } else Body("${acct.email}. You can manage it in Settings › Account.")
                }
                1 -> FirstProfileStep(name, { name = it.take(40) }, picked, { picked = it })
                2 -> {
                    Heading("Turn on blocking")
                    Body("Android only lets an app see which app is open with your permission. Without it, sessions run as a timer. You can do this later in Settings › Blocking.")
                    BlockingSetup()
                }
                3 -> {
                    Heading("Add a partner (optional)")
                    Body("Someone you trust approves it when you want to end a session early. You can also do this later in Settings › Ending early.")
                    PartnerSetup(store)
                }
                else -> Column(Modifier.fillMaxWidth(), horizontalAlignment = Alignment.CenterHorizontally, verticalArrangement = Arrangement.spacedBy(Dim.s4)) {
                    Spacer(Modifier.height(Dim.s4))
                    Heading("You’re set", center = true)
                    Body("Next, a quick look at each part of the main screen. Tap Next to move on, or Skip any time. You can replay it from Settings › Help and tour.", center = true)
                }
            }
            Spacer(Modifier.height(Dim.s4))
        }
        ActionBar {
            when (step) {
                0 -> if (store.state.account != null) MainButton("Continue", { step = 1 })
                    else TextAction("Not now, use Lockd In on this phone only", { step = 1 }, modifier = Modifier.align(Alignment.CenterHorizontally))
                1 -> MainButton("Continue", { if (name.isNotBlank()) { saveProfile(); step = 2 } })
                2 -> MainButton("Continue", { step = 3 })
                3 -> MainButton(if (store.state.partner != null) "Continue" else "Skip for now", {
                    if (store.state.partner?.claimed == true) store.update { it.copy(commitment = app.lockdin.enforce.Commitment.PARTNER) }
                    step = 4
                })
                else -> MainButton("Show me around", { store.update { it.copy(onboarded = true, tourStep = 0) } })
            }
            if (step > 0) TextAction("Back", { step-- }, modifier = Modifier.align(Alignment.CenterHorizontally))
        }
    }
}

private fun saveProfileIfNamed(step: Int, save: () -> Unit) { if (step >= 1) save() }

@Composable
private fun FirstProfileStep(name: String, onName: (String) -> Unit, picked: Set<String>, onPicked: (Set<String>) -> Unit) {
    val c = LocalColors.current
    val ctx = LocalContext.current
    val apps by produceState<List<LaunchableApp>?>(null) {
        value = withContext(Dispatchers.IO) {
            val all = runCatching { InstalledApps.launchable(ctx) }.getOrDefault(emptyList())
            val suggested = all.filter { it.packageName in SUGGESTED }.sortedBy { SUGGESTED.indexOf(it.packageName) }
            suggested + all.filter { it.packageName !in SUGGESTED }.take(maxOf(0, 10 - suggested.size))
        }
    }
    Heading("Make your first profile")
    Body("A profile is what gets blocked for one kind of work. You can make more later in Profiles.")
    OutlinedTextField(
        name, onName, singleLine = true, label = { Text("Name") }, shape = RoundedCornerShape(Dim.radius), colors = fieldColors(),
        modifier = Modifier.fillMaxWidth().semantics { contentDescription = "Profile name" },
    )
    ProfileNameChips(listOf("Study", "Work", "Reading", "Writing"), name, onName)
    Text("Block these apps during focus", style = UiType.label, color = c.ink, modifier = Modifier.semantics { heading() })
    val list = apps
    if (list == null) Text("Loading apps…", style = UiType.caption, color = c.inkMuted)
    else if (list.isEmpty()) Text("No apps found. You can add apps later in Profiles.", style = UiType.caption, color = c.inkMuted)
    else SettingsGroup {
        list.forEachIndexed { i, a ->
            if (i > 0) GroupDivider()
            val on = a.packageName in picked
            Row(
                Modifier.fillMaxWidth().heightIn(min = Dim.control + 4.dp)
                    .toggleable(on, role = Role.Checkbox) { onPicked(if (it) picked + a.packageName else picked - a.packageName) }
                    .padding(horizontal = Dim.s3),
                verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(Dim.s3),
            ) {
                AppIcon(a.packageName, 30.dp)
                Text(a.label, style = UiType.body, color = c.ink, modifier = Modifier.weight(1f))
                Checkbox(on, null, colors = CheckboxDefaults.colors(checkedColor = c.ink, uncheckedColor = c.controlLine, checkmarkColor = c.onInk))
            }
        }
    }
    Text("More apps and websites can be added in Profiles.", style = UiType.caption, color = c.inkMuted)
}

@Composable
private fun ProfileNameChips(names: List<String>, current: String, onPick: (String) -> Unit) {
    val c = LocalColors.current
    androidx.compose.foundation.layout.FlowRow(horizontalArrangement = Arrangement.spacedBy(Dim.s2), verticalArrangement = Arrangement.spacedBy(Dim.s2)) {
        names.forEach { n ->
            val on = n == current.trim()
            Surface(
                shape = androidx.compose.foundation.shape.CircleShape, color = if (on) c.ink else androidx.compose.ui.graphics.Color.Transparent,
                contentColor = if (on) c.onInk else c.ink, border = if (on) null else androidx.compose.foundation.BorderStroke(1.dp, c.line),
                modifier = Modifier.heightIn(min = Dim.control).clickable(role = Role.Button) { onPick(n) },
            ) { Box(Modifier.padding(horizontal = 14.dp, vertical = 10.dp), contentAlignment = Alignment.Center) { Text(n, style = UiType.label) } }
        }
    }
}

@Composable
private fun Dots(at: Int, count: Int, what: String, modifier: Modifier) {
    val c = LocalColors.current
    Row(modifier.semantics { contentDescription = "$what ${at + 1} of $count" }, horizontalArrangement = Arrangement.spacedBy(6.dp)) {
        repeat(count) { i ->
            Box(Modifier.height(8.dp).width(if (i == at) 20.dp else 8.dp).background(if (i == at) c.ink else c.line, RoundedCornerShape(4.dp)))
        }
    }
}

@Composable
private fun Heading(t: String, center: Boolean = false) = Text(t, style = UiType.title, color = LocalColors.current.ink,
    textAlign = if (center) TextAlign.Center else TextAlign.Start, modifier = Modifier.semantics { heading() })

@Composable
private fun Body(t: String, center: Boolean = false) = Text(t, style = UiType.body, color = LocalColors.current.inkMuted,
    textAlign = if (center) TextAlign.Center else TextAlign.Start)

/** Tour tips (owner 2026-11-03): small callouts pointing at each part of the Focus home screen. */
data class TourTip(val target: String, val title: String, val text: String)

val TOUR = listOf(
    TourTip("new", "Start a session", "Pick what you’re working on, how long, and which profile to block with."),
    TourTip("next", "What’s next", "Your next planned session. Tap it to open your schedule."),
    TourTip("tab-schedule", "Schedule", "Plan sessions and weekly routines. Lockd In reminds you, or starts them."),
    TourTip("tab-profiles", "Profiles", "Each profile decides which apps and websites are blocked."),
    TourTip("tab-history", "History", "Finished sessions, kept on this phone."),
    TourTip("settings", "Settings", "Blocking, ending early, your account and more."),
)

/** Where each tour target is on screen, reported by [tourTarget]. */
object TourTargets { val rects = androidx.compose.runtime.mutableStateMapOf<String, androidx.compose.ui.geometry.Rect>() }

fun Modifier.tourTarget(key: String): Modifier = this.then(
    Modifier.onGloballyPositioned { TourTargets.rects[key] = it.boundsInWindow() },
)

/** Dims the screen except the current target, with a small bubble next to it. */
@Composable
fun TourOverlay(store: Store) {
    val step = store.state.tourStep
    val tip = TOUR.getOrNull(step) ?: return
    val c = LocalColors.current
    val density = androidx.compose.ui.platform.LocalDensity.current
    var origin by remember { mutableStateOf(androidx.compose.ui.geometry.Offset.Zero) }
    var size by remember { mutableStateOf(androidx.compose.ui.unit.IntSize.Zero) }
    var bubbleH by remember { mutableIntStateOf(0) }
    val target = TourTargets.rects[tip.target]?.translate(-origin)
    val pad = with(density) { 6.dp.toPx() }
    val hole = target?.let { androidx.compose.ui.geometry.Rect(it.left - pad, it.top - pad, it.right + pad, it.bottom + pad) }
    val scrim = c.ink.copy(alpha = 0.55f)
    val next = { store.update { it.copy(tourStep = if (step + 1 < TOUR.size) step + 1 else -1) } }
    val end = { store.update { it.copy(tourStep = -1) } }

    Box(
        Modifier.fillMaxSize()
            .onGloballyPositioned { origin = it.boundsInWindow().topLeft; size = it.size }
            .pointerInput(Unit) { awaitPointerEventScope { while (true) { awaitPointerEvent().changes.forEach { it.consume() } } } },
    ) {
        androidx.compose.foundation.Canvas(Modifier.fillMaxSize().graphicsLayer { compositingStrategy = androidx.compose.ui.graphics.CompositingStrategy.Offscreen }) {
            drawRect(scrim)
            hole?.let {
                val r = androidx.compose.ui.geometry.CornerRadius(16.dp.toPx())
                drawRoundRect(androidx.compose.ui.graphics.Color.Black, it.topLeft, it.size, r, blendMode = androidx.compose.ui.graphics.BlendMode.Clear)
                drawRoundRect(c.ground, it.topLeft, it.size, r, style = androidx.compose.ui.graphics.drawscope.Stroke(2.dp.toPx()))
            }
        }
        // Below the target when it's in the top half of the screen, otherwise above it.
        val gap = with(density) { 12.dp.toPx() }
        val above = hole != null && hole.center.y > size.height / 2f
        val y = when {
            hole == null -> (size.height - bubbleH) / 2f
            above -> hole.top - gap - bubbleH
            else -> hole.bottom + gap
        }.coerceIn(0f, (size.height - bubbleH).coerceAtLeast(0).toFloat())
        val sidePx = with(density) { Dim.gutter.toPx() }
        val arrowX = (hole?.center?.x ?: (size.width / 2f)).coerceIn(sidePx + 24f, size.width - sidePx - 24f)
        Column(
            Modifier.fillMaxWidth().padding(horizontal = Dim.gutter)
                .offset { androidx.compose.ui.unit.IntOffset(0, y.toInt()) }
                .onGloballyPositioned { bubbleH = it.size.height }
                .semantics { liveRegion = LiveRegionMode.Polite },
        ) {
            if (hole != null && !above) Arrow(up = true, x = arrowX - sidePx, color = c.surface)
            Surface(color = c.surface, contentColor = c.ink, shape = RoundedCornerShape(14.dp), shadowElevation = 6.dp, modifier = Modifier.fillMaxWidth()) {
                Column(Modifier.padding(horizontal = Dim.s4, vertical = Dim.s3), verticalArrangement = Arrangement.spacedBy(4.dp)) {
                    Text("${step + 1} of ${TOUR.size}", style = UiType.caption, color = c.inkMuted)
                    Text(tip.title, style = UiType.label, color = c.ink, modifier = Modifier.semantics { heading() })
                    Text(tip.text, style = UiType.caption, color = c.ink)
                    Row(verticalAlignment = Alignment.CenterVertically) {
                        androidx.compose.material3.TextButton(onClick = end, modifier = Modifier.heightIn(min = Dim.control)) {
                            Text("Skip tour", style = UiType.label, color = c.inkMuted)
                        }
                        Spacer(Modifier.weight(1f))
                        androidx.compose.material3.Button(
                            onClick = next,
                            colors = androidx.compose.material3.ButtonDefaults.buttonColors(containerColor = c.ink, contentColor = c.onInk),
                            shape = RoundedCornerShape(10.dp), modifier = Modifier.heightIn(min = Dim.control),
                        ) { Text(if (step + 1 < TOUR.size) "Next" else "Done", style = UiType.label) }
                    }
                }
            }
            if (hole != null && above) Arrow(up = false, x = arrowX - sidePx, color = c.surface)
        }
    }
}

@Composable
private fun Arrow(up: Boolean, x: Float, color: androidx.compose.ui.graphics.Color) {
    androidx.compose.foundation.Canvas(Modifier.fillMaxWidth().height(10.dp)) {
        val w = 10.dp.toPx()
        val p = androidx.compose.ui.graphics.Path().apply {
            if (up) { moveTo(x - w, size.height); lineTo(x, 0f); lineTo(x + w, size.height) }
            else { moveTo(x - w, 0f); lineTo(x, size.height); lineTo(x + w, 0f) }
            close()
        }
        drawPath(p, color)
    }
}
