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
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.setValue
import androidx.compose.foundation.Canvas
import androidx.compose.material3.LocalContentColor
import androidx.compose.ui.geometry.CornerRadius
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.geometry.Size
import androidx.compose.ui.graphics.drawscope.Stroke
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.semantics
import kotlin.random.Random
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.ui.MotionDurationScale
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
                    // Roll: the die spins in place around two axes, flicks through faces, lands on a random one.
                    val spin = remember { Animatable(0f) }
                    var face by remember { mutableIntStateOf(Random.nextInt(1, 7)) }
                    val scope = rememberCoroutineScope()
                    IconButton(onClick = {
                        vm.rerollRandom()
                        scope.launch(object : MotionDurationScale { override val scaleFactor: Float get() = 1f }) {
                            val flicker = List(14) { Random.nextInt(1, 7) }
                            val landing = Random.nextInt(1, 7)
                            spin.snapTo(0f)
                            spin.animateTo(1f, tween(1000, easing = FastOutSlowInEasing)) {
                                face = if (value < 0.8f) flicker[(value / 0.8f * flicker.size).toInt().coerceIn(0, flicker.lastIndex)] else landing
                            }
                        }
                    }) {
                        // spin.value is read inside the layer block, so each frame only updates the
                        // GPU transform of this one layer; nothing recomposes or redraws until the face changes.
                        DieFace(
                            face,
                            Modifier.size(24.dp).graphicsLayer {
                                val t = spin.value
                                rotationY = t * 720f
                                rotationX = t * 360f
                                cameraDistance = 8f * density
                                val s = 1f + 0.15f * sin(t * PI.toFloat())
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

/** A die face drawn in the current content color: rounded outline plus 1..6 pips. */
@Composable
private fun DieFace(face: Int, modifier: Modifier = Modifier) {
    val color = LocalContentColor.current
    Canvas(modifier.semantics { contentDescription = "Re-roll times" }) {
        val w = size.width
        val stroke = w * 0.09f
        drawRoundRect(color, topLeft = Offset(stroke / 2, stroke / 2), size = Size(w - stroke, w - stroke),
            cornerRadius = CornerRadius(w * 0.22f), style = Stroke(stroke))
        val c = w / 2
        val o = w * 0.24f
        val pips: List<Offset> = when (face.coerceIn(1, 6)) {
            1 -> listOf(Offset(c, c))
            2 -> listOf(Offset(c - o, c - o), Offset(c + o, c + o))
            3 -> listOf(Offset(c - o, c - o), Offset(c, c), Offset(c + o, c + o))
            4 -> listOf(Offset(c - o, c - o), Offset(c + o, c - o), Offset(c - o, c + o), Offset(c + o, c + o))
            5 -> listOf(Offset(c - o, c - o), Offset(c + o, c - o), Offset(c, c), Offset(c - o, c + o), Offset(c + o, c + o))
            else -> listOf(Offset(c - o, c - o), Offset(c + o, c - o), Offset(c - o, c), Offset(c + o, c), Offset(c - o, c + o), Offset(c + o, c + o))
        }
        for (p in pips) drawCircle(color, radius = w * 0.085f, center = p)
    }
}

fun hourLabel(h: Int): String = when {
    h == 0 || h == 24 -> "midnight"
    h == 12 -> "noon"
    h < 12 -> "${h} am"
    else -> "${h - 12} pm"
}
