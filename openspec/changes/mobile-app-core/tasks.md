## 1. Platform and security foundation

- [ ] 1.0 Document the development environment (Android Studio/SDK/emulator on Windows, Xcode/iOS Simulator on a Mac, minimum Android 10 and iOS 16, setup steps) and verify a new developer can follow it to run both app shells on an emulator and a simulator. _Status: Android and iOS documented in `mobile/README.md`; iOS verified with Xcode 27 on an iPhone 17 simulator (iOS 27)._
- [ ] 1.1 Create Kotlin Android and Swift iOS app shells with matching navigation, targeting Android 10 (API 29) and iOS 16 minimums, and verify both build and launch on an emulator and a simulator. _Status: Android done and verified on an API 35 emulator; iOS shell builds, passes its tests and launches on an iPhone 17 simulator with the same five tabs (Dividends and Operations show empty states until import is ported)._
- [ ] 1.2 Verify the required `mobile-encrypted-drive-backup` storage, key, pseudonymization, and backup capabilities are implemented and their security tests pass before enabling report import. _Status: Android: storage, keys, pseudonymization and folder backup are in place and import is enabled; restore follows (backup task 4.5). iOS: device key, encrypted database, pseudonymization, backup exclusion, app lock and app-switcher cover in place; the Imports tab keeps import off until backup keys and folder backup exist (debug builds load the synthetic samples)._
- [ ] 1.3 Add platform parity test cases for privacy defaults, import states, and portfolio calculations and verify both clients use the same expected outcomes. _Status: In progress: both clients run the shared Flex Query and FIFO fixtures with the same expected outcomes (parser records, source keys, open lots); privacy defaults and import states follow with the iOS import screen._
- [ ] 1.4 Add shared synthetic IBKR Flex Query CSV fixtures and mocked Drive and market-data services for automated tests, and verify the test suites need no real credentials, broker data, or network access. _Status: Shared fixtures and Android fakes done; iOS parser tests use the same fixtures; iOS fakes follow with backup and prices._

## 2. Report import

- [ ] 2.1 Implement on-device validation and parsing for IBKR Activity Flex Query CSV in both clients and verify synthetic fixtures cover supported rows, normalization, and malformed input. _Status: Android done (parity with the Python parser). iOS parser and import plan done: same records and source keys as Python for both valid fixtures, same rejections; storing records follows._
- [x] 2.2 Add atomic import and duplicate detection and verify unsupported, corrupted, and duplicate reports create no partial derived records. _iOS: ReportImporter ported with the Android tests (rollback, duplicates, overlapping reports, deletion)._
- [x] 2.3 Store derived records only in encrypted local storage, pseudonymize direct identifiers, and verify logs and diagnostics contain no raw identifiers or report contents. _iOS: records stored in SQLCipher with a keyed fingerprint, account pseudonym and last-four mask only._
- [ ] 2.4 Back up encrypted reports to the user-chosen backup folder, verify the written file's size and SHA-256 before removing app-managed source copies, and verify offline or failed uploads retain protected data with a visible pending state. _Status: Android done: encrypted backup written to the chosen folder and verified by reading back; offline or lost access keeps the import and a pending backup. iOS postponed._

## 3. Portfolio overview

- [x] 3.1 Aggregate holdings using the established instrument identity, FIFO open-lot, and corporate-action rules and verify quantities and average prices with synthetic cases. _Status: Android done; iOS FIFO open lots, realized sales and holdings match the shared scenarios._
- [x] 3.2 Add currency-specific holding values, account masking, and empty states, and verify rows never convert between currencies.
- [x] 3.3 Verify neither client offers PIT-38 calculation or tax-report export in this MVP.

## 4. Market prices

- [ ] 4.1 Confirm Finnhub's terms permit a user's personal API key to be used from this client app, and verify requests send only the ticker symbol and the key. _Status: Pending: confirm with Finnhub before release._
- [x] 4.2 Implement the Finnhub adapter for USD holdings of US-listed instruments with local timestamped caching, and verify refresh behavior, quote currency, and the 15-minute market-hours freshness rule. _iOS: Finnhub adapter, quote cache in the encrypted database and the freshness rules ported with the Android tests._
- [x] 4.3 Add stale, unavailable, offline, and out-of-scope states and verify the UI never presents an old quote as current.
- [x] 4.4 Add encrypted storage and a Settings field for the user's provider API key, and verify no key ships in the app, the key never appears in logs or plaintext storage, and no price requests are made without a key. _iOS: encrypted key storage and the Settings field done (Finnhub opens in an in-app Safari view; the key comes in through the system Paste button, since iOS asks before apps read the clipboard)._
- [x] 4.5 Without a Finnhub key, retrieve prices from Yahoo Finance's public batch endpoint (symbols only, 20 per request) and name the source on the main page and in Settings. _iOS: Yahoo fallback ported; naming the source on screen follows with the main page._
- [x] 4.6 Price holdings on supported European exchanges from Yahoo Finance (symbol suffix per IBKR exchange, pence to pounds, currency check, per-exchange session hours, "delayed" label), also when a Finnhub key is set, and verify with unit tests and on the emulator. _iOS: European exchange table, pence conversion and delayed label ported with the Android tests._

## 5. Integration and documentation

- [ ] 5.1 Run import, backup, portfolio, and price acceptance cases on an Android emulator and an iOS simulator and verify matching observable behavior.
- [ ] 5.2 Verify hardware-backed key protection, biometric auto-lock/reveal, screenshot and app-switcher protection, and integrity checks on one physical Android device and one physical iOS device, and record the results before release.
- [x] 5.3 Update user-facing project documentation with supported report format, informational-only scope, privacy behavior, currency limitations, price scope, and API key setup and verify it matches the approved specs.

## 6. Main page

- [x] 6.1 Read the listing exchange from the report's Financial Instrument Information and verify it with synthetic fixtures.
- [x] 6.2 Derive the daily change from the provider's previous close, cache it with the quote, and verify it with mocked responses.
- [x] 6.3 Retrieve and cache NBP table A rates and compute PLN cross rates to USD, and verify requests send only currency codes and the offline and missing-rate states. _iOS: NBP client, cross rates and encrypted rate cache ported with the Android tests._
- [x] 6.4 Build the compact positions table with default alphabetical order, column sorting, and the daily/unrealized P&L switch, and verify it with synthetic holdings. _iOS: rows, sorting and the USD view ported; the table UI follows._
- [x] 6.5 Add the header with the approximate USD total, daily P&L amount and percentage, rate date, and incomplete marking, and verify mixed-currency synthetic cases. _iOS: USD totals ported; the header UI follows._

## 7. Report management

- [x] 7.1 Import several reports at once, each on its own, with a per-file result and masked file names.
- [x] 7.2 Delete one import after confirmation, keeping records that other imports also contain, and delete its backup file.
- [x] 7.3 Delete all imports after entering the passphrase, and delete their backup files.
- [x] 7.4 Remove the account list from the overview and the standing tax/PIT-38 notice from all screens.

## 8. Price provider choice and Twelve Data (Android)

- [x] 8.1 Add the provider choice (None, Finnhub, Twelve Data) with a key per provider and a stored choice that keeps existing Finnhub users on Finnhub, and verify with unit tests for the migration and independent keys.
- [x] 8.2 Add a credit-based rate limiter with a Twelve Data free-plan preset (8 credits per minute) and verify with unit tests.
- [x] 8.3 Add the Twelve Data provider (`/quote`, header key, chunks of 8, single- and multi-symbol responses, errors as HTTP status or body) and verify with unit tests against the fixtures in `mobile/fixtures/twelve-data`.
- [x] 8.4 Route prices by the chosen provider without automatic switching, make no request with None, keep Yahoo Finance only for European listings while a provider is chosen, price largest positions first with progress, and use 15-minute (Twelve Data) and one-minute (Finnhub) re-request intervals; verify with unit tests.
- [x] 8.5 Record the source of each cached quote (schema version 8, additive) and verify that a quote keeps its actual source.
- [ ] 8.6 Add the provider choice and per-provider key entry, check and removal to Settings, and the per-price source label with Twelve Data attribution to all four skins; verify on the emulator. _Status: implemented and unit-tested; the emulator check is still to do._
- [x] 8.7 Update the privacy notice, processing record, data classification and user guide, and verify they match this spec.
- [ ] 8.8 Bring the iOS app to the same provider choice (later change).
