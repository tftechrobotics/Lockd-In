// Optional backgrounds and quotes (roadmap step 5, C2). Off by default. Scenes are the owner's own
// images (dark and light version of each, owner holds the rights, R46), faded into the theme's ground (80%, words 50%).
// The sculpted lock and the screen's text are never changed. All scenes are static: nothing moves.
package app.lockdin.ui

import android.graphics.BitmapFactory
import android.graphics.Paint
import android.graphics.Typeface
import androidx.compose.foundation.Canvas
import androidx.compose.foundation.Image
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.BoxScope
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.produceState
import androidx.compose.runtime.remember
import androidx.compose.ui.Modifier
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.geometry.Size
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.Path
import androidx.compose.ui.graphics.asImageBitmap
import androidx.compose.ui.graphics.drawscope.DrawScope
import androidx.compose.ui.graphics.drawscope.Stroke
import androidx.compose.ui.graphics.drawscope.drawIntoCanvas
import androidx.compose.ui.graphics.drawscope.rotate
import androidx.compose.ui.graphics.nativeCanvas
import androidx.compose.ui.graphics.toArgb
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.res.painterResource
import androidx.compose.ui.semantics.clearAndSetSemantics
import androidx.compose.ui.unit.dp
import app.lockdin.R
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import java.io.File
import kotlin.math.PI
import kotlin.math.cos
import kotlin.math.sin

enum class Backdrop(val label: String) {
    NONE("None"),
    LIBRARY("Library after hours"),
    MOUNTAINS("Quiet ambition"),
    WORDS("Words to work by"),
    BLUEPRINT("Your reason"),
    PHOTO("Your photo"),
}

const val DEFAULT_BACKDROP_WORDS = "One thing at a time"

object Quotes {
    /** Original short lines: encouraging, never shaming, no claims about how anyone feels. */
    val LINES = listOf(
        "One page at a time.",
        "Start small. Keep going.",
        "The hard part was starting. You’ve started.",
        "Slow is fine. Stopped is the only problem.",
        "This hour is yours.",
        "Do the next small thing.",
        "Progress, not perfect.",
        "Quiet mind, steady work.",
        "Future you will be glad you did this.",
        "Focus is a choice you can make again.",
        "Good work is built in short, honest blocks.",
        "You don’t need to finish. You need to continue.",
        "Make it simple, then make it done.",
        "Attention is the work.",
        "Nothing else needs you right now.",
        "Small steps, many times.",
        "Begin where you are.",
        "Keep the promise you made to yourself.",
        "Clear the next thing, then the next.",
        "Effort counts, even on slow days.",
    )

    /** The same line for the whole session (stable per session id). */
    fun forSession(sessionId: String): String = LINES[Math.floorMod(sessionId.hashCode(), LINES.size)]
}

fun photoFile(ctx: android.content.Context) = File(ctx.filesDir, "backdrop.jpg")

/** Content over the chosen background. With no background this is just `content`. */
@Composable
fun BackdropBox(choice: Backdrop, words: String = DEFAULT_BACKDROP_WORDS, content: @Composable BoxScope.() -> Unit) {
    if (choice == Backdrop.NONE) { Box(Modifier.fillMaxSize(), content = content); return }
    Box(Modifier.fillMaxSize()) {
        BackdropLayer(choice, words, Modifier.fillMaxSize())
        content()
    }
}

/** The background alone (also used, scaled down, for the picker's previews). */
@Composable
fun BackdropLayer(choice: Backdrop, words: String, modifier: Modifier) {
    val c = LocalColors.current
    val dark = c == DarkColors
    val ctx = LocalContext.current
    // The scene supplies its own ground: the screen behind it is transparent.
    Box(modifier.clearAndSetSemantics { }.background(if (choice == Backdrop.NONE) Color.Transparent else c.ground)) {
        when (choice) {
            Backdrop.NONE -> Unit
            Backdrop.LIBRARY, Backdrop.MOUNTAINS -> Scene(choice, dark)
            Backdrop.PHOTO -> {
                val bmp by produceState<android.graphics.Bitmap?>(null) {
                    value = withContext(Dispatchers.IO) { runCatching { BitmapFactory.decodeFile(photoFile(ctx).path) }.getOrNull() }
                }
                bmp?.let {
                    Image(it.asImageBitmap(), contentDescription = null, contentScale = ContentScale.Crop, modifier = Modifier.fillMaxSize())
                    Box(Modifier.fillMaxSize().background(c.ground.copy(alpha = if (dark) 0.72f else 0.8f)))
                }
            }
            // The owner's image for the default line; a person's own line is drawn in the same style.
            Backdrop.WORDS -> if (words.trim().ifEmpty { DEFAULT_BACKDROP_WORDS }.equals(DEFAULT_BACKDROP_WORDS, ignoreCase = true)) Scene(choice, dark)
                else Words(words, c.ink.copy(alpha = if (dark) 0.12f else 0.1f))
            Backdrop.BLUEPRINT -> Blueprint(c.ink.copy(alpha = if (dark) 0.24f else 0.2f))
        }
    }
}

@Composable
private fun Scene(choice: Backdrop, dark: Boolean) {
    val res = when (choice) {
        Backdrop.LIBRARY -> if (dark) R.drawable.bg_library else R.drawable.bg_library_light
        Backdrop.MOUNTAINS -> if (dark) R.drawable.bg_mountains else R.drawable.bg_mountains_light
        else -> if (dark) R.drawable.bg_words else R.drawable.bg_words_light
    }
    // Faded into the theme's ground so the scene stays in the background; the words fade most.
    val alpha = if (choice == Backdrop.WORDS) 0.5f else 0.8f
    Image(painterResource(res), contentDescription = null, contentScale = ContentScale.Crop, alpha = alpha, modifier = Modifier.fillMaxSize())
}

/**
 * Large condensed serif words behind the lock, split into two lines above and below it.
 * Uses the phone's own serif face, narrowed, so there's no font file to license.
 */
@Composable
private fun Words(text: String, color: Color) {
    val lines = remember(text) { splitLines(text.trim().ifEmpty { DEFAULT_BACKDROP_WORDS }.uppercase()) }
    Canvas(Modifier.fillMaxSize()) {
        val paint = Paint(Paint.ANTI_ALIAS_FLAG).apply {
            typeface = Typeface.create(Typeface.SERIF, Typeface.BOLD)
            textScaleX = 0.56f
            this.color = color.toArgb()
            textAlign = Paint.Align.CENTER
        }
        val slots = if (lines.size == 1) listOf(0.42f) else listOf(0.36f, 0.70f)
        lines.take(2).forEachIndexed { i, line ->
            // Fill ~94% of the width, but never taller than a fifth of the screen.
            paint.textSize = 100f
            val w = paint.measureText(line).coerceAtLeast(1f)
            paint.textSize = minOf(100f * size.width * 0.94f / w, size.height / 5f)
            drawIntoCanvas { it.nativeCanvas.drawText(line, size.width / 2, size.height * slots[i], paint) }
        }
    }
}

private fun splitLines(t: String): List<String> {
    val words = t.split(Regex("\\s+")).filter { it.isNotEmpty() }
    if (words.size <= 1 || t.length <= 9) return listOf(t)
    // Two lines of about equal length.
    var best = 1; var bestDiff = Int.MAX_VALUE
    for (k in 1 until words.size) {
        val diff = kotlin.math.abs(words.take(k).joinToString(" ").length - words.drop(k).joinToString(" ").length)
        if (diff < bestDiff) { bestDiff = diff; best = k }
    }
    return listOf(words.take(best).joinToString(" "), words.drop(best).joinToString(" "))
}

/** Blueprint-style drawings of things worth building: gears, an arm, a tracked base, a grid. */
@Composable
private fun Blueprint(ink: Color) {
    Canvas(Modifier.fillMaxSize()) {
        val u = size.width / 100f
        val thin = Stroke(0.7.dp.toPx())
        val line = Stroke(1.2.dp.toPx())
        // Fine grid
        val grid = ink.copy(alpha = ink.alpha * 0.35f)
        var x = 0f
        while (x < size.width) { drawLine(grid, Offset(x, 0f), Offset(x, size.height), 0.5.dp.toPx()); x += 8 * u }
        var y = 0f
        while (y < size.height) { drawLine(grid, Offset(0f, y), Offset(size.width, y), 0.5.dp.toPx()); y += 8 * u }
        // Top left: a robot arm (base, two links, gripper) with joint circles.
        arm(ink, line, thin, Offset(4 * u, 52 * u), u)
        // Right: a large gear with a smaller one meshing.
        gear(ink, line, Offset(92 * u, 70 * u), 22 * u, 18)
        gear(ink, line, Offset(66 * u, 50 * u), 11 * u, 10)
        // Bottom: a tracked base.
        track(ink, line, thin, Offset(8 * u, size.height - 40 * u), 84 * u, u)
        // Dimension line with arrows and a label.
        val dy = size.height - 46 * u
        drawLine(ink, Offset(8 * u, dy), Offset(92 * u, dy), 0.7.dp.toPx())
        arrow(ink, Offset(8 * u, dy), 1f, u); arrow(ink, Offset(92 * u, dy), -1f, u)
        drawIntoCanvas {
            val p = Paint(Paint.ANTI_ALIAS_FLAG).apply { color = ink.toArgb(); textSize = 2.6f * u; typeface = Typeface.MONOSPACE; textAlign = Paint.Align.CENTER }
            it.nativeCanvas.drawText("420.0", 50 * u, dy - 1.2f * u, p)
            p.textAlign = Paint.Align.LEFT
            it.nativeCanvas.drawText("REV A  ·  SCALE 1:4", 6 * u, size.height - 8 * u, p)
        }
    }
}

private fun DrawScope.gear(ink: Color, s: Stroke, c: Offset, r: Float, teeth: Int) {
    val p = Path()
    val n = teeth * 4
    for (i in 0..n) {
        val a = 2 * PI * i / n
        val rr = if ((i / 2) % 2 == 0) r else r * 0.86f
        val pt = Offset(c.x + (rr * cos(a)).toFloat(), c.y + (rr * sin(a)).toFloat())
        if (i == 0) p.moveTo(pt.x, pt.y) else p.lineTo(pt.x, pt.y)
    }
    p.close()
    drawPath(p, ink, style = s)
    drawCircle(ink, r * 0.55f, c, style = s)
    drawCircle(ink, r * 0.14f, c, style = s)
    for (k in 0 until 5) {
        val a = 2 * PI * k / 5
        drawCircle(ink, r * 0.09f, Offset(c.x + (r * 0.36f * cos(a)).toFloat(), c.y + (r * 0.36f * sin(a)).toFloat()), style = s)
    }
}

private fun DrawScope.arm(ink: Color, s: Stroke, thin: Stroke, base: Offset, u: Float) {
    drawRect(ink, Offset(base.x, base.y), Size(16 * u, 5 * u), style = s)
    val j0 = Offset(base.x + 8 * u, base.y)
    val j1 = Offset(j0.x + 10 * u, j0.y - 22 * u)
    val j2 = Offset(j1.x + 20 * u, j1.y - 8 * u)
    fun link(a: Offset, b: Offset, w: Float) {
        val ang = kotlin.math.atan2(b.y - a.y, b.x - a.x) * 180f / PI.toFloat()
        val len = kotlin.math.hypot(b.x - a.x, b.y - a.y)
        rotate(ang, pivot = a) { drawRoundRect(ink, Offset(a.x, a.y - w / 2), Size(len, w), androidx.compose.ui.geometry.CornerRadius(w / 2), style = s) }
    }
    link(j0, j1, 4 * u); link(j1, j2, 3 * u)
    listOf(j0, j1, j2).forEach { drawCircle(ink, 2.6f * u, it, style = s); drawCircle(ink, 0.9f * u, it, style = thin) }
    // Gripper
    drawLine(ink, j2, Offset(j2.x + 5 * u, j2.y - 3 * u), s.width); drawLine(ink, j2, Offset(j2.x + 5 * u, j2.y + 3 * u), s.width)
}

private fun DrawScope.track(ink: Color, s: Stroke, thin: Stroke, at: Offset, w: Float, u: Float) {
    val h = 14 * u
    drawRoundRect(ink, at, Size(w, h), androidx.compose.ui.geometry.CornerRadius(h / 2), style = s)
    val wheels = 6
    for (i in 0 until wheels) {
        val cx = at.x + h / 2 + i * (w - h) / (wheels - 1)
        drawCircle(ink, h * 0.36f, Offset(cx, at.y + h / 2), style = s)
        drawCircle(ink, h * 0.1f, Offset(cx, at.y + h / 2), style = thin)
    }
    var x = at.x + h / 2
    while (x < at.x + w - h / 2) { drawLine(ink, Offset(x, at.y), Offset(x, at.y - 1.2f * u), thin.width); x += 3 * u }
}

private fun DrawScope.arrow(ink: Color, tip: Offset, dir: Float, u: Float) {
    drawLine(ink, tip, Offset(tip.x + dir * 2 * u, tip.y - 1.2f * u), 0.7.dp.toPx())
    drawLine(ink, tip, Offset(tip.x + dir * 2 * u, tip.y + 1.2f * u), 0.7.dp.toPx())
}
