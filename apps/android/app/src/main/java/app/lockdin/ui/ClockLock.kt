// The circular lock timer (approved R36; "Sculpted dial" finish, owner 2026-11-01), drawn natively.
// Same geometry as the shared interface's CircleLock (apps/web/src/ui/Timer.tsx): round body =
// clock face with 60 ticks, a substantial shackle above, centred digits. Elapsed ticks dim
// clockwise from 12. The shackle is shut only when `locked` is true, which callers pass only for
// verified, applied restriction.
//
// Start sequence (R42): when the person starts a session in the app, the tick ring turns one full
// revolution in 12 clicks with clock hands painted on it (the digits are hidden), the hands fade
// into the countdown, and — only once blocking is confirmed — the shackle drops shut with a
// short settle. The digits never move and always show the real session clock. It plays once per
// manual start (see StartIntro) and never for scheduled starts, reopening, rotation or breaks.
package app.lockdin.ui

import androidx.compose.animation.core.tween
import androidx.compose.foundation.Canvas
import kotlinx.coroutines.launch
import androidx.compose.ui.draw.alpha
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.LocalView
import androidx.compose.ui.graphics.toArgb
import androidx.compose.ui.graphics.asAndroidPath
import androidx.compose.ui.graphics.nativeCanvas
import androidx.compose.ui.graphics.drawscope.drawIntoCanvas
import androidx.compose.ui.graphics.drawscope.rotate
import androidx.compose.ui.graphics.drawscope.Stroke
import androidx.compose.ui.graphics.Brush
import androidx.compose.animation.core.spring
import androidx.compose.animation.core.FastOutSlowInEasing
import androidx.compose.animation.core.Animatable
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
 * One-shot start-sequence requests, in process memory only: set when the person presses Start in
 * the app, consumed by the first session screen that shows that session. Rotation, reopening,
 * restoring after process death and scheduled starts therefore never play it.
 */
object StartIntro {
    private var pending: String? = null
    /** Sequences started in this process (tests). */
    @Volatile var plays = 0
        private set

    fun request(sessionId: String) { pending = sessionId }

    fun consume(sessionId: String): Boolean {
        if (pending != sessionId) return false
        pending = null
        plays++
        return true
    }
}

/** True when the person turned animations off (Settings › Accessibility › Remove animations). */
fun animationsOff(ctx: android.content.Context): Boolean =
    runCatching { android.provider.Settings.Global.getFloat(ctx.contentResolver, android.provider.Settings.Global.ANIMATOR_DURATION_SCALE, 1f) == 0f }
        .getOrDefault(false)

private const val STEPS = 12
private const val STEP_MS = 62
private const val STEP_GAP_MS = 14L

/**
 * @param text face text in place of the countdown (the idle lock shows the time of day).
 * @param label spoken summary, minute-level ("Focus: 23 minutes left"); the ticking digits are hidden from TalkBack.
 * @param introFor session id when the start sequence should play (manual start only), else null.
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
    introFor: String? = null,
    haptics: Boolean = false,
    sound: Boolean = false,
    reducedMotion: Boolean? = null, // null = follow the phone's Remove animations setting
) {
    val c = LocalColors.current
    val dark = c == DarkColors
    val lc = if (dark) DarkLock else LightLock
    val f = if (state == TimerState.DONE) 0f else if (totalMs > 0) (remainingMs.toFloat() / totalMs).coerceIn(0f, 1f) else 0f
    val idle = text != null && state == TimerState.READY
    val elapsed = minOf(TICKS, floor((1 - f) * TICKS + 1e-6f).toInt())
    val view = LocalView.current
    val ctx = LocalContext.current

    // Shackle: 0 open, 1 shut. Without a start sequence it shows the true state directly.
    val dial = remember { Animatable(0f) }
    val shackleAnim = remember { Animatable(if (introFor == null && locked) 1f else 0f) }
    val face = remember { Animatable(if (introFor == null) 1f else 0f) }
    // Clock hands shown on the dial only while it turns; they fade into the countdown.
    val hands = remember { Animatable(0f) }
    var turning by remember { mutableStateOf(introFor != null) }
    var introClosed by remember { mutableStateOf(false) }
    val startedAt = remember { android.os.SystemClock.uptimeMillis() }
    val reduced = remember { reducedMotion ?: animationsOff(ctx) }

    fun click() {
        if (haptics) view.performHapticFeedback(android.view.HapticFeedbackConstants.CLOCK_TICK)
        if (sound) view.playSoundEffect(android.view.SoundEffectConstants.CLICK)
    }
    fun thunk() {
        if (haptics) view.performHapticFeedback(
            if (android.os.Build.VERSION.SDK_INT >= 30) android.view.HapticFeedbackConstants.CONFIRM else android.view.HapticFeedbackConstants.VIRTUAL_KEY,
        )
        if (sound) view.playSoundEffect(android.view.SoundEffectConstants.CLICK)
    }

    LaunchedEffect(introFor) {
        if (introFor == null) return@LaunchedEffect
        android.util.Log.i("LockdInIntro", "played session=$introFor reduced=$reduced")
        if (reduced) {
            turning = false
            face.animateTo(1f, tween(220))
        } else {
            hands.snapTo(1f)
            for (i in 1..STEPS) {
                dial.animateTo(i * 360f / STEPS, tween(STEP_MS, easing = FastOutSlowInEasing))
                click()
                delay(STEP_GAP_MS)
            }
            dial.snapTo(0f) // a full turn looks identical; keeps later drawing simple
            turning = false // the shackle may drop now, while the hands give way to the digits
            launch { hands.animateTo(0f, tween(280)) }
            face.animateTo(1f, tween(320, delayMillis = 60))
        }
    }

    // After the turn, the shackle follows the verified state. It drops shut with a settle only
    // the first time during the start sequence; every other change is shown directly.
    LaunchedEffect(locked, turning) {
        if (turning) return@LaunchedEffect
        val target = if (locked) 1f else 0f
        if (introFor != null && locked && !introClosed && !reduced) {
            introClosed = true
            val late = android.os.SystemClock.uptimeMillis() - startedAt > 4_000
            var hit = false
            shackleAnim.animateTo(1f, spring(dampingRatio = 0.45f, stiffness = 700f)) {
                if (!hit && value >= 1f) { hit = true; if (!late) thunk() }
            }
            android.util.Log.i("LockdInIntro", "shackle_closed session=$introFor after_ms=${android.os.SystemClock.uptimeMillis() - startedAt}")
        } else {
            if (introFor != null && locked && !introClosed) {
                introClosed = true
                thunk()
                android.util.Log.i("LockdInIntro", "shackle_closed session=$introFor reduced=true")
            }
            shackleAnim.snapTo(target)
        }
    }

    val closed = shackleAnim.value
    val bodyColor = if (state == TimerState.BREAK) lerp(lc.body, c.ground, 0.3f) else lc.body
    val leftColor = if (state == TimerState.BREAK) lc.arcBreak else lc.arc
    val shadowPaint = remember { android.graphics.Paint(android.graphics.Paint.ANTI_ALIAS_FLAG) }

    BoxWithConstraints(
        modifier.width(width).aspectRatio(W / H).clearAndSetSemantics { contentDescription = label },
    ) {
        val s = with(LocalDensity.current) { maxWidth.toPx() } / W
        val shackle = remember(s) { shacklePath(s) }
        val shackleNative = remember(shackle) { shackle.asAndroidPath() }
        Canvas(Modifier.fillMaxSize()) {
            val center = Offset(CX * s, (CY - MIN_Y) * s)
            val hiFrame = lerp(lc.frame, Color.White, if (dark) 0.14f else 0.32f)
            val loFrame = lerp(lc.frame, Color.Black, if (dark) 0.28f else 0.14f)
            // Shackle: matte, lit from the upper left, with a soft shadow to the lower right.
            translate(top = -LIFT * s * (1 - closed)) {
                drawIntoCanvas { cv ->
                    shadowPaint.color = lc.frame.toArgb()
                    shadowPaint.setShadowLayer(7f * s, 2.5f * s, 4f * s, Color.Black.copy(alpha = if (dark) 0.45f else 0.13f).toArgb())
                    cv.nativeCanvas.drawPath(shackleNative, shadowPaint)
                }
                drawPath(shackle, Brush.linearGradient(
                    listOf(hiFrame, lc.frame, loFrame),
                    start = Offset((CX - RO) * s, (TOP - MIN_Y) * s), end = Offset((CX + RO) * s, (TOP + RO - MIN_Y) * s),
                ))
            }
            // Dial: soft lower-right shadow, a rim lit from the upper left, a matte face.
            drawIntoCanvas { cv ->
                shadowPaint.color = lc.frame.toArgb()
                shadowPaint.setShadowLayer(18f * s, 5f * s, 8f * s, Color.Black.copy(alpha = if (dark) 0.5f else 0.16f).toArgb())
                cv.nativeCanvas.drawCircle(center.x, center.y, R * s, shadowPaint)
            }
            drawCircle(Brush.linearGradient(
                listOf(hiFrame, lc.frame, loFrame),
                start = Offset(center.x - R * s, center.y - R * s), end = Offset(center.x + R * s, center.y + R * s),
            ), R * s, center)
            // Rim highlight: a fine light edge on the upper left only.
            drawCircle(
                Brush.sweepGradient(
                    0f to Color.Transparent, 0.45f to Color.Transparent,
                    0.625f to Color.White.copy(alpha = if (dark) 0.16f else 0.6f),
                    0.8f to Color.Transparent, 1f to Color.Transparent, center = center,
                ),
                R * s - 1.1f * s, center, style = Stroke(1.6f * s),
            )
            val faceR = (R - RIM) * s
            drawCircle(Brush.radialGradient(
                0f to lerp(bodyColor, Color.White, if (dark) 0.06f else 0.38f),
                0.6f to bodyColor,
                1f to lerp(bodyColor, Color.Black, if (dark) 0.2f else 0.06f),
                center = Offset(center.x - 30f * s, center.y - 36f * s), radius = faceR * 1.4f,
            ), faceR, center)
            // The face sits just below the rim: a faint shade along its upper-left edge.
            drawCircle(
                Brush.sweepGradient(
                    0f to Color.Transparent, 0.42f to Color.Transparent,
                    0.625f to Color.Black.copy(alpha = if (dark) 0.3f else 0.09f),
                    0.83f to Color.Transparent, 1f to Color.Transparent, center = center,
                ),
                faceR - 1f * s, center, style = Stroke(2.2f * s),
            )
            // Tick ring: the only layer the start sequence turns. The 12 o'clock index is slightly
            // heavier so the turn reads like a combination dial.
            rotate(dial.value, pivot = center) {
                for (i in 0 until TICKS) {
                    val a = i.toFloat() / TICKS * 2 * Math.PI.toFloat()
                    val hour = i % 5 == 0
                    val r1 = if (i == 0) 72f else if (hour) 75f else 81f
                    val r2 = 91f
                    val dx = sin(a)
                    val dy = -cos(a)
                    drawLine(
                        color = if (idle || i >= elapsed) leftColor else lc.track,
                        start = Offset(center.x + dx * r1 * s, center.y + dy * r1 * s),
                        end = Offset(center.x + dx * r2 * s, center.y + dy * r2 * s),
                        strokeWidth = (if (i == 0) 4.5f else if (hour) 3.5f else 2.5f) * s,
                        cap = StrokeCap.Round,
                    )
                }
                // Hands painted on the turning dial (start sequence only): minute hand at 12,
                // hour hand at 10, so it reads as a clock face clicking round.
                if (hands.value > 0f) {
                    val ink = c.ink.copy(alpha = 0.88f * hands.value)
                    fun hand(deg: Float, len: Float, w: Float) {
                        val a = Math.toRadians(deg.toDouble()).toFloat()
                        drawLine(ink, Offset(center.x - sin(a) * 9f * s, center.y + cos(a) * 9f * s),
                            Offset(center.x + sin(a) * len * s, center.y - cos(a) * len * s), w * s, StrokeCap.Round)
                    }
                    hand(300f, 40f, 6.5f)
                    hand(0f, 62f, 4.5f)
                    drawCircle(ink, 6f * s, center)
                    drawCircle(bodyColor.copy(alpha = hands.value), 2.2f * s, center)
                }
            }
        }
        val px = maxWidth / W // dp per unit
        val showCaption = maxWidth >= 130.dp
        val digits = with(LocalDensity.current) { (52f * px.value).dp.toSp() }
        val small = with(LocalDensity.current) { (14f * px.value).dp.toSp() }
        Column(
            Modifier.align(BiasAlignment(0f, 2 * ((CY - MIN_Y + 8) / H) - 1)).then(if (face.value < 1f) Modifier.alpha(face.value) else Modifier),
            horizontalAlignment = Alignment.CenterHorizontally,
        ) {
            Text(
                text ?: if (state == TimerState.DONE) "Done" else Fmt.countdown(remainingMs),
                style = Type.timer.copy(fontSize = digits, lineHeight = digits, fontWeight = FontWeight.Medium, letterSpacing = (-1).dp.let { with(LocalDensity.current) { it.toSp() } }),
                color = if (state == TimerState.DONE || (state == TimerState.READY && text == null)) c.inkMuted else c.ink,
                textAlign = TextAlign.Center, maxLines = 1,
            )
            if (showCaption) Text(
                caption, style = UiType.caption.copy(fontSize = small, lineHeight = small, fontWeight = FontWeight.Medium, letterSpacing = with(LocalDensity.current) { (0.56f * px.value).dp.toSp() }),
                color = c.inkMuted, maxLines = 1,
            )
        }
    }
}
