# Sprint History

## Sprint 1 & 2: Foundations (Completed)
* Basic CSV parsing.
* FIFO Logic (deque based).
* NBP API integration.

## Sprint 3: Security & Reporting (CURRENT - COMPLETED)
* **Architecture Shift:** Migrated from JSON Snapshots to **SQLCipher Database**.
* **New Modules:**
    * `src/db_connector.py`: Handles encrypted connections.
    * `src/data_collector.py`: Prepares DataFrames for Excel.
    * `src/excel_exporter.py`: Generates multi-sheet .xlsx.
* **Refactoring:**
    * `src/parser.py`: Now writes directly to DB via transactions.
    * `src/processing.py`: optimized to link Taxes to Dividends using a hash map.
* **Reporting:**
    * PDF now highlights Sanctioned Assets (SBER, YNDX, etc.).
    * Excel includes separate sheets for Sales, Dividends, and Inventory.

## Upcoming Sprint 4
* [ ] GUI (Graphical User Interface).
* [ ] Advanced Merger/Spinoff Wizard.