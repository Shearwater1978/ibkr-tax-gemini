# Technical Specification: IBKR Tax Assistant (v1.2.0)

## 1. Project Goal
Automate tax calculation (PIT-38) for Polish tax residents using Interactive Brokers.
Features support for complex corporate actions, FIFO methodology, currency conversion (NBP D-1), and history optimization via Snapshots.

## 2. Architecture
* **Language:** Python 3.10+
* **Core Modules:**
    * `src/parser.py`: CSV parsing, deduplication (GE), date fixes (KVUE), hidden ticker extraction (Spin-offs).
    * `src/fifo.py`: FIFO Engine. Supports `save_state`/`load_state` (JSON) for inventory rollover between years.
    * `src/processing.py`: Orchestrator. Loads Snapshots and filters out processed historical trades.
    * `src/nbp.py`: National Bank of Poland API client.
* **Tools:**
    * `main.py`: Entry point. Automatically detects and loads the appropriate snapshot for the target year.
    * `create_snapshot.py`: Utility to generate a JSON inventory snapshot at year-end.
    * `src/report_pdf.py`: PDF Generator (PIT-38, Monthly Divs, Reconciliation Check).

## 3. Business Logic

### 3.1. Parsing & Data Normalization
* **Data Corrections:**
    * KVUE: Hardcoded date `2023-08-23` for "Voluntary Offer" events (Time Travel Fix).
    * GE: Removal of duplicate split entries within a single file.
    * Spin-offs: WBD, OGN, FG extracted from text descriptions.
* **FIFO Priority:** Intra-day operations are sorted: Split -> Transfer/Buy -> Sell.

### 3.2. Optimization (Snapshots)
* **Problem:** Parsing 5-10 years of CSV history is inefficient.
* **Solution:**
    1.  User generates `snapshot_YYYY.json` (inventory state as of Dec 31, YYYY).
    2.  When calculating year `YYYY+1`, the script loads the JSON as the Cost Basis foundation.
    3.  Historical CSVs can be archived/deleted; FIFO remains consistent.

### 3.3. Tax Math & Reporting
* **Reconciliation:** Compares "Broker View" vs "FIFO Engine View". Status: `OK` or `MISMATCH`.
* **Visuals:** Red highlighting for restricted assets (e.g., RUB, Sanctions).
* **Layout:** "Spacious" mode for dividend details (easy bank statement verification).

## 4. Tech Stack
* `reportlab` (PDF generation)
* `requests` (API calls)
* `pytest` (Edge case testing)
