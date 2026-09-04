package io.github.xalrk.nudge.ui

import androidx.compose.animation.AnimatedContent
import androidx.compose.animation.core.Animatable
import androidx.compose.animation.core.FastOutSlowInEasing
import androidx.compose.animation.core.LinearEasing
import androidx.compose.animation.core.RepeatMode
import androidx.compose.animation.core.animateFloat
import androidx.compose.animation.core.infiniteRepeatable
import androidx.compose.animation.core.rememberInfiniteTransition
import androidx.compose.animation.core.tween
import androidx.compose.animation.fadeIn
import androidx.compose.animation.fadeOut
import androidx.compose.animation.slideInHorizontally
import androidx.compose.animation.slideOutHorizontally
import androidx.compose.animation.togetherWith
import androidx.compose.foundation.Canvas
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.Button
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.geometry.Size
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.StrokeCap
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.graphics.toArgb
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import kotlin.math.sin
import kotlin.random.Random

private data class Page(val title: String, val lines: List<String>, val art: @Composable () -> Unit)

/**
 * A short first-run walkthrough. It only covers what the layout does not make obvious:
 * how random reminders behave, the calendar's colour language, and what keeps
 * notifications reliable. Reachable again from Settings.
 */
@Composable
fun TutorialScreen(onDone: () -> Unit) {
    val pages = listOf(
        Page("Nudge", listOf(
            "Reminders on a calendar, plus a pool of reminders with no time at all that surface when you least expect them.",
            "Two minutes of setup is all it needs.",
        )) { BellArt() },
        Page("Random reminders", listOf(
            "Anything without a date or time goes into the random pool. Each one fires at an unpredictable moment inside your active hours, about once every two weeks on average by default.",
            "They work best as a big list you rarely think about: affirmations, small habits, questions to sit with, things you keep meaning to do. Import a whole list at once from Settings.",
            "The Settings slider sets the average rate for the pool; any reminder can override it with its own rate.",
        )) { RandomArt() },
        Page("Calendar", listOf(
            "Tap a day, then + to add something on that day.",
            "Each dot is a reminder in its own color. A faded dot means it was already delivered.",
            "Changing or deleting a repeating reminder asks whether you mean only that occurrence, everything from then on, or the whole series.",
        )) { CalendarArt() },
        Page("Keep them coming", listOf(
            "Nudge has no background service and never polls, so it costs no battery between reminders.",
            "Some phones still kill quiet apps. If reminders go missing, set Nudge to unrestricted battery use from Settings → Reliability.",
            "Every notification can be snoozed for 10 minutes, an hour, or until tomorrow morning.",
        )) { RingArt() },
    )
    var index by remember { mutableIntStateOf(0) }
    val last = index == pages.lastIndex

    Scaffold { padding ->
        Column(Modifier.fillMaxSize().padding(padding).padding(horizontal = 24.dp)) {
            Row(Modifier.fillMaxWidth().padding(top = 8.dp), horizontalArrangement = Arrangement.End) {
                if (!last) TextButton(onClick = onDone) { Text("Skip") }
            }
            AnimatedContent(
                targetState = index,
                transitionSpec = {
                    val forward = targetState > initialState
                    (slideInHorizontally(tween(320, easing = FastOutSlowInEasing)) { if (forward) it / 3 else -it / 3 } + fadeIn(tween(320)))
                        .togetherWith(slideOutHorizontally(tween(220)) { if (forward) -it / 3 else it / 3 } + fadeOut(tween(180)))
                },
                modifier = Modifier.weight(1f),
                label = "tutorial page",
            ) { i ->
                val page = pages[i]
                Column(Modifier.fillMaxSize().verticalScroll(rememberScrollState()), verticalArrangement = Arrangement.Center) {
                    Box(Modifier.fillMaxWidth().height(180.dp), contentAlignment = Alignment.Center) { page.art() }
                    Spacer(Modifier.height(24.dp))
                    Text(page.title, style = MaterialTheme.typography.headlineMedium, fontWeight = FontWeight.SemiBold)
                    Spacer(Modifier.height(12.dp))
                    for (line in page.lines) {
                        Text(line, style = MaterialTheme.typography.bodyLarge, color = MaterialTheme.colorScheme.onSurfaceVariant, modifier = Modifier.padding(bottom = 10.dp))
                    }
                }
            }
            Row(Modifier.fillMaxWidth().padding(vertical = 16.dp), verticalAlignment = Alignment.CenterVertically) {
                Row(horizontalArrangement = Arrangement.spacedBy(6.dp)) {
                    pages.indices.forEach { i ->
                        Box(
                            Modifier.size(if (i == index) 10.dp else 6.dp).clip(CircleShape)
                                .background(if (i == index) MaterialTheme.colorScheme.primary else MaterialTheme.colorScheme.outlineVariant)
                        )
                    }
                }
                Spacer(Modifier.weight(1f))
                if (index > 0) TextButton(onClick = { index-- }) { Text("Back") }
                Spacer(Modifier.width(8.dp))
                Button(onClick = { if (last) onDone() else index++ }) { Text(if (last) "Done" else "Next") }
            }
        }
    }
}

/** The bell scales in with a soft overshoot and a ring expands once behind it. */
@Composable
private fun BellArt() {
    val enter = remember { Animatable(0f) }
    LaunchedEffect(Unit) { enter.animateTo(1f, tween(700, easing = FastOutSlowInEasing)) }
    val primary = MaterialTheme.colorScheme.primary
    Box(contentAlignment = Alignment.Center) {
        Canvas(Modifier.size(180.dp)) {
            val t = enter.value
            drawCircle(primary.copy(alpha = (1f - t) * 0.5f), radius = size.minDimension / 2 * (0.4f + 0.6f * t))
        }
        BellShape(Modifier.size(96.dp).graphicsLayer {
            val s = 0.6f + 0.4f * enter.value + 0.08f * sin(enter.value * Math.PI.toFloat())
            scaleX = s; scaleY = s; alpha = enter.value.coerceIn(0f, 1f)
        })
    }
}

@Composable
private fun BellShape(modifier: Modifier) {
    val color = MaterialTheme.colorScheme.primary
    Canvas(modifier) {
        val w = size.width
        // Bell body: same silhouette as the launcher icon, drawn as simple shapes.
        val bodyW = w * 0.56f
        val left = (w - bodyW) / 2
        drawRoundRect(color, topLeft = Offset(left, w * 0.22f), size = Size(bodyW, w * 0.5f), cornerRadius = androidx.compose.ui.geometry.CornerRadius(bodyW / 2, bodyW / 2))
        drawRect(color, topLeft = Offset(left, w * 0.45f), size = Size(bodyW, w * 0.3f))
        drawRect(color, topLeft = Offset(left - w * 0.06f, w * 0.72f), size = Size(bodyW + w * 0.12f, w * 0.06f))
        drawCircle(color, radius = w * 0.06f, center = Offset(w / 2, w * 0.2f))
        drawCircle(color, radius = w * 0.07f, center = Offset(w / 2, w * 0.85f))
    }
}

/** A week-long timeline; dots pop in at random moments inside the daytime band, never at night. */
@Composable
private fun RandomArt() {
    val progress = remember { Animatable(0f) }
    LaunchedEffect(Unit) { progress.animateTo(1f, tween(2600, easing = LinearEasing)) }
    val seed = remember { Random(System.nanoTime()) }
    val hits = remember { List(9) { seed.nextFloat() } .sorted() }
    val jitter = remember { List(9) { seed.nextFloat() } }
    val primary = MaterialTheme.colorScheme.primary
    val band = MaterialTheme.colorScheme.surfaceContainerHigh
    val muted = MaterialTheme.colorScheme.outlineVariant
    Canvas(Modifier.fillMaxWidth().height(150.dp)) {
        val days = 7
        val colW = size.width / days
        val top = size.height * 0.25f
        val bandH = size.height * 0.45f
        for (d in 0 until days) {
            drawRoundRect(band, topLeft = Offset(d * colW + 3.dp.toPx(), top), size = Size(colW - 6.dp.toPx(), bandH), cornerRadius = androidx.compose.ui.geometry.CornerRadius(6.dp.toPx()))
            drawLine(muted, Offset(d * colW + colW / 2, top + bandH + 10.dp.toPx()), Offset(d * colW + colW / 2, top + bandH + 16.dp.toPx()), strokeWidth = 2.dp.toPx(), cap = StrokeCap.Round)
        }
        val t = progress.value
        hits.forEachIndexed { i, h ->
            if (h <= t) {
                val age = ((t - h) / 0.12f).coerceIn(0f, 1f)
                val r = 5.dp.toPx() * (1f + 0.6f * (1f - age))
                drawCircle(primary, radius = r, center = Offset(h * size.width, top + bandH * (0.15f + 0.7f * jitter[i])))
            }
        }
        // Playhead.
        drawLine(primary.copy(alpha = 0.6f), Offset(t * size.width, top - 6.dp.toPx()), Offset(t * size.width, top + bandH + 6.dp.toPx()), strokeWidth = 2.dp.toPx(), cap = StrokeCap.Round)
    }
}

/** A week strip: dots appear under days, then the ones in the past fade. */
@Composable
private fun CalendarArt() {
    val progress = remember { Animatable(0f) }
    LaunchedEffect(Unit) { progress.animateTo(1f, tween(2200, easing = FastOutSlowInEasing)) }
    val primary = MaterialTheme.colorScheme.primary
    val second = MaterialTheme.colorScheme.tertiary
    val onSurface = MaterialTheme.colorScheme.onSurface
    val faded = Color(io.github.xalrk.nudge.domain.Colors.faded(primary.toArgb()))
    Canvas(Modifier.fillMaxWidth().height(150.dp)) {
        val days = 7
        val colW = size.width / days
        val t = progress.value
        val today = 3
        for (d in 0 until days) {
            val cx = d * colW + colW / 2
            val cy = size.height * 0.45f
            if (d == today) drawRoundRect(primary, topLeft = Offset(cx - 16.dp.toPx(), cy - 16.dp.toPx()), size = Size(32.dp.toPx(), 32.dp.toPx()), cornerRadius = androidx.compose.ui.geometry.CornerRadius(10.dp.toPx()))
            else drawCircle(onSurface, radius = 3.dp.toPx(), center = Offset(cx, cy))
            val appear = ((t - d * 0.08f) / 0.25f).coerceIn(0f, 1f)
            if (appear > 0f && d != 1 && d != 5) {
                val col = if (d < today) faded else if (d % 2 == 0) primary else second
                drawCircle(col.copy(alpha = appear), radius = 4.dp.toPx() * appear, center = Offset(cx, cy + 26.dp.toPx()))
            }
        }
    }
}


/** The bell rings: a gentle repeating rotation around its top. */
@Composable
private fun RingArt() {
    val transition = rememberInfiniteTransition(label = "ring")
    val angle by transition.animateFloat(0f, 1f, infiniteRepeatable(tween(1400, easing = LinearEasing), RepeatMode.Restart), label = "angle")
    BellShape(Modifier.size(96.dp).graphicsLayer {
        transformOrigin = androidx.compose.ui.graphics.TransformOrigin(0.5f, 0.15f)
        rotationZ = sin(angle * Math.PI.toFloat() * 2f) * 10f * (1f - angle * 0.4f)
    })
}
