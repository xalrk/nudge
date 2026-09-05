import SwiftUI
import NudgeCore

/// What the editor should open with.
struct EditRequest: Identifiable, Equatable {
    var id: Int64 = 0
    var kind: Kind = .scheduled
    var date: LocalDate? = nil
    /// For a repeating reminder, the occurrence that was tapped; enables this/following/all choices.
    var occurrence: LocalDate? = nil
    /// Unique so opening the same reminder twice in a row still presents.
    var token = UUID()
    static func == (a: EditRequest, b: EditRequest) -> Bool { a.token == b.token }
}

enum Tab: Hashable { case calendar, random, settings }

@MainActor
final class Router: ObservableObject {
    @Published var tab: Tab = .calendar
    @Published var edit: EditRequest?
    @Published var showTutorial = false
}

struct RootView: View {
    @EnvironmentObject private var engine: ReminderEngine
    @EnvironmentObject private var settings: SettingsStore
    @Environment(\.colorScheme) private var systemScheme
    @StateObject private var router = Router()
    @State private var visibleToast: Toast?
    @State private var toastTask: Task<Void, Never>?

    private var dark: Bool {
        switch settings.snapshot.themeMode {
        case .system: return systemScheme == .dark
        case .light: return false
        case .dark: return true
        }
    }

    var body: some View {
        let theme = Theme.make(accent: settings.snapshot.accentColor, dark: dark)
        TabView(selection: $router.tab) {
            NavigationStack { CalendarScreen() }
                .tabItem { Label("Calendar", systemImage: "calendar") }.tag(Tab.calendar)
            NavigationStack { RandomScreen() }
                .tabItem { Label("Random", systemImage: "shuffle") }.tag(Tab.random)
            NavigationStack { SettingsScreen() }
                .tabItem { Label("Settings", systemImage: "gearshape.fill") }.tag(Tab.settings)
        }
        .environment(\.theme, theme)
        .environmentObject(router)
        .tint(theme.primary)
        .preferredColorScheme(settings.snapshot.themeMode == .system ? nil : (dark ? .dark : .light))
        .overlay(alignment: .bottom) {
            if let t = visibleToast {
                ToastView(toast: t).padding(.bottom, 58).environment(\.theme, theme)
            }
        }
        .fullScreenCover(item: $router.edit) { req in
            EditReminderScreen(request: req)
                .environment(\.theme, theme)
                .environmentObject(router)
                .tint(theme.primary)
                .preferredColorScheme(settings.snapshot.themeMode == .system ? nil : (dark ? .dark : .light))
        }
        .fullScreenCover(isPresented: $router.showTutorial) {
            TutorialScreen { engine.setTutorialSeen(true); router.showTutorial = false }
                .environment(\.theme, theme)
                .tint(theme.primary)
                .preferredColorScheme(settings.snapshot.themeMode == .system ? nil : (dark ? .dark : .light))
        }
        .onAppear {
            if !settings.snapshot.tutorialSeen { router.showTutorial = true }
        }
        .onReceive(engine.$toast) { t in
            guard let t = t else { return }
            // Newest message replaces whatever is showing instead of queueing behind it.
            toastTask?.cancel()
            withAnimation(.easeOut(duration: 0.2)) { visibleToast = t }
            toastTask = Task {
                try? await Task.sleep(nanoseconds: t.brief ? 2_500_000_000 : 4_000_000_000)
                if !Task.isCancelled { withAnimation(.easeIn(duration: 0.2)) { visibleToast = nil } }
            }
        }
        .onReceive(engine.$pendingAction) { action in
            guard let action = action else { return }
            engine.pendingAction = nil
            switch action {
            case .newReminder: router.edit = EditRequest(kind: .scheduled)
            case .random: router.tab = .random
            }
        }
    }
}
