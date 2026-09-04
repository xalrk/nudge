package io.github.xalrk.nudge.domain

import io.github.xalrk.nudge.data.SettingsSnapshot
import java.time.DayOfWeek
import java.time.Duration
import java.time.ZonedDateTime
import java.time.temporal.ChronoUnit
import kotlin.math.ln
import kotlin.random.Random

/**
 * Random reminders follow a Poisson process that only "ticks" during active hours.
 * The gap to the next firing is drawn from an exponential distribution, so timing is
 * memoryless and genuinely unpredictable, while the long-run average matches the
 * configured mean interval. The mean is scaled by the active fraction of the day so
 * that "once every 2 weeks" still means 2 weeks of wall-clock time even though nights
 * are skipped.
 */
object RandomScheduler {
    private const val MIN_GAP_MILLIS = 60_000L

    /**
     * @param meanIntervalMillis average wall-clock time between firings of this reminder.
     */
    fun sampleNext(
        from: ZonedDateTime,
        meanIntervalMillis: Long,
        startHour: Int,
        endHour: Int,
        random: Random = Random.Default,
        days: Set<DayOfWeek> = DayOfWeek.entries.toSet(),
    ): ZonedDateTime {
        val activeHours = (endHour - startHour).coerceIn(1, 24)
        val activeDays = days.size.coerceIn(1, 7)
        // Scale the mean by the share of the week that is "active" so the wall-clock average holds.
        val activeFraction = activeHours / 24.0 * activeDays / 7.0
        val meanActive = meanIntervalMillis * activeFraction
        val u = random.nextDouble().coerceIn(1e-12, 1.0)
        val gap = (-ln(u) * meanActive).toLong().coerceAtLeast(MIN_GAP_MILLIS)
        return advanceByActiveMillis(from, gap, startHour, endHour, days)
    }

    fun sampleNext(
        from: ZonedDateTime, settings: SettingsSnapshot, poolSize: Int, random: Random = Random.Default,
        overrideMean: Long? = null, overrideDays: Set<DayOfWeek>? = null,
    ): ZonedDateTime {
        val mean = overrideMean ?: effectiveMeanPerReminder(settings, poolSize)
        return sampleNext(from, mean, settings.activeStartHour, settings.activeEndHour, random, overrideDays ?: settings.activeDaySet())
    }

    /** Mean interval for one reminder given the frequency mode and how many random reminders are enabled. */
    fun effectiveMeanPerReminder(settings: SettingsSnapshot, poolSize: Int): Long =
        when (settings.frequencyMode) {
            io.github.xalrk.nudge.data.FrequencyMode.PER_REMINDER -> settings.meanIntervalMillis
            io.github.xalrk.nudge.data.FrequencyMode.WHOLE_POOL -> settings.meanIntervalMillis * poolSize.coerceAtLeast(1)
        }

    fun isInsideActiveWindow(t: ZonedDateTime, startHour: Int, endHour: Int, days: Set<DayOfWeek> = DayOfWeek.entries.toSet()): Boolean {
        val minutes = t.hour * 60 + t.minute
        return t.dayOfWeek in days && minutes >= startHour * 60 && minutes < endHour * 60
    }

    /** Move [from] forward by [millis] of *active* time, skipping everything outside the window and off days. */
    fun advanceByActiveMillis(from: ZonedDateTime, millis: Long, startHour: Int, endHour: Int, days: Set<DayOfWeek> = DayOfWeek.entries.toSet()): ZonedDateTime {
        var cursor = from
        var remaining = millis
        var guard = 0
        val allowed = if (days.isEmpty()) DayOfWeek.entries.toSet() else days
        while (guard++ < 200_000) {
            val date = cursor.toLocalDate()
            if (date.dayOfWeek !in allowed) {
                cursor = date.plusDays(1).atStartOfDay(cursor.zone).plusHours(startHour.toLong())
                continue
            }
            val dayStart = date.atStartOfDay(cursor.zone).plusHours(startHour.toLong())
            val dayEnd = if (endHour >= 24) date.plusDays(1).atStartOfDay(cursor.zone)
                         else date.atStartOfDay(cursor.zone).plusHours(endHour.toLong())
            if (cursor.isBefore(dayStart)) cursor = dayStart
            if (!cursor.isBefore(dayEnd)) {
                cursor = date.plusDays(1).atStartOfDay(cursor.zone).plusHours(startHour.toLong())
                continue
            }
            val available = Duration.between(cursor, dayEnd).toMillis()
            if (remaining <= available) return cursor.plus(remaining, ChronoUnit.MILLIS)
            remaining -= available
            cursor = date.plusDays(1).atStartOfDay(cursor.zone).plusHours(startHour.toLong())
        }
        return cursor
    }
}
