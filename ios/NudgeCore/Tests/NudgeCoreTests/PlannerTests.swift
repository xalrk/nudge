import XCTest
@testable import NudgeCore

final class PlannerTests: XCTestCase {
    private let zone = TimeZone(identifier: "America/Denver")!
    private func z(_ s: String) -> ZonedDateTime { LocalDateTime(iso: s)!.atZone(zone) }

    func testScheduledOccurrencesAreExactAndCapped() {
        let daily = Reminder(id: 1, title: "Stretch", kind: .scheduled, localDateTime: "2026-09-01T09:00", zoneId: zone.identifier, floating: false, repeatRule: .daily)
        let once = Reminder(id: 2, title: "Dentist", kind: .scheduled, localDateTime: "2026-09-05T14:15", zoneId: zone.identifier, floating: false)
        var rng = SeededGenerator(seed: 1)
        let plan = Planner.plan(reminders: [daily, once], settings: SettingsSnapshot(), now: z("2026-09-03T12:00").instant, limit: 10, zone: zone, using: &rng)
        XCTAssertEqual(plan.count, 10)
        XCTAssertEqual(plan[0].at, z("2026-09-04T09:00").epochMillis)
        XCTAssertEqual(plan[1].at, z("2026-09-05T09:00").epochMillis)
        XCTAssertEqual(plan[2].reminderId, 2)
        XCTAssertEqual(plan[2].at, z("2026-09-05T14:15").epochMillis)
        XCTAssertTrue(plan.dropFirst(3).allSatisfy { $0.reminderId == 1 })
        XCTAssertEqual(Set(plan.map { $0.identifier }).count, 10)
    }

    func testRandomChainStaysInsideWindowAndUsesStoredNext() {
        let next = z("2026-09-03T15:00").epochMillis
        let r = Reminder(id: 3, title: "Water", kind: .random, nextAt: next, meanOverrideMillis: SettingsMath.hourMillis * 3)
        var rng = SeededGenerator(seed: 9)
        let plan = Planner.plan(reminders: [r], settings: SettingsSnapshot(), now: z("2026-09-03T12:00").instant, limit: 20, zone: zone, using: &rng)
        XCTAssertEqual(plan.count, 20)
        XCTAssertEqual(plan[0].at, next)
        for p in plan {
            let t = Date(epochMillis: p.at).atZone(zone)
            XCTAssertTrue(RandomScheduler.isInsideActiveWindow(t, 7, 23), "\(t)")
        }
        XCTAssertEqual(plan.map { $0.at }, plan.map { $0.at }.sorted())
    }

    func testPauseSkipsEverythingBeforeItEnds() {
        let daily = Reminder(id: 1, title: "Stretch", kind: .scheduled, localDateTime: "2026-09-01T09:00", zoneId: zone.identifier, floating: false, repeatRule: .daily, snoozeAt: z("2026-09-03T13:00").epochMillis)
        var s = SettingsSnapshot()
        s.pausedUntil = z("2026-09-10T07:00").epochMillis
        var rng = SeededGenerator(seed: 1)
        let plan = Planner.plan(reminders: [daily], settings: s, now: z("2026-09-03T12:00").instant, limit: 3, zone: zone, using: &rng)
        XCTAssertEqual(plan.map { $0.at }, [z("2026-09-10T09:00").epochMillis, z("2026-09-11T09:00").epochMillis, z("2026-09-12T09:00").epochMillis])
        XCTAssertFalse(plan.contains { $0.isSnooze })
    }

    func testSnoozeAndDisabledReminders() {
        let off = Reminder(id: 1, title: "Off", kind: .scheduled, localDateTime: "2026-09-01T09:00", zoneId: zone.identifier, floating: false, repeatRule: .daily, enabled: false)
        let snoozed = Reminder(id: 2, title: "Later", kind: .random, nextAt: z("2026-12-01T10:00").epochMillis, snoozeAt: z("2026-09-03T12:10").epochMillis, meanOverrideMillis: SettingsMath.maxMeanMillis)
        var rng = SeededGenerator(seed: 1)
        let plan = Planner.plan(reminders: [off, snoozed], settings: SettingsSnapshot(), now: z("2026-09-03T12:00").instant, limit: 5, zone: zone, using: &rng)
        XCTAssertEqual(plan.first?.isSnooze, true)
        XCTAssertEqual(plan.first?.identifier, "nudge-2-\(z("2026-09-03T12:10").epochMillis)-snooze")
        XCTAssertFalse(plan.contains { $0.reminderId == 1 })
    }
}
