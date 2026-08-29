# 🇵🇱 IBKR Tax Calculator (PIT-38 Poland) v2.2.0

**Automated Capital Gains & Dividend Tax Calculator for Polish Residents using Interactive Brokers.**

![Python](https://img.shields.io/badge/Python-3.10%2B-blue)
![License](https://img.shields.io/badge/License-MIT-green)
![Security](https://img.shields.io/badge/Security-SQLCipher%20AES--256-red)
![Version](https://img.shields.io/badge/Release-v2.2.0-orange)

## 🚀 Key Features

* **Privacy First:** All financial data is stored in a local **SQLCipher (AES-256)** encrypted database.
    The SQLCipher driver and a non-empty `SQLCIPHER_KEY` are required; the application refuses plaintext SQLite.
* **Universal Parser:** Supports both **Activity Statements** and **Flex Queries**.
* **Smart NBP Rates:** T-1 rule compliant, using **Batch Caching** logic.
* **FIFO Algorithm:** Strictly follows tax laws for Cost Basis.
* **PIT-38 Ready:** Generates a PDF report compatible with Polish tax forms.

## 📦 Installation

1.  **Clone:**
    ```bash
    git clone [https://github.com/your-repo/ibkr-tax-gemini.git](https://github.com/your-repo/ibkr-tax-gemini.git)
    cd ibkr-tax-gemini
    ```

2.  **Install:**
    ```bash
    pip install -r requirements.txt
    ```

    This installs the `sqlcipher3` driver required for encrypted database access.

3.  **Setup Security:**
    Create `.env`:
    ```ini
    SQLCIPHER_KEY=your_secret_key
    DATABASE_PATH=db/ibkr_history.db.enc
    ```

## 🏃 Usage

The `main.py` script is now the single entry point for all operations.

```bash
# 1. Import Data
# Automatically scans the project 'data/' folder and adds only new records.
# Repeated imports are idempotent and report inserted/skipped counts.
python main.py --import-data

# 2. Generate Report
# Calculates taxes for the specific year using FIFO and NBP rates.
python main.py --target-year 2024 --export-pdf --export-excel
```

### FIFO coverage preflight

Check whether imported history contains enough FIFO inventory for planned sales.
This is evidence for a preflight review only: it does not calculate tax, create a
sale, or persist any transaction.

```bash
python main.py --planned-sale AAPL:10:2024-12-31 --coverage-output json
python main.py --coverage-file planned-sales.json
```

The JSON file contains an array of objects with `ticker`, `quantity`, and `as_of`.
The report includes per-asset status, available and missing quantity, and FIFO
lot evidence. `NOT_COVERED` with `history_found: false` means no broker history
was imported for that asset; it is distinct from an imported history with zero
remaining holdings.

Calculations stop with a diagnostic if an NBP rate is unavailable or a sale
cannot be matched to inventory. Export failures are also reported as errors;
the application does not present incomplete tax totals as valid reports.

### Desktop GUI

Install Python dependencies, then start the Electron dashboard:

```bash
cd gui
npm install
npm start
```

The GUI starts a local backend on `127.0.0.1:8000`, waits for its `/health`
endpoint, and uses the same encrypted database and report output as the CLI.

### IB Live API (optional)

CSV import from `data/` remains the default and required workflow. A
read-only live connection to Interactive Brokers TWS/IB Gateway is available
as an **opt-in** alternative/supplement for pulling recent executed trades
directly from a running Gateway session.

**Setup requirements:**

1. Install and run **IB Gateway** (recommended) or TWS, and log in to a
   **paper trading** account (recommended for testing) or live account.
2. In IB Gateway/TWS, enable API access: *Configure → Settings → API →
   Enable ActiveX and Socket Clients*, and add `127.0.0.1` to trusted IPs.
3. Note the socket port shown in the API settings. Defaults used by this
   project: `4002` (paper Gateway), `4001` (live Gateway), `7497` (paper
   TWS), `7496` (live TWS).
4. Add to your `.env` to opt in (live sync is disabled unless set):
   ```ini
   IB_LIVE_ENABLED=True
   IB_HOST=127.0.0.1
   IB_PORT=4002
   IB_CLIENT_ID=1
   ```
   Each concurrent connection to the same Gateway must use a distinct
   `IB_CLIENT_ID`.
5. In the desktop GUI, use **Check IB Connection** to verify connectivity
   and **Live Sync** to pull and store new executions. Both actions report
   success/failure in place; neither blocks CSV import if Gateway is down.

**Operational limits of read-only access:**

* Only executed trades (fills), positions, account summary, and open orders
  are requested. The connector never places, modifies, or cancels orders.
* Dividends, withholding taxes, and corporate actions are **not** available
  through this live path; they still require CSV/Flex Query import.
* Fills reflect executions visible to the current API session. For full
  historical trade history, use CSV import instead.
* If IB Gateway/TWS is not running, not configured, or the session is lost,
  the live sync reports a clear error and CSV import continues to work
  unaffected.

## ⚠️ Disclaimer
Educational purpose only. Not financial advice.
