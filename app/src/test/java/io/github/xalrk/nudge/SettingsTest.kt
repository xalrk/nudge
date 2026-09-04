package io.github.xalrk.nudge

import io.github.xalrk.nudge.data.Settings
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Test

class SettingsTest {
    @Test fun hexParsing() {
        assertEquals(0xFF3D5AFE.toInt(), Settings.parseHex("#3D5AFE"))
        assertEquals(0xFF3D5AFE.toInt(), Settings.parseHex("3d5afe"))
        assertEquals(0xFFFF0000.toInt(), Settings.parseHex("#F00"))
        assertNull(Settings.parseHex("#12345"))
        assertNull(Settings.parseHex("#GGGGGG"))
        assertNull(Settings.parseHex(""))
        assertEquals("#3D5AFE", Settings.toHex(0xFF3D5AFE.toInt()))
        assertEquals(Settings.DEFAULT_ACCENT, Settings.ACCENT_PRESETS.first().second)
    }

    @Test fun sliderRoundTrip() {
        for (ms in listOf(Settings.MIN_MEAN_MILLIS, Settings.DEFAULT_MEAN_MILLIS, Settings.MAX_MEAN_MILLIS)) {
            val back = Settings.sliderToMillis(Settings.millisToSlider(ms))
            assert(kotlin.math.abs(back - ms) < ms * 0.01) { "$ms -> $back" }
        }
    }
}
