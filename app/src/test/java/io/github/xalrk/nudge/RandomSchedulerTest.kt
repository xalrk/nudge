package io.github.xalrk.nudge

import io.github.xalrk.nudge.domain.RandomScheduler
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test
import java.time.Duration
import java.time.ZonedDateTime
import kotlin.random.Random

class RandomSchedulerTest {
    private fun z(s: String) = ZonedDateTime.parse(s)

    @Test fun advanceSkipsNight() {
        val from = z("2026-09-03T22:00-06:00[America/Denver]")
        val got = RandomScheduler.advanceByActiveMillis(from, 2 * 3_600_000L, 7, 23)
        assertEquals("2026-09-04T08:00-06:00[America/Denver]", got.toString())
    }

    @Test fun advanceFromBeforeWindowStartsAtWindow() {
        val from = z("2026-09-03T03:00-06:00[America/Denver]")
        val got = RandomScheduler.advanceByActiveMillis(from, 30 * 60_000L, 7, 23)
        assertEquals("2026-09-03T07:30-06:00[America/Denver]", got.toString())
    }

    @Test fun samplesAlwaysInsideWindowAndAverageMatches() {
        val start = z("2026-09-03T12:00-06:00[America/Denver]")
        val rnd = Random(42)
        val mean = 14L * 24 * 3_600_000L
        val n = 4000
        var total = 0L
        repeat(n) {
            val t = RandomScheduler.sampleNext(start, mean, 7, 23, rnd)
            assertTrue("$t outside window", RandomScheduler.isInsideActiveWindow(t, 7, 23))
            total += Duration.between(start, t).toMillis()
        }
        val avgDays = total / n / 86_400_000.0
        // Nights are skipped but the mean is scaled by the active fraction, so wall-clock average ~ 14 days.
        assertTrue("average was $avgDays days", avgDays > 12.5 && avgDays < 15.5)
    }

    @Test fun samplesAreNotClustered() {
        val start = z("2026-09-03T12:00-06:00[America/Denver]")
        val rnd = Random(7)
        val hours = (1..2000).map { RandomScheduler.sampleNext(start, 3L * 86_400_000L, 7, 23, rnd).hour }.toSet()
        assertEquals((7..22).toSet(), hours)
    }
}

class RandomSchedulerDaysTest {
    private fun z(s: String) = ZonedDateTime.parse(s)

    @Test fun offDaysAreSkipped() {
        val weekdays = java.time.DayOfWeek.entries.filter { it.value <= 5 }.toSet()
        // Friday 22:30 with 1 h of active time left -> skips Sat/Sun, lands Monday.
        val from = z("2026-09-04T22:30-06:00[America/Denver]")
        val got = RandomScheduler.advanceByActiveMillis(from, 2 * 3_600_000L, 7, 23, weekdays)
        assertEquals("2026-09-07T08:30-06:00[America/Denver]", got.toString())
        val rnd = Random(3)
        repeat(500) {
            val t = RandomScheduler.sampleNext(from, 3L * 86_400_000L, 7, 23, rnd, weekdays)
            assertTrue("$t landed on a weekend", t.dayOfWeek in weekdays)
        }
        assertTrue(!RandomScheduler.isInsideActiveWindow(z("2026-09-05T12:00-06:00[America/Denver]"), 7, 23, weekdays))
    }
}
