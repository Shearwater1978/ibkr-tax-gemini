# Sprint History

## Sprint 1: Core Logic (Completed)
* Basic CSV parsing.
* Initial FIFO implementation.
* NBP API integration.

## Sprint 2: Architecture & CLI (Completed)
* Refactoring into `src/` modules.
* Added `tax_cli.py` (deprecated in v1.1, replaced by `main.py`).
* Added `tests/`.

## Sprint 3: Security & Reporting (Completed - 2025-12-09)
* **Database Migration:** Switched from H2/SQLite to **SQLCipher** (Encrypted SQLite).
* **Documentation:** Created Master Restart Prompt for context preservation.
* **Security:** Implementation of **SQLCipher** (AES-256) for local DB encryption.
* **Quality Assurance:** Migration to `pytest` framework with parametrized tests.
* **Robust Parsing:** Fixed regex logic to handle ticker symbols with spaces (e.g., `MGA (ISIN)`).
* **Refactoring:**
    * Updated `src/db_connector.py` to handle encryption keys from `.env`.
    * Updated `src/parser.py` to write directly to SQLCipher via transactions.
    * Created `src/processing.py` as a bridge between DB and Logic.
    * Updated `src/fifo.py` to support object-based matching and serialization.
* **Reporting:**
    * **Excel:** Added multi-sheet export (Sales, Dividends, Inventory).
    * **PDF:** Restored and enhanced PDF generation.
        * Added logic to aggregate Inventory by Ticker.
        * Added highlighting for Restricted Assets (SBER, YNDX, etc.).
        * Filtered "Trades History" to exclude Dividend/Tax rows.
* **Fixes:**
    * **Withholding Tax:** Implemented logic in `processing.py` to map TAX rows to DIVIDEND rows by date/ticker.
    * **FIFO:** Fixed P&L calculation to include buy/sell commissions in Cost Basis.

## Sprint 4: Bug fixing (Completed - 2026-08-25)
* [x] **Security:** Replaced the plaintext `sqlite3` + `PRAGMA key` stub with a real **SQLCipher** (AES-256) driver boundary (`sqlcipher3`); plaintext SQLite fallback is now rejected and the schema is verified after key application.
* [x] **Secrets:** Removed secret interpolation from SQL text; keys are never logged or included in exception messages, and a missing key or missing driver fails closed.
* [x] **Key Rotation:** Rewrote `tools/change_key.py` to open with the current key, rekey, then reopen to verify the new key before reporting success.
* [x] **Verification:** Added temporary-database tests for encrypted open, wrong-key rejection, missing driver, missing key, and key rotation.

## Sprint 5: GUI Implementation & UX (Completed - 2026-08-25)
* [x] **Architecture:** Implemented Electron + FastAPI (Uvicorn) bridge (`gui/main.js`, `gui/preload.js`, `gui/backend/api.py`).
* [x] **Database Integration:** Direct connection between Python Backend and SQLCipher (`transactions` table).
* [x] **UI Development:**
    * Developed interactive HTML/JS dashboard (`gui/ui/index.html`).
    * Implemented asynchronous data loading (Years fetch, CSV Import).
    * Added real-time metrics display (P&L, Dividends, Open Lots).
* [x] **UX Enhancements:**
    * Added loading spinners and button state management for long-running calculations.
    * Implemented direct "Open File" system integration for Excel and PDF reports.
* [x] **Stability:** Fixed Windows-specific encoding issues (UTF-8/Charmap) and socket port management.
* [x] **Environment Sync:** Ensuring shared data paths between UI and CLI.
* [x] **Single Source of Truth:** The UI reads/writes the EXISTING encrypted database (`ibkr_history.db.enc`).
* [x] **Implementation Tasks:**
    * [x] **Backend API:** FastAPI endpoints for `/years`, `/calculate`, `/export`, and `/import`.
    * [x] **Frontend Core:** Electron with IPC bridge, `nodeIntegration` disabled and `contextIsolation` enabled.
    * [x] **Dashboard:** Visual summary of Portfolio, P&L, and Dividends.
    * [ ] **Packaging:** Build executables (.exe / .app) - still open, no `electron-builder` in `gui/package.json`.

## Sprint 6: IB Web API Integration (Completed - 2026-09-01)
* **CPGW Connector:** Added an optional, read-only Client Portal Gateway connector with browser-session authentication, health checks, keep-alive, and actionable connection diagnostics.
* **Data Safety:** Normalized Web API trades into the existing import schema and reused `SourceKey` deduplication for idempotent syncs.
* **GUI/API:** Added independent CPGW status and manual sync endpoints plus dashboard controls, without affecting CSV import or TWS/Gateway sync.
* **Documentation:** Documented CPGW setup, browser login/2FA, local TLS configuration, and read-only operational limits in `README.md`.
* **Verification:** Added focused coverage for session errors, normalization, deduplication, sync workflow, and connector independence; full suite passed (147 tests).

## Sprint 7: Import Integrity (Completed - 2026-08-25)
* **Idempotency:** Introduced source-record identity (`SourceKey`) and atomic upserts, so re-importing a statement updates in place and inserts no duplicate rows.
* **Import Summary:** Import now returns inserted/skipped counts and reports split-ratio extraction and propagation to FIFO.
* **Path Handling:** Centralized project-relative path resolution for CLI, API, and manual fixes.
* **Verification:** Added parser/database integration tests for idempotency, rollback, malformed rows, and split propagation.

## Sprint 8: Calculation Reliability (Completed - 2026-08-25)
* **Diagnostics:** Added shared calculation diagnostic and completeness models consumed by processing, exporters, and the CLI.
* **Rates:** Removed non-PLN `1.0` rate fallbacks and made missing rates explicit instead of silent.
* **FIFO:** Detected residual unmatched sell quantities and propagated them as blocking diagnostics.
* **Reporting:** Made Excel/PDF export failures observable.
* **Verification:** Added tests for NBP outages, unmatched sells, and exporter failures; full suite green.

## Sprint 9: FIFO Coverage Preflight (Completed - 2026-08-28)
* **Coverage Core:** Added read-only planned-sale coverage with `COVERED`/`PARTIAL`/`NOT_COVERED` statuses and FIFO lot evidence, without mutating inventory.
* **CLI / Reports:** Added multi-asset coverage reporting (requested, available, missing, as-of, status, lots) in human-readable and machine-readable form.
* **GUI / API:** Added a coverage endpoint and a dashboard view with explicit empty-history vs. zero-holdings states.
* **Scope:** Documented that coverage is preflight evidence only - it does not calculate tax and never persists a sale.

## Sprint 10: IB Live API (TWS / IB Gateway) (Completed - 2026-08-29)
* **Connector:** Added the TWS/IB Gateway socket connector with connection management and health checks that fail clearly when the gateway is unavailable.
* **Read-only Sync:** Added a read-only account sync mapping live payloads into the existing transaction/corporate-action schema via `src/ib_normalizer.py`.
* **Safety:** Reused `SourceKey` deduplication so duplicate live trades are never inserted.
* **UX:** Added configuration surface for host/port/client ID with ready/error states and a manual "live sync" action; CSV import remains the default workflow.
* **Maintenance:** Fixed Windows cp1252 emoji encoding failures and skipped duplicate SPLIT processing for reverse splits in IBKR CSV.

## Sprint 11: ISIN Change Handling (Completed - 2026-10-01)
* **Storage:** Persisted `ISIN` and `Conid` alongside the ticker via additive `ALTER TABLE` columns with a backfill, so existing encrypted databases open without key rotation.
* **Parsing:** Extracted ISIN from both glued (`OKE(US6826801036)`) and spaced (`MGA (CA5592224011)`) description forms, and from the `Financial Instrument Information` section.
* **Detection:** Derived dated identity changes by grouping history on `(Ticker, ISIN)`; rows with no ISIN resolve to the ticker's earliest observed identity.
* **FIFO:** Changed the lot queue key from raw ticker to instrument identity, so a sale after an ISIN change draws cost basis only from lots of the same ISIN.
* **Reporting:** Added a non-blocking diagnostic (ticker, previous ISIN, new ISIN, change date) surfaced through CLI, `GET /calculate/{year}`, Excel, and PDF.
* **Coverage:** `src/fifo_coverage.py` computes available quantity from the identity in effect on the as-of date.
* **Compatibility:** Output is unchanged when a ticker maps to exactly one ISIN; pre-change code still reads the updated database.

## Open Items
* [ ] **GUI Packaging:** Build `.exe` / `.app` distributables (no `electron-builder` configured in `gui/package.json`).
* [ ] **Wiki Publication:** Manually transfer the reviewed `WIKI_CONTENT.md` to the GitHub Wiki (task 2.3 of the `update-wiki-content` change, intentionally a user action).
