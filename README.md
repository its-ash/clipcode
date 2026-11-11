# Clipcode

One-tap copy for OTP codes from SMS and notifications.

**[Website](https://clipcode.itsash.in)** · **[Download APK](https://github.com/its-ash/clipcode/releases/latest/download/clipcode.apk)** · Get it in **[Ash Store](https://store.itsash.in)**

## Features

- **Finds OTPs** in incoming notifications and SMS: 4–8 character numeric or
  alphanumeric codes near an OTP keyword, with filters for prices, dates,
  phone numbers and order IDs.
- **Floating pill** over any app with one-tap copy. It closes after a timeout
  (10 s by default) or right after you copy.
- **Copy works even when the app is closed**: the clipboard write happens in
  native code.
- **24-hour history** of recent codes, kept on the phone only.
- **Private**: no network calls and no telemetry.

## Install

- **Ash Store**: install [Ash Store](https://store.itsash.in) once, then install and update Clipcode from it.
- **APK**: download the [latest APK](https://github.com/its-ash/clipcode/releases/latest/download/clipcode.apk) on your phone and open it. Allow your browser to install unknown apps if Android asks.

Requires Android 8.0 (API 26) or later. After install, grant notification
access, SMS and "display over other apps" from the app's permission screen.

## Develop

Needs Flutter ≥ 3.44 and the shared theme package at `../theme`.

```bash
make run      # run on a connected device
make build    # release APK
make deploy   # bump version, build, tag, push, GitHub release (see store.mk)
```

## Release

`make deploy` follows the Ash Store kit (`store.mk`, `store.json`): it bumps the
version in `pubspec.yaml`, builds, commits, tags `vX.Y.Z`, pushes, and publishes
a GitHub release with `clipcode.apk`. Ash Store and the website pick up the new
version from that release.

## Project layout

```
lib/                                          # Flutter UI: dashboard, permissions, settings, history
android/app/src/main/kotlin/com/itsash/clipotp/clipotp/
├── SnapNotificationService.kt                # reads notifications, extracts OTPs
├── SnapSmsReceiver.kt                        # SMS_RECEIVED receiver, same path
├── OtpExtractor.kt                           # regex/heuristic OTP engine
├── SnapOverlayService.kt                     # floating pill + native clipboard copy
└── SnapDb.kt                                 # 24 h rolling history
```

See `CLAUDE.md` for details.

## License

No license file is included yet.

Built by [itsash.in](https://itsash.in).
