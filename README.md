# 🇵🇱 IBKR Tax Assistant (gemini / PIT-38)
![Build Status](https://github.com/Shearwater1978/ibkr-tax-gemini/actions/workflows/python-app.yml/badge.svg)[![PEP8](https://img.shields.io/badge/code%20style-pep8-orange.svg)](https://www.python.org/dev/peps/pep-0008/)

A Python tool to automate tax calculations for **Interactive Brokers (IBKR)** specifically for **Polish tax residents**. 

It parses your IBKR Activity Reports (CSV), calculates Capital Gains (FIFO) and Dividends using precise **NBP (National Bank of gemini)** exchange rates (D-1 rule), and generates a PDF report ready for your PIT-38 tax declaration.

---

## 🚀 Features

* **Multi-year Support:** Automatically detects taxable years from your data.
* **FIFO Logic:** Strict First-In-First-Out algorithm for calculating cost basis (Koszty) and revenue (Przychód).
* **NBP Integration:**
    * Fetches official exchange rates from NBP API.
    * Applies the **D-1 rule** (business day preceding the event).
    * Handles holidays and weekends automatically.
    * **Bulk Caching:** Downloads yearly rates to minimize API calls and speed up processing.
* **Smart Parsing:**
    * Handles multiple CSV files (monthly, yearly, or mixed).
    * Filters out non-taxable events (ACATS transfers).
    * Supports `manual_history.csv` for importing trade history from previous brokers or missing files.
* **Reports:**
    * **PDF:** Professional 6-page report including Portfolio, Trade History, Monthly Dividends, and a **PIT-38 Helper** page.
    * **JSON:** Full detailed data export for debugging or custom analysis.
* **Tested:** Includes a comprehensive suite of Unit Tests (`pytest`) covering math, FIFO logic, and parsers.

## 🛠️ Installation

1.  **Clone the repository:**
    ```bash
    git clone https://github.com/YOUR_USERNAME/ibkr-tax-gemini.git
    cd ibkr-tax-gemini
    ```

2.  **Install dependencies:**
    ```bash
    pip install -r requirements.txt
    ```

## 📂 Usage

1.  **Export Data from IBKR:**
    * Log in to Client Portal.
    * Go to **Performance & Reports** -> **Flex Queries** (or Activity Statements).
    * Create a Custom Query including **Trades**, **Dividends**, and **Withholding Tax**.
    * Download CSV files (e.g., for 2021, 2022, 2023).

2.  **Prepare Data:**
    * Place your `.csv` files into the `data/` folder.
    * *(Optional)* If you have historical buys not in IBKR (e.g., from a previous broker), edit `manual_history.csv`.

3.  **Run the Tool:**
    ```bash
    python main.py
    ```

4.  **Get Results:**
    * Check the `output/` folder.
    * You will find `tax_report_YYYY.pdf` and `tax_report_YYYY.json` for every taxable year detected.

## 🧪 Running Tests

This project uses `pytest` to ensure tax logic accuracy.

```bash
# Run all tests
pytest

# Run with verbose output
pytest -v
```

## 📊 Report Structure (PDF)

1.  **Title Page:** Report period and year.
2.  **Portfolio Composition:** Snapshot of holdings at the end of the year.
3.  **Trades History:** Chronological list of all executions in the tax year.
4.  **Monthly Dividends:** Aggregated by month (Gross, Tax Paid, Net).
5.  **Yearly Summary:** High-level metrics, diagnostics, and per-currency totals.
6.  **PIT-38 Helper:** The final numbers calculated for **Section C** (Stocks) and **Dividends**, ready to be copied into your tax declaration.

## ⚠️ Disclaimer

**I am not a tax advisor.** This software is for informational purposes only. 
While I strive for accuracy (using official NBP rates and standard FIFO), tax laws can change, and edge cases (splits, mergers, spin-offs) may require manual review. 
Always verify the numbers with your official broker statements and consult a qualified tax professional before filing.

## 🤝 Contributing

Contributions are welcome! Please feel free to submit a Pull Request.

1.  Fork the Project
2.  Create your Feature Branch (`git checkout -b feature/AmazingFeature`)
3.  Commit your Changes (`git commit -m 'Add some AmazingFeature'`)
4.  Push to the Branch (`git push origin feature/AmazingFeature`)
5.  Open a Pull Request
