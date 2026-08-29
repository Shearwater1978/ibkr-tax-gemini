# main.py

import argparse
import json
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
from src.diagnostics import CalculationError, ReportExportError
from src.fifo_coverage import PlannedSale, check_coverage

# Import parser functions to enable data loading from main.py
from src.parser import parse_csv, save_to_database
from src.ib_connector import IBConnector, IBConnectionError
from src.ib_normalizer import normalize_snapshot

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
    print("--- DATA IMPORT (via main.py) ---")

    base_dir = os.path.dirname(os.path.abspath(__file__))
    data_dir = os.path.join(base_dir, "data")

    # Find all CSVs
    files = glob.glob(os.path.join(data_dir, "*.csv"))
    # Exclude system/manual files
    files = [f for f in files if "manual_" not in os.path.basename(f)]

    if not files:
        print(f"ERROR: No CSV files found in {data_dir}")
        return {"inserted": 0, "skipped": 0}

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
        print("Saving to database...")
        result = save_to_database(combined)
        print(
            f"Import summary: {result['inserted']} inserted, "
            f"{result['skipped']} skipped."
        )
        return result
    else:
        print("WARNING: No valid data found in files.")
        return {"inserted": 0, "skipped": 0}


def run_ib_sync_routine():
    """Connects to IB Gateway/TWS, pulls a read-only account snapshot, and
    saves normalized fills into the database. Optional and separate from
    run_import_routine(): failures here must never affect CSV import.

    Returns a dict with keys: status ("success" | "error"), message,
    inserted, skipped.
    """
    print("--- IB LIVE SYNC (via main.py) ---")
    try:
        with IBConnector() as ib:
            ib.health_check()
            snapshot = ib.fetch_account_snapshot()
    except IBConnectionError as exc:
        print(f"ERROR: IB live sync failed: {exc}")
        return {"status": "error", "message": str(exc), "inserted": 0, "skipped": 0}

    normalized = normalize_snapshot(snapshot)
    if not any(normalized.values()):
        print("WARNING: No new trade data returned from IB Gateway/TWS.")
        return {
            "status": "success",
            "message": "No new live data to import",
            "inserted": 0,
            "skipped": 0,
        }

    result = save_to_database(normalized)
    print(
        f"IB sync summary: {result['inserted']} inserted, "
        f"{result['skipped']} skipped."
    )
    return {
        "status": "success",
        "message": "IB live sync finished",
        "inserted": result["inserted"],
        "skipped": result["skipped"],
    }


def load_planned_sales(arguments, json_path=None):
    items = []
    if json_path:
        with open(json_path, encoding="utf-8") as input_file:
            items.extend(json.load(input_file))
    for item in arguments:
        ticker, quantity, as_of = item.split(":", 2)
        items.append({"ticker": ticker, "quantity": quantity, "as_of": as_of})
    return [PlannedSale(**item) for item in items]


def run_coverage(args):
    try:
        planned_sales = load_planned_sales(args.planned_sale, args.coverage_file)
        as_of = max(sale.as_of for sale in planned_sales)
        with DBConnector() as db:
            db.initialize_schema()
            raw_trades = db.get_trades_for_calculation()
        report = check_coverage(raw_trades, planned_sales)
        if args.coverage_output == "json":
            print(json.dumps(report, indent=2))
        else:
            print("--- FIFO Coverage Preflight (no sale persisted) ---")
            for result in report["results"]:
                print(
                    f"{result['ticker']}: {result['status']} | "
                    f"requested {result['requested']}, available {result['available']}, "
                    f"missing {result['missing']} | as of {result['as_of']}"
                )
            print(f"Overall: {report['status']} (history through {as_of})")
    except (ValueError, OSError, json.JSONDecodeError) as exc:
        print(f"COVERAGE ERROR: {exc}")
        return 2
    return 0


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
    parser.add_argument(
        "--coverage-file", help="JSON file containing planned sales for FIFO preflight."
    )
    parser.add_argument(
        "--planned-sale",
        action="append",
        default=[],
        metavar="TICKER:QUANTITY:DATE",
        help="Planned sale, repeat for multiple assets (e.g. AAPL:10:2024-12-31).",
    )
    parser.add_argument(
        "--coverage-output",
        choices=("text", "json"),
        default="text",
        help="FIFO coverage report format.",
    )

    args = parser.parse_args()

    # --- 1. Import Mode ---
    if args.import_data:
        run_import_routine()
        return  # Stop here if we are just importing

    if args.coverage_file or args.planned_sale:
        return_code = run_coverage(args)
        if return_code:
            sys.exit(return_code)
        return

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
    except CalculationError as e:
        diagnostic = e.diagnostic
        print(f"CRITICAL ERROR [{diagnostic.code}]: {diagnostic.message}")
        sys.exit(1)
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
        except ReportExportError as e:
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
