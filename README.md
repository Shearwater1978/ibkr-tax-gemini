# IBKR Tax Calculator (Poland / PIT-38)

A comprehensive tool for calculating Capital Gains Tax and Dividends for Polish tax residents using Interactive Brokers (IBKR).

## 🚀 Key Features

* **Security First:** All financial data is stored in an **encrypted SQLCipher database** (`db/ibkr_history.db.enc`).
* **Accurate Calculations:**
    * **FIFO (First-In-First-Out):** Strict lot matching for precise cost basis calculation.
    * **NBP Integration:** Automatic fetching of historical exchange rates (PLN) for T-1.
    * **Corporate Actions:** Handling of Splits, Spin-offs, and Mergers.
* **Reporting:**
    * **Excel:** Detailed breakdown of Sales, Dividends, and Inventory (Multi-tab).
    * **PDF:** Professional report including Portfolio composition, PIT-38 helper data, and Sanctions checks (e.g., restricted assets like SBER, YNDX marked in red).
* **Data Import:**
    * Parsing of IBKR CSV Flex Queries.
    * Support for manual history adjustments.

## 🛠 Prerequisites

* Python 3.12+
* SQLCipher (via `pysqlcipher3` or compatible driver)
* Pandas, ReportLab, OpenPyXL

## ⚙️ Setup & Usage

1.  **Environment Setup**
    Create a `.env` file in the root directory:
    ```env
    DATABASE_PATH=db/ibkr_history.db.enc
    SQLCIPHER_KEY='your_secure_random_key_here'
    ```

2.  **Import Data**
    Parse your broker reports (CSVs) into the encrypted database:
    ```bash
    python -m src.parser --files "data/*.csv"
    ```

3.  **Generate Reports**
    Calculate taxes and generate Excel/PDF reports for a specific year:
    ```bash
    python main.py --target-year 2024 --export-excel --export-pdf
    ```

## 📂 Project Structure

* `main.py`: Entry point. Adapts data for reports, filters history, checks sanctions.
* `src/parser.py`: Entry point for data import. Parses CSVs to DB.
* `src/db_connector.py`: Handles encrypted database connections.
* `src/processing.py`: Core logic bridge. Matches Tax rows to Dividends.
* `src/fifo.py`: Trade matching algorithm (TradeMatcher).