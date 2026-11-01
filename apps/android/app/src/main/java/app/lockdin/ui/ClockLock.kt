// The circular lock timer (approved R36), drawn natively. Same geometry as the shared interface's
// CircleLock (apps/web/src/ui/Timer.tsx): round body = clock face with 60 ticks, a substantial
// shackle above, centred digits. Elapsed ticks dim clockwise from 12. The shackle drops shut only
// when `locked` is true, which callers pass only for verified, applied restriction.
package app.lockdin.ui

import androidx.compose.animation.core.CubicBezierEasing
import androidx.compose.animation.core.animateFloatAsState
import androidx.compose.animation.core.tween
import androidx.compose.foundation.Canvas
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.BoxWithConstraints
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.aspectRatio
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.width
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.BiasAlignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.geometry.Rect
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.Path
import androidx.compose.ui.graphics.StrokeCap
import androidx.compose.ui.graphics.drawscope.translate
import androidx.compose.ui.graphics.lerp
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.semantics.clearAndSetSemantics
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp
import kotlinx.coroutines.delay
import kotlin.math.cos
import kotlin.math.floor
import kotlin.math.sin
import kotlin.math.sqrt

/** Lock colours (web: --lock-frame, --lock-body, --lock-track, --lock-arc, --lock-arc-break). */
private data class LockColors(val frame: Color, val body: Color, val track: Color, val arc: Color, val arcBreak: Color)

private val LightLock = LockColors(Color(0xFFABA095), Color(0xFFECE5DA), Color(0x21302A25), Color(0xFF74685D), Color(0xFFB8AC9F))
private val DarkLock = LockColors(Color(0xFF6F655B), Color(0xFF2B251F), Color(0x24F0E8DB), Color(0xFFD2C4B3), Color(0xFF85796D))

// Geometry in the reference's units (viewBox 240 wide).
private const val W = 240f
private const val CX = 120f
private const val CY = 232f
private const val R = 108f
private const val RIM = 7f
private const val RO = 74.5f
private const val STROKE = 21f
private const val TOP = 40f
private const val LIFT = 36f
private const val TICKS = 60
private const val MIN_Y = TOP - LIFT - 4f            // 0
private const val H = CY + R + 4f - MIN_Y             // 344

private fun shacklePath(s: Float): Path {
    val ri = RO - STROKE
    val cap = STROKE / 2
    val arcY = TOP + RO
    val yo = CY - sqrt(R * R - RO * RO)
    val tip = yo + cap
    fun x(v: Float) = v * s
    fun y(v: Float) = (v - MIN_Y) * s
    return Path().apply {
        moveTo(x(CX - RO), y(CY))
        lineTo(x(CX - RO), y(arcY))
        arcTo(Rect(x(CX - RO), y(arcY - RO), x(CX + RO), y(arcY + RO)), 180f, 180f, false)
        lineTo(x(CX + RO), y(tip))
        val capCx = CX + (RO + ri) / 2
        arcTo(Rect(x(capCx - cap), y(tip - cap), x(capCx + cap), y(tip + cap)), 0f, 180f, false)
        lineTo(x(CX + ri), y(arcY))
        arcTo(Rect(x(CX - ri), y(arcY - ri), x(CX + ri), y(arcY + ri)), 0f, -180f, false)
        lineTo(x(CX - ri), y(CY))
        close()
    }
}

/**
 * @param text face text in place of the countdown (the idle lock shows the time of day).
 * @param label spoken summary, minute-level ("Focus: 23 minutes left"); the ticking digits are hidden from TalkBack.
 */
@Composable
fun ClockLock(
    state: TimerState,
    remainingMs: Long,
    totalMs: Long,
    locked: Boolean,
    label: String,
    caption: String,
    modifier: Modifier = Modifier,
    width: Dp = 220.dp,
    text: String? = null,
) {
    val c = LocalColors.current
    val lc = if (c == DarkColors) DarkLock else LightLock
    val f = if (state == TimerState.DONE) 0f else if (totalMs > 0) (remainingMs.toFloat() / totalMs).coerceIn(0f, 1f) else 0f
    val idle = text != null && state == TimerState.READY
    val elapsed = minOf(TICKS, floor((1 - f) * TICKS + 1e-6f).toInt())
    // Starts open and drops shut a moment after appearing, so locking is seen happening.
    var settled by remember { mutableStateOf(false) }
    LaunchedEffect(Unit) { delay(350); settled = true }
    val closed by animateFloatAsState(
        if (locked && settled) 1f else 0f,
        tween(520, easing = CubicBezierEasing(0.5f, 0f, 0.2f, 1.35f)), label = "shackle",
    )
    val bodyColor = if (state == TimerState.BREAK) lerp(lc.body, c.ground, 0.3f) else lc.body
    val leftColor = if (state == TimerState.BREAK) lc.arcBreak else lc.arc

    BoxWithConstraints(
        modifier.width(width).aspectRatio(W / H).clearAndSetSemantics { contentDescription = label },
    ) {
        val s = with(LocalDensity.current) { maxWidth.toPx() } / W
        val shackle = remember(s) { shacklePath(s) }
        Canvas(Modifier.fillMaxSize()) {
            translate(top = -LIFT * s * (1 - closed)) { drawPath(shackle, lc.frame) }
            val center = Offset(CX * s, (CY - MIN_Y) * s)
            drawCircle(lc.frame, R * s, center)
            drawCircle(bodyColor, (R - RIM) * s, center)
            for (i in 0 until TICKS) {
                val a = i.toFloat() / TICKS * 2 * Math.PI.toFloat()
                val hour = i % 5 == 0
                val r1 = if (hour) 75f else 81f
                val r2 = 91f
                val dx = sin(a)
                val dy = -cos(a)
                drawLine(
                    color = if (idle || i >= elapsed) leftColor else lc.track,
                    start = Offset(center.x + dx * r1 * s, center.y + dy * r1 * s),
                    end = Offset(center.x + dx * r2 * s, center.y + dy * r2 * s),
                    strokeWidth = (if (hour) 3.5f else 2.5f) * s,
                    cap = StrokeCap.Round,
                )
            }
        }
        val px = maxWidth / W // dp per unit
        val digits = with(LocalDensity.current) { (52f * px.value).dp.toSp() }
        val small = with(LocalDensity.current) { (14f * px.value).dp.toSp() }
        Column(
            Modifier.align(BiasAlignment(0f, 2 * ((CY - MIN_Y + 8) / H) - 1)),
            horizontalAlignment = Alignment.CenterHorizontally,
        ) {
            Text(
                text ?: if (state == TimerState.DONE) "Done" else Fmt.countdown(remainingMs),
                style = Type.timer.copy(fontSize = digits, lineHeight = digits, fontWeight = FontWeight.Medium, letterSpacing = (-1).dp.let { with(LocalDensity.current) { it.toSp() } }),
                color = if (state == TimerState.DONE || (state == TimerState.READY && text == null)) c.inkMuted else c.ink,
                textAlign = TextAlign.Center, maxLines = 1,
            )
            if (maxWidth >= 130.dp) Text(
                caption, style = UiType.caption.copy(fontSize = small, lineHeight = small, fontWeight = FontWeight.Medium, letterSpacing = with(LocalDensity.current) { (0.56f * px.value).dp.toSp() }),
                color = c.inkMuted, maxLines = 1,
            )
        }
    }
}
