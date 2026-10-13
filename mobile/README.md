# Mobile apps (MVP)

Native clients for the `mobile-app-core` OpenSpec change: Kotlin/Jetpack Compose on Android, Swift/SwiftUI on iOS. Both are informational portfolio viewers; they do not calculate or export PIT-38.

| | Android | iOS |
|---|---|---|
| Path | `mobile/android` | `mobile/ios` |
| Minimum OS | Android 10 (API 29) | iOS 16 |
| App ID | `com.ibkrtax.mobile` | `com.ibkrtax.mobile` |
| Toolchain | Android Studio, JDK 17+ | Xcode 27+, XcodeGen (macOS only); SQLCipher via Swift Package Manager |

Both apps are called **Portfolio Companion**; how to use it is in the [user guide](USER_GUIDE.md). Data protection: [privacy notice](PRIVACY.md) and [record of processing activities](PROCESSING_RECORD.md) (drafts; publisher details still placeholders).

## Navigation contract

Both apps come in four skins (`mobile-skins` OpenSpec change). They share the state holders (Android `state/`, iOS `State/`) and the common screens (Android `ui/common`, iOS `Screens/`: import, imported reports, Settings), so data and numbers are identical; only layout and navigation differ. Each skin declares where every `Feature` lives, and `SkinCatalogTest` (`SkinCatalogTests` on iOS) fails if one is missing. Manual operations (`mobile-manual-operations`) use one shared form, Android `ui/common/ManualEntryDialog.kt` and iOS `Screens/ManualEntryView.swift`, which every skin's activity screen opens as a dialog or sheet instead of a route.

### Classic

Classic is the original UI. Both clients expose the same bottom-tab navigation. Route IDs and order are checked by unit tests on each platform (`AppDestinationTest.kt`, `AppDestinationTests.swift`).

| Order | Route | Tab | Purpose |
|---|---|---|---|
| 1 (start) | `portfolio` | Portfolio | Holdings; empty state with an action that opens Imports |
| 2 | `dividends` | Dividends | Received dividends per month, year and position |
| 3 | `operations` | Operations | Imported transactions with ticker search and type filters |
| 4 | `imports` | Imports | Report import and status; disabled until encrypted storage exists |
| 5 | `settings` | Settings | Backup, price key, and app version |

When you change a destination, change it on both platforms and in this table. Android leads; iOS catches up screen by screen.

### Calm, Bento and Focus

Routes are private to each skin (Android `ui/skins/<skin>`, iOS `Skins/<Skin>`); details open as sheets or as routes. The iOS skins use the same routes as enum cases in a `NavigationStack` path (for example `story(key)`). When you change a skin's navigation, change it on both platforms, in its row here and in its `features` map.

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

1. On a Mac, install Xcode 27 or later from the App Store and XcodeGen (`brew install xcodegen`), then set up the toolchain and the iOS Simulator runtime:

   ```sh
   sudo xcode-select -s /Applications/Xcode.app/Contents/Developer
   sudo xcodebuild -license accept
   xcodebuild -runFirstLaunch
   xcodebuild -downloadPlatform iOS
   xcrun simctl list devices available   # pick a simulator name, e.g. iPhone 17
   ```

2. Generate the project and run the tests:

   ```sh
   cd mobile/ios
   xcodegen generate
   xcodebuild test -scheme IBKRTaxAssistant -destination 'platform=iOS Simulator,name=iPhone 17'
   ```

3. Open `IBKRTaxAssistant.xcodeproj` in Xcode and run it on a simulator, or from the command line:

   ```sh
   xcodebuild build -scheme IBKRTaxAssistant -destination 'platform=iOS Simulator,name=iPhone 17' -derivedDataPath build
   xcrun simctl boot "iPhone 17"
   open -a DeviceHub   # Xcode 27's simulator window (formerly Simulator.app)
   xcrun simctl install "iPhone 17" build/Build/Products/Debug-iphonesimulator/IBKRTaxAssistant.app
   xcrun simctl launch "iPhone 17" com.ibkrtax.mobile
   ```

   The `.xcodeproj` and `build/` are generated and git-ignored; edit `project.yml` instead. The app targets iOS 16; the test target needs iOS 17 because Xcode 27's XCTest does.

4. The app starts locked and asks for Face ID, or for the passcode when Face ID is not enrolled. On the simulator, enrol Face ID once and send a match whenever the prompt appears:

   ```sh
   xcrun simctl spawn "iPhone 17" notifyutil -s com.apple.BiometricKit.enrollmentChanged 1
   xcrun simctl spawn "iPhone 17" notifyutil -p com.apple.BiometricKit.enrollmentChanged   # enrol
   xcrun simctl spawn "iPhone 17" notifyutil -p com.apple.BiometricKit_Sim.pearl.match     # or .nomatch
   ```

## Test data

Use only synthetic fixtures. Never put real IBKR reports, account identifiers, or tax data into tests, emulators, simulators, or this repository.
