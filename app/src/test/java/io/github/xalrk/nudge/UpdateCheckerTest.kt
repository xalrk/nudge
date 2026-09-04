package io.github.xalrk.nudge

import io.github.xalrk.nudge.update.UpdateChecker
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

class UpdateCheckerTest {
    @Test fun versionComparison() {
        assertTrue(UpdateChecker.isNewer("1.0.3", "1.0.2"))
        assertTrue(UpdateChecker.isNewer("1.1", "1.0.9"))
        assertTrue(UpdateChecker.isNewer("2.0.0", "1.9.9"))
        assertTrue(UpdateChecker.isNewer("1.0.10", "1.0.9"))
        assertFalse(UpdateChecker.isNewer("1.0.2", "1.0.2"))
        assertFalse(UpdateChecker.isNewer("1.0.2", "1.0.2-debug"))
        assertFalse(UpdateChecker.isNewer("1.0.1", "1.0.2"))
        assertFalse(UpdateChecker.isNewer("v1.0.2".removePrefix("v"), "1.0.2"))
    }
}
