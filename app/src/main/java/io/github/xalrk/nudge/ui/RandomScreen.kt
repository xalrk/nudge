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
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.setValue
import kotlin.random.Random
import androidx.compose.runtime.rememberCoroutineScope
import kotlinx.coroutines.launch
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
                title = { Text("Random Reminders") },
                actions = {
                    // Roll: a real cube tumbles around two axes and eases onto one flat, random face.
                    val spin = remember { Animatable(0f) }
                    var fromAngles by remember { mutableStateOf(anglesFor(Random.nextInt(1, 7))) }
                    var toAngles by remember { mutableStateOf(fromAngles) }
                    var turns by remember { mutableStateOf(0f to 0f) }
                    val scope = rememberCoroutineScope()
                    IconButton(onClick = {
                        vm.rerollRandom()
                        // The roll is the only cue that the re-roll happened, so it plays even with animations reduced.
                        scope.launch(NormalMotion) {
                            val landing = Random.nextInt(1, 7)
                            // Continue from wherever the die is now so rapid taps never jump.
                            val t = spin.value
                            fromAngles = (fromAngles.first + (toAngles.first + turns.first - fromAngles.first) * t) to
                                (fromAngles.second + (toAngles.second + turns.second - fromAngles.second) * t)
                            toAngles = anglesFor(landing)
                            turns = (360f * Random.nextInt(1, 3)) to (360f * Random.nextInt(2, 4))
                            spin.snapTo(0f)
                            spin.animateTo(1f, tween(1100, easing = FastOutSlowInEasing))
                        }
                    }) {
                        val t = spin.value
                        val rx = fromAngles.first + (toAngles.first + turns.first - fromAngles.first) * t
                        val ry = fromAngles.second + (toAngles.second + turns.second - fromAngles.second) * t
                        Die3D(rx, ry, Modifier.size(28.dp), description = "Re-roll times")
                    }
                },
            )
        },
        floatingActionButton = { FloatingActionButton(onClick = onAdd, containerColor = MaterialTheme.colorScheme.primary, contentColor = MaterialTheme.colorScheme.onPrimary) { Icon(Icons.Filled.Add, contentDescription = "Add random reminder") } },
    ) { padding ->
        LazyColumn(Modifier.fillMaxSize().padding(padding)) {
            item { PauseBanner(settings, onResume = { vm.setPausedUntil(0L) }) }
            item {
                Column(Modifier.padding(16.dp)) {
                    val hours = "between ${hourLabel(settings.activeStartHour)} and ${hourLabel(settings.activeEndHour)}"
                    val rate = Settings.describeInterval(settings.meanIntervalMillis)
                    val text = when (settings.frequencyMode) {
                        FrequencyMode.PER_REMINDER -> "Each of these fires at an unpredictable moment $hours, $rate on average. Both are configurable in Settings."
                        FrequencyMode.WHOLE_POOL -> "One of these fires at an unpredictable moment $hours, $rate on average. Both are configurable in Settings."
                    }
                    Text(text, style = MaterialTheme.typography.bodyMedium, color = MaterialTheme.colorScheme.onSurfaceVariant)
                    if (enabledCount > 1 && settings.frequencyMode == FrequencyMode.PER_REMINDER) {
                        Text("With $enabledCount enabled, that adds up to ${Settings.describeInterval(overallMean.coerceAtLeast(Settings.MIN_MEAN_MILLIS))} overall.",
                            style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onSurfaceVariant, modifier = Modifier.padding(top = 4.dp))
                    }
                }
            }
            if (random.isEmpty()) item {
                Text("No random reminders yet. Tap + or import a CSV file from Settings.",
                    Modifier.padding(16.dp), color = MaterialTheme.colorScheme.onSurfaceVariant)
            }
            items(random, key = { it.id }) { r ->
                val rate = r.meanOverrideMillis?.let { " · " + Settings.describeInterval(it) } ?: ""
                val sub = when {
                    !r.enabled -> "Paused"
                    r.nextAt == null -> "Waiting"
                    settings.showNextRandomTime -> "Next " + Fmt.relative(r.nextAt) + " · " + Fmt.instant(r.nextAt).format(Fmt.dayTime) + rate
                    else -> "Sometime soon$rate"
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
