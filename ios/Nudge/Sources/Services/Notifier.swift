import Foundation
import UserNotifications
import NudgeCore

/// Thin wrapper over UNUserNotificationCenter: permission, the snooze actions, and keeping the
/// pending queue equal to the engine's plan.
enum Notifier {
    static let category = "NUDGE_REMINDER"
    static let actionSnooze10 = "SNOOZE_10"
    static let actionSnooze60 = "SNOOZE_60"
    static let actionSnoozeMorning = "SNOOZE_MORNING"
    static let keyReminderId = "reminderId"
    static let keyAt = "at"
    static let keySnooze = "snooze"

    static func registerCategories() {
        let actions = [
            UNNotificationAction(identifier: actionSnooze10, title: "10 min", options: []),
            UNNotificationAction(identifier: actionSnooze60, title: "1 hour", options: []),
            UNNotificationAction(identifier: actionSnoozeMorning, title: "Tomorrow", options: []),
        ]
        let cat = UNNotificationCategory(identifier: category, actions: actions, intentIdentifiers: [], options: [])
        UNUserNotificationCenter.current().setNotificationCategories([cat])
    }

    static func requestPermission() async -> Bool {
        (try? await UNUserNotificationCenter.current().requestAuthorization(options: [.alert, .sound, .badge])) ?? false
    }

    static func authorizationStatus() async -> UNAuthorizationStatus {
        await UNUserNotificationCenter.current().notificationSettings().authorizationStatus
    }

    private static func content(title: String, body: String, sound: Bool, reminderId: Int64, at: Int64, snooze: Bool) -> UNMutableNotificationContent {
        let c = UNMutableNotificationContent()
        c.title = title
        if !body.isEmpty { c.body = body }
        c.sound = sound ? .default : nil
        c.categoryIdentifier = category
        c.threadIdentifier = "nudge"
        c.userInfo = [keyReminderId: NSNumber(value: reminderId), keyAt: NSNumber(value: at), keySnooze: snooze]
        return c
    }

    /// Makes the pending queue match `plan`: removes what is no longer planned, adds what is new.
    static func sync(plan: [PlannedFiring]) async {
        let center = UNUserNotificationCenter.current()
        let pending = await center.pendingNotificationRequests()
        let wanted = Dictionary(uniqueKeysWithValues: plan.map { ($0.identifier, $0) })
        let have = Set(pending.map { $0.identifier })
        let stale = pending.map { $0.identifier }.filter { wanted[$0] == nil }
        if !stale.isEmpty { center.removePendingNotificationRequests(withIdentifiers: stale) }
        let now = Date()
        for f in plan where !have.contains(f.identifier) {
            let fireDate = Date(epochMillis: f.at)
            guard fireDate > now else { continue }
            var comps = Calendar.current.dateComponents(in: TimeZone.current, from: fireDate)
            comps = DateComponents(timeZone: TimeZone.current, year: comps.year, month: comps.month, day: comps.day, hour: comps.hour, minute: comps.minute, second: comps.second)
            let trigger = UNCalendarNotificationTrigger(dateMatching: comps, repeats: false)
            let req = UNNotificationRequest(identifier: f.identifier,
                                            content: content(title: f.title, body: f.body, sound: f.sound, reminderId: f.reminderId, at: f.at, snooze: f.isSnooze),
                                            trigger: trigger)
            try? await center.add(req)
        }
    }

    /// Posts a notification right away (test button, "fire a random one", shortcuts).
    static func showNow(_ r: Reminder) async {
        let at = Date().epochMillis
        let req = UNNotificationRequest(identifier: "nudge-now-\(r.id)-\(at)",
                                        content: content(title: r.title, body: r.body, sound: r.sound, reminderId: r.id, at: at, snooze: false),
                                        trigger: nil)
        try? await UNUserNotificationCenter.current().add(req)
    }

    /// Clears delivered banners for a reminder (after a snooze or delete).
    static func clearDelivered(reminderId: Int64) async {
        let center = UNUserNotificationCenter.current()
        let delivered = await center.deliveredNotifications()
        let ids = delivered.filter { ($0.request.content.userInfo[keyReminderId] as? NSNumber)?.int64Value == reminderId }.map { $0.request.identifier }
        if !ids.isEmpty { center.removeDeliveredNotifications(withIdentifiers: ids) }
    }

    static func removeAllPending() {
        UNUserNotificationCenter.current().removeAllPendingNotificationRequests()
    }
}
