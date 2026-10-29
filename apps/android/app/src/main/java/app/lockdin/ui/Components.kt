package app.lockdin.ui

import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.Canvas
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.BoxWithConstraints
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.ExperimentalLayoutApi
import androidx.compose.foundation.layout.FlowRow
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.aspectRatio
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.layout.widthIn
import androidx.compose.foundation.selection.selectable
import androidx.compose.foundation.selection.selectableGroup
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.Button
import androidx.compose.material3.ButtonDefaults
import androidx.compose.material3.DropdownMenu
import androidx.compose.material3.DropdownMenuItem
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
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
import androidx.compose.ui.semantics.Role
import androidx.compose.ui.semantics.clearAndSetSemantics
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.heading
import androidx.compose.ui.semantics.role
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp

/**
 * The padlock timer. Shackle open when nothing is restricted, closed during
 * focus. TalkBack reads one summary ("Focus 1 of 2: 23 minutes left") instead of
 * the ticking digits.
 */
@Composable
fun LockTimer(remainingMs: Long, caption: String, locked: Boolean, modifier: Modifier = Modifier, maxWidth: Dp = 220.dp) {
    val c = LocalColors.current
    val body = if (locked) lerp(c.taupe, c.ink, 0.18f) else c.taupe
    val density = LocalDensity.current
    BoxWithConstraints(
        modifier
            .widthIn(max = maxWidth)
            .fillMaxWidth()
            .aspectRatio(240f / 300f)
            .clearAndSetSemantics { contentDescription = "$caption: ${Fmt.spokenRemaining(remainingMs)}" },
    ) {
        val w = this.maxWidth
        Canvas(Modifier.fillMaxSize()) {
            val s = size.width / 240f
            val shackle = Path().apply {
                if (locked) {
                    moveTo(62 * s, 138 * s); lineTo(62 * s, 96 * s)
                    arcTo(Rect(62 * s, 38 * s, 178 * s, 154 * s), 180f, 180f, false)
                } else {
                    moveTo(62 * s, 90 * s); lineTo(62 * s, 62 * s)
                    arcTo(Rect(62 * s, 4 * s, 178 * s, 120 * s), 180f, 180f, false)
                }
                lineTo(178 * s, 138 * s)
            }
            drawPath(shackle, body, style = Stroke(width = 24 * s, cap = StrokeCap.Round))
            drawRoundRect(body, topLeft = Offset(14 * s, 120 * s), size = Size(212 * s, 172 * s), cornerRadius = CornerRadius(26 * s))
        }
        // The digits are sized to the lock, not to the font-size setting: they are
        // decorative here (TalkBack reads the summary), and must stay inside the body.
        val digits = with(density) { (w * 0.24f).toSp() }
        Column(
            Modifier.fillMaxSize().padding(top = w * 0.5f, bottom = w * 0.04f, start = w * 0.08f, end = w * 0.08f),
            horizontalAlignment = Alignment.CenterHorizontally,
            verticalArrangement = Arrangement.Center,
        ) {
            ClockGlyph(c.ink, body, w * 0.1f)
            Text(Fmt.countdown(remainingMs), style = Type.timer.copy(fontSize = digits, lineHeight = digits * 1.1f), color = c.ink, maxLines = 1)
            Text(caption, style = Type.label, color = c.ink, textAlign = TextAlign.Center, maxLines = 2)
        }
    }
}

@Composable
private fun ClockGlyph(fill: Color, cut: Color, d: Dp) {
    Canvas(Modifier.size(d)) {
        val r = size.minDimension / 2
        drawCircle(fill, r)
        val c = Offset(r, r)
        drawLine(cut, c, Offset(r, r * 0.45f), strokeWidth = r * 0.2f, cap = StrokeCap.Round)
        drawLine(cut, c, Offset(r * 1.4f, r), strokeWidth = r * 0.2f, cap = StrokeCap.Round)
    }
}

/** A single-choice pill group (TalkBack: radio buttons). */
@OptIn(ExperimentalLayoutApi::class)
@Composable
fun <T> PillGroup(options: List<Pair<T, String>>, selected: T?, onSelect: (T) -> Unit, modifier: Modifier = Modifier) {
    val c = LocalColors.current
    FlowRow(modifier.selectableGroup(), horizontalArrangement = Arrangement.spacedBy(8.dp), verticalArrangement = Arrangement.spacedBy(8.dp)) {
        options.forEach { (value, label) ->
            val on = value == selected
            Surface(
                shape = CircleShape,
                color = if (on) c.ink else c.taupeSoft,
                contentColor = if (on) c.onInk else c.ink,
                border = BorderStroke(1.dp, if (on) c.ink else c.line),
                modifier = Modifier
                    .heightIn(min = 48.dp)
                    .selectable(selected = on, role = Role.RadioButton, onClick = { onSelect(value) }),
            ) {
                Box(Modifier.heightIn(min = 48.dp).padding(horizontal = 18.dp), contentAlignment = Alignment.Center) {
                    Text(label, style = Type.label)
                }
            }
        }
    }
}

/** An outlined field that opens a menu of choices. */
@Composable
fun <T> SelectField(icon: LIcon, label: String, options: List<Pair<T, String>>, selected: T, onSelect: (T) -> Unit, modifier: Modifier = Modifier) {
    val c = LocalColors.current
    var open by remember { mutableStateOf(false) }
    val current = options.firstOrNull { it.first == selected }?.second ?: ""
    Box(modifier) {
        OutlinedButton(
            onClick = { open = true },
            shape = RoundedCornerShape(12.dp),
            border = BorderStroke(1.dp, c.controlLine),
            colors = ButtonDefaults.outlinedButtonColors(containerColor = c.surface, contentColor = c.ink),
            modifier = Modifier.fillMaxWidth().heightIn(min = 52.dp)
                .semantics { contentDescription = "$label: $current. Change" },
        ) {
            LockdIcon(icon, size = 20.dp)
            Spacer(Modifier.width(10.dp))
            Text("$label · $current", style = Type.label, modifier = Modifier.weight(1f))
            LockdIcon(LIcon.ChevronDown, size = 20.dp)
        }
        DropdownMenu(expanded = open, onDismissRequest = { open = false }) {
            options.forEach { (value, text) ->
                DropdownMenuItem(text = { Text(text, style = Type.body) }, onClick = { onSelect(value); open = false })
            }
        }
    }
}

/** Connected single-choice control (TalkBack: radio buttons). */
@Composable
fun <T> Segmented(options: List<Pair<T, String>>, selected: T?, onSelect: (T) -> Unit, modifier: Modifier = Modifier) {
    val c = LocalColors.current
    Surface(
        shape = RoundedCornerShape(12.dp), color = c.ground, border = BorderStroke(1.dp, c.controlLine),
        modifier = modifier.fillMaxWidth(),
    ) {
        // At very large text sizes the options split into rows instead of clipping.
        val perRow = if (LocalDensity.current.fontScale > 1.4f) 3 else options.size
        Column(Modifier.padding(3.dp).selectableGroup(), verticalArrangement = Arrangement.spacedBy(2.dp)) {
          options.chunked(perRow).forEach { row ->
          Row(horizontalArrangement = Arrangement.spacedBy(2.dp)) {
            row.forEach { (value, label) ->
                val on = value == selected
                Surface(
                    shape = RoundedCornerShape(9.dp),
                    color = if (on) c.ink else Color.Transparent,
                    contentColor = if (on) c.onInk else c.ink,
                    modifier = Modifier.weight(1f).heightIn(min = 44.dp)
                        .selectable(selected = on, role = Role.RadioButton, onClick = { onSelect(value) }),
                ) {
                    Box(Modifier.heightIn(min = 44.dp).padding(horizontal = 2.dp), contentAlignment = Alignment.Center) {
                        Text(label, style = Type.label, maxLines = 1, textAlign = TextAlign.Center)
                    }
                }
            }
            repeat(perRow - row.size) { Spacer(Modifier.weight(1f)) }
          }
          }
        }
    }
}

/** Small caption above a field. */
@Composable
fun FieldLabel(text: String, modifier: Modifier = Modifier) {
    Text(text, style = Type.caption.copy(fontWeight = androidx.compose.ui.text.font.FontWeight.SemiBold), color = LocalColors.current.inkMuted, modifier = modifier)
}

/** A labelled field that opens a menu; label above, value inside. */
@Composable
fun <T> CompactSelect(label: String, options: List<Pair<T, String>>, selected: T, onSelect: (T) -> Unit, modifier: Modifier = Modifier, enabled: Boolean = true) {
    val c = LocalColors.current
    var open by remember { mutableStateOf(false) }
    val current = options.firstOrNull { it.first == selected }?.second ?: ""
    Column(modifier, verticalArrangement = Arrangement.spacedBy(6.dp)) {
        FieldLabel(label, Modifier.clearAndSetSemantics { })
        Box {
            OutlinedButton(
                onClick = { open = true },
                enabled = enabled,
                shape = RoundedCornerShape(12.dp),
                border = BorderStroke(1.dp, if (enabled) c.controlLine else c.line),
                colors = ButtonDefaults.outlinedButtonColors(containerColor = c.surface, contentColor = c.ink, disabledContainerColor = c.surface, disabledContentColor = c.inkMuted),
                contentPadding = androidx.compose.foundation.layout.PaddingValues(start = 14.dp, end = 10.dp),
                modifier = Modifier.fillMaxWidth().heightIn(min = 48.dp)
                    .semantics { contentDescription = "$label: $current. Change" },
            ) {
                Text(current, style = Type.body, modifier = Modifier.weight(1f), maxLines = 1)
                LockdIcon(LIcon.ChevronDown, size = 18.dp, tint = if (enabled) c.ink else c.inkMuted)
            }
            DropdownMenu(expanded = open, onDismissRequest = { open = false }) {
                options.forEach { (value, text) ->
                    DropdownMenuItem(text = { Text(text, style = Type.body) }, onClick = { onSelect(value); open = false })
                }
            }
        }
    }
}

/** The bordered card that holds a session: lock band on top, content below. */
@Composable
fun SessionCard(lock: @Composable () -> Unit, modifier: Modifier = Modifier, content: @Composable () -> Unit) {
    val c = LocalColors.current
    Surface(shape = RoundedCornerShape(16.dp), color = c.surface, border = BorderStroke(1.dp, c.line), modifier = modifier.fillMaxWidth()) {
        Column {
            Box(Modifier.fillMaxWidth().background(c.ground).padding(vertical = 18.dp), contentAlignment = Alignment.Center) { lock() }
            androidx.compose.material3.HorizontalDivider(color = c.line)
            Column(Modifier.padding(18.dp), verticalArrangement = Arrangement.spacedBy(16.dp)) { content() }
        }
    }
}

/** "● Locked in · Focus 1 of 2" */
@Composable
fun StatusLine(text: String, active: Boolean, modifier: Modifier = Modifier) {
    val c = LocalColors.current
    Row(modifier, verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(8.dp)) {
        Canvas(Modifier.size(7.dp)) { drawCircle(if (active) c.ink else c.controlLine) }
        Text(text, style = Type.caption.copy(fontWeight = androidx.compose.ui.text.font.FontWeight.SemiBold), color = c.inkMuted)
    }
}

@Composable
fun PrimaryButton(text: String, onClick: () -> Unit, modifier: Modifier = Modifier, arrow: Boolean = true) {
    val c = LocalColors.current
    Button(
        onClick = onClick,
        shape = RoundedCornerShape(12.dp),
        colors = ButtonDefaults.buttonColors(containerColor = c.ink, contentColor = c.onInk),
        modifier = modifier.fillMaxWidth().heightIn(min = 52.dp),
    ) {
        Text(text, style = Type.button)
        if (arrow) {
            Spacer(Modifier.width(10.dp))
            LockdIcon(LIcon.Arrow, tint = c.onInk, size = 18.dp)
        }
    }
}

@Composable
fun QuietButton(text: String, onClick: () -> Unit, danger: Boolean = false) {
    val c = LocalColors.current
    TextButton(onClick = onClick, modifier = Modifier.heightIn(min = 48.dp)) {
        Text(text, style = Type.label, color = if (danger) c.danger else c.ink)
    }
}

@Composable
fun Heading(text: String, modifier: Modifier = Modifier, center: Boolean = true) {
    Text(
        text, style = Type.display, color = LocalColors.current.ink,
        textAlign = if (center) TextAlign.Center else TextAlign.Start,
        modifier = modifier.fillMaxWidth().semantics { heading() },
    )
}

@Composable
fun Note(text: String, modifier: Modifier = Modifier) {
    val c = LocalColors.current
    Surface(color = c.taupeSoft, contentColor = c.ink, shape = RoundedCornerShape(12.dp), modifier = modifier.fillMaxWidth()) {
        Row(Modifier.padding(14.dp), horizontalArrangement = Arrangement.spacedBy(10.dp)) {
            LockdIcon(LIcon.Info, size = 20.dp)
            Text(text, style = Type.caption)
        }
    }
}

data class DeviceRow(val icon: LIcon, val name: String, val status: String, val ok: Boolean)

/** Honest readiness: blocking on this phone isn't built into the app yet; nothing is paired. */
val PHONE_DEVICE_ROWS = listOf(
    DeviceRow(LIcon.Phone, "This phone", "Blocking not set up", false),
    DeviceRow(LIcon.Laptop, "PC", "Not paired", false),
)

@OptIn(ExperimentalLayoutApi::class)
@Composable
fun DeviceStrip(onOpen: () -> Unit, modifier: Modifier = Modifier) {
    val c = LocalColors.current
    Surface(
        onClick = onOpen,
        color = c.ground,
        modifier = modifier.fillMaxWidth().semantics(mergeDescendants = true) { role = Role.Button },
    ) {
        FlowRow(Modifier.heightIn(min = 48.dp).padding(vertical = 8.dp), horizontalArrangement = Arrangement.spacedBy(18.dp), verticalArrangement = Arrangement.spacedBy(6.dp)) {
            PHONE_DEVICE_ROWS.forEach { d ->
                Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(6.dp)) {
                    LockdIcon(d.icon, size = 16.dp)
                    Text(d.name, style = Type.caption.copy(fontWeight = androidx.compose.ui.text.font.FontWeight.SemiBold), color = c.ink)
                    Canvas(Modifier.size(6.dp)) { drawCircle(if (d.ok) c.ink else c.controlLine) }
                    Text(d.status, style = Type.caption, color = c.inkMuted)
                }
            }
        }
    }
}
