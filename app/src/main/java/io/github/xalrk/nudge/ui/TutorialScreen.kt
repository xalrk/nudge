package io.github.xalrk.nudge.ui

import androidx.compose.animation.core.Animatable
import androidx.compose.animation.core.FastOutSlowInEasing
import androidx.compose.animation.core.LinearEasing
import androidx.compose.animation.core.tween
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
import androidx.compose.foundation.pager.HorizontalPager
import androidx.compose.foundation.pager.rememberPagerState
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
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.geometry.CornerRadius
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.geometry.Size
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.StrokeCap
import androidx.compose.ui.graphics.TransformOrigin
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.graphics.toArgb
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import kotlinx.coroutines.delay
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import kotlin.math.PI
import kotlin.math.cos
import kotlin.math.pow
import kotlin.math.sin
import kotlin.random.Random

private data class Page(val title: String, val lines: List<String>, val art: @Composable () -> Unit)

/**
 * A short first-run walkthrough. It only covers what the layout does not make obvious:
 * how random reminders behave, the calendar's color language, and what keeps
 * notifications reliable. Swipe or use the buttons. Reachable again from Settings.
 */
@Composable
fun TutorialScreen(onDone: () -> Unit) {
    val pages = listOf(
        Page("Nudge", listOf(
            "Reminders on a calendar, plus a pool of reminders with no time at all that surface when you least expect them.",
            "Two minutes of setup is all it needs.",
        )) { IntroArt() },
        Page("Random Reminders", listOf(
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
    val pager = rememberPagerState { pages.size }
    val scope = rememberCoroutineScope()
    val index = pager.currentPage
    val last = index == pages.lastIndex
    fun go(page: Int) { scope.launch { pager.animateScrollToPage(page.coerceIn(0, pages.lastIndex)) } }

    Scaffold { padding ->
        Column(Modifier.fillMaxSize().padding(padding)) {
            Row(Modifier.fillMaxWidth().padding(top = 8.dp, end = 12.dp), horizontalArrangement = Arrangement.End) {
                if (!last) TextButton(onClick = onDone) { Text("Skip") }
            }
            HorizontalPager(state = pager, modifier = Modifier.weight(1f), beyondViewportPageCount = 0) { i ->
                val page = pages[i]
                Column(
                    Modifier.fillMaxSize().padding(horizontal = 24.dp).verticalScroll(rememberScrollState()),
                    verticalArrangement = Arrangement.Center,
                ) {
                    Box(Modifier.fillMaxWidth().height(180.dp), contentAlignment = Alignment.Center) { page.art() }
                    Spacer(Modifier.height(24.dp))
                    Text(page.title, style = MaterialTheme.typography.headlineMedium, fontWeight = FontWeight.SemiBold)
                    Spacer(Modifier.height(12.dp))
                    for (line in page.lines) {
                        Text(line, style = MaterialTheme.typography.bodyLarge, color = MaterialTheme.colorScheme.onSurfaceVariant, modifier = Modifier.padding(bottom = 10.dp))
                    }
                }
            }
            Row(Modifier.fillMaxWidth().padding(horizontal = 24.dp, vertical = 16.dp), verticalAlignment = Alignment.CenterVertically) {
                Row(horizontalArrangement = Arrangement.spacedBy(6.dp)) {
                    pages.indices.forEach { i ->
                        Box(
                            Modifier.size(if (i == index) 10.dp else 6.dp).clip(CircleShape)
                                .background(if (i == index) MaterialTheme.colorScheme.primary else MaterialTheme.colorScheme.outlineVariant)
                        )
                    }
                }
                Spacer(Modifier.weight(1f))
                if (index > 0) TextButton(onClick = { go(index - 1) }) { Text("Back") }
                Spacer(Modifier.width(8.dp))
                Button(onClick = { if (last) onDone() else go(index + 1) }) { Text(if (last) "Done" else "Next") }
            }
        }
    }
}

/** Ease-out with overshoot, for drops and pops. */
private fun backOut(x: Float, k: Float = 1.7f): Float { val t = x - 1f; return 1f + t * t * ((k + 1f) * t + k) }

/**
 * The intro: the bell drops in and bounces, rings, the red badge pops onto its shoulder and
 * a burst of colored dots scatters from it. Skipped entirely when the system reduces animations.
 */
@Composable
private fun IntroArt() {
    val reduced = rememberAnimationsReduced()
    val t = remember { Animatable(if (reduced) 1f else 0f) }
    LaunchedEffect(Unit) { if (!reduced) t.animateTo(1f, tween(2400, easing = LinearEasing)) }
    val primary = MaterialTheme.colorScheme.primary
    val badge = Color(0xFFFF3B30)
    val burstColors = remember { listOf(0xFFFF3B30, 0xFFFFB300, 0xFF2ECC71, 0xFF8C9EFF, 0xFFFF6A1F, 0xFF00BFA5, 0xFFD81B60, 0xFF40C4FF).map { Color(it) } }
    val angles = remember { List(8) { it * (2f * PI.toFloat() / 8f) + 0.3f } }

    Box(Modifier.fillMaxWidth().height(180.dp), contentAlignment = Alignment.Center) {
        // Burst dots, drawn behind the bell.
        Canvas(Modifier.fillMaxSize()) {
            val v = t.value
            val b = ((v - 0.66f) / 0.34f).coerceIn(0f, 1f)
            if (b > 0f) {
                val origin = Offset(size.width / 2 + 28.dp.toPx(), size.height / 2 - 30.dp.toPx())
                val ease = 1f - (1f - b).pow(2)
                angles.forEachIndexed { i, a ->
                    val dist = (34f + 26f * (i % 3)) * ease
                    val p = origin + Offset(cos(a) * dist.dp.toPx(), sin(a) * dist.dp.toPx())
                    drawCircle(burstColors[i].copy(alpha = (1f - b).coerceIn(0f, 1f)), radius = (4f + (i % 2) * 2f).dp.toPx() * (0.5f + 0.5f * ease), center = p)
                }
            }
        }
        // Bell: drop, then ring.
        BellShape(Modifier.size(96.dp).graphicsLayer {
            val v = t.value
            val drop = (v / 0.35f).coerceIn(0f, 1f)
            translationY = (1f - backOut(drop)) * -140.dp.toPx()
            val ring = ((v - 0.35f) / 0.3f).coerceIn(0f, 1f)
            transformOrigin = TransformOrigin(0.5f, 0.15f)
            rotationZ = if (ring in 0f..1f && v > 0.35f) sin(ring * PI.toFloat() * 4f) * 14f * (1f - ring) else 0f
            alpha = drop.coerceAtLeast(0.01f)
        }, color = primary)
        // Badge: pop.
        Box(Modifier.fillMaxSize()) {
            Canvas(Modifier.fillMaxSize().graphicsLayer {
                val v = t.value
                val pop = ((v - 0.58f) / 0.18f).coerceIn(0f, 1f)
                val s = if (v < 0.58f) 0f else backOut(pop, 2.2f)
                scaleX = s; scaleY = s
                transformOrigin = TransformOrigin(0.5f + 28f / 360f, 0.5f - 30f / 180f)
            }) {
                drawCircle(badge, radius = 12.dp.toPx(), center = Offset(size.width / 2 + 28.dp.toPx(), size.height / 2 - 30.dp.toPx()))
            }
        }
    }
}

@Composable
private fun BellShape(modifier: Modifier, color: Color = MaterialTheme.colorScheme.primary) {
    Canvas(modifier) {
        val w = size.width
        val bodyW = w * 0.56f
        val left = (w - bodyW) / 2
        drawRoundRect(color, topLeft = Offset(left, w * 0.22f), size = Size(bodyW, w * 0.5f), cornerRadius = CornerRadius(bodyW / 2, bodyW / 2))
        drawRect(color, topLeft = Offset(left, w * 0.45f), size = Size(bodyW, w * 0.3f))
        drawRect(color, topLeft = Offset(left - w * 0.06f, w * 0.72f), size = Size(bodyW + w * 0.12f, w * 0.06f))
        drawCircle(color, radius = w * 0.06f, center = Offset(w / 2, w * 0.2f))
        drawCircle(color, radius = w * 0.07f, center = Offset(w / 2, w * 0.85f))
    }
}

/** A week-long timeline; dots pop in at random moments inside the daytime band, never at night. Loops with fresh moments each pass. */
@Composable
private fun RandomArt() {
    val progress = remember { Animatable(0f) }
    var hits by remember { mutableStateOf(List(9) { Random.nextFloat() }.sorted()) }
    var jitter by remember { mutableStateOf(List(9) { Random.nextFloat() }) }
    // Teaching animation: pinned to normal speed regardless of the system animation setting.
    LaunchedEffect(Unit) {
        withContext(NormalMotion) {
            while (true) {
                progress.snapTo(0f)
                progress.animateTo(1f, tween(2600, easing = LinearEasing))
                delay(700)
                hits = List(9) { Random.nextFloat() }.sorted()
                jitter = List(9) { Random.nextFloat() }
            }
        }
    }
    val primary = MaterialTheme.colorScheme.primary
    val band = MaterialTheme.colorScheme.surfaceContainerHigh
    val muted = MaterialTheme.colorScheme.outlineVariant
    Canvas(Modifier.fillMaxWidth().height(150.dp)) {
        val days = 7
        val colW = size.width / days
        val top = size.height * 0.25f
        val bandH = size.height * 0.45f
        for (d in 0 until days) {
            drawRoundRect(band, topLeft = Offset(d * colW + 3.dp.toPx(), top), size = Size(colW - 6.dp.toPx(), bandH), cornerRadius = CornerRadius(6.dp.toPx()))
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
        drawLine(primary.copy(alpha = 0.6f), Offset(t * size.width, top - 6.dp.toPx()), Offset(t * size.width, top + bandH + 6.dp.toPx()), strokeWidth = 2.dp.toPx(), cap = StrokeCap.Round)
    }
}

/** A week strip: dots appear under days, then the ones in the past fade. */
@Composable
private fun CalendarArt() {
    val progress = remember { Animatable(0f) }
    LaunchedEffect(Unit) { withContext(NormalMotion) { progress.animateTo(1f, tween(2200, easing = FastOutSlowInEasing)) } }
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
            if (d == today) drawRoundRect(primary, topLeft = Offset(cx - 16.dp.toPx(), cy - 16.dp.toPx()), size = Size(32.dp.toPx(), 32.dp.toPx()), cornerRadius = CornerRadius(10.dp.toPx()))
            else drawCircle(onSurface, radius = 3.dp.toPx(), center = Offset(cx, cy))
            val appear = ((t - d * 0.08f) / 0.25f).coerceIn(0f, 1f)
            if (appear > 0f && d != 1 && d != 5) {
                val col = if (d < today) faded else if (d % 2 == 0) primary else second
                drawCircle(col.copy(alpha = appear), radius = 4.dp.toPx() * appear, center = Offset(cx, cy + 26.dp.toPx()))
            }
        }
    }
}

/** The bell rings: a gentle repeating swing around its top. */
@Composable
private fun RingArt() {
    val phase = remember { Animatable(0f) }
    LaunchedEffect(Unit) {
        withContext(NormalMotion) { while (true) { phase.snapTo(0f); phase.animateTo(1f, tween(1400, easing = LinearEasing)) } }
    }
    BellShape(Modifier.size(96.dp).graphicsLayer {
        transformOrigin = TransformOrigin(0.5f, 0.15f)
        val a = phase.value
        rotationZ = sin(a * PI.toFloat() * 2f) * 10f * (1f - a * 0.4f)
    })
}
