import XCTest
@testable import NudgeCore

final class RecurrenceTests: XCTestCase {
    private let zone = TimeZone(identifier: "America/Denver")!

    private func sched(_ at: String, _ repeatRule: Repeat = .none, interval: Int = 1, weekdays: Int = 0, end: String? = nil) -> Reminder {
        Reminder(title: "t", kind: .scheduled, localDateTime: at, zoneId: zone.identifier, floating: false,
                 repeatRule: repeatRule, interval: interval, weekdays: weekdays, endDate: end)
    }

    /// "2026-09-01T00:00" read in Denver.
    private func z(_ s: String, _ tz: TimeZone? = nil) -> ZonedDateTime { LocalDateTime(iso: s)!.atZone(tz ?? zone) }

    func testOnceInFuture() {
        let r = sched("2026-09-10T14:30")
        XCTAssertEqual(Recurrence.nextOccurrenceAfter(r, z("2026-09-01T00:00").instant)?.description, "2026-09-10T14:30-06:00[America/Denver]")
    }

    func testOncePastIsNil() {
        XCTAssertNil(Recurrence.nextOccurrenceAfter(sched("2026-09-10T14:30"), z("2026-09-10T14:30").instant))
    }

    func testDailyJumpsAhead() {
        let n = Recurrence.nextOccurrenceAfter(sched("2026-01-01T09:00", .daily), z("2026-09-03T10:00").instant)!
        XCTAssertEqual(n.description, "2026-09-04T09:00-06:00[America/Denver]")
    }

    func testEveryThreeDaysKeepsPhase() {
        let n = Recurrence.nextOccurrenceAfter(sched("2026-09-01T09:00", .daily, interval: 3), z("2026-09-05T00:00").instant)!
        XCTAssertEqual(n.description, "2026-09-07T09:00-06:00[America/Denver]")
    }

    func testDailyAcrossDstKeepsWallClock() {
        let n = Recurrence.nextOccurrenceAfter(sched("2026-10-30T09:00", .daily), z("2026-11-01T12:00").instant)!
        XCTAssertEqual(n.description, "2026-11-02T09:00-07:00[America/Denver]")
    }

    func testWeeklyOnDays() {
        let mask = Reminder.maskOf([DayOfWeek.monday, .wednesday, .friday])
        let r = sched("2026-09-01T18:00", .weekly, weekdays: mask) // Tuesday
        var after = z("2026-09-01T00:00").instant
        var got: [String] = []
        for _ in 1...4 { let n = Recurrence.nextOccurrenceAfter(r, after)!; after = n.instant; got.append(n.localDate.description) }
        XCTAssertEqual(got, ["2026-09-02", "2026-09-04", "2026-09-07", "2026-09-09"])
    }

    func testEveryTwoWeeksSkipsAlternateWeeks() {
        let n = Recurrence.nextOccurrenceAfter(sched("2026-09-01T18:00", .weekly, interval: 2), z("2026-09-02T00:00").instant)!
        XCTAssertEqual(n.localDate.description, "2026-09-15")
    }

    func testMonthlyClampsDay() {
        let n = Recurrence.nextOccurrenceAfter(sched("2026-01-31T08:00", .monthly), z("2026-02-01T00:00").instant)!
        XCTAssertEqual(n.localDate.description, "2026-02-28")
    }

    func testYearly() {
        let n = Recurrence.nextOccurrenceAfter(sched("2020-02-29T08:00", .yearly), z("2026-09-03T00:00").instant)!
        XCTAssertEqual(n.localDate.description, "2027-02-28")
    }

    func testEndDateStopsSeries() {
        let r = sched("2026-09-01T09:00", .daily, end: "2026-09-03")
        XCTAssertNil(Recurrence.nextOccurrenceAfter(r, z("2026-09-03T09:00").instant))
        XCTAssertEqual(Recurrence.nextOccurrenceAfter(r, z("2026-09-02T09:00").instant)!.localDate.description, "2026-09-03")
    }

    func testOccurrencesInMonth() {
        let list = Recurrence.occurrencesBetween(sched("2026-08-01T09:00", .weekly), z("2026-09-01T00:00"), z("2026-10-01T00:00"))
        XCTAssertEqual(list.map { $0.localDate.description }, ["2026-09-05", "2026-09-12", "2026-09-19", "2026-09-26"])
    }

    func testPinnedZoneStaysFixedWhenDeviceZoneDiffers() {
        let n = Recurrence.nextOccurrenceAfter(sched("2026-09-10T09:00"), z("2026-09-01T00:00", TimeZone(identifier: "UTC")!).instant)!
        XCTAssertEqual(n.instant, LocalDateTime(iso: "2026-09-10T15:00")!.atZone(TimeZone(identifier: "UTC")!).instant)
    }

    func testExcludedDatesAreSkipped() {
        let r = Reminder(title: "t", kind: .scheduled, localDateTime: "2026-09-01T09:00", zoneId: zone.identifier, floating: false, repeatRule: .daily)
            .withExcluded(LocalDate(iso: "2026-09-02")!).withExcluded(LocalDate(iso: "2026-09-03")!)
        XCTAssertEqual(Recurrence.nextOccurrenceAfter(r, z("2026-09-01T09:00").instant)!.localDate.description, "2026-09-04")
        let month = Recurrence.occurrencesBetween(r, z("2026-09-01T00:00"), z("2026-09-06T00:00"))
        XCTAssertEqual(month.map { $0.localDate.description }, ["2026-09-01", "2026-09-04", "2026-09-05"])
        XCTAssertEqual(r.excludedDates, "2026-09-02,2026-09-03")
    }

    func testDescribe() {
        XCTAssertEqual(Recurrence.describe(sched("2026-09-01T18:00", .weekly, weekdays: Reminder.maskOf([DayOfWeek.monday, .friday]), end: "2026-12-19")), "Every week on Mon, Fri until 2026-12-19")
        XCTAssertEqual(Recurrence.describe(sched("2026-09-01T18:00", .daily, interval: 3)), "Every 3 days")
        XCTAssertEqual(Recurrence.describe(sched("2026-09-01T18:00")), "Once")
    }
}
