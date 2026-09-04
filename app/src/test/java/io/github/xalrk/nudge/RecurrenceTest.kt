package io.github.xalrk.nudge

import io.github.xalrk.nudge.data.Kind
import io.github.xalrk.nudge.data.Reminder
import io.github.xalrk.nudge.data.Repeat
import io.github.xalrk.nudge.domain.Recurrence
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Test
import java.time.DayOfWeek
import java.time.ZoneId
import java.time.ZonedDateTime

class RecurrenceTest {
    private val zone = ZoneId.of("America/Denver")

    private fun sched(at: String, repeat: Repeat = Repeat.NONE, interval: Int = 1, weekdays: Int = 0, end: String? = null) =
        Reminder(title = "t", kind = Kind.SCHEDULED, localDateTime = at, zoneId = zone.id, floating = false,
            repeat = repeat, interval = interval, weekdays = weekdays, endDate = end)

    private fun z(s: String) = ZonedDateTime.parse(s)

    @Test fun onceInFuture() {
        val r = sched("2026-09-10T14:30")
        assertEquals("2026-09-10T14:30-06:00[America/Denver]", Recurrence.nextOccurrenceAfter(r, z("2026-09-01T00:00-06:00[America/Denver]").toInstant()).toString())
    }

    @Test fun oncePastIsNull() {
        val r = sched("2026-09-10T14:30")
        assertNull(Recurrence.nextOccurrenceAfter(r, z("2026-09-10T14:30-06:00[America/Denver]").toInstant()))
    }

    @Test fun dailyJumpsAhead() {
        val r = sched("2026-01-01T09:00", Repeat.DAILY)
        val n = Recurrence.nextOccurrenceAfter(r, z("2026-09-03T10:00-06:00[America/Denver]").toInstant())!!
        assertEquals("2026-09-04T09:00-06:00[America/Denver]", n.toString())
    }

    @Test fun everyThreeDaysKeepsPhase() {
        val r = sched("2026-09-01T09:00", Repeat.DAILY, interval = 3)
        val n = Recurrence.nextOccurrenceAfter(r, z("2026-09-05T00:00-06:00[America/Denver]").toInstant())!!
        assertEquals("2026-09-07T09:00-06:00[America/Denver]", n.toString())
    }

    @Test fun dailyAcrossDstKeepsWallClock() {
        val r = sched("2026-10-30T09:00", Repeat.DAILY)
        val n = Recurrence.nextOccurrenceAfter(r, z("2026-11-01T12:00-07:00[America/Denver]").toInstant())!!
        assertEquals("2026-11-02T09:00-07:00[America/Denver]", n.toString())
    }

    @Test fun weeklyOnDays() {
        val mask = Reminder.maskOf(listOf(DayOfWeek.MONDAY, DayOfWeek.WEDNESDAY, DayOfWeek.FRIDAY))
        val r = sched("2026-09-01T18:00", Repeat.WEEKLY, weekdays = mask) // Tuesday
        var after = z("2026-09-01T00:00-06:00[America/Denver]").toInstant()
        val got = (1..4).map { val n = Recurrence.nextOccurrenceAfter(r, after)!!; after = n.toInstant(); n.toLocalDate().toString() }
        assertEquals(listOf("2026-09-02", "2026-09-04", "2026-09-07", "2026-09-09"), got)
    }

    @Test fun everyTwoWeeksSkipsAlternateWeeks() {
        val r = sched("2026-09-01T18:00", Repeat.WEEKLY, interval = 2)
        val n = Recurrence.nextOccurrenceAfter(r, z("2026-09-02T00:00-06:00[America/Denver]").toInstant())!!
        assertEquals("2026-09-15", n.toLocalDate().toString())
    }

    @Test fun monthlyClampsDay() {
        val r = sched("2026-01-31T08:00", Repeat.MONTHLY)
        val n = Recurrence.nextOccurrenceAfter(r, z("2026-02-01T00:00-07:00[America/Denver]").toInstant())!!
        assertEquals("2026-02-28", n.toLocalDate().toString())
    }

    @Test fun yearly() {
        val r = sched("2020-02-29T08:00", Repeat.YEARLY)
        val n = Recurrence.nextOccurrenceAfter(r, z("2026-09-03T00:00-06:00[America/Denver]").toInstant())!!
        assertEquals("2027-02-28", n.toLocalDate().toString())
    }

    @Test fun endDateStopsSeries() {
        val r = sched("2026-09-01T09:00", Repeat.DAILY, end = "2026-09-03")
        assertNull(Recurrence.nextOccurrenceAfter(r, z("2026-09-03T09:00-06:00[America/Denver]").toInstant()))
        assertEquals("2026-09-03", Recurrence.nextOccurrenceAfter(r, z("2026-09-02T09:00-06:00[America/Denver]").toInstant())!!.toLocalDate().toString())
    }

    @Test fun occurrencesInMonth() {
        val r = sched("2026-08-01T09:00", Repeat.WEEKLY)
        val list = Recurrence.occurrencesBetween(r, z("2026-09-01T00:00-06:00[America/Denver]"), z("2026-10-01T00:00-06:00[America/Denver]"))
        assertEquals(listOf("2026-09-05", "2026-09-12", "2026-09-19", "2026-09-26"), list.map { it.toLocalDate().toString() })
    }

    @Test fun pinnedZoneStaysFixedWhenDeviceZoneDiffers() {
        val r = sched("2026-09-10T09:00")
        val n = Recurrence.nextOccurrenceAfter(r, z("2026-09-01T00:00Z").toInstant())!!
        assertEquals("2026-09-10T15:00:00Z", n.toInstant().toString())
    }
}
