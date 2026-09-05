import SwiftUI
import UniformTypeIdentifiers
import UserNotifications
import NudgeCore

struct CSVDocument: FileDocument {
    static var readableContentTypes: [UTType] { [.commaSeparatedText, .plainText] }
    var text: String
    init(text: String) { self.text = text }
    init(configuration: ReadConfiguration) throws {
        text = String(data: configuration.file.regularFileContents ?? Data(), encoding: .utf8) ?? ""
    }
    func fileWrapper(configuration: WriteConfiguration) throws -> FileWrapper { FileWrapper(regularFileWithContents: Data(text.utf8)) }
}

struct SettingsScreen: View {
    @EnvironmentObject private var engine: ReminderEngine
    @EnvironmentObject private var store: Store
    @EnvironmentObject private var settings: SettingsStore
    @EnvironmentObject private var router: Router
    @Environment(\.theme) private var theme
    @Environment(\.scenePhase) private var scenePhase

    @State private var slider: Double = 0
    @State private var showFormatHelp = false
    @State private var showCredits = false
    @State private var showImporter = false
    @State private var showExporter = false
    @State private var exportDoc = CSVDocument(text: "")
    @State private var notifStatus: UNAuthorizationStatus = .notDetermined

    private var s: SettingsSnapshot { settings.snapshot }
    private static let releasesPage = URL(string: "https://github.com/xalrk/nudge/releases/latest")!
    private var version: String { (Bundle.main.infoDictionary?["CFBundleShortVersionString"] as? String) ?? "?" }

    var body: some View {
        let reminders = store.state.reminders
        let plan = store.state.plan
        Screen {
            ScrollView {
                VStack(alignment: .leading, spacing: 8) {
                    SectionTitle("Appearance")
                    Segmented(options: [(ThemeMode.system, "System"), (.light, "Light"), (.dark, "Dark")],
                              selection: Binding(get: { s.themeMode }, set: { engine.setThemeMode($0) }))
                    Hint("Dark mode is true black for OLED screens.")
                    Text("Accent color").padding(.top, 8)
                    SwatchRow(current: s.accentColor, customColors: s.customColors,
                              onPick: { if let c = $0 { engine.setAccentColor(c) } }, onAddCustom: { engine.addCustomColor($0) }, onRemoveCustom: { engine.removeCustomColor($0) })
                    Hint("Dark mode lightens the accent so it stays readable on black. Calendar dots default to the complementary color; each reminder can pick its own.")

                    Divider().padding(.vertical, 12)
                    SectionTitle("Random Reminders")
                    Text("Average frequency")
                    Text(SettingsMath.describeInterval(SettingsMath.sliderToMillis(slider)).capitalizedFirst).font(.subheadline).foregroundColor(theme.primary)
                    Slider(value: $slider, in: 0...1) { editing in if !editing { engine.setMeanInterval(SettingsMath.sliderToMillis(slider)) } }
                    Hint("Timing stays random: this only changes how often it happens on average.")
                    Text("Frequency applies to").padding(.top, 8)
                    Segmented(options: [(FrequencyMode.perReminder, "Each reminder"), (.wholePool, "Whole list")],
                              selection: Binding(get: { s.frequencyMode }, set: { engine.setFrequencyMode($0) }))
                    Hint(s.frequencyMode == .perReminder
                         ? "Every random reminder fires on its own schedule, so a long list means more notifications overall."
                         : "Only one random reminder fires per interval, picked at random from the whole list.")

                    Text("Active hours").padding(.top, 12)
                    HStack {
                        Picker("From", selection: Binding(get: { s.activeStartHour }, set: { engine.setActiveWindow(start: $0, end: max(s.activeEndHour, $0 + 1)) })) {
                            ForEach(0..<24, id: \.self) { Text(SettingsMath.hourLabel($0)).tag($0) }
                        }
                        Text("to").foregroundColor(theme.onSurfaceVariant)
                        Picker("To", selection: Binding(get: { s.activeEndHour }, set: { engine.setActiveWindow(start: min(s.activeStartHour, $0 - 1), end: $0) })) {
                            ForEach(1..<25, id: \.self) { Text(SettingsMath.hourLabel($0)).tag($0) }
                        }
                        Spacer()
                    }
                    Hint("Random reminders never fire outside this window. Times follow the device time zone (\(Fmt.zoneLabel())).")
                    Text("Active days").padding(.top, 8)
                    DayCircles(selected: s.activeDaySet()) { d in
                        let cur = s.activeDaySet()
                        let next = cur.contains(d) ? cur.subtracting([d]) : cur.union([d])
                        if !next.isEmpty { engine.setActiveDays(Reminder.maskOf(next)) }
                    }
                    Hint("Random reminders skip the days that are off.")
                    Toggle("Show next random time", isOn: Binding(get: { s.showNextRandomTime }, set: { engine.setShowNextRandom($0) })).padding(.top, 8)

                    Divider().padding(.vertical, 12)
                    HStack {
                        SectionTitle("Import & export")
                        Spacer()
                        Button { showFormatHelp = true } label: { Image(systemName: "info.circle") }.accessibilityLabel("How to format the CSV")
                    }
                    Hint("Import a CSV file with one reminder per row. Duplicates are skipped automatically. Tap the info icon for the column layout. You can also open a CSV from Files or Mail with \"Open in Nudge\".")
                    HStack(spacing: 8) {
                        FilledButton(title: "Import CSV") { showImporter = true }
                        OutlineButton(title: "Export CSV") { exportDoc = CSVDocument(text: engine.exportCsv()); showExporter = true }
                    }
                    .padding(.top, 4)
                    Hint("\(reminders.count) reminders stored")

                    Divider().padding(.vertical, 12)
                    SectionTitle("Reliability")
                    StatusRow(label: "Notifications", ok: notifStatus == .authorized || notifStatus == .provisional) {
                        if let url = URL(string: UIApplication.openNotificationSettingsURLString) { UIApplication.shared.open(url) }
                    }
                    let last = plan.last.map { Fmt.dayTime($0.at) }
                    Hint(plan.isEmpty ? "Nothing is queued with iOS yet."
                         : "\(plan.count) notifications are queued with iOS, through \(last!). iOS lets an app queue about 60 at a time, and Nudge tops the queue up every time you open it, so open the app now and then.")
                    Hint("This copy was installed with AltStore or Sideloadly, so it needs re-signing every 7 days: keep AltStore refreshing, or reinstall from the same computer.")
                    HStack(spacing: 8) {
                        OutlineButton(title: "Test notification") { engine.testNotification() }
                        OutlineButton(title: "Fire a random one") { engine.fireRandomNow() }
                    }
                    .padding(.top, 4)

                    Divider().padding(.vertical, 12)
                    SectionTitle("Updates")
                    Hint("New versions are posted on GitHub. Install them the same way you installed this one.")
                    Link("Releases page", destination: SettingsScreen.releasesPage).padding(.top, 4)

                    Divider().padding(.vertical, 12)
                    SectionTitle("Help")
                    HStack(spacing: 8) {
                        OutlineButton(title: "Show tutorial") { router.showTutorial = true }
                        OutlineButton(title: "Open-source credits") { showCredits = true }
                    }
                    Divider().padding(.vertical, 12)
                    Hint("Nudge \(version) for iOS · github.com/xalrk/nudge")
                    Spacer(minLength: 32)
                }
                .padding(.horizontal, 16)
            }
        }
        .navigationTitle("Settings")
        .onAppear { slider = SettingsMath.millisToSlider(s.meanIntervalMillis); refreshStatus() }
        .onChange(of: scenePhase) { if $0 == .active { refreshStatus() } }
        .onChange(of: s.meanIntervalMillis) { slider = SettingsMath.millisToSlider($0) }
        .fileImporter(isPresented: $showImporter, allowedContentTypes: [.commaSeparatedText, .plainText, .json, .data], allowsMultipleSelection: false) { result in
            if case .success(let urls) = result, let url = urls.first { engine.importFile(url) }
        }
        .fileExporter(isPresented: $showExporter, document: exportDoc, contentType: .commaSeparatedText, defaultFilename: "nudge-reminders") { result in
            switch result {
            case .success: engine.show("Exported \(reminders.count) reminders")
            case .failure: engine.show("Export failed")
            }
        }
        .sheet(isPresented: $showFormatHelp) { FormatHelpSheet().environment(\.theme, theme).tint(theme.primary) }
        .sheet(isPresented: $showCredits) { CreditsSheet().environment(\.theme, theme).tint(theme.primary) }
    }

    private func refreshStatus() {
        Task { notifStatus = await Notifier.authorizationStatus() }
    }
}

private struct StatusRow: View {
    @Environment(\.theme) private var theme
    let label: String
    let ok: Bool
    let onFix: () -> Void
    var body: some View {
        HStack {
            Text(label)
            Spacer()
            if ok { Text("OK").font(.subheadline.weight(.medium)).foregroundColor(theme.primary) }
            else { Button("Fix", action: onFix) }
        }
        .padding(.vertical, 4)
    }
}

private struct FormatHelpSheet: View {
    @Environment(\.dismiss) private var dismiss
    @Environment(\.theme) private var theme
    var body: some View {
        NavigationStack {
            ScrollView {
                VStack(alignment: .leading, spacing: 10) {
                    Hint("One reminder per row. Put this header on the first line, then one row per reminder. Only \"title\" is required; leave any other cell empty.")
                    Text("""
                    title,details,date,time,repeat,every,weekdays,until,zone,follow_device_zone
                    Drink some water,,,,,,,,,
                    Call mom,,2026-09-14,18:00,weekly,1,sun,,,
                    Standup,,,09:30,weekly,1,mon;tue;wed;thu;fri,2026-12-19,,
                    Gym,"Bring a towel, water",,18:00,weekly,1,mon;wed;fri,,,
                    Pay rent,,2026-10-01,09:00,monthly,,,,,
                    Flight,,2026-10-20,06:30,,,,,America/Denver,no
                    """).font(.system(.caption, design: .monospaced))
                    VStack(alignment: .leading, spacing: 4) {
                        col("title", "the notification text (required)")
                        col("details", "longer text shown under the title")
                        col("date", "YYYY-MM-DD; empty with a time = the next such time")
                        col("time", "HH:MM or 2:15pm; defaults to 09:00 when a date is given")
                        col("repeat", "daily, weekly, monthly, yearly, weekdays, weekends; empty = once")
                        col("every", "repeat every N days/weeks/months/years (default 1)")
                        col("weekdays", "for weekly: mon;tue;wed;thu;fri;sat;sun, separated by ;")
                        col("until", "YYYY-MM-DD, last day of a repeat")
                        col("zone", "time zone id such as Europe/Berlin (default: this device)")
                        col("follow_device_zone", "yes (ring at the same wall-clock time anywhere) or no (pin to the zone)")
                    }
                    Hint("A row with no date and no time becomes a random reminder. Wrap a cell in double quotes if it contains a comma. Columns may be in any order when the header is present; without a header they are read in the order above. Rows starting with # are ignored. Spreadsheet apps such as Numbers, Excel and Google Sheets can save this with File → Export/Save as → CSV.")
                }
                .padding()
            }
            .background(theme.background.ignoresSafeArea())
            .foregroundColor(theme.onSurface)
            .navigationTitle("CSV format")
            .navigationBarTitleDisplayMode(.inline)
            .toolbar { ToolbarItem(placement: .confirmationAction) { Button("Got it") { dismiss() } } }
        }
    }
    private func col(_ name: String, _ meaning: String) -> some View {
        HStack(alignment: .top) {
            Text(name).font(.system(.caption, design: .monospaced)).foregroundColor(theme.primary).frame(width: 130, alignment: .leading)
            Text(meaning).font(.caption)
        }
    }
}

private struct CreditsSheet: View {
    @Environment(\.dismiss) private var dismiss
    @Environment(\.theme) private var theme
    private let credits: [(String, String)] = [
        ("Swift", "Language and standard library. Apple; Apache License 2.0."),
        ("SwiftUI, UserNotifications, Foundation", "The user interface and notifications. Apple SDK."),
        ("SF Symbols", "Icons used throughout. Apple."),
        ("XcodeGen", "Generates the Xcode project on the build server. Yonas Kolb; MIT."),
        ("GitHub Actions", "Free macOS build machines for the open-source repository."),
        ("AltStore / Sideloadly", "Install tools that sign the app with your own Apple ID."),
    ]
    var body: some View {
        NavigationStack {
            ScrollView {
                VStack(alignment: .leading, spacing: 10) {
                    Hint("Nudge is MIT-licensed. The iOS app is built with Apple's SDKs and these projects.")
                    ForEach(credits, id: \.0) { c in
                        VStack(alignment: .leading, spacing: 2) {
                            Text(c.0).font(.subheadline).foregroundColor(theme.primary)
                            Text(c.1).font(.caption).foregroundColor(theme.onSurfaceVariant)
                        }
                    }
                }
                .padding()
            }
            .background(theme.background.ignoresSafeArea())
            .foregroundColor(theme.onSurface)
            .navigationTitle("Open-source credits")
            .navigationBarTitleDisplayMode(.inline)
            .toolbar { ToolbarItem(placement: .confirmationAction) { Button("Close") { dismiss() } } }
        }
    }
}
