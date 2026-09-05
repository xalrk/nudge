import XCTest
@testable import NudgeCore

final class DateTests: XCTestCase {
    func testCivilRoundTrip() {
        for day in stride(from: -200_000, through: 200_000, by: 997) {
            let d = LocalDate(epochDay: day)
            XCTAssertEqual(d.epochDay, day, "\(d)")
        }
        XCTAssertEqual(LocalDate(1970, 1, 1).epochDay, 0)
        XCTAssertEqual(LocalDate(1970, 1, 1).dayOfWeek, .thursday)
        XCTAssertEqual(LocalDate(2026, 9, 3).dayOfWeek, .thursday)
        XCTAssertEqual(LocalDate(2026, 9, 6).dayOfWeek, .sunday)
    }

    func testMonthArithmeticClamps() {
        XCTAssertEqual(LocalDate(2026, 1, 31).plusMonths(1), LocalDate(2026, 2, 28))
        XCTAssertEqual(LocalDate(2024, 1, 31).plusMonths(1), LocalDate(2024, 2, 29))
        XCTAssertEqual(LocalDate(2026, 3, 31).plusMonths(-1), LocalDate(2026, 2, 28))
        XCTAssertEqual(LocalDate(2020, 2, 29).plusYears(1), LocalDate(2021, 2, 28))
        XCTAssertEqual(LocalDate(2026, 12, 15).plusMonths(1), LocalDate(2027, 1, 15))
        XCTAssertEqual(LocalDate.monthsBetween(LocalDate(2026, 1, 1), LocalDate(2026, 9, 1)), 8)
        XCTAssertEqual(LocalDate.monthsBetween(LocalDate(2026, 1, 31), LocalDate(2026, 2, 28)), 0)
        XCTAssertEqual(LocalDate.yearsBetween(LocalDate(2020, 2, 29), LocalDate(2026, 9, 3)), 6)
        XCTAssertEqual(LocalDate.yearsBetween(LocalDate(2020, 9, 4), LocalDate(2026, 9, 3)), 5)
    }

    func testWithWeekday() {
        let thu = LocalDate(2026, 9, 3)
        XCTAssertEqual(thu.with(.monday), LocalDate(2026, 8, 31))
        XCTAssertEqual(thu.with(.sunday), LocalDate(2026, 9, 6))
    }

    func testParsing() {
        XCTAssertEqual(LocalDate(iso: "2026-09-10"), LocalDate(2026, 9, 10))
        XCTAssertNil(LocalDate(iso: "2026-13-45"))
        XCTAssertNil(LocalDate(iso: "2026-9-1"))
        XCTAssertEqual(LocalDateTime(iso: "2026-09-10T14:30")?.description, "2026-09-10T14:30")
        XCTAssertEqual(LocalDateTime(iso: "2026-09-10T14:30:00")?.time, LocalTime(14, 30))
        XCTAssertEqual(LocalDateTime(iso: "2026-09-10T23:30")!.plusMinutes(45).description, "2026-09-11T00:15")
    }

    func testZoneResolution() {
        let denver = TimeZone(identifier: "America/Denver")!
        let z = LocalDateTime(iso: "2026-09-10T14:30")!.atZone(denver)
        XCTAssertEqual(z.description, "2026-09-10T14:30-06:00[America/Denver]")
        XCTAssertEqual(z.instant.epochMillis, 1789072200000)
        // Spring forward 2026-03-08 02:00 -> 03:00 in Denver: 02:30 does not exist and becomes 03:30.
        let gap = LocalDateTime(iso: "2026-03-08T02:30")!.atZone(denver)
        XCTAssertEqual(gap.description, "2026-03-08T03:30-06:00[America/Denver]")
        // Fall back 2026-11-01 01:30 exists twice; the earlier (daylight) offset wins.
        let overlap = LocalDateTime(iso: "2026-11-01T01:30")!.atZone(denver)
        XCTAssertEqual(overlap.description, "2026-11-01T01:30-06:00[America/Denver]")
        XCTAssertEqual(LocalDate(2026, 11, 1).atStartOfDay(denver).plusHours(7).description, "2026-11-01T06:00-07:00[America/Denver]")
    }
}
