# 🇵🇱 IBKR Tax Calculator (PIT-38 Poland) v2.2.0

**Automated Capital Gains & Dividend Tax Calculator for Polish Residents using Interactive Brokers.**

![Python](https://img.shields.io/badge/Python-3.10%2B-blue)
![License](https://img.shields.io/badge/License-MIT-green)
![Security](https://img.shields.io/badge/Security-SQLCipher%20AES--256-red)
![Version](https://img.shields.io/badge/Release-v2.2.0-orange)

## 🚀 Key Features

* **Privacy First:** All financial data is stored in a local **SQLCipher (AES-256)** encrypted database.
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
# Automatically scans the 'data/' folder for CSV files and updates the DB.
python main.py --import-data

# 2. Generate Report
# Calculates taxes for the specific year using FIFO and NBP rates.
python main.py --target-year 2024 --export-pdf --export-excel
```

## ⚠️ Disclaimer
Educational purpose only. Not financial advice.
