package io.github.xalrk.nudge.ui

import androidx.activity.compose.BackHandler
import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.aspectRatio
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.material.icons.filled.Delete
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.AssistChip
import androidx.compose.material3.Slider
import androidx.compose.foundation.horizontalScroll
import io.github.xalrk.nudge.data.Settings
import androidx.compose.material3.DatePicker
import androidx.compose.material3.DatePickerDialog
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
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.input.KeyboardType
import androidx.compose.ui.unit.dp
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import io.github.xalrk.nudge.data.Kind
import io.github.xalrk.nudge.data.Reminder
import io.github.xalrk.nudge.data.Repeat
import io.github.xalrk.nudge.domain.Colors
import io.github.xalrk.nudge.domain.Recurrence
import io.github.xalrk.nudge.scheduler.ReminderEngine.SeriesScope
import java.time.DayOfWeek
import java.time.Instant
import java.time.LocalDate
import java.time.LocalDateTime
import java.time.LocalTime
import java.time.ZoneId
import java.time.ZoneOffset
import java.time.format.TextStyle
import java.util.Locale

/**
 * @param defaultDate date preselected for a new reminder (the calendar's selected day).
 * @param occurrence  for a repeating reminder, the occurrence that was tapped; enables
 *                    "this one / following / all" choices on save and delete.
 */
@Composable
fun EditReminderScreen(
    vm: NudgeViewModel, id: Long, defaultKind: String, defaultDate: LocalDate?, occurrence: LocalDate?, onBack: () -> Unit,
) {
    val reminders by vm.reminders.collectAsStateWithLifecycle()
    val settings by vm.settings.collectAsStateWithLifecycle()
    val existing = reminders.firstOrNull { it.id == id }

    var loaded by remember { mutableStateOf(id == 0L) }
    var title by remember { mutableStateOf("") }
    var body by remember { mutableStateOf("") }
    var kind by remember { mutableStateOf(runCatching { Kind.valueOf(defaultKind) }.getOrDefault(Kind.SCHEDULED)) }
    var date by remember { mutableStateOf(defaultDate ?: LocalDate.now()) }
    var time by remember { mutableStateOf(LocalTime.now().plusHours(1).withMinute(0).withSecond(0).withNano(0)) }
    var repeat by remember { mutableStateOf(Repeat.NONE) }
    var intervalText by remember { mutableStateOf("1") }
    var weekdays by remember { mutableStateOf(setOf<DayOfWeek>()) }
    var endDate by remember { mutableStateOf<LocalDate?>(null) }
    var floating by remember { mutableStateOf(true) }
    var zoneId by remember { mutableStateOf(ZoneId.systemDefault().id) }
    var enabled by remember { mutableStateOf(true) }
    var color by remember { mutableStateOf<Int?>(null) }
    var sound by remember { mutableStateOf(true) }
    var vibrate by remember { mutableStateOf(true) }
    var customRate by remember { mutableStateOf(false) }
    var rateSlider by remember { mutableStateOf(Settings.millisToSlider(Settings.DEFAULT_MEAN_MILLIS)) }
    var showDate by remember { mutableStateOf(false) }
    var showTime by remember { mutableStateOf(false) }
    var showEnd by remember { mutableStateOf(false) }
    var confirmDelete by remember { mutableStateOf(false) }
    var askScope by remember { mutableStateOf<String?>(null) } // "save" or "delete"

    LaunchedEffect(existing) {
        if (!loaded && existing != null) {
            title = existing.title; body = existing.body; kind = existing.kind
            existing.localDateTimeOrNull()?.let { date = occurrence ?: it.toLocalDate(); time = it.toLocalTime() }
            repeat = existing.repeat; intervalText = existing.interval.toString()
            weekdays = existing.weekdaySet(); endDate = existing.endDateOrNull()
            floating = existing.floating; zoneId = existing.zoneId ?: ZoneId.systemDefault().id
            enabled = existing.enabled; color = existing.color; sound = existing.sound; vibrate = existing.vibrate
            customRate = existing.meanOverrideMillis != null
            rateSlider = Settings.millisToSlider(existing.meanOverrideMillis ?: settings.meanIntervalMillis)
            loaded = true
        }
    }

    val isNew = id == 0L
    val canSave = title.isNotBlank()
    val isSeries = existing != null && existing.isScheduled && existing.repeat != Repeat.NONE && occurrence != null
    val autoColor = Colors.complementary(settings.accentColor)

    fun build(): Reminder {
        val base = existing ?: Reminder(title = "", kind = kind)
        val common = base.copy(title = title.trim(), body = body.trim(), color = color, sound = sound, vibrate = vibrate)
        return if (kind == Kind.RANDOM) common.copy(
            kind = Kind.RANDOM, localDateTime = null, zoneId = null, repeat = Repeat.NONE, interval = 1, weekdays = 0, endDate = null,
            excludedDates = "", enabled = enabled, meanOverrideMillis = if (customRate) Settings.sliderToMillis(rateSlider) else null,
            // A changed rate needs a fresh roll; otherwise keep the pending time.
            nextAt = if (existing?.isRandom == true && existing.meanOverrideMillis == (if (customRate) Settings.sliderToMillis(rateSlider) else null)) existing.nextAt else null,
        ) else common.copy(
            meanOverrideMillis = null,
            kind = Kind.SCHEDULED,
            localDateTime = LocalDateTime.of(date, time).format(Reminder.DT_FORMAT),
            zoneId = if (existing?.zoneId != null && !floating) existing.zoneId else ZoneId.systemDefault().id,
            floating = floating, repeat = repeat, interval = intervalText.toIntOrNull()?.coerceAtLeast(1) ?: 1,
            weekdays = if (repeat == Repeat.WEEKLY) Reminder.maskOf(weekdays) else 0,
            endDate = if (repeat == Repeat.NONE) null else endDate?.toString(),
            excludedDates = if (repeat == Repeat.NONE) "" else base.excludedDates,
            enabled = true, nextAt = null, snoozeAt = null,
        )
    }

    /** The editable fields only, so two builds compare equal when nothing the user can change differs. */
    fun signature(): Reminder = build().copy(id = 0, nextAt = null, snoozeAt = null, lastFiredAt = null, createdAt = 0, dedupeKey = "", zoneId = null)

    var initial by remember { mutableStateOf<Reminder?>(null) }
    LaunchedEffect(loaded) { if (loaded && initial == null) initial = signature() }
    val dirty = initial != null && signature() != initial
    var confirmDiscard by remember { mutableStateOf(false) }

    fun leave() { if (dirty) confirmDiscard = true else onBack() }
    BackHandler(enabled = dirty) { confirmDiscard = true }

    fun save() {
        if (isSeries && kind == Kind.SCHEDULED && repeat != Repeat.NONE) askScope = "save"
        else vm.save(build()) { onBack() }
    }

    Scaffold(
        topBar = {
            TopAppBar(
                title = { Text(if (isNew) "New reminder" else "Edit reminder") },
                navigationIcon = { IconButton(onClick = { leave() }) { Icon(Icons.AutoMirrored.Filled.ArrowBack, "Back") } },
                actions = {
                    if (!isNew) IconButton(onClick = { if (isSeries) askScope = "delete" else confirmDelete = true }) { Icon(Icons.Filled.Delete, "Delete") }
                    TextButton(enabled = canSave, onClick = { save() }) { Text("Save") }
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
                Text("Fires at a random moment during your active hours.", style = MaterialTheme.typography.bodyMedium, color = MaterialTheme.colorScheme.onSurfaceVariant)
                Row(Modifier.fillMaxWidth(), verticalAlignment = Alignment.CenterVertically) {
                    Column(Modifier.weight(1f)) {
                        Text("Custom frequency")
                        Text(
                            if (customRate) Settings.describeInterval(Settings.sliderToMillis(rateSlider)).replaceFirstChar { it.uppercase() }
                            else "Uses the Settings default (${Settings.describeInterval(settings.meanIntervalMillis)})",
                            style = MaterialTheme.typography.bodySmall, color = if (customRate) MaterialTheme.colorScheme.primary else MaterialTheme.colorScheme.onSurfaceVariant,
                        )
                    }
                    Spacer(Modifier.width(16.dp))
                    Switch(checked = customRate, onCheckedChange = { customRate = it })
                }
                if (customRate) SliderGuard(value = rateSlider, onCommit = { rateSlider = it }, onFinished = {}) { onChange, onFinished ->
                    Slider(value = rateSlider, onValueChange = onChange, onValueChangeFinished = onFinished)
                }
                if (!isNew) Row(Modifier.fillMaxWidth(), verticalAlignment = Alignment.CenterVertically) {
                    Text("Enabled", Modifier.weight(1f)); Spacer(Modifier.width(16.dp)); Switch(checked = enabled, onCheckedChange = { enabled = it })
                }
            } else {
                Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                    OutlinedButton(onClick = { showDate = true }, Modifier.weight(1f)) { Text(date.format(Fmt.date)) }
                    OutlinedButton(onClick = { showTime = true }) { Text(time.format(Fmt.time)) }
                }
                // Quick picks for the common "remind me soon" cases.
                Row(Modifier.fillMaxWidth().horizontalScroll(rememberScrollState()), horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                    fun pick(dt: LocalDateTime) { date = dt.toLocalDate(); time = dt.toLocalTime().withSecond(0).withNano(0) }
                    val now = LocalDateTime.now()
                    val tonight = now.toLocalDate().atTime(20, 0).let { if (it.isAfter(now)) it else it.plusDays(1) }
                    AssistChip(onClick = { pick(now.plusMinutes(15)) }, label = { Text("In 15 min") })
                    AssistChip(onClick = { pick(now.plusHours(1)) }, label = { Text("In 1 hour") })
                    AssistChip(onClick = { pick(tonight) }, label = { Text("Tonight 8 pm") })
                    AssistChip(onClick = { pick(now.toLocalDate().plusDays(1).atTime(9, 0)) }, label = { Text("Tomorrow 9 am") })
                    AssistChip(onClick = { pick(now.toLocalDate().plusWeeks(1).atTime(time)) }, label = { Text("Next week") })
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
                        // Seven equal circles that stretch with the screen width.
                        Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.spacedBy(6.dp)) {
                            for (d in DayOfWeek.entries) {
                                val on = d in weekdays || (weekdays.isEmpty() && d == date.dayOfWeek)
                                Box(
                                    Modifier.weight(1f).aspectRatio(1f).clip(CircleShape)
                                        .background(if (on) MaterialTheme.colorScheme.primary else Color.Transparent)
                                        .then(if (on) Modifier else Modifier.border(1.dp, MaterialTheme.colorScheme.outlineVariant, CircleShape))
                                        .clickable { weekdays = if (d in weekdays) weekdays - d else weekdays + d },
                                    contentAlignment = Alignment.Center,
                                ) {
                                    Text(
                                        d.getDisplayName(TextStyle.NARROW, Locale.getDefault()),
                                        style = MaterialTheme.typography.labelLarge,
                                        color = if (on) MaterialTheme.colorScheme.onPrimary else MaterialTheme.colorScheme.onSurface,
                                    )
                                }
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
                    Spacer(Modifier.width(16.dp))
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

            Text("Notification", style = MaterialTheme.typography.labelLarge, modifier = Modifier.padding(top = 4.dp))
            Row(Modifier.fillMaxWidth(), verticalAlignment = Alignment.CenterVertically) {
                Text("Sound", Modifier.weight(1f)); Spacer(Modifier.width(16.dp)); Switch(checked = sound, onCheckedChange = { sound = it })
            }
            Row(Modifier.fillMaxWidth(), verticalAlignment = Alignment.CenterVertically) {
                Text("Vibration", Modifier.weight(1f)); Spacer(Modifier.width(16.dp)); Switch(checked = vibrate, onCheckedChange = { vibrate = it })
            }
            Text("Color", style = MaterialTheme.typography.bodyLarge)
            Text("\"A\" follows the app accent (its complementary color).", style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
            SwatchRow(
                current = color, customColors = settings.customColors, autoColor = autoColor,
                onPick = { color = it }, onAddCustom = { vm.addCustomColor(it) },
                onRemoveCustom = { removed -> vm.removeCustomColor(removed); if (color == removed) color = null },
            )
            Spacer(Modifier.padding(8.dp))
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
    if (confirmDiscard) AlertDialog(
        onDismissRequest = { confirmDiscard = false },
        title = { Text("Discard changes?") },
        text = { Text("You have unsaved changes to this reminder.") },
        confirmButton = { TextButton(onClick = { confirmDiscard = false; onBack() }) { Text("Discard") } },
        dismissButton = { TextButton(onClick = { confirmDiscard = false }) { Text("Keep editing") } },
    )
    if (confirmDelete) AlertDialog(
        onDismissRequest = { confirmDelete = false },
        title = { Text("Delete reminder?") },
        text = { Text("\"$title\" will be removed.") },
        confirmButton = { TextButton(onClick = { confirmDelete = false; vm.delete(id); onBack() }) { Text("Delete") } },
        dismissButton = { TextButton(onClick = { confirmDelete = false }) { Text("Cancel") } },
    )
    val scopeAction = askScope
    if (scopeAction != null && existing != null && occurrence != null) {
        val deleting = scopeAction == "delete"
        fun run(scope: SeriesScope) {
            askScope = null
            if (deleting) vm.deleteFromSeries(existing, occurrence, scope) { onBack() }
            else vm.editSeries(existing, build(), occurrence, scope) { onBack() }
        }
        AlertDialog(
            onDismissRequest = { askScope = null },
            title = { Text(if (deleting) "Delete repeating reminder" else "Change repeating reminder") },
            text = {
                Column {
                    Text("This reminder repeats. Apply to:", style = MaterialTheme.typography.bodyMedium)
                    Spacer(Modifier.padding(6.dp))
                    ScopeOption("Only this one", occurrence.format(Fmt.date)) { run(SeriesScope.THIS) }
                    ScopeOption("This and following", "From ${occurrence.format(Fmt.date)} onward") { run(SeriesScope.FOLLOWING) }
                    ScopeOption("All", "Every occurrence in the series") { run(SeriesScope.ALL) }
                }
            },
            confirmButton = {},
            dismissButton = { TextButton(onClick = { askScope = null }) { Text("Cancel") } },
        )
    }
}

@Composable
private fun ScopeOption(label: String, detail: String, onClick: () -> Unit) {
    OutlinedButton(
        onClick = onClick,
        modifier = Modifier.fillMaxWidth().padding(vertical = 4.dp),
        shape = RoundedCornerShape(14.dp),
        border = BorderStroke(1.5.dp, MaterialTheme.colorScheme.primary),
        contentPadding = PaddingValues(horizontal = 16.dp, vertical = 12.dp),
    ) {
        Column(Modifier.fillMaxWidth()) {
            // Text in the normal foreground (white on the black theme); only the border carries the accent.
            Text(label, style = MaterialTheme.typography.bodyLarge, color = MaterialTheme.colorScheme.onSurface)
            Text(detail, style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
        }
    }
}
