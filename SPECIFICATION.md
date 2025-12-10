# Technical Specification (Sprint 3)

## 1. Data Flow Pipeline

1.  **Ingestion (ETL):**
    * Input: IBKR CSV Flex Query.
    * Process: `src.parser` reads CSV -> identifies Dividends/Trades/Taxes -> Normalizes data.
    * Output: Inserts rows into `transactions` table in SQLCipher.

2.  **Storage:**
    * Engine: SQLCipher (SQLite + AES-256).
    * Schema: Consolidated `transactions` table with `EventType` (BUY, SELL, DIVIDEND, TAX).

3.  **Processing (Runtime):**
    * `main.py` initializes `DBConnector`.
    * `src.processing` loads raw rows for the target year + historical buys.
    * **Tax Linking:** Pre-scans `TAX` events and maps them to `DIVIDEND` events by (Date, Ticker).
    * **FIFO:** `src.fifo.TradeMatcher` processes trades to calculate Realized P&L.

4.  **Reporting:**
    * `src.data_collector` aggregates results into Pandas DataFrames.
    * `src.excel_exporter` writes `.xlsx` with tabs: Summary, Sales P&L, Dividends, Open Positions.
    * `src.report_pdf` uses ReportLab to create a printable statement.

## 2. Key Logic Details

* **Sanctions Check:** Hardcoded list of tickers (e.g. SBER, GAZP) in `main.py` marks assets in PDF as Restricted.
* **Currency:** All foreign amounts converted to PLN using NBP mid-rate (T-1).
* **Cost Basis:** Includes purchase commissions. Sales commissions reduce proceeds.