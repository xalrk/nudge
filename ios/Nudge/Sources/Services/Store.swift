import Foundation
import NudgeCore

/// Everything the app persists, in one JSON file. Small enough that whole-file writes are fine.
struct AppState: Codable {
    var reminders: [Reminder] = []
    var events: [FiredEvent] = []
    /// The notifications currently handed to iOS, so a later launch can tell what was delivered.
    var plan: [PlannedFiring] = []
    var nextReminderId: Int64 = 1
    var nextEventId: Int64 = 1
}

@MainActor
final class Store: ObservableObject {
    @Published private(set) var state: AppState

    private let url: URL

    init() {
        let dir = FileManager.default.urls(for: .applicationSupportDirectory, in: .userDomainMask)[0].appendingPathComponent("Nudge", isDirectory: true)
        try? FileManager.default.createDirectory(at: dir, withIntermediateDirectories: true)
        url = dir.appendingPathComponent("state.json")
        if let data = try? Data(contentsOf: url), let s = try? JSONDecoder().decode(AppState.self, from: data) {
            state = s
        } else {
            state = AppState()
        }
    }

    func mutate(_ change: (inout AppState) -> Void) {
        change(&state)
        save()
    }

    private func save() {
        do {
            let data = try JSONEncoder().encode(state)
            try data.write(to: url, options: .atomic)
        } catch {
            NSLog("Nudge: could not save state: \(error)")
        }
    }
}
