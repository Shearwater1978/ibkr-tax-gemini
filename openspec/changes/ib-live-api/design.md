## Context

The repository already imports IBKR activity data from CSV files and normalizes it into a local SQLCipher database. There is no direct TWS/IB Gateway integration today, and the current application is intentionally local and file-first. The live API feature must fit into that architecture without replacing the CSV pipeline.

## Goals / Non-Goals

**Goals:**
- Add a live IBKR connection as an optional data source for the same tax workflow.
- Reuse the project’s normalization/storage model so downstream tax calculations are unchanged.
- Surface connection and account errors in a user-friendly way.

**Non-Goals:**
- Real-time trading execution or order placement.
- Replacing the current CSV and encrypted local database model.
- Full account automation beyond read-only import and normalization.

## Decisions

1. **Use the official IB TWS/IB Gateway API as the integration boundary.** The project should rely on the broker’s socket protocol instead of scraping or reverse-engineering data files. This keeps the integration aligned with IB’s supported interfaces and minimizes custom parsing.
2. **Treat the feature as read-only.** The app will import account and trade data, but it will not submit orders or alter positions on the IB side. This reduces risk and matches the current tax-reporting purpose.
3. **Normalize into existing internal schemas.** Instead of creating a parallel storage model, live data should flow through the same normalized transaction/corporate-action format used by CSV imports. This preserves FIFO logic and avoids dual logic paths.
4. **Keep live connectivity optional.** CSV import remains the default, and the app should detect when IB Gateway is unavailable without blocking prior workflows.
5. **Model failure states explicitly.** The live connector will return connection, authorization, and sync errors as structured diagnostics, not as silent empty imports.

## Risks / Trade-offs

- [Risk] IB Gateway/TWS must be installed and running locally → mitigate with explicit configuration validation and a health check before sync.
- [Risk] Account permissions and market data subscriptions vary by user → mitigate with account-specific diagnostics and read-only requests.
- [Risk] Live data schemas differ from CSV exports → mitigate by a strict normalization layer before writing to the DB.
- [Risk] API sessions can disconnect during long syncs → mitigate with reconnect rules and resumable import boundaries.

## Migration Plan

1. Add the connector behind a clearly optional configuration path.
2. Validate the live session against the current DB and import model without changing existing CSV flows.
3. Run focused tests for normalization, invalid configuration, and connection failure handling.
4. Merge as a feature branch; CSV import remains the baseline path and the live connector is enabled only when configured.

## Confirmed Target Environment (Task 1.1)

- **Integration mode:** IB Gateway (headless), not the full TWS desktop client. Gateway is the recommended mode for unattended/API-only integrations and has a smaller footprint.
- **Account type:** Paper trading account for development and testing. Default paper Gateway port is `4002` (vs `4001` for live Gateway, `7497`/`7496` for paper/live TWS).
- **Client ID:** A fixed, configurable `client_id` (default `1`) is required per socket connection; each concurrent connection to the same Gateway instance must use a distinct client ID.
- **Client library:** `ib_insync` (maintained fork: `ib_async`) will be used instead of the raw `ibapi` package. It wraps the official socket protocol with an asyncio-friendly, higher-level API, reducing boilerplate for connection management and request/response handling.
- **Configuration surface:** host, port, and client ID must be externally configurable (e.g., via `python-decouple` `.env` settings, consistent with existing project conventions) rather than hardcoded, so the same code can target paper vs live endpoints later.
- **Supported read-only requests:** account summary, positions, executed trades/fills, and open orders (informational only) — no order placement or account modification calls will be implemented.

## CSV/Live Compatibility Verification (Task 1.2)

- `src/parser.py::save_to_database()` already accepts a source-agnostic `{"trades": [], "dividends": [], "taxes": [], "corp_actions": []}` dict and only relies on normalized record fields (date, ticker, currency, qty, price, type, commission, source, ratio). It has no CSV-specific coupling.
- A live IB connector can therefore build the same dict shape from Gateway payloads and call `save_to_database()` directly, without touching `parse_csv()` or any CSV-specific code path.
- `main.py::run_import_routine()` (CSV path) and a future live-sync entry point can coexist as separate functions that both terminate at `save_to_database()`; neither needs to know about the other's data source.
- Conclusion: no changes to the existing CSV import workflow are required to add the live-API path. The live connector is purely additive.

## Open Questions

- Should the live data sync be a manual “connect and import” action, or should it also support periodic refresh in the GUI?
- Should the feature support only account/trade history, or also market data snapshots for planned-sale validation?
