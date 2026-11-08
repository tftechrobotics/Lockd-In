// Month calendar for Schedule (owner 2026-11-08, R59): a real month grid. Days with a planned
// session carry a dot; tapping a day shows that day's sessions and free time below.
package app.lockdin.ui

import androidx.compose.animation.animateColorAsState
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.aspectRatio
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.material3.IconButton
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.semantics.Role
import androidx.compose.ui.semantics.clearAndSetSemantics
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.heading
import androidx.compose.ui.semantics.role
import androidx.compose.ui.semantics.selected
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import java.time.LocalDate
import java.time.YearMonth
import java.time.format.DateTimeFormatter
import java.time.format.TextStyle
import java.util.Locale

private val monthFmt = DateTimeFormatter.ofPattern("MMMM yyyy")
private val spokenFmt = DateTimeFormatter.ofPattern("EEEE, MMMM d")

@Composable
fun MonthCalendar(
    month: YearMonth,
    onMonth: (YearMonth) -> Unit,
    selected: LocalDate,
    today: LocalDate,
    planned: (LocalDate) -> Int,
    onSelect: (LocalDate) -> Unit,
) {
    val c = LocalColors.current
    val week = localWeek()
    Column(Modifier.fillMaxWidth()) {
        Row(verticalAlignment = Alignment.CenterVertically) {
            Text(month.atDay(1).format(monthFmt), style = Type.title, color = c.ink, modifier = Modifier.weight(1f).semantics { heading() })
            IconButton(onClick = { onMonth(month.minusMonths(1)) }, enabled = month > YearMonth.from(today)) {
                LockdIcon(LIcon.Back, description = "Previous month", tint = if (month > YearMonth.from(today)) c.ink else c.line)
            }
            IconButton(onClick = { onMonth(month.plusMonths(1)) }) { LockdIcon(LIcon.Chevron, description = "Next month") }
        }
        Spacer(Modifier.height(Dim.s2))
        Row(Modifier.fillMaxWidth().clearAndSetSemantics { }) {
            week.forEach {
                Text(it.getDisplayName(TextStyle.NARROW, Locale.getDefault()), style = UiType.caption, color = c.inkMuted,
                    textAlign = TextAlign.Center, modifier = Modifier.weight(1f))
            }
        }
        Spacer(Modifier.height(Dim.s1))
        val first = month.atDay(1)
        val offset = Math.floorMod(first.dayOfWeek.value - week.first().value, 7)
        val cells = offset + month.lengthOfMonth()
        val rows = (cells + 6) / 7
        for (r in 0 until rows) {
            Row(Modifier.fillMaxWidth()) {
                for (col in 0 until 7) {
                    val n = r * 7 + col - offset + 1
                    Box(Modifier.weight(1f).aspectRatio(1f), contentAlignment = Alignment.Center) {
                        if (n in 1..month.lengthOfMonth()) {
                            val d = month.atDay(n)
                            DayCell(d, d == selected, d == today, d.isBefore(today), planned(d)) { onSelect(d) }
                        }
                    }
                }
            }
        }
    }
}

@Composable
private fun DayCell(d: LocalDate, isSelected: Boolean, isToday: Boolean, past: Boolean, count: Int, onClick: () -> Unit) {
    val c = LocalColors.current
    val fill by animateColorAsState(if (isSelected) c.ink else Color.Transparent, label = "day")
    val text = when { isSelected -> c.onInk; past -> c.inkMuted.copy(alpha = 0.6f); else -> c.ink }
    val spoken = buildString {
        append(d.format(spokenFmt))
        if (isToday) append(", today")
        if (count > 0) append(", $count session${if (count > 1) "s" else ""} planned")
    }
    Box(
        Modifier.size(44.dp).clip(CircleShape).background(fill, CircleShape)
            .then(if (isToday && !isSelected) Modifier.border(1.5.dp, c.ink, CircleShape) else Modifier)
            .clickable(onClick = onClick)
            .testTag("day-$d")
            .semantics(mergeDescendants = true) { contentDescription = spoken; selected = isSelected; role = Role.Button },
        contentAlignment = Alignment.Center,
    ) {
        Column(horizontalAlignment = Alignment.CenterHorizontally) {
            Text("${d.dayOfMonth}", color = text, modifier = Modifier.clearAndSetSemantics { },
                style = Type.fact.copy(fontSize = 16.sp, lineHeight = 18.sp, fontWeight = if (isToday || isSelected) FontWeight.SemiBold else FontWeight.Normal))
            Box(Modifier.padding(top = 2.dp).size(5.dp).background(if (count > 0) (if (isSelected) c.onInk else c.ink) else Color.Transparent, CircleShape))
        }
    }
}

/** "Today", "Tomorrow" or "Tuesday, November 10". */
fun dayTitle(d: LocalDate, today: LocalDate): String = when (d) {
    today -> "Today"
    today.plusDays(1) -> "Tomorrow"
    else -> d.format(spokenFmt)
}

