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
    private val header = "title,details,date,time,repeat,every,weekdays,until,zone,follow_device_zone\n"

    private fun one(csv: String) = ImportExport.parseCsv(csv, now).also { assertEquals(it.errors.toString(), 0, it.errors.size) }.reminders.single()

    @Test fun titleOnlyRowIsRandom() {
        val r = one("Drink some water")
        assertEquals(Kind.RANDOM, r.kind)
        assertEquals("Drink some water", r.title)
    }

    @Test fun headerlessRowUsesDefaultOrder() {
        val r = one("Call mom,she is home,2026-09-10,14:30")
        assertEquals(Kind.SCHEDULED, r.kind)
        assertEquals("2026-09-10T14:30", r.localDateTime)
        assertEquals("she is home", r.body)
        assertEquals(Repeat.NONE, r.repeat)
    }

    @Test fun headerAllowsAnyColumnOrder() {
        val r = one("time,title,repeat\n09:00,Stretch,daily")
        assertEquals("Stretch", r.title)
        assertEquals(Repeat.DAILY, r.repeat)
        assertEquals("2026-09-04T09:00", r.localDateTime) // 09:00 already passed today
    }

    @Test fun timeOnlyTodayIfStillAhead() {
        assertEquals("2026-09-03T18:00", one(header + "Stretch,,,6:00pm,daily").localDateTime)
    }

    @Test fun weekdaysQuotedAndSemicolon() {
        val r = one(header + "Gym,\"bring a towel, and water\",,18:00,weekly,1,\"mon, wed, fri\"")
        assertEquals(Repeat.WEEKLY, r.repeat)
        assertEquals(setOf(DayOfWeek.MONDAY, DayOfWeek.WEDNESDAY, DayOfWeek.FRIDAY), r.weekdaySet())
        assertEquals("bring a towel, and water", r.body)
        assertEquals("2026-09-04T18:00", r.localDateTime) // Thursday noon -> Friday
        val s = one(header + "Standup,,2026-09-07,09:30,weekly,,mon;tue;wed;thu;fri,2026-12-19")
        assertEquals(5, s.weekdaySet().size); assertEquals("2026-12-19", s.endDate)
    }

    @Test fun shorthandRepeatAndZone() {
        val r = one(header + "Review,,2026-09-08,10:00,weekly,2,,2026-12-19,Europe/Berlin,no")
        assertEquals(2, r.interval); assertEquals("Europe/Berlin", r.zoneId); assertEquals(false, r.floating)
        val w = one(header + "Standup,,,09:30,weekdays")
        assertEquals(5, w.weekdaySet().size)
    }

    @Test fun semicolonDelimitedFile() {
        val res = ImportExport.parseCsv("title;date;time\nDentist;2026-11-03;14:15\n", now)
        assertEquals("2026-11-03T14:15", res.reminders.single().localDateTime)
    }

    @Test fun errorsAreReportedPerRow() {
        val res = ImportExport.parseCsv(header + "A\nB,,2026-13-45,10:00\nC,,,,daily\n# comment\n\n", now)
        assertEquals(listOf("A"), res.reminders.map { it.title })
        assertEquals(2, res.errors.size)
        assertTrue(res.errors[0].startsWith("Row 3"))
    }

    @Test fun csvRoundTrip() {
        val parsed = ImportExport.parse(header + "Call mom,\"quote \"\"hi\"\"\",2026-09-14,18:00,weekly,1,sun,,Europe/Berlin,no\nDrink water", now)
        assertEquals(2, parsed.reminders.size)
        val csv = ImportExport.toCsv(parsed.reminders)
        val again = ImportExport.parse(csv, now)
        assertEquals(0, again.errors.size)
        assertEquals(parsed.reminders.map { it.dedupeKey }, again.reminders.map { it.dedupeKey })
        assertEquals("quote \"hi\"", again.reminders[0].body)
        assertEquals(false, again.reminders[0].floating)
    }

    @Test fun jsonStillAccepted() {
        val parsed = ImportExport.parse("""[{"title":"Call mom","at":"2026-09-10T14:30","repeat":"weekly","weekdays":["sun"]},{"title":"Drink water"}]""", now)
        assertEquals(2, parsed.reminders.size)
        assertEquals(Repeat.WEEKLY, parsed.reminders[0].repeat)
    }

    @Test fun dedupeIgnoresCaseAndWhitespace() {
        val a = one("Drink   water ")
        val b = one("drink water")
        assertEquals(a.dedupeKey, b.dedupeKey)
        val c = one("drink water,,2026-09-10,14:30")
        assertNotEquals(a.dedupeKey, c.dedupeKey)
        assertTrue(Dedupe.keyFor(a).startsWith("drink water|"))
    }
}
