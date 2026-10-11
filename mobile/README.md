# Mobile apps (MVP)

Native clients for the `mobile-app-core` OpenSpec change: Kotlin/Jetpack Compose on Android, Swift/SwiftUI on iOS. Both are informational portfolio viewers; they do not calculate or export PIT-38.

| | Android | iOS |
|---|---|---|
| Path | `mobile/android` | `mobile/ios` |
| Minimum OS | Android 10 (API 29) | iOS 16 |
| App ID | `com.ibkrtax.mobile` | `com.ibkrtax.mobile` |
| Toolchain | Android Studio, JDK 17+ | Xcode 15+, XcodeGen (macOS only) |

The Android app is called **Portfolio Companion**; how to use it is in the [user guide](USER_GUIDE.md). Data protection: [privacy notice](PRIVACY.md) and [record of processing activities](PROCESSING_RECORD.md) (drafts; publisher details still placeholders).

## Navigation contract

The Android app comes in four skins (`mobile-skins` OpenSpec change). They share the state holders in `state/` and the screens in `ui/common` (import, imported reports, Settings), so data and numbers are identical; only layout and navigation differ. Each skin declares where every `Feature` lives, and `SkinCatalogTest` fails if one is missing.

### Classic (both platforms)

Classic is the original UI and the only navigation on iOS. Both clients expose the same bottom-tab navigation. Route IDs and order are checked by unit tests on each platform (`AppDestinationTest.kt`, `AppDestinationTests.swift`).

| Order | Route | Tab | Purpose |
|---|---|---|---|
| 1 (start) | `portfolio` | Portfolio | Holdings; empty state with an action that opens Imports |
| 2 | `dividends` | Dividends | Received dividends per month, year and position (Android only for now) |
| 3 | `operations` | Operations | Imported transactions with ticker search and type filters (Android only for now) |
| 4 | `imports` | Imports | Report import and status; disabled until encrypted storage exists |
| 5 | `settings` | Settings | Backup, price key, and app version |

When you change a destination, change it on both platforms and in this table. While iOS work is paused, Android leads: tabs marked "Android only for now" are added to iOS when it resumes.

### Calm, Bento and Focus (Android)

Routes are private to each skin package (`ui/skins/<skin>`); details open as sheets or as routes. When you change a skin's navigation, update its row here and its `features` map.

| Skin | Tabs (start first) | Other routes | Details |
|---|---|---|---|
| Calm | `home`, `income`, `activity` (bottom bar) | `import` ("+" on Home), `settings` (avatar) | Position, total return, value info and payer history open as sheets |
| Bento | `home`, `income`, `activity`, `data` (floating bar, shown on every screen) | `position/{key}`, `payer/{ticker}/{currency}`, `import`, `reports`, `settings` | A tab tap leaves any opened screen first, so each tab opens on its first screen |
| Focus | `today`, `income` (bar with a central "+") | `holdings`, `story/{key}`, `activity`, `import`, `reports`, `settings`; the "+" menu opens them | Payer history opens as a sheet; tab taps behave as in Bento |

Position keys are `ticker|currency|isin` for held and `ticker|currency|sold` for sold positions.

## Android

1. Install Android Studio. In SDK Manager, add Android SDK Platform 35 and a system image for API 29 or later; create an emulator in Device Manager.
2. Open `mobile/android` in Android Studio (it writes `local.properties` with the SDK path), or set `ANDROID_HOME`.
3. Build and test from the command line:

   ```powershell
   cd mobile/android
   .\gradlew.bat testDebugUnitTest assembleDebug
   .\gradlew.bat installDebug   # with an emulator running
   .\gradlew.bat connectedDebugAndroidTest   # Keystore and SQLCipher tests, needs an emulator
   ```

   Emulator Keystore is usually software-backed, so hardware key protection must still be checked on a physical device.

## iOS

1. On a Mac, install Xcode and XcodeGen (`brew install xcodegen`).
2. Generate the project and run the tests:

   ```sh
   cd mobile/ios
   xcodegen generate
   xcodebuild test -scheme IBKRTaxAssistant -destination 'platform=iOS Simulator,name=iPhone 15'
   ```

3. Open `IBKRTaxAssistant.xcodeproj` and run it on a simulator. The `.xcodeproj` is generated and git-ignored; edit `project.yml` instead.

## Test data

Use only synthetic fixtures. Never put real IBKR reports, account identifiers, or tax data into tests, emulators, simulators, or this repository.
