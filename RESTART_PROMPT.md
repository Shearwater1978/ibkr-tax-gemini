# RESTART PROMPT (MASTER INDEX - v2.1.0)

**Role:** Expert Python Developer (Finance/Tax domain).
**Project:** IBKR Tax Calculator for Poland (PIT-38).
**Version:** v2.1.0 (Stable).

## ⚠️ RESTORATION INSTRUCTIONS (READ FIRST)
This project context is split into **3 parts** due to size limits. You are currently viewing Part 1.

1.  **PART 1 (Current File):** Contains Root files (`main.py`, config). Restore them immediately.
2.  **PART 2 (Source Code):** YOU MUST ASK THE USER for the file `RESTART_SRC.md` to restore the `src/` directory.
3.  **PART 3 (Tests):** YOU MUST ASK THE USER for the file `RESTART_TESTS.md` to restore the `tests/` directory.

**DO NOT proceed with major refactoring until you have received all 3 parts.**

## 1. ROOT FILES (Restore content below)

# --- FILE: main.py ---
```python
# main.py

import argparse
from datetime import date
from collections import defaultdict
import sys
import os
import glob
import pandas as pd

# Project module imports
from src.data_collector import collect_all_trade_data
from src.excel_exporter import export_to_excel
from src.db_connector import DBConnector
from src.processing import process_yearly_data

# Import parser functions to enable data loading from main.py
from src.parser import parse_csv, save_to_database

# Attempt to import PDF generator
try:
    from src.report_pdf import generate_pdf

    PDF_AVAILABLE = True
except ImportError:
    print("WARNING: src/report_pdf.py not found. PDF export disabled.")
    PDF_AVAILABLE = False


def prepare_data_for_pdf(target_year, raw_trades, realized_gains, dividends, inventory):
    """
    Adapter: Converts processing results into the dictionary structure
    expected by src/report_pdf.py.
    """

    # --- LIST OF SANCTIONED STOCKS (Example for RU context) ---
    RESTRICTED_TICKERS = {
        "YNDX",
        "OZON",
        "VKCO",
        "FIVE",
        "FIXP",
        "HHR",
        "QIWI",
        "CIAN",
        "GEMC",
        "HMSG",
        "MDMG",
        "POLY",
        "PLZL",
        "GMKN",
        "NLMK",
        "CHMF",
        "MAGN",
        "RUAL",
        "ALRS",
        "PHOR",
        "GLTR",
        "GAZP",
        "LKOH",
        "NVTK",
        "ROSN",
        "TATN",
        "SNGS",
        "SNGSP",
        "SBER",
        "SBERP",
        "VTBR",
        "TCSG",
        "CBOM",
        "MTSS",
        "AFKS",
        "AFLT",
    }
    RESTRICTED_CURRENCIES = {"RUB"}

    # 1. Filter raw trades for the "History" section
    history_trades = []
    corp_actions = []

    # Sort by 'Date' key (PascalCase from DB)
    raw_trades.sort(key=lambda x: x["Date"])

    for t in raw_trades:
        # Check year. Key 'Date'
        if t["Date"].startswith(str(target_year)):
            event_type = t["EventType"]  # Key 'EventType'

            # Separate events. Only BUY and SELL go into history.
            if event_type in ["SPLIT", "TRANSFER", "MERGER", "SPINOFF"]:
                corp_actions.append(
                    {
                        "date": t["Date"],
                        "ticker": t["Ticker"],
                        "type": event_type,
                        "qty": float(t["Quantity"]) if t["Quantity"] else 0,
                        "ratio": 1,
                        "source": t.get("Description", "DB"),
                    }
                )

            elif event_type in ["BUY", "SELL"]:  # <--- STRICT FILTER
                history_trades.append(
                    {
                        "date": t["Date"],
                        "ticker": t["Ticker"],
                        "type": event_type,
                        "qty": float(t["Quantity"]) if t["Quantity"] else 0,
                        "price": float(t["Price"]) if t["Price"] else 0,
                        "commission": float(t["Fee"]) if t["Fee"] else 0,
                        "currency": t["Currency"],
                    }
                )
            # DIVIDEND and TAX events do NOT go here (they go to dividends section)

    # 2. Aggregate dividends by month
    monthly_divs = defaultdict(
        lambda: {"gross_pln": 0.0, "tax_pln": 0.0, "net_pln": 0.0}
    )
    formatted_divs = []

    for d in dividends:
        # dividends come from processing.py, which typically returns snake_case keys
        date_str = d["ex_date"]
        month_key = date_str[5:7]  # MM

        gross = d["gross_amount_pln"]
        tax = d.get("tax_withheld_pln", 0.0)
        net = gross - tax

        monthly_divs[month_key]["gross_pln"] += gross
        monthly_divs[month_key]["tax_pln"] += tax
        monthly_divs[month_key]["net_pln"] += net

        formatted_divs.append(
            {
                "date": date_str,
                "ticker": d["ticker"],
                "amount": (
                    d.get("gross_amount_pln", 0) / d.get("rate", 1)
                    if d.get("rate")
                    else 0
                ),
                "currency": d.get("currency", "UNK"),
                "rate": d.get("rate", 1.0),
                "amount_pln": gross,
                "tax_paid_pln": tax,
            }
        )

    # 3. Capital Gains
    cap_gains_data = []
    for g in realized_gains:
        # Similarly, realized_gains comes from processing.py in snake_case
        cap_gains_data.append(
            {"revenue_pln": g["sale_amount"], "cost_pln": g["cost_basis"]}
        )

    # 4. Assets at end of period (Inventory)
    aggregated_holdings = defaultdict(float)
    restricted_status = {}

    for i in inventory:
        ticker = i["ticker"]
        qty = i["quantity"]
        aggregated_holdings[ticker] += qty

        if ticker in RESTRICTED_TICKERS or i.get("currency") in RESTRICTED_CURRENCIES:
            restricted_status[ticker] = True

    holdings_data = []
    for ticker, total_qty in aggregated_holdings.items():
        if abs(total_qty) > 0.000001:
            holdings_data.append(
                {
                    "ticker": ticker,
                    "qty": total_qty,
                    "is_restricted": restricted_status.get(ticker, False),
                    "fifo_match": True,
                }
            )

    holdings_data.sort(key=lambda x: x["ticker"])

    # 5. Diagnostics
    per_curr = defaultdict(float)
    for d in dividends:
        per_curr[d.get("currency", "UNK")] += d["gross_amount_pln"]

    pdf_payload = {
        "year": target_year,
        "data": {
            "holdings": holdings_data,
            "trades_history": history_trades,
            "corp_actions": corp_actions,
            "monthly_dividends": dict(monthly_divs),
            "dividends": formatted_divs,
            "capital_gains": cap_gains_data,
            "per_currency": dict(per_curr),
            "diagnostics": {
                "tickers_count": len(aggregated_holdings),
                "div_rows_count": len(dividends),
                "tax_rows_count": 0,
            },
        },
    }
    return pdf_payload


def run_import_routine():
    """Helper function to find and parse CSV files from data/ directory."""
    print("--- 📥 DATA IMPORT (via main.py) ---")

    base_dir = os.path.dirname(os.path.abspath(__file__))
    data_dir = os.path.join(base_dir, "data")

    # Find all CSVs
    files = glob.glob(os.path.join(data_dir, "*.csv"))
    # Exclude system/manual files
    files = [f for f in files if "manual_" not in os.path.basename(f)]

    if not files:
        print(f"❌ No CSV files found in {data_dir}")
        return

    combined = {"trades": [], "dividends": [], "taxes": [], "corp_actions": []}
    print(f"Found {len(files)} files to process.")

    for fp in sorted(files):
        try:
            parsed = parse_csv(fp)
            for k in combined:
                combined[k].extend(parsed[k])
        except Exception as e:
            print(f"Error reading {fp}: {e}")

    if any(combined.values()):
        print("💾 Saving to database...")
        save_to_database(combined)
    else:
        print("⚠️ No valid data found in files.")


def main():
    parser = argparse.ArgumentParser(description="IBKR Tax Calculator")

    # Import Argument
    parser.add_argument(
        "--import-data",
        action="store_true",
        help="Import all CSV files from data/ folder into DB.",
    )

    # Filtering Arguments
    parser.add_argument(
        "--target-year",
        type=int,
        default=date.today().year,
        help="Tax year for calculation (e.g., 2024).",
    )
    parser.add_argument(
        "--ticker", type=str, default=None, help="Filter by ticker symbol (e.g., AAPL)."
    )

    # Export Arguments
    parser.add_argument(
        "--export-excel", action="store_true", help="Export full history to Excel."
    )
    parser.add_argument(
        "--export-pdf", action="store_true", help="Export tax report to PDF."
    )

    args = parser.parse_args()

    # --- 1. Import Mode ---
    if args.import_data:
        run_import_routine()
        return  # Stop here if we are just importing

    # --- 2. Calculation Mode ---
    print(f"Starting tax calculation for year {args.target_year}...")

    # Load data from DB
    raw_trades = []
    try:
        # Initialize connection (env vars loaded internally)
        with DBConnector() as db:
            db.initialize_schema()
            raw_trades = db.get_trades_for_calculation(
                target_year=args.target_year, ticker=args.ticker
            )
            print(f"INFO: Loaded {len(raw_trades)} records from DB.")
    except Exception as e:
        print(f"CRITICAL ERROR: Could not connect or fetch data. {e}")
        sys.exit(1)

    if not raw_trades:
        print(
            "WARNING: No trades found. Please import data first (python main.py --import-data)."
        )
        return

    # Run FIFO Logic
    print("INFO: Running FIFO matching and NBP currency conversion...")
    try:
        # process_yearly_data works with original PascalCase DB keys
        realized_gains, dividends, inventory = process_yearly_data(
            raw_trades, args.target_year
        )
    except Exception as e:
        print(f"CRITICAL ERROR during processing: {e}")
        sys.exit(1)

    # Calculate Totals
    total_pl = sum(r["profit_loss"] for r in realized_gains)
    total_dividends = sum(d["gross_amount_pln"] for d in dividends)

    print(f"\n--- Results for {args.target_year} ---")
    print(f"Realized P&L (FIFO): {total_pl:.2f} PLN")
    print(f"Dividends (Gross): {total_dividends:.2f} PLN")
    print(f"Open Positions (lots): {len(inventory)}")

    # Prepare export data
    file_name_suffix = f"_{args.ticker}" if args.ticker else ""

    # --- 3. Export to Excel ---
    if args.export_excel:
        print("\nStarting Excel export...")
        try:
            sheets_dict, ticker_summary = collect_all_trade_data(
                realized_gains, dividends, inventory
            )

            summary_metrics = {
                "Total P&L": f"{total_pl:.2f} PLN",
                "Total Dividends (Gross)": f"{total_dividends:.2f} PLN",
                "Report Year": args.target_year,
                "Filtered Ticker": args.ticker if args.ticker else "All Tickers",
                "Database Records": len(raw_trades),
            }
            output_path_xlsx = (
                f"output/tax_report_{args.target_year}{file_name_suffix}.xlsx"
            )
            export_to_excel(
                sheets_dict, output_path_xlsx, summary_metrics, ticker_summary
            )
            print(f"SUCCESS: Excel report saved to {output_path_xlsx}")
        except Exception as e:
            print(f"ERROR exporting to Excel: {e}")

    # --- 4. Export to PDF ---
    if args.export_pdf:
        if PDF_AVAILABLE:
            print("\nStarting PDF export...")
            output_path_pdf = (
                f"output/tax_report_{args.target_year}{file_name_suffix}.pdf"
            )

            # Prepare data for PDF (handling PascalCase keys)
            try:
                pdf_data = prepare_data_for_pdf(
                    args.target_year, raw_trades, realized_gains, dividends, inventory
                )
                generate_pdf(pdf_data, output_path_pdf)
                print(f"SUCCESS: PDF report saved to {output_path_pdf}")
            except Exception as e:
                print(f"ERROR: Could not generate PDF: {e}")
        else:
            print("ERROR: PDF generation module (src/report_pdf.py) not found.")

    print("Processing completed.")


if __name__ == "__main__":
    main()
```

# --- FILE: requirements.txt ---
```text
openpyxl
requests
reportlab
pytest
pytest-mock
flake8
black
pre-commit
cryptography
python-decouple
pandas
```

# --- FILE: .gitignore ---
```text
# --- User Data (SENSITIVE) ---
# Ignore all CSVs from broker
data/*
!data/.gitkeep
snapshot_*.json
db/*
*.csv
*.db
*.db.*

# Ignore manual history override (contains personal trades)
manual_history.csv
manual_fixes.csv

# --- Output Reports ---
# Ignore generated PDFs and JSONs
output/*
!output/.gitkeep

# --- Caches ---
# Ignore NBP rate cache (can be re-fetched)
cache/

# --- Python internals ---
__pycache__/
*.pyc
*.pyo
*.pyd

# --- Virtual Environments ---
venv/
.venv/
env/
.python-version

# --- IDE & OS files ---
.idea/
.vscode/
.DS_Store
Thumbs.db
.env

# --- Node ---
**/node_modules/
```

