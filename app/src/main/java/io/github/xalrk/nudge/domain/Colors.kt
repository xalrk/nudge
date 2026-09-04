package io.github.xalrk.nudge.domain

import android.graphics.Color as AColor

object Colors {
    /** Hue rotated by 180 degrees, keeping saturation and value; the default notification color. */
    fun complementary(argb: Int): Int {
        val hsv = FloatArray(3)
        AColor.colorToHSV(argb, hsv)
        hsv[0] = (hsv[0] + 180f) % 360f
        // A grey accent has no hue to flip; give it a usable tint instead.
        if (hsv[1] < 0.08f) { hsv[0] = 30f; hsv[1] = 0.85f; hsv[2] = 0.95f }
        return AColor.HSVToColor(0xFF, hsv)
    }

    /** Same hue at half saturation and reduced brightness: the "already happened" look. */
    fun faded(argb: Int): Int {
        val hsv = FloatArray(3)
        AColor.colorToHSV(argb, hsv)
        hsv[1] *= 0.5f
        hsv[2] = (hsv[2] * 0.6f).coerceAtLeast(0.35f)
        return AColor.HSVToColor(0xFF, hsv)
    }
}
