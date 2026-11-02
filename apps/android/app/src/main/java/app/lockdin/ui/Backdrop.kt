// Optional backgrounds and quotes (roadmap step 5, C2). Both are off by default; the plain
// cream/espresso design and the sculpted lock stay the default and are never changed by them.
// Backgrounds are static (drawn once, no animation). A photo sits under a scrim so text keeps
// its contrast. Quotes are short original lines, one per session, never rotating in a round.
package app.lockdin.ui

import android.graphics.BitmapFactory
import androidx.compose.foundation.Canvas
import androidx.compose.foundation.Image
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.BoxScope
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.produceState
import androidx.compose.ui.Modifier
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.graphics.Path
import androidx.compose.ui.graphics.asImageBitmap
import androidx.compose.ui.graphics.drawscope.Stroke
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.semantics.clearAndSetSemantics
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import java.io.File
import kotlin.math.PI
import kotlin.math.sin

enum class Backdrop(val label: String) { NONE("None"), PAPER("Paper"), LINEN("Linen"), CONTOURS("Contours"), HILLS("Hills"), PHOTO("Your photo") }

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
fun BackdropBox(choice: Backdrop, content: @Composable BoxScope.() -> Unit) {
    if (choice == Backdrop.NONE) { Box(Modifier.fillMaxSize(), content = content); return }
    val c = LocalColors.current
    val ctx = LocalContext.current
    Box(Modifier.fillMaxSize()) {
        Box(Modifier.fillMaxSize().clearAndSetSemantics { }) {
            when (choice) {
                Backdrop.PHOTO -> {
                    val bmp by produceState<android.graphics.Bitmap?>(null) {
                        value = withContext(Dispatchers.IO) { runCatching { BitmapFactory.decodeFile(photoFile(ctx).path) }.getOrNull() }
                    }
                    bmp?.let {
                        Image(it.asImageBitmap(), contentDescription = null, contentScale = ContentScale.Crop, modifier = Modifier.fillMaxSize())
                        // Scrim: keeps text at the same contrast as the plain design.
                        Box(Modifier.fillMaxSize().background(c.ground.copy(alpha = 0.82f)))
                    }
                }
                else -> Pattern(choice)
            }
        }
        content()
    }
}

/** Static, low-contrast patterns in the theme's own ink: drawn once, never animated. */
@Composable
private fun Pattern(choice: Backdrop) {
    val ink = LocalColors.current.ink
    val taupe = LocalColors.current.inkMuted
    Canvas(Modifier.fillMaxSize()) {
        when (choice) {
            Backdrop.PAPER -> {
                var seed = 0x2545F491L
                fun rnd(): Float { seed = (seed * 6364136223846793005L + 1442695040888963407L); return ((seed ushr 33).toInt() and 0xFFFF) / 65535f }
                repeat(((size.width * size.height) / 900f).toInt().coerceAtMost(4000)) {
                    drawCircle(ink.copy(alpha = 0.05f), radius = 0.6.dp.toPx() + rnd() * 0.5.dp.toPx(), center = Offset(rnd() * size.width, rnd() * size.height))
                }
            }
            Backdrop.LINEN -> {
                val step = 7.dp.toPx(); val col = ink.copy(alpha = 0.035f); val w = 1.dp.toPx()
                var x = -size.height
                while (x < size.width) {
                    drawLine(col, Offset(x, 0f), Offset(x + size.height, size.height), w)
                    drawLine(col, Offset(x + size.height, 0f), Offset(x, size.height), w)
                    x += step
                }
            }
            Backdrop.CONTOURS -> {
                val col = ink.copy(alpha = 0.06f)
                for (k in 0 until 14) {
                    val p = Path()
                    val base = size.height * (k + 0.5f) / 14f
                    var x = 0f
                    p.moveTo(0f, base)
                    while (x <= size.width) {
                        val y = base + sin((x / size.width * 2 * PI + k * 0.7).toFloat()) * 18.dp.toPx() + sin((x / size.width * 5 * PI + k).toFloat()) * 6.dp.toPx()
                        p.lineTo(x, y); x += 8f
                    }
                    drawPath(p, col, style = Stroke(1.2.dp.toPx()))
                }
            }
            Backdrop.HILLS -> {
                fun hill(top: Float, amp: Float, phase: Float, alpha: Float) {
                    val p = Path(); p.moveTo(0f, size.height)
                    var x = 0f
                    while (x <= size.width) { p.lineTo(x, top + sin((x / size.width * PI * 1.6 + phase).toFloat()) * amp); x += 6f }
                    p.lineTo(size.width, size.height); p.close()
                    drawPath(p, taupe.copy(alpha = alpha))
                }
                hill(size.height * 0.78f, 26.dp.toPx(), 0.4f, 0.07f)
                hill(size.height * 0.86f, 18.dp.toPx(), 2.1f, 0.09f)
            }
            else -> Unit
        }
    }
}
