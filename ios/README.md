# Nudge for iOS

A native SwiftUI port of the Android app in the parent folder. Same features, same CSV
format, same scheduling rules; the Android app is untouched by anything in here.

**Installing it on a phone:** see [SIDELOAD.md](SIDELOAD.md) (written for non-technical people).

## Layout

| Path | What |
|---|---|
| `NudgeCore/` | Swift package with all logic: dates/zones, recurrence, random scheduler, CSV/JSON import-export, settings maths, colours, and the notification planner. Pure Foundation, so it builds and tests on Linux as well as macOS. |
| `Nudge/Sources/App` | App entry point, notification delegate, quick actions, background refresh. |
| `Nudge/Sources/Services` | JSON persistence, UserDefaults settings, `Notifier` (UNUserNotificationCenter), `ReminderEngine`. |
| `Nudge/Sources/UI` | SwiftUI screens: calendar, random list, editor, settings, all-reminders list, tutorial. |
| `project.yml` | XcodeGen spec; `Nudge.xcodeproj` is generated, not committed. |
| `../.github/workflows/ios.yml` | Builds the unsigned `.ipa` on a free macOS runner. |

## How scheduling works on iOS

Android keeps one exact alarm and re-plans when it fires. iOS has no equivalent: an app may
queue at most 64 local notifications and is not woken up to add more. So `Planner` computes
the next 60 firings across every reminder (exact occurrences for scheduled ones, draws from
the same memoryless process for random ones) and `Notifier.sync` hands them to iOS. The plan
is persisted; the next time the app runs, `ReminderEngine.refresh` treats every planned
firing whose time has passed as delivered (that is what fills the calendar history), recomputes
`nextAt` for every reminder, and queues the next batch. This happens on every foreground,
on every notification tap or snooze action, and opportunistically via `BGAppRefreshTask`.

Consequences worth knowing: if the phone is not opened for long enough to exhaust the queue,
reminders stop until it is opened again (Settings → Reliability shows the horizon); a
notification is booked as delivered even if the phone was off at that moment.

Not available on iOS and therefore absent: per-reminder vibration, notification colour,
exact-alarm and battery-optimisation switches, in-app update checks (there is a link to the
releases page instead), sharing text into the app (opening a CSV file works).

## Building

The `.ipa` is built by GitHub Actions on every push that touches `ios/` and attached to any
`v*` release, alongside the Android APK. To get one without a release, open the
[Actions](https://github.com/xalrk/nudge/actions/workflows/ios.yml) tab, pick the latest
run, and download the `nudge-ios-ipa` artifact.

On a Mac:

```
brew install xcodegen
cd ios && xcodegen generate && open Nudge.xcodeproj
```

Then pick your personal team under Signing & Capabilities and run on a plugged-in iPhone.
A free Apple ID works; Xcode signs it for 7 days, like AltStore does.

Core logic without a Mac (any OS with a Swift toolchain):

```
swift test --package-path ios/NudgeCore
```

## Sideloading from Linux

AltServer has no official Linux build, but the community port works:
<https://github.com/NyaMisty/AltServer-Linux>. Run it with `-p` to pair, then
`AltServer -u <udid> -a <apple-id> -p <password> nudge-ios.ipa`. It needs `usbmuxd`,
`libimobiledevice` and, on iOS 17+, a Developer Disk Image mounted via `pymobiledevice3`.
Sideloadly (Windows/macOS) is the least fiddly one-off alternative if a second machine is
around.

## Free-tier limits (Apple)

- Apps signed with a free Apple ID expire after 7 days; AltStore refreshes them.
- At most 3 sideloaded apps per device, 10 new app IDs per week per Apple ID.
- No push notifications, no iCloud, no App Groups. Nudge uses none of those.
