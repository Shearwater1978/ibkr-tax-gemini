# Project Specification: IBKR Tax Assistant (Poland/PIT-38)

### Core Logic

1.  **Data Ingestion:**
    * **Inputs:** Multiple CSVs (Activity Reports) + `manual_history.csv`.
    * **Filtering:** Ignores technical transfers (ACATS, Inter-Company) *unless* they resolve negative inventory.
    
2.  **Complex Parsing:**
    * **Escrow/Sanctions:** Detects `Tendered` events. Balances old ADRs to 0 and creates new local shares via Transfer logic.
    * **Spinoffs/Mergers:** Regex extraction of Target Ticker from description body (e.g. `(WBD, ...)`).

3.  **Financial Engine:**
    * **FIFO:** Consumes batches chronologically.
    * **Cost Basis:** Adjusted for Splits. Spin-off shares = 0 cost.
    * **NBP:** Official rates (D-1).

4.  **Reporting (PDF):**
    * **Visuals:** Zebra striping. **Red Highlight** for restricted (RUB) assets.
    * **Pagination:** Footers with App Version and Page Numbers.
    * **Structure:** Portfolio, History, Corp Actions, Monthly Divs, Detailed Divs, PIT-38 Helper.

5.  **Quality:**
    * `pytest` coverage, `flake8` linting, `bandit` security scan.