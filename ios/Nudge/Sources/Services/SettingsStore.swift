import Foundation
import NudgeCore

/// UserDefaults-backed settings with the same keys and defaults as the Android app.
@MainActor
final class SettingsStore: ObservableObject {
    @Published private(set) var snapshot: SettingsSnapshot

    private let d = UserDefaults.standard

    init() {
        snapshot = SettingsStore.read(UserDefaults.standard)
    }

    private static func read(_ d: UserDefaults) -> SettingsSnapshot {
        var s = SettingsSnapshot()
        s.themeMode = ThemeMode(rawValue: d.string(forKey: "theme_mode") ?? "") ?? .system
        if d.object(forKey: "accent_color") != nil { s.accentColor = Int32(truncatingIfNeeded: d.integer(forKey: "accent_color")) }
        s.customColors = (d.string(forKey: "custom_colors") ?? "").split(separator: ",").compactMap { SettingsMath.parseHex(String($0)) }
        if d.object(forKey: "mean_interval_millis") != nil { s.meanIntervalMillis = Int64(d.double(forKey: "mean_interval_millis")) }
        s.frequencyMode = FrequencyMode(rawValue: d.string(forKey: "frequency_mode") ?? "") ?? .perReminder
        if d.object(forKey: "active_start_hour") != nil { s.activeStartHour = d.integer(forKey: "active_start_hour") }
        if d.object(forKey: "active_end_hour") != nil { s.activeEndHour = d.integer(forKey: "active_end_hour") }
        if d.object(forKey: "active_days") != nil { s.activeDays = d.integer(forKey: "active_days") }
        if d.object(forKey: "paused_until") != nil { s.pausedUntil = Int64(d.double(forKey: "paused_until")) }
        if d.object(forKey: "show_next_random") != nil { s.showNextRandomTime = d.bool(forKey: "show_next_random") }
        s.tutorialSeen = d.bool(forKey: "tutorial_seen")
        return s
    }

    var themeMode: ThemeMode {
        get { snapshot.themeMode }
        set { d.set(newValue.rawValue, forKey: "theme_mode"); snapshot.themeMode = newValue }
    }
    var accentColor: Int32 {
        get { snapshot.accentColor }
        set { let v = newValue | Int32(bitPattern: 0xFF000000); d.set(Int(v), forKey: "accent_color"); snapshot.accentColor = v }
    }
    var customColors: [Int32] {
        get { snapshot.customColors }
        set {
            var seen = Set<Int32>()
            let v = newValue.filter { seen.insert($0).inserted }
            d.set(v.map(SettingsMath.toHex).joined(separator: ","), forKey: "custom_colors"); snapshot.customColors = v
        }
    }
    var meanIntervalMillis: Int64 {
        get { snapshot.meanIntervalMillis }
        set { let v = min(max(newValue, SettingsMath.minMeanMillis), SettingsMath.maxMeanMillis); d.set(Double(v), forKey: "mean_interval_millis"); snapshot.meanIntervalMillis = v }
    }
    var frequencyMode: FrequencyMode {
        get { snapshot.frequencyMode }
        set { d.set(newValue.rawValue, forKey: "frequency_mode"); snapshot.frequencyMode = newValue }
    }
    var activeStartHour: Int {
        get { snapshot.activeStartHour }
        set { let v = min(max(newValue, 0), 23); d.set(v, forKey: "active_start_hour"); snapshot.activeStartHour = v }
    }
    var activeEndHour: Int {
        get { snapshot.activeEndHour }
        set { let v = min(max(newValue, 1), 24); d.set(v, forKey: "active_end_hour"); snapshot.activeEndHour = v }
    }
    var activeDays: Int {
        get { snapshot.activeDays }
        set { let v = (newValue & SettingsMath.allDays) == 0 ? SettingsMath.allDays : newValue & SettingsMath.allDays; d.set(v, forKey: "active_days"); snapshot.activeDays = v }
    }
    var pausedUntil: Int64 {
        get { snapshot.pausedUntil }
        set { let v = max(newValue, 0); d.set(Double(v), forKey: "paused_until"); snapshot.pausedUntil = v }
    }
    var showNextRandomTime: Bool {
        get { snapshot.showNextRandomTime }
        set { d.set(newValue, forKey: "show_next_random"); snapshot.showNextRandomTime = newValue }
    }
    var tutorialSeen: Bool {
        get { snapshot.tutorialSeen }
        set { d.set(newValue, forKey: "tutorial_seen"); snapshot.tutorialSeen = newValue }
    }
}
