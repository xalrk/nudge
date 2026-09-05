import Foundation

/// Random reminders follow a Poisson process that only "ticks" during active hours.
/// The gap to the next firing is drawn from an exponential distribution, so timing is
/// memoryless and genuinely unpredictable, while the long-run average matches the
/// configured mean interval. The mean is scaled by the active fraction of the day so
/// that "once every 2 weeks" still means 2 weeks of wall-clock time even though nights
/// are skipped.
public enum RandomScheduler {
    private static let minGapMillis: Int64 = 60_000

    /// - Parameter meanIntervalMillis: average wall-clock time between firings of this reminder.
    public static func sampleNext<G: RandomNumberGenerator>(
        from: ZonedDateTime, meanIntervalMillis: Int64, startHour: Int, endHour: Int,
        using rng: inout G, days: Set<DayOfWeek> = Set(DayOfWeek.allCases)
    ) -> ZonedDateTime {
        let activeHours = min(max(endHour - startHour, 1), 24)
        let activeDays = min(max(days.count, 1), 7)
        // Scale the mean by the share of the week that is "active" so the wall-clock average holds.
        let activeFraction = Double(activeHours) / 24.0 * Double(activeDays) / 7.0
        let meanActive = Double(meanIntervalMillis) * activeFraction
        let u = min(max(Double.random(in: 0..<1, using: &rng), 1e-12), 1.0)
        let gap = max(Int64(-log(u) * meanActive), minGapMillis)
        return advanceByActiveMillis(from, gap, startHour, endHour, days)
    }

    public static func sampleNext(
        from: ZonedDateTime, meanIntervalMillis: Int64, startHour: Int, endHour: Int, days: Set<DayOfWeek> = Set(DayOfWeek.allCases)
    ) -> ZonedDateTime {
        var rng = SystemRandomNumberGenerator()
        return sampleNext(from: from, meanIntervalMillis: meanIntervalMillis, startHour: startHour, endHour: endHour, using: &rng, days: days)
    }

    public static func sampleNext<G: RandomNumberGenerator>(
        from: ZonedDateTime, settings: SettingsSnapshot, poolSize: Int, using rng: inout G,
        overrideMean: Int64? = nil, overrideDays: Set<DayOfWeek>? = nil
    ) -> ZonedDateTime {
        let mean = overrideMean ?? effectiveMeanPerReminder(settings, poolSize)
        return sampleNext(from: from, meanIntervalMillis: mean, startHour: settings.activeStartHour, endHour: settings.activeEndHour,
                          using: &rng, days: overrideDays ?? settings.activeDaySet())
    }

    public static func sampleNext(from: ZonedDateTime, settings: SettingsSnapshot, poolSize: Int,
                                  overrideMean: Int64? = nil, overrideDays: Set<DayOfWeek>? = nil) -> ZonedDateTime {
        var rng = SystemRandomNumberGenerator()
        return sampleNext(from: from, settings: settings, poolSize: poolSize, using: &rng, overrideMean: overrideMean, overrideDays: overrideDays)
    }

    /// Mean interval for one reminder given the frequency mode and how many random reminders are enabled.
    public static func effectiveMeanPerReminder(_ settings: SettingsSnapshot, _ poolSize: Int) -> Int64 {
        switch settings.frequencyMode {
        case .perReminder: return settings.meanIntervalMillis
        case .wholePool: return settings.meanIntervalMillis * Int64(max(poolSize, 1))
        }
    }

    public static func isInsideActiveWindow(_ t: ZonedDateTime, _ startHour: Int, _ endHour: Int, days: Set<DayOfWeek> = Set(DayOfWeek.allCases)) -> Bool {
        let minutes = t.hour * 60 + t.minute
        return days.contains(t.dayOfWeek) && minutes >= startHour * 60 && minutes < endHour * 60
    }

    /// Move `from` forward by `millis` of *active* time, skipping everything outside the window and off days.
    public static func advanceByActiveMillis(_ from: ZonedDateTime, _ millis: Int64, _ startHour: Int, _ endHour: Int,
                                             _ days: Set<DayOfWeek> = Set(DayOfWeek.allCases)) -> ZonedDateTime {
        var cursor = from
        var remaining = millis
        var guardCount = 0
        let allowed = days.isEmpty ? Set(DayOfWeek.allCases) : days
        while guardCount < 200_000 {
            guardCount += 1
            let date = cursor.localDate
            if !allowed.contains(date.dayOfWeek) {
                cursor = date.plusDays(1).atStartOfDay(cursor.zone).plusHours(startHour)
                continue
            }
            let dayStart = date.atStartOfDay(cursor.zone).plusHours(startHour)
            let dayEnd = endHour >= 24 ? date.plusDays(1).atStartOfDay(cursor.zone)
                                       : date.atStartOfDay(cursor.zone).plusHours(endHour)
            if cursor.isBefore(dayStart) { cursor = dayStart }
            if !cursor.isBefore(dayEnd) {
                cursor = date.plusDays(1).atStartOfDay(cursor.zone).plusHours(startHour)
                continue
            }
            let available = Int64((dayEnd.instant.timeIntervalSince(cursor.instant) * 1000).rounded())
            if remaining <= available { return cursor.plusMillis(remaining) }
            remaining -= available
            cursor = date.plusDays(1).atStartOfDay(cursor.zone).plusHours(startHour)
        }
        return cursor
    }
}

/// Deterministic generator for tests (SplitMix64).
public struct SeededGenerator: RandomNumberGenerator {
    private var state: UInt64
    public init(seed: UInt64) { state = seed }
    public mutating func next() -> UInt64 {
        state &+= 0x9E3779B97F4A7C15
        var z = state
        z = (z ^ (z >> 30)) &* 0xBF58476D1CE4E5B9
        z = (z ^ (z >> 27)) &* 0x94D049BB133111EB
        return z ^ (z >> 31)
    }
}
