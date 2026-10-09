## 1. Platform and security foundation

- [ ] 1.1 Create Kotlin Android and Swift iOS app shells with matching navigation and verify both platforms build and launch.
- [ ] 1.2 Verify the required `mobile-encrypted-drive-backup` storage, key, pseudonymization, and backup capabilities are implemented and their security tests pass before enabling report import.
- [ ] 1.3 Add platform parity test cases for privacy defaults, import states, and portfolio calculations and verify both clients use the same expected outcomes.

## 2. Report import

- [ ] 2.1 Implement on-device validation and parsing for IBKR Activity Flex Query CSV in both clients and verify synthetic fixtures cover supported rows, normalization, and malformed input.
- [ ] 2.2 Add atomic import and duplicate detection and verify unsupported, corrupted, and duplicate reports create no partial derived records.
- [ ] 2.3 Store derived records only in encrypted local storage, pseudonymize direct identifiers, and verify logs and diagnostics contain no raw identifiers or report contents.
- [ ] 2.4 Back up encrypted reports to the Drive app data folder, verify upload size/checksum before removing app-managed source copies, and verify offline or failed uploads retain protected data with a visible pending state.

## 3. Portfolio overview

- [ ] 3.1 Aggregate holdings using the established instrument identity, FIFO open-lot, and corporate-action rules and verify quantities and average prices with synthetic cases.
- [ ] 3.2 Add currency-specific holdings and value subtotals, account masking, and empty states and verify mixed-currency cases never produce a converted grand total.
- [ ] 3.3 Label the overview informational and verify neither client offers PIT-38 calculation or tax-report export in this MVP.

## 4. Market prices

- [ ] 4.1 Select a provider and verify its licensing permits the intended distribution and its request path sends only required instrument identifiers.
- [ ] 4.2 Implement price retrieval and local timestamped caching and verify refresh behavior, quote currency, and the 15-minute market-hours freshness rule.
- [ ] 4.3 Add stale, unavailable, and offline states and verify the UI never presents an old quote as current.

## 5. Integration and documentation

- [ ] 5.1 Run import, backup, portfolio, and price acceptance cases on Android and iOS and verify matching observable behavior.
- [ ] 5.2 Update user-facing project documentation with supported report format, informational-only scope, privacy behavior, and currency limitations and verify it matches the approved specs.
