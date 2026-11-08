// Lockd In interface kit: the reusable pieces every screen is built from. Pass 5 (R53): LiterataText
// normal width for the interface, LiterataText Expanded for titles, numbers and the primary button.
// Grouped content sits on solid tonal plates (no outlined cards); rows are separated by a cut
// of the background colour, like machined plates. One spacing scale, one control height.
package app.lockdin.ui

import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.ui.draw.clip
import androidx.compose.ui.draw.drawWithContent
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
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.interaction.collectIsPressedAsState

/** Spacing, sizes and radii (dp). Keep every screen on these. */
object Dim {
    val gutter = 20.dp
    val s1 = 4.dp; val s2 = 8.dp; val s3 = 12.dp; val s4 = 16.dp; val s5 = 24.dp; val s6 = 32.dp
    val control = 48.dp      // rows, fields, buttons: Android minimum touch target
    val segment = 44.dp
    val primary = 52.dp
    val icon = 20.dp
    val radius = 12.dp       // controls, fields, segments
    val radiusLarge = 16.dp  // sheets, dialogs, buttons
    val plate = 20.dp        // grouped plates (settings, lists, summaries)
}

/** Interface type: LiterataText. Expanded LiterataText only for titles, digits and the primary button. */
object UiType {
    val title = Type.title
    val section = Type.caption.copy(fontWeight = FontWeight.SemiBold, fontSize = 14.sp, lineHeight = 18.sp)
    val body = Type.body
    val label = Type.label
    val value = Type.label.copy(fontWeight = FontWeight.Normal)
    val caption = Type.caption.copy(fontSize = 13.sp, lineHeight = 18.sp)
    val figure = Type.timer.copy(fontSize = 24.sp, lineHeight = 30.sp)
}

@Composable
fun ScreenTitle(text: String, modifier: Modifier = Modifier) {
    Text(text, style = Type.display, color = LocalColors.current.ink, modifier = modifier.semantics { heading() })
}

@Composable
fun SectionLabel(text: String, modifier: Modifier = Modifier) {
    Text(text, style = UiType.section, color = LocalColors.current.inkMuted, modifier = modifier.padding(bottom = Dim.s2).semantics { heading() })
}

/**
 * Owner 2026-11-07 (R55): boxes around text read as AI everywhere except Settings. Settings provides
 * true; everywhere else groups, rows, choices and secondary buttons draw without boxes.
 */
val LocalBoxed = androidx.compose.runtime.staticCompositionLocalOf { false }

/** Plate colour and side padding: a plate in Settings, plain (no box, no side inset) elsewhere. */
@Composable fun plateColor(): Color = Color.Transparent
/** A Settings plate is frosted glass (R58); elsewhere there is no box. */
@Composable fun Modifier.plate(): Modifier = if (LocalBoxed.current) this.glass(RoundedCornerShape(Dim.plate)) else this
@Composable fun plateSide(d: androidx.compose.ui.unit.Dp): androidx.compose.ui.unit.Dp = if (LocalBoxed.current) d else 0.dp

/** True when the person turned animations off (Android's "Remove animations"). */
@Composable
fun reducedMotion(): Boolean {
    val ctx = androidx.compose.ui.platform.LocalContext.current
    return remember { animationsOff(ctx.applicationContext) }
}

/** Presses in a little while held (R55: more response to touch). Pass the control's interaction source. */
@Composable
fun Modifier.pressScale(source: androidx.compose.foundation.interaction.MutableInteractionSource): Modifier {
    val pressed by source.collectIsPressedAsState()
    val still = reducedMotion()
    val s by androidx.compose.animation.core.animateFloatAsState(
        if (pressed && !still) 0.96f else 1f,
        androidx.compose.animation.core.spring(dampingRatio = 0.55f, stiffness = 900f), label = "press",
    )
    return this.graphicsLayer { scaleX = s; scaleY = s }
}

/**
 * A text option with an ink bar that grows under it when chosen (no box). Used for single choices
 * outside Settings: lengths, profiles, segmented choices.
 */
@Composable
fun UnderlineOption(
    on: Boolean, onClick: () -> Unit, modifier: Modifier = Modifier, role: Role = Role.RadioButton,
    content: @Composable () -> Unit,
) {
    val c = LocalColors.current
    val still = reducedMotion()
    val grow by androidx.compose.animation.core.animateFloatAsState(
        if (on) 1f else 0f, if (still) androidx.compose.animation.core.snap() else androidx.compose.animation.core.spring(dampingRatio = 0.7f, stiffness = 500f), label = "bar",
    )
    val source = remember { androidx.compose.foundation.interaction.MutableInteractionSource() }
    Column(
        modifier.width(androidx.compose.foundation.layout.IntrinsicSize.Max).heightIn(min = Dim.control).pressScale(source)
            .selectable(on, interactionSource = source, indication = androidx.compose.material3.ripple(bounded = false), role = role, onClick = onClick)
            .padding(top = 6.dp),
        horizontalAlignment = Alignment.Start,
    ) {
        content()
        Spacer(Modifier.height(6.dp))
        Box(Modifier.fillMaxWidth(grow.coerceAtLeast(0.001f)).height(3.dp).background(c.ink.copy(alpha = grow), RoundedCornerShape(2.dp)))
    }
}

/** Connected segmented control (TalkBack: radio buttons). Boxed only inside Settings. */
@Composable
fun <T> SegmentedChoice(options: List<Pair<T, String>>, selected: T?, onSelect: (T) -> Unit, modifier: Modifier = Modifier, label: String? = null) {
    val c = LocalColors.current
    val perRow = if (LocalDensity.current.fontScale > 1.4f) 2 else options.size
    if (!LocalBoxed.current) {
        Column(modifier.fillMaxWidth().selectableGroup().semantics { label?.let { contentDescription = it } }, verticalArrangement = Arrangement.spacedBy(Dim.s1)) {
            options.chunked(perRow).forEach { row ->
                Row(horizontalArrangement = Arrangement.spacedBy(Dim.s4)) {
                    row.forEach { (value, text) ->
                        val on = value == selected
                        UnderlineOption(on, { onSelect(value) }, Modifier.weight(1f)) {
                            Text(text, style = UiType.label.copy(fontWeight = if (on) FontWeight.SemiBold else FontWeight.Medium), color = if (on) c.ink else c.inkMuted, maxLines = 1)
                        }
                    }
                    repeat(perRow - row.size) { Spacer(Modifier.weight(1f)) }
                }
            }
        }
        return
    }
    Surface(shape = RoundedCornerShape(Dim.radius + 3.dp), color = c.taupeSoft, modifier = modifier.fillMaxWidth()) {
        Column(Modifier.padding(3.dp).selectableGroup().semantics { label?.let { contentDescription = it } }, verticalArrangement = Arrangement.spacedBy(2.dp)) {
            options.chunked(perRow).forEach { row ->
                Row(horizontalArrangement = Arrangement.spacedBy(2.dp)) {
                    row.forEach { (value, text) ->
                        val on = value == selected
                        Box(
                            Modifier.weight(1f).heightIn(min = Dim.segment - 6.dp)
                                .background(if (on) c.ink else Color.Transparent, RoundedCornerShape(Dim.radius))
                                .selectable(on, role = Role.RadioButton, onClick = { onSelect(value) }),
                            contentAlignment = Alignment.Center,
                        ) {
                            Text(text, style = if (on) UiType.label.copy(fontWeight = FontWeight.SemiBold) else UiType.label, color = if (on) c.onInk else c.ink, maxLines = 1, modifier = Modifier.padding(horizontal = 4.dp, vertical = 10.dp))
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
    var customOpen by remember { mutableStateOf(minutes !in presets) }
    val selected = if (minutes in presets) minutes else -1
    Column(modifier, verticalArrangement = Arrangement.spacedBy(Dim.s3)) {
        SegmentedChoice(presets.map { it to "$it min" } + (-1 to "Custom"), selected, { v ->
            if (v == -1) customOpen = true else { customOpen = false; onChange(v) }
        }, label = "Focus length")
        if (customOpen || minutes !in presets) CustomLengthRow(minutes, onChange)
    }
}

/**
 * Custom length editor: − / minutes field / +. The field keeps what the user typed; it only
 * resyncs when the length changes from elsewhere (a preset or the ± buttons), so typing is
 * never rewritten mid-edit.
 */
@Composable
fun CustomLengthRow(minutes: Int, onChange: (Int) -> Unit, label: String = "Custom length") {
    val c = LocalColors.current
    var text by remember { mutableStateOf(minutes.toString()) }
    LaunchedEffect(minutes) { if ((text.toIntOrNull() ?: 0) != minutes) text = minutes.toString() }
    val focus = LocalFocusManager.current
    Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(Dim.s2)) {
        Text(label, style = UiType.label, color = c.ink, modifier = Modifier.weight(1f))
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

/** A grouped list of labelled settings with dividers (Profile, Breaks, Focus rounds). */
@Composable
fun SettingsGroup(modifier: Modifier = Modifier, content: @Composable ColumnScope.() -> Unit) {
    val c = LocalColors.current
    if (!LocalBoxed.current) { Column(modifier.fillMaxWidth(), content = content); return }
    // R58: frosted glass over the (blurred) background, a light edge along the top.
    Column(modifier.fillMaxWidth().glass(RoundedCornerShape(Dim.plate)), content = content)
}

/** Glass tints (R58). Dark: cream-tinted smoke. Light: milky white. */
private object Glass {
    val darkFill = listOf(Color(0x1CF2E6D6), Color(0x0FF2E6D6))
    val darkEdge = listOf(Color(0x3DFFF5E6), Color(0x0AFFF5E6))
    val lightFill = listOf(Color(0xB3FFFFFF), Color(0x80FFFFFF))
    val lightEdge = listOf(Color(0xF2FFFFFF), Color(0x4DFFFFFF))
}

/** A frosted glass surface: translucent vertical tint and a 1 dp edge that is brightest at the top. */
@Composable
fun Modifier.glass(shape: androidx.compose.ui.graphics.Shape): Modifier {
    val dark = LocalColors.current == DarkColors
    val fill = if (dark) Glass.darkFill else Glass.lightFill
    val edge = if (dark) Glass.darkEdge else Glass.lightEdge
    return this.clip(shape)
        .background(androidx.compose.ui.graphics.Brush.verticalGradient(fill), shape)
        .border(1.dp, androidx.compose.ui.graphics.Brush.verticalGradient(edge), shape)
}

/**
 * The "Start a session" button (R58, owner's pick "Lens" without the tab lens): a thick slab of
 * clear glass — bright top edge, a darker lip at the bottom, the background showing through.
 */
@Composable
fun GlassButton(text: String, onClick: () -> Unit, modifier: Modifier = Modifier) {
    val c = LocalColors.current
    val dark = c == DarkColors
    val source = remember { androidx.compose.foundation.interaction.MutableInteractionSource() }
    val shape = RoundedCornerShape(50)
    val fill = if (dark) listOf(0f to Color(0x4DFFF8EC), 0.48f to Color(0x1AFFF0DC), 1f to Color(0x2EFFF0DC))
        else listOf(0f to Color(0xF2FFFFFF), 0.48f to Color(0xA6FFFFFF), 1f to Color(0xC7FFFFFF))
    val rim = if (dark) Color(0x73FFF5E6) else Color(0xFFFFFFFF)
    val lip = if (dark) Color(0x52000000) else Color(0x24302A25)
    Box(
        modifier.fillMaxWidth().heightIn(min = 58.dp).pressScale(source)
            .clip(shape)
            .background(androidx.compose.ui.graphics.Brush.verticalGradient(*fill.toTypedArray()), shape)
            .border(1.5.dp, rim, shape)
            .drawWithContent {
                drawContent()
                val r = size.height / 2
                // Inner light along the top, inner shade along the bottom: the glass has depth.
                drawRoundRect(
                    androidx.compose.ui.graphics.Brush.verticalGradient(
                        0f to Color.White.copy(alpha = if (dark) 0.5f else 0.9f), 0.08f to Color.Transparent,
                        0.78f to Color.Transparent, 1f to lip,
                    ),
                    cornerRadius = androidx.compose.ui.geometry.CornerRadius(r, r),
                )
            }
            .clickable(interactionSource = source, indication = androidx.compose.material3.ripple(), role = Role.Button, onClick = onClick)
            .padding(horizontal = Dim.s4),
        contentAlignment = Alignment.Center,
    ) {
        Text(
            text, maxLines = 1, overflow = TextOverflow.Ellipsis,
            style = Type.button.copy(
                fontWeight = FontWeight.SemiBold,
                shadow = if (dark) androidx.compose.ui.graphics.Shadow(Color(0x80000000), androidx.compose.ui.geometry.Offset(0f, 2f), 4f) else null,
            ),
            color = if (dark) Color(0xFFFFF8EE) else c.ink,
        )
    }
}

/** Between rows: a cut of the background colour on a Settings plate, a hairline elsewhere. */
@Composable
fun GroupDivider() = if (LocalBoxed.current) HorizontalDivider(thickness = 1.dp,
        color = if (LocalColors.current == DarkColors) Color(0x14FFF5E6) else Color(0x1A302A25))
    else HorizontalDivider(thickness = 1.dp, color = LocalColors.current.line)

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
            .padding(horizontal = if (LocalBoxed.current) Dim.s4 else 0.dp, vertical = Dim.s2),
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
    val source = remember { androidx.compose.foundation.interaction.MutableInteractionSource() }
    Button(
        onClick = onClick,
        shape = RoundedCornerShape(Dim.radiusLarge),
        colors = ButtonDefaults.buttonColors(containerColor = c.ink, contentColor = c.onInk),
        interactionSource = source,
        modifier = modifier.fillMaxWidth().heightIn(min = Dim.primary).pressScale(source),
    ) {
        Text(text, style = Type.button, maxLines = 1, overflow = TextOverflow.Ellipsis)
        icon?.let { Spacer(Modifier.width(Dim.s2)); LockdIcon(it, tint = c.onInk, size = Dim.icon) }
    }
}

@Composable
fun SecondaryButton(text: String, onClick: () -> Unit, modifier: Modifier = Modifier) {
    val c = LocalColors.current
    // Outside Settings (R55): no box, an underlined text action with a press response.
    if (!LocalBoxed.current) {
        val source = remember { androidx.compose.foundation.interaction.MutableInteractionSource() }
        TextButton(onClick = onClick, interactionSource = source, contentPadding = androidx.compose.foundation.layout.PaddingValues(horizontal = 0.dp, vertical = 8.dp),
            modifier = modifier.heightIn(min = Dim.control).pressScale(source)) {
            Text(text, style = UiType.label.copy(fontSize = 16.sp, fontWeight = FontWeight.SemiBold, textDecoration = androidx.compose.ui.text.style.TextDecoration.Underline), color = c.ink)
        }
        return
    }
    // In Settings: tonal (taupe), one step stronger than the plates.
    Button(
        onClick = onClick, shape = RoundedCornerShape(Dim.radiusLarge),
        colors = ButtonDefaults.buttonColors(containerColor = c.taupe, contentColor = c.ink),
        modifier = modifier.heightIn(min = Dim.control),
    ) { Text(text, style = UiType.label.copy(fontWeight = FontWeight.SemiBold)) }
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
    Surface(shape = RoundedCornerShape(Dim.plate), color = if (LocalBoxed.current) c.taupeSoft else Color.Transparent, modifier = modifier.fillMaxWidth()) {
        Column(Modifier.padding(if (LocalBoxed.current) Dim.s4 else 0.dp), verticalArrangement = Arrangement.spacedBy(2.dp)) {
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
