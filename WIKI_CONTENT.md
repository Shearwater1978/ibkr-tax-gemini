# User Guide (Wiki)

Welcome to the **IBKR Tax Calculator** documentation.

---

## 📥 How to get data from IBKR?

You have two options. Both are supported.

### Option A: Activity Statement (Easiest)
1.  Log in to Interactive Brokers Portal.
2.  Go to **Performance & Reports** -> **Statements** -> **Activity**.
3.  Select **Period**: `Annual` (or Custom Date Range).
4.  **Format**: `CSV`.
5.  Download and save to `data/` folder.

### Option B: Flex Query (Advanced)
1.  Go to **Performance & Reports** -> **Flex Queries**.
2.  Create a new Trade Confirmation Flex Query.
3.  Select sections: `Trades`, `Cash Transactions` (Dividends/Tax).
4.  Save and Run.
5.  Download CSV.

---

## 🧮 How PIT-38 calculation works

### 1. Capital Gains (Akcje)
* **Revenue (Przychód):** The sum of all SELL transactions converted to PLN at the rate of the day preceding the sale (D-1).
* **Cost (Koszty):** The sum of the purchase price of the shares sold (FIFO basis) + commissions, converted to PLN at the rate of D-1 relative to the purchase/sale.

### 2. Dividends (Dywidendy)
* **Gross Income:** The full dividend amount converted to PLN.
* **Tax Paid:** The withholding tax taken by the broker (e.g., 15% for US stocks) converted to PLN.
* **Tax Due in Poland:** Calculated as `19% of Gross` minus `Tax Paid`. If Tax Paid > 19%, you pay 0 in Poland.

---

## ❓ Troubleshooting

### "Report is empty / Zero values"
* **Check Dates:** Ensure your CSV contains data for the requested year.
* **Check NBP:** Ensure you have internet access. If NBP API is down, the script defaults to rate 1.0 (check logs).
* **Re-import:** Try deleting the database file (`data/ibkr_history.db.enc`) and running the import command again.

### "Tickers marked in RED"
* The PDF highlights Russian assets or sanctioned companies (e.g., YNDX, SBER). This is a warning that these assets might be frozen or require special tax treatment.

---