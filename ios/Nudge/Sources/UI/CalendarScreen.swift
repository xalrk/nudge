import SwiftUI
import NudgeCore

/// One entry on a calendar day: either a future occurrence of a rule, or a notification that was delivered.
struct Occurrence: Identifiable {
    let at: ZonedDateTime
    let title: String
    let body: String
    let reminderId: Int64
    let done: Bool
    let rule: String
    let color: Int32
    var eventId: Int64? = nil
    var id: String { "\(done)-\(reminderId)-\(at.epochMillis)-\(eventId ?? 0)" }
}

struct CalendarScreen: View {
    @EnvironmentObject private var engine: ReminderEngine
    @EnvironmentObject private var store: Store
    @EnvironmentObject private var settings: SettingsStore
    @EnvironmentObject private var router: Router
    @Environment(\.theme) private var theme

    @State private var month = YearMonth(of: LocalDate.today)
    @State private var selected = LocalDate.today
    @State private var orphan: Occurrence?
    @State private var showPauseUntil = false
    @State private var pauseDate = Date()

    private var s: SettingsSnapshot { settings.snapshot }

    /// Past = what was actually delivered (history), plus occurrences a reminder is known to have
    /// fired for before the history log existed; future = what the rules say will come.
    private func occurrences() -> [LocalDate: [Occurrence]] {
        let reminders = store.state.reminders
        let history = store.state.events
        let zone = TimeZone.device
        let from = month.atDay(1).atStartOfDay(zone)
        let to = month.plusMonths(1).atDay(1).atStartOfDay(zone)
        let now = Date().atZone(zone)
        let byId = Dictionary(uniqueKeysWithValues: reminders.map { ($0.id, $0) })
        var all: [Occurrence] = []
        for r in reminders where r.isScheduled && r.enabled {
            for occ in Recurrence.occurrencesBetween(r, max(from, now), to) {
                all.append(Occurrence(at: occ.withZone(zone), title: r.title, body: r.body, reminderId: r.id, done: false, rule: Recurrence.describe(r), color: engine.colorOf(r)))
            }
        }
        var loggedKeys = Set<String>()
        for e in history {
            let at = Fmt.instant(e.firedAt)
            if at.isBefore(from) || !at.isBefore(to) { continue }
            all.append(Occurrence(at: at, title: e.title, body: e.body, reminderId: e.reminderId, done: true,
                                  rule: e.kind == .random ? "Random" : "Delivered", color: Colors.faded(engine.colorOf(byId[e.reminderId])), eventId: e.id))
            loggedKeys.insert("\(e.reminderId)-\(at.localDate)")
        }
        for r in reminders where r.isScheduled && r.lastFiredAt != nil {
            let last = Fmt.instant(r.lastFiredAt!)
            for occ in Recurrence.occurrencesBetween(r, from, min(to, now)) where !occ.isAfter(last.plusMinutes(1)) && !loggedKeys.contains("\(r.id)-\(occ.localDate)") {
                all.append(Occurrence(at: occ.withZone(zone), title: r.title, body: r.body, reminderId: r.id, done: true, rule: "Delivered", color: Colors.faded(engine.colorOf(r))))
            }
        }
        return Dictionary(grouping: all, by: { $0.at.localDate })
    }

    var body: some View {
        let occ = occurrences()
        let dots = occ.mapValues { list in Array(list.sorted { $0.at < $1.at }.map { $0.color }.prefix(4)) }
        let dayList = (occ[selected] ?? []).sorted { $0.at < $1.at }
        let reminders = store.state.reminders
        Screen {
            ZStack(alignment: .bottomTrailing) {
                ScrollView {
                    LazyVStack(alignment: .leading, spacing: 0) {
                        PauseBanner(settings: s) { engine.setPausedUntil(0) }
                        HStack {
                            Button { month = month.minusMonths(1) } label: { Image(systemName: "chevron.left").padding(12) }.accessibilityLabel("Previous month")
                            Spacer()
                            Text(Fmt.month(month)).font(.headline)
                            Spacer()
                            Button { month = month.plusMonths(1) } label: { Image(systemName: "chevron.right").padding(12) }.accessibilityLabel("Next month")
                        }
                        .padding(.horizontal, 8)
                        MonthGrid(month: month, selected: selected, today: LocalDate.today, dots: dots) { selected = $0 }
                        Divider().padding(.top, 16)
                        Text(Fmt.date(selected)).font(.subheadline.weight(.medium)).foregroundColor(theme.onSurfaceVariant)
                            .padding(EdgeInsets(top: 12, leading: 16, bottom: 4, trailing: 16))
                        if dayList.isEmpty {
                            Text("Nothing scheduled this day.").foregroundColor(theme.onSurfaceVariant).padding(.horizontal, 16).padding(.vertical, 4)
                        }
                        ForEach(dayList) { o in
                            OccurrenceRow(occ: o) {
                                if reminders.contains(where: { $0.id == o.reminderId }) {
                                    router.edit = EditRequest(id: o.reminderId, occurrence: o.at.localDate)
                                } else if o.eventId != nil { orphan = o }
                            }
                        }
                        Color.clear.frame(height: 88)
                    }
                }
                Fab(systemImage: "plus", label: "Add reminder") { router.edit = EditRequest(kind: .scheduled, date: selected) }
            }
        }
        .navigationTitle("Nudge")
        .toolbar {
            ToolbarItemGroup(placement: .navigationBarTrailing) {
                Menu {
                    if s.isPaused() {
                        Button("Resume now") { engine.setPausedUntil(0) }
                        Text("Paused until " + Fmt.dayTime(s.pausedUntil))
                    } else {
                        Button("Pause for an hour") { engine.setPausedUntil(Date().epochMillis + 3_600_000) }
                        Button("Pause until tomorrow") { engine.setPausedUntil(morning(1)) }
                        Button("Pause for a week") { engine.setPausedUntil(morning(7)) }
                        Button("Pause until a date and time…") {
                            pauseDate = LocalDate.today.plusDays(1).atTime(s.activeStartHour, 0).atZone(.device).instant
                            showPauseUntil = true
                        }
                    }
                } label: {
                    Image(systemName: s.isPaused() ? "play.fill" : "pause")
                        .foregroundColor(s.isPaused() ? theme.primary : theme.onSurface)
                }
                .accessibilityLabel(s.isPaused() ? "Paused, tap to resume" : "Pause all reminders")
                NavigationLink { ListScreen() } label: { Image(systemName: "magnifyingglass") }
                    .accessibilityLabel("All reminders")
            }
        }
        .sheet(isPresented: $showPauseUntil) {
            NavigationStack {
                VStack {
                    DatePicker("Resume at", selection: $pauseDate, in: Date()..., displayedComponents: [.date, .hourAndMinute])
                        .datePickerStyle(.graphical)
                    Spacer()
                }
                .padding()
                .background(theme.background.ignoresSafeArea())
                .navigationTitle("Pause until")
                .navigationBarTitleDisplayMode(.inline)
                .toolbar {
                    ToolbarItem(placement: .cancellationAction) { Button("Cancel") { showPauseUntil = false } }
                    ToolbarItem(placement: .confirmationAction) {
                        Button("Pause") {
                            showPauseUntil = false
                            if pauseDate > Date() { engine.setPausedUntil(pauseDate.epochMillis) } else { engine.show("That time has already passed") }
                        }
                    }
                }
            }
            .environment(\.theme, theme)
            .tint(theme.primary)
        }
        .alert("Reminder no longer exists", isPresented: Binding(get: { orphan != nil }, set: { if !$0 { orphan = nil } }), presenting: orphan) { o in
            Button("Remove", role: .destructive) { if let e = o.eventId { engine.deleteHistoryEntry(e) }; orphan = nil }
            Button("Keep", role: .cancel) { orphan = nil }
        } message: { o in
            Text("\"\(o.title)\" was delivered on \(Fmt.dayTime(o.at.epochMillis)) but its reminder has since been deleted. Remove this entry from the calendar?")
        }
    }

    private func morning(_ daysAhead: Int) -> Int64 {
        LocalDate.today.plusDays(daysAhead).atStartOfDay(.device).plusHours(s.activeStartHour).epochMillis
    }
}

private struct OccurrenceRow: View {
    @Environment(\.theme) private var theme
    let occ: Occurrence
    let onTap: () -> Void
    var body: some View {
        Button(action: onTap) {
            HStack(spacing: 12) {
                ColorDot(argb: occ.color)
                VStack(alignment: .leading, spacing: 2) {
                    Text(occ.title).font(.body).strikethrough(occ.done).foregroundColor(occ.done ? theme.onSurfaceVariant : theme.onSurface).lineLimit(2)
                    if !occ.body.isBlank { Text(occ.body).font(.footnote).foregroundColor(theme.onSurfaceVariant).lineLimit(2) }
                    Text(Fmt.time(occ.at.instant) + " · " + occ.rule + (occ.done ? " ✓" : ""))
                        .font(.caption).foregroundColor(occ.done ? theme.onSurfaceVariant : theme.primary)
                }
                Spacer(minLength: 0)
            }
            .contentShape(Rectangle())
            .padding(.horizontal, 16).padding(.vertical, 10)
        }
        .buttonStyle(.plain)
    }
}

private struct MonthGrid: View {
    @Environment(\.theme) private var theme
    let month: YearMonth
    let selected: LocalDate
    let today: LocalDate
    let dots: [LocalDate: [Int32]]
    let onSelect: (LocalDate) -> Void

    var body: some View {
        let first = month.atDay(1)
        let lead = (first.dayOfWeek.rawValue - 1 + 7) % 7
        let cells = lead + month.lengthOfMonth
        let rows = (cells + 6) / 7
        VStack(spacing: 0) {
            HStack(spacing: 0) {
                ForEach(DayOfWeek.allCases, id: \.rawValue) { d in
                    Text(d.narrow).font(.caption2).foregroundColor(theme.onSurfaceVariant).frame(maxWidth: .infinity)
                }
            }
            ForEach(0..<rows, id: \.self) { row in
                HStack(spacing: 0) {
                    ForEach(0..<7, id: \.self) { col in
                        let idx = row * 7 + col - lead
                        Group {
                            if idx >= 0 && idx < month.lengthOfMonth {
                                let date = month.atDay(idx + 1)
                                DayCell(day: idx + 1, isSelected: date == selected, isToday: date == today, dots: dots[date] ?? []) { onSelect(date) }
                            } else {
                                Color.clear
                            }
                        }
                        .frame(maxWidth: .infinity).frame(height: 52)
                    }
                }
            }
        }
        .padding(.horizontal, 8)
    }
}

private struct DayCell: View {
    @Environment(\.theme) private var theme
    let day: Int
    let isSelected: Bool
    let isToday: Bool
    let dots: [Int32]
    let onTap: () -> Void

    var body: some View {
        Button(action: onTap) {
            VStack(spacing: 3) {
                // Number sits centred in its own squircle: filled when selected, outlined for today.
                Text("\(day)")
                    .font(.subheadline.weight(isToday || isSelected ? .semibold : .regular))
                    .foregroundColor(isSelected ? theme.onPrimary : (isToday ? theme.primary : theme.onSurface))
                    .frame(width: 32, height: 32)
                    .background(RoundedRectangle(cornerRadius: 10).fill(isSelected ? theme.primary : Color.clear))
                    .overlay(RoundedRectangle(cornerRadius: 10).strokeBorder(theme.primary, lineWidth: isToday && !isSelected ? 1.5 : 0))
                // Dots live below the highlight so their colors are never inverted: one per reminder (up to four).
                HStack(spacing: 3) {
                    ForEach(Array(dots.enumerated()), id: \.offset) { _, c in ColorDot(argb: c, size: 5) }
                }
                .frame(height: 5)
            }
            .padding(.top, 6)
            .frame(maxWidth: .infinity, maxHeight: .infinity, alignment: .top)
            .contentShape(Rectangle())
        }
        .buttonStyle(.plain)
    }
}
