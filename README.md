# 🇵🇱 IBKR Tax Assistant (Poland / PIT-38)

A Python tool to automate tax calculations for **Interactive Brokers (IBKR)** specifically for **Polish tax residents**. 

It parses your IBKR Activity Reports (CSV), calculates Capital Gains (FIFO) and Dividends using precise **NBP (National Bank of Poland)** exchange rates (D-1 rule), and generates a PDF report ready for your PIT-38 tax declaration.

---

## 🚀 Features

* **Multi-year Support:** Automatically detects taxable years from your data.
* **FIFO Logic:** Strict First-In-First-Out algorithm for calculating cost basis (Koszty) and revenue (Przychód).
* **Advanced Corporate Actions:**
    * **Stock Splits:** Correctly handles Forward and Reverse splits to preserve Cost Basis.
    * **Stock Dividends / Spin-offs:** Treats stock distributions as acquisition with 0 cost (Polish tax rule).
* **NBP Integration:**
    * Fetches official exchange rates from NBP API (D-1 rule).
    * Handles holidays and weekends automatically.
    * **Bulk Caching:** Downloads yearly rates to minimize API calls.
* **Smart Parsing:**
    * Filters out non-taxable events (ACATS, Inter-Company Transfers).
    * Supports `manual_history.csv` for importing trade history from previous brokers.
* **Reports:**
    * **PDF:** Professional report including:
        * Portfolio Snapshot.
        * Trades History & Corporate Actions log.
        * **Detailed Dividends Breakdown** (per payment).
        * **PIT-38 Helper** (Final Section C and Dividends numbers).
    * **JSON:** Full detailed data export.
* **Quality & Security:**
    * **Unit Tests:** `pytest` suite covering math, FIFO, and parsing.
    * **Security:** Checked with `Bandit` (SHA256 hashing).
    * **CI/CD:** GitHub Actions pipeline for tests and linting (`flake8`).

## 🛠️ Installation

1.  **Clone the repository:**
    ```bash
    git clone https://github.com/YOUR_USERNAME/ibkr-tax-poland.git
    cd ibkr-tax-poland
    ```

2.  **Install dependencies:**
    ```bash
    pip install -r requirements.txt
    ```
    *(Optional) Install pre-commit hooks:*
    ```bash
    pre-commit install
    ```

## 📂 Usage

1.  **Export Data from IBKR:**
    * Create a Custom Query in Flex Queries including **Trades**, **Dividends**, **Withholding Tax**, and **Corporate Actions**.
    * Download CSV files.

2.  **Prepare Data:**
    * Place your `.csv` files into the `data/` folder.

3.  **Run the Tool:**
    ```bash
    python main.py
    ```

4.  **Get Results:**
    * Check the `output/` folder for `tax_report_YYYY.pdf`.

## 🧪 Running Tests

```bash
# Run all tests
pytest -v
```

## ⚠️ Disclaimer

**I am not a tax advisor.** This software is for informational purposes only. Always verify the numbers with your official broker statements and consult a qualified tax professional before filing.