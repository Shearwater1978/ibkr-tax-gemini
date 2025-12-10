# Technical Specification

## 1. Architecture

The application follows a modular ETL (Extract, Transform, Load) pipeline pattern:

1.  **Extract (Parser):**
    * Reads raw CSV files from Interactive Brokers.
    * Identifies events: Trade, Dividend, Tax, Corporate Action (Split/Merger).
    * **Output:** Normalized records inserted into `transactions` table in SQLCipher.

2.  **Storage (SQLCipher):**
    * **File:** `db/ibkr_history.db.enc`
    * **Encryption:** 256-bit AES (via PRAGMA key).
    * **Schema:** `TradeId`, `Date`, `EventType`, `Ticker`, `Quantity`, `Price`, `Amount`, `Fee`, `Currency`.

3.  **Transform (Processing & FIFO):**
    * **Loader:** `src.db_connector` fetches raw rows.
    * **Enrichment:** `src.processing` fetches NBP rates for T-1. It also pre-scans for `TAX` rows to link them with `DIVIDEND` events.
    * **Logic:** `src.fifo.TradeMatcher` queues Buy lots and matches Sells.
    * **Tax Logic:**
        * Capital Gains = (Sale Price * Rate) - (Buy Price * Buy Rate) - Costs.
        * Dividends = (Gross * Rate) - (Foreign Tax * Rate).

4.  **Load/Report (Exporters):**
    * **Excel:** Uses `pandas` and `openpyxl` to generate multi-tab spreadsheets.
    * **PDF:** Uses `reportlab` to generate printable statements. The `main.py` adapter prepares specific data structures.

## 2. Data Flow

```
[CSV Files] -> (src.parser) -> [SQLCipher DB]
                                     |
                                     v
                                (src.db_connector)
                                     |
                                     v
                                (src.processing) <-> [NBP API]
                                     |
                                     v
                                (src.fifo)
                                     |
    +--------------------------------+--------------------------------+
    |                                |                                |
(src.excel_exporter)           (src.report_pdf)              (Console Output)
    |                                |
[ .xlsx Report ]               [ .pdf Report ]
```

## 3. Key Entities

* **TradeMatcher:** State machine that holds inventory (`deque`) for every ticker.
* **Restricted Assets:** Logic in `main.py` checks tickers against a hardcoded set (e.g., SBER, YNDX) to flag them in reports with a red highlight.