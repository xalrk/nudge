# Nudge

A small, battery-friendly Android reminder app. Schedule notifications on a calendar
(one-off or repeating), or hand it a list of messages and let it surprise you with
them at random moments during the day.

**[Download the latest APK](https://github.com/xalrk/nudge/releases/latest)** · Android 8.0+ (tested on Android 14)

## What it does

- **Calendar reminders.** Pick a date and time, optionally repeat every N days,
  weeks (on chosen weekdays), months or years, with an optional end date.
- **Random reminders.** Anything without a time fires at an unpredictable moment
  inside your active hours (default 7 am – 11 pm, adjustable). The timing is a
  genuine random process (exponential gaps, so it is memoryless and never falls
  into a pattern); a slider in Settings sets the *average* rate, default once every
  two weeks per reminder. You can also apply the rate to the whole list instead of
  each item.
- **Import a list.** Plain text (one line per reminder) or JSON. Duplicates are
  skipped, both within the file and against what is already stored. You can also
  "Open with" / "Share to" Nudge from any file manager or notes app.
- **Export** everything as JSON, which re-imports cleanly.
- **Time-zone aware.** Reminders follow the device's wall clock by default, so a
  9:00 reminder rings at 9:00 wherever you are, and DST switches are handled. Flip
  "Follow device time zone" off to pin a reminder to the zone it was created in
  (useful for a flight departure). Everything is re-planned automatically when the
  zone or clock changes, after a reboot, and after an app update.
- **Snooze** from the notification. Missed reminders (phone off) are delivered when
  the phone comes back, except random ones, which are quietly re-rolled so they
  never fire at night.
- **Flat light theme and true-black AMOLED dark theme.** Optional Material You
  accent on Android 12+.

## Battery

Nudge has no background service and never polls. It keeps exactly one alarm with
the system for the next due reminder; the phone wakes for a few milliseconds,
posts the notification, computes the next time, and goes back to sleep. Between
reminders the app uses no CPU at all.

If your phone's manufacturer kills background apps aggressively, set Nudge to
"Unrestricted" battery use (Settings → Reliability → Fix).

## Import format

### Plain text

```
# comments and blank lines are ignored
Drink some water                       ← no "@": random reminder
Call mom @ 2026-09-14 18:00 every week
Stretch @ 09:00 every day              ← today if still ahead, else tomorrow
Standup @ 09:30 every weekday
Gym @ 18:00 every mon,wed,fri :: bring a towel
Pay rent @ 2026-10-01 09:00 every month
Sprint review @ 2026-09-08 10:00 every 2 weeks until 2026-12-19
Dentist @ 2026-11-03 2:15pm
```

- `@ <date> <time>` sets the first occurrence. Date is `YYYY-MM-DD`; time is
  `HH:MM` (24 h) or `h:MMam/pm`. Date alone defaults to 09:00; time alone means
  the next such time.
- `every [N] day|week|month|year`, `every weekday`, `every weekend`, or a list of
  weekdays such as `every mon,thu`.
- `until YYYY-MM-DD` ends a repeat.
- Text after `::` becomes the notification body.

### JSON

```json
{ "reminders": [
  { "title": "Drink some water" },
  { "title": "Call mom", "body": "", "at": "2026-09-14T18:00",
    "repeat": "weekly", "interval": 1, "weekdays": ["sun"],
    "until": "2026-12-31", "zone": "America/Denver", "floating": true }
]}
```

A bare array works too. Omit `at` for a random reminder. `repeat` is one of
`none`, `daily`, `weekly`, `monthly`, `yearly`.

Sample files are in [`samples/`](samples/).

## Building

```
./gradlew assembleDebug        # app/build/outputs/apk/debug/app-debug.apk
./gradlew testDebugUnitTest    # recurrence, random-window and parser tests
```

Requires JDK 17 and the Android SDK (platform 35). For a signed release build,
create `keystore.properties` in the project root:

```
storeFile=/path/to/release.jks
storePassword=...
keyAlias=nudge
keyPassword=...
```

or set `NUDGE_KEYSTORE_FILE`, `NUDGE_KEYSTORE_PASSWORD`, `NUDGE_KEY_ALIAS`,
`NUDGE_KEY_PASSWORD`. Pushing a `v*` tag builds and attaches the APK to a GitHub
release via the included workflow.

## How the random timing works

Each random reminder draws its next firing time from an exponential distribution
whose mean is the configured average interval, scaled by the fraction of the day
that is "active". The resulting gap is then laid over the calendar skipping the
hours outside the active window. This gives a Poisson process restricted to
waking hours: the average rate is exactly what the slider says, but the actual
moments are unpredictable. Changing the slider re-rolls all pending random
reminders, which is statistically indistinguishable from continuing the old
process because the distribution is memoryless.

## License

MIT
