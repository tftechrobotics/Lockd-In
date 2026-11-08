package app.lockdin.ui

import androidx.compose.foundation.Canvas
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.BoxWithConstraints
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
import androidx.compose.ui.semantics.LiveRegionMode
import androidx.compose.ui.semantics.clearAndSetSemantics
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.liveRegion
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.text.TextStyle
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import app.lockdin.engine.Phase
import app.lockdin.engine.PhaseKind
import java.time.ZoneId

/*
 * The session timer identity (design pass 4, direction B): display numerals with a small
 * lock detail and a session rail. The lock closes only when restriction is verified on
 * this phone, never just because a focus round is running.
 */

enum class TimerState { READY, FOCUS, BREAK, DONE }

object TimerType {
    val numerals = TextStyle(fontFamily = BricolageDigits, fontWeight = FontWeight.SemiBold, fontFeatureSettings = "tnum", letterSpacing = (-1).sp)
    val question = TextStyle(fontFamily = Bricolage, fontWeight = FontWeight.SemiBold, fontSize = 19.sp, lineHeight = 24.sp)
    val task = TextStyle(fontFamily = Bricolage, fontWeight = FontWeight.SemiBold, fontSize = 19.sp, lineHeight = 24.sp)
    val bigField = TextStyle(fontFamily = Archivo, fontWeight = FontWeight.Normal, fontSize = 19.sp, lineHeight = 26.sp)
    val status = Type.caption.copy(fontWeight = FontWeight.SemiBold)
}

/** Break colour: taupe pulled toward ink, so breaks read as quieter than focus. */
@Composable
fun breakTone(): Color = LocalColors.current.let { lerp(it.taupe, it.ink, 0.45f) }

@Composable
fun MiniLock(closed: Boolean, tint: Color, size: Dp = 16.dp) {
    Canvas(Modifier.size(size)) {
        val u = this.size.width / 24f
        val p = Path().apply {
            moveTo(7.5f * u, 11 * u)
            if (closed) { lineTo(7.5f * u, 8 * u); arcTo(Rect(7.5f * u, 3.5f * u, 16.5f * u, 12.5f * u), 180f, 180f, false); lineTo(16.5f * u, 11 * u) }
            else { lineTo(7.5f * u, 6.5f * u); arcTo(Rect(7.5f * u, 2f * u, 16.5f * u, 11f * u), 180f, 180f, false) }
        }
        drawPath(p, tint, style = Stroke(2 * u, cap = StrokeCap.Round))
        drawRoundRect(tint, Offset(4.5f * u, 11 * u), Size(15 * u, 10 * u), CornerRadius(3 * u))
    }
}

/** "Focusing · round 1 of 2" with the lock detail. Announced politely when it changes. */
@Composable
fun TimerStatus(state: TimerState, closed: Boolean, text: String) {
    val c = LocalColors.current
    val tint = if (state == TimerState.FOCUS) c.ink else c.inkMuted
    Row(
        Modifier.heightIn(min = 24.dp).semantics(mergeDescendants = true) { liveRegion = LiveRegionMode.Polite },
        verticalAlignment = Alignment.CenterVertically,
    ) {
        // The icon follows the text size so it stays legible at large font scales.
        val icon = with(LocalDensity.current) { (16 * fontScale).dp }
        MiniLock(closed, tint, icon)
        Spacer(Modifier.width(Dim.s2))
        Text(text, style = TimerType.status, color = tint)
    }
}

/**
 * The countdown. Sized from the available width rather than the font scale, so large text
 * never pushes it off-screen (it is already far larger than body text). TalkBack reads
 * [label] (minute-level), not the ticking digits.
 */
@Composable
fun Numerals(state: TimerState, remainingMs: Long, label: String, max: Dp = 104.dp) {
    val c = LocalColors.current
    val color = when (state) { TimerState.BREAK -> breakTone(); TimerState.FOCUS -> c.ink; else -> c.inkMuted }
    BoxWithConstraints(Modifier.fillMaxWidth().clearAndSetSemantics { contentDescription = label }) {
        val d = LocalDensity.current
        val px = minOf(max, maxWidth * 0.31f)
        val size = with(d) { px.toSp() }
        Text(
            if (state == TimerState.DONE) "Done" else Fmt.countdown(remainingMs),
            style = TimerType.numerals.copy(fontSize = size, lineHeight = size * 1.04f),
            color = color, maxLines = 1,
        )
    }
}

/** The session as a rail: widths follow minutes, the elapsed part is filled, breaks are dashed. */
@Composable
fun SessionRail(phases: List<Phase>, now: Long, zone: ZoneId, labels: Boolean = true) {
    val c = LocalColors.current
    val deep = breakTone()
    val large = LocalDensity.current.fontScale > 1.3f
    Column(Modifier.fillMaxWidth().clearAndSetSemantics {}) {
        Row(Modifier.fillMaxWidth().height(8.dp), horizontalArrangement = Arrangement.spacedBy(5.dp)) {
            phases.forEach { p ->
                val f = ((now - p.start).toFloat() / (p.end - p.start)).coerceIn(0f, 1f)
                Canvas(Modifier.weight((p.end - p.start).toFloat()).fillMaxSize()) {
                    val r = CornerRadius(size.height / 2)
                    if (p.kind == PhaseKind.FOCUS) drawRoundRect(c.ink.copy(alpha = 0.12f), cornerRadius = r)
                    else { var x = 0f; while (x < size.width) { drawRoundRect(c.taupe, Offset(x, 0f), Size(4.dp.toPx(), size.height), r); x += 8.dp.toPx() } }
                    if (f > 0f) drawRoundRect(if (p.kind == PhaseKind.FOCUS) c.ink else deep, size = Size(size.width * f, size.height), cornerRadius = r)
                }
            }
        }
        if (labels) {
            val breaks = phases.filter { it.kind == PhaseKind.BREAK }
            val style = Type.caption.copy(fontSize = 13.sp, lineHeight = 18.sp, fontFeatureSettings = "tnum")
            Row(Modifier.fillMaxWidth().padding(top = Dim.s2)) {
                Text(Fmt.clock(phases.first().start, zone), style = style, color = c.inkMuted)
                Spacer(Modifier.weight(1f))
                if (breaks.size == 1 && !large) {
                    Text("Break ${Fmt.clock(breaks[0].start, zone)}", style = style, color = c.inkMuted)
                    Spacer(Modifier.weight(1f))
                }
                Text(Fmt.clock(phases.last().end, zone), style = style, color = c.inkMuted)
            }
        }
    }
}

/** A label/value row in the running session ("Next break 7:38 PM"). */
@Composable
fun FactRow(label: String, value: String) {
    val c = LocalColors.current
    Row(
        Modifier.fillMaxWidth().heightIn(min = Dim.control).semantics(mergeDescendants = true) {},
        verticalAlignment = Alignment.CenterVertically,
    ) {
        Text(label, style = Type.body, color = c.inkMuted, modifier = Modifier.weight(1f))
        Spacer(Modifier.width(Dim.s3))
        Text(value, style = Type.label.copy(fontWeight = FontWeight.SemiBold, fontFeatureSettings = "tnum"), color = c.ink)
    }
}

@Composable
fun FactList(facts: List<Pair<String, String>>) {
    val c = LocalColors.current
    Column(Modifier.fillMaxWidth()) {
        androidx.compose.material3.HorizontalDivider(color = c.line)
        facts.forEach { (k, v) -> FactRow(k, v); androidx.compose.material3.HorizontalDivider(color = c.line) }
    }
}
