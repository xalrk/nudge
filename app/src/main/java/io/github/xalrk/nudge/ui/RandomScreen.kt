package io.github.xalrk.nudge.ui

import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Add
import androidx.compose.material.icons.filled.Casino
import androidx.compose.material3.FloatingActionButton
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Text
import androidx.compose.material3.TopAppBar
import androidx.compose.runtime.Composable
import androidx.compose.animation.core.Animatable
import androidx.compose.animation.core.FastOutSlowInEasing
import androidx.compose.animation.core.tween
import androidx.compose.runtime.getValue
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.ui.graphics.graphicsLayer
import kotlinx.coroutines.launch
import kotlin.math.PI
import kotlin.math.sin
import androidx.compose.ui.Modifier
import androidx.compose.ui.unit.dp
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import io.github.xalrk.nudge.data.FrequencyMode
import io.github.xalrk.nudge.data.Settings

@Composable
fun RandomScreen(vm: NudgeViewModel, onAdd: () -> Unit, onOpen: (Long) -> Unit) {
    val reminders by vm.reminders.collectAsStateWithLifecycle()
    val settings by vm.settings.collectAsStateWithLifecycle()
    val random = reminders.filter { it.isRandom }
    val enabledCount = random.count { it.enabled }

    val overallMean = when (settings.frequencyMode) {
        FrequencyMode.PER_REMINDER -> if (enabledCount > 0) settings.meanIntervalMillis / enabledCount else 0L
        FrequencyMode.WHOLE_POOL -> settings.meanIntervalMillis
    }

    Scaffold(
        topBar = {
            TopAppBar(
                title = { Text("Random reminders") },
                actions = {
                    // A quick tumble so the re-roll feels like rolling a die.
                    val spin = remember { Animatable(0f) }
                    val scope = rememberCoroutineScope()
                    IconButton(onClick = {
                        vm.rerollRandom()
                        scope.launch {
                            spin.snapTo(0f)
                            spin.animateTo(1f, tween(650, easing = FastOutSlowInEasing))
                        }
                    }) {
                        val t = spin.value
                        val wobble = sin(t * PI.toFloat() * 3f) * 12f
                        Icon(
                            Icons.Filled.Casino, contentDescription = "Re-roll times",
                            modifier = Modifier.graphicsLayer {
                                rotationZ = t * 720f + wobble
                                val s = 1f + 0.25f * sin(t * PI.toFloat())
                                scaleX = s; scaleY = s
                            },
                        )
                    }
                },
            )
        },
        floatingActionButton = { FloatingActionButton(onClick = onAdd, containerColor = MaterialTheme.colorScheme.primary, contentColor = MaterialTheme.colorScheme.onPrimary) { Icon(Icons.Filled.Add, contentDescription = "Add random reminder") } },
    ) { padding ->
        LazyColumn(Modifier.fillMaxSize().padding(padding)) {
            item {
                Column(Modifier.padding(16.dp)) {
                    Text(
                        "These go off at unpredictable moments between ${hourLabel(settings.activeStartHour)} and ${hourLabel(settings.activeEndHour)} (configurable in Settings).",
                        style = MaterialTheme.typography.bodyMedium, color = MaterialTheme.colorScheme.onSurfaceVariant,
                    )
                    val detail = when (settings.frequencyMode) {
                        FrequencyMode.PER_REMINDER -> "Each one fires ${Settings.describeInterval(settings.meanIntervalMillis)} (configurable in Settings)."
                        FrequencyMode.WHOLE_POOL -> "One of them fires ${Settings.describeInterval(settings.meanIntervalMillis)} (configurable in Settings)."
                    }
                    Text(detail, style = MaterialTheme.typography.bodyMedium, color = MaterialTheme.colorScheme.onSurfaceVariant)
                    if (enabledCount > 1 && settings.frequencyMode == FrequencyMode.PER_REMINDER) {
                        Text("With $enabledCount enabled that is ${Settings.describeInterval(overallMean.coerceAtLeast(Settings.MIN_MEAN_MILLIS))} overall.",
                            style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
                    }
                }
            }
            if (random.isEmpty()) item {
                Text("No random reminders yet. Tap + or import a CSV file from Settings.",
                    Modifier.padding(16.dp), color = MaterialTheme.colorScheme.onSurfaceVariant)
            }
            items(random, key = { it.id }) { r ->
                val sub = when {
                    !r.enabled -> "Paused"
                    r.nextAt == null -> "Waiting"
                    settings.showNextRandomTime -> "Next " + Fmt.relative(r.nextAt) + " · " + Fmt.instant(r.nextAt).format(Fmt.dayTime)
                    else -> "Sometime soon"
                }
                ReminderRow(r, subtitle = sub, color = vm.colorOf(r), onClick = { onOpen(r.id) }, onToggle = { vm.setEnabled(r.id, it) })
            }
            item { Box(Modifier.size(80.dp)) }
        }
    }
}

fun hourLabel(h: Int): String = when {
    h == 0 || h == 24 -> "midnight"
    h == 12 -> "noon"
    h < 12 -> "${h} am"
    else -> "${h - 12} pm"
}
