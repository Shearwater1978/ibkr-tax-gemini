# 🇵🇱 IBKR Tax Calculator (PIT-38 Poland)

**Automated Capital Gains & Dividend Tax Calculator for Polish Residents using Interactive Brokers.**

![Python](https://img.shields.io/badge/Python-3.10%2B-blue)
![License](https://img.shields.io/badge/License-MIT-green)
![Security](https://img.shields.io/badge/Security-SQLCipher%20AES--256-red)

## 🚀 Key Features

* **Privacy First:** All financial data is stored in a local **SQLCipher (AES-256)** encrypted database. Your data never leaves your machine.
* **Universal Parser:** Supports both **Activity Statements** (Default) and **Flex Queries**. Handles dynamic column orders and weird date formats.
* **Smart NBP Rates:** Automatically fetches FX rates from the National Bank of Poland (NBP) using the **T-1 rule**. Uses **Batch Caching** to minimize API calls (12 requests per year vs 400+).
* **FIFO Algorithm:** Strictly follows "First-In-First-Out" logic for correct Cost Basis calculation.
* **PIT-38 Ready:** Generates a PDF report that maps directly to Polish tax forms (Przychód, Koszty, Podatek zapłacony).

## 📦 Installation

1.  **Clone the repository:**
    ```bash
    git clone [https://github.com/your-repo/ibkr-tax-pl.git](https://github.com/your-repo/ibkr-tax-pl.git)
    cd ibkr-tax-pl
    ```

2.  **Install dependencies:**
    *(Note: You need SQLCipher installed on your OS for `pysqlcipher3`)*
    ```bash
    pip install -r requirements.txt
    ```

3.  **Security Setup:**
    Create a `.env` file in the root directory:
    ```ini
    SQLCIPHER_KEY=your_super_secret_password_here
    DATABASE_PATH=data/ibkr_history.db.enc
    ```

## 🏃 Usage

### Step 1: Import Data
Download your CSV reports from IBKR (Activity Statement or Flex Query) and place them in the `data/` folder.
```bash
python -m src.parser --files "data/*.csv"
```
*This parses trades, dividends, and taxes into the encrypted database.*

### Step 2: Generate Report
Calculate taxes for a specific year (e.g., 2024):
```bash
python main.py --target-year 2024 --export-pdf --export-excel
```

### Step 3: Get Results
Check the `output/` folder:
* `tax_report_2024.pdf` - For printing and filing.
* `tax_report_2024.xlsx` - For detailed audit and verification.

## ⚠️ Disclaimer
This software is for educational purposes only. I am not a tax advisor. Always verify the results with a professional accountant before filing your taxes.