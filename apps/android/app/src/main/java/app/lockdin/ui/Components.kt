package app.lockdin.ui

import androidx.compose.foundation.Canvas
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.BoxWithConstraints
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.aspectRatio
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.widthIn
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.geometry.CornerRadius
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.geometry.Rect
import androidx.compose.ui.geometry.Size
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.Path
import androidx.compose.ui.graphics.StrokeCap
import androidx.compose.ui.graphics.drawscope.Stroke
import androidx.compose.ui.graphics.lerp
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.semantics.clearAndSetSemantics
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp

/**
 * The padlock timer. Shackle open when nothing is restricted, closed during
 * focus. TalkBack reads one summary ("Focus 1 of 2: 23 minutes left") instead of
 * the ticking digits.
 */
@Composable
fun LockTimer(remainingMs: Long, caption: String, locked: Boolean, modifier: Modifier = Modifier, maxWidth: Dp = 200.dp, compact: Boolean = false) {
    val c = LocalColors.current
    val body = if (locked) lerp(c.taupe, c.ink, 0.18f) else c.taupe
    val shackleColor = if (locked) body else lerp(c.taupe, c.ground, 0.15f)
    val density = LocalDensity.current
    BoxWithConstraints(
        modifier
            .widthIn(max = maxWidth)
            .fillMaxWidth()
            .aspectRatio(240f / 260f)
            .clearAndSetSemantics { contentDescription = "$caption: ${Fmt.spokenRemaining(remainingMs)}" },
    ) {
        val w = this.maxWidth
        // Same geometry as the web lock (viewBox 0 36 240 260), measured from the approved reference.
        Canvas(Modifier.fillMaxSize()) {
            val s = size.width / 240f
            fun y(v: Float) = (v - 36f) * s
            val shackle = Path().apply {
                if (locked) {
                    moveTo(75.5f * s, y(136f)); lineTo(75.5f * s, y(108f))
                    arcTo(Rect(75.5f * s, y(63.5f), 164.5f * s, y(152.5f)), 180f, 180f, false)
                    lineTo(164.5f * s, y(136f))
                } else {
                    moveTo(75.5f * s, y(136f)); lineTo(75.5f * s, y(94f))
                    arcTo(Rect(75.5f * s, y(49.5f), 164.5f * s, y(138.5f)), 180f, 170f, false)
                }
            }
            drawPath(shackle, shackleColor, style = Stroke(width = 22 * s, cap = StrokeCap.Round))
            drawRoundRect(body, topLeft = Offset(14 * s, y(120f)), size = Size(212 * s, 172 * s), cornerRadius = CornerRadius(22 * s))
        }
        // Digits are sized to the lock, not to the font-size setting: TalkBack reads the
        // summary, and the digits must stay inside the body.
        val digits = with(density) { (w * (if (compact) 0.27f else 0.24f)).toSp() }
        val cap = with(density) { (w * 0.075f).toSp() }
        Column(
            Modifier.fillMaxSize().padding(top = w * (if (compact) 0.36f else 0.37f), bottom = w * 0.03f, start = w * 0.06f, end = w * 0.06f),
            horizontalAlignment = Alignment.CenterHorizontally,
            verticalArrangement = Arrangement.Center,
        ) {
            if (!compact) { ClockGlyph(c.ink, body, w * 0.15f); Spacer(Modifier.height(w * 0.02f)) }
            Text(Fmt.countdown(remainingMs), style = Type.timer.copy(fontWeight = androidx.compose.ui.text.font.FontWeight.Bold, fontSize = digits, lineHeight = digits * 1.05f), color = c.ink, maxLines = 1)
            if (!compact) Text(caption, style = Type.label.copy(fontWeight = androidx.compose.ui.text.font.FontWeight.SemiBold, fontSize = cap, lineHeight = cap * 1.25f), color = c.ink, textAlign = TextAlign.Center, maxLines = 2)
        }
    }
}

@Composable
private fun ClockGlyph(fill: Color, cut: Color, d: Dp) {
    Canvas(Modifier.size(d)) {
        val r = size.minDimension / 2
        drawCircle(fill, r)
        val c = Offset(r, r)
        drawLine(cut, c, Offset(r, r * 0.42f), strokeWidth = r * 0.16f, cap = StrokeCap.Round)
        drawLine(cut, c, Offset(r * 1.38f, r * 1.22f), strokeWidth = r * 0.16f, cap = StrokeCap.Round)
    }
}
