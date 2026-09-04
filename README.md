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
- **Import a list.** A CSV file with one reminder per row (a spreadsheet saved as
  CSV works). Duplicates are skipped, both within the file and against what is
  already stored. The info button next to Import shows the column layout in-app.
  You can also "Open with" / "Share to" Nudge from a file manager.
- **Export** everything as CSV, which re-imports cleanly.
- **Time-zone aware.** Reminders follow the device's wall clock by default, so a
  9:00 reminder rings at 9:00 wherever you are, and DST switches are handled. Flip
  "Follow device time zone" off to pin a reminder to the zone it was created in
  (useful for a flight departure). Everything is re-planned automatically when the
  zone or clock changes, after a reboot, and after an app update.
- **History on the calendar.** Delivered notifications stay on their day, greyed
  out with a check, so you can tell what already happened from what is coming.
- **Snooze** from the notification. Missed reminders (phone off) are delivered when
  the phone comes back, except random ones, which are quietly re-rolled so they
  never fire at night.
- **Update notices.** About once a day (online, battery not low) the app asks
  GitHub whether a newer release exists and, if so, posts a notification that
  opens the download page. Can be switched off in Settings; "Check now" is there too.
- **Flat light theme and true-black AMOLED dark theme.** Pick the accent from a
  set of presets or type any hex value; optional Material You accent on Android 12+.

## Battery

Nudge has no background service and never polls. It keeps exactly one alarm with
the system for the next due reminder; the phone wakes for a few milliseconds,
posts the notification, computes the next time, and goes back to sleep. Between
reminders the app uses no CPU at all.

If your phone's manufacturer kills background apps aggressively, set Nudge to
"Unrestricted" battery use (Settings → Reliability → Fix).

## Import format

CSV, one reminder per row, with this header on the first line:

```csv
title,details,date,time,repeat,every,weekdays,until,zone,follow_device_zone
Drink some water,,,,,,,,,
Call mom,,2026-09-14,18:00,weekly,1,sun,,,
Standup,,,09:30,weekly,1,mon;tue;wed;thu;fri,2026-12-19,,
Gym,"Bring a towel, water",,18:00,weekly,1,mon;wed;fri,,,
Pay rent,,2026-10-01,09:00,monthly,,,,,
Flight,,2026-10-20,06:30,,,,,America/Denver,no
Dentist,,2026-11-03,2:15pm,,,,,,
```

| column | meaning |
|---|---|
| `title` | the notification text (required) |
| `details` | longer text shown under the title |
| `date` | `YYYY-MM-DD`. Empty with a time set means the next such time |
| `time` | `HH:MM` or `2:15pm`. Defaults to 09:00 when a date is given |
| `repeat` | `daily`, `weekly`, `monthly`, `yearly`, `weekdays`, `weekends`. Empty = once |
| `every` | repeat every N days/weeks/months/years (default 1) |
| `weekdays` | for weekly repeats: `mon;tue;wed;thu;fri;sat;sun`, separated by `;` |
| `until` | `YYYY-MM-DD`, last day of a repeat |
| `zone` | IANA time zone such as `Europe/Berlin` (default: the device zone) |
| `follow_device_zone` | `yes` rings at the same wall-clock time anywhere; `no` pins the moment to `zone` |

- A row with no date and no time becomes a random reminder.
- Only `title` is required. With the header present, columns can be in any order
  and extra columns are ignored; without a header they are read in the order
  above (so a file with just one message per line is valid).
- Quote a cell in double quotes if it contains a comma. Semicolon-delimited CSV
  (as some spreadsheet locales export) is detected automatically.
- Rows starting with `#` are ignored.

JSON in the shape `{"reminders": [{"title": "...", "at": "2026-09-10T14:30", "repeat": "weekly", "weekdays": ["sun"]}]}` is also accepted.

A sample file is in [`samples/reminders.csv`](samples/reminders.csv).

## Building

```
./gradlew assembleDebug        # app/build/outputs/apk/debug/app-debug.apk
./gradlew testDebugUnitTest    # recurrence, random-window and CSV parser tests
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
