package io.github.xalrk.nudge.ui

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.material.icons.filled.Delete
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.Button
import androidx.compose.material3.DatePicker
import androidx.compose.material3.DatePickerDialog
import androidx.compose.material3.FilterChip
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Scaffold
import androidx.compose.material3.SegmentedButton
import androidx.compose.material3.SegmentedButtonDefaults
import androidx.compose.material3.SingleChoiceSegmentedButtonRow
import androidx.compose.material3.Switch
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.material3.TimePicker
import androidx.compose.material3.TopAppBar
import androidx.compose.material3.rememberDatePickerState
import androidx.compose.material3.rememberTimePickerState
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.text.input.KeyboardType
import androidx.compose.ui.unit.dp
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import io.github.xalrk.nudge.data.Kind
import io.github.xalrk.nudge.data.Reminder
import io.github.xalrk.nudge.data.Repeat
import io.github.xalrk.nudge.domain.Recurrence
import java.time.DayOfWeek
import java.time.Instant
import java.time.LocalDate
import java.time.LocalDateTime
import java.time.LocalTime
import java.time.ZoneId
import java.time.ZoneOffset
import java.time.format.TextStyle
import java.util.Locale

@Composable
fun EditReminderScreen(vm: NudgeViewModel, id: Long, defaultKind: String, onBack: () -> Unit) {
    val reminders by vm.reminders.collectAsStateWithLifecycle()
    val existing = reminders.firstOrNull { it.id == id }

    var loaded by remember { mutableStateOf(id == 0L) }
    var title by remember { mutableStateOf("") }
    var body by remember { mutableStateOf("") }
    var kind by remember { mutableStateOf(runCatching { Kind.valueOf(defaultKind) }.getOrDefault(Kind.SCHEDULED)) }
    var date by remember { mutableStateOf(LocalDate.now()) }
    var time by remember { mutableStateOf(LocalTime.now().plusHours(1).withMinute(0).withSecond(0).withNano(0)) }
    var repeat by remember { mutableStateOf(Repeat.NONE) }
    var intervalText by remember { mutableStateOf("1") }
    var weekdays by remember { mutableStateOf(setOf<DayOfWeek>()) }
    var endDate by remember { mutableStateOf<LocalDate?>(null) }
    var floating by remember { mutableStateOf(true) }
    var zoneId by remember { mutableStateOf(ZoneId.systemDefault().id) }
    var enabled by remember { mutableStateOf(true) }
    var showDate by remember { mutableStateOf(false) }
    var showTime by remember { mutableStateOf(false) }
    var showEnd by remember { mutableStateOf(false) }
    var confirmDelete by remember { mutableStateOf(false) }

    LaunchedEffect(existing) {
        if (!loaded && existing != null) {
            title = existing.title; body = existing.body; kind = existing.kind
            existing.localDateTimeOrNull()?.let { date = it.toLocalDate(); time = it.toLocalTime() }
            repeat = existing.repeat; intervalText = existing.interval.toString()
            weekdays = existing.weekdaySet(); endDate = existing.endDateOrNull()
            floating = existing.floating; zoneId = existing.zoneId ?: ZoneId.systemDefault().id
            enabled = existing.enabled
            loaded = true
        }
    }

    val isNew = id == 0L
    val canSave = title.isNotBlank()

    fun build(): Reminder {
        val base = existing ?: Reminder(title = "", kind = kind)
        return if (kind == Kind.RANDOM) base.copy(
            title = title.trim(), body = body.trim(), kind = Kind.RANDOM,
            localDateTime = null, zoneId = null, repeat = Repeat.NONE, interval = 1, weekdays = 0, endDate = null,
            enabled = enabled, nextAt = if (existing?.isRandom == true) existing.nextAt else null,
        ) else base.copy(
            title = title.trim(), body = body.trim(), kind = Kind.SCHEDULED,
            localDateTime = LocalDateTime.of(date, time).format(Reminder.DT_FORMAT),
            zoneId = if (existing?.zoneId != null && !floating) existing.zoneId else ZoneId.systemDefault().id,
            floating = floating, repeat = repeat, interval = intervalText.toIntOrNull()?.coerceAtLeast(1) ?: 1,
            weekdays = if (repeat == Repeat.WEEKLY) Reminder.maskOf(weekdays) else 0,
            endDate = if (repeat == Repeat.NONE) null else endDate?.toString(),
            enabled = true, nextAt = null, snoozeAt = null,
        )
    }

    Scaffold(
        topBar = {
            TopAppBar(
                title = { Text(if (isNew) "New reminder" else "Edit reminder") },
                navigationIcon = { IconButton(onClick = onBack) { Icon(Icons.AutoMirrored.Filled.ArrowBack, "Back") } },
                actions = {
                    if (!isNew) IconButton(onClick = { confirmDelete = true }) { Icon(Icons.Filled.Delete, "Delete") }
                    TextButton(enabled = canSave, onClick = { vm.save(build()) { onBack() } }) { Text("Save") }
                },
            )
        }
    ) { padding ->
        Column(Modifier.fillMaxSize().padding(padding).verticalScroll(rememberScrollState()).padding(16.dp), verticalArrangement = Arrangement.spacedBy(12.dp)) {
            OutlinedTextField(value = title, onValueChange = { title = it }, label = { Text("Message") }, modifier = Modifier.fillMaxWidth(), singleLine = true)
            OutlinedTextField(value = body, onValueChange = { body = it }, label = { Text("Details (optional)") }, modifier = Modifier.fillMaxWidth(), minLines = 2)

            SingleChoiceSegmentedButtonRow(Modifier.fillMaxWidth()) {
                SegmentedButton(selected = kind == Kind.SCHEDULED, onClick = { kind = Kind.SCHEDULED }, shape = SegmentedButtonDefaults.itemShape(0, 2)) { Text("Scheduled") }
                SegmentedButton(selected = kind == Kind.RANDOM, onClick = { kind = Kind.RANDOM }, shape = SegmentedButtonDefaults.itemShape(1, 2)) { Text("Random") }
            }

            if (kind == Kind.RANDOM) {
                Text("Fires at a random moment during your active hours, at the average frequency set in Settings.",
                    style = MaterialTheme.typography.bodyMedium, color = MaterialTheme.colorScheme.onSurfaceVariant)
                if (!isNew) Row(Modifier.fillMaxWidth(), verticalAlignment = Alignment.CenterVertically) {
                    Text("Enabled", Modifier.weight(1f)); Switch(checked = enabled, onCheckedChange = { enabled = it })
                }
            } else {
                Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                    OutlinedButton(onClick = { showDate = true }, Modifier.weight(1f)) { Text(date.format(Fmt.date)) }
                    OutlinedButton(onClick = { showTime = true }) { Text(time.format(Fmt.time)) }
                }

                Text("Repeat", style = MaterialTheme.typography.labelLarge)
                SingleChoiceSegmentedButtonRow(Modifier.fillMaxWidth()) {
                    val labels = listOf("Once", "Day", "Week", "Month", "Year")
                    Repeat.entries.forEachIndexed { i, r ->
                        SegmentedButton(selected = repeat == r, onClick = { repeat = r }, shape = SegmentedButtonDefaults.itemShape(i, Repeat.entries.size)) { Text(labels[i]) }
                    }
                }
                if (repeat != Repeat.NONE) {
                    Row(verticalAlignment = Alignment.CenterVertically) {
                        Text("Every")
                        Spacer(Modifier.width(8.dp))
                        OutlinedTextField(
                            value = intervalText, onValueChange = { intervalText = it.filter(Char::isDigit).take(3) },
                            modifier = Modifier.width(80.dp), singleLine = true,
                            keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Number),
                        )
                        Spacer(Modifier.width(8.dp))
                        val n = intervalText.toIntOrNull() ?: 1
                        Text(when (repeat) { Repeat.DAILY -> "day"; Repeat.WEEKLY -> "week"; Repeat.MONTHLY -> "month"; Repeat.YEARLY -> "year"; else -> "" } + if (n == 1) "" else "s")
                    }
                    if (repeat == Repeat.WEEKLY) {
                        Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.spacedBy(4.dp)) {
                            for (d in DayOfWeek.entries) {
                                FilterChip(
                                    selected = d in weekdays || (weekdays.isEmpty() && d == date.dayOfWeek),
                                    onClick = { weekdays = if (d in weekdays) weekdays - d else weekdays + d },
                                    label = { Text(d.getDisplayName(TextStyle.SHORT, Locale.getDefault()).take(2)) },
                                )
                            }
                        }
                    }
                    Row(Modifier.fillMaxWidth(), verticalAlignment = Alignment.CenterVertically) {
                        Text("Ends", Modifier.weight(1f))
                        OutlinedButton(onClick = { showEnd = true }) { Text(endDate?.format(Fmt.date) ?: "Never") }
                        if (endDate != null) TextButton(onClick = { endDate = null }) { Text("Clear") }
                    }
                }

                Row(Modifier.fillMaxWidth(), verticalAlignment = Alignment.CenterVertically) {
                    Column(Modifier.weight(1f)) {
                        Text("Follow device time zone")
                        Text(
                            if (floating) "Rings at ${time.format(Fmt.time)} local time wherever you are (${ZoneId.systemDefault().id})"
                            else "Pinned to $zoneId; the moment stays fixed when you travel",
                            style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onSurfaceVariant,
                        )
                    }
                    Switch(checked = floating, onCheckedChange = { floating = it })
                }

                val preview = remember(date, time, repeat, intervalText, weekdays, endDate, floating) {
                    val r = build()
                    Recurrence.nextOccurrenceAfter(r, Instant.now())?.withZoneSameInstant(ZoneId.systemDefault())
                }
                Text(
                    if (preview != null) "Next: ${preview.format(Fmt.dayTimeYear)} · ${Recurrence.describe(build())}"
                    else "That time is in the past; nothing will fire.",
                    style = MaterialTheme.typography.bodyMedium,
                    color = if (preview != null) MaterialTheme.colorScheme.primary else MaterialTheme.colorScheme.error,
                )
            }

            Spacer(Modifier.height(8.dp))
            Button(onClick = { vm.save(build()) { onBack() } }, enabled = canSave, modifier = Modifier.fillMaxWidth()) { Text("Save") }
        }
    }

    if (showDate) {
        val state = rememberDatePickerState(initialSelectedDateMillis = date.atStartOfDay(ZoneOffset.UTC).toInstant().toEpochMilli())
        DatePickerDialog(
            onDismissRequest = { showDate = false },
            confirmButton = { TextButton(onClick = {
                state.selectedDateMillis?.let { date = Instant.ofEpochMilli(it).atZone(ZoneOffset.UTC).toLocalDate() }
                showDate = false
            }) { Text("OK") } },
            dismissButton = { TextButton(onClick = { showDate = false }) { Text("Cancel") } },
        ) { DatePicker(state) }
    }
    if (showEnd) {
        val state = rememberDatePickerState(initialSelectedDateMillis = (endDate ?: date.plusMonths(1)).atStartOfDay(ZoneOffset.UTC).toInstant().toEpochMilli())
        DatePickerDialog(
            onDismissRequest = { showEnd = false },
            confirmButton = { TextButton(onClick = {
                state.selectedDateMillis?.let { endDate = Instant.ofEpochMilli(it).atZone(ZoneOffset.UTC).toLocalDate() }
                showEnd = false
            }) { Text("OK") } },
            dismissButton = { TextButton(onClick = { showEnd = false }) { Text("Cancel") } },
        ) { DatePicker(state) }
    }
    if (showTime) {
        val state = rememberTimePickerState(initialHour = time.hour, initialMinute = time.minute)
        AlertDialog(
            onDismissRequest = { showTime = false },
            confirmButton = { TextButton(onClick = { time = LocalTime.of(state.hour, state.minute); showTime = false }) { Text("OK") } },
            dismissButton = { TextButton(onClick = { showTime = false }) { Text("Cancel") } },
            text = { TimePicker(state) },
        )
    }
    if (confirmDelete) AlertDialog(
        onDismissRequest = { confirmDelete = false },
        title = { Text("Delete reminder?") },
        text = { Text("\"$title\" will be removed.") },
        confirmButton = { TextButton(onClick = { confirmDelete = false; vm.delete(id); onBack() }) { Text("Delete") } },
        dismissButton = { TextButton(onClick = { confirmDelete = false }) { Text("Cancel") } },
    )
}
