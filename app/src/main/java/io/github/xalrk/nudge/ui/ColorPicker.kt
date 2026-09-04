package io.github.xalrk.nudge.ui

import androidx.compose.foundation.Canvas
import androidx.compose.foundation.ExperimentalFoundationApi
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.combinedClickable
import androidx.compose.foundation.gestures.detectDragGestures
import androidx.compose.foundation.gestures.detectTapGestures
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.FlowRow
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Add
import androidx.compose.material.icons.filled.Check
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.luminance
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.text.input.KeyboardType
import androidx.compose.ui.unit.dp
import io.github.xalrk.nudge.data.Settings
import android.graphics.Color as AColor

/** Swatch label shown on a circle: a check when selected, otherwise nothing. */
@Composable
private fun Swatch(argb: Int, selected: Boolean, name: String, onClick: () -> Unit, onLongClick: (() -> Unit)? = null, badge: String? = null) {
    val fg = if (Color(argb).luminance() < 0.4f) Color.White else Color.Black
    Box(
        Modifier.size(36.dp).clip(CircleShape).background(Color(argb))
            .then(if (selected) Modifier.border(3.dp, MaterialTheme.colorScheme.onSurface, CircleShape) else Modifier)
            .combinedClickable(onClick = onClick, onLongClick = onLongClick)
            .semantics { contentDescription = name },
        contentAlignment = Alignment.Center,
    ) {
        if (selected) Icon(Icons.Filled.Check, contentDescription = null, tint = fg, modifier = Modifier.size(18.dp))
        else if (badge != null) Text(badge, color = fg, style = MaterialTheme.typography.labelLarge)
    }
}

/**
 * Presets, then the user's custom colors, then a "+" that opens the picker.
 * [autoColor] adds a leading "A" swatch meaning "automatic" (selected when [current] is null).
 */
@OptIn(ExperimentalFoundationApi::class)
@Composable
fun SwatchRow(
    current: Int?,
    customColors: List<Int>,
    onPick: (Int?) -> Unit,
    onAddCustom: (Int) -> Unit,
    onRemoveCustom: (Int) -> Unit,
    autoColor: Int? = null,
) {
    var showPicker by remember { mutableStateOf(false) }
    FlowRow(horizontalArrangement = Arrangement.spacedBy(10.dp), verticalArrangement = Arrangement.spacedBy(10.dp), modifier = Modifier.padding(top = 8.dp)) {
        if (autoColor != null) Swatch(autoColor, selected = current == null, name = "Automatic", onClick = { onPick(null) }, badge = "A")
        for ((name, argb) in Settings.ACCENT_PRESETS) Swatch(argb, selected = argb == current, name = name, onClick = { onPick(argb) })
        for (argb in customColors) {
            if (Settings.ACCENT_PRESETS.any { it.second == argb }) continue
            Swatch(argb, selected = argb == current, name = Settings.toHex(argb), onClick = { onPick(argb) }, onLongClick = { onRemoveCustom(argb) })
        }
        Box(
            Modifier.size(36.dp).clip(CircleShape).border(1.5.dp, MaterialTheme.colorScheme.outline, CircleShape)
                .combinedClickable(onClick = { showPicker = true })
                .semantics { contentDescription = "Add a custom color" },
            contentAlignment = Alignment.Center,
        ) { Icon(Icons.Filled.Add, contentDescription = null, tint = MaterialTheme.colorScheme.onSurfaceVariant, modifier = Modifier.size(20.dp)) }
    }
    if (customColors.isNotEmpty()) Text("Long-press a custom color to remove it.", style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onSurfaceVariant, modifier = Modifier.padding(top = 6.dp))
    if (showPicker) ColorPickerDialog(
        initial = current ?: autoColor ?: Settings.DEFAULT_ACCENT,
        onDismiss = { showPicker = false },
        onPick = { onAddCustom(it); onPick(it); showPicker = false },
    )
}

/** Hue bar + saturation/value square + hex field. */
@Composable
fun ColorPickerDialog(initial: Int, onDismiss: () -> Unit, onPick: (Int) -> Unit) {
    val start = remember(initial) { FloatArray(3).also { AColor.colorToHSV(initial, it) } }
    var hue by remember { mutableStateOf(start[0]) }
    var sat by remember { mutableStateOf(start[1]) }
    var value by remember { mutableStateOf(start[2]) }
    val argb = AColor.HSVToColor(0xFF, floatArrayOf(hue, sat, value))
    var hex by remember(argb) { mutableStateOf(Settings.toHex(argb)) }

    AlertDialog(
        onDismissRequest = onDismiss,
        title = { Text("Custom color") },
        confirmButton = { TextButton(onClick = { onPick(argb) }) { Text("Add") } },
        dismissButton = { TextButton(onClick = onDismiss) { Text("Cancel") } },
        text = {
            Column(verticalArrangement = Arrangement.spacedBy(12.dp)) {
                // Saturation (x) / value (y) square for the current hue.
                val pureHue = Color(AColor.HSVToColor(0xFF, floatArrayOf(hue, 1f, 1f)))
                Canvas(
                    Modifier.fillMaxWidth().height(180.dp).clip(MaterialTheme.shapes.medium)
                        .pointerInput(Unit) {
                            fun set(o: Offset) { sat = (o.x / size.width).coerceIn(0f, 1f); value = 1f - (o.y / size.height).coerceIn(0f, 1f) }
                            detectDragGestures(onDragStart = { set(it) }) { change, _ -> set(change.position) }
                        }
                        .pointerInput(Unit) { detectTapGestures { sat = (it.x / size.width).coerceIn(0f, 1f); value = 1f - (it.y / size.height).coerceIn(0f, 1f) } }
                ) {
                    drawRect(Brush.horizontalGradient(listOf(Color.White, pureHue)))
                    drawRect(Brush.verticalGradient(listOf(Color.Transparent, Color.Black)))
                    val p = Offset(sat * size.width, (1f - value) * size.height)
                    drawCircle(Color.Black, radius = 9.dp.toPx(), center = p, style = androidx.compose.ui.graphics.drawscope.Stroke(3.dp.toPx()))
                    drawCircle(Color.White, radius = 9.dp.toPx(), center = p, style = androidx.compose.ui.graphics.drawscope.Stroke(1.5.dp.toPx()))
                }
                // Hue bar.
                Canvas(
                    Modifier.fillMaxWidth().height(28.dp).clip(CircleShape)
                        .pointerInput(Unit) {
                            fun set(x: Float) { hue = (x / size.width).coerceIn(0f, 0.9999f) * 360f }
                            detectDragGestures(onDragStart = { set(it.x) }) { change, _ -> set(change.position.x) }
                        }
                        .pointerInput(Unit) { detectTapGestures { hue = (it.x / size.width).coerceIn(0f, 0.9999f) * 360f } }
                ) {
                    val stops = (0..6).map { Color(AColor.HSVToColor(0xFF, floatArrayOf(it * 60f % 360f, 1f, 1f))) }
                    drawRect(Brush.horizontalGradient(stops))
                    val x = hue / 360f * size.width
                    drawCircle(Color.Black, radius = 10.dp.toPx(), center = Offset(x, size.height / 2), style = androidx.compose.ui.graphics.drawscope.Stroke(3.dp.toPx()))
                    drawCircle(Color.White, radius = 10.dp.toPx(), center = Offset(x, size.height / 2), style = androidx.compose.ui.graphics.drawscope.Stroke(1.5.dp.toPx()))
                }
                Row(verticalAlignment = Alignment.CenterVertically) {
                    Box(Modifier.size(44.dp).clip(CircleShape).background(Color(argb)).border(1.dp, MaterialTheme.colorScheme.outlineVariant, CircleShape))
                    Spacer(Modifier.width(12.dp))
                    OutlinedTextField(
                        value = hex,
                        onValueChange = { t ->
                            hex = t.take(7)
                            Settings.parseHex(t)?.let { c ->
                                val h = FloatArray(3); AColor.colorToHSV(c, h); hue = h[0]; sat = h[1]; value = h[2]
                            }
                        },
                        label = { Text("Hex") }, singleLine = true, isError = Settings.parseHex(hex) == null,
                        keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Ascii),
                        modifier = Modifier.width(150.dp),
                    )
                }
            }
        },
    )
}
