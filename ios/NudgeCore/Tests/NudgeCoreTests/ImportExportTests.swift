import XCTest
@testable import NudgeCore

final class ImportExportTests: XCTestCase {
    private let now = LocalDateTime(iso: "2026-09-03T12:00")!
    private let zone = TimeZone(identifier: "America/Denver")!
    private let header = "title,details,date,time,repeat,every,weekdays,until,zone,follow_device_zone\n"

    private func one(_ csv: String) -> Reminder {
        let res = ImportExport.parseCsv(csv, now: now, zone: zone)
        XCTAssertEqual(res.errors.count, 0, res.errors.description)
        XCTAssertEqual(res.reminders.count, 1)
        return res.reminders[0]
    }

    func testTitleOnlyRowIsRandom() {
        let r = one("Drink some water")
        XCTAssertEqual(r.kind, .random)
        XCTAssertEqual(r.title, "Drink some water")
    }

    func testHeaderlessRowUsesDefaultOrder() {
        let r = one("Call mom,she is home,2026-09-10,14:30")
        XCTAssertEqual(r.kind, .scheduled)
        XCTAssertEqual(r.localDateTime, "2026-09-10T14:30")
        XCTAssertEqual(r.body, "she is home")
        XCTAssertEqual(r.repeatRule, .none)
    }

    func testHeaderAllowsAnyColumnOrder() {
        let r = one("time,title,repeat\n09:00,Stretch,daily")
        XCTAssertEqual(r.title, "Stretch")
        XCTAssertEqual(r.repeatRule, .daily)
        XCTAssertEqual(r.localDateTime, "2026-09-04T09:00") // 09:00 already passed today
    }

    func testTimeOnlyTodayIfStillAhead() {
        XCTAssertEqual(one(header + "Stretch,,,6:00pm,daily").localDateTime, "2026-09-03T18:00")
    }

    func testWeekdaysQuotedAndSemicolon() {
        let r = one(header + "Gym,\"bring a towel, and water\",,18:00,weekly,1,\"mon, wed, fri\"")
        XCTAssertEqual(r.repeatRule, .weekly)
        XCTAssertEqual(r.weekdaySet(), Set([DayOfWeek.monday, .wednesday, .friday]))
        XCTAssertEqual(r.body, "bring a towel, and water")
        XCTAssertEqual(r.localDateTime, "2026-09-04T18:00") // Thursday noon -> Friday
        let s = one(header + "Standup,,2026-09-07,09:30,weekly,,mon;tue;wed;thu;fri,2026-12-19")
        XCTAssertEqual(s.weekdaySet().count, 5); XCTAssertEqual(s.endDate, "2026-12-19")
    }

    func testShorthandRepeatAndZone() {
        let r = one(header + "Review,,2026-09-08,10:00,weekly,2,,2026-12-19,Europe/Berlin,no")
        XCTAssertEqual(r.interval, 2); XCTAssertEqual(r.zoneId, "Europe/Berlin"); XCTAssertEqual(r.floating, false)
        let w = one(header + "Standup,,,09:30,weekdays")
        XCTAssertEqual(w.weekdaySet().count, 5)
    }

    func testSemicolonDelimitedFile() {
        let res = ImportExport.parseCsv("title;date;time\nDentist;2026-11-03;14:15\n", now: now, zone: zone)
        XCTAssertEqual(res.reminders.first?.localDateTime, "2026-11-03T14:15")
    }

    func testOtherDateAndTimeFormats() {
        XCTAssertEqual(one(header + "A,,3/4/2026,2:15pm").localDateTime, "2026-04-03T14:15")
        XCTAssertEqual(one(header + "A,,2026/10/20,12am").localDateTime, "2026-10-20T00:00")
        XCTAssertEqual(one(header + "A,,Nov 3 2026,7").localDateTime, "2026-11-03T07:00")
        XCTAssertEqual(one(header + "A,,3 nov 2026,12:30 PM").localDateTime, "2026-11-03T12:30")
        XCTAssertEqual(one(header + "A,,13.4.2026,9:05").localDateTime, "2026-04-13T09:05")
    }

    func testErrorsAreReportedPerRow() {
        let res = ImportExport.parseCsv(header + "A\nB,,2026-13-45,10:00\nC,,,,daily\n# comment\n\n", now: now, zone: zone)
        XCTAssertEqual(res.reminders.map { $0.title }, ["A"])
        XCTAssertEqual(res.errors.count, 2)
        XCTAssertTrue(res.errors[0].hasPrefix("Row 3"), res.errors[0])
    }

    func testCsvRoundTrip() {
        let parsed = ImportExport.parse(header + "Call mom,\"quote \"\"hi\"\"\",2026-09-14,18:00,weekly,1,sun,,Europe/Berlin,no\nDrink water", now: now, zone: zone)
        XCTAssertEqual(parsed.reminders.count, 2)
        let csv = ImportExport.toCsv(parsed.reminders)
        let again = ImportExport.parse(csv, now: now, zone: zone)
        XCTAssertEqual(again.errors.count, 0)
        XCTAssertEqual(parsed.reminders.map { $0.dedupeKey }, again.reminders.map { $0.dedupeKey })
        XCTAssertEqual(again.reminders[0].body, "quote \"hi\"")
        XCTAssertEqual(again.reminders[0].floating, false)
        XCTAssertTrue(csv.hasPrefix("title,details,date,time,repeat,every,weekdays,until,zone,follow_device_zone\r\n"))
    }

    func testJsonStillAccepted() {
        let parsed = ImportExport.parse("[{\"title\":\"Call mom\",\"at\":\"2026-09-10T14:30\",\"repeat\":\"weekly\",\"weekdays\":[\"sun\"]},{\"title\":\"Drink water\"}]", now: now, zone: zone)
        XCTAssertEqual(parsed.errors, [])
        XCTAssertEqual(parsed.reminders.count, 2)
        XCTAssertEqual(parsed.reminders[0].repeatRule, .weekly)
        XCTAssertEqual(parsed.reminders[0].weekdaySet(), [DayOfWeek.sunday])
        XCTAssertEqual(parsed.reminders[1].kind, .random)
        let bad = ImportExport.parse("{\"reminders\": [{\"at\": \"2026-01-01T09:00\"}]}", now: now, zone: zone)
        XCTAssertEqual(bad.reminders.count, 0)
        XCTAssertEqual(bad.errors.count, 1)
    }

    func testDedupeIgnoresCaseAndWhitespace() {
        let a = one("Drink   water ")
        let b = one("drink water")
        XCTAssertEqual(a.dedupeKey, b.dedupeKey)
        let c = one("drink water,,2026-09-10,14:30")
        XCTAssertNotEqual(a.dedupeKey, c.dedupeKey)
        XCTAssertTrue(Dedupe.keyFor(a).hasPrefix("drink water|"))
    }

    func testSampleFileFromRepo() throws {
        let url = URL(fileURLWithPath: #filePath).deletingLastPathComponent().deletingLastPathComponent().deletingLastPathComponent()
            .deletingLastPathComponent().deletingLastPathComponent().appendingPathComponent("samples/reminders.csv")
        guard let text = try? String(contentsOf: url, encoding: .utf8) else { throw XCTSkip("samples/reminders.csv not found") }
        let res = ImportExport.parse(text, now: now, zone: zone)
        XCTAssertEqual(res.errors, [])
        XCTAssertFalse(res.reminders.isEmpty)
    }
}
