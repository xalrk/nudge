import SwiftUI
import NudgeCore

struct RandomScreen: View {
    @EnvironmentObject private var engine: ReminderEngine
    @EnvironmentObject private var store: Store
    @EnvironmentObject private var settings: SettingsStore
    @EnvironmentObject private var router: Router
    @Environment(\.theme) private var theme

    // Roll: a real cube tumbles around two axes and eases onto one flat, random face.
    @State private var fromAngles = anglesFor(Int.random(in: 1...6))
    @State private var toAngles = anglesFor(1)
    @State private var turns: (Double, Double) = (0, 0)
    @State private var spin: Double = 0

    var body: some View {
        let s = settings.snapshot
        let random = store.state.reminders.filter { $0.isRandom }
        let enabledCount = random.filter { $0.enabled }.count
        let perReminderMean: Int64 = enabledCount > 0 ? s.meanIntervalMillis / Int64(enabledCount) : 0
        let overallMean: Int64 = s.frequencyMode == .perReminder ? perReminderMean : s.meanIntervalMillis
        Screen {
            ZStack(alignment: .bottomTrailing) {
                ScrollView {
                    LazyVStack(alignment: .leading, spacing: 0) {
                        PauseBanner(settings: s) { engine.setPausedUntil(0) }
                        VStack(alignment: .leading, spacing: 4) {
                            Text(headline(s)).font(.subheadline).foregroundColor(theme.onSurfaceVariant)
                            if enabledCount > 1 && s.frequencyMode == .perReminder {
                                Text(sumLine(enabledCount, overallMean)).font(.footnote).foregroundColor(theme.onSurfaceVariant)
                            }
                        }
                        .padding(16)
                        if random.isEmpty {
                            Text("No random reminders yet. Tap + or import a CSV file from Settings.").foregroundColor(theme.onSurfaceVariant).padding(16)
                        }
                        ForEach(random) { r in
                            ReminderRow(r: r, subtitle: subtitle(r, s), color: engine.colorOf(r), onTap: { router.edit = EditRequest(id: r.id) }, onToggle: { engine.setEnabled(r.id, $0) })
                        }
                        Color.clear.frame(height: 88)
                    }
                }
                Fab(systemImage: "plus", label: "Add random reminder") { router.edit = EditRequest(kind: .random) }
            }
        }
        .navigationTitle("Random Reminders")
        .toolbar {
            ToolbarItem(placement: .navigationBarTrailing) {
                Button(action: roll) {
                    let rx = fromAngles.0 + (toAngles.0 + turns.0 - fromAngles.0) * spin
                    let ry = fromAngles.1 + (toAngles.1 + turns.1 - fromAngles.1) * spin
                    Die3D(rotX: rx, rotY: ry).frame(width: 28, height: 28)
                }
                .accessibilityLabel("Re-roll times")
            }
        }
        .onAppear { toAngles = fromAngles }
    }

    private func headline(_ s: SettingsSnapshot) -> String {
        let hours = "between \(SettingsMath.hourLabel(s.activeStartHour)) and \(SettingsMath.hourLabel(s.activeEndHour))"
        let rate = SettingsMath.describeInterval(s.meanIntervalMillis)
        let who = s.frequencyMode == .perReminder ? "Each" : "One"
        return "\(who) of these fires at an unpredictable moment \(hours), \(rate) on average. Both are configurable in Settings."
    }

    private func sumLine(_ enabledCount: Int, _ overallMean: Int64) -> String {
        "With \(enabledCount) enabled, that adds up to \(SettingsMath.describeInterval(max(overallMean, SettingsMath.minMeanMillis))) overall."
    }

    private func subtitle(_ r: Reminder, _ s: SettingsSnapshot) -> String {
        var rate = ""
        if let m = r.meanOverrideMillis { rate += " · " + SettingsMath.describeInterval(m) }
        if let d = r.randomDaysOrNil() {
            let names = DayOfWeek.allCases.filter { d.contains($0) }.map { String($0.name.prefix(2)).capitalized }
            rate += " · " + names.joined(separator: " ")
        }
        if !r.enabled { return "Paused" }
        guard let n = r.nextAt else { return "Waiting" }
        if s.showNextRandomTime { return "Next " + Fmt.relative(n) + " · " + Fmt.dayTime(n) + rate }
        return "Sometime soon" + rate
    }

    private func roll() {
        engine.rerollRandom()
        let landing = Int.random(in: 1...6)
        // Continue from wherever the die is now so rapid taps never jump.
        let t = spin
        fromAngles = (fromAngles.0 + (toAngles.0 + turns.0 - fromAngles.0) * t, fromAngles.1 + (toAngles.1 + turns.1 - fromAngles.1) * t)
        toAngles = anglesFor(landing)
        turns = (360 * Double(Int.random(in: 1...2)), 360 * Double(Int.random(in: 2...3)))
        spin = 0
        withAnimation(.timingCurve(0.4, 0, 0.2, 1, duration: 1.1)) { spin = 1 }
    }
}
