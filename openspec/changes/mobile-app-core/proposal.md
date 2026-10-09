## Why

Users need a secure way to review imported IBKR holdings from a phone. The first release needs a clear, testable mobile scope that complements the existing tax tool without changing tax-report behavior.

## What Changes

- Define matching iOS and Android apps using the existing Kotlin and Swift platform direction.
- Support importing the IBKR Flex Query CSV format through the system file picker, with on-device processing and secure backup.
- Show current holdings using remaining FIFO lots and display market prices with freshness information.
- In the MVP, retrieve prices only for USD holdings of US-listed instruments, using the user's own provider API key; other holdings show prices as unavailable.
- Keep portfolio values informational: show subtotals by currency, with no converted grand total.
- Exclude PIT-38 calculation or export, a new backend, and broker connectivity from this MVP.

## Capabilities

### New Capabilities
- `mobile-report-upload`: Import and validate IBKR Flex Query CSV reports, protect report data, and expose import status.
- `mobile-portfolio-overview`: Show privacy-preserving current holdings and informational FIFO-based values.
- `mobile-market-prices`: Retrieve and display licensed market prices with currency, freshness, and failure behavior.

### Modified Capabilities

## Impact

- Adds iOS and Android client behavior; does not change existing desktop/CLI behavior, tax calculations, or APIs.
- Depends on the encryption, local storage, pseudonymization, and Google Drive backup rules in `mobile-encrypted-drive-backup`.
- Uses the repository's existing IBKR Flex Query CSV parsing and instrument identity rules as the behavioral reference.
- Finnhub is the initial market-data provider, used with a personal API key supplied by each user; the app ships no key and needs no commercial data licence. Prices for EU and other non-US listings are deferred to a later change.
