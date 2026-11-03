// Opening splash and the "you're ready" moment after the tour (owner 2026-11-03). Both reuse the
// lock's start sequence: the dial turns, then the shackle drops shut, here with a small burst.
package app.lockdin.ui

import androidx.compose.animation.core.Animatable
import androidx.compose.animation.core.FastOutSlowInEasing
import androidx.compose.animation.core.spring
import androidx.compose.animation.core.tween
import androidx.compose.foundation.Canvas
import androidx.compose.foundation.Image
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.alpha
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.graphics.ColorFilter
import androidx.compose.ui.graphics.drawscope.Stroke
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.res.painterResource
import androidx.compose.ui.semantics.LiveRegionMode
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.liveRegion
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp
import app.lockdin.R
import kotlinx.coroutines.delay
import kotlinx.coroutines.launch

/** Time from the start of the lock's sequence until the shackle hits shut (12 dial steps + drop). */
private const val SHUT_MS = 1_050L

/** Shown after the tour's Done. */
object ReadyMoment { var showing by mutableStateOf(false) }

/** The lock turning and snapping shut, with a ring that bursts out as it closes. */
@Composable
fun LockBurst(id: String, width: Dp, haptics: Boolean = false) {
    val c = LocalColors.current
    val ctx = LocalContext.current
    val reduced = remember { animationsOff(ctx.applicationContext) }
    val pop = remember { Animatable(1f) }
    val ring = remember { Animatable(0f) }
    LaunchedEffect(id) {
        if (reduced) return@LaunchedEffect
        delay(SHUT_MS)
        launch { ring.animateTo(1f, tween(520, easing = FastOutSlowInEasing)) }
        pop.animateTo(1.08f, tween(110))
        pop.animateTo(1f, spring(dampingRatio = 0.4f, stiffness = 600f))
    }
    Box(contentAlignment = Alignment.Center) {
        Canvas(Modifier.size(width * 1.6f)) {
            val t = ring.value
            if (t > 0f && t < 1f) {
                // Centred on the lock body (the lower part of the lock).
                val center = Offset(size.width / 2f, size.height / 2f + width.toPx() * 0.16f)
                drawCircle(c.ink.copy(alpha = 0.35f * (1f - t)), radius = width.toPx() * (0.42f + 0.45f * t), center = center,
                    style = Stroke((10f * (1f - t) + 1f).dp.toPx()))
            }
        }
        ClockLock(
            TimerState.READY, 0, 1, locked = true, label = "Lock closing", caption = "", text = "", width = width, introFor = id,
            haptics = haptics, modifier = Modifier.graphicsLayer { scaleX = pop.value; scaleY = pop.value },
        )
    }
}

/** "You're ready to lock in and take control", after the tour. */
@Composable
fun ReadyScreen(haptics: Boolean, onDone: () -> Unit) {
    val c = LocalColors.current
    val words = remember { Animatable(0f) }
    var canGo by remember { mutableStateOf(false) }
    LaunchedEffect(Unit) {
        delay(SHUT_MS + 150)
        words.animateTo(1f, tween(380))
        canGo = true
        delay(3_000)
        onDone()
    }
    Box(Modifier.fillMaxSize().background(c.ground).semantics { contentDescription = "Ready" }, contentAlignment = Alignment.Center) {
        Column(Modifier.fillMaxWidth().padding(horizontal = Dim.gutter), horizontalAlignment = Alignment.CenterHorizontally,
            verticalArrangement = Arrangement.spacedBy(Dim.s4)) {
            LockBurst("ready", 200.dp, haptics)
            Column(Modifier.alpha(words.value).graphicsLayer { translationY = (1f - words.value) * 24f }.semantics { liveRegion = LiveRegionMode.Polite },
                horizontalAlignment = Alignment.CenterHorizontally, verticalArrangement = Arrangement.spacedBy(Dim.s2)) {
                Text("You’re ready.", style = UiType.title, color = c.ink, textAlign = TextAlign.Center)
                Text("Time to lock in and take control.", style = UiType.body, color = c.inkMuted, textAlign = TextAlign.Center)
            }
            Spacer(Modifier.height(Dim.s2))
            Box(Modifier.alpha(if (canGo) 1f else 0f)) { MainButton("Let’s go", { if (canGo) onDone() }, modifier = Modifier.width(220.dp)) }
        }
    }
}

/** Plays once when the app opens: the lock shuts, the wordmark settles, then the app appears. */
object Splash { var played = false }

@Composable
fun SplashScreen(onDone: () -> Unit) {
    val c = LocalColors.current
    val ctx = LocalContext.current
    val reduced = remember { animationsOff(ctx.applicationContext) }
    val rise = remember { Animatable(0f) }
    val fade = remember { Animatable(1f) }
    LaunchedEffect(Unit) {
        if (reduced) { delay(250); onDone(); return@LaunchedEffect }
        delay(SHUT_MS + 80)
        rise.animateTo(1f, spring(dampingRatio = 0.7f, stiffness = 300f))
        delay(220)
        fade.animateTo(0f, tween(240))
        onDone()
    }
    Box(Modifier.fillMaxSize().alpha(fade.value).background(c.ground).semantics { contentDescription = "Lockd In" }, contentAlignment = Alignment.Center) {
        Column(horizontalAlignment = Alignment.CenterHorizontally) {
            LockBurst("splash", 150.dp)
            // After the lock shuts, the wordmark moves up a little closer and comes in.
            Image(
                painterResource(R.drawable.wordmark), contentDescription = null, colorFilter = ColorFilter.tint(c.ink),
                modifier = Modifier.padding(top = 28.dp).height(30.dp)
                    .graphicsLayer { translationY = (1f - rise.value) * 36f; alpha = 0.25f + 0.75f * rise.value; scaleX = 0.94f + 0.06f * rise.value; scaleY = scaleX },
            )
        }
    }
}
