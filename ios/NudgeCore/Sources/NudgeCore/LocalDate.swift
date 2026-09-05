import Foundation

/// Monday = 1 ... Sunday = 7, matching java.time.DayOfWeek and the Android weekday bitmask.
public enum DayOfWeek: Int, CaseIterable, Codable, Comparable {
    case monday = 1, tuesday, wednesday, thursday, friday, saturday, sunday

    public static func < (a: DayOfWeek, b: DayOfWeek) -> Bool { a.rawValue < b.rawValue }

    /// Upper-case English name, e.g. "MONDAY" (what the CSV parser matches against).
    public var name: String {
        switch self {
        case .monday: return "MONDAY"; case .tuesday: return "TUESDAY"; case .wednesday: return "WEDNESDAY"
        case .thursday: return "THURSDAY"; case .friday: return "FRIDAY"; case .saturday: return "SATURDAY"; case .sunday: return "SUNDAY"
        }
    }
    /// "Mon", "Tue", ...
    public var short: String { name.prefix(3).capitalized }
    /// "M", "T", "W", ...
    public var narrow: String { String(name.prefix(1)) }

    public var mask: Int { 1 << (rawValue - 1) }
    public static func maskOf<S: Sequence>(_ days: S) -> Int where S.Element == DayOfWeek { days.reduce(0) { $0 | $1.mask } }
    public static func set(fromMask mask: Int) -> Set<DayOfWeek> { Set(allCases.filter { mask & $0.mask != 0 }) }
}

/// A calendar date with no time zone, like java.time.LocalDate. Arithmetic is proleptic
/// Gregorian and independent of Foundation's Calendar, so it behaves identically everywhere.
public struct LocalDate: Hashable, Comparable, Codable, CustomStringConvertible {
    public let year: Int
    public let month: Int
    public let day: Int

    public init(_ year: Int, _ month: Int, _ day: Int) {
        self.year = year; self.month = month; self.day = day
    }

    /// Strict "yyyy-MM-dd".
    public init?(iso: String) {
        let parts = iso.split(separator: "-", omittingEmptySubsequences: false)
        guard parts.count == 3, parts[0].count == 4, parts[1].count == 2, parts[2].count == 2,
              let y = Int(parts[0]), let m = Int(parts[1]), let d = Int(parts[2]),
              LocalDate.isValid(y, m, d) else { return nil }
        self.init(y, m, d)
    }

    public static func isValid(_ y: Int, _ m: Int, _ d: Int) -> Bool {
        m >= 1 && m <= 12 && d >= 1 && d <= lengthOfMonth(y, m)
    }

    public static func isLeap(_ y: Int) -> Bool { (y % 4 == 0 && y % 100 != 0) || y % 400 == 0 }
    public static func lengthOfMonth(_ y: Int, _ m: Int) -> Int {
        switch m {
        case 2: return isLeap(y) ? 29 : 28
        case 4, 6, 9, 11: return 30
        default: return 31
        }
    }
    public var lengthOfMonth: Int { LocalDate.lengthOfMonth(year, month) }

    // Days since 1970-01-01 (Howard Hinnant's civil algorithms).
    public var epochDay: Int {
        let y = month <= 2 ? year - 1 : year
        let era = (y >= 0 ? y : y - 399) / 400
        let yoe = y - era * 400
        let mp = (month + 9) % 12
        let doy = (153 * mp + 2) / 5 + day - 1
        let doe = yoe * 365 + yoe / 4 - yoe / 100 + doy
        return era * 146097 + doe - 719468
    }

    public init(epochDay z0: Int) {
        let z = z0 + 719468
        let era = (z >= 0 ? z : z - 146096) / 146097
        let doe = z - era * 146097
        let yoe = (doe - doe / 1460 + doe / 36524 - doe / 146096) / 365
        let y = yoe + era * 400
        let doy = doe - (365 * yoe + yoe / 4 - yoe / 100)
        let mp = (5 * doy + 2) / 153
        let d = doy - (153 * mp + 2) / 5 + 1
        let m = mp < 10 ? mp + 3 : mp - 9
        self.init(m <= 2 ? y + 1 : y, m, d)
    }

    public var dayOfWeek: DayOfWeek {
        // 1970-01-01 was a Thursday (4).
        let v = ((epochDay + 3) % 7 + 7) % 7 // Monday = 0
        return DayOfWeek(rawValue: v + 1)!
    }

    public func plusDays(_ n: Int) -> LocalDate { LocalDate(epochDay: epochDay + n) }
    public func minusDays(_ n: Int) -> LocalDate { plusDays(-n) }
    public func plusWeeks(_ n: Int) -> LocalDate { plusDays(7 * n) }

    /// Adds months, clamping the day to the target month's length (31 Jan + 1 month = 28/29 Feb).
    public func plusMonths(_ n: Int) -> LocalDate {
        let total = year * 12 + (month - 1) + n
        let y = Int((Double(total) / 12.0).rounded(.down))
        let m = total - y * 12 + 1
        return LocalDate(y, m, min(day, LocalDate.lengthOfMonth(y, m)))
    }
    public func plusYears(_ n: Int) -> LocalDate {
        let y = year + n
        return LocalDate(y, month, min(day, LocalDate.lengthOfMonth(y, month)))
    }

    public func withDayOfMonth(_ d: Int) -> LocalDate { LocalDate(year, month, d) }
    /// The date with the given weekday inside the same Monday-to-Sunday week.
    public func with(_ dow: DayOfWeek) -> LocalDate { plusDays(dow.rawValue - dayOfWeek.rawValue) }

    public func isBefore(_ o: LocalDate) -> Bool { self < o }
    public func isAfter(_ o: LocalDate) -> Bool { self > o }

    public static func daysBetween(_ a: LocalDate, _ b: LocalDate) -> Int { b.epochDay - a.epochDay }
    /// Whole months from a to b, like ChronoUnit.MONTHS.between.
    public static func monthsBetween(_ a: LocalDate, _ b: LocalDate) -> Int {
        let packed1 = a.year * 12 + a.month - 1
        let packed2 = b.year * 12 + b.month - 1
        let months = packed2 - packed1
        if months > 0 && b.day < a.day { return months - 1 }
        if months < 0 && b.day > a.day { return months + 1 }
        return months
    }
    public static func yearsBetween(_ a: LocalDate, _ b: LocalDate) -> Int {
        let m = monthsBetween(a, b)
        return m >= 0 ? m / 12 : -((-m) / 12)
    }

    public static func < (a: LocalDate, b: LocalDate) -> Bool {
        (a.year, a.month, a.day) < (b.year, b.month, b.day)
    }

    public var description: String { "\(pad(year, 4))-\(pad(month, 2))-\(pad(day, 2))" }

    public func atTime(_ t: LocalTime) -> LocalDateTime { LocalDateTime(date: self, time: t) }
    public func atTime(_ h: Int, _ m: Int) -> LocalDateTime { LocalDateTime(date: self, time: LocalTime(h, m)) }
}

public struct LocalTime: Hashable, Comparable, Codable, CustomStringConvertible {
    public let hour: Int
    public let minute: Int
    public let second: Int

    public init(_ hour: Int, _ minute: Int, _ second: Int = 0) {
        self.hour = hour; self.minute = minute; self.second = second
    }

    /// "HH:mm" or "HH:mm:ss".
    public init?(iso: String) {
        let parts = iso.split(separator: ":", omittingEmptySubsequences: false)
        guard parts.count == 2 || parts.count == 3, parts[0].count == 2, parts[1].count == 2,
              let h = Int(parts[0]), let m = Int(parts[1]), h >= 0, h < 24, m >= 0, m < 60 else { return nil }
        var s = 0
        if parts.count == 3 { guard let ss = Int(parts[2].prefix(2)), ss >= 0, ss < 60 else { return nil }; s = ss }
        self.init(h, m, s)
    }

    public var secondOfDay: Int { hour * 3600 + minute * 60 + second }
    public func withSecond(_ s: Int) -> LocalTime { LocalTime(hour, minute, s) }
    public func withMinute(_ m: Int) -> LocalTime { LocalTime(hour, m, second) }

    public static func < (a: LocalTime, b: LocalTime) -> Bool { a.secondOfDay < b.secondOfDay }
    /// "HH:mm" (seconds only when non-zero, like java.time).
    public var description: String { second == 0 ? "\(pad(hour, 2)):\(pad(minute, 2))" : "\(pad(hour, 2)):\(pad(minute, 2)):\(pad(second, 2))" }
    public var hhmm: String { "\(pad(hour, 2)):\(pad(minute, 2))" }
}

public struct LocalDateTime: Hashable, Comparable, Codable, CustomStringConvertible {
    public let date: LocalDate
    public let time: LocalTime

    public init(date: LocalDate, time: LocalTime) { self.date = date; self.time = time }

    /// "yyyy-MM-ddTHH:mm" with optional seconds.
    public init?(iso: String) {
        guard let t = iso.firstIndex(of: "T") else { return nil }
        guard let d = LocalDate(iso: String(iso[iso.startIndex..<t])), let tm = LocalTime(iso: String(iso[iso.index(after: t)...])) else { return nil }
        self.init(date: d, time: tm)
    }

    public var dayOfWeek: DayOfWeek { date.dayOfWeek }
    public func plusDays(_ n: Int) -> LocalDateTime { LocalDateTime(date: date.plusDays(n), time: time) }
    public func plusWeeks(_ n: Int) -> LocalDateTime { plusDays(7 * n) }
    public func plusMonths(_ n: Int) -> LocalDateTime { LocalDateTime(date: date.plusMonths(n), time: time) }
    public func plusYears(_ n: Int) -> LocalDateTime { LocalDateTime(date: date.plusYears(n), time: time) }
    /// Wall-clock arithmetic on the local time-line (no zone), like java's LocalDateTime.plusMinutes.
    public func plusMinutes(_ n: Int) -> LocalDateTime { plusSeconds(n * 60) }
    public func plusHours(_ n: Int) -> LocalDateTime { plusSeconds(n * 3600) }
    public func plusSeconds(_ n: Int) -> LocalDateTime {
        let total = time.secondOfDay + n
        let dayShift = Int((Double(total) / 86400.0).rounded(.down))
        let sod = total - dayShift * 86400
        return LocalDateTime(date: date.plusDays(dayShift), time: LocalTime(sod / 3600, (sod % 3600) / 60, sod % 60))
    }

    public func isBefore(_ o: LocalDateTime) -> Bool { self < o }
    public func isAfter(_ o: LocalDateTime) -> Bool { self > o }
    public static func < (a: LocalDateTime, b: LocalDateTime) -> Bool { a.date == b.date ? a.time < b.time : a.date < b.date }

    /// Storage format, minutes precision: "2026-09-10T14:30".
    public var description: String { "\(date)T\(time.hhmm)" }
}

public struct YearMonth: Hashable, Comparable, Codable, CustomStringConvertible {
    public let year: Int
    public let month: Int
    public init(_ year: Int, _ month: Int) { self.year = year; self.month = month }
    public init(of date: LocalDate) { self.init(date.year, date.month) }
    public func atDay(_ d: Int) -> LocalDate { LocalDate(year, month, d) }
    public var lengthOfMonth: Int { LocalDate.lengthOfMonth(year, month) }
    public func plusMonths(_ n: Int) -> YearMonth { let d = atDay(1).plusMonths(n); return YearMonth(d.year, d.month) }
    public func minusMonths(_ n: Int) -> YearMonth { plusMonths(-n) }
    public static func < (a: YearMonth, b: YearMonth) -> Bool { (a.year, a.month) < (b.year, b.month) }
    public var description: String { "\(pad(year, 4))-\(pad(month, 2))" }
}

func pad(_ v: Int, _ width: Int) -> String {
    let s = String(abs(v))
    let p = s.count < width ? String(repeating: "0", count: width - s.count) + s : s
    return v < 0 ? "-" + p : p
}
