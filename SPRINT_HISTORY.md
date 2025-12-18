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

## Sprint 4: GUI Implementation & UX (Completed - 2025-12-18)
* [x] **Architecture:** Implemented Electron + FastAPI (Uvicorn) bridge.
* [x] **Database Integration:** Direct connection between Python Backend and SQLCipher (`transactions` table).
* [x] **UI Development:**
    * Developed interactive HTML/JS dashboard.
    * Implemented asynchronous data loading (Years fetch, CSV Import).
    * Added real-time metrics display (P&L, Dividends, Open Lots).
* [x] **UX Enhancements:**
    * Added loading spinners and button state management for long-running calculations.
    * Implemented direct "Open File" system integration for Excel and PDF reports.
* [x] **Stability:** Fixed Windows-specific encoding issues (UTF-8/Charmap) and socket port management.

## Upcoming Sprint 5
* [ ] Advanced Corporate Action wizard.
* [ ] Multi-account support (filtering by IBKR Account ID).
* [ ] Automatic NBP rate caching in a separate DB table.
