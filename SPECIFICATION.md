# Project Specification: IBKR Tax Assistant (Poland/PIT-38)

### Role
Act as a Senior Python Developer and Financial Tech Expert.

### Goal
Create a robust Python application to automate tax calculations for **Interactive Brokers (IBKR)** specifically for **Polish Tax Residents**. The output must facilitate filling out the **PIT-38** tax form.

### Core Features & Logic

1.  **Input Data Management:**
    * **Source:** Scan a `data/` directory for multiple CSV files (IBKR Activity Reports).
    * **Flexibility:** Support ingestion of monthly, yearly, or mixed timeframe reports.
    * **Manual History:** Support `manual_history.csv` for importing historical trades ("Genesis Block") not present in IBKR reports.
    * **Filtering:** Explicitly ignore specific dummy tickers (e.g., "EXAMPLE", "DUMMY") to prevent test data from polluting tax reports.

2.  **Parsing Engine:**
    * Extract **Trades** (Buy/Sell), **Dividends**, and **Withholding Taxes**.
    * **Transfer Handling:** Identify and filter out non-taxable events like ACATS/Internal Transfers (do not treat them as Buy/Sell events, but maintain portfolio integrity).
    * **Ticker Extraction:** Robust regex logic to extract tickers from IBKR descriptions (e.g., `MSFT(US...)`).

3.  **Financial Logic (The Core):**
    * **FIFO (First-In-First-Out):** Match Sells with Buys chronologically to calculate Realized P&L.
    * **NBP Exchange Rates:**
        * Fetch official rates from **National Bank of Poland (NBP)** API.
        * **D-1 Rule:** Strictly apply the tax rule: use the exchange rate from the *business day preceding* the event.
        * **Smart Recursion:** If D-1 is a holiday/weekend, recurse backwards until a valid rate is found.
    * **Optimization (Bulk Cache):** Instead of daily API calls, download the *entire year's* exchange rates in one request and cache them locally (JSON) to maximize performance.
    * **Precision:** Use Python's `Decimal` type for all monetary calculations to prevent floating-point errors.

4.  **Reporting (PDF Output):**
    * **Library:** `reportlab`.
    * **Structure (6 Pages):**
        1.  **Title Page:** Tax Year and Reporting Period (DD-MM-YYYY).
        2.  **Portfolio:** Snapshot of holdings at year-end (Ticker, Quantity to 3 decimals).
        3.  **Trades History:** Chronological list of executions in the tax year. Start on a new page.
        4.  **Monthly Dividends:** Aggregated by month (Gross, Tax, Net).
        5.  **Yearly Summary:** High-level metrics, diagnostics (row counts), and per-currency totals.
        6.  **PIT-38 Helper:** Final numbers for **Section C** (Capital Gains) and **Dividends** (Foreign Tax), formatted exactly as needed for the tax declaration.
    * **Styling:** Professional "Zebra" striping (alternating grey/white rows) for readability. Headers must be centered.

5.  **Quality Assurance:**
    * **Unit Tests (`pytest`):**
        * Mock NBP API calls to test offline.
        * Test FIFO matching logic with partial sells and multiple buys.
        * Test Parser filters (dummy tickers).
        * Test Rounding logic (Banker's rounding).
    * **Clean Code:** English comments, type hinting, and modular structure.

### Project Structure

```text
project_root/
├── src/
│   ├── parser.py       # CSV parsing & regex
│   ├── fifo.py         # Trade matching logic
│   ├── nbp.py          # API & Caching logic
│   ├── processing.py   # Data aggregation & orchestration
│   ├── report_pdf.py   # PDF Generation (ReportLab)
│   └── utils.py        # Math helpers (Decimal)
├── tests/              # Pytest suite
├── data/               # Place for user CSV files
├── output/             # Generated PDFs and JSONs
├── main.py             # Entry point
└── manual_history.csv  # Optional manual inputs
```

### Execution Flow
1.  **Ingest:** Load `manual_history.csv` + all CSVs in `data/`.
2.  **Deduplicate:** Use hash signatures to prevent duplicate entries if reports overlap.
3.  **Detect Years:** Identify which years have taxable events (Sells or Dividends).
4.  **Process:** For each detected year:
    * Fetch NBP rates (Bulk).
    * Calculate FIFO & Taxes.
    * Generate JSON & PDF.