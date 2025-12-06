# IBKR Tax Assistant (Poland / PIT-38)

**Automated tax reporting tool for Interactive Brokers users resident in Poland.**

This tool parses IBKR Activity Statements (CSV), applies Polish tax rules (FIFO, NBP rates D-1), and generates a clean, audit-ready PDF report including specific data for the PIT-38 declaration.

## Key Features 🚀

* **Robust Parsing:** Handles Spin-offs (WBD, OGN), Mergers (KVUE), Splits (GE), and complex Corporate Actions that standard reports often miss.
* **Audit-Ready PDF:**
    * **Spacious Layout:** Dividend details split by month for easy manual verification.
    * **Sanctions Handling:** Visually highlights restricted assets (RUB).
    * **FIFO Reconciliation:** Automatically checks if the calculated FIFO inventory matches the broker's reported ending positions.

## Usage

1.  Place CSV files in `data/`.
2.  Run `python main.py`.
3.  Get report in `output/report.pdf`.

## Disclaimer
For educational purposes only. Verify with a tax advisor.
