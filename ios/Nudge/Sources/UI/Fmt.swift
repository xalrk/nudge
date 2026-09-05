import Foundation
import NudgeCore

/// Date wording shared by the screens; same patterns as the Android app.
enum Fmt {
    private static func formatter(_ pattern: String) -> DateFormatter {
        let f = DateFormatter()
        f.locale = Locale(identifier: "en_US_POSIX")
        f.timeZone = TimeZone.current
        f.dateFormat = pattern
        return f
    }

    static func time(_ d: Date) -> String { formatter("h:mm a").string(from: d) }
    static func dayTime(_ d: Date) -> String { formatter("EEE, MMM d · h:mm a").string(from: d) }
    static func dayTimeYear(_ d: Date) -> String { formatter("EEE, MMM d, yyyy · h:mm a").string(from: d) }
    static func date(_ d: Date) -> String { formatter("EEE, MMM d, yyyy").string(from: d) }
    static func month(_ ym: YearMonth) -> String { formatter("MMMM yyyy").string(from: ym.atDay(1).atStartOfDay(.device).instant) }

    static func time(_ t: LocalTime) -> String { time(LocalDate(2000, 1, 1).atTime(t).atZone(.device).instant) }
    static func date(_ d: LocalDate) -> String { date(d.atStartOfDay(.device).instant) }
    static func dayTime(_ ms: Int64) -> String { dayTime(Date(epochMillis: ms)) }
    static func dayTimeYear(_ z: ZonedDateTime) -> String { dayTimeYear(z.instant) }

    static func instant(_ ms: Int64) -> ZonedDateTime { Date(epochMillis: ms).atZone(.device) }

    static func relative(_ ms: Int64, now: Int64 = Date().epochMillis) -> String {
        let diff = ms - now
        let a = abs(diff)
        let unit: String
        switch a {
        case ..<60_000: unit = "moments"
        case ..<3_600_000: unit = "\(a / 60_000) min"
        case ..<86_400_000: unit = "\(a / 3_600_000) h"
        case ..<(14 * 86_400_000): unit = "\(a / 86_400_000) d"
        default: unit = "\(a / (7 * 86_400_000)) wk"
        }
        return diff >= 0 ? "in \(unit)" : "\(unit) ago"
    }

    static func zoneLabel() -> String {
        let z = TimeZone.current
        let name = z.abbreviation() ?? ""
        let off = z.secondsFromGMT()
        let sign = off < 0 ? "-" : "+"
        let a = abs(off)
        return "\(z.identifier), \(name) \(sign)\(String(format: "%02d:%02d", a / 3600, (a % 3600) / 60))"
    }
}

extension LocalDate {
    /// Today in the device zone.
    static var today: LocalDate { Date().toLocal(in: .device).date }

    /// Bridges to Foundation for DatePicker: local midnight in the device zone.
    var foundationDate: Date { atStartOfDay(.device).instant }
    init(foundationDate d: Date) { self = d.toLocal(in: .device).date }
}

extension LocalTime {
    var foundationDate: Date { LocalDate.today.atTime(self).atZone(.device).instant }
    init(foundationDate d: Date) { let t = d.toLocal(in: .device).time; self = LocalTime(t.hour, t.minute) }
}

extension LocalDateTime {
    static var now: LocalDateTime { Date().toLocal(in: .device) }
}
