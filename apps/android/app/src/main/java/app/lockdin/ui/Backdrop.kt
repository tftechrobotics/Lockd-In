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
import androidx.compose.runtime.setValue
import androidx.compose.runtime.produceState
import androidx.compose.runtime.remember
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.blur
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
    PHOTO("Your photo"),
}

/** True only while a session view shows the lock closed (blocking confirmed). Backgrounds appear only then. */
object BackdropGate { var locked by androidx.compose.runtime.mutableStateOf(false) }

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
fun BackdropBox(choice: Backdrop, words: String = DEFAULT_BACKDROP_WORDS, frosted: Boolean = false, content: @Composable BoxScope.() -> Unit) {
    if (choice == Backdrop.NONE) { Box(Modifier.fillMaxSize(), content = content); return }
    Box(Modifier.fillMaxSize()) {
        // Frosted (Settings, R58): blurred where the phone supports it (Android 12+) and dimmed
        // further everywhere so rows stay readable over the glass plates.
        BackdropLayer(choice, words, Modifier.fillMaxSize().then(if (frosted) Modifier.blur(14.dp) else Modifier))
        if (frosted) Box(Modifier.fillMaxSize().background(LocalColors.current.ground.copy(alpha = 0.3f)))
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
