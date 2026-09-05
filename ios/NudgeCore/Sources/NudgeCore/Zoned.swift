import Foundation

/// An instant paired with the zone used to read its wall-clock fields, like java.time.ZonedDateTime.
/// Conversions use the zone's UTC offset directly (tzdata via TimeZone), never Foundation's
/// Calendar, so results are identical on iOS, macOS and Linux and match java.time's rules.
public struct ZonedDateTime: Comparable, CustomStringConvertible {
    public let instant: Date
    public let zone: TimeZone

    public init(instant: Date, zone: TimeZone) { self.instant = instant; self.zone = zone }

    public var local: LocalDateTime { instant.toLocal(in: zone) }
    public var localDate: LocalDate { local.date }
    public var localTime: LocalTime { local.time }
    public var hour: Int { local.time.hour }
    public var minute: Int { local.time.minute }
    public var dayOfWeek: DayOfWeek { local.date.dayOfWeek }
    public var epochMillis: Int64 { instant.epochMillis }
    public var offsetSeconds: Int { zone.secondsFromGMT(for: instant) }

    /// Instant-based arithmetic (a duration), like java's plusHours/plusMinutes on ZonedDateTime.
    public func plusHours(_ n: Int) -> ZonedDateTime { plusSeconds(Double(n) * 3600) }
    public func plusMinutes(_ n: Int) -> ZonedDateTime { plusSeconds(Double(n) * 60) }
    public func plusMillis(_ ms: Int64) -> ZonedDateTime { plusSeconds(Double(ms) / 1000) }
    public func plusSeconds(_ s: Double) -> ZonedDateTime { ZonedDateTime(instant: instant.addingTimeInterval(s), zone: zone) }
    /// Local-time-line arithmetic: the same wall-clock time n days later.
    public func plusDays(_ n: Int) -> ZonedDateTime { local.plusDays(n).atZone(zone) }

    public func withZone(_ z: TimeZone) -> ZonedDateTime { ZonedDateTime(instant: instant, zone: z) }

    public func isBefore(_ o: ZonedDateTime) -> Bool { instant < o.instant }
    public func isAfter(_ o: ZonedDateTime) -> Bool { instant > o.instant }
    public static func < (a: ZonedDateTime, b: ZonedDateTime) -> Bool { a.instant < b.instant }
    public static func == (a: ZonedDateTime, b: ZonedDateTime) -> Bool { a.instant == b.instant }

    /// "2026-09-10T14:30-06:00[America/Denver]", for tests and logs.
    public var description: String {
        let off = offsetSeconds
        let sign = off < 0 ? "-" : "+"
        let a = abs(off)
        return "\(local)\(sign)\(pad(a / 3600, 2)):\(pad((a % 3600) / 60, 2))[\(zone.identifier)]"
    }
}

public extension Date {
    var epochMillis: Int64 { Int64((timeIntervalSince1970 * 1000).rounded()) }
    init(epochMillis: Int64) { self.init(timeIntervalSince1970: Double(epochMillis) / 1000) }

    func atZone(_ zone: TimeZone) -> ZonedDateTime { ZonedDateTime(instant: self, zone: zone) }

    func toLocal(in zone: TimeZone) -> LocalDateTime {
        let secs = Int((timeIntervalSince1970).rounded(.down)) + zone.secondsFromGMT(for: self)
        return LocalDateTime(epochSeconds: secs)
    }
}

extension LocalDateTime {
    /// Wall-clock seconds since 1970-01-01T00:00 read as if it were UTC.
    var epochSecondsAsUTC: Int { date.epochDay * 86400 + time.secondOfDay }

    init(epochSeconds s: Int) {
        let days = Int((Double(s) / 86400.0).rounded(.down))
        let sod = s - days * 86400
        self.init(date: LocalDate(epochDay: days), time: LocalTime(sod / 3600, (sod % 3600) / 60, sod % 60))
    }
}

public extension LocalDateTime {
    /// Resolves a wall-clock time in a zone the way java.time does: inside a DST gap the time is
    /// pushed forward by the length of the gap (2:30 becomes 3:30); in an overlap the earlier
    /// offset (the earlier instant) wins.
    func atZone(_ zone: TimeZone) -> ZonedDateTime {
        let naive = epochSecondsAsUTC
        // Offsets in force around this wall-clock time; at most two distinct ones matter.
        var offsets: [Int] = []
        for delta in [-86400, 0, 86400] {
            let o = zone.secondsFromGMT(for: Date(timeIntervalSince1970: Double(naive + delta)))
            if !offsets.contains(o) { offsets.append(o) }
        }
        var valid: [Int] = []
        for o in offsets {
            let inst = naive - o
            if Date(timeIntervalSince1970: Double(inst)).toLocal(in: zone) == self { valid.append(inst) }
        }
        if let earliest = valid.min() { return ZonedDateTime(instant: Date(timeIntervalSince1970: Double(earliest)), zone: zone) }
        // Gap: use the offset from before the transition, which lands after the gap by its length.
        let before = offsets.min() ?? 0
        return ZonedDateTime(instant: Date(timeIntervalSince1970: Double(naive - before)), zone: zone)
    }
}

public extension LocalDate {
    func atStartOfDay(_ zone: TimeZone) -> ZonedDateTime { LocalDateTime(date: self, time: LocalTime(0, 0)).atZone(zone) }
}

public extension TimeZone {
    /// The device zone, re-read each time so a zone change is picked up without a restart.
    static var device: TimeZone { TimeZone.autoupdatingCurrent }
}
