import SwiftUI
import UserNotifications
import BackgroundTasks
import NudgeCore

/// Shared object graph. Created once, before SwiftUI, so the notification delegate can reach it.
@MainActor
final class AppEnvironment {
    static let shared = AppEnvironment()
    let store: Store
    let settings: SettingsStore
    let engine: ReminderEngine

    private init() {
        store = Store()
        settings = SettingsStore()
        engine = ReminderEngine(store: store, settings: settings)
    }
}

@main
struct NudgeApp: App {
    @UIApplicationDelegateAdaptor(AppDelegate.self) private var delegate
    @Environment(\.scenePhase) private var scenePhase
    @StateObject private var engine = AppEnvironment.shared.engine
    @StateObject private var settings = AppEnvironment.shared.settings
    @StateObject private var store = AppEnvironment.shared.store

    var body: some Scene {
        WindowGroup {
            RootView()
                .environmentObject(engine)
                .environmentObject(settings)
                .environmentObject(store)
                .onOpenURL { url in engine.importFile(url) }
        }
        .onChange(of: scenePhase) { phase in
            switch phase {
            case .active:
                Task {
                    _ = await Notifier.requestPermission()
                    engine.refresh()
                }
            case .background:
                AppDelegate.scheduleBackgroundRefresh()
            default: break
            }
        }
    }
}

final class AppDelegate: NSObject, UIApplicationDelegate, UNUserNotificationCenterDelegate {
    static let refreshTaskId = "io.github.xalrk.nudge.refresh"

    func application(_ application: UIApplication, didFinishLaunchingWithOptions launchOptions: [UIApplication.LaunchOptionsKey: Any]? = nil) -> Bool {
        UNUserNotificationCenter.current().delegate = self
        Notifier.registerCategories()
        BGTaskScheduler.shared.register(forTaskWithIdentifier: AppDelegate.refreshTaskId, using: nil) { task in
            AppDelegate.handleBackgroundRefresh(task as! BGAppRefreshTask)
        }
        return true
    }

    func application(_ application: UIApplication, configurationForConnecting connectingSceneSession: UISceneSession, options: UIScene.ConnectionOptions) -> UISceneConfiguration {
        let config = UISceneConfiguration(name: nil, sessionRole: connectingSceneSession.role)
        config.delegateClass = SceneDelegate.self
        return config
    }

    // MARK: background refresh (best effort: iOS decides when, if ever, to run it)

    static func scheduleBackgroundRefresh() {
        let request = BGAppRefreshTaskRequest(identifier: refreshTaskId)
        request.earliestBeginDate = Date(timeIntervalSinceNow: 6 * 3600)
        try? BGTaskScheduler.shared.submit(request)
    }

    static func handleBackgroundRefresh(_ task: BGAppRefreshTask) {
        scheduleBackgroundRefresh()
        task.expirationHandler = { }
        Task { @MainActor in
            AppEnvironment.shared.engine.refresh()
            task.setTaskCompleted(success: true)
        }
    }

    // MARK: notification delegate

    /// Show banners even while the app is in the foreground, and book the delivery right away.
    func userNotificationCenter(_ center: UNUserNotificationCenter, willPresent notification: UNNotification) async -> UNNotificationPresentationOptions {
        await MainActor.run { AppEnvironment.shared.engine.refresh() }
        return [.banner, .list, .sound]
    }

    func userNotificationCenter(_ center: UNUserNotificationCenter, didReceive response: UNNotificationResponse) async {
        let info = response.notification.request.content.userInfo
        let id = (info[Notifier.keyReminderId] as? NSNumber)?.int64Value ?? -1
        let action = response.actionIdentifier
        await MainActor.run {
            let engine = AppEnvironment.shared.engine
            if id >= 0 && action != UNNotificationDefaultActionIdentifier && action != UNNotificationDismissActionIdentifier {
                engine.handleAction(action, reminderId: id)
            } else {
                engine.refresh()
            }
        }
    }
}

/// Routes home-screen quick actions ("New reminder", "Roll a random one").
final class SceneDelegate: NSObject, UIWindowSceneDelegate {
    func scene(_ scene: UIScene, willConnectTo session: UISceneSession, options connectionOptions: UIScene.ConnectionOptions) {
        if let item = connectionOptions.shortcutItem { SceneDelegate.handle(item) }
    }

    func windowScene(_ windowScene: UIWindowScene, performActionFor shortcutItem: UIApplicationShortcutItem, completionHandler: @escaping (Bool) -> Void) {
        SceneDelegate.handle(shortcutItem)
        completionHandler(true)
    }

    static func handle(_ item: UIApplicationShortcutItem) {
        Task { @MainActor in
            let engine = AppEnvironment.shared.engine
            switch item.type {
            case "new": engine.pendingAction = .newReminder
            case "roll": engine.fireRandomNow(); engine.pendingAction = .random
            default: break
            }
        }
    }
}
