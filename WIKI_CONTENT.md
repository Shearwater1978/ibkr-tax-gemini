# Project Documentation (Wiki Home)

Welcome to the **IBKR Tax Calculator (Poland / PIT-38)** project.
This tool processes **Interactive Brokers** reports to calculate Capital Gains (FIFO) and Dividends for Polish tax residents.

It features **military-grade security** (SQLCipher encryption) and generates professional reports ready for tax filing.

---

## 🎯 Purpose

This project is designed to automate the complex requirements of the Polish Tax Office (Urząd Skarbowy) regarding foreign assets.

The tool:
- **Secures Data:** Stores all financial history in an AES-256 encrypted database.
- **Calculates FIFO:** Matches Sells against the oldest Buys (First-In-First-Out) to optimize Cost Basis.
- **Converts Currency:** Fetches official **NBP (National Bank of Poland)** rates for the day preceding the event (T-1).
- **Checks Sanctions:** Highlights restricted assets (e.g., Russian ADRs like SBER, YNDX) in the portfolio.
- **Prepares PIT-38:** Aggregates data into "Revenue" (Przychód) and "Costs" (Koszty).

---

## 📂 Folder Structure

```text
project-root
│
├── .env                       # Stores SQLCIPHER_KEY and DB path (Private!)
├── main.py                    # Entry point for reporting
├── requirements.txt           # Python dependencies
│
├── data/                      # Data storage
│   ├── ibkr_history.db.enc    # Encrypted SQLCipher Database
│   └── *.csv                  # Raw broker reports (Activity Statements / Flex Queries)
│
├── output/                    # Generated Reports
│   ├── tax_report_2024.pdf
│   └── tax_report_2024.xlsx
│
├── src/                       # Source Code
│   ├── db_connector.py        # Database Manager (Encryption)
│   ├── parser.py              # CSV Importer (Universal Parser)
│   ├── processing.py          # Logic Orchestrator (Tax Linking)
│   ├── fifo.py                # TradeMatcher Engine
│   ├── nbp.py                 # NBP API Client (Batch Caching)
│   ├── report_pdf.py          # PDF Generator
│   └── excel_exporter.py      # Excel Generator
│
└── tests/                     # Pytest suite
```

---

## 📥 Input: IBKR Reports

The system supports CSV files generated via **IBKR Activity Statements** (Default) or **Flex Queries**.

**Important Columns Detected (Dynamic Mapping):**
* **Trades:** `Date`, `Symbol`, `Buy/Sell`, `Quantity`, `TradePrice`, `Commission`
* **Dividends:** `PayDate`, `Amount`, `Currency`, `Description`
* **Withholding Tax:** `Date`, `Amount` (Negative value)

**Example Trade Row (Activity Statement):**
```csv
Trades,Data,Order,Stocks,USD,AAPL,2024-01-15,10,150.0, ..., -1.0, ...
```

**Example Dividend Row:**
```csv
Dividends,Data,USD,2024-03-01,AAPL Cash Div, 0.24, ...
```

---

## 🔄 Workflow

The application works in two distinct steps: **Import** and **Report**.

### Step 1: Import Data
Parse raw CSV files into the secure database. You can run this multiple times; the system handles duplicates (mostly).

```bash
# Import all CSVs from the data folder
python -m src.parser --files "data/*.csv"
```

### Step 2: Generate Report
Run the calculation for a specific tax year. This pulls data from the DB, fetches NBP rates, runs FIFO, and outputs files.

```bash
# Generate PDF and Excel for 2024
python main.py --target-year 2024 --export-pdf --export-excel
```

---

## 🧮 Logic & Math

### 1. FIFO (First-In-First-Out)
When you sell a stock, the engine searches for the oldest available "Buy" lot.
* **Cost Basis** = (Buy Price * Buy Rate) + (Buy Commission * Buy Rate).
* **Revenue** = (Sell Price * Sell Rate) - (Sell Commission * Sell Rate).
* **Profit** = Revenue - Cost Basis.

### 2. NBP FX Rates
Rates are fetched from `api.nbp.pl`.
* **Rule:** The rate used is from the **last working day before** the event (D-1).
* **Optimization:** Uses Batch Caching to fetch monthly rates in one go.
* **Fallback:** If the API fails, it defaults to 1.0 (with a warning).

### 3. Tax Linking
IBKR reports Withholding Tax as a separate transaction. The logic maps these taxes to dividends based on **Date** and **Ticker**.
* *Dividend:* +10.00 USD
* *Tax Row:* -1.50 USD
* *Result:* Gross = 10.00, Tax Paid = 1.50.

---

## 📄 Output Formats

### 1. Excel Report (`.xlsx`)
Designed for deep analysis and verification.
* **Summary:** Top-level metrics.
* **Sales P&L:** Every closed trade, exploded by tax lot. Shows `Holding_Days`.
* **Dividends:** List of all payments and taxes paid abroad.
* **Open Positions:** What is currently in your inventory (FIFO queue).

### 2. PDF Report (`.pdf`)
Designed for printing and filing.
| Page | Content |
|-----:|---------|
| 1 | **Cover** (Year + Period) |
| 2 | **Portfolio:** Aggregated holdings. <br>⚠️ **Red Flag:** Marks sanctioned assets (SBER, YNDX, RUB). |
| 3 | **Trades History:** Clean list of BUY/SELL events (filtered). |
| 4 | **Dividends:** Monthly summary table (Gross vs Net). |
| 5 | **PIT-38 Helper:** Exact figures for "Przychód" and "Koszty". |

---

## 🧾 PIT-38 Mapping

Use the **PDF Page 5** to fill your tax form:

| PIT-38 Field | Source in Report | Logic |
|:--- |:--- |:--- |
| **Sekcja C, Poz. 20** <br>(Przychód) | **Capital Gains -> Revenue** | Sum of all Sales converted to PLN. |
| **Sekcja C, Poz. 21** <br>(Koszty uzyskania) | **Capital Gains -> Cost** | Sum of matched Buy Costs + Commissions. |
| **Sekcja G** <br>(Podatek zapłacony) | **Dividends -> Tax Paid** | Total Withholding Tax paid abroad (PLN). |

---

## 🔒 Security (SQLCipher)

This project does not store financial data in plain text.
* **Encryption:** AES-256.
* **Key:** Stored in `.env` as `SQLCIPHER_KEY`.
* **Safety:** If you lose the key, the database is unreadable. If you delete `.env`, you must re-import data.

---

## 💡 Best Practices

1.  **Keep History:** Do not delete old CSVs. Import them all to build a full FIFO history.
2.  **Check Sanctions:** If the PDF highlights a ticker in RED, consult a tax advisor regarding "Blocked Assets".
3.  **Verify NBP:** Occasionally check if the NBP API is reachable (no firewall blocks).

---

## 🤝 Troubleshooting

* **"PDF generator module not found":** Ensure `reportlab` is installed.
* **"SQLCipher connection failed":** Check if `SQLCIPHER_KEY` is present in `.env`.
* **"Zero Profit":** Ensure you imported the *entire* history, not just the current year (FIFO needs purchase dates).

---
End of documentation.