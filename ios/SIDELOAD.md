# Installing Nudge on an iPhone (no App Store)

Nudge for iPhone is not in the App Store. You install it yourself with a free tool called
**AltStore**, which uses your own Apple ID to put the app on your phone. It takes about
15 minutes the first time. After that it mostly looks after itself.

There is nothing to pay and no account to create beyond the Apple ID you already have.

## What you need

- An iPhone running iOS 16 or newer.
- A Windows PC or a Mac, and the cable you charge the phone with.
- Your Apple ID email and password (the one you use for iCloud / the App Store).
- The Nudge app file: `nudge-ios-….ipa`, downloaded from
  <https://github.com/xalrk/nudge/releases/latest>. Save it somewhere easy to find, such as
  your Downloads folder. If your browser complains about the file, keep it anyway; it is a
  plain zip file with the app inside.

## Part 1: put AltStore on your phone (one time)

1. On the computer, go to <https://altstore.io> and download **AltServer** for Windows or
   Mac. Install it. On Windows it also asks you to install iTunes and iCloud from Apple's
   website (not the Microsoft Store versions); do that, then restart the computer.
2. Plug the iPhone into the computer with the cable. If the phone asks "Trust This
   Computer?", tap **Trust** and enter your passcode.
3. Start AltServer. It has no window of its own: look for its icon in the menu bar (Mac,
   top right) or the system tray (Windows, bottom right, you may need to click the small
   up-arrow).
4. Click the AltServer icon, choose **Install AltStore**, then choose your iPhone.
5. Type in your Apple ID email and password. AltServer sends these straight to Apple to
   register your phone; they are not stored anywhere else. If your Apple ID has two-factor
   authentication (most do), you may get a code on your phone to type in.
6. After a minute, an app called **AltStore** appears on the iPhone. Do not open it yet.

## Part 2: tell the phone to trust the app (one time)

1. On the iPhone, open **Settings → General → VPN & Device Management**.
2. Under "Developer App", tap your Apple ID email, then tap **Trust**, then **Trust** again.
3. Go to **Settings → Privacy & Security**, scroll to the bottom, and turn on
   **Developer Mode**. The phone restarts and asks once more; confirm.

## Part 3: install Nudge

1. Get the `.ipa` file onto the phone. The simplest way: email it to yourself, or put it in
   iCloud Drive / Google Drive / Dropbox and open that app on the phone.
2. Tap the file on the phone. When it asks what to open it with, choose **AltStore**
   (you may have to tap **More…** or the share icon and pick AltStore from the list).
   Alternatively, open AltStore, go to **My Apps**, tap **+** in the corner, and pick the
   file from Files.
3. AltStore asks for your Apple ID password once more and installs the app. Nudge now sits
   on your home screen.
4. Open Nudge. When it asks to send notifications, tap **Allow**. That is the whole point.

## Part 4: the 7-day rule (important)

Apple lets an app installed this way run for **7 days**. After that it refuses to open
until it is "refreshed". AltStore refreshes it for you automatically, as long as:

- the computer with AltServer is switched on and connected to the **same Wi-Fi** as the
  phone every few days, and
- AltStore is allowed to run in the background (on the phone, Settings → AltStore →
  Background App Refresh on).

The easy habit: leave AltServer running on the computer, and every few days open AltStore
on the phone and tap **Refresh All** while you are at home. If the app ever says it has
expired, plug the phone in and do Part 3 again; your reminders are kept.

## Everyday notes

- **Open Nudge every few days.** iPhones only let an app line up about 60 notifications in
  advance. Nudge tops that queue up each time you open it. Settings → Reliability inside the
  app shows how far ahead you are covered.
- **Snoozing:** press and hold a notification to see the 10 min / 1 hour / Tomorrow buttons.
- **Random reminders** only come during your active hours (7 am to 11 pm unless you change
  them in Settings).
- **Updates** are just new `.ipa` files on the same download page. Install them the same
  way; nothing is lost.

## If something goes wrong

| Problem | Fix |
|---|---|
| AltServer says it cannot find the phone | Use a different cable or USB port, unlock the phone, tap Trust. On Windows, make sure iTunes is installed from apple.com, not the Microsoft Store. |
| "Maximum number of apps" | A free Apple ID can have 3 sideloaded apps at once. Delete one you no longer need. |
| Nudge will not open, just bounces back | The 7 days are up. Open AltStore and tap Refresh, or reinstall the `.ipa`. |
| No notifications arrive | On the phone: Settings → Notifications → Nudge → Allow Notifications. Then open Nudge once so it can queue them. |
| Notifications stopped after a week or two of not opening the app | Open the app; it refills the queue. |
