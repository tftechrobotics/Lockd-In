// Lockd In interface kit (design pass 3): the reusable pieces the Focus and Schedule
// screens are built from. Figtree for interface text, Outfit only for the wordmark,
// page titles and timer digits. One spacing scale, one control height, one radius set.
package app.lockdin.ui

import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.ColumnScope
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.selection.selectable
import androidx.compose.foundation.selection.selectableGroup
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.text.KeyboardActions
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.material3.Button
import androidx.compose.material3.ButtonDefaults
import androidx.compose.material3.DropdownMenu
import androidx.compose.material3.DropdownMenuItem
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.IconButton
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.platform.LocalFocusManager
import androidx.compose.ui.semantics.LiveRegionMode
import androidx.compose.ui.semantics.Role
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.heading
import androidx.compose.ui.semantics.liveRegion
import androidx.compose.ui.semantics.role
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.semantics.stateDescription
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.input.ImeAction
import androidx.compose.ui.text.input.KeyboardType
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp

/** Spacing, sizes and radii (dp). Keep every screen on these. */
object Dim {
    val gutter = 20.dp
    val s1 = 4.dp; val s2 = 8.dp; val s3 = 12.dp; val s4 = 16.dp; val s5 = 24.dp; val s6 = 32.dp
    val control = 48.dp      // rows, fields, buttons: Android minimum touch target
    val segment = 44.dp
    val primary = 52.dp
    val icon = 20.dp
    val radius = 12.dp       // controls and grouped lists
    val radiusLarge = 16.dp  // sheets, dialogs
}

/** Interface type: Figtree. Outfit is reserved for titles, the wordmark and digits. */
object UiType {
    val title = Type.display.copy(fontWeight = FontWeight.SemiBold, fontSize = 22.sp, lineHeight = 28.sp, letterSpacing = 0.sp)
    val section = Type.caption.copy(fontWeight = FontWeight.SemiBold, fontSize = 13.sp, lineHeight = 18.sp)
    val body = Type.body
    val label = Type.label
    val value = Type.label.copy(fontWeight = FontWeight.Normal)
    val caption = Type.caption.copy(fontSize = 13.sp, lineHeight = 18.sp)
    val figure = Type.timer.copy(fontWeight = FontWeight.SemiBold, fontSize = 24.sp, lineHeight = 30.sp)
}

@Composable
fun ScreenTitle(text: String, modifier: Modifier = Modifier) {
    Text(text, style = UiType.title, color = LocalColors.current.ink, modifier = modifier.semantics { heading() })
}

@Composable
fun SectionLabel(text: String, modifier: Modifier = Modifier) {
    Text(text, style = UiType.section, color = LocalColors.current.inkMuted, modifier = modifier.padding(bottom = Dim.s2).semantics { heading() })
}

/** Connected segmented control (TalkBack: radio buttons). Pills are used only here. */
@Composable
fun <T> SegmentedChoice(options: List<Pair<T, String>>, selected: T?, onSelect: (T) -> Unit, modifier: Modifier = Modifier, label: String? = null) {
    val c = LocalColors.current
    val perRow = if (LocalDensity.current.fontScale > 1.4f) 2 else options.size
    Surface(shape = CircleShape, color = c.taupeSoft, border = BorderStroke(1.dp, c.line), modifier = modifier.fillMaxWidth()) {
        Column(Modifier.padding(3.dp).selectableGroup().semantics { label?.let { contentDescription = it } }, verticalArrangement = Arrangement.spacedBy(2.dp)) {
            options.chunked(perRow).forEach { row ->
                Row(horizontalArrangement = Arrangement.spacedBy(2.dp)) {
                    row.forEach { (value, text) ->
                        val on = value == selected
                        Box(
                            Modifier.weight(1f).heightIn(min = Dim.segment - 6.dp)
                                .background(if (on) c.ink else Color.Transparent, CircleShape)
                                .selectable(on, role = Role.RadioButton, onClick = { onSelect(value) }),
                            contentAlignment = Alignment.Center,
                        ) {
                            Text(text, style = UiType.label, color = if (on) c.onInk else c.ink, maxLines = 1, modifier = Modifier.padding(horizontal = 4.dp, vertical = 10.dp))
                        }
                    }
                    repeat(perRow - row.size) { Spacer(Modifier.weight(1f)) }
                }
            }
        }
    }
}

/**
 * Focus length: presets plus Custom, as one control. A custom value equal to a preset
 * selects that preset. The custom editor stays inline under the control.
 */
@Composable
fun LengthControl(minutes: Int, onChange: (Int) -> Unit, modifier: Modifier = Modifier, presets: List<Int> = listOf(25, 40, 60)) {
    val c = LocalColors.current
    var customOpen by remember { mutableStateOf(minutes !in presets) }
    // The field keeps what the user typed; it only resyncs when the length changes from
    // elsewhere (a preset or the ± buttons), so typing is never rewritten mid-edit.
    var text by remember { mutableStateOf(minutes.toString()) }
    LaunchedEffect(minutes) { if ((text.toIntOrNull() ?: 0) != minutes) text = minutes.toString() }
    val selected = if (minutes in presets) minutes else -1
    val focus = LocalFocusManager.current
    Column(modifier, verticalArrangement = Arrangement.spacedBy(Dim.s3)) {
        SegmentedChoice(presets.map { it to "$it min" } + (-1 to "Custom"), selected, { v ->
            if (v == -1) customOpen = true else { customOpen = false; onChange(v) }
        }, label = "Focus length")
        if (customOpen || minutes !in presets) {
            Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(Dim.s2)) {
                Text("Custom length", style = UiType.label, color = c.ink, modifier = Modifier.weight(1f))
                IconButton(onClick = { onChange((minutes - 5).coerceAtLeast(1)) }, enabled = minutes > 1) {
                    LockdIcon(LIcon.Minus, description = "5 minutes shorter", tint = if (minutes > 1) c.ink else c.line)
                }
                OutlinedTextField(
                    value = text,
                    onValueChange = { t -> text = t.filter(Char::isDigit).take(3); onChange(text.toIntOrNull() ?: 0) },
                    singleLine = true,
                    suffix = { Text("min", style = UiType.value, color = c.inkMuted) },
                    textStyle = UiType.body.copy(color = c.ink),
                    keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Number, imeAction = ImeAction.Done),
                    keyboardActions = KeyboardActions(onDone = { focus.clearFocus() }),
                    shape = RoundedCornerShape(Dim.radius),
                    colors = fieldColors(),
                    modifier = Modifier.width(112.dp).semantics { contentDescription = "Minutes" },
                )
                IconButton(onClick = { onChange((minutes + 5).coerceAtMost(240)) }, enabled = minutes < 240) {
                    LockdIcon(LIcon.Plus, description = "5 minutes longer", tint = if (minutes < 240) c.ink else c.line)
                }
            }
        }
    }
}

/** A grouped list of labelled settings with dividers (Profile, Breaks, Focus rounds). */
@Composable
fun SettingsGroup(modifier: Modifier = Modifier, content: @Composable ColumnScope.() -> Unit) {
    val c = LocalColors.current
    Surface(shape = RoundedCornerShape(Dim.radius), color = c.surface, border = BorderStroke(1.dp, c.line), modifier = modifier.fillMaxWidth()) {
        Column(content = content)
    }
}

@Composable
fun GroupDivider() = HorizontalDivider(color = LocalColors.current.line, modifier = Modifier.padding(start = Dim.s4))

/** One setting: label on the left, value and chevron on the right; opens a menu of options. */
@Composable
fun <T> SettingMenuRow(
    label: String,
    options: List<Pair<T, String>>,
    selected: T,
    onSelect: (T) -> Unit,
    icon: LIcon? = null,
    enabled: Boolean = true,
    supporting: String? = null,
) {
    val c = LocalColors.current
    var open by remember { mutableStateOf(false) }
    val current = options.firstOrNull { it.first == selected }?.second ?: ""
    Box {
        SettingRow(label, current, icon = icon, enabled = enabled, supporting = supporting, chevron = LIcon.ChevronDown, onClick = { open = true })
        DropdownMenu(expanded = open, onDismissRequest = { open = false }, modifier = Modifier.background(c.surface)) {
            options.forEach { (value, text) ->
                DropdownMenuItem(
                    text = { Text(text, style = UiType.body, color = c.ink, fontWeight = if (value == selected) FontWeight.SemiBold else FontWeight.Normal) },
                    trailingIcon = if (value == selected) ({ LockdIcon(LIcon.Check, size = 18.dp, tint = c.ink) }) else null,
                    onClick = { onSelect(value); open = false },
                )
            }
        }
    }
}

@Composable
fun SettingRow(
    label: String,
    value: String,
    icon: LIcon? = null,
    enabled: Boolean = true,
    supporting: String? = null,
    chevron: LIcon? = LIcon.Chevron,
    onClick: (() -> Unit)? = null,
) {
    val c = LocalColors.current
    val tint = if (enabled) c.ink else c.inkMuted
    Row(
        Modifier.fillMaxWidth().heightIn(min = Dim.control + 4.dp)
            .then(if (onClick != null && enabled) Modifier.clickable(role = Role.Button, onClick = onClick) else Modifier)
            .semantics(mergeDescendants = true) { stateDescription = value }
            .padding(horizontal = Dim.s4, vertical = Dim.s2),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        icon?.let { LockdIcon(it, size = Dim.icon, tint = tint); Spacer(Modifier.width(Dim.s3)) }
        Column(Modifier.weight(1f)) {
            Text(label, style = UiType.label, color = tint)
            supporting?.let { Text(it, style = UiType.caption, color = c.inkMuted) }
        }
        Spacer(Modifier.width(Dim.s3))
        Text(value, style = UiType.value, color = if (enabled) c.ink else c.inkMuted, maxLines = 1, overflow = TextOverflow.Ellipsis)
        if (chevron != null && onClick != null && enabled) { Spacer(Modifier.width(Dim.s1)); LockdIcon(chevron, size = 18.dp, tint = c.inkMuted) }
    }
}

/** The primary action area, pinned above the bottom navigation. */
@Composable
fun ActionBar(modifier: Modifier = Modifier, content: @Composable ColumnScope.() -> Unit) {
    val c = LocalColors.current
    Column(modifier.fillMaxWidth().background(c.ground)) {
        HorizontalDivider(color = c.line)
        Column(Modifier.padding(horizontal = Dim.gutter, vertical = Dim.s3), verticalArrangement = Arrangement.spacedBy(Dim.s2), content = content)
    }
}

@Composable
fun MainButton(text: String, onClick: () -> Unit, modifier: Modifier = Modifier, icon: LIcon? = null) {
    val c = LocalColors.current
    Button(
        onClick = onClick,
        shape = RoundedCornerShape(Dim.radius),
        colors = ButtonDefaults.buttonColors(containerColor = c.ink, contentColor = c.onInk),
        modifier = modifier.fillMaxWidth().heightIn(min = Dim.primary),
    ) {
        Text(text, style = UiType.label.copy(fontSize = 17.sp, fontWeight = FontWeight.SemiBold))
        icon?.let { Spacer(Modifier.width(Dim.s2)); LockdIcon(it, tint = c.onInk, size = Dim.icon) }
    }
}

@Composable
fun SecondaryButton(text: String, onClick: () -> Unit, modifier: Modifier = Modifier) {
    val c = LocalColors.current
    OutlinedButton(
        onClick = onClick, shape = RoundedCornerShape(Dim.radius), border = BorderStroke(1.dp, c.controlLine),
        colors = ButtonDefaults.outlinedButtonColors(contentColor = c.ink),
        modifier = modifier.heightIn(min = Dim.control),
    ) { Text(text, style = UiType.label) }
}

@Composable
fun TextAction(text: String, onClick: () -> Unit, danger: Boolean = false, modifier: Modifier = Modifier) {
    val c = LocalColors.current
    TextButton(onClick = onClick, modifier = modifier.heightIn(min = Dim.control)) {
        Text(text, style = UiType.label.copy(fontWeight = FontWeight.SemiBold), color = if (danger) c.danger else c.ink)
    }
}

/**
 * Honest protection status with a way to act on it. `ok` = verified protection; this build
 * never passes true on Android because blocking isn't built into the app yet.
 */
@Composable
fun ProtectionLine(text: String, ok: Boolean, action: String?, onAction: () -> Unit) {
    val c = LocalColors.current
    Row(Modifier.fillMaxWidth().semantics(mergeDescendants = true) { liveRegion = LiveRegionMode.Polite }, verticalAlignment = Alignment.CenterVertically) {
        androidx.compose.foundation.Canvas(Modifier.size(8.dp)) {
            if (ok) drawCircle(c.statusDot)
            else drawCircle(c.statusDot, style = androidx.compose.ui.graphics.drawscope.Stroke(width = 1.5.dp.toPx()), radius = size.minDimension / 2 - 0.75.dp.toPx())
        }
        Spacer(Modifier.width(Dim.s2))
        Text(text, style = UiType.caption, color = c.inkMuted, modifier = Modifier.weight(1f))
        action?.let { TextButton(onClick = onAction, modifier = Modifier.heightIn(min = Dim.control)) { Text(it, style = UiType.label.copy(fontWeight = FontWeight.SemiBold), color = c.ink) } }
    }
}

/** Status above a running session: "Focus · round 1 of 2". */
@Composable
fun PhaseTag(text: String, active: Boolean) {
    val c = LocalColors.current
    Surface(shape = CircleShape, color = if (active) c.ink else c.taupeSoft, contentColor = if (active) c.onInk else c.ink) {
        Text(text, style = UiType.caption.copy(fontWeight = FontWeight.SemiBold), modifier = Modifier.padding(horizontal = 12.dp, vertical = 6.dp))
    }
}

/** Plain-language summary block (session plan before starting or saving). */
@Composable
fun SummaryText(lines: List<String>, modifier: Modifier = Modifier) {
    val c = LocalColors.current
    Surface(shape = RoundedCornerShape(Dim.radius), color = c.taupeSoft, modifier = modifier.fillMaxWidth()) {
        Column(Modifier.padding(Dim.s4), verticalArrangement = Arrangement.spacedBy(2.dp)) {
            lines.forEachIndexed { i, l -> Text(l, style = if (i == 0) UiType.label else UiType.caption, color = if (i == 0) c.ink else c.inkMuted) }
        }
    }
}

/** Human durations: 90 → "1 h 30 min". */
fun durationLabel(min: Int): String = when {
    min < 60 -> "$min min"
    min % 60 == 0 -> "${min / 60} h"
    else -> "${min / 60} h ${min % 60} min"
}
