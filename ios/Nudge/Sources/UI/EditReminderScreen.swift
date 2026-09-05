import SwiftUI
import NudgeCore

/// Create or edit one reminder. Presented full screen; Cancel asks before discarding changes.
struct EditReminderScreen: View {
    @EnvironmentObject private var engine: ReminderEngine
    @EnvironmentObject private var store: Store
    @EnvironmentObject private var settings: SettingsStore
    @EnvironmentObject private var router: Router
    @Environment(\.theme) private var theme

    let request: EditRequest

    @State private var loaded = false
    @State private var title = ""
    @State private var body_ = ""
    @State private var kind: Kind = .scheduled
    @State private var date = LocalDate.today
    @State private var time = LocalTime(0, 0)
    @State private var repeatRule: Repeat = .none
    @State private var intervalText = "1"
    @State private var weekdays = Set<DayOfWeek>()
    @State private var endDate: LocalDate? = nil
    @State private var floating = true
    @State private var zoneId = TimeZone.current.identifier
    @State private var enabled = true
    @State private var color: Int32? = nil
    @State private var sound = true
    @State private var customRate = false
    @State private var customDays = false
    @State private var randomDays = Set<DayOfWeek>()
    @State private var rateSlider = SettingsMath.millisToSlider(SettingsMath.defaultMeanMillis)
    @State private var initial: Reminder? = nil
    @State private var confirmDelete = false
    @State private var confirmDiscard = false
    @State private var askScope: String? = nil // "save" or "delete"

    private var existing: Reminder? { store.state.reminders.first { $0.id == request.id } }
    private var isNew: Bool { request.id == 0 }
    private var canSave: Bool { !title.isBlank }
    private var isSeries: Bool { existing != nil && existing!.isScheduled && existing!.repeatRule != .none && request.occurrence != nil }
    private var s: SettingsSnapshot { settings.snapshot }

    private func load() {
        guard !loaded else { return }
        kind = request.kind
        date = request.date ?? LocalDate.today
        let now = LocalDateTime.now
        time = LocalTime((now.time.hour + 1) % 24, 0)
        if now.time.hour == 23 { date = date.plusDays(1) }
        rateSlider = SettingsMath.millisToSlider(s.meanIntervalMillis)
        randomDays = s.activeDaySet()
        if let e = existing {
            title = e.title; body_ = e.body; kind = e.kind
            if let ldt = e.localDateTimeOrNil() { date = request.occurrence ?? ldt.date; time = ldt.time }
            repeatRule = e.repeatRule; intervalText = String(e.interval)
            weekdays = e.weekdaySet(); endDate = e.endDateOrNil()
            floating = e.floating; zoneId = e.zoneId ?? TimeZone.current.identifier
            enabled = e.enabled; color = e.color; sound = e.sound
            customRate = e.meanOverrideMillis != nil
            customDays = e.isRandom && e.weekdays != 0
            randomDays = e.randomDaysOrNil() ?? s.activeDaySet()
            rateSlider = SettingsMath.millisToSlider(e.meanOverrideMillis ?? s.meanIntervalMillis)
        }
        loaded = true
        initial = signature()
    }

    private func build() -> Reminder {
        var r = existing ?? Reminder(title: "", kind: kind)
        r.title = title.trimmingCharacters(in: .whitespacesAndNewlines)
        r.body = body_.trimmingCharacters(in: .whitespacesAndNewlines)
        r.color = color; r.sound = sound; r.vibrate = true
        if kind == .random {
            let mean: Int64? = customRate ? SettingsMath.sliderToMillis(rateSlider) : nil
            let mask = (customDays && !randomDays.isEmpty) ? Reminder.maskOf(randomDays) : 0
            r.kind = .random; r.localDateTime = nil; r.zoneId = nil; r.repeatRule = .none; r.interval = 1; r.endDate = nil
            r.weekdays = mask; r.excludedDates = ""; r.enabled = enabled; r.meanOverrideMillis = mean
            // A changed rate or day set needs a fresh roll; otherwise keep the pending time.
            r.nextAt = (existing?.isRandom == true && existing!.meanOverrideMillis == mean && existing!.weekdays == mask) ? existing!.nextAt : nil
        } else {
            r.meanOverrideMillis = nil
            r.kind = .scheduled
            r.localDateTime = date.atTime(time).description
            r.zoneId = (existing?.zoneId != nil && !floating) ? existing!.zoneId : TimeZone.current.identifier
            r.floating = floating; r.repeatRule = repeatRule; r.interval = max(Int(intervalText) ?? 1, 1)
            r.weekdays = repeatRule == .weekly ? Reminder.maskOf(weekdays) : 0
            r.endDate = repeatRule == .none ? nil : endDate?.description
            r.excludedDates = repeatRule == .none ? "" : (existing?.excludedDates ?? "")
            r.enabled = true; r.nextAt = nil; r.snoozeAt = nil
        }
        return r
    }

    /// The editable fields only, so two builds compare equal when nothing the user can change differs.
    private func signature() -> Reminder {
        var r = build()
        r.id = 0; r.nextAt = nil; r.snoozeAt = nil; r.lastFiredAt = nil; r.createdAt = 0; r.dedupeKey = ""; r.zoneId = nil
        return r
    }

    private var dirty: Bool { loaded && initial != nil && signature() != initial }

    private func leave() { if dirty { confirmDiscard = true } else { router.edit = nil } }

    private func save() {
        if isSeries && kind == .scheduled && repeatRule != .none { askScope = "save" }
        else if engine.save(build()) { router.edit = nil }
    }

    var body: some View {
        NavigationStack {
            Screen {
                ScrollView {
                    VStack(alignment: .leading, spacing: 12) {
                        TextField("Message", text: $title).textFieldStyle(.roundedBorder)
                        TextField("Details (optional)", text: $body_, axis: .vertical).lineLimit(2...6).textFieldStyle(.roundedBorder)
                        Segmented(options: [(Kind.scheduled, "Scheduled"), (Kind.random, "Random")], selection: $kind)

                        if kind == .random { randomSection } else { scheduledSection }

                        Text("Notification").font(.subheadline.weight(.semibold)).padding(.top, 4)
                        Toggle("Sound", isOn: $sound)
                        Hint("Silent reminders still show a banner. iOS decides vibration from your sound settings.")
                        Text("Color").font(.body)
                        Hint("Shown on the calendar. \"A\" follows the app accent (its complementary color).")
                        SwatchRow(current: color, customColors: s.customColors, autoColor: Colors.complementary(s.accentColor),
                                  onPick: { color = $0 }, onAddCustom: { engine.addCustomColor($0) },
                                  onRemoveCustom: { removed in engine.removeCustomColor(removed); if color == removed { color = nil } })
                        Spacer(minLength: 24)
                    }
                    .padding(16)
                }
            }
            .navigationTitle(isNew ? "New Reminder" : "Edit Reminder")
            .navigationBarTitleDisplayMode(.inline)
            .toolbar {
                ToolbarItem(placement: .cancellationAction) { Button("Cancel") { leave() } }
                ToolbarItemGroup(placement: .confirmationAction) {
                    if !isNew {
                        Button { if isSeries { askScope = "delete" } else { confirmDelete = true } } label: { Image(systemName: "trash") }
                            .accessibilityLabel("Delete")
                    }
                    Button("Save") { save() }.disabled(!canSave).fontWeight(.semibold)
                }
            }
        }
        .interactiveDismissDisabled(dirty)
        .onAppear(perform: load)
        .alert("Discard changes?", isPresented: $confirmDiscard) {
            Button("Discard", role: .destructive) { router.edit = nil }
            Button("Keep editing", role: .cancel) { }
        } message: { Text("You have unsaved changes to this reminder.") }
        .alert("Delete reminder?", isPresented: $confirmDelete) {
            Button("Delete", role: .destructive) { engine.delete(request.id); router.edit = nil }
            Button("Cancel", role: .cancel) { }
        } message: { Text("\"\(title)\" will be removed.") }
        .confirmationDialog(askScope == "delete" ? "Delete repeating reminder" : "Change repeating reminder",
                            isPresented: Binding(get: { askScope != nil }, set: { if !$0 { askScope = nil } }), titleVisibility: .visible) {
            if let occ = request.occurrence, let orig = existing {
                let deleting = askScope == "delete"
                Button("Only this one (\(Fmt.date(occ)))") { runScope(.this, deleting: deleting, orig: orig, occ: occ) }
                Button("This and following") { runScope(.following, deleting: deleting, orig: orig, occ: occ) }
                Button("All occurrences", role: deleting ? ButtonRole.destructive : nil) { runScope(.all, deleting: deleting, orig: orig, occ: occ) }
                Button("Cancel", role: .cancel) { askScope = nil }
            }
        } message: { Text("This reminder repeats. Apply to:") }
    }

    private func runScope(_ scope: SeriesScope, deleting: Bool, orig: Reminder, occ: LocalDate) {
        askScope = nil
        if deleting { engine.deleteFromSeries(original: orig, occDate: occ, scope: scope); router.edit = nil }
        else if engine.editSeries(original: orig, edited: build(), occDate: occ, scope: scope) { router.edit = nil }
    }

    // MARK: sections

    @ViewBuilder private var randomSection: some View {
        Hint("Fires at a random moment during your active hours.")
        LabeledToggle(title: "Custom frequency",
                      subtitle: customRate ? SettingsMath.describeInterval(SettingsMath.sliderToMillis(rateSlider)).capitalizedFirst
                                           : "Uses the Settings default (\(SettingsMath.describeInterval(s.meanIntervalMillis)))",
                      subtitleAccent: customRate, isOn: $customRate)
        if customRate { Slider(value: $rateSlider, in: 0...1) }
        LabeledToggle(title: "Custom days", subtitle: customDays ? "Only on the selected days" : "Uses the Settings active days", subtitleAccent: customDays,
                      isOn: Binding(get: { customDays }, set: { on in customDays = on; if on && randomDays.isEmpty { randomDays = s.activeDaySet() } }))
        if customDays {
            DayCircles(selected: randomDays) { d in
                let next = randomDays.contains(d) ? randomDays.subtracting([d]) : randomDays.union([d])
                if !next.isEmpty { randomDays = next }
            }
        }
        if !isNew { Toggle("Enabled", isOn: $enabled) }
    }

    @ViewBuilder private var scheduledSection: some View {
        HStack {
            DatePicker("Date", selection: Binding(get: { date.foundationDate }, set: { date = LocalDate(foundationDate: $0) }), displayedComponents: .date).labelsHidden()
            Spacer()
            DatePicker("Time", selection: Binding(get: { time.foundationDate }, set: { time = LocalTime(foundationDate: $0) }), displayedComponents: .hourAndMinute).labelsHidden()
        }
        // Quick picks for the common "remind me soon" cases.
        let now = LocalDateTime.now
        let tonight: LocalDateTime = { let t = now.date.atTime(20, 0); return t.isAfter(now) ? t : t.plusDays(1) }()
        VStack(alignment: .leading, spacing: 6) {
            HStack(spacing: 8) {
                Chip("In 15 min") { pick(now.plusMinutes(15)) }
                Chip("In 1 hour") { pick(now.plusHours(1)) }
                Chip("Tonight 8 pm") { pick(tonight) }
            }
            HStack(spacing: 8) {
                Chip("Tomorrow 9 am") { pick(now.date.plusDays(1).atTime(9, 0)) }
                Chip("Next week") { pick(now.date.plusWeeks(1).atTime(time)) }
            }
        }

        Text("Repeat").font(.subheadline.weight(.semibold))
        Segmented(options: [(Repeat.none, "Once"), (.daily, "Day"), (.weekly, "Week"), (.monthly, "Month"), (.yearly, "Year")], selection: $repeatRule)
        if repeatRule != .none {
            HStack(spacing: 8) {
                Text("Every")
                TextField("1", text: Binding(get: { intervalText }, set: { intervalText = String($0.filter { $0.isNumber }.prefix(3)) }))
                    .keyboardType(.numberPad).textFieldStyle(.roundedBorder).frame(width: 70)
                let n = Int(intervalText) ?? 1
                Text(unitName + (n == 1 ? "" : "s"))
            }
            if repeatRule == .weekly {
                let effective: Set<DayOfWeek> = weekdays.isEmpty ? [date.dayOfWeek] : weekdays
                DayCircles(selected: effective) { d in
                    if weekdays.contains(d) { weekdays.remove(d) } else { weekdays.insert(d) }
                }
            }
            HStack {
                Text("Ends")
                Spacer()
                if let end = endDate {
                    DatePicker("Ends", selection: Binding(get: { end.foundationDate }, set: { endDate = LocalDate(foundationDate: $0) }), displayedComponents: .date).labelsHidden()
                    Button("Clear") { endDate = nil }
                } else {
                    OutlineButton(title: "Never") { endDate = date.plusMonths(1) }
                }
            }
        }

        LabeledToggle(title: "Follow device time zone",
                      subtitle: floating ? "Rings at \(Fmt.time(time)) local time wherever you are (\(TimeZone.current.identifier))"
                                         : "Pinned to \(zoneId); the moment stays fixed when you travel",
                      isOn: $floating)

        let preview = Recurrence.nextOccurrenceAfter(build(), Date())?.withZone(.device)
        Text(preview != nil ? "Next: \(Fmt.dayTimeYear(preview!)) · \(Recurrence.describe(build()))" : "That time is in the past; nothing will fire.")
            .font(.subheadline).foregroundColor(preview != nil ? theme.primary : theme.error)
    }

    private var unitName: String {
        switch repeatRule { case .daily: return "day"; case .weekly: return "week"; case .monthly: return "month"; case .yearly: return "year"; case .none: return "" }
    }

    private func pick(_ dt: LocalDateTime) { date = dt.date; time = LocalTime(dt.time.hour, dt.time.minute) }
}

private struct Chip: View {
    @Environment(\.theme) private var theme
    let label: String
    let action: () -> Void
    init(_ label: String, action: @escaping () -> Void) { self.label = label; self.action = action }
    var body: some View {
        Button(action: action) {
            Text(label).font(.footnote).foregroundColor(theme.onSurface)
                .padding(.horizontal, 12).padding(.vertical, 7)
                .overlay(RoundedRectangle(cornerRadius: 8).strokeBorder(theme.outline, lineWidth: 1))
        }
        .buttonStyle(.plain)
    }
}

extension String {
    var capitalizedFirst: String { prefix(1).uppercased() + dropFirst() }
}
