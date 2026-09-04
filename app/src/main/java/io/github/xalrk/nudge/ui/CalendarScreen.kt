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

data class Occurrence(val reminder: Reminder, val at: ZonedDateTime)

@Composable
fun CalendarScreen(vm: NudgeViewModel, onAdd: () -> Unit, onOpen: (Long) -> Unit) {
    val reminders by vm.reminders.collectAsStateWithLifecycle()
    var month by rememberSaveable { mutableStateOf(YearMonth.now().toString()) }
    var selected by rememberSaveable { mutableStateOf(LocalDate.now().toString()) }
    val ym = YearMonth.parse(month)
    val selectedDate = LocalDate.parse(selected)
    val zone = ZoneId.systemDefault()

    val occurrences = remember(reminders, ym) {
        val from = ym.atDay(1).atStartOfDay(zone)
        val to = ym.plusMonths(1).atDay(1).atStartOfDay(zone)
        reminders.filter { it.isScheduled && it.enabled }
            .flatMap { r -> Recurrence.occurrencesBetween(r, from, to).map { Occurrence(r, it.withZoneSameInstant(zone)) } }
            .groupBy { it.at.toLocalDate() }
    }
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
                MonthGrid(ym, selectedDate, today, occurrences.keys) { selected = it.toString() }
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
            items(dayList, key = { "${it.reminder.id}-${it.at.toInstant().toEpochMilli()}" }) { occ ->
                ReminderRow(occ.reminder, subtitle = occ.at.format(Fmt.time) + " · " + Recurrence.describe(occ.reminder), onClick = { onOpen(occ.reminder.id) })
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
private fun MonthGrid(ym: YearMonth, selected: LocalDate, today: LocalDate, marked: Set<LocalDate>, onSelect: (LocalDate) -> Unit) {
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
                                Box(
                                    Modifier.padding(top = 3.dp).size(5.dp).clip(CircleShape)
                                        .background(if (date in marked) MaterialTheme.colorScheme.tertiary else Color.Transparent)
                                )
                            }
                        }
                    }
                }
            }
        }
    }
}
