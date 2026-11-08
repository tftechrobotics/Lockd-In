// History tab (roadmap step 2, B): finished sessions on this phone, a week at a glance, and
// each session's summary. Plain numbers only: no scores, streaks or judgements.
package app.lockdin.ui

import androidx.activity.compose.BackHandler
import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.Canvas
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.size
import androidx.compose.ui.unit.sp
import androidx.compose.ui.draw.clip
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.IconButton
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.geometry.CornerRadius
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.geometry.Size
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.semantics.Role
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.heading
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import app.lockdin.engine.History
import app.lockdin.engine.HistoryEntry
import app.lockdin.engine.Outcome
import app.lockdin.state.AppClock
import app.lockdin.state.HistoryStore
import app.lockdin.state.Store
import java.time.LocalDate
import java.time.ZoneId
import java.time.format.DateTimeFormatter
import java.time.format.TextStyle
import java.util.Locale

fun outcomeText(o: Outcome) = when (o) {
    Outcome.COMPLETED -> "Finished as planned"
    Outcome.ENDED_EARLY -> "Ended early with an unlock"
    Outcome.EMERGENCY -> "Ended with emergency recovery"
    Outcome.STOPPED -> "Stopped"
}

fun minutesLabel(m: Long): String = when {
    m < 60 -> "$m min"
    m % 60 == 0L -> "${m / 60} h"
    else -> "${m / 60} h ${m % 60} min"
}

@Composable
fun HistoryScreen(store: Store, history: HistoryStore, clock: AppClock, zone: ZoneId, onTryUnlocked: () -> Unit = {}) {
    val c = LocalColors.current
    val now = clock.now()
    val today = History.dateOf(now, zone)
    var weekStart by rememberSaveable { mutableStateOf(History.weekStart(today).toString()) }
    var openId by rememberSaveable { mutableStateOf<String?>(null) }
    var deleteDay by remember { mutableStateOf<LocalDate?>(null) }
    val entries = history.entries
    val open = entries.firstOrNull { it.id == openId }
    if (open != null) {
        BackHandler { openId = null }
        HistoryDetail(open, zone, now, onBack = { openId = null }, onDelete = { history.delete(open.id); openId = null })
        return
    }
    val ws = LocalDate.parse(weekStart)
    val week = remember(entries, weekStart, store.state.schedule, now / 60_000) { History.week(entries, ws, zone, store.state.schedule, now) }
    val days = remember(entries) { History.byDay(entries, zone) }

    LazyColumn(
        Modifier.fillMaxSize().testTag("history-list"),
        contentPadding = PaddingValues(start = Dim.gutter, end = Dim.gutter, top = LocalTopInset.current + Dim.s2, bottom = Dim.s5),
        verticalArrangement = Arrangement.spacedBy(Dim.s4),
    ) {
        item {
            Column(verticalArrangement = Arrangement.spacedBy(Dim.s1)) {
                ScreenTitle("History")
                Text("Sessions you’ve finished, kept on this phone only.", style = UiType.caption, color = c.inkMuted)
            }
        }
        if (entries.isEmpty()) {
            item {
                Surface(shape = RoundedCornerShape(Dim.plate), color = androidx.compose.ui.graphics.Color.Transparent, modifier = Modifier.fillMaxWidth().plate()) {
                    Column(Modifier.padding(horizontal = plateSide(Dim.s4), vertical = Dim.s4), verticalArrangement = Arrangement.spacedBy(Dim.s1)) {
                        Text("No sessions yet", style = UiType.label.copy(fontWeight = FontWeight.SemiBold), color = c.ink)
                        Text("When you finish a session, its time and what you tried to open appear here.", style = UiType.body, color = c.inkMuted)
                    }
                }
            }
            return@LazyColumn
        }
        item(key = "streak") {
            val streak = remember(entries) { app.lockdin.engine.Streak.compute(entries, zone) }
            StreakCard(streak, store.state.active == null, onTryUnlocked)
        }
        item {
            androidx.compose.material3.HorizontalDivider(thickness = 1.5.dp, color = c.inkMuted.copy(alpha = 0.45f))
        }
        item {
            WeekCard(
                week, isCurrent = ws == History.weekStart(today),
                onPrev = { weekStart = ws.minusWeeks(1).toString() },
                onNext = { if (ws < History.weekStart(today)) weekStart = ws.plusWeeks(1).toString() },
            )
        }
        days.forEach { (date, list) ->
            item(key = "day-$date") {
                // Owner 2026-11-08: each day starts under a clear rule so days don't run together.
                androidx.compose.material3.HorizontalDivider(thickness = 1.5.dp, color = c.inkMuted.copy(alpha = 0.45f), modifier = Modifier.padding(top = Dim.s3))
                Row(Modifier.fillMaxWidth().padding(top = Dim.s3), verticalAlignment = Alignment.CenterVertically) {
                    Text(dayHeading(date, today), style = Type.title, color = c.ink,
                        modifier = Modifier.weight(1f).semantics { heading() })
                    TextAction("Delete day", { deleteDay = date })
                }
            }
            item(key = "list-$date") {
                SettingsGroup {
                    list.forEachIndexed { i, e ->
                        if (i > 0) GroupDivider()
                        EntryRow(e, zone) { openId = e.id }
                    }
                }
            }
        }
    }
    deleteDay?.let { d ->
        val ids = days.firstOrNull { it.first == d }?.second?.map { it.id }?.toSet() ?: emptySet()
        AlertDialog(
            onDismissRequest = { deleteDay = null }, containerColor = c.surface,
            title = { Text("Delete ${dayHeading(d, today).lowercase().replaceFirstChar { it.uppercase() }}?", style = UiType.title, color = c.ink) },
            text = { Text("${ids.size} session${if (ids.size == 1) "" else "s"} will be removed from history. This can’t be undone.", style = UiType.body, color = c.inkMuted) },
            confirmButton = { TextButton(onClick = { history.deleteDay(ids); deleteDay = null }) { Text("Delete", style = UiType.label, color = c.danger) } },
            dismissButton = { TextButton(onClick = { deleteDay = null }) { Text("Keep", style = UiType.label, color = c.ink) } },
        )
    }
}

private fun dayHeading(d: LocalDate, today: LocalDate): String = when (d) {
    today -> "Today"
    today.minusDays(1) -> "Yesterday"
    else -> d.format(DateTimeFormatter.ofPattern(if (d.year == today.year) "EEEE, MMM d" else "EEEE, MMM d, yyyy", Locale.getDefault()))
}

@Composable
private fun WeekCard(w: app.lockdin.engine.WeekSummary, isCurrent: Boolean, onPrev: () -> Unit, onNext: () -> Unit) {
    val c = LocalColors.current
    val max = maxOf(30L, w.days.maxOf { it.focusedMinutes })
    val spoken = w.days.joinToString(", ") { "${it.date.dayOfWeek.getDisplayName(TextStyle.FULL, Locale.getDefault())} ${it.focusedMinutes} minutes" }
    Surface(shape = RoundedCornerShape(Dim.plate), color = androidx.compose.ui.graphics.Color.Transparent, modifier = Modifier.fillMaxWidth().plate()) {
        Column(Modifier.padding(horizontal = plateSide(Dim.s4), vertical = Dim.s4), verticalArrangement = Arrangement.spacedBy(Dim.s3)) {
            Row(verticalAlignment = Alignment.CenterVertically) {
                Text(
                    if (isCurrent) "This week" else "Week of ${w.start.format(DateTimeFormatter.ofPattern("MMM d", Locale.getDefault()))}",
                    style = UiType.label.copy(fontWeight = FontWeight.SemiBold), color = c.ink, modifier = Modifier.weight(1f).semantics { heading() },
                )
                IconButton(onClick = onPrev) { LockdIcon(LIcon.Back, size = 20.dp, tint = c.ink, description = "Previous week") }
                IconButton(onClick = onNext, enabled = !isCurrent) {
                    LockdIcon(LIcon.Chevron, size = 20.dp, tint = if (isCurrent) c.line else c.ink, description = "Next week")
                }
            }
            Text(
                if (w.sessions == 0) "No sessions this week" else "${minutesLabel(w.focusedMinutes)} focused",
                style = TimerType.task, color = c.ink,
            )
            if (w.sessions > 0) Text(
                "${w.sessions} session${if (w.sessions == 1) "" else "s"} · ${w.completed} finished as planned" +
                    if (w.planned > 0) " · planned ${w.plannedDone} of ${w.planned} done" else "",
                style = UiType.caption, color = c.inkMuted,
            )
            // Minutes per day. Read out as a list; the drawing is decorative.
            Column(Modifier.semantics(mergeDescendants = true) { contentDescription = "Minutes focused: $spoken" }) {
                val bar = c.ink; val track = c.line
                Canvas(Modifier.fillMaxWidth().height(64.dp)) {
                    // Same columns as the day labels below: each bar is centred in its day's seventh.
                    val n = 7; val col = size.width / n; val bw = col * 0.62f
                    w.days.forEachIndexed { i, d ->
                        val x = i * col + (col - bw) / 2
                        drawRoundRect(track, Offset(x, size.height - 2.dp.toPx()), Size(bw, 2.dp.toPx()), CornerRadius(1.dp.toPx()))
                        if (d.focusedMinutes > 0) {
                            val h = (size.height * d.focusedMinutes / max).coerceAtLeast(3.dp.toPx())
                            drawRoundRect(bar, Offset(x, size.height - h), Size(bw, h), CornerRadius(3.dp.toPx()))
                        }
                    }
                }
                Spacer(Modifier.height(Dim.s1))
                Row(Modifier.fillMaxWidth()) {
                    w.days.forEach { d ->
                        Text(d.date.dayOfWeek.getDisplayName(TextStyle.NARROW, Locale.getDefault()), style = UiType.caption, color = c.inkMuted,
                            textAlign = TextAlign.Center, modifier = Modifier.weight(1f))
                    }
                }
            }
        }
    }
}

@Composable
private fun EntryRow(e: HistoryEntry, zone: ZoneId, onOpen: () -> Unit) {
    val c = LocalColors.current
    Row(
        Modifier.fillMaxWidth().heightIn(min = 64.dp).clickable(role = Role.Button, onClickLabel = "View summary", onClick = onOpen)
            .semantics(mergeDescendants = true) {}.padding(horizontal = plateSide(Dim.s4), vertical = Dim.s4),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        // Same shape as Schedule rows: the time first, then what it was, then the details.
        Column(Modifier.weight(1f), verticalArrangement = Arrangement.spacedBy(2.dp)) {
            Text("${Fmt.clock(e.start, zone)} – ${Fmt.clock(e.end, zone)}", style = Type.fact, color = c.ink)
            Text(e.task ?: "Focus session", style = UiType.label.copy(fontWeight = FontWeight.SemiBold), color = c.ink, maxLines = 1)
            Text("${minutesLabel(e.focusedMinutes)} focused · ${e.profileName}", style = UiType.caption, color = c.inkMuted, maxLines = 2)
            if (e.outcome != Outcome.COMPLETED) Text(outcomeText(e.outcome), style = UiType.caption, color = c.ink)
            if (e.unlocked) Text(if (e.blocked.isEmpty()) "Unlocked · nothing opened" else "Unlocked · blocked apps opened", style = UiType.caption, color = c.ink)
        }
        LockdIcon(LIcon.Chevron, size = 18.dp, tint = c.inkMuted)
    }
}

@Composable
private fun HistoryDetail(e: HistoryEntry, zone: ZoneId, now: Long, onBack: () -> Unit, onDelete: () -> Unit) {
    val c = LocalColors.current
    val ctx = LocalContext.current
    var confirm by remember { mutableStateOf(false) }
    val blocked = remember(e.blocked) {
        e.blocked.entries.sortedByDescending { it.value }.map { (k, n) ->
            (if (k.startsWith("site:")) k.removePrefix("site:") else app.lockdin.enforce.InstalledApps.label(ctx, k)) to n
        }
    }
    ScrollScreen {
        Row(verticalAlignment = Alignment.CenterVertically) {
            TextButton(onClick = onBack, modifier = Modifier.heightIn(min = Dim.control)) {
                LockdIcon(LIcon.Back, size = 18.dp, tint = c.ink)
                Spacer(Modifier.padding(start = Dim.s1))
                Text("History", style = UiType.label, color = c.ink)
            }
        }
        Column(verticalArrangement = Arrangement.spacedBy(Dim.s1)) {
            ScreenTitle(e.task ?: "Focus session")
            Text("${Fmt.dayLabel(e.start, now, zone)} · ${outcomeText(e.outcome)}", style = UiType.caption, color = c.inkMuted)
        }
        FactList(buildList {
            add("Focused" to "${minutesLabel(e.focusedMinutes)} of ${minutesLabel(e.plannedFocusMs / 60_000)}")
            add("Time" to "${Fmt.clock(e.start, zone)}–${Fmt.clock(e.end, zone)}")
            if (e.roundsPlanned > 1) add("Rounds" to "${e.roundsDone} of ${e.roundsPlanned}")
            add("Profile" to e.profileName)
            e.occurrenceKey?.let { add("Started" to if (it.startsWith("PLACE:")) "When you arrived at a place" else "From your schedule") }
        })
        Column {
            SectionLabel("Blocked during this session")
            if (blocked.isEmpty()) Text("Nothing blocked was opened.", style = UiType.body, color = c.inkMuted)
            else FactList(blocked.take(10).map { (name, n) -> name to "$n ${if (n == 1) "time" else "times"}" })
        }
        TextAction("Delete this session", { confirm = true }, danger = true)
    }
    if (confirm) AlertDialog(
        onDismissRequest = { confirm = false }, containerColor = c.surface,
        title = { Text("Delete this session?", style = UiType.title, color = c.ink) },
        text = { Text("It will be removed from history. This can’t be undone.", style = UiType.body, color = c.inkMuted) },
        confirmButton = { TextButton(onClick = { confirm = false; onDelete() }) { Text("Delete", style = UiType.label, color = c.danger) } },
        dismissButton = { TextButton(onClick = { confirm = false }) { Text("Keep", style = UiType.label, color = c.ink) } },
    )
}

/**
 * Focus streak (R62; ring design R63 from the owner's reference): one tick per day around a dial —
 * filled clockwise from the top for days done, light for days ahead — with the count in the middle.
 * At 30 days it offers an unlocked session, which only starts if the person taps it.
 */
@Composable
private fun StreakCard(st: app.lockdin.engine.Streak.Result, canStart: Boolean, onTryUnlocked: () -> Unit) {
    val c = LocalColors.current
    val goal = app.lockdin.engine.Streak.GOAL
    val shown = st.days.coerceAtMost(goal)
    // Ticks fill one after another when the screen opens.
    val fill by androidx.compose.animation.core.animateFloatAsState(
        shown.toFloat(), androidx.compose.animation.core.tween(if (reducedMotion()) 0 else 60 * shown.coerceAtLeast(1), easing = androidx.compose.animation.core.LinearEasing), label = "streak",
    )
    val ahead = c.taupe
    Column(Modifier.fillMaxWidth(), horizontalAlignment = Alignment.CenterHorizontally, verticalArrangement = Arrangement.spacedBy(Dim.s3)) {
        Text("Focus streak", style = Type.title, color = c.ink, modifier = Modifier.fillMaxWidth().semantics { heading() })
        Box(
            Modifier.padding(vertical = Dim.s2).size(260.dp).semantics(mergeDescendants = true) {
                contentDescription = "Focus streak: ${st.days} of $goal days" + if (st.best > st.days) ", best ${st.best}" else ""
            },
            contentAlignment = Alignment.Center,
        ) {
            androidx.compose.foundation.Canvas(Modifier.matchParentSize()) {
                val r = size.minDimension / 2
                val outer = r - 4.dp.toPx(); val inner = outer - 22.dp.toPx()
                val w = 7.dp.toPx()
                for (i in 0 until goal) {
                    val a = Math.toRadians((i * 360.0 / goal) - 90.0)
                    val done = i < fill
                    val col = if (done) c.ink else ahead
                    val cs = kotlin.math.cos(a).toFloat(); val sn = kotlin.math.sin(a).toFloat()
                    drawLine(col, androidx.compose.ui.geometry.Offset(center.x + inner * cs, center.y + inner * sn),
                        androidx.compose.ui.geometry.Offset(center.x + outer * cs, center.y + outer * sn), strokeWidth = w, cap = androidx.compose.ui.graphics.StrokeCap.Round)
                }
            }
            Column(horizontalAlignment = Alignment.CenterHorizontally) {
                LockdIcon(LIcon.Focus, size = 30.dp, tint = c.ink)
                Text("${st.days}", style = Type.timer.copy(fontSize = 72.sp, lineHeight = 76.sp), color = c.ink)
                Text(if (st.days > goal) "days" else "of $goal days", style = Type.fact, color = c.ink)
            }
        }
        Text("One day at a time.", style = Type.title.copy(fontFamily = LiterataItalic, fontStyle = androidx.compose.ui.text.font.FontStyle.Italic, fontWeight = FontWeight.Medium, fontSize = 26.sp, lineHeight = 32.sp), color = c.ink)
        Row(horizontalArrangement = Arrangement.spacedBy(Dim.s5), verticalAlignment = Alignment.CenterVertically) {
            LegendItem(c.ink, "Completed day")
            LegendItem(ahead, "Day ahead")
        }
        Text(
            if (st.reachedGoal) "Goal reached" else "${goal - st.days} day${if (goal - st.days == 1) "" else "s"} until your next milestone",
            style = UiType.body, color = c.inkMuted,
        )
        Text(
            "A day counts when your sessions finish without ending early or a blocked app coming up. Days without a session don’t count against it.",
            style = UiType.caption, color = c.inkMuted, textAlign = androidx.compose.ui.text.style.TextAlign.Center,
        )
        val now = LocalDate.now()
        if (st.lastBreak != null && st.days < 7 && st.lastBreak >= now.minusDays(14)) {
            val h = dayHeading(st.lastBreak, now)
            val whenText = if (h == "Today" || h == "Yesterday") h.lowercase() else "on $h"
            Text("Started over $whenText: ${st.lastBreakReason}.", style = UiType.caption, color = c.ink, textAlign = androidx.compose.ui.text.style.TextAlign.Center)
        }
        if (st.best > st.days) Text("Best so far: ${st.best} days", style = UiType.caption, color = c.inkMuted)
        if (st.reachedGoal) {
            Spacer(Modifier.height(Dim.s1))
            Column(Modifier.fillMaxWidth(), verticalArrangement = Arrangement.spacedBy(Dim.s2)) {
                Text("$goal days without slipping. Try a session with nothing locked?", style = UiType.label.copy(fontWeight = FontWeight.SemiBold), color = c.ink)
                Text(
                    "Your apps stay open. Lockd In only notes if you open one this profile would block. It’s a check on your own control, not the app’s." +
                        if (st.unlockedClean > 0) " You’ve done ${st.unlockedClean} cleanly so far." else "",
                    style = UiType.caption, color = c.inkMuted,
                )
                if (canStart) GlassButton("Try an unlocked session", onTryUnlocked)
                else Text("Available when no session is running.", style = UiType.caption, color = c.inkMuted)
            }
        }
    }
}

@Composable
private fun LegendItem(color: androidx.compose.ui.graphics.Color, label: String) {
    val c = LocalColors.current
    Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(Dim.s2)) {
        Box(Modifier.size(width = 26.dp, height = 7.dp).clip(RoundedCornerShape(4.dp)).background(color))
        Text(label, style = UiType.caption, color = c.ink)
    }
}
