# SnapOTP (package: ClipOTP / com.itsash.clipotp.clipotp)

Privacy-focused Android app: Flutter UI shell + native Kotlin platform code
that intercepts notifications and SMS to detect OTPs, extracts codes, and
shows a floating overlay with one-tap copy. Zero network calls, zero
telemetry.

## Architecture

- `lib/` — Flutter UI only (dashboard, permissions, settings, history). Uses
  the shared `theme` package (`../theme`, path dependency) for ThemeData and
  components.
- `android/app/src/main/kotlin/com/itsash/clipotp/clipotp/`:
  - `SnapNotificationService.kt` — `NotificationListenerService`; reads
    posted notifications, extracts OTPs, writes history, starts the overlay.
  - `SnapSmsReceiver.kt` — `BroadcastReceiver` for
    `android.provider.Telephony.SMS_RECEIVED`; same extraction/overlay path.
  - `OtpExtractor.kt` — regex/heuristic engine. 4-8 char numeric or
    alphanumeric codes; requires an OTP keyword within ~40 chars OR a
    "strict" code shape (mixed alnum, or exactly 6 digits). Excludes
    currency-adjacent, date-shaped, phone-number-length (10+), and
    order/tracking-id-adjacent matches unless a keyword is nearby.
  - `SnapOverlayService.kt` — `WindowManager` + `TYPE_APPLICATION_OVERLAY`
    pill UI (`res/layout/view_otp_overlay.xml`). Clipboard writes happen
    **natively here**, never routed through the Flutter engine, so copy
    works even when the Flutter engine isn't running. Auto-dismisses after
    a configurable timeout (default 10s) or immediately after copy. Add/
    remove of the overlay view is lifecycle-safe (single tracked view,
    always removed in `onDestroy`).
  - `SnapDb.kt` — `SQLiteOpenHelper` for the 24h rolling OTP history table
    (`otp_history`). **Shared file contract**: both this Kotlin helper
    (`context.getDatabasePath("snapotp_history.db")`) and Flutter's sqflite
    (`lib/services/history_repository.dart`, via `getDatabasesPath()`) open
    the identical on-disk file by using the same file name — so the native
    services can insert rows while the Flutter engine is not running, and
    the Flutter History screen reads/searches that data directly with no
    platform channel round-trip. Do not rename the DB file without updating
    both sides.
  - `MainActivity.kt` — registers the platform channel contract.

## Platform channel contract

- `MethodChannel("com.itsash.clipotp/control")`: `getMasterEnabled`,
  `setMasterEnabled`, `getAutoCopy`, `setAutoCopy`, `getOverlayTimeoutSec`,
  `setOverlayTimeoutSec`, `isNotificationAccessGranted`,
  `isOverlayPermissionGranted`, `openNotificationAccessSettings`,
  `openOverlayPermissionSettings`. Backed by Android `SharedPreferences`
  (`SnapPrefs`, file `snapotp_prefs`) so native services read the same
  values Flutter writes.
- `EventChannel("com.itsash.clipotp/events")`: reserved for live-forwarding
  capture events to a running Flutter UI. The DB remains the source of
  truth; the History screen also polls it directly.
- Dart wrapper: `lib/services/platform_channel_service.dart`.

## Permissions

`RECEIVE_SMS`, `READ_SMS`, `SYSTEM_ALERT_WINDOW`, `POST_NOTIFICATIONS`
(API 33+). Notification access and overlay permission are special
Settings-page grants (deep-linked via the platform channel); SMS is a
runtime permission requested via `permission_handler`.

## Build / run

- `flutter pub get && flutter run` — run on a connected device/emulator.
- `flutter build apk --release` — release APK.
- `flutter analyze` — static analysis; keep clean.
- Icon source: `assets/icon/icon.png` (+ `icon_fg.png` for the adaptive
  foreground), generated from `Icons/shieldsdotio.svg` via `rsvg-convert`.
  Regenerate launcher icons with `flutter pub run flutter_launcher_icons`
  after changing the source art.

## Conventions

- minSdk 26, targetSdk/compileSdk 34.
- No analytics/telemetry dependencies, ever. No network permissions.
- Theme: the app is wrapped in `UiApp` (theme v2). Use the `Ui*` component
  widgets from `package:theme/theme.dart` instead of raw Material widgets
  where an equivalent exists (see `../theme/COMPONENTS.md`). Read colors from
  `context.colors` (status colors via `context.colors.tone(UiStatus.x)`) and
  text styles from `context.text`; never hardcode colors.

## Release (Ash Store)
- Published through the Ash Store kit: `store.json` (id `snapotp`, repo its-ash/snapotp), `store.mk`, `index.html` + `CNAME` (`snapotp.itsash.in`). See `../ash-store/store-kit/STORE.md`.
- `make deploy` = bump version → build → commit, tag `vX.Y.Z`, push → GitHub release with `snapotp.apk` → repo topics, homepage, Pages. Never create releases, tags or asset names by hand.
- Do not edit `store.mk` here. Edit `../ash-store/store-kit/store.mk`, then run `make store-sync` at the workspace root.
