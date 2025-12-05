# Welcome to the IBKR Tax Assistant Wiki

This project automates the complex task of calculating taxes for Interactive Brokers under Polish jurisdiction. Below is a deep dive into the logic used.

## 🧠 Tax Logic Explained

### 1. Capital Gains (Stocks)
The application uses the **FIFO (First-In, First-Out)** method, which is the standard for Polish tax law unless specified otherwise by your broker.

* **Cost Basis (Koszty):** Calculated in PLN using the NBP exchange rate from the **business day preceding (D-1)** the BUY date.
* **Revenue (Przychód):** Calculated in PLN using the NBP exchange rate from the **business day preceding (D-1)** the SELL date.
* **Profit/Loss:** `Revenue - (Cost Basis + Commissions)`.

### 2. Dividends
* **Gross Amount:** Converted to PLN using the D-1 NBP rate.
* **Withholding Tax (WHT):** The tax already paid in the US (usually 15% with W-8BEN) is converted to PLN using the *same* D-1 rate.
* **Polish Tax:** Poland charges 19% on dividends. You can deduct the WHT paid abroad.
    * *Calculation:* `(Gross_PLN * 0.19) - WHT_Paid_PLN`.
    * This is the "Additional Tax" shown in the report.

### 3. Corporate Actions
Handling splits and spin-offs is crucial to avoid fake "profits".

* **Stock Splits:** If you hold 1 share @ $100 and a 10:1 split occurs, the app adjusts your inventory to 10 shares @ $10. **No tax event is triggered.**
* **Stock Dividends:** Receiving shares as a dividend (not DRIP, but a bonus issue) is treated as a **BUY transaction with 0.00 cost**. This means when you sell them later, the entire sale price is profit.

### 4. Exchange Rates (NBP)
We use the official API of the National Bank of Poland (`api.nbp.pl`).
* **Caching:** To be respectful to the API and speed up processing, the app downloads rates for the *entire year* in bulk and caches them locally (`cache/nbp_cache_YYYY.json`).
* **Holiday Logic:** If D-1 falls on a Sunday or Holiday, the app recurses backwards day-by-day until it finds a valid trading day rate (Table A).

## 🛠️ Advanced Usage

### Handling Account Migrations
If you moved your account (e.g., from IBKR UK to IBKR IE):
1.  Download Activity Reports for **BOTH** accounts.
2.  Place both CSV files in the `data/` folder.
3.  The app will automatically merge the timelines.
4.  **Note:** Technical "Transfers" (ACATS/Inter-Company) are ignored to prevent double taxation.

### Manual History
If you have stocks bought years ago at a different broker:
1.  Create `manual_history.csv` in the root folder.
2.  Add columns: `Date,Ticker,Quantity,Price,Currency,Commission`.
3.  These trades will be loaded into the FIFO engine first ("Genesis Block").

## 🤝 Contributing
Found a bug?
1.  Open an Issue.
2.  Submit a PR (Please run `flake8` and `pytest` first!).