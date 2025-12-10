# IBKR Tax Calculator (Poland / PIT-38) - Sprint 3

Automated tax reporting tool for Interactive Brokers users resident in Poland.
Now features **SQLCipher encryption** for data security and advanced Excel/PDF reporting.

## 🚀 Key Features

* **Security:** Financial data is stored in an AES-256 encrypted SQLite database (`db/ibkr_history.db.enc`).
* **Data Import:** robust parsing of IBKR CSV Flex Queries directly into the DB.
* **Reporting:**
    * **PDF:** Audit-ready report with Portfolio Sanctions check (e.g. SBER, RUB) and PIT-38 helper.
    * **Excel:** Multi-tab report (Sales, Dividends, Open Positions) for deep analysis.
* **Logic:** Strict FIFO matching, automatic NBP (PLN) conversion.

## 🛠 Setup

1. **Install Requirements:**
   ```bash
   pip install -r requirements.txt
   ```
   *(Requires `pysqlcipher3` or a compatible driver for SQLCipher)*

2. **Environment Variables:**
   Create a `.env` file:
   ```env
   DATABASE_PATH=data/ibkr_history.db.enc
   SQLCIPHER_KEY='your_secret_key'
   ```

## ⚙️ Usage

### 1. Import Data
Parse your CSV files into the encrypted database:
```bash
python -m src.parser --files "data/*.csv"
```

### 2. Generate Reports
Calculate taxes for a specific year:
```bash
python main.py --target-year 2024 --export-pdf --export-excel
```
Reports will be saved to the `output/` folder.

## 📂 Structure
* `src/db_connector.py`: Encrypted DB management.
* `src/processing.py`: Core logic (Tax mapping, FIFO orchestration).
* `src/report_pdf.py` & `src/excel_exporter.py`: Reporting modules.