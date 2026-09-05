import Foundation

/// Pure recurrence arithmetic on wall-clock times. Every calculation is done in the
/// reminder's effective zone (device zone for floating reminders), so a 9:00 reminder
/// stays at 9:00 on the wall clock after a time-zone change or a DST switch.
public enum Recurrence {

    /// First occurrence strictly after `after` that is not excluded, or nil when the series is over.
    public static func nextOccurrenceAfter(_ r: Reminder, _ after: Date) -> ZonedDateTime? {
        let excluded = r.excludedDateSet()
        var cursor = after
        var guardCount = 0
        while guardCount < 2000 {
            guardCount += 1
            guard let c = nextRawOccurrenceAfter(r, cursor) else { return nil }
            if !excluded.contains(c.localDate) { return c }
            cursor = c.instant
        }
        return nil
    }

    private static func nextRawOccurrenceAfter(_ r: Reminder, _ after: Date) -> ZonedDateTime? {
        guard let start = r.localDateTimeOrNil() else { return nil }
        let zone = r.effectiveZone()
        let startZ = start.atZone(zone)
        let afterZ = after.atZone(zone)
        let end = r.endDateOrNil()
        let interval = max(r.interval, 1)

        func ok(_ z: ZonedDateTime?) -> ZonedDateTime? {
            guard let z = z else { return nil }
            if let end = end, z.localDate.isAfter(end) { return nil }
            return z
        }

        if r.repeatRule == .none { return startZ.isAfter(afterZ) ? startZ : nil }
        // The start moment itself is the first occurrence, unless a weekly rule excludes its weekday.
        let startCounts = r.repeatRule != .weekly || r.weekdays == 0 || r.weekdaySet().contains(start.dayOfWeek)
        if startZ.isAfter(afterZ) && startCounts { return ok(startZ) }

        switch r.repeatRule {
        case .none: return nil
        case .daily:
            let days = LocalDate.daysBetween(startZ.localDate, afterZ.localDate)
            var k = days / interval
            var cand = start.plusDays(k * interval).atZone(zone)
            while !cand.isAfter(afterZ) { k += 1; cand = start.plusDays(k * interval).atZone(zone) }
            return ok(cand)
        case .weekly:
            return nextWeekly(start, zone, afterZ, interval, r.weekdaySet(), end)
        case .monthly:
            let months = LocalDate.monthsBetween(startZ.localDate.withDayOfMonth(1), afterZ.localDate.withDayOfMonth(1))
            var k = max(months / interval - 1, 0)
            var cand = start.plusMonths(k * interval).atZone(zone)
            while !cand.isAfter(afterZ) { k += 1; cand = start.plusMonths(k * interval).atZone(zone) }
            return ok(cand)
        case .yearly:
            let years = LocalDate.yearsBetween(startZ.localDate, afterZ.localDate)
            var k = max(years / interval - 1, 0)
            var cand = start.plusYears(k * interval).atZone(zone)
            while !cand.isAfter(afterZ) { k += 1; cand = start.plusYears(k * interval).atZone(zone) }
            return ok(cand)
        }
    }

    private static func nextWeekly(_ start: LocalDateTime, _ zone: TimeZone, _ afterZ: ZonedDateTime, _ interval: Int,
                                   _ weekdays: Set<DayOfWeek>, _ end: LocalDate?) -> ZonedDateTime? {
        let days = weekdays.isEmpty ? Set([start.dayOfWeek]) : weekdays
        // Week blocks start on the Monday of the start date's week.
        let blockStart = start.date.with(.monday)
        let weeksElapsed = LocalDate.daysBetween(blockStart, afterZ.localDate.with(.monday)) / 7
        var block = max(weeksElapsed / interval - 1, 0)
        var guardCount = 0
        while guardCount < 10_000 {
            guardCount += 1
            let monday = blockStart.plusWeeks(block * interval)
            for d in DayOfWeek.allCases where days.contains(d) {
                let date = monday.with(d)
                if date.isBefore(start.date) { continue }
                let cand = date.atTime(start.time).atZone(zone)
                if cand.isAfter(afterZ) {
                    if let end = end, date.isAfter(end) { return nil }
                    return cand
                }
            }
            block += 1
        }
        return nil
    }

    /// All occurrences with from <= t < to (capped), used for the calendar view.
    public static func occurrencesBetween(_ r: Reminder, _ from: ZonedDateTime, _ to: ZonedDateTime, cap: Int = 400) -> [ZonedDateTime] {
        var out: [ZonedDateTime] = []
        var cursor = from.instant.addingTimeInterval(-0.001)
        while out.count < cap {
            guard let n = nextOccurrenceAfter(r, cursor) else { break }
            if !n.isBefore(to) { break }
            out.append(n)
            cursor = n.instant
        }
        return out
    }

    public static func describe(_ r: Reminder) -> String {
        if !r.isScheduled { return "Random" }
        let n = max(r.interval, 1)
        let unit: String
        switch r.repeatRule {
        case .none: return "Once"
        case .daily: unit = "day"
        case .weekly: unit = "week"
        case .monthly: unit = "month"
        case .yearly: unit = "year"
        }
        let base = n == 1 ? "Every \(unit)" : "Every \(n) \(unit)s"
        let days = r.weekdaySet()
        let dayPart = (r.repeatRule == .weekly && !days.isEmpty)
            ? " on " + DayOfWeek.allCases.filter { days.contains($0) }.map { $0.short }.joined(separator: ", ")
            : ""
        let until = r.endDate.map { " until \($0)" } ?? ""
        return base + dayPart + until
    }
}
