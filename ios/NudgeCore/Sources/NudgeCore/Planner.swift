import Foundation

/// One local notification iOS will deliver on its own, with or without the app running.
public struct PlannedFiring: Codable, Equatable {
    public var reminderId: Int64
    public var at: Int64
    public var kind: Kind
    public var title: String
    public var body: String
    public var sound: Bool
    public var isSnooze: Bool

    public init(reminderId: Int64, at: Int64, kind: Kind, title: String, body: String, sound: Bool, isSnooze: Bool = false) {
        self.reminderId = reminderId; self.at = at; self.kind = kind; self.title = title; self.body = body; self.sound = sound; self.isSnooze = isSnooze
    }

    /// Stable notification request identifier, so the same firing is never queued twice.
    public var identifier: String { "nudge-\(reminderId)-\(at)" + (isSnooze ? "-snooze" : "") }
}

/// Android keeps a single exact alarm and re-plans every time it fires. iOS has no such alarm:
/// an app may queue at most 64 pending local notifications and is not woken to add more.
/// So Nudge queues the next `limit` firings across every reminder, ordered by time, and
/// tops the queue up whenever it gets a chance to run (app opened, notification action,
/// background refresh). Scheduled occurrences are exact; random ones are drawn ahead of
/// time from the same memoryless process, which is statistically identical to drawing
/// them one at a time.
public enum Planner {
    public static let defaultLimit = 60

    public static func plan(reminders: [Reminder], settings: SettingsSnapshot, now: Date, limit: Int = defaultLimit,
                            horizonDays: Int = 400, zone: TimeZone = .device) -> [PlannedFiring] {
        var rng = SystemRandomNumberGenerator()
        return plan(reminders: reminders, settings: settings, now: now, limit: limit, horizonDays: horizonDays, zone: zone, using: &rng)
    }

    public static func plan<G: RandomNumberGenerator>(reminders: [Reminder], settings: SettingsSnapshot, now: Date, limit: Int = defaultLimit,
                                                      horizonDays: Int = 400, zone: TimeZone = .device, using rng: inout G) -> [PlannedFiring] {
        // Anything that would fire while everything is paused is skipped, not queued.
        let start = max(now, Date(epochMillis: settings.pausedUntil))
        let horizon = now.addingTimeInterval(Double(horizonDays) * 86400)
        let pool = reminders.filter { $0.isRandom && $0.enabled }.count
        var out: [PlannedFiring] = []

        for r in reminders where r.enabled {
            // Snoozes are one-off and independent of the rule.
            if let s = r.snoozeAt, s > now.epochMillis, s >= settings.pausedUntil {
                out.append(PlannedFiring(reminderId: r.id, at: s, kind: r.kind, title: r.title, body: r.body, sound: r.sound, isSnooze: true))
            }
            switch r.kind {
            case .scheduled:
                var cursor = start
                var count = 0
                while count < limit, let n = Recurrence.nextOccurrenceAfter(r, cursor), n.instant <= horizon {
                    out.append(PlannedFiring(reminderId: r.id, at: n.epochMillis, kind: .scheduled, title: r.title, body: r.body, sound: r.sound))
                    cursor = n.instant
                    count += 1
                }
            case .random:
                let days = r.randomDaysOrNil() ?? settings.activeDaySet()
                var t: ZonedDateTime
                if let next = r.nextAt, next > start.epochMillis {
                    t = Date(epochMillis: next).atZone(zone)
                } else {
                    t = RandomScheduler.sampleNext(from: start.atZone(zone), settings: settings, poolSize: pool, using: &rng,
                                                   overrideMean: r.meanOverrideMillis, overrideDays: r.randomDaysOrNil())
                }
                var count = 0
                while count < limit && t.instant <= horizon {
                    // Only deliver inside active hours, as Android does when it wakes up late.
                    if RandomScheduler.isInsideActiveWindow(t, settings.activeStartHour, settings.activeEndHour, days: days) {
                        out.append(PlannedFiring(reminderId: r.id, at: t.epochMillis, kind: .random, title: r.title, body: r.body, sound: r.sound))
                        count += 1
                    }
                    t = RandomScheduler.sampleNext(from: t, settings: settings, poolSize: pool, using: &rng,
                                                   overrideMean: r.meanOverrideMillis, overrideDays: r.randomDaysOrNil())
                }
            }
        }
        out.sort { a, b in a.at == b.at ? a.reminderId < b.reminderId : a.at < b.at }
        // Requests scheduled for the very same second collide on iOS only by identifier, which includes the id, so ties are fine.
        return Array(out.prefix(limit))
    }
}
