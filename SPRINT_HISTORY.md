# Sprint History

## Sprint 1: Core Logic (Completed)
* Basic CSV parsing.
* Initial FIFO implementation.
* NBP API integration.

## Sprint 2: Architecture & CLI (Completed)
* Refactoring into `src/` modules.
* Unified CLI entry point.
* Added `tests/` infrastructure.

## Sprint 3: Security & Stability (Completed - v2.1.0)
* **Security:** Implementation of **SQLCipher** (AES-256) for local DB encryption.
* **Quality Assurance:** Migration to `pytest` framework with parametrized tests.
* **Robust Parsing:** Fixed regex logic to handle ticker symbols with spaces (e.g., `MGA (ISIN)`).
* **Architecture:** Unified `main.py` to handle both Import and Reporting (Single Entry Point).
* **Documentation:** Created Master Restart Prompt for context preservation.

## Sprint 4: Modern UI (UPCOMING)
**Goal:** Move from CLI to a user-friendly Desktop Application.
**Stack:** Electron (Frontend) + FastAPI (Python Backend).
**Target:** Cross-platform support (Windows 11 / macOS).

### Key Requirements (Updated):
1.  **Single Source of Truth:** The UI must read/write to the EXISTING encrypted database (`ibkr_history.db.enc`).
2.  **Dynamic Filtering:** Users can select a Tax Year based on actual data availability in the DB.
3.  **Export Capability:** Buttons to generate PDF and Excel reports (saved to `output/`).
4.  **Re-Import Workflow:** Trigger the CSV parsing routine from the UI, updating the existing DB seamlessly.

### Implementation Tasks:
* [ ] **Backend API:** Create FastAPI endpoints for `/years`, `/calculate`, `/export`, and `/import`.
* [ ] **Frontend Core:** Setup Electron with IPC bridge.
* [ ] **Dashboard:** Visual summary of Portfolio, P&L, and Dividends.
* [ ] **Packaging:** Build executables (.exe / .app).

## Future Ideas (Backlog)
* [ ] Cloud Sync (Optional).
* [ ] Real-time stock prices integration.
* [ ] Multi-user support.
