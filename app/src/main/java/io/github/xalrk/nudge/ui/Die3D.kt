package io.github.xalrk.nudge.ui

import androidx.compose.foundation.Canvas
import androidx.compose.material3.LocalContentColor
import androidx.compose.material3.MaterialTheme
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.Path
import androidx.compose.ui.graphics.drawscope.DrawScope
import androidx.compose.ui.graphics.drawscope.Stroke
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.semantics
import kotlin.math.cos
import kotlin.math.sin

/**
 * A wireframe die rendered as a real cube. [rotX]/[rotY] are in degrees; at the angles from
 * [anglesFor] a single face is flat toward the viewer, which is how a roll ends.
 */
@Composable
fun Die3D(rotX: Float, rotY: Float, modifier: Modifier = Modifier, description: String = "Die") {
    val line = LocalContentColor.current
    val fill = MaterialTheme.colorScheme.surface
    Canvas(modifier.semantics { contentDescription = description }) { drawDie(rotX, rotY, line, fill) }
}

/** Rotation (x, y) in degrees that presents [face] (1..6) squarely to the viewer. */
fun anglesFor(face: Int): Pair<Float, Float> = when (face.coerceIn(1, 6)) {
    1 -> 0f to 0f        // +Z front
    2 -> 0f to -90f      // +X right
    3 -> 90f to 0f       // +Y top (y up in model space)
    4 -> -90f to 0f      // -Y bottom
    5 -> 0f to 90f       // -X left
    else -> 0f to 180f   // -Z back
}

private class V3(val x: Float, val y: Float, val z: Float)

private fun rotate(v: V3, ax: Float, ay: Float): V3 {
    // Rotate about X, then about Y.
    val cx = cos(ax); val sx = sin(ax)
    val y1 = v.y * cx - v.z * sx
    val z1 = v.y * sx + v.z * cx
    val cy = cos(ay); val sy = sin(ay)
    val x2 = v.x * cy + z1 * sy
    val z2 = -v.x * sy + z1 * cy
    return V3(x2, y1, z2)
}

/** Faces as (normal, u axis, v axis, pip count). Opposite faces sum to seven. */
private val FACES: List<Triple<V3, Pair<V3, V3>, Int>> = listOf(
    Triple(V3(0f, 0f, 1f), V3(1f, 0f, 0f) to V3(0f, 1f, 0f), 1),
    Triple(V3(1f, 0f, 0f), V3(0f, 0f, -1f) to V3(0f, 1f, 0f), 2),
    Triple(V3(0f, 1f, 0f), V3(1f, 0f, 0f) to V3(0f, 0f, -1f), 3),
    Triple(V3(0f, -1f, 0f), V3(1f, 0f, 0f) to V3(0f, 0f, 1f), 4),
    Triple(V3(-1f, 0f, 0f), V3(0f, 0f, 1f) to V3(0f, 1f, 0f), 5),
    Triple(V3(0f, 0f, -1f), V3(-1f, 0f, 0f) to V3(0f, 1f, 0f), 6),
)

private fun pips(n: Int): List<Pair<Float, Float>> {
    val o = 0.5f
    return when (n) {
        1 -> listOf(0f to 0f)
        2 -> listOf(-o to -o, o to o)
        3 -> listOf(-o to -o, 0f to 0f, o to o)
        4 -> listOf(-o to -o, o to -o, -o to o, o to o)
        5 -> listOf(-o to -o, o to -o, 0f to 0f, -o to o, o to o)
        else -> listOf(-o to -o, o to -o, -o to 0f, o to 0f, -o to o, o to o)
    }
}

private fun DrawScope.drawDie(rotXDeg: Float, rotYDeg: Float, line: Color, fill: Color) {
    val ax = Math.toRadians(rotXDeg.toDouble()).toFloat()
    val ay = Math.toRadians(rotYDeg.toDouble()).toFloat()
    val half = size.minDimension * 0.30f
    val camera = size.minDimension * 2.2f
    val center = Offset(size.width / 2, size.height / 2)
    fun project(v: V3): Offset {
        val r = rotate(V3(v.x * half, v.y * half, v.z * half), ax, ay)
        val s = camera / (camera - r.z)
        return Offset(center.x + r.x * s, center.y - r.y * s)
    }
    val stroke = size.minDimension * 0.075f
    val visible = FACES.map { it to rotate(it.first, ax, ay).z }.filter { it.second > 0.02f }.sortedBy { it.second }
    for ((face, _) in visible) {
        val (n, axes, count) = face
        val (u, v) = axes
        fun corner(su: Float, sv: Float) = project(V3(n.x + u.x * su + v.x * sv, n.y + u.y * su + v.y * sv, n.z + u.z * su + v.z * sv))
        val path = Path().apply {
            moveTo(corner(-1f, -1f).x, corner(-1f, -1f).y)
            lineTo(corner(1f, -1f).x, corner(1f, -1f).y)
            lineTo(corner(1f, 1f).x, corner(1f, 1f).y)
            lineTo(corner(-1f, 1f).x, corner(-1f, 1f).y)
            close()
        }
        drawPath(path, fill)
        drawPath(path, line, style = Stroke(stroke, join = androidx.compose.ui.graphics.StrokeJoin.Round))
        val facing = rotate(n, ax, ay).z.coerceIn(0f, 1f)
        for ((pu, pv) in pips(count)) {
            val p = project(V3(n.x + u.x * pu + v.x * pv, n.y + u.y * pu + v.y * pv, n.z + u.z * pu + v.z * pv))
            drawCircle(line, radius = size.minDimension * 0.07f * (0.6f + 0.4f * facing), center = p)
        }
    }
}
