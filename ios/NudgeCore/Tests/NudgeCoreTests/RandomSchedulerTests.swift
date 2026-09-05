import XCTest
@testable import NudgeCore

final class RandomSchedulerTests: XCTestCase {
    private let zone = TimeZone(identifier: "America/Denver")!
    private func z(_ s: String) -> ZonedDateTime { LocalDateTime(iso: s)!.atZone(zone) }

    func testAdvanceSkipsNight() {
        let got = RandomScheduler.advanceByActiveMillis(z("2026-09-03T22:00"), 2 * 3_600_000, 7, 23)
        XCTAssertEqual(got.description, "2026-09-04T08:00-06:00[America/Denver]")
    }

    func testAdvanceFromBeforeWindowStartsAtWindow() {
        let got = RandomScheduler.advanceByActiveMillis(z("2026-09-03T03:00"), 30 * 60_000, 7, 23)
        XCTAssertEqual(got.description, "2026-09-03T07:30-06:00[America/Denver]")
    }

    func testSamplesAlwaysInsideWindowAndAverageMatches() {
        let start = z("2026-09-03T12:00")
        var rng = SeededGenerator(seed: 42)
        let mean: Int64 = 14 * 24 * 3_600_000
        let n = 4000
        var total: Double = 0
        for _ in 0..<n {
            let t = RandomScheduler.sampleNext(from: start, meanIntervalMillis: mean, startHour: 7, endHour: 23, using: &rng)
            XCTAssertTrue(RandomScheduler.isInsideActiveWindow(t, 7, 23), "\(t) outside window")
            total += t.instant.timeIntervalSince(start.instant)
        }
        let avgDays = total / Double(n) / 86_400
        XCTAssertTrue(avgDays > 12.5 && avgDays < 15.5, "average was \(avgDays) days")
    }

    func testSamplesAreNotClustered() {
        let start = z("2026-09-03T12:00")
        var rng = SeededGenerator(seed: 7)
        var hours = Set<Int>()
        for _ in 0..<2000 { hours.insert(RandomScheduler.sampleNext(from: start, meanIntervalMillis: 3 * 86_400_000, startHour: 7, endHour: 23, using: &rng).hour) }
        XCTAssertEqual(hours, Set(7...22))
    }

    func testOffDaysAreSkipped() {
        let weekdays = Set(DayOfWeek.allCases.filter { $0.rawValue <= 5 })
        // Friday 22:30 with 1 h of active time left -> skips Sat/Sun, lands Monday.
        let from = z("2026-09-04T22:30")
        XCTAssertEqual(RandomScheduler.advanceByActiveMillis(from, 2 * 3_600_000, 7, 23, weekdays).description, "2026-09-07T08:30-06:00[America/Denver]")
        var rng = SeededGenerator(seed: 3)
        for _ in 0..<500 {
            let t = RandomScheduler.sampleNext(from: from, meanIntervalMillis: 3 * 86_400_000, startHour: 7, endHour: 23, using: &rng, days: weekdays)
            XCTAssertTrue(weekdays.contains(t.dayOfWeek), "\(t) landed on a weekend")
        }
        XCTAssertFalse(RandomScheduler.isInsideActiveWindow(z("2026-09-05T12:00"), 7, 23, days: weekdays))
    }

    func testMidnightEndHour() {
        let got = RandomScheduler.advanceByActiveMillis(z("2026-09-03T23:30"), 60 * 60_000, 6, 24)
        XCTAssertEqual(got.description, "2026-09-04T06:30-06:00[America/Denver]")
    }
}
