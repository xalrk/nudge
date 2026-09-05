import Foundation
import NudgeCore

struct Toast: Identifiable, Equatable {
    let id = UUID()
    let text: String
    /// Brief toasts hold for 2.5 s instead of 4 s.
    let brief: Bool
}

enum PendingAction: Equatable { case newReminder, random }

enum SeriesScope { case this, following, all }

/// Single place that decides *when* reminders fire, mirroring the Android ReminderEngine.
/// Every change ends in `replan()`, which hands the next batch of firings to iOS.
@MainActor
final class ReminderEngine: ObservableObject {
    let store: Store
    let settings: SettingsStore

    @Published var toast: Toast?
    @Published var pendingAction: PendingAction?

    private var syncTask: Task<Void, Never>?
    private var rerollNotice: Task<Void, Never>?
    private static let historyMillis: Int64 = 400 * 24 * 3_600_000

    init(store: Store, settings: SettingsStore) {
        self.store = store
        self.settings = settings
    }

    var reminders: [Reminder] { store.state.reminders }
    var events: [FiredEvent] { store.state.events }
    private var s: SettingsSnapshot { settings.snapshot }

    func reminder(_ id: Int64) -> Reminder? { reminders.first { $0.id == id } }

    /// The color a reminder's calendar dot uses.
    func colorOf(_ r: Reminder?) -> Int32 { r?.color ?? Colors.complementary(s.accentColor) }

    func show(_ text: String, brief: Bool = false) { toast = Toast(text: text, brief: brief) }

    // ------------------------------------------------------------------ saving

    /// Compute nextAt for a new/edited reminder and persist it. Returns false (with a toast) on a duplicate.
    @discardableResult
    func save(_ reminder: Reminder) -> Bool {
        let keyed = reminder.withDedupeKey()
        if reminders.contains(where: { $0.id != keyed.id && $0.dedupeKey == keyed.dedupeKey }) {
            show("An identical reminder already exists"); return false
        }
        let now = Date()
        let pool = countEnabledRandom() + ((keyed.isRandom && keyed.enabled && keyed.id == 0) ? 1 : 0)
        let prepared = prepare(keyed, pool: pool, now: now)
        store.mutate { st in
            if prepared.id == 0 {
                var r = prepared; r.id = st.nextReminderId; st.nextReminderId += 1
                st.reminders.append(r)
            } else if let i = st.reminders.firstIndex(where: { $0.id == prepared.id }) {
                st.reminders[i] = prepared
            } else {
                st.reminders.append(prepared)
            }
        }
        if keyed.isRandom && s.frequencyMode == .wholePool { resampleAllRandom() }
        replan()
        return true
    }

    /// Insert many (import). Returns (inserted, skippedDuplicates).
    func importAll(_ list: [Reminder]) -> (Int, Int) {
        let now = Date()
        var pool = countEnabledRandom()
        var inserted = 0, skipped = 0
        var seen = Set(reminders.map { $0.dedupeKey })
        store.mutate { st in
            for r in list {
                let keyed = r.withDedupeKey()
                if !seen.insert(keyed.dedupeKey).inserted { skipped += 1; continue }
                if keyed.isRandom && keyed.enabled { pool += 1 }
                var p = self.prepare(keyed, pool: pool, now: now)
                p.id = st.nextReminderId; st.nextReminderId += 1
                st.reminders.append(p)
                inserted += 1
            }
        }
        if s.frequencyMode == .wholePool { resampleAllRandom() }
        replan()
        return (inserted, skipped)
    }

    /// Apply an edit to a repeating series.
    /// this: the occurrence on occDate is removed from the series and `edited` becomes a standalone one-off.
    /// following: the series ends the day before occDate; `edited` starts a new series.
    /// all: `edited` replaces the series, keeping its original start date shifted by the same
    ///      number of days the user moved the occurrence.
    @discardableResult
    func editSeries(original: Reminder, edited: Reminder, occDate: LocalDate, scope: SeriesScope) -> Bool {
        let now = Date()
        guard let editedStart = edited.localDateTimeOrNil() else { return false }
        var toInsert: Reminder? = nil
        var toUpdate: Reminder? = nil
        switch scope {
        case .this:
            toUpdate = prepare(original.withExcluded(occDate).withDedupeKey(), pool: 0, now: now)
            var single = edited
            single.id = 0; single.repeatRule = .none; single.interval = 1; single.weekdays = 0; single.endDate = nil; single.excludedDates = ""
            single.nextAt = nil; single.snoozeAt = nil; single.lastFiredAt = nil; single.createdAt = now.epochMillis
            toInsert = prepare(single.withDedupeKey(), pool: 0, now: now)
        case .following:
            let seriesStart = original.localDateTimeOrNil()?.date
            if seriesStart == nil || !occDate.isAfter(seriesStart!) {
                // Editing from the first occurrence: same as changing everything.
                var all = edited; all.id = original.id
                toUpdate = prepare(all.withDedupeKey(), pool: 0, now: now)
            } else {
                let cutoff = occDate.minusDays(1)
                let end = original.endDateOrNil().map { $0.isBefore(cutoff) ? $0 : cutoff } ?? cutoff
                var trimmed = original; trimmed.endDate = end.description
                toUpdate = prepare(trimmed.withDedupeKey(), pool: 0, now: now)
                let keep = original.excludedDateSet().filter { !$0.isBefore(editedStart.date) }.sorted().map { $0.description }.joined(separator: ",")
                var next = edited
                next.id = 0; next.excludedDates = keep; next.nextAt = nil; next.snoozeAt = nil; next.lastFiredAt = nil; next.createdAt = now.epochMillis
                toInsert = prepare(next.withDedupeKey(), pool: 0, now: now)
            }
        case .all:
            let origStart = original.localDateTimeOrNil()
            let shift = LocalDate.daysBetween(occDate, editedStart.date)
            let newStart = (origStart?.date.plusDays(shift) ?? editedStart.date).atTime(editedStart.time)
            var all = edited; all.id = original.id; all.localDateTime = newStart.description; all.excludedDates = original.excludedDates
            toUpdate = prepare(all.withDedupeKey(), pool: 0, now: now)
        }
        // Uniqueness, as the database index enforces on Android.
        for cand in [toInsert, toUpdate].compactMap({ $0 }) {
            if reminders.contains(where: { $0.id != cand.id && $0.dedupeKey == cand.dedupeKey }) || (toInsert != nil && toUpdate != nil && toInsert!.dedupeKey == toUpdate!.dedupeKey) {
                show("An identical reminder already exists"); return false
            }
        }
        store.mutate { st in
            if let u = toUpdate, let i = st.reminders.firstIndex(where: { $0.id == u.id }) { st.reminders[i] = u }
            if var n = toInsert { n.id = st.nextReminderId; st.nextReminderId += 1; st.reminders.append(n) }
        }
        replan()
        return true
    }

    func deleteFromSeries(original: Reminder, occDate: LocalDate, scope: SeriesScope) {
        let now = Date()
        let zone = TimeZone.device
        let dayStart = occDate.atStartOfDay(zone).epochMillis
        let dayEnd = occDate.plusDays(1).atStartOfDay(zone).epochMillis
        switch scope {
        case .this:
            let u = prepare(original.withExcluded(occDate).withDedupeKey(), pool: 0, now: now)
            store.mutate { st in
                if let i = st.reminders.firstIndex(where: { $0.id == u.id }) { st.reminders[i] = u }
                st.events.removeAll { $0.reminderId == original.id && $0.firedAt >= dayStart && $0.firedAt < dayEnd }
            }
        case .following:
            let seriesStart = original.localDateTimeOrNil()?.date
            if seriesStart == nil || !occDate.isAfter(seriesStart!) { removeReminder(original.id) }
            else {
                var trimmed = original; trimmed.endDate = occDate.minusDays(1).description
                let u = prepare(trimmed.withDedupeKey(), pool: 0, now: now)
                store.mutate { st in
                    if let i = st.reminders.firstIndex(where: { $0.id == u.id }) { st.reminders[i] = u }
                    st.events.removeAll { $0.reminderId == original.id && $0.firedAt >= dayStart }
                }
            }
        case .all: removeReminder(original.id)
        }
        replan()
    }

    /// Deletes the reminder, its notification, and its delivery history (so it leaves the calendar too).
    private func removeReminder(_ id: Int64) {
        store.mutate { st in
            st.reminders.removeAll { $0.id == id }
            st.events.removeAll { $0.reminderId == id }
        }
        Task { await Notifier.clearDelivered(reminderId: id) }
    }

    func delete(_ id: Int64) {
        removeReminder(id)
        replan()
    }

    func setEnabled(_ id: Int64, _ enabled: Bool) {
        guard let r = reminder(id) else { return }
        let pool = countEnabledRandom()
        var updated = r
        if enabled {
            updated.enabled = true; updated.nextAt = nil; updated.snoozeAt = nil
            updated = prepare(updated, pool: pool + 1, now: Date())
        } else {
            updated.enabled = false; updated.nextAt = nil; updated.snoozeAt = nil
        }
        let u = updated
        store.mutate { st in if let i = st.reminders.firstIndex(where: { $0.id == id }) { st.reminders[i] = u } }
        if r.isRandom && s.frequencyMode == .wholePool { resampleAllRandom() }
        replan()
    }

    // ----------------------------------------------------------------- catching up

    /// Called whenever the app gets to run: books what iOS delivered meanwhile, recomputes every
    /// nextAt, drops stale history, and re-plans. The iOS counterpart of Android's refresh + fireDue.
    func refresh(resampleRandom: Bool = false) {
        let now = Date()
        reconcile(now: now)
        let nowMs = now.epochMillis
        let pool = countEnabledRandom()
        store.mutate { st in
            for i in st.reminders.indices {
                var r = st.reminders[i]
                if let sn = r.snoozeAt, sn <= nowMs { r.snoozeAt = nil }
                if !r.enabled { r.nextAt = nil; st.reminders[i] = r; continue }
                switch r.kind {
                case .scheduled:
                    if let next = Recurrence.nextOccurrenceAfter(r, now) { r.nextAt = next.epochMillis }
                    else { r.enabled = false; r.nextAt = nil; r.snoozeAt = nil } // series over or data unusable
                case .random:
                    if resampleRandom || r.nextAt == nil || r.nextAt! <= nowMs { r.nextAt = self.sampleRandom(r, pool: pool, now: now) }
                }
                st.reminders[i] = r
            }
            let ids = Set(st.reminders.map { $0.id })
            st.events.removeAll { $0.firedAt < nowMs - ReminderEngine.historyMillis || !ids.contains($0.reminderId) }
        }
        replan()
    }

    /// Everything in the last plan whose time has passed was delivered by iOS: record it.
    private func reconcile(now: Date) {
        let nowMs = now.epochMillis
        let fired = store.state.plan.filter { $0.at <= nowMs }
        guard !fired.isEmpty else { return }
        store.mutate { st in
            for f in fired {
                guard let i = st.reminders.firstIndex(where: { $0.id == f.reminderId }) else { continue }
                if !st.events.contains(where: { $0.reminderId == f.reminderId && $0.firedAt == f.at }) {
                    st.events.append(FiredEvent(id: st.nextEventId, reminderId: f.reminderId, title: f.title, body: f.body, kind: f.kind, firedAt: f.at))
                    st.nextEventId += 1
                }
                var r = st.reminders[i]
                r.lastFiredAt = max(r.lastFiredAt ?? 0, f.at)
                if f.isSnooze {
                    if r.snoozeAt == f.at { r.snoozeAt = nil }
                } else if r.isScheduled {
                    if r.repeatRule == .none { r.enabled = false; r.nextAt = nil }
                } else if r.nextAt == f.at {
                    r.nextAt = nil
                }
                st.reminders[i] = r
            }
            st.plan.removeAll { $0.at <= nowMs }
        }
    }

    /// Hands the next batch of firings to iOS.
    func replan() {
        let plan = Planner.plan(reminders: reminders, settings: s, now: Date())
        store.mutate { $0.plan = plan }
        let previous = syncTask
        syncTask = Task {
            await previous?.value
            await Notifier.sync(plan: plan)
        }
    }

    func deleteHistoryEntry(_ eventId: Int64) {
        store.mutate { $0.events.removeAll { $0.id == eventId } }
    }

    // ------------------------------------------------------------------ snooze

    func snooze(_ id: Int64, minutes: Int = 10) {
        snoozeUntil(id, Date().epochMillis + Int64(minutes) * 60_000)
    }

    /// Snooze until the start of the next active window (tomorrow morning, or later today if before it).
    func snoozeUntilMorning(_ id: Int64) {
        let now = Date().atZone(.device)
        var target = now.localDate.atStartOfDay(now.zone).plusHours(s.activeStartHour)
        if !target.isAfter(now.plusMinutes(1)) { target = target.plusDays(1) }
        snoozeUntil(id, target.epochMillis)
    }

    func snoozeUntil(_ id: Int64, _ at: Int64) {
        guard reminder(id) != nil else { return }
        store.mutate { st in
            if let i = st.reminders.firstIndex(where: { $0.id == id }) { st.reminders[i].snoozeAt = at; st.reminders[i].enabled = true }
        }
        Task { await Notifier.clearDelivered(reminderId: id) }
        replan()
    }

    /// Mute everything until untilMillis (0 clears). Random reminders are re-rolled so they land after the pause.
    func setPausedUntil(_ untilMillis: Int64) {
        settings.pausedUntil = untilMillis
        resampleAllRandom()
        replan()
        show(untilMillis > Date().epochMillis ? "Paused until \(Fmt.dayTime(untilMillis))" : "Reminders resumed")
    }

    /// Fire one random reminder right now.
    func fireRandomNow() {
        let pool = reminders.filter { $0.isRandom && $0.enabled }
        guard let pick = pool.randomElement() else { show("No enabled random reminders yet"); return }
        let now = Date().epochMillis
        store.mutate { st in
            st.events.append(FiredEvent(id: st.nextEventId, reminderId: pick.id, title: pick.title, body: pick.body, kind: pick.kind, firedAt: now))
            st.nextEventId += 1
            if let i = st.reminders.firstIndex(where: { $0.id == pick.id }) { st.reminders[i].lastFiredAt = now }
        }
        Task { await Notifier.showNow(pick) }
    }

    func testNotification() {
        Task { await Notifier.showNow(Reminder(id: Int64(Int32.max), title: "Nudge test", body: "Notifications are working.", kind: .random)) }
    }

    /// Re-rolls immediately; the confirmation appears once, shortly after the tapping stops, and fades fast.
    func rerollRandom() {
        resampleAllRandom()
        replan()
        rerollNotice?.cancel()
        rerollNotice = Task {
            try? await Task.sleep(nanoseconds: 400_000_000)
            if !Task.isCancelled { show("Random reminders re-rolled", brief: true) }
        }
    }

    private func resampleAllRandom() {
        let now = Date()
        let pool = reminders.filter { $0.isRandom && $0.enabled }
        store.mutate { st in
            for i in st.reminders.indices where st.reminders[i].isRandom && st.reminders[i].enabled {
                st.reminders[i].nextAt = self.sampleRandom(st.reminders[i], pool: pool.count, now: now)
            }
        }
    }

    private func countEnabledRandom() -> Int { reminders.filter { $0.isRandom && $0.enabled }.count }

    private func prepare(_ r0: Reminder, pool: Int, now: Date) -> Reminder {
        var r = r0
        if !r.enabled { r.nextAt = nil; return r }
        switch r.kind {
        case .scheduled:
            let next = Recurrence.nextOccurrenceAfter(r, now)?.epochMillis
            r.nextAt = next; r.enabled = next != nil
        case .random:
            r.nextAt = r.nextAt ?? sampleRandom(r, pool: pool, now: now)
            r.localDateTime = nil; r.repeatRule = .none
        }
        return r
    }

    private func sampleRandom(_ r: Reminder, pool: Int, now: Date) -> Int64 {
        RandomScheduler.sampleNext(from: now.atZone(.device), settings: s, poolSize: pool,
                                   overrideMean: r.meanOverrideMillis, overrideDays: r.randomDaysOrNil()).epochMillis
    }

    // ---------------------------------------------------------------- settings

    func setMeanInterval(_ ms: Int64) { settings.meanIntervalMillis = ms; resampleAllRandom(); replan() }
    func setFrequencyMode(_ m: FrequencyMode) { settings.frequencyMode = m; resampleAllRandom(); replan() }
    func setActiveWindow(start: Int, end: Int) { settings.activeStartHour = start; settings.activeEndHour = end; resampleAllRandom(); replan() }
    func setActiveDays(_ mask: Int) { settings.activeDays = mask; resampleAllRandom(); replan() }
    func setShowNextRandom(_ on: Bool) { settings.showNextRandomTime = on }
    func setTutorialSeen(_ seen: Bool) { settings.tutorialSeen = seen }
    func setThemeMode(_ m: ThemeMode) { settings.themeMode = m }
    func setAccentColor(_ argb: Int32) { settings.accentColor = argb }
    func addCustomColor(_ argb: Int32) { settings.customColors = settings.customColors + [argb] }
    func removeCustomColor(_ argb: Int32) {
        settings.customColors = settings.customColors.filter { $0 != argb }
        // Removing the swatch that is the current accent falls back to the first preset.
        if settings.accentColor == argb { settings.accentColor = SettingsMath.accentPresets[0].argb }
    }

    // ------------------------------------------------------------ import / export

    func importText(_ text: String) {
        let parsed = ImportExport.parse(text)
        if parsed.reminders.isEmpty && !parsed.errors.isEmpty {
            show("Nothing imported. \(parsed.errors[0])"); return
        }
        let (inserted, skipped) = importAll(parsed.reminders)
        var parts = ["Imported \(inserted)"]
        if skipped > 0 { parts.append("skipped \(skipped) duplicate\(skipped == 1 ? "" : "s")") }
        if !parsed.errors.isEmpty { parts.append("\(parsed.errors.count) row\(parsed.errors.count == 1 ? "" : "s") not understood (\(parsed.errors[0]))") }
        show(parts.joined(separator: ", "))
    }

    func importFile(_ url: URL) {
        let secured = url.startAccessingSecurityScopedResource()
        defer { if secured { url.stopAccessingSecurityScopedResource() } }
        guard let data = try? Data(contentsOf: url), let text = String(data: data, encoding: .utf8) ?? String(data: data, encoding: .isoLatin1) else {
            show("Could not read that file"); return
        }
        importText(text)
    }

    func exportCsv() -> String { ImportExport.toCsv(reminders) }

    // ------------------------------------------------------------ notifications

    /// A snooze button on a notification, possibly with the app not running.
    func handleAction(_ identifier: String, reminderId: Int64) {
        switch identifier {
        case Notifier.actionSnooze10: snooze(reminderId, minutes: 10)
        case Notifier.actionSnooze60: snooze(reminderId, minutes: 60)
        case Notifier.actionSnoozeMorning: snoozeUntilMorning(reminderId)
        default: refresh()
        }
    }
}
