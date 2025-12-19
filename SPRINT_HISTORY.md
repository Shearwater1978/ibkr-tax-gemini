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

## Sprint 4: GUI Implementation & UX (Goal)
* [ ] **Architecture:** Implemented Electron + FastAPI (Uvicorn) bridge.
* [ ] **Database Integration:** Direct connection between Python Backend and SQLCipher (`transactions` table).
* [ ] **UI Development:**
    * Developed interactive HTML/JS dashboard.
    * Implemented asynchronous data loading (Years fetch, CSV Import).
    * Added real-time metrics display (P&L, Dividends, Open Lots).
* [ ] **UX Enhancements:**
    * Added loading spinners and button state management for long-running calculations.
    * Implemented direct "Open File" system integration for Excel and PDF reports.
* [ ] **Stability:** Fixed Windows-specific encoding issues (UTF-8/Charmap) and socket port management.
* [ ] **Environment Sync:** Ensuring shared data paths between UI and CLI.
* [ ] **Single Source of Truth:** The UI must read/write to the EXISTING encrypted database (`ibkr_history.db.enc`).
* [ ] **Single Source of Truth:** The UI must read/write to the EXISTING encrypted database (`ibkr_history.db.enc`).
* [ ] **Implementation Tasks:**
    * [ ] **Backend API:** Create FastAPI endpoints for `/years`, `/calculate`, `/export`, and `/import`.
    * [ ] **Frontend Core:** Setup Electron with IPC bridge.
    * [ ] **Dashboard:** Visual summary of Portfolio, P&L, and Dividends.
    * [ ] **Packaging:** Build executables (.exe / .app).