package io.github.xalrk.nudge.ui

import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Switch
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.unit.dp
import io.github.xalrk.nudge.data.Reminder
import java.time.Instant
import java.time.ZoneId
import java.time.ZonedDateTime
import java.time.format.DateTimeFormatter

object Fmt {
    val time: DateTimeFormatter = DateTimeFormatter.ofPattern("h:mm a")
    val dayTime: DateTimeFormatter = DateTimeFormatter.ofPattern("EEE, MMM d · h:mm a")
    val dayTimeYear: DateTimeFormatter = DateTimeFormatter.ofPattern("EEE, MMM d, yyyy · h:mm a")
    val date: DateTimeFormatter = DateTimeFormatter.ofPattern("EEE, MMM d, yyyy")
    val month: DateTimeFormatter = DateTimeFormatter.ofPattern("MMMM yyyy")

    fun instant(ms: Long): ZonedDateTime = Instant.ofEpochMilli(ms).atZone(ZoneId.systemDefault())

    fun relative(ms: Long, now: Long = System.currentTimeMillis()): String {
        val diff = ms - now
        val abs = kotlin.math.abs(diff)
        val unit = when {
            abs < 60_000 -> "moments"
            abs < 3_600_000 -> "${abs / 60_000} min"
            abs < 86_400_000 -> "${abs / 3_600_000} h"
            abs < 14 * 86_400_000L -> "${abs / 86_400_000} d"
            else -> "${abs / (7 * 86_400_000L)} wk"
        }
        return if (diff >= 0) "in $unit" else "$unit ago"
    }
}

@Composable
fun ColorDot(argb: Int, modifier: Modifier = Modifier) {
    Box(modifier.size(10.dp).clip(CircleShape).background(Color(argb)))
}

@Composable
fun ReminderRow(
    r: Reminder,
    subtitle: String,
    color: Int,
    onClick: () -> Unit,
    onToggle: ((Boolean) -> Unit)? = null,
) {
    Row(
        modifier = Modifier.fillMaxWidth().clickable(onClick = onClick).padding(horizontal = 16.dp, vertical = 12.dp),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        ColorDot(color, Modifier.padding(end = 12.dp))
        Column(Modifier.weight(1f)) {
            Text(r.title, style = MaterialTheme.typography.bodyLarge, maxLines = 2)
            if (r.body.isNotBlank()) Text(r.body, style = MaterialTheme.typography.bodySmall, maxLines = 2,
                color = MaterialTheme.colorScheme.onSurfaceVariant)
            Text(subtitle, style = MaterialTheme.typography.labelMedium, color = MaterialTheme.colorScheme.primary)
        }
        if (onToggle != null) Switch(checked = r.enabled, onCheckedChange = onToggle)
    }
}


/** Shown at the top of the main tabs while every reminder is muted. */
@Composable
fun PauseBanner(settings: io.github.xalrk.nudge.data.SettingsSnapshot, onResume: () -> Unit) {
    if (!settings.isPaused) return
    Row(
        Modifier.fillMaxWidth().padding(horizontal = 16.dp, vertical = 8.dp).clip(androidx.compose.foundation.shape.RoundedCornerShape(12.dp))
            .background(MaterialTheme.colorScheme.surfaceContainerHigh).padding(start = 16.dp, end = 4.dp, top = 4.dp, bottom = 4.dp),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        Column(Modifier.weight(1f)) {
            Text("All reminders paused", style = MaterialTheme.typography.bodyMedium)
            Text("Until " + Fmt.instant(settings.pausedUntil).format(Fmt.dayTime), style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
        }
        androidx.compose.material3.TextButton(onClick = onResume) { Text("Resume") }
    }
}
