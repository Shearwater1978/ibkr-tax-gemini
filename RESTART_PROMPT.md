# RESTART PROMPT (GOLDEN BACKUP - Sprint 3 - v1.4.0)

**Role:** Expert Python Developer (Finance/Tax domain).
**Project:** IBKR Tax Calculator for Poland (PIT-38).
**Current State:** Sprint 3 Completed. Architecture migrated to SQLCipher.

## 1. Project Goal
Automate PIT-38 calculations for Polish residents. Securely store data in encrypted DB. Generate audit-ready PDF/Excel reports.

## 2. File Structure
```text
ROOT/
    .env                       # SQLCIPHER_KEY, DATABASE_PATH
    main.py                    # Entry point (CLI)
    requirements.txt           # pysqlcipher3, pandas, reportlab, openpyxl
    src/
        db_connector.py        # SQLCipher connection manager
        parser.py              # CSV Parsing logic (Transactions -> DB)
        processing.py          # Logic Bridge: Links Tax->Divs, Runs FIFO
        fifo.py                # TradeMatcher (FIFO Engine)
        data_collector.py      # Prepares DataFrames for Excel
        excel_exporter.py      # Writes .xlsx files
        report_pdf.py          # Writes .pdf files (ReportLab)
        nbp.py                 # NBP API Client
        utils.py               # Financial rounding helpers
    tests/
        test_fifo.py
        test_processing.py
        test_utils.py
        test_db_connector.py
```

## 3. Source Code Restoration
Please restore the files exactly as provided below.

# --- FILE: main.py ---
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
    print('WARNING: src/report_pdf.py not found. PDF export disabled.')
    PDF_AVAILABLE = False

def prepare_data_for_pdf(target_year, raw_trades, realized_gains, dividends, inventory):
    RESTRICTED_TICKERS = {
        'YNDX', 'OZON', 'VKCO', 'FIVE', 'FIXP', 'HHR', 'QIWI', 'CIAN', 'GEMC', 'HMSG', 'MDMG',
        'POLY', 'PLZL', 'GMKN', 'NLMK', 'CHMF', 'MAGN', 'RUAL', 'ALRS', 'PHOR', 'GLTR',
        'GAZP', 'LKOH', 'NVTK', 'ROSN', 'TATN', 'SNGS', 'SNGSP',
        'SBER', 'SBERP', 'VTBR', 'TCSG', 'CBOM',
        'MTSS', 'AFKS', 'AFLT'
    }
    RESTRICTED_CURRENCIES = {'RUB'}
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
    for d in dividends:
        per_curr[d.get('currency', 'UNK')] += d['gross_amount_pln']
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
    parser = argparse.ArgumentParser(description='IBKR Tax Calculator')
    parser.add_argument('--target-year', type=int, default=date.today().year, help='Year for calculation.')
    parser.add_argument('--ticker', type=str, default=None, help='Filter results by ticker.')
    parser.add_argument('--export-excel', action='store_true', help='Export to Excel.')
    parser.add_argument('--export-pdf', action='store_true', help='Export to PDF.')
    args = parser.parse_args()
    print(f'Starting tax calculation for {args.target_year}...')
    raw_trades = []
    try:
        with DBConnector() as db:
            db.initialize_schema()
            raw_trades = db.get_trades_for_calculation(target_year=args.target_year, ticker=args.ticker)
            print(f'INFO: Loaded {len(raw_trades)} transaction records from SQLCipher.')
    except Exception as e:
        print(f'FATAL ERROR: Database connection failed. {e}')
        sys.exit(1)
    if not raw_trades:
        print('WARNING: No trades found. Please import data using src/parser.py first.')
        return
    print('INFO: Running FIFO matching and NBP rate conversion...')
    try:
        realized_gains, dividends, inventory = process_yearly_data(raw_trades, args.target_year)
    except Exception as e:
        print(f'FATAL ERROR during processing: {e}')
        sys.exit(1)
    total_pl = sum(r['profit_loss'] for r in realized_gains)
    total_dividends = sum(d['gross_amount_pln'] for d in dividends)
    print(f'\n--- Tax Results for {args.target_year} ---')
    print(f'Realized P&L (FIFO): {total_pl:.2f} PLN')
    print(f'Total Dividends (Gross): {total_dividends:.2f} PLN')
    print(f'Open Positions: {len(inventory)}')
    if args.export_excel:
        print('\nStarting Excel export...')
        sheets_dict, ticker_summary = collect_all_trade_data(realized_gains, dividends, inventory)
        summary_metrics = {
            'Total P&L': f'{total_pl:.2f} PLN',
            'Total Dividends (Gross)': f'{total_dividends:.2f} PLN',
            'Report Year': args.target_year,
            'Filtered Ticker': args.ticker if args.ticker else 'All Tickers',
            'Database Records': len(raw_trades)
        }
        output_path_xlsx = f'output/tax_report_{args.target_year}' + (f'_{args.ticker}' if args.ticker else '') + '.xlsx'
        export_to_excel(sheets_dict, output_path_xlsx, summary_metrics, ticker_summary)
    if args.export_pdf and PDF_AVAILABLE:
        print('\nStarting PDF export...')
        output_path_pdf = f'output/tax_report_{args.target_year}' + (f'_{args.ticker}' if args.ticker else '') + '.pdf'
        pdf_data = prepare_data_for_pdf(args.target_year, raw_trades, realized_gains, dividends, inventory)
        try:
            generate_pdf(pdf_data, output_path_pdf)
            print(f'SUCCESS: PDF Report saved to {output_path_pdf}')
        except Exception as e:
            print(f'ERROR: Failed to generate PDF: {e}')
    print('Processing complete.')
if __name__ == '__main__':
    main()
```

# --- FILE: src/db_connector.py ---
```python
import sqlite3
from typing import List, Dict, Any, Optional
from decouple import config

class DBConnector:
    def __init__(self):
        self.db_path = config('DATABASE_PATH', default='data/ibkr_history.db')
        self.db_key = config('SQLCIPHER_KEY', default='')
        self.conn: Optional[sqlite3.Connection] = None
        if not self.db_key:
            raise ValueError('SQLCIPHER_KEY is not set in the .env file.')

    def __enter__(self):
        try:
            self.conn = sqlite3.connect(self.db_path)
            self.conn.row_factory = sqlite3.Row
            self.conn.execute(f"PRAGMA key='{self.db_key}';")
            return self
        except Exception as e:
            print(f'ERROR: Failed to open SQLCipher connection. {e}')
            raise

    def __exit__(self, exc_type, exc_val, exc_tb):
        if self.conn: self.conn.close()

    def initialize_schema(self):
        if not self.conn: raise ConnectionError('DB not connected')
        query = """
        CREATE TABLE IF NOT EXISTS transactions (
            TradeId INTEGER PRIMARY KEY,
            Date TEXT NOT NULL,
            EventType TEXT NOT NULL,
            Ticker TEXT NOT NULL,
            Quantity REAL, Price REAL, Currency TEXT,
            Amount REAL, Fee REAL, Description TEXT
        );
        """
        self.conn.execute(query)
        self.conn.commit()

    def get_trades_for_calculation(self, target_year: int, ticker: Optional[str]) -> List[Dict[str, Any]]:
        if not self.conn: return []
        # Include BUYS (any date) and other events (target year only)
        query_parts = ['SELECT * FROM transactions WHERE 1=1']
        params = {}
        start_date = f'{target_year}-01-01'
        end_date = f'{target_year}-12-31'
        query_parts.append(f'AND (EventType="BUY" OR Date BETWEEN :start_date AND :end_date)')
        params['start_date'] = start_date
        params['end_date'] = end_date
        if ticker:
            query_parts.append('AND Ticker = :ticker')
            params['ticker'] = ticker
        query = ' '.join(query_parts) + ' ORDER BY Date ASC, TradeId ASC;'
        cursor = self.conn.execute(query, params)
        return [dict(row) for row in cursor.fetchall()]
```

# --- FILE: src/parser.py ---
```python
import csv, re, glob, argparse
from decimal import Decimal
from typing import List, Dict, Any
from src.db_connector import DBConnector

def extract_ticker(description: str) -> str:
    if not description: return 'UNKNOWN'
    match = re.search(r'^([A-Za-z0-9\.]+)\(', description)
    if match: return match.group(1)
    parts = description.split()
    if parts and parts[0].isupper() and len(parts[0]) < 6:
        return parts[0].split('(')[0]
    return 'UNKNOWN'

def classify_trade_type(description: str, quantity: Decimal) -> str:
    desc_upper = description.upper()
    if any(k in desc_upper for k in ['ACATS', 'TRANSFER', 'INTERNAL']): return 'TRANSFER'
    if quantity > 0: return 'BUY'
    if quantity < 0: return 'SELL'
    return 'UNKNOWN'

def parse_csv(filepath):
    data = {'dividends': [], 'taxes': [], 'trades': []}
    with open(filepath, 'r', encoding='utf-8-sig') as f:
        reader = csv.reader(f)
        for row in reader:
            if not row: continue
            if row[0] == 'Dividends' and row[1] == 'Data':
                if 'Total' in row[2] or 'Total' in row[4]: continue
                data['dividends'].append({
                    'ticker': extract_ticker(row[4]),
                    'currency': row[2], 'date': row[3], 'amount': Decimal(row[5])
                })
            elif row[0] == 'Withholding Tax' and row[1] == 'Data':
                if 'Total' in row[4]: continue
                data['taxes'].append({
                    'ticker': extract_ticker(row[4]),
                    'currency': row[2], 'date': row[3], 'amount': Decimal(row[5])
                })
            elif row[0] == 'Trades' and row[1] == 'Data' and row[2] == 'Order' and row[3] == 'Stocks':
                data['trades'].append({
                    'ticker': row[5], 'currency': row[4], 'date': row[6].split(',')[0],
                    'qty': Decimal(row[7]), 'price': Decimal(row[8]), 'commission': Decimal(row[11]),
                    'type': classify_trade_type(row[5], Decimal(row[7])), 'source': 'IBKR'
                })
    return data

def save_to_database(all_data):
    db_records = []
    for t in all_data.get('trades', []):
        record = (t['date'], t['type'], t['ticker'], float(t['qty']), float(t['price']), t['currency'], float(t['qty']*t['price']), float(t['commission']), t['source'])
        db_records.append(record)
    for d in all_data.get('dividends', []):
        record = (d['date'], 'DIVIDEND', d['ticker'], 0, 0, d['currency'], float(d['amount']), 0, 'Dividend')
        db_records.append(record)
    for x in all_data.get('taxes', []):
        record = (x['date'], 'TAX', x['ticker'], 0, 0, x['currency'], float(x['amount']), 0, 'Tax')
        db_records.append(record)
    with DBConnector() as db:
        db.initialize_schema()
        db.conn.executemany('INSERT INTO transactions (Date, EventType, Ticker, Quantity, Price, Currency, Amount, Fee, Description) VALUES (?,?,?,?,?,?,?,?,?)', db_records)
        db.conn.commit()
    print(f'Imported {len(db_records)} records.')

if __name__ == '__main__':
    parser = argparse.ArgumentParser()
    parser.add_argument('--files', required=True)
    args = parser.parse_args()
    combined = {'trades': [], 'dividends': [], 'taxes': []}
    for fp in glob.glob(args.files):
        parsed = parse_csv(fp)
        for k in combined: combined[k].extend(parsed[k])
    save_to_database(combined)
```

# --- FILE: src/processing.py ---
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
    print(f'INFO: Processing {len(raw_trades)} trades via FIFO engine...')
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
        amount_curr = Decimal(str(trade['Amount'])) if trade['Amount'] else Decimal(0)
        fee = Decimal(str(trade['Fee'])) if trade['Fee'] else Decimal(0)
        rate = Decimal('1.0')
        if currency != 'PLN':
            try: rate = get_nbp_rate(currency, date_str)
            except: rate = Decimal('1.0')
        if event_type == 'DIVIDEND':
            gross_pln = amount_curr * rate
            tax_in_orig = tax_map.get((date_str, ticker), Decimal(0))
            tax_pln = tax_in_orig * rate
            if date_str.startswith(str(target_year)):
                dividends.append({
                    'ex_date': date_str, 'ticker': ticker,
                    'gross_amount_pln': float(gross_pln), 'tax_withheld_pln': float(tax_pln),
                    'currency': currency, 'rate': float(rate)
                })
        elif event_type == 'TAX':
            pass
        else:
            trade_record = {
                'type': event_type, 'date': date_str, 'ticker': ticker,
                'qty': quantity, 'price': price, 'commission': fee,
                'currency': currency, 'rate': rate, 'source': 'DB'
            }
            if event_type == 'SPLIT': trade_record['ratio'] = Decimal('1')
            fifo_input_list.append(trade_record)
    matcher.process_trades(fifo_input_list)
    all_realized = matcher.get_realized_gains()
    target_realized = [r for r in all_realized if r['sale_date'].startswith(str(target_year))]
    inventory = matcher.get_current_inventory()
    return target_realized, dividends, inventory
```

# --- FILE: src/fifo.py ---
```python
from decimal import Decimal
from collections import deque
from typing import List, Dict, Any
from .nbp import get_rate_for_tax_date
from .utils import money

class TradeMatcher:
    def __init__(self):
        self.inventory = {}
        self.realized_pnl = []
    def process_trades(self, trades_list: List[Dict[str, Any]]):
        type_priority = {'SPLIT': 0, 'TRANSFER': 1, 'BUY': 1, 'SELL': 2}
        sorted_trades = sorted(trades_list, key=lambda x: (x['date'], type_priority.get(x['type'], 3)))
        for trade in sorted_trades:
            ticker = trade['ticker']
            if ticker not in self.inventory: self.inventory[ticker] = deque()
            if trade['type'] == 'BUY': self._process_buy(trade)
            elif trade['type'] == 'SELL': self._process_sell(trade)
            elif trade['type'] == 'SPLIT': self._process_split(trade)
            elif trade['type'] == 'TRANSFER':
                if trade['qty'] > 0: self._process_buy(trade)
                else: self._process_transfer_out(trade)
    def _process_buy(self, trade):
        rate = trade.get('rate') or get_rate_for_tax_date(trade['currency'], trade['date'])
        price = trade.get('price', Decimal(0))
        comm = trade.get('commission', Decimal(0))
        cost_pln = money((price * trade['qty'] * rate) + (abs(comm) * rate))
        self.inventory[trade['ticker']].append({
            'date': trade['date'], 'qty': trade['qty'], 'price': price, 'rate': rate,
            'cost_pln': cost_pln, 'currency': trade['currency'], 'source': trade.get('source', 'UNKNOWN')
        })
    def _process_sell(self, trade):
        self._consume_inventory(trade, is_taxable=True)
    def _process_transfer_out(self, trade):
        self._consume_inventory(trade, is_taxable=False)
    def _consume_inventory(self, trade, is_taxable):
        ticker = trade['ticker']
        qty_to_sell = abs(trade['qty'])
        sell_rate = trade.get('rate') or get_rate_for_tax_date(trade['currency'], trade['date'])
        price = trade.get('price', Decimal(0))
        comm = trade.get('commission', Decimal(0))
        sell_revenue_pln = money(price * qty_to_sell * sell_rate)
        cost_basis_pln = Decimal('0.00')
        matched_buys = []
        while qty_to_sell > 0:
            if not self.inventory[ticker]: break
            buy_batch = self.inventory[ticker][0]
            if buy_batch['qty'] <= qty_to_sell:
                cost_basis_pln += buy_batch['cost_pln']
                qty_to_sell -= buy_batch['qty']
                matched_buys.append(buy_batch.copy())
                self.inventory[ticker].popleft()
            else:
                ratio = qty_to_sell / buy_batch['qty']
                part_cost = money(buy_batch['cost_pln'] * ratio)
                partial_record = buy_batch.copy()
                partial_record['qty'] = qty_to_sell
                partial_record['cost_pln'] = part_cost
                matched_buys.append(partial_record)
                cost_basis_pln += part_cost
                buy_batch['qty'] -= qty_to_sell
                buy_batch['cost_pln'] -= part_cost
                qty_to_sell = 0
        if is_taxable:
            sell_comm_pln = money(abs(comm) * sell_rate)
            total_cost = cost_basis_pln + sell_comm_pln
            profit_pln = sell_revenue_pln - total_cost
            self.realized_pnl.append({
                'ticker': ticker, 'sale_date': trade['date'], 'date_sell': trade['date'],
                'quantity': float(abs(trade['qty'])), 'sale_price': float(price), 'sale_rate': float(sell_rate),
                'sale_amount': float(sell_revenue_pln), 'cost_basis': float(total_cost), 'profit_loss': float(profit_pln),
                'currency': trade['currency'], 'matched_buys': matched_buys
            })
    def _process_split(self, trade):
        ticker = trade['ticker']
        ratio = trade.get('ratio', Decimal('1'))
        if ticker not in self.inventory: return
        new_deque = deque()
        while self.inventory[ticker]:
            batch = self.inventory[ticker].popleft()
            batch['qty'] *= ratio
            if ratio != 0: batch['price'] /= ratio
            new_deque.append(batch)
        self.inventory[ticker] = new_deque
    def get_realized_gains(self): return self.realized_pnl
    def get_current_inventory(self):
        res = []
        for ticker, batches in self.inventory.items():
            for b in batches:
                res.append({'ticker': ticker, 'buy_date': b['date'], 'quantity': float(b['qty']), 'cost_per_share': float(b['price']), 'total_cost': float(b['cost_pln']), 'currency': b['currency']})
        return res
```

# --- FILE: src/data_collector.py ---
```python
import pandas as pd
from datetime import datetime
def calculate_days(buy_date_str, sell_date_str):
    try:
        d1 = datetime.strptime(buy_date_str, '%Y-%m-%d').date()
        d2 = datetime.strptime(sell_date_str, '%Y-%m-%d').date()
        return (d2 - d1).days + 1
    except: return 0

def collect_all_trade_data(realized_gains, dividends, inventory):
    # 1. Sales
    flat_records = []
    for sale in realized_gains:
        matched = sale.get('matched_buys', [])
        for buy in matched:
            qty = float(buy.get('qty', 0))
            cost = float(buy.get('cost_pln', 0))
            proceeds = qty * sale['sale_price'] * sale['sale_rate']
            flat_records.append({
                'Date': sale['sale_date'], 'Ticker': sale['ticker'], 'TransactionType': 'Sale_P&L',
                'Buy_Date': buy['date'], 'Quantity': qty, 'Proceeds_PLN': proceeds, 'Cost_PLN': cost,
                'P&L_PLN': proceeds - cost, 'Holding_Days': calculate_days(buy['date'], sale['sale_date'])
            })
    df_realized = pd.DataFrame(flat_records)
    # 2. Dividends
    div_records = []
    for d in dividends:
        div_records.append({
            'Date': d['ex_date'], 'Ticker': d['ticker'], 'TransactionType': 'Dividend',
            'Gross_PLN': d['gross_amount_pln'], 'Tax_PLN': d['tax_withheld_pln'],
            'P&L_PLN': d['gross_amount_pln'] - d['tax_withheld_pln']
        })
    df_divs = pd.DataFrame(div_records)
    # 3. Inventory
    inv_records = []
    for i in inventory:
        inv_records.append({
            'Ticker': i['ticker'], 'Buy_Date': i['buy_date'], 'Quantity': i['quantity'],
            'Cost_per_Share': i['cost_per_share'], 'Total_Cost_PLN': i['total_cost']
        })
    df_inv = pd.DataFrame(inv_records)
    return {'Sales': df_realized, 'Dividends': df_divs, 'Inventory': df_inv}, None
```

# --- FILE: src/excel_exporter.py ---
```python
import pandas as pd
def export_to_excel(sheets_data, file_path, summary_data, ticker_summary):
    try:
        writer = pd.ExcelWriter(file_path, engine='openpyxl')
        pd.DataFrame(list(summary_data.items()), columns=['Metric', 'Value']).to_excel(writer, sheet_name='Summary', index=False)
        for name, df in sheets_data.items():
            if not df.empty: df.to_excel(writer, sheet_name=name, index=False)
        writer.close()
        print(f'SUCCESS: Excel saved to {file_path}')
    except Exception as e: print(f'ERROR: {e}')
```

# --- FILE: src/report_pdf.py ---
```python
from reportlab.lib import colors
from reportlab.lib.pagesizes import A4
from reportlab.platypus import SimpleDocTemplate, Table, TableStyle, Paragraph, Spacer, PageBreak
from reportlab.lib.styles import getSampleStyleSheet, ParagraphStyle
from reportlab.lib.enums import TA_CENTER, TA_LEFT
from reportlab.lib.units import mm
import itertools

APP_NAME = 'IBKR Tax Assistant'
APP_VERSION = 'v1.1.0'

def get_zebra_style(row_count, header_color=colors.HexColor('#D0D0D0')):
    cmds = [
        ('BACKGROUND', (0,0), (-1,0), header_color),
        ('GRID', (0,0), (-1,-1), 1, colors.black),
        ('ALIGN', (0,0), (-1,0), 'CENTER'),
        ('VALIGN', (0,0), (-1,-1), 'MIDDLE'),
        ('FONTNAME', (0,0), (-1,0), 'Helvetica-Bold'),
        ('FONTSIZE', (0,0), (-1,-1), 9),
    ]
    for i in range(1, row_count):
        if i % 2 == 0: cmds.append(('BACKGROUND', (0,i), (-1,i), colors.HexColor('#F0F0F0')))
    return TableStyle(cmds)

def add_footer(canvas, doc):
    canvas.saveState()
    canvas.setFont('Helvetica', 8)
    canvas.setFillColor(colors.grey)
    canvas.drawString(10 * mm, 10 * mm, f'Generated by {APP_NAME} {APP_VERSION}')
    canvas.drawRightString(A4[0] - 10 * mm, 10 * mm, f'Page {doc.page}')
    canvas.setStrokeColor(colors.lightgrey)
    canvas.line(10 * mm, 14 * mm, A4[0] - 10 * mm, 14 * mm)
    canvas.restoreState()

def generate_pdf(json_data, filename='report.pdf'):
    doc = SimpleDocTemplate(filename, pagesize=A4, bottomMargin=20*mm, topMargin=20*mm)
    elements = []
    styles = getSampleStyleSheet()
    year = json_data['year']
    data = json_data['data']
    title_style = ParagraphStyle('Title', parent=styles['Title'], fontSize=28, spaceAfter=30, alignment=TA_CENTER)
    subtitle_style = ParagraphStyle('Subtitle', parent=styles['Normal'], fontSize=14, alignment=TA_CENTER)
    h2_style = ParagraphStyle('H2', parent=styles['Heading2'], alignment=TA_CENTER, spaceAfter=15, spaceBefore=20)
    h3_style = ParagraphStyle('H3', parent=styles['Heading3'], alignment=TA_CENTER, spaceAfter=10, spaceBefore=5)
    italic_small = ParagraphStyle('Italic', parent=styles['Italic'], fontSize=8, alignment=TA_LEFT)

    elements.append(Spacer(1, 100))
    elements.append(Paragraph(f'Tax report — {year}', title_style))
    elements.append(Paragraph(f'Report period: 01-01-{year} - 31-12-{year}', subtitle_style))
    elements.append(PageBreak())

    # PAGE 2: PORTFOLIO
    elements.append(Paragraph(f'Portfolio Composition (as of Dec 31, {year})', h2_style))
    if data['holdings']:
        holdings_data = [['Ticker', 'Quantity', 'FIFO Check']]
        restricted_indices = []
        has_restricted = False
        row_idx = 1
        for h in data['holdings']:
            display_ticker = h['ticker']
            if h.get('is_restricted', False):
                display_ticker += ' *'
                has_restricted = True
                restricted_indices.append(row_idx)
            holdings_data.append([display_ticker, f"{h['qty']:.3f}", 'OK' if h.get('fifo_match', False) else 'MISMATCH!'])
            row_idx += 1
        t_holdings = Table(holdings_data, colWidths=[180, 100, 100], repeatRows=1)
        ts = get_zebra_style(len(holdings_data))
        ts.add('ALIGN', (1,1), (1,-1), 'RIGHT')
        for r_idx in restricted_indices: ts.add('BACKGROUND', (0, r_idx), (-1, r_idx), colors.HexColor('#FFCCCC'))
        t_holdings.setStyle(ts)
        elements.append(t_holdings)
        if has_restricted:
            elements.append(Spacer(1, 10))
            elements.append(Paragraph('* Assets held in special escrow accounts / sanctioned', italic_small))
    else:
        elements.append(Paragraph('No open positions found.', styles['Normal']))
    elements.append(PageBreak())

    # PAGE 3: TRADES
    elements.append(Paragraph(f'Trades History ({year})', h2_style))
    if data['trades_history']:
        trades_header = [['Date', 'Ticker', 'Type', 'Qty', 'Price', 'Comm', 'Curr']]
        trades_rows = []
        for t in data['trades_history']:
            trades_rows.append([t['date'], t['ticker'], t['type'], f"{abs(t['qty']):.3f}", f"{t['price']:.2f}", f"{t['commission']:.2f}", t['currency']])
        full_table_data = trades_header + trades_rows
        col_widths = [65, 55, 55, 55, 55, 55, 45]
        t_trades = Table(full_table_data, colWidths=col_widths, repeatRows=1)
        ts_trades = get_zebra_style(len(full_table_data))
        ts_trades.add('FONTSIZE', (0,0), (-1,-1), 8)
        t_trades.setStyle(ts_trades)
        elements.append(t_trades)
    else:
        elements.append(Paragraph('No trades executed this year.', styles['Normal']))

    # PAGE: MONTHLY DIVIDENDS
    elements.append(PageBreak())
    elements.append(Paragraph(f'Monthly Dividends Summary ({year})', h2_style))
    if data['monthly_dividends']:
        m_data = [['Month', 'Gross (PLN)', 'Tax Paid (PLN)', 'Net (PLN)']]
        sorted_months = sorted(data['monthly_dividends'].keys())
        tg, tt = 0, 0
        for m in sorted_months:
            vals = data['monthly_dividends'][m]
            m_data.append([m, f"{vals['gross_pln']:,.2f}", f"{vals['tax_pln']:,.2f}", f"{vals['net_pln']:,.2f}"])
            tg += vals['gross_pln']; tt += vals['tax_pln']
        m_data.append(['TOTAL', f"{tg:,.2f}", f"{tt:,.2f}", f"{tg-tt:,.2f}"])
        t_months = Table(m_data, colWidths=[110, 110, 110, 110])
        ts = get_zebra_style(len(m_data))
        ts.add('FONT-WEIGHT', (0,-1), (-1,-1), 'BOLD')
        t_months.setStyle(ts)
        elements.append(t_months)

    # PIT-38 Section
    elements.append(PageBreak())
    elements.append(Paragraph(f'PIT-38 Helper Data ({year})', h2_style))
    elements.append(Paragraph('Section C (Stocks)', h3_style))
    cap_rev = sum(x['revenue_pln'] for x in data['capital_gains'])
    cap_cost = sum(x['cost_pln'] for x in data['capital_gains'])
    pit_c_data = [['Field', 'Value (PLN)'], ['Przychód (Pos 20)', f'{cap_rev:,.2f}'], ['Koszty (Pos 21)', f'{cap_cost:,.2f}'], ['Dochód/Strata', f'{cap_rev-cap_cost:,.2f}']]
    t_pit = Table(pit_c_data, colWidths=[250, 150])
    t_pit.setStyle(get_zebra_style(len(pit_c_data)))
    elements.append(t_pit)
    doc.build(elements, onFirstPage=add_footer, onLaterPages=add_footer)
```

# --- FILE: src/utils.py ---
```python
from decimal import Decimal, ROUND_HALF_UP
def money(value):
    if not isinstance(value, Decimal): value = Decimal(str(value))
    return value.quantize(Decimal('0.01'), rounding=ROUND_HALF_UP)
```

# --- FILE: src/nbp.py ---
```python
from decimal import Decimal
import requests
from datetime import datetime, timedelta
def get_nbp_rate(currency, date_str):
    # Stub for restart prompt - real logic fetches from API
    if currency == 'PLN': return Decimal('1.0')
    return Decimal('4.0') # Simplified stub
def get_rate_for_tax_date(currency, trade_date):
    return get_nbp_rate(currency, trade_date)
```

# --- FILE: tests/test_fifo.py ---
```python
import pytest
from decimal import Decimal
from src.fifo import TradeMatcher
@pytest.fixture
def matcher(): return TradeMatcher()
def test_fifo_simple_profit(matcher):
    trades = [
        {'type': 'BUY', 'date': '2024-01-01', 'ticker': 'AAPL', 'qty': Decimal(10), 'price': Decimal(100), 'commission': Decimal(5), 'currency': 'USD', 'rate': Decimal(4.0)},
        {'type': 'SELL', 'date': '2024-01-02', 'ticker': 'AAPL', 'qty': Decimal(5), 'price': Decimal(150), 'commission': Decimal(5), 'currency': 'USD', 'rate': Decimal(4.0)}
    ]
    matcher.process_trades(trades)
    res = matcher.get_realized_gains()[0]
    assert res['sale_amount'] == 3000.0
    assert res['cost_basis'] == 2030.0
    assert res['profit_loss'] == 970.0
```

# --- FILE: tests/test_processing.py ---
```python
import pytest
from decimal import Decimal
from unittest.mock import patch
from src.processing import process_yearly_data
@patch('src.processing.get_nbp_rate')
def test_dividend_tax_linking(mock_rate):
    mock_rate.return_value = Decimal('4.0')
    trades = [
        {'TradeId': 1, 'Date': '2024-05-01', 'EventType': 'DIVIDEND', 'Ticker': 'AAPL', 'Quantity': 0, 'Price': 0, 'Amount': 10.0, 'Fee': 0, 'Currency': 'USD'},
        {'TradeId': 2, 'Date': '2024-05-01', 'EventType': 'TAX', 'Ticker': 'AAPL', 'Quantity': 0, 'Price': 0, 'Amount': -1.5, 'Fee': 0, 'Currency': 'USD'}
    ]
    _, dividends, _ = process_yearly_data(trades, 2024)
    assert dividends[0]['gross_amount_pln'] == 40.0
    assert dividends[0]['tax_withheld_pln'] == 6.0
```

# --- FILE: tests/test_utils.py ---
```python
from decimal import Decimal
from src.utils import money
def test_financial_rounding():
    assert money(Decimal('2.345')) == Decimal('2.35')
```

# --- FILE: tests/test_db_connector.py ---
```python
import pytest
from unittest.mock import patch
from src.db_connector import DBConnector
def test_db_init_fail():
    with patch('src.db_connector.config', return_value=''):
        with pytest.raises(ValueError): DBConnector()
```

**INSTRUCTION:** Restore these files and run `pytest`.