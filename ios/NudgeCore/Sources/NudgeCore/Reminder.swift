import Foundation

public enum Kind: String, Codable, CaseIterable { case scheduled = "SCHEDULED", random = "RANDOM" }

public enum Repeat: String, Codable, CaseIterable { case none = "NONE", daily = "DAILY", weekly = "WEEKLY", monthly = "MONTHLY", yearly = "YEARLY" }

/// A reminder, field for field the same as the Android Room entity so CSV files and the
/// scheduling rules behave identically on both platforms. Times are epoch milliseconds.
public struct Reminder: Codable, Equatable, Identifiable {
    public var id: Int64
    public var title: String
    public var body: String
    public var kind: Kind
    /// ISO-8601 local date-time (e.g. 2026-09-10T14:30). Scheduled only.
    public var localDateTime: String?
    /// Zone id the reminder was created in (e.g. America/Denver). Scheduled only.
    public var zoneId: String?
    /// true = fire at the wall-clock time in whatever zone the device is in; false = pin to zoneId.
    public var floating: Bool
    public var repeatRule: Repeat
    public var interval: Int
    /// Bitmask of weekdays for WEEKLY repeats: Mon = 1, Tue = 2, ... Sun = 64. 0 = weekday of start date.
    public var weekdays: Int
    /// ISO local date after which a repeating reminder stops.
    public var endDate: String?
    public var enabled: Bool
    public var nextAt: Int64?
    public var snoozeAt: Int64?
    public var lastFiredAt: Int64?
    public var createdAt: Int64
    public var dedupeKey: String
    /// ARGB color; nil = complementary of the app accent.
    public var color: Int32?
    public var sound: Bool
    public var vibrate: Bool
    /// Comma-separated ISO dates of occurrences removed from a repeating series.
    public var excludedDates: String
    /// Random reminders only: personal average interval in millis, overriding the global slider.
    public var meanOverrideMillis: Int64?

    public init(
        id: Int64 = 0, title: String, body: String = "", kind: Kind,
        localDateTime: String? = nil, zoneId: String? = nil, floating: Bool = true,
        repeatRule: Repeat = .none, interval: Int = 1, weekdays: Int = 0, endDate: String? = nil,
        enabled: Bool = true, nextAt: Int64? = nil, snoozeAt: Int64? = nil, lastFiredAt: Int64? = nil,
        createdAt: Int64 = Date().epochMillis, dedupeKey: String = "", color: Int32? = nil,
        sound: Bool = true, vibrate: Bool = true, excludedDates: String = "", meanOverrideMillis: Int64? = nil
    ) {
        self.id = id; self.title = title; self.body = body; self.kind = kind
        self.localDateTime = localDateTime; self.zoneId = zoneId; self.floating = floating
        self.repeatRule = repeatRule; self.interval = interval; self.weekdays = weekdays; self.endDate = endDate
        self.enabled = enabled; self.nextAt = nextAt; self.snoozeAt = snoozeAt; self.lastFiredAt = lastFiredAt
        self.createdAt = createdAt; self.dedupeKey = dedupeKey; self.color = color
        self.sound = sound; self.vibrate = vibrate; self.excludedDates = excludedDates; self.meanOverrideMillis = meanOverrideMillis
    }

    public var isScheduled: Bool { kind == .scheduled }
    public var isRandom: Bool { kind == .random }

    /// Random reminders: the weekdays this one may fire on, or nil to use the global active days.
    public func randomDaysOrNil() -> Set<DayOfWeek>? { isRandom && weekdays != 0 ? weekdaySet() : nil }

    public func excludedDateSet() -> Set<LocalDate> {
        Set(excludedDates.split(separator: ",").compactMap { LocalDate(iso: $0.trimmingCharacters(in: .whitespaces)) })
    }

    public func withExcluded(_ date: LocalDate) -> Reminder {
        var r = self
        r.excludedDates = (excludedDateSet().union([date])).sorted().map { $0.description }.joined(separator: ",")
        return r
    }

    public func localDateTimeOrNil() -> LocalDateTime? { localDateTime.flatMap { LocalDateTime(iso: $0) } }
    public func endDateOrNil() -> LocalDate? { endDate.flatMap { LocalDate(iso: $0) } }

    /// Effective zone: device zone when floating, the stored zone otherwise.
    public func effectiveZone() -> TimeZone {
        if !floating, let z = zoneId, let tz = TimeZone(identifier: z) { return tz }
        return TimeZone.device
    }

    public func weekdaySet() -> Set<DayOfWeek> { DayOfWeek.set(fromMask: weekdays) }

    public func withDedupeKey() -> Reminder { var r = self; r.dedupeKey = Dedupe.keyFor(self); return r }

    public static func maskOf<S: Sequence>(_ days: S) -> Int where S.Element == DayOfWeek { DayOfWeek.maskOf(days) }
}

public enum Dedupe {
    public static func normalize(_ s: String) -> String {
        s.trimmingCharacters(in: .whitespacesAndNewlines).lowercased()
            .split(whereSeparator: { $0.isWhitespace }).joined(separator: " ")
    }

    public static func keyFor(_ r: Reminder) -> String {
        var parts: [String] = [normalize(r.title), normalize(r.body), r.kind.rawValue]
        if r.isScheduled {
            parts.append(r.localDateTime ?? "")
            parts.append(r.repeatRule.rawValue)
            parts.append(String(r.interval))
            parts.append(String(r.weekdays))
            parts.append(r.endDate ?? "")
            return parts.joined(separator: "|")
        }
        return parts.joined(separator: "|") + "|"
    }
}

/// A notification that was actually delivered. Kept so the calendar can show what already happened.
public struct FiredEvent: Codable, Equatable, Identifiable {
    public var id: Int64
    public var reminderId: Int64
    public var title: String
    public var body: String
    public var kind: Kind
    public var firedAt: Int64

    public init(id: Int64 = 0, reminderId: Int64, title: String, body: String, kind: Kind, firedAt: Int64) {
        self.id = id; self.reminderId = reminderId; self.title = title; self.body = body; self.kind = kind; self.firedAt = firedAt
    }
}
