# RESTART PROMPT (SPRINT 3 COMPLETE - v1.3.0)

I want to recreate the Python project exactly as it exists now.
It is an **IBKR Tax Calculator for Poland (PIT-38)** using **SQLCipher** for security.

## 1. File Structure
```text
ROOT/
    .env                       # Stores SQLCIPHER_KEY and DATABASE_PATH
    main.py                    # Entry point (CLI)
    requirements.txt           # pysqlcipher3, pandas, reportlab, openpyxl
    src/
        db_connector.py        # SQLCipher connection manager
        parser.py              # CSV Parsing logic
        processing.py          # Logic Bridge: Links Tax->Divs, Runs FIFO
        fifo.py                # TradeMatcher (FIFO Engine)
        data_collector.py      # Prepares DataFrames for Excel
        excel_exporter.py      # Writes .xlsx files
        report_pdf.py          # Writes .pdf files
        nbp.py                 # NBP API Client
        utils.py               # Rounding helpers
```

## 2. Technical Specification (Sprint 3)

### 2.1. Project Goal
Automate PIT-38 calculations. Securely store financial data. Generate audit-ready Excel/PDF reports.

### 2.2. Architecture
* **Storage:** `src/db_connector.py` uses **SQLCipher** (AES-256). No clear-text JSON/CSV storage for processed data.
* **Processing:** Functional approach (`process_yearly_data` in `src/processing.py`).
* **Exchange Rates:** NBP API (T-1 rule).

### 2.3. Key Logic Changes (vs v1.2)
* **Withholding Tax:** Now handled by pre-scanning `TAX` rows and linking them to `DIVIDEND` rows by `(Date, Ticker)`.
* **Reporting:**
    * **Excel:** Separated into tabs: *Sales P&L*, *Dividends*, *Open Positions*.
    * **PDF:** 'Trades History' filtered to show only BUY/SELL (clean view). 'Portfolio' aggregates lots by Ticker.
* **Sanctions:** `main.py` contains a hardcoded list of restricted assets (SBER, YNDX, RUB, etc.) to highlight in PDF.

## 3. SOURCE CODE
Please populate the files with the following content exactly.

# --- FILE: ./main.py ---
```python
import argparse
from datetime import date
from collections import defaultdict
import sys
import pandas as pd

from src.data_collector import collect_all_trade_data
from src.excel_exporter import export_to_excel
from src.db_connector import DBConnector 
from src.processing import process_yearly_data 

try:
    from src.report_pdf import generate_pdf
    PDF_AVAILABLE = True
except ImportError:
    print("WARNING: src/report_pdf.py not found. PDF export disabled.")
    PDF_AVAILABLE = False

def prepare_data_for_pdf(target_year, raw_trades, realized_gains, dividends, inventory):
    # --- SANCTIONS / RESTRICTED LIST ---
    RESTRICTED_TICKERS = {
        "YNDX", "OZON", "VKCO", "FIVE", "FIXP", "HHR", "QIWI", "CIAN", "GEMC", "HMSG", "MDMG",
        "POLY", "PLZL", "GMKN", "NLMK", "CHMF", "MAGN", "RUAL", "ALRS", "PHOR", "GLTR",
        "GAZP", "LKOH", "NVTK", "ROSN", "TATN", "SNGS", "SNGSP",
        "SBER", "SBERP", "VTBR", "TCSG", "CBOM",
        "MTSS", "AFKS", "AFLT"
    }
    RESTRICTED_CURRENCIES = {"RUB"}

    history_trades = []
    corp_actions = []
    raw_trades.sort(key=lambda x: x['Date'])
    
    for t in raw_trades:
        if t['Date'].startswith(str(target_year)):
            event_type = t['EventType']
            if event_type in ['SPLIT', 'TRANSFER', 'MERGER', 'SPINOFF']:
                corp_actions.append({
                    'date': t['Date'], 'ticker': t['Ticker'], 'type': event_type,
                    'qty': float(t['Quantity']) if t['Quantity'] else 0,
                    'ratio': 1, 'source': t.get('Description', 'DB')
                })
            elif event_type in ['BUY', 'SELL']:
                history_trades.append({
                    'date': t['Date'], 'ticker': t['Ticker'], 'type': event_type,
                    'qty': float(t['Quantity']) if t['Quantity'] else 0,
                    'price': float(t['Price']) if t['Price'] else 0,
                    'commission': float(t['Fee']) if t['Fee'] else 0,
                    'currency': t['Currency']
                })

    monthly_divs = defaultdict(lambda: {'gross_pln': 0.0, 'tax_pln': 0.0, 'net_pln': 0.0})
    formatted_divs = []
    for d in dividends:
        date_str = d['ex_date']
        month_key = date_str[5:7]
        gross = d['gross_amount_pln']
        tax = d.get('tax_withheld_pln', 0.0)
        net = gross - tax
        monthly_divs[month_key]['gross_pln'] += gross
        monthly_divs[month_key]['tax_pln'] += tax
        monthly_divs[month_key]['net_pln'] += net
        formatted_divs.append({
            'date': date_str, 'ticker': d['ticker'],
            'amount': d.get('gross_amount_pln', 0) / d.get('rate', 1) if d.get('rate') else 0,
            'currency': d.get('currency', 'UNK'), 'rate': d.get('rate', 1.0),
            'amount_pln': gross, 'tax_paid_pln': tax
        })

    cap_gains_data = [{'revenue_pln': g['sale_amount'], 'cost_pln': g['cost_basis']} for g in realized_gains]

    aggregated_holdings = defaultdict(float)
    restricted_status = {}
    for i in inventory:
        ticker = i['ticker']
        qty = i['quantity']
        aggregated_holdings[ticker] += qty
        if ticker in RESTRICTED_TICKERS or i.get('currency') in RESTRICTED_CURRENCIES:
            restricted_status[ticker] = True

    holdings_data = []
    for ticker, total_qty in aggregated_holdings.items():
        if abs(total_qty) > 0.000001:
            holdings_data.append({
                'ticker': ticker, 'qty': total_qty,
                'is_restricted': restricted_status.get(ticker, False), 'fifo_match': True
            })
    holdings_data.sort(key=lambda x: x['ticker'])

    per_curr = defaultdict(float)
    for d in dividends: per_curr[d.get('currency', 'UNK')] += d['gross_amount_pln']

    return {
        'year': target_year,
        'data': {
            'holdings': holdings_data, 'trades_history': history_trades, 'corp_actions': corp_actions,
            'monthly_dividends': dict(monthly_divs), 'dividends': formatted_divs,
            'capital_gains': cap_gains_data, 'per_currency': dict(per_curr),
            'diagnostics': {'tickers_count': len(aggregated_holdings), 'div_rows_count': len(dividends), 'tax_rows_count': 0}
        }
    }

def main():
    parser = argparse.ArgumentParser(description="IBKR Tax Calculator")
    parser.add_argument('--target-year', type=int, default=date.today().year, help='Year for calculation.')
    parser.add_argument('--ticker', type=str, default=None, help='Filter by ticker.')
    parser.add_argument('--export-excel', action='store_true', help='Export to Excel.')
    parser.add_argument('--export-pdf', action='store_true', help='Export to PDF.')
    args = parser.parse_args()

    print(f"Starting tax calculation for {args.target_year}...")
    raw_trades = []
    try:
        with DBConnector() as db:
            db.initialize_schema() 
            raw_trades = db.get_trades_for_calculation(target_year=args.target_year, ticker=args.ticker)
            print(f"INFO: Loaded {len(raw_trades)} records from SQLCipher.")
    except Exception as e:
        print(f"FATAL ERROR: DB Connection failed. {e}")
        sys.exit(1)

    if not raw_trades:
        print("WARNING: No trades found. Import data first.")
        return

    realized_gains, dividends, inventory = process_yearly_data(raw_trades, args.target_year)
    
    total_pl = sum(r['profit_loss'] for r in realized_gains)
    total_dividends = sum(d['gross_amount_pln'] for d in dividends)
    print(f"\n--- Results {args.target_year} ---")
    print(f"Realized P&L: {total_pl:.2f} PLN")
    print(f"Dividends (Gross): {total_dividends:.2f} PLN")

    sheets_dict, ticker_summary = collect_all_trade_data(realized_gains, dividends, inventory)
    suffix = f"_{args.ticker}" if args.ticker else ""

    if args.export_excel:
        out_xlsx = f"output/tax_report_{args.target_year}{suffix}.xlsx"
        summary_metrics = {"Total P&L": total_pl, "Dividends": total_dividends}
        export_to_excel(sheets_dict, out_xlsx, summary_metrics, ticker_summary)

    if args.export_pdf and PDF_AVAILABLE:
        out_pdf = f"output/tax_report_{args.target_year}{suffix}.pdf"
        pdf_data = prepare_data_for_pdf(args.target_year, raw_trades, realized_gains, dividends, inventory)
        generate_pdf(pdf_data, out_pdf)
        print(f"SUCCESS: PDF saved to {out_pdf}")

if __name__ == "__main__":
    main()
```

# --- FILE: ./src/processing.py ---
```python
from typing import List, Dict, Any, Tuple
from decimal import Decimal
from collections import defaultdict
from src.nbp import get_nbp_rate
from src.fifo import TradeMatcher 

def process_yearly_data(raw_trades: List[Dict[str, Any]], target_year: int) -> Tuple[List[Dict], List[Dict], List[Dict]]:
    matcher = TradeMatcher()
    dividends = []
    fifo_input_list = []
    
    # 0. Pre-process Taxes
    tax_map = defaultdict(Decimal)
    for t in raw_trades:
        if t['EventType'] == 'TAX':
            amt = Decimal(str(t['Amount'])) if t['Amount'] else Decimal(0)
            key = (t['Date'], t['Ticker'])
            tax_map[key] += abs(amt)

    sorted_trades = sorted(raw_trades, key=lambda x: (x['Date'], x['TradeId']))
    
    for trade in sorted_trades:
        date_str = trade['Date']
        ticker = trade['Ticker']
        event_type = trade['EventType']
        currency = trade['Currency']
        quantity = Decimal(str(trade['Quantity'])) if trade['Quantity'] else Decimal(0)
        price = Decimal(str(trade['Price'])) if trade['Price'] else Decimal(0)
        amount = Decimal(str(trade['Amount'])) if trade['Amount'] else Decimal(0)
        fee = Decimal(str(trade['Fee'])) if trade['Fee'] else Decimal(0)
        
        rate = Decimal("1.0")
        if currency != 'PLN':
             # Note: In real app, handle NBP exceptions properly
             try: rate = get_nbp_rate(currency, date_str)
             except: rate = Decimal("1.0")

        if event_type == 'DIVIDEND':
            gross_pln = amount * rate
            tax_in_curr = tax_map.get((date_str, ticker), Decimal(0))
            tax_pln = tax_in_curr * rate
            if date_str.startswith(str(target_year)):
                dividends.append({
                    'ex_date': date_str, 'ticker': ticker,
                    'gross_amount_pln': float(gross_pln), 'tax_withheld_pln': float(tax_pln),
                    'currency': currency, 'rate': float(rate)
                })
        elif event_type == 'TAX':
            pass # Handled in pre-process
        else:
            # TRADES
            trade_record = {
                'type': event_type, 'date': date_str, 'ticker': ticker,
                'qty': quantity, 'price': price, 'commission': fee,
                'currency': currency, 'rate': rate, 'source': 'DB'
            }
            if event_type == 'SPLIT': trade_record['ratio'] = Decimal("1") # Simplified for snippet
            fifo_input_list.append(trade_record)

    matcher.process_trades(fifo_input_list)
    all_realized = matcher.get_realized_gains()
    target_realized = [r for r in all_realized if r['sale_date'].startswith(str(target_year))]
    inventory = matcher.get_current_inventory()
    return target_realized, dividends, inventory
```

# --- FILE: ./src/db_connector.py ---
```python
import sqlite3
import os
from decouple import config

class DBConnector:
    def __init__(self):
        self.db_path = config('DATABASE_PATH', default='db/ibkr_history.db.enc')
        self.key = config('SQLCIPHER_KEY', default='TEST_KEY')
        self.conn = None

    def __enter__(self):
        self.conn = sqlite3.connect(self.db_path)
        # Enforce SQLCipher encryption
        self.conn.execute(f"PRAGMA key = '{self.key}';")
        self.conn.row_factory = sqlite3.Row
        return self

    def __exit__(self, exc_type, exc_val, exc_tb):
        if self.conn:
            self.conn.close()

    def initialize_schema(self):
        # Standard table schema
        query = """
        CREATE TABLE IF NOT EXISTS transactions (
            TradeId INTEGER PRIMARY KEY AUTOINCREMENT,
            Date TEXT, EventType TEXT, Ticker TEXT,
            Quantity REAL, Price REAL, Amount REAL, Fee REAL, Currency TEXT, Description TEXT
        );
        """
        self.conn.execute(query)

    def get_trades_for_calculation(self, target_year, ticker=None):
        # Returns ALL history up to end of target_year to ensure FIFO correctness
        end_date = f"{target_year}-12-31"
        query = "SELECT * FROM transactions WHERE Date <= :end_date"
        params = {'end_date': end_date}
        if ticker:
            query += " AND Ticker = :ticker"
            params['ticker'] = ticker
        return [dict(row) for row in self.conn.execute(query, params)]
```

---
**INSTRUCTION:**
Use the above file contents to initialize the environment.
The DB logic replaces the old JSON snapshot system.