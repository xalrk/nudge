import Foundation

/// How the random-frequency setting is interpreted.
public enum FrequencyMode: String, Codable, CaseIterable {
    /// Each random reminder independently averages one firing per meanIntervalMillis.
    case perReminder = "PER_REMINDER"
    /// The whole pool together averages one firing per interval (one reminder picked at random).
    case wholePool = "WHOLE_POOL"
}

public enum ThemeMode: String, Codable, CaseIterable { case system = "SYSTEM", light = "LIGHT", dark = "DARK" }

public struct SettingsSnapshot: Equatable {
    public var themeMode: ThemeMode
    /// ARGB accent used for the whole theme.
    public var accentColor: Int32
    /// User-added swatches shown after the presets.
    public var customColors: [Int32]
    public var meanIntervalMillis: Int64
    public var frequencyMode: FrequencyMode
    public var activeStartHour: Int
    public var activeEndHour: Int
    /// Weekday bitmask (Mon = 1 ... Sun = 64) on which random reminders may fire.
    public var activeDays: Int
    /// Epoch millis until which every reminder is muted; 0 = not paused.
    public var pausedUntil: Int64
    public var showNextRandomTime: Bool
    public var tutorialSeen: Bool

    public init(themeMode: ThemeMode = .system, accentColor: Int32 = SettingsMath.defaultAccent, customColors: [Int32] = [],
                meanIntervalMillis: Int64 = SettingsMath.defaultMeanMillis, frequencyMode: FrequencyMode = .perReminder,
                activeStartHour: Int = 7, activeEndHour: Int = 23, activeDays: Int = SettingsMath.allDays,
                pausedUntil: Int64 = 0, showNextRandomTime: Bool = true, tutorialSeen: Bool = false) {
        self.themeMode = themeMode; self.accentColor = accentColor; self.customColors = customColors
        self.meanIntervalMillis = meanIntervalMillis; self.frequencyMode = frequencyMode
        self.activeStartHour = activeStartHour; self.activeEndHour = activeEndHour; self.activeDays = activeDays
        self.pausedUntil = pausedUntil; self.showNextRandomTime = showNextRandomTime; self.tutorialSeen = tutorialSeen
    }

    public var activeHoursPerDay: Int { activeEndHour - activeStartHour }
    public func isPaused(now: Date = Date()) -> Bool { pausedUntil > now.epochMillis }
    public func activeDaySet() -> Set<DayOfWeek> { DayOfWeek.set(fromMask: activeDays) }
}

/// Constants and pure helpers shared with the settings screen; storage lives in the app.
public enum SettingsMath {
    public static let allDays = 0x7F
    public static let defaultAccent: Int32 = Int32(bitPattern: 0xFF008DCA)

    /// Hand-picked accents that read well on both white and true black.
    public static let accentPresets: [(name: String, argb: Int32)] = [
        ("Nudge blue", Int32(bitPattern: 0xFF008DCA)),
        ("Indigo", Int32(bitPattern: 0xFF3D5AFE)),
        ("Blue", Int32(bitPattern: 0xFF1E88E5)),
        ("Teal", Int32(bitPattern: 0xFF00897B)),
        ("Green", Int32(bitPattern: 0xFF43A047)),
        ("Amber", Int32(bitPattern: 0xFFF9A825)),
        ("Orange", Int32(bitPattern: 0xFFF4511E)),
        ("Red", Int32(bitPattern: 0xFFE53935)),
        ("Pink", Int32(bitPattern: 0xFFD81B60)),
        ("Purple", Int32(bitPattern: 0xFF8E24AA)),
        ("Violet", Int32(bitPattern: 0xFF5E35B1)),
        ("Slate", Int32(bitPattern: 0xFF546E7A)),
        ("Mono", Int32(bitPattern: 0xFF212121)),
    ]

    /// Parses "#RGB", "#RRGGBB", "RRGGBB"; returns nil when invalid.
    public static func parseHex(_ input: String) -> Int32? {
        var h = input.trimmingCharacters(in: .whitespacesAndNewlines)
        if h.hasPrefix("#") { h.removeFirst() }
        guard !h.isEmpty, h.allSatisfy({ $0.isHexDigit }) else { return nil }
        let full: String
        switch h.count {
        case 3: full = h.map { "\($0)\($0)" }.joined()
        case 6: full = h
        default: return nil
        }
        guard let v = UInt32(full, radix: 16) else { return nil }
        return Int32(bitPattern: 0xFF000000 | v)
    }

    public static func toHex(_ argb: Int32) -> String {
        let v = UInt32(bitPattern: argb) & 0xFFFFFF
        let s = String(v, radix: 16, uppercase: true)
        return "#" + String(repeating: "0", count: max(0, 6 - s.count)) + s
    }

    public static let hourMillis: Int64 = 60 * 60 * 1000
    public static let dayMillis: Int64 = 24 * hourMillis
    public static let defaultMeanMillis: Int64 = 14 * dayMillis
    public static let minMeanMillis: Int64 = hourMillis
    public static let maxMeanMillis: Int64 = 180 * dayMillis

    /// Map the 0..1 slider position onto the mean interval (log scale, 1 hour .. 180 days).
    public static func sliderToMillis(_ t: Double) -> Int64 {
        let lo = log(Double(minMeanMillis))
        let hi = log(Double(maxMeanMillis))
        return Int64(exp(lo + (hi - lo) * min(max(t, 0), 1)))
    }

    public static func millisToSlider(_ ms: Int64) -> Double {
        let lo = log(Double(minMeanMillis))
        let hi = log(Double(maxMeanMillis))
        let v = min(max(Double(ms), Double(minMeanMillis)), Double(maxMeanMillis))
        return (log(v) - lo) / (hi - lo)
    }

    /// Human wording for an average interval, e.g. "about once every 2 weeks".
    public static func describeInterval(_ ms: Int64) -> String {
        let hours = Double(ms) / Double(hourMillis)
        let days = Double(ms) / Double(dayMillis)
        switch true {
        case hours < 1.5: return "about once an hour"
        case hours < 36: return "about once every \(roundNice(hours)) hours"
        case days < 1.5: return "about once a day"
        case days < 13: return "about once every \(roundNice(days)) days"
        case days < 60: return "about once every \(roundNice(days / 7)) weeks"
        default: return "about once every \(roundNice(days / 30.4)) months"
        }
    }

    private static func roundNice(_ v: Double) -> String {
        let r = v < 10 ? (v * 2).rounded() / 2.0 : v.rounded()
        return r == r.rounded(.down) ? String(Int64(r)) : String(r)
    }

    public static func hourLabel(_ h: Int) -> String {
        switch h {
        case 0, 24: return "midnight"
        case 12: return "noon"
        case ..<12: return "\(h) am"
        default: return "\(h - 12) pm"
        }
    }
}
