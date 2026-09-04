package io.github.xalrk.nudge.domain

import io.github.xalrk.nudge.data.Reminder
import io.github.xalrk.nudge.data.Repeat
import java.time.DayOfWeek
import java.time.Instant
import java.time.LocalDate
import java.time.LocalDateTime
import java.time.ZoneId
import java.time.ZonedDateTime
import java.time.temporal.ChronoUnit

/**
 * Pure recurrence arithmetic on wall-clock times. Every calculation is done in the
 * reminder's effective zone (device zone for floating reminders), so a 9:00 reminder
 * stays at 9:00 on the wall clock after a time-zone change or a DST switch.
 */
object Recurrence {

    /** First occurrence strictly after [after], or null when the series is over. */
    fun nextOccurrenceAfter(r: Reminder, after: Instant): ZonedDateTime? {
        val start = r.localDateTimeOrNull() ?: return null
        val zone = r.effectiveZone()
        val startZ = start.atZone(zone)
        val afterZ = after.atZone(zone)
        val end = r.endDateOrNull()
        val interval = r.interval.coerceAtLeast(1)

        fun ok(z: ZonedDateTime?): ZonedDateTime? =
            z?.takeIf { end == null || !it.toLocalDate().isAfter(end) }

        if (r.repeat == Repeat.NONE) return if (startZ.isAfter(afterZ)) startZ else null
        // The start moment itself is the first occurrence, unless a weekly rule excludes its weekday.
        val startCounts = r.repeat != Repeat.WEEKLY || r.weekdays == 0 || start.dayOfWeek in r.weekdaySet()
        if (startZ.isAfter(afterZ) && startCounts) return ok(startZ)

        return when (r.repeat) {
            Repeat.NONE -> null
            Repeat.DAILY -> {
                val days = ChronoUnit.DAYS.between(startZ.toLocalDate(), afterZ.toLocalDate())
                var k = days / interval
                var cand = start.plusDays(k * interval).atZone(zone)
                while (!cand.isAfter(afterZ)) { k++; cand = start.plusDays(k * interval).atZone(zone) }
                ok(cand)
            }
            Repeat.WEEKLY -> nextWeekly(start, zone, afterZ, interval, r.weekdaySet(), end)
            Repeat.MONTHLY -> {
                val months = ChronoUnit.MONTHS.between(startZ.toLocalDate().withDayOfMonth(1), afterZ.toLocalDate().withDayOfMonth(1))
                var k = (months / interval - 1).coerceAtLeast(0)
                var cand = start.plusMonths(k * interval).atZone(zone)
                while (!cand.isAfter(afterZ)) { k++; cand = start.plusMonths(k * interval).atZone(zone) }
                ok(cand)
            }
            Repeat.YEARLY -> {
                val years = ChronoUnit.YEARS.between(startZ.toLocalDate(), afterZ.toLocalDate())
                var k = (years / interval - 1).coerceAtLeast(0)
                var cand = start.plusYears(k * interval).atZone(zone)
                while (!cand.isAfter(afterZ)) { k++; cand = start.plusYears(k * interval).atZone(zone) }
                ok(cand)
            }
        }
    }

    private fun nextWeekly(
        start: LocalDateTime, zone: ZoneId, afterZ: ZonedDateTime, interval: Int,
        weekdays: Set<DayOfWeek>, end: LocalDate?
    ): ZonedDateTime? {
        val days = if (weekdays.isEmpty()) setOf(start.dayOfWeek) else weekdays
        // Week blocks start on the Monday of the start date's week.
        val blockStart = start.toLocalDate().with(DayOfWeek.MONDAY)
        val weeksElapsed = ChronoUnit.WEEKS.between(blockStart, afterZ.toLocalDate().with(DayOfWeek.MONDAY))
        var block = (weeksElapsed / interval - 1).coerceAtLeast(0)
        var guard = 0
        while (guard++ < 10_000) {
            val monday = blockStart.plusWeeks(block * interval)
            for (d in DayOfWeek.entries) {
                if (d !in days) continue
                val date = monday.with(d)
                if (date.isBefore(start.toLocalDate())) continue
                val cand = date.atTime(start.toLocalTime()).atZone(zone)
                if (cand.isAfter(afterZ)) {
                    return if (end == null || !date.isAfter(end)) cand else null
                }
            }
            block++
        }
        return null
    }

    /** All occurrences with [from] <= t < [to] (capped), used for the calendar view. */
    fun occurrencesBetween(r: Reminder, from: ZonedDateTime, to: ZonedDateTime, cap: Int = 400): List<ZonedDateTime> {
        val out = ArrayList<ZonedDateTime>()
        var cursor = from.toInstant().minusMillis(1)
        while (out.size < cap) {
            val n = nextOccurrenceAfter(r, cursor) ?: break
            if (!n.isBefore(to)) break
            out += n
            cursor = n.toInstant()
        }
        return out
    }

    fun describe(r: Reminder): String {
        if (!r.isScheduled) return "Random"
        val n = r.interval.coerceAtLeast(1)
        val unit = when (r.repeat) {
            Repeat.NONE -> return "Once"
            Repeat.DAILY -> "day"
            Repeat.WEEKLY -> "week"
            Repeat.MONTHLY -> "month"
            Repeat.YEARLY -> "year"
        }
        val base = if (n == 1) "Every $unit" else "Every $n ${unit}s"
        val days = r.weekdaySet()
        val dayPart = if (r.repeat == Repeat.WEEKLY && days.isNotEmpty())
            " on " + DayOfWeek.entries.filter { it in days }.joinToString(", ") { it.name.take(3).lowercase().replaceFirstChar(Char::uppercase) }
        else ""
        val until = r.endDate?.let { " until $it" } ?: ""
        return base + dayPart + until
    }
}
