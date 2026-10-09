# Mobile apps (MVP)

Native clients for the `mobile-app-core` OpenSpec change: Kotlin/Jetpack Compose on Android, Swift/SwiftUI on iOS. Both are informational portfolio viewers; they do not calculate or export PIT-38.

| | Android | iOS |
|---|---|---|
| Path | `mobile/android` | `mobile/ios` |
| Minimum OS | Android 10 (API 29) | iOS 16 |
| App ID | `com.ibkrtax.mobile` | `com.ibkrtax.mobile` |
| Toolchain | Android Studio, JDK 17+ | Xcode 15+, XcodeGen (macOS only) |

## Navigation contract

Both clients expose the same bottom-tab navigation. Route IDs and order are checked by unit tests on each platform (`AppDestinationTest.kt`, `AppDestinationTests.swift`).

| Order | Route | Tab | Purpose |
|---|---|---|---|
| 1 (start) | `portfolio` | Portfolio | Holdings; empty state with an action that opens Imports |
| 2 | `imports` | Imports | Report import and status; disabled until encrypted storage exists |
| 3 | `settings` | Settings | Privacy notice and app version |

When you change a destination, change it on both platforms and in this table.

## Android

1. Install Android Studio. In SDK Manager, add Android SDK Platform 35 and a system image for API 29 or later; create an emulator in Device Manager.
2. Open `mobile/android` in Android Studio (it writes `local.properties` with the SDK path), or set `ANDROID_HOME`.
3. Build and test from the command line:

   ```powershell
   cd mobile/android
   .\gradlew.bat testDebugUnitTest assembleDebug
   .\gradlew.bat installDebug   # with an emulator running
   ```

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
