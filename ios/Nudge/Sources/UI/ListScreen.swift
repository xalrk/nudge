import SwiftUI
import NudgeCore

/// Every reminder in one searchable list: scheduled first by next time, then random.
struct ListScreen: View {
    @EnvironmentObject private var engine: ReminderEngine
    @EnvironmentObject private var store: Store
    @EnvironmentObject private var router: Router
    @Environment(\.theme) private var theme
    @State private var query = ""

    var body: some View {
        let reminders = store.state.reminders
        let q = query.trimmingCharacters(in: .whitespaces).lowercased()
        let shown = reminders.filter { q.isEmpty || $0.title.lowercased().contains(q) || $0.body.lowercased().contains(q) }
            .sorted { a, b in
                if a.isRandom != b.isRandom { return !a.isRandom }
                if a.enabled != b.enabled { return a.enabled }
                let na = a.nextAt ?? Int64.max, nb = b.nextAt ?? Int64.max
                if na != nb { return na < nb }
                return a.title.lowercased() < b.title.lowercased()
            }
        Screen {
            ScrollView {
                LazyVStack(alignment: .leading, spacing: 0) {
                    Text("\(shown.count) of \(reminders.count)").font(.caption).foregroundColor(theme.onSurfaceVariant)
                        .padding(.horizontal, 16).padding(.vertical, 4)
                    ForEach(shown) { r in
                        let sub: String = {
                            if !r.enabled { return r.isScheduled ? "Off · " + Recurrence.describe(r) : "Paused · Random" }
                            if r.isScheduled { return (r.nextAt.map { Fmt.dayTime($0) + " · " } ?? "") + Recurrence.describe(r) }
                            return "Random" + (r.meanOverrideMillis.map { " · " + SettingsMath.describeInterval($0) } ?? "")
                        }()
                        ReminderRow(r: r, subtitle: sub, color: engine.colorOf(r), onTap: { router.edit = EditRequest(id: r.id) }, onToggle: { engine.setEnabled(r.id, $0) })
                    }
                    Color.clear.frame(height: 24)
                }
            }
        }
        .navigationTitle("All Reminders")
        .navigationBarTitleDisplayMode(.inline)
        .searchable(text: $query, placement: .navigationBarDrawer(displayMode: .always), prompt: "Search")
    }
}
