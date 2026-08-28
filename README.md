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

## ⚠️ Disclaimer
Educational purpose only. Not financial advice.
