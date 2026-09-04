package io.github.xalrk.nudge.ui

import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.material.icons.filled.Clear
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Text
import androidx.compose.material3.TopAppBar
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.unit.dp
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import io.github.xalrk.nudge.data.Reminder
import io.github.xalrk.nudge.domain.Recurrence

/** Every reminder in one searchable list: scheduled first by next time, then random. */
@Composable
fun ListScreen(vm: NudgeViewModel, onBack: () -> Unit, onOpen: (Long) -> Unit) {
    val reminders by vm.reminders.collectAsStateWithLifecycle()
    var query by rememberSaveable { mutableStateOf("") }
    val q = query.trim().lowercase()
    val shown = reminders.filter { q.isEmpty() || it.title.lowercase().contains(q) || it.body.lowercase().contains(q) }
        .sortedWith(compareBy<Reminder> { it.isRandom }.thenBy { !it.enabled }.thenBy { it.nextAt ?: Long.MAX_VALUE }.thenBy { it.title.lowercase() })

    Scaffold(
        topBar = {
            TopAppBar(
                title = { Text("All Reminders") },
                navigationIcon = { IconButton(onClick = onBack) { Icon(Icons.AutoMirrored.Filled.ArrowBack, "Back") } },
            )
        }
    ) { padding ->
        LazyColumn(Modifier.fillMaxSize().padding(padding)) {
            item {
                OutlinedTextField(
                    value = query, onValueChange = { query = it }, singleLine = true,
                    placeholder = { Text("Search") },
                    trailingIcon = { if (query.isNotEmpty()) IconButton(onClick = { query = "" }) { Icon(Icons.Filled.Clear, "Clear") } },
                    modifier = Modifier.fillMaxWidth().padding(horizontal = 16.dp, vertical = 8.dp),
                )
                Text(
                    "${shown.size} of ${reminders.size}",
                    Modifier.padding(horizontal = 16.dp, vertical = 4.dp),
                    style = MaterialTheme.typography.labelMedium, color = MaterialTheme.colorScheme.onSurfaceVariant,
                )
            }
            items(shown, key = { it.id }) { r ->
                val sub = when {
                    !r.enabled -> if (r.isScheduled) "Off · " + Recurrence.describe(r) else "Paused · Random"
                    r.isScheduled -> (r.nextAt?.let { Fmt.instant(it).format(Fmt.dayTime) + " · " } ?: "") + Recurrence.describe(r)
                    else -> "Random" + (r.meanOverrideMillis?.let { " · " + io.github.xalrk.nudge.data.Settings.describeInterval(it) } ?: "")
                }
                ReminderRow(r, subtitle = sub, color = vm.colorOf(r), onClick = { onOpen(r.id) }, onToggle = { vm.setEnabled(r.id, it) })
            }
            item { Box(Modifier.size(24.dp)) }
        }
    }
}
