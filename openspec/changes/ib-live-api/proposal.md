## Why

The project currently imports IBKR data from CSV activity reports and stores normalized transactions locally. A direct live connection to Interactive Brokers is feasible via the official TWS/IB Gateway API, but it is not currently implemented in this repository. Adding a controlled live connector would allow the application to pull account/trade data in near-real time instead of relying only on manual CSV import.

## What Changes

- Add a dedicated live-connection capability for Interactive Brokers through the official TWS/IB Gateway API.
- Support a read-only account sync flow that imports trades, positions, corporate actions, and account metadata into the existing normalized transaction model.
- Keep the live connector isolated from the current CSV import workflow so the existing tax engine and encrypted storage remain stable.
- Surface connection, authentication, and permission failures as explicit diagnostics rather than silent mis-imports.
- Keep the feature optional: CSV import continues to work without requiring a running IB Gateway session.

## Capabilities

### New Capabilities
- `ib-live-api`: Live connection and read-only synchronization with Interactive Brokers TWS/IB Gateway for account and trade data.

### Modified Capabilities
- None.

## Impact

Affected areas include the local import pipeline, DB normalization, diagnostics, and any future UI actions that expose live connection state. The change does not replace the current CSV-based import path; it adds a second data source that must validate and normalize into the same internal schema used by FIFO and report generation.
