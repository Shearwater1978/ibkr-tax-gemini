## Why

The project has a desktop/CLI tax tool and a separate change (`mobile-encrypted-drive-backup`) that defines the security model for a mobile app, but no spec for the app's user-facing behavior. Users want to work with their broker data from an iPhone or Android phone. This change defines the first feature set of that app; more features will be added in later changes.

## What Changes

- Add a mobile app (iOS and Android) as a new client.
- Users upload broker reports from the app UI (file picker).
- Reports are anonymized (pseudonymized) while being processed, before results are stored or shown.
- Reports are stored securely in Google Drive using the encryption, key management and backup rules from `mobile-encrypted-drive-backup`; this change does not redefine them.
- The app shows accumulated information about all stocks the user holds.
- The app fetches current stock prices to display up-to-date values.

## Capabilities

### New Capabilities
- `mobile-report-upload`: Uploading broker reports from the UI, anonymization during processing, and handing the result to the secure Drive backup.
- `mobile-portfolio-overview`: Accumulated per-stock and total holdings view built from processed reports.
- `mobile-market-prices`: Fetching and displaying current stock prices, with freshness and failure behavior.

### Modified Capabilities

## Impact

- New mobile client; no change to existing desktop/CLI behavior or specs.
- Depends on `mobile-encrypted-drive-backup` (key management, encrypted backup, local storage, data anonymization, device hardening). That change must land before or together with implementation.
- Requires a third-party market data provider and its terms of use (see design.md, open question).
- Only ticker symbols leave the device to fetch prices; no personal or account data.
- The feature list is expected to grow; later features get their own changes.
