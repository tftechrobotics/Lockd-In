// Design exploration (pass 4): two directions for the Android Focus screen, rendered
// from one sample session. Lives in the androidTest source set, so it never ships in the
// app; LabScreenshotTest renders it for review.
package app.lockdin.lab

import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.Canvas
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.ExperimentalLayoutApi
import androidx.compose.foundation.layout.FlowRow
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.aspectRatio
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.layout.widthIn
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.Button
import androidx.compose.material3.ButtonDefaults
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.NavigationBar
import androidx.compose.material3.NavigationBarItem
import androidx.compose.material3.NavigationBarItemDefaults
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.geometry.CornerRadius
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.geometry.Size
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.Path
import androidx.compose.ui.graphics.StrokeCap
import androidx.compose.ui.graphics.StrokeJoin
import androidx.compose.ui.graphics.drawscope.Stroke
import androidx.compose.ui.graphics.lerp
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.semantics.clearAndSetSemantics
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.text.TextStyle
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import app.lockdin.R
import app.lockdin.ui.Figtree
import app.lockdin.ui.LIcon
import app.lockdin.ui.LocalColors
import app.lockdin.ui.LockdIcon
import app.lockdin.ui.Outfit
import java.time.ZoneId
import java.time.ZonedDateTime
import java.time.format.DateTimeFormatter

enum class LabState { READY, FOCUS, BREAK, DONE }

/** The one sample session both directions show: 2 × 40 min with a 10-minute break, 7:00–8:30 PM. */
object Sample {
    val zone: ZoneId = ZoneId.of("America/Chicago")
    val t0: Long = ZonedDateTime.of(2026, 11, 3, 19, 0, 0, 0, zone).toInstant().toEpochMilli()
    const val MIN = 60_000L
    const val TASK = "TAM 212 · Problem set 6"
    val segments = listOf(Triple(true, 0L, 40L), Triple(false, 40L, 50L), Triple(true, 50L, 90L)) // focus?, start, end (min)
    fun now(s: LabState) = t0 + MIN * when (s) { LabState.READY -> -2; LabState.FOCUS -> 14; LabState.BREAK -> 44; LabState.DONE -> 90 }
    fun remaining(s: LabState) = when (s) { LabState.READY -> 40 * MIN; LabState.FOCUS -> 26 * MIN; LabState.BREAK -> 6 * MIN; LabState.DONE -> 0L }
    fun progress(s: LabState) = when (s) { LabState.FOCUS -> 14f / 40f; LabState.BREAK -> 4f / 10f; else -> 0f }
    private val fmt = DateTimeFormatter.ofPattern("h:mm a")
    fun clock(minutesFromStart: Long): String = fmt.format(java.time.Instant.ofEpochMilli(t0 + minutesFromStart * MIN).atZone(zone))
    fun countdown(ms: Long): String { val s = ms / 1000; return "%02d:%02d".format(s / 60, s % 60) }
}

private object LabType {
    val numeralsA = TextStyle(fontFamily = Outfit, fontWeight = FontWeight.SemiBold, fontFeatureSettings = "tnum")
    val numeralsB = TextStyle(fontFamily = Outfit, fontWeight = FontWeight.Medium, fontFeatureSettings = "tnum", letterSpacing = (-2).sp)
    val question = TextStyle(fontFamily = Outfit, fontWeight = FontWeight.Medium, fontSize = 18.sp, lineHeight = 24.sp)
    val task = TextStyle(fontFamily = Outfit, fontWeight = FontWeight.Medium, fontSize = 22.sp, lineHeight = 28.sp)
    val title = TextStyle(fontFamily = Outfit, fontWeight = FontWeight.Medium, fontSize = 20.sp, lineHeight = 26.sp)
    val body = TextStyle(fontFamily = Figtree, fontWeight = FontWeight.Normal, fontSize = 16.sp, lineHeight = 22.sp)
    val label = TextStyle(fontFamily = Figtree, fontWeight = FontWeight.Medium, fontSize = 15.sp, lineHeight = 20.sp)
    val meta = TextStyle(fontFamily = Figtree, fontWeight = FontWeight.Normal, fontSize = 14.sp, lineHeight = 20.sp)
    val strong = TextStyle(fontFamily = Figtree, fontWeight = FontWeight.SemiBold, fontSize = 14.sp, lineHeight = 20.sp)
}

@Composable private fun deep() = LocalColors.current.let { lerp(it.taupe, it.ink, 0.45f) }

/* ------------------------------ shared shell ------------------------------ */

@Composable
fun LabShell(content: @Composable () -> Unit, action: @Composable () -> Unit) {
    val c = LocalColors.current
    Column(Modifier.fillMaxSize().background(c.ground)) {
        Row(Modifier.fillMaxWidth().height(56.dp).padding(horizontal = 16.dp), verticalAlignment = Alignment.CenterVertically) {
            androidx.compose.foundation.Image(
                androidx.compose.ui.res.painterResource(R.drawable.wordmark), contentDescription = "Lockd In",
                colorFilter = androidx.compose.ui.graphics.ColorFilter.tint(c.ink), modifier = Modifier.height(22.dp),
            )
            Spacer(Modifier.weight(1f))
            LockdIcon(LIcon.Gear, description = "Settings")
        }
        Column(Modifier.weight(1f).fillMaxWidth().verticalScroll(rememberScrollState()).padding(horizontal = 24.dp)) { content() }
        Column(Modifier.fillMaxWidth().padding(horizontal = 24.dp, vertical = 12.dp), verticalArrangement = Arrangement.spacedBy(10.dp)) { action() }
        HorizontalDivider(color = c.line)
        NavigationBar(containerColor = c.ground, tonalElevation = 0.dp) {
            listOf(LIcon.Focus to "Focus", LIcon.Calendar to "Schedule", LIcon.Book to "Profiles").forEachIndexed { i, (icon, label) ->
                NavigationBarItem(
                    selected = i == 0, onClick = {}, icon = { LockdIcon(icon) },
                    label = { Text(label, style = LabType.meta.copy(fontSize = 12.sp, fontWeight = if (i == 0) FontWeight.SemiBold else FontWeight.Normal)) },
                    colors = NavigationBarItemDefaults.colors(selectedIconColor = c.ink, selectedTextColor = c.ink, indicatorColor = c.taupeSoft, unselectedIconColor = c.inkMuted, unselectedTextColor = c.inkMuted),
                )
            }
        }
    }
}

@Composable
fun Honest() {
    val c = LocalColors.current
    Row(verticalAlignment = Alignment.Top) {
        Canvas(Modifier.padding(top = 6.dp).size(8.dp)) { drawCircle(c.statusDot, style = Stroke(1.5.dp.toPx()), radius = size.minDimension / 2 - 0.75.dp.toPx()) }
        Spacer(Modifier.width(10.dp))
        Text(
            androidx.compose.ui.text.buildAnnotatedString {
                append("Timer only. Apps stay available on this phone. ")
                pushStyle(androidx.compose.ui.text.SpanStyle(fontWeight = FontWeight.SemiBold, color = c.ink, textDecoration = androidx.compose.ui.text.style.TextDecoration.Underline))
                append("Set up blocking"); pop()
            },
            style = LabType.meta, color = c.inkMuted,
        )
    }
}

@Composable
fun Primary(text: String) {
    val c = LocalColors.current
    Button(onClick = {}, shape = RoundedCornerShape(16.dp), colors = ButtonDefaults.buttonColors(containerColor = c.ink, contentColor = c.onInk),
        modifier = Modifier.fillMaxWidth().heightIn(min = 56.dp)) { Text(text, style = LabType.label.copy(fontSize = 17.sp, fontWeight = FontWeight.SemiBold)) }
}

@Composable
fun Ghost(text: String, modifier: Modifier = Modifier) {
    val c = LocalColors.current
    OutlinedButton(onClick = {}, shape = RoundedCornerShape(16.dp), border = BorderStroke(1.dp, c.line), modifier = modifier.heightIn(min = 52.dp)) {
        Text(text, style = LabType.label.copy(fontWeight = FontWeight.SemiBold), color = c.ink)
    }
}

/* ------------------------------ Direction A ------------------------------ */

/** Refined padlock (same geometry as the web lab, 200 × 224 units). */
@Composable
fun Padlock(state: LabState, blocked: Boolean, width: Dp, caption: String) {
    val c = LocalColors.current
    val deep = deep()
    val closed = blocked && state == LabState.FOCUS
    val body = when (state) { LabState.READY -> c.taupe; LabState.FOCUS -> c.ink; LabState.BREAK -> c.surface; LabState.DONE -> c.taupeSoft }
    val fg = if (state == LabState.FOCUS) c.onInk else c.ink
    val shackle = when { closed -> c.ink; state == LabState.DONE -> c.taupe; else -> deep }
    val density = LocalDensity.current
    Box(
        Modifier.width(width).aspectRatio(200f / 224f).clearAndSetSemantics {
            contentDescription = if (state == LabState.DONE) "Session complete" else "$caption: ${Sample.remaining(state) / Sample.MIN} minutes"
        },
    ) {
        Canvas(Modifier.fillMaxSize()) {
            val u = size.width / 200f
            val p = Path().apply {
                if (closed) { moveTo(60 * u, 108 * u); lineTo(60 * u, 74 * u); arcTo(androidx.compose.ui.geometry.Rect(60 * u, 34 * u, 140 * u, 114 * u), 180f, 180f, false); lineTo(140 * u, 108 * u) }
                else { moveTo(60 * u, 108 * u); lineTo(60 * u, 62 * u); arcTo(androidx.compose.ui.geometry.Rect(60 * u, 22 * u, 140 * u, 102 * u), 180f, 180f, false); lineTo(140 * u, 80 * u) }
            }
            drawPath(p, shackle, style = Stroke(11 * u, cap = StrokeCap.Round))
            drawRoundRect(body, Offset(16 * u, 96 * u), Size(168 * u, 124 * u), CornerRadius(30 * u))
            if (state == LabState.BREAK) drawRoundRect(deep, Offset(17.5f * u, 97.5f * u), Size(165 * u, 121 * u), CornerRadius(29 * u), style = Stroke(3 * u))
            if (state == LabState.DONE) {
                drawRoundRect(c.line, Offset(17 * u, 97 * u), Size(166 * u, 122 * u), CornerRadius(29 * u), style = Stroke(2 * u))
                val ck = Path().apply { moveTo(80 * u, 150 * u); lineTo(95 * u, 165 * u); lineTo(122 * u, 136 * u) }
                drawPath(ck, c.ink, style = Stroke(9 * u, cap = StrokeCap.Round, join = StrokeJoin.Round))
            }
            if (state == LabState.FOCUS || state == LabState.BREAK) {
                val track = if (state == LabState.FOCUS) c.onInk.copy(alpha = 0.25f) else c.ink.copy(alpha = 0.12f)
                val fill = if (state == LabState.FOCUS) c.onInk else deep
                drawLine(track, Offset(52 * u, 204 * u), Offset(148 * u, 204 * u), 3 * u, StrokeCap.Round)
                drawLine(fill, Offset(52 * u, 204 * u), Offset((52 + 96 * Sample.progress(state)) * u, 204 * u), 3 * u, StrokeCap.Round)
            }
        }
        val w = width
        Column(Modifier.fillMaxSize().padding(top = w * (if (state == LabState.DONE) 0.86f else 0.555f)), horizontalAlignment = Alignment.CenterHorizontally) {
            if (state != LabState.DONE) {
                val size = with(density) { (w * 0.23f).toSp() }
                Text(Sample.countdown(Sample.remaining(state)), style = LabType.numeralsA.copy(fontSize = size, lineHeight = size), color = fg, maxLines = 1)
            }
            val cap = with(density) { (w * 0.058f).toSp() }
            Text(if (state == LabState.DONE) "Session complete" else caption, style = LabType.label.copy(fontSize = cap, lineHeight = cap * 1.2f), color = if (state == LabState.DONE) c.inkMuted else fg)
        }
    }
}

@Composable
private fun Question(text: String) = Text(text, style = LabType.question, color = LocalColors.current.ink)

@Composable
private fun TaskUnderline(text: String) {
    val c = LocalColors.current
    Column {
        Text(text, style = LabType.task, color = c.ink, modifier = Modifier.padding(vertical = 6.dp))
        HorizontalDivider(color = c.controlLine, thickness = 1.5.dp)
    }
}

@Composable
private fun Tiles(selected: Int) {
    val c = LocalColors.current
    val large = LocalDensity.current.fontScale > 1.3f
    val items = listOf(25, 40, 60, -1)
    Column(verticalArrangement = Arrangement.spacedBy(10.dp)) {
        items.chunked(if (large) 2 else 4).forEach { row ->
            Row(horizontalArrangement = Arrangement.spacedBy(10.dp)) {
                row.forEach { m ->
                    val on = m == selected
                    Surface(
                        shape = RoundedCornerShape(14.dp), color = if (on) c.ink else Color.Transparent, contentColor = if (on) c.onInk else c.ink,
                        border = if (on) null else BorderStroke(1.dp, c.line), modifier = Modifier.weight(1f).heightIn(min = 68.dp),
                    ) {
                        Column(Modifier.padding(horizontal = 12.dp, vertical = 10.dp), verticalArrangement = Arrangement.Bottom) {
                            if (m > 0) {
                                Text("$m", style = LabType.numeralsA.copy(fontWeight = FontWeight.Medium, fontSize = 26.sp, lineHeight = 28.sp))
                                Text("min", style = LabType.meta)
                            } else {
                                Spacer(Modifier.weight(1f, fill = false)); Text("Custom", style = LabType.label)
                            }
                        }
                    }
                }
            }
        }
    }
}

@OptIn(ExperimentalLayoutApi::class)
@Composable
private fun ProfileChips(selected: String) {
    val c = LocalColors.current
    FlowRow(horizontalArrangement = Arrangement.spacedBy(8.dp), verticalArrangement = Arrangement.spacedBy(8.dp)) {
        listOf("Study", "Writing", "Coding").forEach { p ->
            val on = p == selected
            Surface(shape = CircleShape, color = if (on) c.ink else Color.Transparent, contentColor = if (on) c.onInk else c.ink, border = if (on) null else BorderStroke(1.dp, c.line)) {
                Text(p, style = LabType.label, modifier = Modifier.padding(horizontal = 18.dp, vertical = 11.dp))
            }
        }
    }
}

@Composable
fun SetupA() {
    val c = LocalColors.current
    LabShell(content = {
        Spacer(Modifier.height(12.dp))
        Row(verticalAlignment = Alignment.CenterVertically) {
            Padlock(LabState.READY, false, 104.dp, "per round")
            Spacer(Modifier.width(18.dp))
            Column {
                Text("Ends at ${Sample.clock(90)}", style = LabType.title, color = c.ink)
                Text("2 rounds · 10 min break", style = LabType.meta, color = c.inkMuted)
                Text("1 h 30 min in all", style = LabType.meta, color = c.inkMuted)
            }
        }
        Spacer(Modifier.height(28.dp))
        Question("What are you working on?")
        TaskUnderline(Sample.TASK)
        Spacer(Modifier.height(28.dp))
        Question("How long?")
        Spacer(Modifier.height(12.dp))
        Tiles(40)
        Row(Modifier.heightIn(min = 44.dp), verticalAlignment = Alignment.CenterVertically) {
            Text("2 rounds · 10 min break between  ", style = LabType.meta, color = c.inkMuted)
            Text("Change", style = LabType.strong.copy(textDecoration = androidx.compose.ui.text.style.TextDecoration.Underline), color = c.ink)
        }
        Spacer(Modifier.height(16.dp))
        Question("Which profile?")
        Spacer(Modifier.height(12.dp))
        ProfileChips("Study")
        Spacer(Modifier.height(16.dp))
    }, action = { Honest(); Primary("Start timer") })
}

@Composable
fun RunA(state: LabState, blocked: Boolean = false) {
    val c = LocalColors.current
    val caption = if (state == LabState.BREAK) "left in break" else "left in round 1"
    LabShell(content = {
        Column(Modifier.fillMaxWidth().padding(top = 36.dp), horizontalAlignment = Alignment.CenterHorizontally) {
            Padlock(state, blocked, 236.dp, caption)
            Spacer(Modifier.height(28.dp))
            Text(Sample.TASK, style = LabType.task, color = c.ink, textAlign = TextAlign.Center)
            Spacer(Modifier.height(6.dp))
            Text(
                when (state) {
                    LabState.FOCUS -> "Round 1 of 2 · Break at ${Sample.clock(40)}"
                    LabState.BREAK -> "Round 2 starts at ${Sample.clock(50)}"
                    LabState.DONE -> "80 min focused · ${Sample.clock(0)}–${Sample.clock(90)}"
                    LabState.READY -> ""
                },
                style = LabType.meta.copy(fontSize = 15.sp), color = c.inkMuted, textAlign = TextAlign.Center,
            )
        }
    }, action = {
        if (state == LabState.DONE) { Primary("Same again"); Ghost("Done", Modifier.fillMaxWidth()) }
        else { Honest(); Ghost("Need out?", Modifier.fillMaxWidth()) }
    })
}

/* ------------------------------ Direction B ------------------------------ */

@Composable
fun MiniLock(closed: Boolean, tint: Color, size: Dp = 16.dp) {
    Canvas(Modifier.size(size)) {
        val u = this.size.width / 24f
        val p = Path().apply {
            moveTo(7.5f * u, 11 * u)
            if (closed) { lineTo(7.5f * u, 8 * u); arcTo(androidx.compose.ui.geometry.Rect(7.5f * u, 3.5f * u, 16.5f * u, 12.5f * u), 180f, 180f, false); lineTo(16.5f * u, 11 * u) }
            else { lineTo(7.5f * u, 6.5f * u); arcTo(androidx.compose.ui.geometry.Rect(7.5f * u, 2f * u, 16.5f * u, 11f * u), 180f, 180f, false) }
        }
        drawPath(p, tint, style = Stroke(2 * u, cap = StrokeCap.Round))
        drawRoundRect(tint, Offset(4.5f * u, 11 * u), Size(15 * u, 10 * u), CornerRadius(3 * u))
    }
}

/** The session as a rail: widths follow minutes, the elapsed part is filled, breaks are dashed. */
@Composable
fun Rail(state: LabState, showLabels: Boolean = true) {
    val c = LocalColors.current
    val deep = deep()
    val elapsed = (Sample.now(state) - Sample.t0) / Sample.MIN.toFloat()
    Column {
        Row(Modifier.fillMaxWidth().height(8.dp), horizontalArrangement = Arrangement.spacedBy(5.dp)) {
            Sample.segments.forEach { (focus, s, e) ->
                val f = ((elapsed - s) / (e - s)).coerceIn(0f, 1f)
                Canvas(Modifier.weight((e - s).toFloat()).fillMaxSize()) {
                    val r = CornerRadius(size.height / 2)
                    if (focus) drawRoundRect(c.ink.copy(alpha = 0.12f), cornerRadius = r)
                    else { var x = 0f; while (x < size.width) { drawRoundRect(c.taupe, Offset(x, 0f), Size(4.dp.toPx(), size.height), r); x += 8.dp.toPx() } }
                    if (f > 0f) drawRoundRect(if (focus) c.ink else deep, size = Size(size.width * f, size.height), cornerRadius = r)
                }
            }
        }
        if (showLabels) Row(Modifier.fillMaxWidth().padding(top = 8.dp)) {
            Text(Sample.clock(0), style = LabType.meta.copy(fontSize = 13.sp), color = c.inkMuted)
            Spacer(Modifier.weight(1f))
            Text("Break ${Sample.clock(40)}", style = LabType.meta.copy(fontSize = 13.sp), color = c.inkMuted)
            Spacer(Modifier.weight(1f))
            Text(Sample.clock(90), style = LabType.meta.copy(fontSize = 13.sp), color = c.inkMuted)
        }
    }
}

@Composable
private fun RowSetting(label: String, value: String) {
    val c = LocalColors.current
    Row(Modifier.fillMaxWidth().heightIn(min = 52.dp), verticalAlignment = Alignment.CenterVertically) {
        Text(label, style = LabType.body, color = c.inkMuted, modifier = Modifier.weight(1f))
        Text(value, style = LabType.label, color = c.ink)
        Spacer(Modifier.width(6.dp))
        LockdIcon(LIcon.Chevron, size = 16.dp, tint = c.inkMuted)
    }
}

@Composable
fun SetupB() {
    val c = LocalColors.current
    val large = LocalDensity.current.fontScale > 1.3f
    LabShell(content = {
        Spacer(Modifier.height(8.dp))
        Text("I’m working on", style = LabType.meta, color = c.inkMuted)
        TaskUnderline(Sample.TASK)
        Spacer(Modifier.height(28.dp))
        Text("for", style = LabType.meta, color = c.inkMuted)
        Row(verticalAlignment = Alignment.CenterVertically, modifier = Modifier.fillMaxWidth()) {
            StepButton(LIcon.Minus, "5 minutes shorter")
            Row(Modifier.weight(1f), horizontalArrangement = Arrangement.Center, verticalAlignment = Alignment.Bottom) {
                Text("40", style = LabType.numeralsB.copy(fontSize = 96.sp, lineHeight = 96.sp), color = c.ink)
                Text(" min", style = LabType.title, color = c.inkMuted, modifier = Modifier.padding(bottom = 14.dp))
            }
            StepButton(LIcon.Plus, "5 minutes longer")
        }
        Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.Center) {
            listOf(25, 40, 60).forEach { m ->
                val on = m == 40
                Box(Modifier.padding(horizontal = 4.dp).heightIn(min = 44.dp).widthIn(min = 56.dp).background(if (on) c.taupeSoft else Color.Transparent, CircleShape), contentAlignment = Alignment.Center) {
                    Text("$m", style = LabType.label.copy(fontWeight = if (on) FontWeight.SemiBold else FontWeight.Medium), color = if (on) c.ink else c.inkMuted)
                }
            }
        }
        Spacer(Modifier.height(24.dp))
        Rail(LabState.READY, showLabels = !large)
        Spacer(Modifier.height(16.dp))
        HorizontalDivider(color = c.line)
        RowSetting("Rounds", "2 · 10 min break")
        HorizontalDivider(color = c.line)
        RowSetting("Profile", "Study")
        HorizontalDivider(color = c.line)
        Spacer(Modifier.height(12.dp))
    }, action = { Honest(); Primary("Start timer · ends ${Sample.clock(90)}") })
}

@Composable
private fun StepButton(icon: LIcon, description: String) {
    val c = LocalColors.current
    Box(Modifier.size(48.dp).border(1.dp, c.line, CircleShape), contentAlignment = Alignment.Center) { LockdIcon(icon, size = 20.dp, tint = c.ink, description = description) }
}

@Composable
fun RunB(state: LabState, blocked: Boolean = false) {
    val c = LocalColors.current
    val deep = deep()
    val status = when (state) { LabState.FOCUS -> "Focusing · round 1 of 2"; LabState.BREAK -> "On a break · round 2 at ${Sample.clock(50)}"; LabState.DONE -> "Complete"; LabState.READY -> "Ready" }
    LabShell(content = {
        Column(Modifier.padding(top = 32.dp)) {
            Text(Sample.TASK, style = LabType.title, color = c.ink)
            Spacer(Modifier.height(20.dp))
            Row(verticalAlignment = Alignment.CenterVertically) {
                MiniLock(blocked && state == LabState.FOCUS, if (state == LabState.FOCUS) c.ink else c.inkMuted)
                Spacer(Modifier.width(8.dp))
                Text(status, style = LabType.strong, color = if (state == LabState.FOCUS) c.ink else c.inkMuted)
            }
            Text(
                if (state == LabState.DONE) "Done" else Sample.countdown(Sample.remaining(state)),
                style = LabType.numeralsB.copy(fontSize = 104.sp, lineHeight = 108.sp),
                color = when (state) { LabState.BREAK -> deep; LabState.DONE -> c.inkMuted; else -> c.ink },
                maxLines = 1,
            )
            Spacer(Modifier.height(12.dp))
            Rail(state)
            Spacer(Modifier.height(24.dp))
            HorizontalDivider(color = c.line)
            if (state == LabState.DONE) RowFact("Focused", "80 min")
            else RowFact(if (state == LabState.BREAK) "Focus resumes" else "Next break", if (state == LabState.BREAK) Sample.clock(50) else Sample.clock(40))
            HorizontalDivider(color = c.line)
            RowFact("Session ends", Sample.clock(90))
            HorizontalDivider(color = c.line)
        }
    }, action = {
        if (state == LabState.DONE) { Primary("Same again"); Ghost("Done", Modifier.fillMaxWidth()) }
        else { Honest(); Ghost("Need out?", Modifier.fillMaxWidth()) }
    })
}

@Composable
private fun RowFact(label: String, value: String) {
    val c = LocalColors.current
    Row(Modifier.fillMaxWidth().heightIn(min = 48.dp), verticalAlignment = Alignment.CenterVertically) {
        Text(label, style = LabType.body, color = c.inkMuted, modifier = Modifier.weight(1f))
        Text(value, style = LabType.label.copy(fontFeatureSettings = "tnum"), color = c.ink)
    }
}

/** All timer states side by side for review. */
@Composable
fun StatesSheet() {
    val c = LocalColors.current
    Column(Modifier.fillMaxSize().background(c.ground).padding(20.dp), verticalArrangement = Arrangement.spacedBy(20.dp)) {
        Text("Direction A · padlock", style = LabType.strong, color = c.inkMuted)
        Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
            listOf(Triple(LabState.READY, false, "per round"), Triple(LabState.FOCUS, false, "left in round 1"), Triple(LabState.FOCUS, true, "left in round 1"), Triple(LabState.BREAK, false, "left in break"), Triple(LabState.DONE, false, "")).forEach { (s, b, cap) ->
                Padlock(s, b, 70.dp, cap)
            }
        }
        Text("Ready · timer only · blocking verified · break · complete", style = LabType.meta.copy(fontSize = 12.sp), color = c.inkMuted)
        Text("Direction B · numerals", style = LabType.strong, color = c.inkMuted)
        listOf(LabState.FOCUS to false, LabState.FOCUS to true, LabState.BREAK to false).forEach { (s, b) ->
            Row(verticalAlignment = Alignment.CenterVertically) {
                MiniLock(b && s == LabState.FOCUS, c.ink)
                Spacer(Modifier.width(8.dp))
                Text(Sample.countdown(Sample.remaining(s)), style = LabType.numeralsB.copy(fontSize = 40.sp), color = if (s == LabState.BREAK) deep() else c.ink)
                Spacer(Modifier.width(12.dp))
                Text(if (s == LabState.BREAK) "break" else if (b) "focusing, blocking verified" else "focusing, timer only", style = LabType.meta, color = c.inkMuted)
            }
        }
    }
}
