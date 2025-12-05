# 🇵🇱 IBKR Tax Assistant

A production-ready Python tool for **Interactive Brokers (IBKR)** tax calculations in Poland (**PIT-38**).

---

## 🌟 Key Features

* **Complex Events:**
    * **Sanctions/Escrow:** Correctly handles `Tendered` assets (e.g., SBER ADR -> SBER) and flagged restricted assets (RUB).
    * **Mergers/Spin-offs:** "Smart" parser detects hidden tickers in descriptions (e.g., WBD spinoff from AT&T).
* **Precise Math:**
    * **FIFO Logic:** Strict chronological matching.
    * **NBP Integration:** D-1 rule, holiday recursion, caching.
* **Professional Reports:**
    * **Visual Alerts:** Restricted assets marked with `*` and **Light Red** background.
    * **Details:** Monthly Dividend breakdown, Corporate Actions log.
    * **PIT-38:** Ready-to-copy numbers for Section C and Dividends.
* **Security:**
    * Local processing only.
    * CI/CD with `bandit` security scans.

## 🚀 Quick Start

1.  **Clone & Install:**
    ```bash
    git clone [https://github.com/Shearwater1978/ibkr-tax-gemini.git](https://github.com/Shearwater1978/ibkr-tax-gemini.git)
    cd ibkr-tax-gemini
    pip install -r requirements.txt
    ```

2.  **Run:**
    * Place your CSVs in `data/`.
    * `python main.py`
    * Check `output/tax_report_2024.pdf`.

## ⚠️ Disclaimer
**I am not a tax advisor.** Verify all numbers with your official broker statements.
