package io.github.xalrk.nudge.ui

import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.border
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.ui.graphics.Color
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.KeyboardArrowLeft
import androidx.compose.material.icons.automirrored.filled.KeyboardArrowRight
import androidx.compose.material.icons.filled.Add
import androidx.compose.material.icons.filled.Check
import androidx.compose.material3.FloatingActionButton
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.material3.TopAppBar
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextDecoration
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import io.github.xalrk.nudge.data.Reminder
import io.github.xalrk.nudge.domain.Recurrence
import java.time.DayOfWeek
import java.time.LocalDate
import java.time.YearMonth
import java.time.ZoneId
import java.time.ZonedDateTime

/** One entry on a calendar day: either a future occurrence of a rule, or a notification that was delivered. */
data class Occurrence(val at: ZonedDateTime, val title: String, val body: String, val reminderId: Long, val done: Boolean, val rule: String)

@Composable
fun CalendarScreen(vm: NudgeViewModel, onAdd: () -> Unit, onOpen: (Long) -> Unit) {
    val reminders by vm.reminders.collectAsStateWithLifecycle()
    val history by vm.history.collectAsStateWithLifecycle()
    var month by rememberSaveable { mutableStateOf(YearMonth.now().toString()) }
    var selected by rememberSaveable { mutableStateOf(LocalDate.now().toString()) }
    val ym = YearMonth.parse(month)
    val selectedDate = LocalDate.parse(selected)
    val zone = ZoneId.systemDefault()

    // Past = what was actually delivered (history); future = what the rules say will come.
    val occurrences = remember(reminders, history, ym) {
        val from = ym.atDay(1).atStartOfDay(zone)
        val to = ym.plusMonths(1).atDay(1).atStartOfDay(zone)
        val now = ZonedDateTime.now(zone)
        val upcomingOnes = reminders.filter { it.isScheduled && it.enabled }.flatMap { r ->
            Recurrence.occurrencesBetween(r, maxOf(from, now), to).map {
                Occurrence(it.withZoneSameInstant(zone), r.title, r.body, r.id, done = false, rule = Recurrence.describe(r))
            }
        }
        val doneOnes = history.map { Fmt.instant(it.firedAt) }.zip(history).mapNotNull { (at, e) ->
            if (at.isBefore(from) || !at.isBefore(to)) null
            else Occurrence(at, e.title, e.body, e.reminderId, done = true, rule = if (e.kind == io.github.xalrk.nudge.data.Kind.RANDOM) "Random" else "Delivered")
        }
        (upcomingOnes + doneOnes).groupBy { it.at.toLocalDate() }
    }
    val upcomingDays = remember(occurrences) { occurrences.filterValues { l -> l.any { !it.done } }.keys }
    val today = LocalDate.now()
    val dayList = (occurrences[selectedDate] ?: emptyList()).sortedBy { it.at }
    val upcoming = remember(reminders) {
        val now = System.currentTimeMillis()
        reminders.filter { it.isScheduled && it.enabled && it.nextAt != null && it.nextAt >= now }.sortedBy { it.nextAt }.take(5)
    }

    Scaffold(
        topBar = { TopAppBar(title = { Text("Nudge") }) },
        floatingActionButton = { FloatingActionButton(onClick = onAdd) { Icon(Icons.Filled.Add, contentDescription = "Add reminder") } },
    ) { padding ->
        LazyColumn(Modifier.fillMaxSize().padding(padding)) {
            item {
                Row(Modifier.fillMaxWidth().padding(horizontal = 8.dp), verticalAlignment = Alignment.CenterVertically) {
                    IconButton(onClick = { month = ym.minusMonths(1).toString() }) { Icon(Icons.AutoMirrored.Filled.KeyboardArrowLeft, "Previous month") }
                    Text(ym.format(Fmt.month), Modifier.weight(1f), textAlign = TextAlign.Center, style = MaterialTheme.typography.titleMedium)
                    IconButton(onClick = { month = ym.plusMonths(1).toString() }) { Icon(Icons.AutoMirrored.Filled.KeyboardArrowRight, "Next month") }
                }
                MonthGrid(ym, selectedDate, today, upcoming = upcomingDays, past = occurrences.keys - upcomingDays) { selected = it.toString() }
                if (ym != YearMonth.now() || selectedDate != today) {
                    TextButton(onClick = { month = YearMonth.now().toString(); selected = today.toString() }, Modifier.padding(horizontal = 8.dp)) { Text("Today") }
                }
                HorizontalDivider()
                Text(
                    selectedDate.format(Fmt.date),
                    Modifier.padding(16.dp, 12.dp, 16.dp, 4.dp),
                    style = MaterialTheme.typography.titleSmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                )
            }
            if (dayList.isEmpty()) item {
                Text("Nothing scheduled this day.", Modifier.padding(16.dp, 4.dp), color = MaterialTheme.colorScheme.onSurfaceVariant)
            }
            items(dayList, key = { "${it.done}-${it.reminderId}-${it.at.toInstant().toEpochMilli()}" }) { occ ->
                OccurrenceRow(occ, onClick = { if (reminders.any { it.id == occ.reminderId }) onOpen(occ.reminderId) })
            }
            if (upcoming.isNotEmpty()) {
                item {
                    HorizontalDivider(Modifier.padding(top = 8.dp))
                    Text("Coming up", Modifier.padding(16.dp, 12.dp, 16.dp, 4.dp), style = MaterialTheme.typography.titleSmall,
                        color = MaterialTheme.colorScheme.onSurfaceVariant)
                }
                items(upcoming, key = { "up-${it.id}" }) { r ->
                    ReminderRow(r, subtitle = Fmt.instant(r.nextAt!!).format(Fmt.dayTime) + " · " + Fmt.relative(r.nextAt), onClick = { onOpen(r.id) })
                }
            }
            item { Box(Modifier.size(80.dp)) }
        }
    }
}

@Composable
private fun OccurrenceRow(occ: Occurrence, onClick: () -> Unit) {
    val muted = MaterialTheme.colorScheme.onSurfaceVariant
    Row(
        Modifier.fillMaxWidth().clickable(onClick = onClick).padding(horizontal = 16.dp, vertical = 12.dp),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        if (occ.done) {
            Icon(Icons.Filled.Check, contentDescription = "Delivered", tint = muted, modifier = Modifier.size(18.dp).padding(end = 2.dp))
            Box(Modifier.size(8.dp))
        }
        Column(Modifier.weight(1f)) {
            Text(
                occ.title, style = MaterialTheme.typography.bodyLarge, maxLines = 2,
                color = if (occ.done) muted else MaterialTheme.colorScheme.onSurface,
                textDecoration = if (occ.done) TextDecoration.LineThrough else null,
            )
            if (occ.body.isNotBlank()) Text(occ.body, style = MaterialTheme.typography.bodySmall, maxLines = 2, color = muted)
            Text(
                occ.at.format(Fmt.time) + " · " + occ.rule,
                style = MaterialTheme.typography.labelMedium,
                color = if (occ.done) muted else MaterialTheme.colorScheme.primary,
            )
        }
    }
}

@Composable
private fun MonthGrid(ym: YearMonth, selected: LocalDate, today: LocalDate, upcoming: Set<LocalDate>, past: Set<LocalDate>, onSelect: (LocalDate) -> Unit) {
    val first = ym.atDay(1)
    val lead = (first.dayOfWeek.value - DayOfWeek.MONDAY.value + 7) % 7
    val cells = lead + ym.lengthOfMonth()
    val rows = (cells + 6) / 7
    val squircle = RoundedCornerShape(10.dp)
    Column(Modifier.padding(horizontal = 8.dp)) {
        Row(Modifier.fillMaxWidth()) {
            for (d in DayOfWeek.entries) {
                Text(d.name.take(1), Modifier.weight(1f), textAlign = TextAlign.Center,
                    style = MaterialTheme.typography.labelSmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
            }
        }
        for (row in 0 until rows) {
            Row(Modifier.fillMaxWidth()) {
                for (col in 0 until 7) {
                    val idx = row * 7 + col - lead
                    Box(Modifier.weight(1f).height(52.dp), contentAlignment = Alignment.TopCenter) {
                        if (idx in 0 until ym.lengthOfMonth()) {
                            val date = ym.atDay(idx + 1)
                            val isSel = date == selected
                            val isToday = date == today
                            Column(
                                Modifier.fillMaxSize().clip(squircle).clickable { onSelect(date) }.padding(top = 6.dp),
                                horizontalAlignment = Alignment.CenterHorizontally,
                            ) {
                                // Number sits centred in its own squircle: filled when selected, outlined for today.
                                Box(
                                    Modifier.size(32.dp).clip(squircle)
                                        .background(if (isSel) MaterialTheme.colorScheme.primary else Color.Transparent)
                                        .then(if (isToday && !isSel) Modifier.border(1.5.dp, MaterialTheme.colorScheme.primary, squircle) else Modifier),
                                    contentAlignment = Alignment.Center,
                                ) {
                                    Text(
                                        "${idx + 1}",
                                        style = MaterialTheme.typography.bodyMedium,
                                        fontWeight = if (isToday || isSel) FontWeight.SemiBold else FontWeight.Normal,
                                        color = when {
                                            isSel -> MaterialTheme.colorScheme.onPrimary
                                            isToday -> MaterialTheme.colorScheme.primary
                                            else -> MaterialTheme.colorScheme.onSurface
                                        },
                                    )
                                }
                                // Event dot lives below the highlight so its color is never inverted.
                                // Accent = something still to come; grey = only delivered ones that day.
                                Box(
                                    Modifier.padding(top = 3.dp).size(5.dp).clip(CircleShape).background(
                                        when (date) {
                                            in upcoming -> MaterialTheme.colorScheme.tertiary
                                            in past -> MaterialTheme.colorScheme.outline
                                            else -> Color.Transparent
                                        }
                                    )
                                )
                            }
                        }
                    }
                }
            }
        }
    }
}
