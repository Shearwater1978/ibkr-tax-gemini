# Project Specification: IBKR Tax Assistant (Poland/PIT-38)

### Role
Act as a Senior Python Developer and Financial Tech Expert.

### Goal
Create a robust Python application to automate tax calculations for **Interactive Brokers (IBKR)** specifically for **Polish Tax Residents**. The output must facilitate filling out the **PIT-38** tax form.

### Core Features & Logic

1.  **Input Data Management:**
    * **Source:** Scan `data/` for multiple CSV files.
    * **Filtering:** Explicitly ignore dummy tickers and non-taxable transfers (ACATS, Inter-Company).
    * **Manual History:** Support `manual_history.csv` for "Genesis Block" trades.

2.  **Parsing Engine:**
    * Extract **Trades**, **Dividends**, **Withholding Taxes**, and **Corporate Actions**.
    * **Corporate Actions:**
        * **Splits:** Parse "Split X for Y" to adjust inventory qty/price without taxable events.
        * **Stock Dividends:** Treat as BUY with 0.0 cost.

3.  **Financial Logic (The Core):**
    * **FIFO (First-In-First-Out):** Chronological matching of Sells to Buys.
    * **NBP Exchange Rates:** D-1 rule, recursion for holidays, bulk caching.
    * **Security:** Use SHA256 for deduplication signatures (Bandit compliant).

4.  **Reporting (PDF Output):**
    * **Library:** `reportlab`.
    * **Structure:**
        1.  **Title Page**
        2.  **Portfolio Composition**
        3.  **Trades History**
        4.  **Corporate Actions Log:** (Splits, Spin-offs)
        5.  **Monthly Dividends Summary:** (Totals)
        6.  **Detailed Dividends:** (Chronological list of every payment with NBP rates)
        7.  **Yearly Summary:** Metrics & Diagnostics.
        8.  **PIT-38 Helper:** Final numbers for Section C and Dividends.

5.  **Quality Assurance:**
    * **Tests:** `pytest` for FIFO, Parser, NBP.
    * **CI/CD:** GitHub Actions for Linting (`flake8`) and Security (`bandit`).