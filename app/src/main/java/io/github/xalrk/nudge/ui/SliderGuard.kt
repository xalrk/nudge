package io.github.xalrk.nudge.ui

import androidx.compose.foundation.gestures.awaitEachGesture
import androidx.compose.foundation.gestures.awaitFirstDown
import androidx.compose.foundation.layout.Box
import androidx.compose.runtime.Composable
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.ui.Modifier
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.input.pointer.PointerEventPass
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.ui.input.pointer.positionChange
import androidx.compose.ui.platform.LocalViewConfiguration
import kotlin.math.abs

private enum class Intent { UNDECIDED, SLIDE, SCROLL, TAP }

/**
 * Wraps a slider so a vertical scroll that happens to start on it does not change the value.
 *
 * Material sliders move the thumb the moment a finger touches the track. This guard watches
 * the gesture in the initial pointer pass: value changes are held back until the finger has
 * moved sideways past touch slop (a deliberate slide) or lifted without moving (a tap).
 * If the finger moves mostly vertically first, the held changes are dropped and the page
 * scrolls as usual. Works for any value type, so both Slider and RangeSlider use it.
 *
 * Usage:
 *   SliderGuard(value, onCommit = { v -> ... }, onFinished = { ... }) { onChange, onFinished ->
 *       Slider(value = value, onValueChange = onChange, onValueChangeFinished = onFinished)
 *   }
 */
@Composable
fun <T> SliderGuard(
    value: T,
    onCommit: (T) -> Unit,
    onFinished: () -> Unit,
    content: @Composable (onChange: (T) -> Unit, onFinished: () -> Unit) -> Unit,
) {
    val slop = LocalViewConfiguration.current.touchSlop
    val intent = remember { mutableStateOf(Intent.UNDECIDED) }
    val pending = remember { mutableStateOf<T?>(null) }
    val committed = remember { mutableStateOf(false) }

    val onChange: (T) -> Unit = { v ->
        when (intent.value) {
            Intent.SLIDE, Intent.TAP -> { onCommit(v); committed.value = true }
            Intent.UNDECIDED -> pending.value = v
            Intent.SCROLL -> {}
        }
    }
    val finished: () -> Unit = {
        if (intent.value != Intent.SCROLL) {
            pending.value?.let { onCommit(it); committed.value = true }
            if (committed.value) onFinished()
        }
        pending.value = null
        committed.value = false
        intent.value = Intent.UNDECIDED
    }

    Box(
        Modifier.pointerInput(Unit) {
            awaitEachGesture {
                val down = awaitFirstDown(requireUnconsumed = false, pass = PointerEventPass.Initial)
                intent.value = Intent.UNDECIDED
                pending.value = null
                committed.value = false
                var total = Offset.Zero
                while (true) {
                    val event = awaitPointerEvent(PointerEventPass.Initial)
                    val change = event.changes.firstOrNull { it.id == down.id } ?: break
                    if (!change.pressed) {
                        if (intent.value == Intent.UNDECIDED) intent.value = Intent.TAP
                        break
                    }
                    if (intent.value == Intent.UNDECIDED) {
                        total += change.positionChange()
                        if (abs(total.y) > slop && abs(total.y) > abs(total.x)) {
                            intent.value = Intent.SCROLL
                            pending.value = null
                        } else if (abs(total.x) > slop) {
                            intent.value = Intent.SLIDE
                            pending.value?.let { onCommit(it); committed.value = true }
                            pending.value = null
                        }
                    }
                }
            }
        }
    ) {
        content(onChange, finished)
    }
}
