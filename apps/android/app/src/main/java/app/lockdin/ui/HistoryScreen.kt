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
        Modifier.fillMaxSize(),
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
 * Focus streak (R62): days in a row with clean sessions, as a bar toward 30 days. At 30 it offers an
 * unlocked session, which only starts if the person taps it.
 */
@Composable
private fun StreakCard(st: app.lockdin.engine.Streak.Result, canStart: Boolean, onTryUnlocked: () -> Unit) {
    val c = LocalColors.current
    val goal = app.lockdin.engine.Streak.GOAL
    val fill by androidx.compose.animation.core.animateFloatAsState(st.progress, androidx.compose.animation.core.tween(if (reducedMotion()) 0 else 700), label = "streak")
    Column(
        Modifier.fillMaxWidth().semantics(mergeDescendants = true) {
            contentDescription = "Focus streak: ${st.days} of $goal days" + if (st.best > st.days) ", best ${st.best}" else ""
        },
        verticalArrangement = Arrangement.spacedBy(Dim.s2),
    ) {
        Text("Focus streak", style = UiType.section, color = c.inkMuted, modifier = Modifier.semantics { heading() })
        Row(verticalAlignment = Alignment.Bottom) {
            Text("${st.days}", style = Type.timer.copy(fontSize = 40.sp, lineHeight = 44.sp), color = c.ink)
            Text(if (st.days == 1) " day" else " days", style = Type.fact, color = c.ink, modifier = Modifier.padding(bottom = 6.dp))
            Spacer(Modifier.weight(1f))
            Text(if (st.reachedGoal) "Goal reached" else "${goal - st.days} to go", style = UiType.caption, color = c.inkMuted, modifier = Modifier.padding(bottom = 6.dp))
        }
        // The bar: a glass track with an ink fill.
        Box(Modifier.fillMaxWidth().height(12.dp).clip(RoundedCornerShape(6.dp)).glass(RoundedCornerShape(6.dp))) {
            Box(Modifier.fillMaxWidth(fill).height(12.dp).clip(RoundedCornerShape(6.dp)).background(c.ink))
        }
        Text(
            "Days in a row where your sessions finished without ending early or a blocked app coming up. Days without a session don’t count against it.",
            style = UiType.caption, color = c.inkMuted,
        )
        val now = LocalDate.now()
        if (st.lastBreak != null && st.days < 7 && st.lastBreak >= now.minusDays(14)) {
            val h = dayHeading(st.lastBreak, now)
            val whenText = if (h == "Today" || h == "Yesterday") h.lowercase() else "on $h"
            Text("Started over $whenText: ${st.lastBreakReason}.", style = UiType.caption, color = c.ink)
        }
        if (st.best > st.days) Text("Best so far: ${st.best} days", style = UiType.caption, color = c.inkMuted)
        if (st.reachedGoal) {
            Spacer(Modifier.height(Dim.s1))
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
