package io.github.xalrk.nudge.ui

import android.app.AlarmManager
import android.content.Intent
import android.net.Uri
import io.github.xalrk.nudge.BuildConfig
import io.github.xalrk.nudge.update.UpdateChecker
import android.os.Build
import android.os.PowerManager
import android.provider.Settings as SysSettings
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.FlowRow
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.text.KeyboardActions
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.material.icons.filled.Check
import androidx.compose.material3.OutlinedTextField
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.luminance
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.text.input.ImeAction
import androidx.compose.ui.text.input.KeyboardType
import androidx.compose.foundation.layout.width
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.outlined.Info
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.Button
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.RangeSlider
import androidx.compose.material3.Scaffold
import androidx.compose.material3.SegmentedButton
import androidx.compose.material3.SegmentedButtonDefaults
import androidx.compose.material3.SingleChoiceSegmentedButtonRow
import androidx.compose.material3.Slider
import androidx.compose.material3.Switch
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.material3.TopAppBar
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableFloatStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.font.FontFamily
import androidx.compose.ui.unit.dp
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import io.github.xalrk.nudge.data.FrequencyMode
import io.github.xalrk.nudge.data.Settings
import io.github.xalrk.nudge.data.ThemeMode
import io.github.xalrk.nudge.scheduler.Notifier
import java.time.ZoneId
import java.time.ZonedDateTime
import java.time.format.TextStyle
import java.util.Locale

@Composable
fun SettingsScreen(vm: NudgeViewModel) {
    val settings by vm.settings.collectAsStateWithLifecycle()
    val reminders by vm.reminders.collectAsStateWithLifecycle()
    val context = LocalContext.current

    var slider by remember { mutableFloatStateOf(Settings.millisToSlider(settings.meanIntervalMillis)) }
    LaunchedEffect(settings.meanIntervalMillis) { slider = Settings.millisToSlider(settings.meanIntervalMillis) }
    var window by remember { mutableStateOf(settings.activeStartHour.toFloat()..settings.activeEndHour.toFloat()) }
    LaunchedEffect(settings.activeStartHour, settings.activeEndHour) { window = settings.activeStartHour.toFloat()..settings.activeEndHour.toFloat() }
    var showFormatHelp by remember { mutableStateOf(false) }

    val importLauncher = rememberLauncherForActivityResult(ActivityResultContracts.OpenDocument()) { uri -> uri?.let { vm.importFrom(it) } }
    val exportLauncher = rememberLauncherForActivityResult(ActivityResultContracts.CreateDocument("text/csv")) { uri -> uri?.let { vm.exportTo(it) } }

    Scaffold(topBar = { TopAppBar(title = { Text("Settings") }) }) { padding ->
        Column(Modifier.fillMaxSize().padding(padding).verticalScroll(rememberScrollState()).padding(horizontal = 16.dp)) {

            SectionTitle("Appearance")
            SingleChoiceSegmentedButtonRow(Modifier.fillMaxWidth()) {
                val labels = listOf("System", "Light", "Dark")
                ThemeMode.entries.forEachIndexed { i, m ->
                    SegmentedButton(selected = settings.themeMode == m, onClick = { vm.setThemeMode(m) },
                        shape = SegmentedButtonDefaults.itemShape(i, ThemeMode.entries.size)) { Text(labels[i]) }
                }
            }
            Text("Dark mode is true black for AMOLED screens.", style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
            if (Build.VERSION.SDK_INT >= 31) Row(Modifier.fillMaxWidth().padding(top = 4.dp), verticalAlignment = Alignment.CenterVertically) {
                Text("Use system accent color", Modifier.weight(1f))
                Spacer(Modifier.width(16.dp))
                Switch(checked = settings.dynamicColor, onCheckedChange = { vm.setDynamicColor(it) })
            }
            if (!settings.dynamicColor || Build.VERSION.SDK_INT < 31) {
                Spacer(Modifier.height(12.dp))
                Text("Accent color", style = MaterialTheme.typography.bodyLarge)
                SwatchRow(
                    current = settings.accentColor, customColors = settings.customColors,
                    onPick = { it?.let(vm::setAccentColor) }, onAddCustom = { vm.addCustomColor(it) }, onRemoveCustom = { vm.removeCustomColor(it) },
                )
                Text("Dark mode lightens the accent so it stays readable on black. Notifications default to the complementary color; each reminder can pick its own.",
                    style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onSurfaceVariant, modifier = Modifier.padding(top = 6.dp))
            }

            HorizontalDivider(Modifier.padding(vertical = 16.dp))
            SectionTitle("Random reminders")
            Text("Average frequency", style = MaterialTheme.typography.bodyLarge)
            Text(
                Settings.describeInterval(Settings.sliderToMillis(slider)).replaceFirstChar { it.uppercase() },
                style = MaterialTheme.typography.bodyMedium, color = MaterialTheme.colorScheme.primary,
            )
            Slider(
                value = slider,
                onValueChange = { slider = it },
                onValueChangeFinished = { vm.setMeanInterval(Settings.sliderToMillis(slider)) },
            )
            Text(
                "Timing stays random: this only changes how often it happens on average.",
                style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onSurfaceVariant,
            )
            Spacer(Modifier.height(12.dp))
            Text("Frequency applies to", style = MaterialTheme.typography.bodyLarge)
            SingleChoiceSegmentedButtonRow(Modifier.fillMaxWidth().padding(top = 4.dp)) {
                FrequencyMode.entries.forEachIndexed { i, mode ->
                    SegmentedButton(
                        selected = settings.frequencyMode == mode,
                        onClick = { vm.setFrequencyMode(mode) },
                        shape = SegmentedButtonDefaults.itemShape(i, FrequencyMode.entries.size),
                    ) { Text(if (mode == FrequencyMode.PER_REMINDER) "Each reminder" else "Whole list") }
                }
            }
            Text(
                if (settings.frequencyMode == FrequencyMode.PER_REMINDER)
                    "Every random reminder fires on its own schedule, so a long list means more notifications overall."
                else "Only one random reminder fires per interval, picked at random from the whole list.",
                style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onSurfaceVariant,
            )

            Spacer(Modifier.height(16.dp))
            Text("Active hours", style = MaterialTheme.typography.bodyLarge)
            Text("${hourLabel(window.start.toInt())} – ${hourLabel(window.endInclusive.toInt())}",
                style = MaterialTheme.typography.bodyMedium, color = MaterialTheme.colorScheme.primary)
            RangeSlider(
                value = window,
                onValueChange = { r ->
                    val s = r.start.toInt().coerceIn(0, 23)
                    val e = r.endInclusive.toInt().coerceIn(s + 1, 24)
                    window = s.toFloat()..e.toFloat()
                },
                valueRange = 0f..24f,
                steps = 23,
                onValueChangeFinished = { vm.setActiveWindow(window.start.toInt(), window.endInclusive.toInt()) },
            )
            Text("Random reminders never fire outside this window. Times follow the device time zone (${zoneLabel()}).",
                style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onSurfaceVariant)

            Spacer(Modifier.height(8.dp))
            Row(Modifier.fillMaxWidth(), verticalAlignment = Alignment.CenterVertically) {
                Text("Show next random time", Modifier.weight(1f))
                Spacer(Modifier.width(16.dp))
                Switch(checked = settings.showNextRandomTime, onCheckedChange = { vm.setShowNextRandom(it) })
            }

            HorizontalDivider(Modifier.padding(vertical = 16.dp))
            Row(Modifier.fillMaxWidth(), verticalAlignment = Alignment.CenterVertically) {
                SectionTitle("Import & export")
                Spacer(Modifier.weight(1f))
                IconButton(onClick = { showFormatHelp = true }) { Icon(Icons.Outlined.Info, contentDescription = "How to format the CSV") }
            }
            Text("Import a CSV file with one reminder per row. Duplicates are skipped automatically. Tap the info icon for the column layout.",
                style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
            Row(Modifier.fillMaxWidth().padding(top = 8.dp), horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                Button(onClick = { importLauncher.launch(arrayOf("text/csv", "text/comma-separated-values", "text/plain", "application/json", "application/octet-stream", "*/*")) }) { Text("Import CSV") }
                OutlinedButton(onClick = { exportLauncher.launch("nudge-reminders.csv") }) { Text("Export CSV") }
            }
            Text("${reminders.size} reminders stored", style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onSurfaceVariant)

            HorizontalDivider(Modifier.padding(vertical = 16.dp))
            SectionTitle("Reliability")
            StatusRow("Notifications", Notifier.canPost(context)) {
                context.startActivity(Intent(SysSettings.ACTION_APP_NOTIFICATION_SETTINGS).putExtra(SysSettings.EXTRA_APP_PACKAGE, context.packageName))
            }
            val am = context.getSystemService(AlarmManager::class.java)
            val exactOk = Build.VERSION.SDK_INT < 31 || am.canScheduleExactAlarms()
            StatusRow("Exact alarms", exactOk) {
                if (Build.VERSION.SDK_INT >= 31) context.startActivity(Intent(SysSettings.ACTION_REQUEST_SCHEDULE_EXACT_ALARM))
            }
            val pm = context.getSystemService(PowerManager::class.java)
            StatusRow("Unrestricted battery", pm.isIgnoringBatteryOptimizations(context.packageName)) {
                context.startActivity(Intent(SysSettings.ACTION_IGNORE_BATTERY_OPTIMIZATION_SETTINGS))
            }
            Text("Some phones kill background apps aggressively. If reminders go missing, allow unrestricted battery use.",
                style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
            Row(Modifier.fillMaxWidth().padding(top = 8.dp), horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                OutlinedButton(onClick = { vm.testNotification() }) { Text("Test notification") }
                OutlinedButton(onClick = { vm.fireRandomNow() }) { Text("Fire a random one") }
            }

            HorizontalDivider(Modifier.padding(vertical = 16.dp))
            SectionTitle("Updates")
            Row(Modifier.fillMaxWidth(), verticalAlignment = Alignment.CenterVertically) {
                Column(Modifier.weight(1f)) {
                    Text("Check for updates automatically")
                    Text("About once a day, only on a network and never on low battery. You get a notification when a newer version is on GitHub; tapping it opens the download page.",
                        style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
                }
                Spacer(Modifier.width(16.dp))
                Switch(checked = settings.autoUpdateCheck, onCheckedChange = { vm.setAutoUpdateCheck(it) })
            }
            Row(Modifier.fillMaxWidth().padding(top = 8.dp), horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                OutlinedButton(onClick = { vm.checkForUpdatesNow() }) { Text("Check now") }
                TextButton(onClick = { context.startActivity(Intent(Intent.ACTION_VIEW, Uri.parse(UpdateChecker.RELEASES_PAGE))) }) { Text("Releases page") }
            }

            HorizontalDivider(Modifier.padding(vertical = 16.dp))
            Text("Nudge ${BuildConfig.VERSION_NAME} · github.com/xalrk/nudge", style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
            Spacer(Modifier.height(32.dp))
        }
    }

    if (showFormatHelp) AlertDialog(
        onDismissRequest = { showFormatHelp = false },
        confirmButton = { TextButton(onClick = { showFormatHelp = false }) { Text("Got it") } },
        title = { Text("CSV format") },
        text = {
            Column(Modifier.verticalScroll(rememberScrollState()), verticalArrangement = Arrangement.spacedBy(8.dp)) {
                Text("One reminder per row. Put this header on the first line, then one row per reminder. Only \"title\" is required; leave any other cell empty.",
                    style = MaterialTheme.typography.bodySmall)
                Text(
                    "title,details,date,time,repeat,every,weekdays,until,zone,follow_device_zone\n" +
                    "Drink some water,,,,,,,,,\n" +
                    "Call mom,,2026-09-14,18:00,weekly,1,sun,,,\n" +
                    "Standup,,,09:30,weekly,1,mon;tue;wed;thu;fri,2026-12-19,,\n" +
                    "Gym,\"Bring a towel, water\",,18:00,weekly,1,mon;wed;fri,,,\n" +
                    "Pay rent,,2026-10-01,09:00,monthly,,,,,\n" +
                    "Flight,,2026-10-20,06:30,,,,,America/Denver,no",
                    fontFamily = FontFamily.Monospace, style = MaterialTheme.typography.bodySmall,
                )
                Column(verticalArrangement = Arrangement.spacedBy(4.dp)) {
                    CsvColumn("title", "the notification text (required)")
                    CsvColumn("details", "longer text shown under the title")
                    CsvColumn("date", "YYYY-MM-DD; empty with a time = the next such time")
                    CsvColumn("time", "HH:MM or 2:15pm; defaults to 09:00 when a date is given")
                    CsvColumn("repeat", "daily, weekly, monthly, yearly, weekdays, weekends; empty = once")
                    CsvColumn("every", "repeat every N days/weeks/months/years (default 1)")
                    CsvColumn("weekdays", "for weekly: mon;tue;wed;thu;fri;sat;sun, separated by ;")
                    CsvColumn("until", "YYYY-MM-DD, last day of a repeat")
                    CsvColumn("zone", "time zone id such as Europe/Berlin (default: this device)")
                    CsvColumn("follow_device_zone", "yes (ring at the same wall-clock time anywhere) or no (pin to the zone)")
                }
                Text("A row with no date and no time becomes a random reminder. Wrap a cell in double quotes if it contains a comma. Columns may be in any order when the header is present; without a header they are read in the order above. Rows starting with # are ignored. Spreadsheet apps such as Excel, Numbers and Google Sheets can save this with File → Save as → CSV.",
                    style = MaterialTheme.typography.bodySmall)
            }
        },
    )
}

@Composable
private fun CsvColumn(name: String, meaning: String) {
    Row {
        Text(name, Modifier.width(140.dp), fontFamily = FontFamily.Monospace, style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.primary)
        Text(meaning, style = MaterialTheme.typography.bodySmall)
    }
}

@Composable
private fun SectionTitle(text: String) {
    Text(text, Modifier.padding(top = 16.dp, bottom = 8.dp), style = MaterialTheme.typography.titleMedium, color = MaterialTheme.colorScheme.primary)
}

@Composable
private fun StatusRow(label: String, ok: Boolean, onFix: () -> Unit) {
    Row(Modifier.fillMaxWidth().padding(vertical = 4.dp), verticalAlignment = Alignment.CenterVertically) {
        Text(label, Modifier.weight(1f))
        if (ok) Text("OK", color = MaterialTheme.colorScheme.secondary, style = MaterialTheme.typography.labelLarge)
        else TextButton(onClick = onFix) { Text("Fix") }
    }
}

private fun zoneLabel(): String {
    val z = ZoneId.systemDefault()
    val name = z.getDisplayName(TextStyle.SHORT, Locale.getDefault())
    val offset = ZonedDateTime.now(z).offset.id.replace("Z", "+00:00")
    return "${z.id}, $name $offset"
}
