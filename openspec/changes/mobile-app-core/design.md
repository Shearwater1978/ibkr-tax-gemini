## Context

The mobile security change defines Kotlin/Swift platform targets, encrypted local storage, on-device processing, pseudonymization, and encrypted Google Drive backup. The existing Python parser handles IBKR Activity Flex Query CSV files; the existing tax logic uses FIFO matching and instrument identity rules.

## Goals / Non-Goals

**Goals:**
- Deliver matching iOS and Android behavior while using native platform security features.
- Keep report processing local and make portfolio figures consistent with remaining FIFO lots.
- Make backup, price freshness, currency, privacy, and error behavior explicit before implementation.

**Non-Goals:**
- PIT-38 calculation, tax-report generation/export, or changes to tax calculation rules.
- A server-side processing service, broker account connection, or order placement.
- Converting portfolio values across currencies in the first release.

## Decisions

### Native platform clients with behavioral parity

Implement the Android client in Kotlin and the iOS client in Swift, matching user-visible behavior and using each platform's secure storage and lifecycle protections. This follows the mobile security plan; a single shared framework would require revisiting its security architecture.

### IBKR Flex Query CSV as the initial report format

The first release accepts only IBKR Activity Flex Query CSV reports selected through the system file picker. Validate the format and parseability locally before creating a backup or derived records. Other report formats require a later spec change. Parser normalization and instrument identity behavior must match the repository's established rules.

### On-device import and protected backup

All parsing and aggregation run on-device and work without a network connection. Keep temporary source data in app-private storage, encrypt it before network access, and store derived records only in the encrypted local database after a complete successful import. Upload the encrypted report to the Google Drive app data folder and verify it before removing the app-managed source copy. If backup is unavailable, retain only the protected local copy, show a pending status, and retry without blocking local processing. Never delete the user's original file outside app-managed storage.

Direct identifiers are pseudonymized before derived data is persisted, logged, or displayed; report contents and financial values tied to a person are excluded from logs. The encrypted-backup change remains the authority for key management, cryptographic formats, device hardening, erasure, and Drive access.

### Informational holdings based on FIFO open lots

Aggregate positions by the repository's instrument identity and derive current quantity and average purchase price from remaining FIFO lots, including the established split/corporate-action handling. The overview is informational only and does not compute or export PIT-38. Keep holdings and market values in their own currencies; where an instrument has lots in different currencies, show separate currency subpositions rather than inventing an FX conversion.

### Market-price integration

Use a replaceable provider adapter. Before implementing or releasing price requests, select a provider whose terms permit the intended app distribution and whose interface can request prices using instrument identifiers only. Request updates when the overview opens and on user refresh, cache the last price and timestamp locally, and mark stale prices visibly. Show currency-specific values and do not calculate a converted grand total.

## Risks / Trade-offs

- Separate native clients can drift → use shared behavioral acceptance cases and verify parity on both platforms.
- Market-data licensing or availability may change → complete provider and terms review before integration and retain a replaceable adapter.
- Currency-specific subtotals are less convenient than one portfolio total → avoid unsupported FX assumptions and add conversion only in a separately specified change.
- On-device parser behavior may differ from Python → use synthetic shared test cases for supported report variations and normalization.
