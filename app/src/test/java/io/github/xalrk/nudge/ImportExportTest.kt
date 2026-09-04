package io.github.xalrk.nudge

import io.github.xalrk.nudge.data.Dedupe
import io.github.xalrk.nudge.data.Kind
import io.github.xalrk.nudge.data.Repeat
import io.github.xalrk.nudge.domain.ImportExport
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNotEquals
import org.junit.Assert.assertTrue
import org.junit.Test
import java.time.DayOfWeek
import java.time.LocalDateTime

class ImportExportTest {
    private val now = LocalDateTime.parse("2026-09-03T12:00")

    @Test fun plainLineIsRandom() {
        val r = ImportExport.parseLine("Drink some water", now)
        assertEquals(Kind.RANDOM, r.kind)
        assertEquals("Drink some water", r.title)
    }

    @Test fun dateTimeOnce() {
        val r = ImportExport.parseLine("Call mom @ 2026-09-10 14:30", now)
        assertEquals(Kind.SCHEDULED, r.kind)
        assertEquals("2026-09-10T14:30", r.localDateTime)
        assertEquals(Repeat.NONE, r.repeat)
    }

    @Test fun timeOnlyDailyUsesTodayOrTomorrow() {
        assertEquals("2026-09-04T09:00", ImportExport.parseLine("Stretch @ 09:00 every day", now).localDateTime)
        assertEquals("2026-09-03T18:00", ImportExport.parseLine("Stretch @ 6:00pm every day", now).localDateTime)
    }

    @Test fun weekdaysAndBody() {
        val r = ImportExport.parseLine("Gym @ 18:00 every mon,wed,fri :: bring towel", now)
        assertEquals(Repeat.WEEKLY, r.repeat)
        assertEquals(setOf(DayOfWeek.MONDAY, DayOfWeek.WEDNESDAY, DayOfWeek.FRIDAY), r.weekdaySet())
        assertEquals("bring towel", r.body)
        assertEquals("Gym", r.title)
        assertEquals("2026-09-04T18:00", r.localDateTime) // Thursday noon -> Friday
    }

    @Test fun everyWeekdayAndUntil() {
        val r = ImportExport.parseLine("Review @ 2026-09-08 10:00 every 2 weeks until 2026-12-19", now)
        assertEquals(2, r.interval); assertEquals(Repeat.WEEKLY, r.repeat)
        assertEquals("2026-09-08T10:00", r.localDateTime); assertEquals("2026-12-19", r.endDate)
        val w = ImportExport.parseLine("Standup @ 09:30 every weekday", now)
        assertEquals(5, w.weekdaySet().size)
    }

    @Test fun commentsAndErrors() {
        val res = ImportExport.parseText("# header\n\nA\nB @ nonsense every fortnight\nC every day\n", now)
        assertEquals(listOf("A"), res.reminders.map { it.title })
        assertEquals(2, res.errors.size)
    }

    @Test fun jsonRoundTrip() {
        val parsed = ImportExport.parseJson("""[{"title":"Call mom","at":"2026-09-10T14:30","repeat":"weekly","weekdays":["sun"],"zone":"Europe/Berlin","floating":false},{"title":"Drink water"}]""")
        assertEquals(2, parsed.reminders.size)
        val json = ImportExport.toJson(parsed.reminders)
        val again = ImportExport.parseJson(json)
        assertEquals(parsed.reminders.map { it.dedupeKey }, again.reminders.map { it.dedupeKey })
        assertEquals("Europe/Berlin", again.reminders[0].zoneId)
        assertEquals(false, again.reminders[0].floating)
    }

    @Test fun dedupeIgnoresCaseAndWhitespace() {
        val a = ImportExport.parseLine("Drink   water ", now)
        val b = ImportExport.parseLine("drink water", now)
        assertEquals(a.dedupeKey, b.dedupeKey)
        val c = ImportExport.parseLine("drink water @ 2026-09-10 14:30", now)
        assertNotEquals(a.dedupeKey, c.dedupeKey)
        assertTrue(Dedupe.keyFor(a).startsWith("drink water|"))
    }
}
