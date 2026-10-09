## 1. Platform and security foundation

- [ ] 1.0 Document the development environment (Android Studio/SDK/emulator on Windows, Xcode/iOS Simulator on a Mac, minimum Android 10 and iOS 16, setup steps) and verify a new developer can follow it to run both app shells on an emulator and a simulator.
- [ ] 1.1 Create Kotlin Android and Swift iOS app shells with matching navigation, targeting Android 10 (API 29) and iOS 16 minimums, and verify both build and launch on an emulator and a simulator.
- [ ] 1.2 Verify the required `mobile-encrypted-drive-backup` storage, key, pseudonymization, and backup capabilities are implemented and their security tests pass before enabling report import.
- [ ] 1.3 Add platform parity test cases for privacy defaults, import states, and portfolio calculations and verify both clients use the same expected outcomes.
- [ ] 1.4 Add shared synthetic IBKR Flex Query CSV fixtures and mocked Drive and market-data services for automated tests, and verify the test suites need no real credentials, broker data, or network access.

## 2. Report import

- [ ] 2.1 Implement on-device validation and parsing for IBKR Activity Flex Query CSV in both clients and verify synthetic fixtures cover supported rows, normalization, and malformed input.
- [ ] 2.2 Add atomic import and duplicate detection and verify unsupported, corrupted, and duplicate reports create no partial derived records.
- [ ] 2.3 Store derived records only in encrypted local storage, pseudonymize direct identifiers, and verify logs and diagnostics contain no raw identifiers or report contents.
- [ ] 2.4 Back up encrypted reports to the Drive app data folder, verify upload size/checksum before removing app-managed source copies, and verify offline or failed uploads retain protected data with a visible pending state.

## 3. Portfolio overview

- [ ] 3.1 Aggregate holdings using the established instrument identity, FIFO open-lot, and corporate-action rules and verify quantities and average prices with synthetic cases.
- [ ] 3.2 Add currency-specific holding values, account masking, and empty states, and verify rows never convert between currencies.
- [ ] 3.3 Label the overview informational and verify neither client offers PIT-38 calculation or tax-report export in this MVP.

## 4. Market prices

- [ ] 4.1 Confirm Finnhub's terms permit a user's personal API key to be used from this client app, and verify requests send only the ticker symbol and the key.
- [ ] 4.2 Implement the Finnhub adapter for USD holdings of US-listed instruments with local timestamped caching, and verify refresh behavior, quote currency, and the 15-minute market-hours freshness rule.
- [ ] 4.3 Add stale, unavailable, offline, and out-of-scope states and verify the UI never presents an old quote as current.
- [ ] 4.4 Add encrypted storage and a Settings field for the user's provider API key, and verify no key ships in the app, the key never appears in logs or plaintext storage, and no price requests are made without a key.

## 5. Integration and documentation

- [ ] 5.1 Run import, backup, portfolio, and price acceptance cases on an Android emulator and an iOS simulator and verify matching observable behavior.
- [ ] 5.2 Verify hardware-backed key protection, biometric auto-lock/reveal, screenshot and app-switcher protection, and integrity checks on one physical Android device and one physical iOS device, and record the results before release.
- [ ] 5.3 Update user-facing project documentation with supported report format, informational-only scope, privacy behavior, currency limitations, price scope, and API key setup and verify it matches the approved specs.

## 6. Main page

- [ ] 6.1 Read the listing exchange from the report's Financial Instrument Information and verify it with synthetic fixtures.
- [ ] 6.2 Derive the daily change from the provider's previous close, cache it with the quote, and verify it with mocked responses.
- [ ] 6.3 Retrieve and cache NBP table A rates and compute PLN cross rates to USD, and verify requests send only currency codes and the offline and missing-rate states.
- [ ] 6.4 Build the compact positions table with default alphabetical order, column sorting, and the daily/unrealized P&L switch, and verify it with synthetic holdings.
- [ ] 6.5 Add the header with the approximate USD total, daily P&L amount and percentage, rate date, and incomplete marking, and verify mixed-currency synthetic cases.
