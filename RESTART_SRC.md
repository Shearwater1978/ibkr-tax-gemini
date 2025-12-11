# RESTART PROMPT: SOURCE CODE (v2.1.0)

**Context:** Part 1 of 2. Contains application source code.
**Instructions:** Restore these files to `src/` directory.

# --- FILE: src/db_connector.py ---
```python
import sqlite3
from decouple import config
class DBConnector:
    def __init__(self):
        self.db_path = config('DATABASE_PATH', default='data/ibkr_history.db')
        self.db_key = config('SQLCIPHER_KEY', default='')
        self.conn = None
        if not self.db_key: raise ValueError('SQLCIPHER_KEY missing')
    def __enter__(self):
        self.conn = sqlite3.connect(self.db_path)
        self.conn.row_factory = sqlite3.Row
        self.conn.execute(f"PRAGMA key='{self.db_key}';")
        return self
    def __exit__(self, exc_type, exc_val, exc_tb):
        if self.conn: self.conn.close()
    def initialize_schema(self):
        self.conn.execute('CREATE TABLE IF NOT EXISTS transactions (TradeId INTEGER PRIMARY KEY, Date TEXT, EventType TEXT, Ticker TEXT, Quantity REAL, Price REAL, Currency TEXT, Amount REAL, Fee REAL, Description TEXT)')
        self.conn.commit()
    def get_trades_for_calculation(self, year, ticker=None):
        q = 'SELECT * FROM transactions WHERE (EventType="BUY" OR Date BETWEEN ? AND ?) '
        params = [f'{year}-01-01', f'{year}-12-31']
        if ticker: 
            q += ' AND Ticker = ?'
            params.append(ticker)
        q += ' ORDER BY Date ASC, TradeId ASC'
        return [dict(row) for row in self.conn.execute(q, params).fetchall()]
```

# --- FILE: src/nbp.py ---
```python
import requests, calendar
from datetime import datetime, timedelta, date
from decimal import Decimal
_MONTHLY_CACHE = {}
def fetch_month_rates(curr, year, month):
    key = (curr, year, month)
    if key in _MONTHLY_CACHE: return
    s = date(year, month, 1)
    e = date(year, month, calendar.monthrange(year, month)[1])
    if s > date.today(): return
    if e > date.today(): e = date.today()
    url = f'http://api.nbp.pl/api/exchangerates/rates/a/{curr}/{s}/{e}/?format=json'
    try:
        r = requests.get(url, timeout=10)
        m = {}
        if r.status_code == 200: 
            for i in r.json().get('rates', []): m[i['effectiveDate']] = Decimal(str(i['mid']))
        _MONTHLY_CACHE[key] = m
    except: pass
def get_nbp_rate(curr, date_str):
    if curr == 'PLN': return Decimal(1)
    try: dt = datetime.strptime(date_str, '%Y-%m-%d').date()
    except: return Decimal(1)
    target = dt - timedelta(days=1)
    for _ in range(10):
        if (curr, target.year, target.month) not in _MONTHLY_CACHE: fetch_month_rates(curr, target.year, target.month)
        cache = _MONTHLY_CACHE.get((curr, target.year, target.month), {})
        d_s = target.strftime('%Y-%m-%d')
        if d_s in cache: return cache[d_s]
        target -= timedelta(days=1)
    return Decimal(1)
def get_rate_for_tax_date(c, d): return get_nbp_rate(c, d)
```

# --- FILE: src/parser.py ---
```python
import csv, re, glob, argparse, os
from datetime import datetime
from decimal import Decimal
from src.db_connector import DBConnector
def normalize_date(d):
    if not d: return None
    clean = d.split(',')[0].strip().split(' ')[0]
    for fmt in ['%Y-%m-%d', '%Y%m%d', '%m/%d/%Y']: 
        try: return datetime.strptime(clean, fmt).strftime('%Y-%m-%d')
        except: continue
    return None
def extract_ticker(desc, sym):
    if sym: return sym.strip()
    if not desc: return 'UNKNOWN'
    m = re.search(r'^([A-Z0-9.]+)\(', desc)
    if m: return m.group(1)
    parts = desc.split()
    if parts and len(parts[0])<6: return parts[0]
    return 'UNKNOWN'
def classify_trade_type(desc, qty):
    if 'ACATS' in desc.upper(): return 'TRANSFER'
    return 'BUY' if qty > 0 else 'SELL'
def parse_csv(fp):
    data = {'trades':[], 'dividends':[], 'taxes':[]}
    with open(fp, 'r', encoding='utf-8-sig') as f:
        reader = csv.reader(f)
        headers = {}
        for row in reader:
            if len(row) < 2: continue
            sec, typ = row[0], row[1]
            if typ == 'Header': 
                headers[sec] = {n:i for i,n in enumerate(row)}
                continue
            if typ != 'Data' or sec not in headers: continue
            h = headers[sec]
            if sec == 'Dividends':
                if 'Currency' not in h or 'Date' not in h: continue
                d_norm = normalize_date(row[h['Date']])
                if not d_norm or 'Total' in row[h['Description']]: continue
                data['dividends'].append({'ticker': extract_ticker(row[h['Description']], ''), 'currency': row[h['Currency']], 'date': d_norm, 'amount': Decimal(row[h['Amount']])})
            elif sec == 'Withholding Tax':
                if 'Currency' not in h or 'Date' not in h: continue
                d_norm = normalize_date(row[h['Date']])
                if not d_norm or 'Total' in row[h['Description']]: continue
                data['taxes'].append({'ticker': extract_ticker(row[h['Description']], ''), 'currency': row[h['Currency']], 'date': d_norm, 'amount': Decimal(row[h['Amount']])})
            elif sec == 'Trades' and row[h.get('Asset Category','')] == 'Stocks':
                if 'Symbol' not in h: continue
                qty = Decimal(row[h['Quantity']])
                data['trades'].append({'ticker': row[h['Symbol']], 'currency': row[h['Currency']], 'date': normalize_date(row[h['Date/Time']]), 'qty': qty, 'price': Decimal(row[h['T. Price']]), 'type': classify_trade_type(row[h.get('Description','')], qty), 'source': 'IBKR'})
    return data
def save_db(data):
    recs = []
    for t in data['trades']: recs.append((t['date'], t['type'], t['ticker'], float(t['qty']), float(t['price']), t['currency'], 0, 0, 'Trade'))
    for d in data['dividends']: recs.append((d['date'], 'DIVIDEND', d['ticker'], 0, 0, d['currency'], float(d['amount']), 0, 'Dividend'))
    for x in data['taxes']: recs.append((x['date'], 'TAX', x['ticker'], 0, 0, x['currency'], float(x['amount']), 0, 'Tax'))
    with DBConnector() as db:
        db.initialize_schema()
        db.conn.execute('DELETE FROM transactions')
        db.conn.executemany('INSERT INTO transactions (Date, EventType, Ticker, Quantity, Price, Currency, Amount, Fee, Description) VALUES (?,?,?,?,?,?,?,?,?)', recs)
        db.conn.commit()
if __name__ == '__main__':
    fs = glob.glob('data/*.csv')
    comb = {'trades':[], 'dividends':[], 'taxes':[]}
    for f in fs:
        p = parse_csv(f)
        for k in comb: comb[k].extend(p[k])
    save_db(comb)
```

# --- FILE: src/fifo.py ---
```python
from collections import deque
from decimal import Decimal
class TradeMatcher:
    def __init__(self):
        self.inventory = {}
        self.realized_pnl = []
    def process_trades(self, trades):
        for t in trades:
            if t['type'] == 'BUY':
                if t['ticker'] not in self.inventory: self.inventory[t['ticker']] = deque()
                self.inventory[t['ticker']].append(t)
            elif t['type'] == 'SELL':
                self.match_sell(t)
    def match_sell(self, sell_order):
        rem_qty = abs(sell_order['qty'])
        ticker = sell_order['ticker']
        while rem_qty > 0 and self.inventory.get(ticker):
            lot = self.inventory[ticker][0]
            matched = min(rem_qty, lot['qty'])
            self.realized_pnl.append({
                'ticker': ticker, 'date_sell': sell_order['date'], 'qty': matched,
                'sale_price': sell_order['price'], 'cost_basis': lot['price'],
                'sale_rate': sell_order['rate'], 'buy_rate': lot['rate'],
                'profit_loss': (sell_order['price']*sell_order['rate'] - lot['price']*lot['rate'])*matched
            })
            lot['qty'] -= matched
            rem_qty -= matched
            if lot['qty'] == 0: self.inventory[ticker].popleft()
    def get_current_inventory(self):
        res = []
        for t, q in self.inventory.items():
            for lot in q: res.append({'ticker': t, 'quantity': float(lot['qty']), 'cost_per_share': float(lot['price'])})
        return res
```

# --- FILE: src/processing.py ---
```python
from collections import defaultdict
from decimal import Decimal
from src.nbp import get_nbp_rate
from src.fifo import TradeMatcher
def process_yearly_data(raw, year):
    matcher = TradeMatcher()
    tax_map = defaultdict(Decimal)
    for t in raw:
        if t['EventType'] == 'TAX': tax_map[(t['Date'], t['Ticker'])] += abs(Decimal(str(t['Amount'])))
    fifo_in = []
    divs = []
    for t in raw:
        d = t['Date']
        sym = t['Ticker']
        if t['EventType'] == 'DIVIDEND':
            if d.startswith(str(year)):
                rate = get_nbp_rate(t['Currency'], d)
                divs.append({
                    'date': d, 'ticker': sym, 'gross_amount_pln': float(Decimal(str(t['Amount']))*rate),
                    'tax_withheld_pln': float(tax_map[(d, sym)]*rate)
                })
        elif t['EventType'] in ['BUY', 'SELL']:
            t['rate'] = get_nbp_rate(t['Currency'], d)
            t['type'] = t['EventType']
            t['qty'] = Decimal(str(t['Quantity']))
            t['price'] = Decimal(str(t['Price']))
            fifo_in.append(t)
    matcher.process_trades(fifo_in)
    realized = [x for x in matcher.realized_pnl if x['date_sell'].startswith(str(year))]
    return realized, divs, matcher.get_current_inventory()
```

# --- FILE: src/report_pdf.py ---
```python
from reportlab.platypus import SimpleDocTemplate, Paragraph, Spacer
from reportlab.lib.styles import getSampleStyleSheet
def generate_pdf(data, filepath):
    doc = SimpleDocTemplate(filepath)
    styles = getSampleStyleSheet()
    story = [Paragraph(f"Tax Report {data['year']}", styles['Title'])]
    story.append(Spacer(1, 12))
    story.append(Paragraph(f"Capital Gains: {len(data['data']['capital_gains'])} records", styles['Normal']))
    doc.build(story)
```

# --- FILE: src/excel_exporter.py ---
```python
import pandas as pd
def export_to_excel(sheets, path, summary, ticker_sum):
    with pd.ExcelWriter(path) as writer:
        pd.DataFrame(list(summary.items()), columns=['Key','Value']).to_excel(writer, sheet_name='Summary')
        for k, v in sheets.items(): pd.DataFrame(v).to_excel(writer, sheet_name=k)
```

# --- FILE: src/data_collector.py ---
```python
def collect_all_trade_data(realized, divs, inv):
    return {'Realized': realized, 'Dividends': divs, 'Inventory': inv}, {}, {}
```

# --- FILE: src/utils.py ---
```python
def money(x): return round(float(x), 2)
```

# --- FILE: src/__init__.py ---
```python
```
