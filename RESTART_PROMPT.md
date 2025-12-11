# RESTART PROMPT (GOLDEN BACKUP - Sprint 3 - v1.5.0)

**Role:** Expert Python Developer (Finance/Tax domain).
**Project:** IBKR Tax Calculator for Poland (PIT-38).
**Current State:** Sprint 3 Completed + Hotfixes (Parser/NBP).
**Features:**
- SQLCipher Encrypted DB
- FIFO Calculation
- Activity Statement Parsing (Dynamic Columns + Date Normalization)
- NBP Batch Caching (Smart T-1 lookup, low API usage)
- PDF/Excel Reporting

## 1. File Structure
```text
ROOT/
    .env                       # SQLCIPHER_KEY, DATABASE_PATH
    main.py                    # Entry point (CLI)
    requirements.txt           # pysqlcipher3, pandas, reportlab, openpyxl, python-decouple
    src/
        db_connector.py        # SQLCipher connection manager
        parser.py              # CSV Parsing logic (Activity Statements & Flex Queries)
        processing.py          # Logic Bridge: Links Tax->Divs, Runs FIFO
        fifo.py                # TradeMatcher (FIFO Engine)
        data_collector.py      # Prepares DataFrames for Excel
        excel_exporter.py      # Writes .xlsx files
        report_pdf.py          # Writes .pdf files (ReportLab)
        nbp.py                 # NBP API Client (Batch Caching)
        utils.py               # Financial rounding helpers
    tests/
        test_fifo.py
        test_processing.py
        test_parser.py
        test_nbp.py
```

## 2. Source Code Restoration
Please restore the files exactly as provided below.

# --- FILE: src/nbp.py ---
```python
import requests
import calendar
from datetime import datetime, timedelta, date
from decimal import Decimal
from typing import Dict, Optional

# Global Cache: {(currency, year, month): {date_str: rate_decimal}}
_MONTHLY_CACHE: Dict[tuple, Dict[str, Decimal]] = {}

def fetch_month_rates(currency: str, year: int, month: int) -> None:
    cache_key = (currency, year, month)
    if cache_key in _MONTHLY_CACHE: return
    start_date = date(year, month, 1)
    last_day = calendar.monthrange(year, month)[1]
    end_date = date(year, month, last_day)
    if start_date > date.today():
        _MONTHLY_CACHE[cache_key] = {}
        return
    if end_date > date.today(): end_date = date.today()
    fmt_start = start_date.strftime('%Y-%m-%d')
    fmt_end = end_date.strftime('%Y-%m-%d')
    url = f'http://api.nbp.pl/api/exchangerates/rates/a/{currency}/{fmt_start}/{fmt_end}/?format=json'
    try:
        response = requests.get(url, timeout=10)
        rates_map = {}
        if response.status_code == 200:
            data = response.json()
            for item in data.get('rates', []):
                rates_map[item['effectiveDate']] = Decimal(str(item['mid']))
        _MONTHLY_CACHE[cache_key] = rates_map
    except Exception as e:
        print(f'❌ NBP Network Error for {fmt_start}: {e}')

def get_nbp_rate(currency: str, date_str: str) -> Decimal:
    if currency == 'PLN': return Decimal('1.0')
    try: event_date = datetime.strptime(date_str, '%Y-%m-%d').date()
    except ValueError: return Decimal('1.0')
    target_date = event_date - timedelta(days=1)
    for _ in range(10):
        t_year, t_month = target_date.year, target_date.month
        if (currency, t_year, t_month) not in _MONTHLY_CACHE:
            fetch_month_rates(currency, t_year, t_month)
        month_data = _MONTHLY_CACHE.get((currency, t_year, t_month), {})
        t_str = target_date.strftime('%Y-%m-%d')
        if t_str in month_data: return month_data[t_str]
        target_date -= timedelta(days=1)
    print(f'❌ NBP FATAL: No rate for {currency} near {date_str}. Using 1.0')
    return Decimal('1.0')

def get_rate_for_tax_date(currency, trade_date):
    return get_nbp_rate(currency, trade_date)
```

# --- FILE: src/parser.py ---
```python
import csv
import re
import glob
import argparse
import os
from datetime import datetime
from decimal import Decimal
from typing import List, Dict, Any, Optional
from src.db_connector import DBConnector

def parse_decimal(value: str) -> Decimal:
    if not value: return Decimal(0)
    clean = value.replace(',', '').replace('"', '').strip()
    try: return Decimal(clean)
    except: return Decimal(0)

def normalize_date(date_str: str) -> Optional[str]:
    if not date_str: return None
    clean = date_str.split(',')[0].strip().split(' ')[0]
    formats = ['%Y-%m-%d', '%Y%m%d', '%m/%d/%Y', '%d/%m/%Y', '%d-%b-%y']
    for fmt in formats:
        try: return datetime.strptime(clean, fmt).strftime('%Y-%m-%d')
        except ValueError: continue
    return None

def extract_ticker(description: str, symbol_col: str) -> str:
    if symbol_col and symbol_col.strip(): return symbol_col.strip()
    if not description: return 'UNKNOWN'
    match = re.search(r'^([A-Za-z0-9\.]+)\(', description)
    if match: return match.group(1)
    parts = description.split()
    if parts and parts[0].isupper() and len(parts[0]) < 6:
        return parts[0].split('(')[0]
    return 'UNKNOWN'

def classify_trade_type(description: str, quantity: Decimal) -> str:
    desc_upper = description.upper()
    transfer_keywords = ['ACATS', 'TRANSFER', 'INTERNAL', 'POSITION MOVEM', 'RECEIVE DELIVER']
    if any(k in desc_upper for k in transfer_keywords): return 'TRANSFER'
    if quantity > 0: return 'BUY'
    if quantity < 0: return 'SELL'
    return 'UNKNOWN'

def get_col_idx(headers, possible_names):
    for name in possible_names:
        if name in headers: return headers[name]
    return None

def parse_csv(filepath: str) -> Dict[str, List]:
    data = {'trades': [], 'dividends': [], 'taxes': []}
    section_headers = {}
    print(f'Parsing: {os.path.basename(filepath)}')
    with open(filepath, 'r', encoding='utf-8-sig') as f:
        reader = csv.reader(f)
        for row in reader:
            if len(row) < 2: continue
            section, row_type = row[0], row[1]
            if row_type == 'Header':
                section_headers[section] = {n.strip(): i for i, n in enumerate(row)}
                continue
            if row_type != 'Data' or section not in section_headers: continue
            headers = section_headers[section]

            if section == 'Dividends':
                idx_cur = get_col_idx(headers, ['Currency'])
                idx_date = get_col_idx(headers, ['Date', 'PayDate'])
                idx_desc = get_col_idx(headers, ['Description', 'Label'])
                idx_amt = get_col_idx(headers, ['Amount', 'Gross Rate', 'Gross Amount'])
                if any(x is None for x in [idx_cur, idx_date, idx_desc, idx_amt]): continue
                if 'Total' in row[idx_desc] or 'Total' in row[idx_cur]: continue
                date_norm = normalize_date(row[idx_date])
                if not date_norm: continue
                data['dividends'].append({
                    'ticker': extract_ticker(row[idx_desc], ''), 'currency': row[idx_cur],
                    'date': date_norm, 'amount': parse_decimal(row[idx_amt])
                })

            elif section == 'Withholding Tax':
                idx_cur = get_col_idx(headers, ['Currency'])
                idx_date = get_col_idx(headers, ['Date'])
                idx_desc = get_col_idx(headers, ['Description', 'Label'])
                idx_amt = get_col_idx(headers, ['Amount'])
                if any(x is None for x in [idx_cur, idx_date, idx_desc, idx_amt]): continue
                if 'Total' in row[idx_desc] or 'Total' in row[idx_cur]: continue
                date_norm = normalize_date(row[idx_date])
                if not date_norm: continue
                data['taxes'].append({
                    'ticker': extract_ticker(row[idx_desc], ''), 'currency': row[idx_cur],
                    'date': date_norm, 'amount': parse_decimal(row[idx_amt])
                })

            elif section == 'Trades':
                col_asset = get_col_idx(headers, ['Asset Category', 'Asset Class'])
                if col_asset and row[col_asset] not in ['Stocks', 'Equity']: continue
                col_disc = get_col_idx(headers, ['DataDiscriminator', 'Header'])
                if col_disc and row[col_disc] not in ['Order', 'Trade']: continue
                idx_cur = get_col_idx(headers, ['Currency'])
                idx_sym = get_col_idx(headers, ['Symbol', 'Ticker'])
                idx_date = get_col_idx(headers, ['Date/Time', 'Date', 'TradeDate'])
                idx_qty = get_col_idx(headers, ['Quantity'])
                idx_price = get_col_idx(headers, ['T. Price', 'TradePrice', 'Price'])
                idx_comm = get_col_idx(headers, ['Comm/Fee', 'IBCommission', 'Commission'])
                idx_desc = get_col_idx(headers, ['Description'])
                if any(x is None for x in [idx_cur, idx_date, idx_qty, idx_price]): continue
                if idx_desc and 'Total' in row[idx_desc]: continue
                date_norm = normalize_date(row[idx_date])
                if not date_norm: continue
                qty = parse_decimal(row[idx_qty])
                desc = row[idx_desc] if idx_desc else ''
                data['trades'].append({
                    'ticker': extract_ticker(desc, row[idx_sym] if idx_sym else ''),
                    'currency': row[idx_cur], 'date': date_norm, 'qty': qty,
                    'price': parse_decimal(row[idx_price]),
                    'commission': parse_decimal(row[idx_comm]) if idx_comm else Decimal(0),
                    'type': classify_trade_type(desc, qty), 'source': 'IBKR'
                })
    return data

def save_to_database(all_data):
    db_records = []
    for t in all_data['trades']: db_records.append((t['date'], t['type'], t['ticker'], float(t['qty']), float(t['price']), t['currency'], float(t['qty']*t['price']), float(t['commission']), t['source']))
    for d in all_data['dividends']: db_records.append((d['date'], 'DIVIDEND', d['ticker'], 0, 0, d['currency'], float(d['amount']), 0, 'Dividend'))
    for x in all_data['taxes']: db_records.append((x['date'], 'TAX', x['ticker'], 0, 0, x['currency'], float(x['amount']), 0, 'Tax'))
    if not db_records: return
    with DBConnector() as db:
        db.initialize_schema()
        db.conn.execute('DELETE FROM transactions')
        db.conn.executemany('INSERT INTO transactions (Date, EventType, Ticker, Quantity, Price, Currency, Amount, Fee, Description) VALUES (?,?,?,?,?,?,?,?,?)', db_records)
        db.conn.commit()
    print(f'SUCCESS: Imported {len(db_records)} records.')

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
        if not self.db_key: raise ValueError('SQLCIPHER_KEY missing in .env')

    def __enter__(self):
        try:
            self.conn = sqlite3.connect(self.db_path)
            self.conn.row_factory = sqlite3.Row
            self.conn.execute(f"PRAGMA key='{self.db_key}';")
            return self
        except Exception as e:
            print(f'ERROR: SQLCipher failed. {e}')
            raise

    def __exit__(self, exc_type, exc_val, exc_tb):
        if self.conn: self.conn.close()

    def initialize_schema(self):
        if not self.conn: raise ConnectionError('DB not connected')
        query = """
        CREATE TABLE IF NOT EXISTS transactions (
            TradeId INTEGER PRIMARY KEY,
            Date TEXT NOT NULL, EventType TEXT NOT NULL, Ticker TEXT NOT NULL,
            Quantity REAL, Price REAL, Currency TEXT, Amount REAL, Fee REAL, Description TEXT
        );
        """
        self.conn.execute(query)
        self.conn.commit()

    def get_trades_for_calculation(self, target_year: int, ticker: Optional[str]) -> List[Dict[str, Any]]:
        if not self.conn: return []
        query = 'SELECT * FROM transactions WHERE 1=1'
        params = {}
        start_date = f'{target_year}-01-01'
        end_date = f'{target_year}-12-31'
        query += ' AND (EventType="BUY" OR Date BETWEEN :start AND :end)'
        params['start'] = start_date
        params['end'] = end_date
        if ticker:
            query += ' AND Ticker = :ticker'
            params['ticker'] = ticker
        query += ' ORDER BY Date ASC, TradeId ASC;'
        cursor = self.conn.execute(query, params)
        return [dict(row) for row in cursor.fetchall()]
```

# --- FILE: tests/test_parser.py ---
```python
import pytest
from decimal import Decimal
from src.parser import normalize_date, extract_ticker, parse_decimal, classify_trade_type

def test_normalize_date():
    assert normalize_date('20250102') == '2025-01-02'
    assert normalize_date('01/02/2025') == '2025-01-02'
    assert normalize_date('') is None

def test_extract_ticker():
    assert extract_ticker('AGR(US...)', '') == 'AGR'
    assert extract_ticker('TEST Cash Div', '') == 'TEST'
    assert extract_ticker('Unknown', 'AAPL') == 'AAPL'

def test_classify_trade():
    assert classify_trade_type('ACATS Transfer', Decimal(10)) == 'TRANSFER'
    assert classify_trade_type('Buy', Decimal(10)) == 'BUY'
```

# --- FILE: tests/test_nbp.py ---
```python
import pytest
from decimal import Decimal
from unittest.mock import patch, MagicMock
from src.nbp import get_nbp_rate, _MONTHLY_CACHE

@pytest.fixture(autouse=True)
def clear_cache():
    _MONTHLY_CACHE.clear()

@patch('src.nbp.requests.get')
def test_fetch_month_rates_success(mock_get):
    mock_response = MagicMock()
    mock_response.status_code = 200
    mock_response.json.return_value = {
        'rates': [
            {'effectiveDate': '2025-01-02', 'mid': 4.10},
            {'effectiveDate': '2025-01-03', 'mid': 4.15}
        ]
    }
    mock_get.return_value = mock_response
    rate = get_nbp_rate('USD', '2025-01-03')
    assert rate == Decimal('4.10')
    assert mock_get.call_count == 1
    rate2 = get_nbp_rate('USD', '2025-01-04')
    assert rate2 == Decimal('4.15')
    assert mock_get.call_count == 1
```

## 3. Instructions
Restore these files. Ensure `src/fifo.py`, `src/processing.py`, and `src/report_pdf.py` are present (from Sprint 3 baseline).
Run `python -m src.parser --files 'data/*.csv'` to re-import data.
Run `pytest` to verify integrity.
