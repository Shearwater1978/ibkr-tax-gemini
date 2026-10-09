## Context

Builds on `mobile-encrypted-drive-backup`, which owns encryption, key management, local storage, Drive access, device hardening and anonymization. This change defines app behavior on top of it and reuses its rules rather than duplicating them. The app is for iOS and Android.

## Decisions

### Decision 1: Process on device
Parsing, anonymization and aggregation happen on the device. No server of our own is introduced.

### Decision 2: Order of operations
Import -> parse -> pseudonymize -> store processed data -> encrypt original and back up to Drive (per `mobile-encrypted-drive-backup`). Aggregation reads only pseudonymized, locally stored data.

### Decision 3: Reuse the existing parsing rules
Report formats and normalization follow the existing Python logic (`src/parser.py`, `src/ib_normalizer.py`). Whether the app reimplements it natively or shares it via a cross-platform module is left to the apply phase.

### Decision 4: Prices
- Prices come from a third-party market data provider; the provider is an open question below.
- Freshness limit: 15 minutes during market hours; outside market hours the last close is current. Refresh on screen open and manual pull-to-refresh.
- Only ticker symbols are sent. Last known prices are cached locally (in the encrypted local DB) with timestamp and currency.
- Prices in different currencies are shown in their own currency; totals need a conversion rate (open question).

### Decision 5: Instrument identity
Holdings are keyed by instrument identity (ISIN where available, ticker otherwise), aligned with `src/instrument_identity.py` and the pending `isin-change-handling` change.

## Open Questions
- Which market data provider (free tier limits, licensing for a distributed app, delay vs real-time)?
- Base currency for totals and the FX source (NBP is used for tax; display may differ).
- Which broker report formats must be supported at launch (IBKR Activity Statement / Flex only?).
- Is the average purchase price FIFO-based, as in the tax logic, or a simple weighted average?

## Risks
- Provider rate limits or terms change; mitigate with caching and a replaceable provider interface.
- Delayed prices could be mistaken for live ones; mitigate with visible timestamps and stale markers.
- Differences between the app's aggregation and the tax FIFO results could confuse users; mitigate by labeling the overview as informational, not a tax report.
