# RESTART PROMPT: SOURCE CODE (v2.1.0)

**Context:** Part 1 of 2. Contains application source code.
**Instructions:** Restore these files to `src/` directory.

# --- FILE: src/db_connector.py ---
```python
# src/db_connector.py

import sqlite3
from typing import List, Dict, Any, Optional
from decouple import config

class DBConnector:
    """
    Manages the connection to the SQLCipher encrypted SQLite database.
    Handles connection setup, key management, and data retrieval with filtering.
    """
    def __init__(self):
        # Configuration is loaded from .env via python-decouple
        self.db_path = config('DATABASE_PATH', default='data/ibkr_history.db')
        self.db_key = config('SQLCIPHER_KEY', default='')
        self.conn: Optional[sqlite3.Connection] = None

        if not self.db_key:
            raise ValueError("SQLCIPHER_KEY is not set in the .env file. Cannot connect to encrypted database.")

    def __enter__(self):
        """Opens the encrypted database connection."""
        try:
            # NOTE: We use the standard sqlite3 interface, assuming the underlying
            # environment (like pysqlcipher3) handles the encryption settings via PRAGMA.
            self.conn = sqlite3.connect(self.db_path)
            self.conn.row_factory = sqlite3.Row  # Allows accessing columns by name
            
            # Execute PRAGMA key to set the decryption key for SQLCipher
            self.conn.execute(f"PRAGMA key='{self.db_key}';")
            
            print(f"INFO: Successfully connected to encrypted DB: {self.db_path}")
            return self

        except Exception as e:
            print(f"ERROR: Failed to open SQLCipher connection. Check key and path. {e}")
            self.conn = None
            raise

    def initialize_schema(self):
        """Creates the necessary database tables if they do not exist (e.g., 'transactions')."""
        if not self.conn:
            raise ConnectionError("Database connection is not open. Cannot initialize schema.")
            
        # Define the schema for the consolidated transactions table
        create_table_query = """
        CREATE TABLE IF NOT EXISTS transactions (
            TradeId INTEGER PRIMARY KEY,
            Date TEXT NOT NULL,
            EventType TEXT NOT NULL, -- e.g., 'BUY', 'SELL', 'DIVIDEND', 'MANUAL_ADJUST'
            Ticker TEXT NOT NULL,
            Quantity REAL,
            Price REAL,
            Currency TEXT,
            Amount REAL,
            Fee REAL,
            Description TEXT,
            -- Add an index for faster filtering and sorting
            INDEX_YEAR_TICKER INTEGER
        );
        """
        try:
            self.conn.execute(create_table_query)
            self.conn.commit()
            print("INFO: Database schema (transactions table) initialized successfully.")
        except Exception as e:
            print(f"ERROR: Failed to initialize schema. {e}")
            raise

    def __exit__(self, exc_type, exc_val, exc_tb):
        """Closes the database connection."""
        if self.conn:
            self.conn.close()

    def get_trades_for_calculation(self, target_year: int, ticker: Optional[str]) -> List[Dict[str, Any]]:
        """
        Retrieves all trades (buys, sales, dividends) relevant to the calculation.
        Filters data based on the target year and optional ticker.
        
        Args:
            target_year: The year used to determine which sales and dividends to include.
            ticker: Optional ticker to filter trades.

        Returns:
            A list of trade records as dictionaries.
        """
        if not self.conn:
            return []

        # We assume the database has a consolidated 'transactions' table
        query_parts = ["SELECT * FROM transactions WHERE 1=1"]
        params = {}
        
        # 1. Filter by Target Year (Sales/Dividends that occurred in that year)
        # We need all prior BUYS too, so we only filter the event date for non-BUYs.
        
        start_date = f"{target_year}-01-01"
        end_date = f"{target_year}-12-31"
        
        # NOTE: This query includes all Buys (Type='BUY') and all other events 
        # (Sales, Divs) that fall within the target year.
        query_parts.append(
            f"AND (EventType='BUY' OR Date BETWEEN :start_date AND :end_date)"
        )
        params['start_date'] = start_date
        params['end_date'] = end_date

        # 2. Filter by Ticker (if specified)
        if ticker:
            query_parts.append("AND Ticker = :ticker")
            params['ticker'] = ticker

        query = " ".join(query_parts) + " ORDER BY Date ASC, TradeId ASC;"
        
        cursor = self.conn.execute(query, params)
        
        # Convert sqlite3.Row objects to standard dictionaries for processing
        return [dict(row) for row in cursor.fetchall()]

# Example usage (simulated)
# if __name__ == "__main__":
#     try:
#         with DBConnector() as db:
#             trades = db.get_trades_for_calculation(target_year=2024, ticker='AAPL')
#             print(f"Loaded {len(trades)} records.")
#     except Exception:
#         print("Connection failed.")
```

# --- FILE: src/nbp.py ---
```python
# src/nbp.py

import requests
import calendar
from datetime import datetime, timedelta, date
from decimal import Decimal
from typing import Dict, Optional

# Глобальный кэш: {(currency, year, month): {date_str: rate_decimal}}
_MONTHLY_CACHE: Dict[tuple, Dict[str, Decimal]] = {}

def fetch_month_rates(currency: str, year: int, month: int) -> None:
    """
    Загружает курсы валют за ВЕСЬ месяц одним запросом и сохраняет в глобальный кэш.
    """
    cache_key = (currency, year, month)
    if cache_key in _MONTHLY_CACHE:
        return  # Уже загружено

    # Вычисляем первый и последний день месяца
    start_date = date(year, month, 1)
    last_day = calendar.monthrange(year, month)[1]
    end_date = date(year, month, last_day)

    # Если запрашиваем будущий месяц, данных нет, кэшируем пустоту и выходим
    if start_date > date.today():
        _MONTHLY_CACHE[cache_key] = {}
        return

    # Ограничиваем конец текущей датой (чтобы не просить курсы из будущего)
    if end_date > date.today():
        end_date = date.today()

    fmt_start = start_date.strftime("%Y-%m-%d")
    fmt_end = end_date.strftime("%Y-%m-%d")

    # Формируем запрос диапазона (Table A - средние курсы)
    url = f"http://api.nbp.pl/api/exchangerates/rates/a/{currency}/{fmt_start}/{fmt_end}/?format=json"

    try:
        # print(f"🌐 NBP API Fetch: {currency} for {fmt_start}..{fmt_end}")
        response = requests.get(url, timeout=10)
        
        rates_map = {}
        if response.status_code == 200:
            data = response.json()
            # Разбираем ответ: [{'no': '...', 'effectiveDate': '2025-01-02', 'mid': 4.1012}, ...]
            for item in data.get('rates', []):
                d_str = item['effectiveDate']
                rate_val = Decimal(str(item['mid']))
                rates_map[d_str] = rate_val
        elif response.status_code == 404:
            # 404 для диапазона значит, что в этом диапазоне нет курсов (например, одни праздники или начало месяца)
            # Это нормально, сохраняем пустой словарь
            pass
        else:
            print(f"⚠️ NBP API Warning: HTTP {response.status_code} for {url}")

        _MONTHLY_CACHE[cache_key] = rates_map

    except Exception as e:
        print(f"❌ NBP Network Error for {fmt_start}: {e}")
        # Не сохраняем в кэш, чтобы при следующем вызове попробовать снова? 
        # Или сохраняем пустоту, чтобы не ддосить? Лучше не сохранять, вдруг сеть моргнула.
        pass

def get_nbp_rate(currency: str, date_str: str) -> Decimal:
    """
    Возвращает курс NBP (средний) для указанной валюты на день, 
    ПРЕДШЕСТВУЮЩИЙ указанной дате (правило T-1).
    Использует кэширование по месяцам.
    """
    if currency == 'PLN':
        return Decimal('1.0')

    try:
        event_date = datetime.strptime(date_str, "%Y-%m-%d").date()
    except ValueError:
        print(f"⚠️ NBP: Invalid date format {date_str}, using 1.0")
        return Decimal('1.0')

    # Начинаем поиск с T-1
    target_date = event_date - timedelta(days=1)

    # Пытаемся найти курс, отматывая назад до 10 дней
    # (обычно достаточно 3-4 дней для длинных выходных)
    for _ in range(10):
        t_year = target_date.year
        t_month = target_date.month
        t_str = target_date.strftime("%Y-%m-%d")

        # 1. Проверяем, загружен ли этот месяц
        if (currency, t_year, t_month) not in _MONTHLY_CACHE:
            fetch_month_rates(currency, t_year, t_month)

        # 2. Ищем дату в кэше
        month_data = _MONTHLY_CACHE.get((currency, t_year, t_month), {})
        
        if t_str in month_data:
            return month_data[t_str]

        # Если не нашли, идем на день назад (и на следующей итерации проверим кэш)
        target_date -= timedelta(days=1)

    print(f"❌ NBP FATAL: Could not find rate for {currency} around {date_str}. Using 1.0 fallback.")
    return Decimal('1.0')

def get_rate_for_tax_date(currency, trade_date):
    """Алиас для совместимости"""
    return get_nbp_rate(currency, trade_date)
```

# --- FILE: src/parser.py ---
```python
# src/parser.py

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
    """Убирает запятые и пробелы, парсит число."""
    if not value: return Decimal(0)
    clean = value.replace(',', '').replace('"', '').strip()
    try:
        return Decimal(clean)
    except:
        return Decimal(0)

def normalize_date(date_str: str) -> Optional[str]:
    """Приводит дату к формату YYYY-MM-DD. Возвращает None, если дата пустая."""
    if not date_str: return None
    
    # Отрезаем время, если есть
    clean = date_str.split(',')[0].strip().split(' ')[0]
    
    formats = ["%Y-%m-%d", "%Y%m%d", "%m/%d/%Y", "%d/%m/%Y", "%d-%b-%y"]
    for fmt in formats:
        try:
            return datetime.strptime(clean, fmt).strftime("%Y-%m-%d")
        except ValueError:
            continue
    return None

def extract_ticker(description: str, symbol_col: str) -> str:
    if symbol_col and symbol_col.strip(): return symbol_col.strip()
    if not description: return "UNKNOWN"
    match = re.search(r'^([A-Za-z0-9\.]+)\(', description)
    if match: return match.group(1)
    parts = description.split()
    if parts and parts[0].isupper() and len(parts[0]) < 6:
        return parts[0].split('(')[0]
    return "UNKNOWN"

def classify_trade_type(description: str, quantity: Decimal) -> str:
    """Определяет тип сделки: BUY, SELL или TRANSFER."""
    desc_upper = description.upper()
    # Ключевые слова для трансферов (ввод/вывод активов без продажи)
    transfer_keywords = ["ACATS", "TRANSFER", "INTERNAL", "POSITION MOVEM", "RECEIVE DELIVER"]
    
    if any(k in desc_upper for k in transfer_keywords):
        return "TRANSFER"
    if quantity > 0: return "BUY"
    if quantity < 0: return "SELL"
    return "UNKNOWN"

def get_col_idx(headers: Dict[str, int], possible_names: List[str]) -> Optional[int]:
    for name in possible_names:
        if name in headers: return headers[name]
    return None

def parse_csv(filepath: str) -> Dict[str, List]:
    data = {'trades': [], 'dividends': [], 'taxes': []}
    section_headers = {}
    print(f"\n📂 Parsing file: {os.path.basename(filepath)}")
    
    with open(filepath, 'r', encoding='utf-8-sig') as f:
        reader = csv.reader(f)
        for row in reader:
            if len(row) < 2: continue
            section, row_type = row[0], row[1]
            
            if row_type == 'Header':
                header_map = {name.strip(): idx for idx, name in enumerate(row)}
                section_headers[section] = header_map
                continue

            if row_type != 'Data': continue
            if section not in section_headers: continue
            
            headers = section_headers[section]

            # --- DIVIDENDS ---
            if section == 'Dividends':
                idx_cur  = get_col_idx(headers, ['Currency'])
                idx_date = get_col_idx(headers, ['Date', 'PayDate'])
                idx_desc = get_col_idx(headers, ['Description', 'Label'])
                idx_amt  = get_col_idx(headers, ['Amount', 'Gross Rate', 'Gross Amount'])

                if any(x is None for x in [idx_cur, idx_date, idx_desc, idx_amt]): continue

                desc = row[idx_desc]
                if "Total" in desc or "Total" in row[idx_cur]: continue

                date_norm = normalize_date(row[idx_date])
                if not date_norm: continue 

                ticker = extract_ticker(desc, "")
                amount = parse_decimal(row[idx_amt])
                
                print(f"   💰 Found DIVIDEND: {date_norm} | {ticker} | {amount} {row[idx_cur]}")
                
                data['dividends'].append({
                    'ticker': ticker,
                    'currency': row[idx_cur],
                    'date': date_norm,
                    'amount': amount
                })

            # --- TAXES ---
            elif section == 'Withholding Tax':
                idx_cur  = get_col_idx(headers, ['Currency'])
                idx_date = get_col_idx(headers, ['Date'])
                idx_desc = get_col_idx(headers, ['Description', 'Label'])
                idx_amt  = get_col_idx(headers, ['Amount'])

                if any(x is None for x in [idx_cur, idx_date, idx_desc, idx_amt]): continue
                
                desc = row[idx_desc]
                if "Total" in desc or "Total" in row[idx_cur]: continue

                date_norm = normalize_date(row[idx_date])
                if not date_norm: continue 

                print(f"   💸 Found TAX: {date_norm} | {extract_ticker(desc, '')} | {row[idx_amt]}")

                data['taxes'].append({
                    'ticker': extract_ticker(desc, ""),
                    'currency': row[idx_cur],
                    'date': date_norm,
                    'amount': parse_decimal(row[idx_amt])
                })
            
            # --- TRADES ---
            elif section == 'Trades':
                col_asset = get_col_idx(headers, ['Asset Category', 'Asset Class'])
                if col_asset and row[col_asset] not in ['Stocks', 'Equity']: continue
                
                col_disc = get_col_idx(headers, ['DataDiscriminator', 'Header'])
                if col_disc and row[col_disc] not in ['Order', 'Trade']: continue

                idx_cur   = get_col_idx(headers, ['Currency'])
                idx_sym   = get_col_idx(headers, ['Symbol', 'Ticker'])
                idx_date  = get_col_idx(headers, ['Date/Time', 'Date', 'TradeDate'])
                idx_qty   = get_col_idx(headers, ['Quantity'])
                idx_price = get_col_idx(headers, ['T. Price', 'TradePrice', 'Price'])
                idx_comm  = get_col_idx(headers, ['Comm/Fee', 'IBCommission', 'Commission'])
                idx_desc  = get_col_idx(headers, ['Description'])

                if any(x is None for x in [idx_cur, idx_date, idx_qty, idx_price]): continue
                if idx_desc and "Total" in row[idx_desc]: continue

                date_norm = normalize_date(row[idx_date])
                if not date_norm: continue

                qty = parse_decimal(row[idx_qty])
                desc = row[idx_desc] if idx_desc else ""
                
                # Использование восстановленной функции
                trade_type = classify_trade_type(desc, qty)

                data['trades'].append({
                    'ticker': extract_ticker(desc, row[idx_sym] if idx_sym else ""),
                    'currency': row[idx_cur],
                    'date': date_norm,
                    'qty': qty,
                    'price': parse_decimal(row[idx_price]),
                    'commission': parse_decimal(row[idx_comm]) if idx_comm else Decimal(0),
                    'type': trade_type,
                    'source': 'IBKR'
                })

    return data

def save_to_database(all_data):
    db_records = []
    for t in all_data.get('trades', []):
        db_records.append((t['date'], t['type'], t['ticker'], float(t['qty']), float(t['price']), t['currency'], float(t['qty']*t['price']), float(t['commission']), t['source']))
    for d in all_data.get('dividends', []):
        db_records.append((d['date'], 'DIVIDEND', d['ticker'], 0, 0, d['currency'], float(d['amount']), 0, 'Dividend'))
    for x in all_data.get('taxes', []):
        db_records.append((x['date'], 'TAX', x['ticker'], 0, 0, x['currency'], float(x['amount']), 0, 'Tax'))

    if not db_records:
        print("⚠️  No records parsed!")
        return

    with DBConnector() as db:
        db.initialize_schema()
        print("🧹 Cleaning DB before import...")
        db.conn.execute("DELETE FROM transactions")
        print(f"📥 Inserting {len(db_records)} records...")
        db.conn.executemany('INSERT INTO transactions (Date, EventType, Ticker, Quantity, Price, Currency, Amount, Fee, Description) VALUES (?,?,?,?,?,?,?,?,?)', db_records)
        db.conn.commit()

if __name__ == '__main__':
    parser = argparse.ArgumentParser()
    parser.add_argument('--files', required=True)
    args = parser.parse_args()
    combined = {'trades': [], 'dividends': [], 'taxes': []}
    
    files = glob.glob(args.files)
    if not files:
        print("No files found.")
        exit(1)

    for fp in files:
        parsed = parse_csv(fp)
        for k in combined: combined[k].extend(parsed[k])
    
    save_to_database(combined)
```

# --- FILE: src/fifo.py ---
```python
# src/fifo.py

import json
from decimal import Decimal
from collections import deque
from typing import List, Dict, Any

# Импортируем функции. Убедитесь, что src/utils.py существует.
from .nbp import get_rate_for_tax_date
from .utils import money

class TradeMatcher:
    def __init__(self):
        self.inventory = {} 
        self.realized_pnl = []

    def save_state(self, filepath: str, cutoff_date: str):
        serializable_inv = {}
        for ticker, queue in self.inventory.items():
            batches = []
            for batch in queue:
                b_copy = batch.copy()
                b_copy['qty'] = str(b_copy['qty'])
                b_copy['price'] = str(b_copy['price'])
                b_copy['cost_pln'] = str(b_copy['cost_pln'])
                if 'rate' in b_copy:
                    b_copy['rate'] = float(b_copy['rate'])
                batches.append(b_copy)
            if batches:
                serializable_inv[ticker] = batches
        
        data = {
            "cutoff_date": cutoff_date,
            "inventory": serializable_inv
        }
        
        try:
            with open(filepath, 'w', encoding='utf-8') as f:
                json.dump(data, f, indent=2)
            print(f"💾 Snapshot saved to {filepath} (Cutoff: {cutoff_date})")
        except Exception as e:
            print(f"WARNING: Failed to save snapshot: {e}")

    def load_state(self, filepath: str) -> str:
        try:
            with open(filepath, 'r', encoding='utf-8') as f:
                data = json.load(f)
        except FileNotFoundError:
            return "1900-01-01"
        
        cutoff = data.get("cutoff_date", "1900-01-01")
        loaded_inv = data.get("inventory", {})
        
        self.inventory = {}
        count_positions = 0
        
        for ticker, batches in loaded_inv.items():
            self.inventory[ticker] = deque()
            for b in batches:
                b['qty'] = Decimal(b['qty'])
                b['price'] = Decimal(b['price'])
                b['cost_pln'] = Decimal(b['cost_pln'])
                self.inventory[ticker].append(b)
            count_positions += 1
            
        print(f"📂 Snapshot loaded: {count_positions} positions restored (Cutoff: {cutoff}).")
        return cutoff

    def process_trades(self, trades_list: List[Dict[str, Any]]):
        # Order matters for correct FIFO/Tax calculations
        type_priority = {'SPLIT': 0, 'TRANSFER': 1, 'BUY': 1, 'SELL': 2}
        
        sorted_trades = sorted(
            trades_list, 
            key=lambda x: (x['date'], type_priority.get(x['type'], 3))
        )

        for trade in sorted_trades:
            ticker = trade['ticker']
            if ticker not in self.inventory:
                self.inventory[ticker] = deque()

            if trade['type'] == 'BUY':
                self._process_buy(trade)
            elif trade['type'] == 'SELL':
                self._process_sell(trade)
            elif trade['type'] == 'SPLIT':
                self._process_split(trade)
            elif trade['type'] == 'TRANSFER':
                if trade['qty'] > 0:
                    self._process_buy(trade)
                else:
                    self._process_transfer_out(trade)

    def _process_buy(self, trade):
        # OPTIMIZATION: Use injected rate if available to avoid DB/API call
        if 'rate' in trade and trade['rate']:
            rate = trade['rate']
        else:
            rate = get_rate_for_tax_date(trade['currency'], trade['date'])
            
        price = trade.get('price', Decimal(0))
        comm = trade.get('commission', Decimal(0))
        
        # Calculate Cost in PLN
        cost_pln = money((price * trade['qty'] * rate) + (abs(comm) * rate))
        
        self.inventory[trade['ticker']].append({
            "date": trade['date'],
            "qty": trade['qty'],
            "price": price,
            "rate": rate,
            "cost_pln": cost_pln,
            "currency": trade['currency'],
            "source": trade.get('source', 'UNKNOWN')
        })

    def _process_sell(self, trade):
        self._consume_inventory(trade, is_taxable=True)

    def _process_transfer_out(self, trade):
        self._consume_inventory(trade, is_taxable=False)

    def _consume_inventory(self, trade, is_taxable):
        ticker = trade['ticker']
        qty_to_sell = abs(trade['qty'])
        
        # OPTIMIZATION: Use injected rate
        if 'rate' in trade and trade['rate']:
            sell_rate = trade['rate']
        else:
            sell_rate = get_rate_for_tax_date(trade['currency'], trade['date'])
            
        price = trade.get('price', Decimal(0))
        comm = trade.get('commission', Decimal(0))
        
        sell_revenue_pln = money(price * qty_to_sell * sell_rate)
        
        cost_basis_pln = Decimal("0.00")
        matched_buys = []

        while qty_to_sell > 0:
            if not self.inventory[ticker]: 
                # Handling empty inventory (e.g. data missing or short sell)
                # We log it and break to avoid infinite loops or crashes
                print(f"WARNING: Insufficient inventory for {ticker} sell on {trade['date']}. Missing {qty_to_sell}")
                break 

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
                "ticker": ticker,
                "sale_date": trade['date'], # Renamed to match data_collector expectation
                "date_sell": trade['date'],
                "quantity": float(abs(trade['qty'])),
                "sale_price": float(price),
                "sale_rate": float(sell_rate),
                "sale_amount": float(sell_revenue_pln), # Revenue
                "cost_basis": float(total_cost),        # Cost
                "profit_loss": float(profit_pln),       # P&L
                "currency": trade['currency'],
                "matched_buys": matched_buys
            })

    def _process_split(self, trade):
        ticker = trade['ticker']
        ratio = trade.get('ratio', Decimal("1"))
        if ticker not in self.inventory: return

        new_deque = deque()
        while self.inventory[ticker]:
            batch = self.inventory[ticker].popleft()
            new_qty = batch['qty'] * ratio
            
            # Avoid division by zero if ratio is weird, though usually valid
            if ratio != 0:
                new_price = batch['price'] / ratio
            else:
                new_price = batch['price']

            batch['qty'] = new_qty
            batch['price'] = new_price
            # cost_pln remains the same for the batch in a split
            new_deque.append(batch)
        self.inventory[ticker] = new_deque

    def get_realized_gains(self):
        """Adapter method for new architecture"""
        return self.realized_pnl

    def get_current_inventory(self):
        """
        Returns inventory in a format suitable for the Excel exporter.
        Flattens the deque queues into a list of dictionaries.
        """
        inventory_list = []
        for ticker, batches in self.inventory.items():
            for batch in batches:
                inventory_list.append({
                    'ticker': ticker,
                    'buy_date': batch['date'],
                    'quantity': float(batch['qty']),
                    'cost_per_share': float(batch['price']),
                    'total_cost': float(batch['cost_pln']),
                    'currency': batch['currency']
                })
        return inventory_list
```

# --- FILE: src/processing.py ---
```python
# src/processing.py

from typing import List, Dict, Any, Tuple
from decimal import Decimal
from collections import defaultdict
import logging

# Project imports
from src.nbp import get_nbp_rate
from src.fifo import TradeMatcher 

def process_yearly_data(raw_trades: List[Dict[str, Any]], target_year: int) -> Tuple[List[Dict], List[Dict], List[Dict]]:
    """
    Orchestrates the processing of raw database records into calculated tax reports.
    Adapts SQLCipher data to the TradeMatcher input format.
    Matches Withholding Taxes to Dividends.
    """
    
    matcher = TradeMatcher()
    
    dividends = []
    fifo_input_list = []
    
    print(f"INFO: Processing {len(raw_trades)} trades via FIFO engine...")

    # --- 0. Pre-process Taxes (Link TAX rows to Dividends) ---
    # IBKR reports store Withholding Tax as separate rows.
    # We map (Date, Ticker) -> Total Tax Amount (absolute value)
    tax_map = defaultdict(Decimal)
    
    for t in raw_trades:
        if t['EventType'] == 'TAX':
            # Tax amount is usually negative in DB, we need positive magnitude
            amt = Decimal(str(t['Amount'])) if t['Amount'] else Decimal(0)
            key = (t['Date'], t['Ticker'])
            tax_map[key] += abs(amt)

    # Sort trades by date and ID
    sorted_trades = sorted(raw_trades, key=lambda x: (x['Date'], x['TradeId']))

    for trade in sorted_trades:
        # Extract basic fields from DB
        date_str = trade['Date']
        ticker = trade['Ticker']
        event_type = trade['EventType'] # BUY, SELL, DIVIDEND, SPLIT, TAX
        currency = trade['Currency']
        
        # Convert DB types to Decimal
        quantity = Decimal(str(trade['Quantity'])) if trade['Quantity'] else Decimal(0)
        price = Decimal(str(trade['Price'])) if trade['Price'] else Decimal(0)
        amount_currency = Decimal(str(trade['Amount'])) if trade['Amount'] else Decimal(0)
        fee = Decimal(str(trade['Fee'])) if trade['Fee'] else Decimal(0)
        
        description = trade.get('Description', '')

        # --- 1. Get NBP Rate ---
        rate = Decimal("1.0")
        if currency != 'PLN':
            try:
                rate = get_nbp_rate(currency, date_str)
            except Exception as e:
                print(f"WARNING: Could not fetch NBP rate for {currency} on {date_str}. Using 1.0. Error: {e}")
                rate = Decimal("1.0")

        # --- 2. Build Logic ---
        
        if event_type == 'DIVIDEND':
            # Calculate Dividend in PLN
            gross_pln = amount_currency * rate
            
            # Look up the tax for this specific dividend (Same Date, Same Ticker)
            tax_in_original_currency = tax_map.get((date_str, ticker), Decimal(0))
            tax_pln = tax_in_original_currency * rate
            
            div_record = {
                'ex_date': date_str,
                'ticker': ticker,
                'gross_amount_pln': float(gross_pln),
                'tax_withheld_pln': float(tax_pln), # <--- NOW FILLED
                'currency': currency,
                'rate': float(rate)
            }
            if date_str.startswith(str(target_year)):
                dividends.append(div_record)
        
        elif event_type == 'TAX':
            # Skip processing TAX rows here, as they are handled via tax_map above
            pass
            
        else:
            # Handle Trades (BUY, SELL, SPLIT, TRANSFER)
            matcher_type = event_type
            
            trade_record = {
                'type': matcher_type,
                'date': date_str,
                'ticker': ticker,
                'qty': quantity,
                'price': price,
                'commission': fee,
                'currency': currency,
                'rate': rate,
                'source': 'DB'
            }
            
            if matcher_type == 'SPLIT':
                trade_record['ratio'] = Decimal("1") 

            fifo_input_list.append(trade_record)

    # --- 3. Execute FIFO Logic ---
    matcher.process_trades(fifo_input_list)

    # --- 4. Extract Results ---
    all_realized = matcher.get_realized_gains()
    
    target_realized = [
        r for r in all_realized 
        if r['sale_date'].startswith(str(target_year))
    ]
    
    inventory = matcher.get_current_inventory()
    
    return target_realized, dividends, inventory
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

APP_NAME = "IBKR Tax Assistant"
APP_VERSION = "v1.1.0"

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
        if i % 2 == 0:
            cmds.append(('BACKGROUND', (0,i), (-1,i), colors.HexColor('#F0F0F0')))
    return TableStyle(cmds)

def add_footer(canvas, doc):
    canvas.saveState()
    canvas.setFont('Helvetica', 8)
    canvas.setFillColor(colors.grey)
    footer_text = f"Generated by {APP_NAME} {APP_VERSION}"
    canvas.drawString(10 * mm, 10 * mm, footer_text)
    page_num = f"Page {doc.page}"
    canvas.drawRightString(A4[0] - 10 * mm, 10 * mm, page_num)
    canvas.setStrokeColor(colors.lightgrey)
    canvas.line(10 * mm, 14 * mm, A4[0] - 10 * mm, 14 * mm)
    canvas.restoreState()

def generate_pdf(json_data, filename="report.pdf"):
    doc = SimpleDocTemplate(filename, pagesize=A4, bottomMargin=20*mm, topMargin=20*mm)
    elements = []
    styles = getSampleStyleSheet()
    
    year = json_data['year']
    data = json_data['data']

    title_style = ParagraphStyle('ReportTitle', parent=styles['Title'], fontSize=28, spaceAfter=30, alignment=TA_CENTER)
    subtitle_style = ParagraphStyle('ReportSubtitle', parent=styles['Normal'], fontSize=14, alignment=TA_CENTER)
    h2_style = ParagraphStyle('H2Centered', parent=styles['Heading2'], alignment=TA_CENTER, spaceAfter=15, spaceBefore=20)
    h3_style = ParagraphStyle('H3Centered', parent=styles['Heading3'], alignment=TA_CENTER, spaceAfter=10, spaceBefore=5)
    normal_style = styles['Normal']
    italic_small = ParagraphStyle('ItalicSmall', parent=styles['Italic'], fontSize=8, alignment=TA_LEFT)
    
    # PAGE 1
    elements.append(Spacer(1, 100))
    elements.append(Paragraph(f"Tax report — {year}", title_style))
    elements.append(Spacer(1, 10))
    elements.append(Paragraph(f"Report period: 01-01-{year} - 31-12-{year}", subtitle_style))
    elements.append(PageBreak())

    # PAGE 2: PORTFOLIO (WITH FIFO CHECK)
    elements.append(Paragraph(f"Portfolio Composition (as of Dec 31, {year})", h2_style))
    if data['holdings']:
        holdings_data = [["Ticker", "Quantity", "FIFO Check"]]
        restricted_indices = []
        has_restricted = False
        
        row_idx = 1
        for h in data['holdings']:
            display_ticker = h['ticker']
            if h.get('is_restricted', False):
                display_ticker += " *"
                has_restricted = True
                restricted_indices.append(row_idx)
            
            check_mark = "OK" if h.get('fifo_match', False) else "MISMATCH!"
            holdings_data.append([display_ticker, f"{h['qty']:.3f}", check_mark])
            row_idx += 1
            
        t_holdings = Table(holdings_data, colWidths=[180, 100, 100], repeatRows=1)
        ts = get_zebra_style(len(holdings_data))
        
        # --- STYLING ---
        ts.add('ALIGN', (1,1), (1,-1), 'RIGHT')  # Qty -> Right
        ts.add('ALIGN', (2,1), (2,-1), 'CENTER') # FIFO Check -> Center (FIXED)
        
        # Color coding for Mismatches
        for i, row in enumerate(holdings_data[1:], start=1):
            if row[2] != "OK":
                ts.add('TEXTCOLOR', (2, i), (2, i), colors.red)
        
        # Red Highlight for Restricted
        for r_idx in restricted_indices:
            ts.add('BACKGROUND', (0, r_idx), (-1, r_idx), colors.HexColor('#FFCCCC'))
            
        t_holdings.setStyle(ts)
        elements.append(t_holdings)
        
        if has_restricted:
            elements.append(Spacer(1, 10))
            elements.append(Paragraph("* Assets held in special escrow accounts / sanctioned (RUB)", italic_small))
    else:
        elements.append(Paragraph("No open positions found at end of year.", normal_style))
    elements.append(PageBreak())

    # PAGE 3: TRADES HISTORY
    elements.append(Paragraph(f"Trades History ({year})", h2_style))
    if data['trades_history']:
        trades_header = [["Date", "Ticker", "Type", "Qty", "Price", "Comm", "Curr"]]
        trades_rows = []
        for t in data['trades_history']:
            t_type = t.get('type', 'UNKNOWN')
            row = [
                t['date'],
                t['ticker'],
                t_type,
                f"{abs(t['qty']):.3f}",
                f"{t['price']:.2f}",
                f"{t['commission']:.2f}",
                t['currency']
            ]
            trades_rows.append(row)
        full_table_data = trades_header + trades_rows
        col_widths = [65, 55, 55, 55, 55, 55, 45]
        t_trades = Table(full_table_data, colWidths=col_widths, repeatRows=1)
        ts_trades = get_zebra_style(len(full_table_data))
        ts_trades.add('ALIGN', (3,1), (-1,-1), 'RIGHT') 
        ts_trades.add('FONTSIZE', (0,0), (-1,-1), 8)    
        t_trades.setStyle(ts_trades)
        elements.append(t_trades)
    else:
        elements.append(Paragraph("No trades executed this year.", normal_style))
    
    # PAGE: CORPORATE ACTIONS
    if data['corp_actions']:
        elements.append(PageBreak())
        elements.append(Paragraph(f"Corporate Actions & Splits ({year})", h2_style))
        corp_header = [["Date", "Ticker", "Type", "Details"]]
        corp_rows = []
        for act in data['corp_actions']:
            details = ""
            if act['type'] == 'SPLIT':
                ratio = act.get('ratio', 1)
                details = f"Split Ratio: {ratio:.4f}"
            elif act['type'] == 'BUY' and act.get('source') == 'IBKR_CORP_ACTION':
                 details = f"Stock Div: +{act['qty']:.4f} shares"
            elif act['type'] == 'TRANSFER' and act.get('source') == 'IBKR_CORP_ACTION':
                 details = f"Adjustment: {act['qty']:.4f}"
            else:
                 details = "Other Adjustment"
            corp_rows.append([act['date'], act['ticker'], act['type'], details])
        full_corp_data = corp_header + corp_rows
        t_corp = Table(full_corp_data, colWidths=[100, 80, 80, 200], repeatRows=1)
        t_corp.setStyle(get_zebra_style(len(full_corp_data)))
        elements.append(t_corp)

    elements.append(PageBreak())

    # PAGE 4: MONTHLY DIVIDENDS SUMMARY
    elements.append(Paragraph(f"Monthly Dividends Summary ({year})", h2_style))
    month_names = { "01": "January", "02": "February", "03": "March", "04": "April", "05": "May", "06": "June", "07": "July", "08": "August", "09": "September", "10": "October", "11": "November", "12": "December" }
    
    if data['monthly_dividends']:
        m_data = [["Month", "Gross (PLN)", "Tax Paid (PLN)", "Net (PLN)"]]
        sorted_months = sorted(data['monthly_dividends'].keys())
        total_gross, total_tax = 0, 0
        for m in sorted_months:
            vals = data['monthly_dividends'][m]
            m_data.append([
                month_names.get(m, m),
                f"{vals['gross_pln']:,.2f}",
                f"{vals['tax_pln']:,.2f}",
                f"{vals['net_pln']:,.2f}"
            ])
            total_gross += vals['gross_pln']
            total_tax += vals['tax_pln']
        m_data.append(["TOTAL", f"{total_gross:,.2f}", f"{total_tax:,.2f}", f"{total_gross - total_tax:,.2f}"])
        t_months = Table(m_data, colWidths=[110, 110, 110, 110], repeatRows=1)
        ts = get_zebra_style(len(m_data))
        ts.add('FONT-WEIGHT', (0,-1), (-1,-1), 'BOLD')
        ts.add('BACKGROUND', (0,-1), (-1,-1), colors.lightgrey)
        ts.add('ALIGN', (1,1), (-1,-1), 'RIGHT')
        t_months.setStyle(ts)
        elements.append(t_months)
        
        # --- DETAILED DIVIDENDS ---
        elements.append(PageBreak()) 
        elements.append(Paragraph(f"Dividend Details (Chronological)", h2_style))
        elements.append(Paragraph("Detailed breakdown of every dividend payment received.", normal_style))
        elements.append(Spacer(1, 10))
        
        sorted_divs = sorted(data['dividends'], key=lambda x: x['date'])
        
        is_first_month = True
        for month_key, group in itertools.groupby(sorted_divs, key=lambda x: x['date'][:7]):
            if not is_first_month:
                elements.append(PageBreak())
            is_first_month = False
            
            y, m = month_key.split('-')
            m_name = month_names.get(m, m)
            elements.append(Paragraph(f"{m_name} {y}", h2_style))
            
            det_header = [["Date", "Ticker", "Gross", "Rate", "Gross PLN", "Tax PLN"]]
            det_rows = []
            for d in group:
                det_rows.append([
                    d['date'],
                    d['ticker'],
                    f"{d['amount']:.2f} {d['currency']}",
                    f"{d['rate']:.4f}",
                    f"{d['amount_pln']:.2f}",
                    f"{d['tax_paid_pln']:.2f}"
                ])
            full_det_data = det_header + det_rows
            t_det = Table(full_det_data, colWidths=[70, 50, 90, 50, 70, 70], repeatRows=1)
            ts_det = get_zebra_style(len(full_det_data))
            ts_det.add('ALIGN', (2,1), (-1,-1), 'RIGHT')
            ts_det.add('FONTSIZE', (0,0), (-1,-1), 8)
            t_det.setStyle(ts_det)
            elements.append(t_det)
        
    else:
        elements.append(Paragraph("No dividends received this year.", normal_style))
    
    elements.append(PageBreak())

    # PAGE: YEARLY SUMMARY
    elements.append(Paragraph(f"Yearly Summary", h2_style))
    div_gross = sum(x['amount_pln'] for x in data['dividends'])
    div_tax = sum(x['tax_paid_pln'] for x in data['dividends'])
    polish_tax_due = max(0, (div_gross * 0.19) - div_tax)
    final_net = div_gross - div_tax - polish_tax_due
    
    summary_data = [
        ["Metric", "Amount (PLN)"],
        ["Total Dividends", f"{div_gross:,.2f}"],
        ["Withheld Tax (sum)", f"-{div_tax:,.2f}"],
        ["Additional Tax (PL, ~diff)", f"{polish_tax_due:,.2f}"],
        ["Final Net (after full 19%)", f"{final_net:,.2f}"]
    ]
    t_summary = Table(summary_data, colWidths=[250, 150])
    ts_sum = get_zebra_style(len(summary_data))
    ts_sum.add('ALIGN', (1,1), (-1,-1), 'RIGHT')
    t_summary.setStyle(ts_sum)
    elements.append(t_summary)
    
    elements.append(Spacer(1, 20))
    elements.append(Paragraph("Diagnostics", h2_style))
    diag = data['diagnostics']
    diag_data = [
        ["Indicator", "Value"],
        ["Tickers (unique)", str(diag['tickers_count'])],
        ["Dividend rows", str(diag['div_rows_count'])],
        ["Tax rows", str(diag['tax_rows_count'])]
    ]
    t_diag = Table(diag_data, colWidths=[250, 150])
    ts_diag = get_zebra_style(len(diag_data))
    ts_diag.add('ALIGN', (1,1), (-1,-1), 'CENTER')
    t_diag.setStyle(ts_diag)
    elements.append(t_diag)
    
    elements.append(Spacer(1, 20))
    elements.append(Paragraph("Per-currency totals (PLN)", h2_style))
    curr_data = [["Currency", "PLN total"]]
    for curr, val in data['per_currency'].items():
        curr_data.append([curr, f"{val:,.2f}"])
    t_curr = Table(curr_data, colWidths=[250, 150], repeatRows=1)
    ts_curr = get_zebra_style(len(curr_data))
    ts_curr.add('ALIGN', (1,1), (-1,-1), 'RIGHT')
    t_curr.setStyle(ts_curr)
    elements.append(t_curr)
    elements.append(PageBreak())

    # PAGE: PIT-38
    elements.append(Paragraph(f"PIT-38 Helper Data ({year})", h2_style))
    elements.append(Spacer(1, 10))
    elements.append(Paragraph("Section C (Stocks/Derivatives)", h3_style))
    cap_rev = sum(x['revenue_pln'] for x in data['capital_gains'])
    cap_cost = sum(x['cost_pln'] for x in data['capital_gains'])
    pit_c_data = [
        ["Field in PIT-38", "Value (PLN)"],
        ["Przychód (Revenue) [Pos 20]", f"{cap_rev:,.2f}"],
        ["Koszty (Costs) [Pos 21]", f"{cap_cost:,.2f}"],
        ["Dochód/Strata", f"{cap_rev - cap_cost:,.2f}"]
    ]
    t_pit_c = Table(pit_c_data, colWidths=[250, 150])
    ts_pit = get_zebra_style(len(pit_c_data))
    ts_pit.add('ALIGN', (1,1), (-1,-1), 'RIGHT')
    t_pit_c.setStyle(ts_pit)
    elements.append(t_pit_c)
    elements.append(Spacer(1, 5))
    elements.append(Paragraph("<i>* Note: 'Koszty' includes purchase price + buy/sell commissions.</i>", styles['Italic']))
    elements.append(Spacer(1, 20))

    elements.append(Paragraph("Dividends (Foreign Tax)", h3_style))
    pit_div_data = [
        ["Description", "Value (PLN)"],
        ["Gross Income", f"{div_gross:,.2f}"],
        ["Tax Paid Abroad (Max deductible)", f"{div_tax:,.2f}"],
        ["TO PAY (Difference) [Pos 45]", f"{polish_tax_due:,.2f}"] 
    ]
    t_pit_div = Table(pit_div_data, colWidths=[250, 150])
    ts_pit_div = get_zebra_style(len(pit_div_data))
    ts_pit_div.add('ALIGN', (1,1), (-1,-1), 'RIGHT')
    ts_pit_div.add('FONTNAME', (0,-1), (-1,-1), 'Helvetica-Bold') 
    t_pit_div.setStyle(ts_pit_div)
    elements.append(t_pit_div)

    doc.build(elements, onFirstPage=add_footer, onLaterPages=add_footer)
```

# --- FILE: src/excel_exporter.py ---
```python
# src/excel_exporter.py

import pandas as pd
from typing import Dict, Any

def export_to_excel(sheets_data: Dict[str, pd.DataFrame], 
                    file_path: str, 
                    summary_data: Dict[str, Any], 
                    ticker_summary: Dict[str, Dict[str, str]]):
    """
    Exports data to a formatted Excel file with multiple tabs.

    Args:
        sheets_data: Dictionary where Key = Sheet Name, Value = DataFrame.
        file_path: The full path to save the .xlsx file.
        summary_data: Dictionary containing project summary metrics.
        ticker_summary: Dictionary containing P&L breakdown aggregated by ticker.
    """
    
    try:
        writer = pd.ExcelWriter(file_path, engine='openpyxl')

        # 1. Write General Summary Sheet (First Tab)
        summary_df = pd.DataFrame(list(summary_data.items()), columns=['Metric', 'Value'])
        summary_df.to_excel(writer, sheet_name='Summary', index=False)

        # 2. Write Ticker Summary Sheet
        if ticker_summary:
            df_ticker_summary = pd.DataFrame.from_dict(ticker_summary, orient='index').reset_index()
            df_ticker_summary = df_ticker_summary.rename(columns={'index': 'Ticker'})
            
            if not df_ticker_summary.empty:
                cols = ['Ticker', 'Total_P&L_PLN', 'Total_Proceeds_PLN', 'Total_Cost_PLN']
                df_ticker_summary = df_ticker_summary.reindex(columns=cols)
            
            df_ticker_summary.to_excel(writer, sheet_name='Ticker Summary', index=False)

        # 3. Write Separate Data Sheets (Sales, Dividends, Inventory)
        for sheet_name, df in sheets_data.items():
            if not df.empty:
                df.to_excel(writer, sheet_name=sheet_name, index=False)
                print(f"INFO: Added sheet '{sheet_name}' with {len(df)} rows.")
            else:
                print(f"INFO: Skipping empty sheet '{sheet_name}'.")

        writer.close()
        print(f"SUCCESS: Data exported to Excel at {file_path}")

    except Exception as e:
        print(f"ERROR: Failed to export Excel file at {file_path}. Reason: {e}")
```

# --- FILE: src/data_collector.py ---
```python
# src/data_collector.py

import pandas as pd
from typing import List, Dict, Any, Tuple
from datetime import datetime

# Helper to categorize holding period
def calculate_holding_period(buy_date_str: str, sell_date_str: str) -> str:
    try:
        if not buy_date_str or not sell_date_str:
            return "N/A"
        fmt = '%Y-%m-%d'
        d1 = datetime.strptime(buy_date_str, fmt).date()
        d2 = datetime.strptime(sell_date_str, fmt).date()
        days = (d2 - d1).days + 1
        return "Long-Term" if days > 365 else "Short-Term"
    except Exception:
        return "Error"

def calculate_days(buy_date_str: str, sell_date_str: str) -> int:
    try:
        fmt = '%Y-%m-%d'
        d1 = datetime.strptime(buy_date_str, fmt).date()
        d2 = datetime.strptime(sell_date_str, fmt).date()
        return (d2 - d1).days + 1
    except:
        return 0

def calculate_ticker_summary(flat_gains: List[Dict[str, Any]]) -> Dict[str, Dict[str, str]]:
    summary = {}
    for record in flat_gains:
        ticker = record.get('Ticker')
        pl_pln = record.get('P&L_PLN', 0.0)
        proceeds = record.get('Proceeds_PLN', 0.0)
        cost = record.get('Cost_PLN', 0.0)
        
        if ticker not in summary:
            summary[ticker] = {'Total_P&L_PLN': 0.0, 'Total_Proceeds_PLN': 0.0, 'Total_Cost_PLN': 0.0}
        
        summary[ticker]['Total_P&L_PLN'] += pl_pln
        summary[ticker]['Total_Proceeds_PLN'] += proceeds
        summary[ticker]['Total_Cost_PLN'] += cost

    formatted = {}
    for t, m in summary.items():
        formatted[t] = {k: f"{v:.2f}" for k, v in m.items()}
    return formatted

def collect_all_trade_data(realized_gains: List[Dict[str, Any]], 
                           dividends: List[Dict[str, Any]], 
                           inventory: List[Dict[str, Any]]) -> Tuple[Dict[str, pd.DataFrame], Dict[str, Dict[str, str]]]:
    
    # --- 1. Sales P&L ---
    flat_records = []
    for sale in realized_gains:
        matched_buys = sale.get('matched_buys', [])
        sale_date = sale.get('date_sell') or sale.get('sale_date')
        ticker = sale.get('ticker')
        sale_price = float(sale.get('sale_price', 0))
        sale_rate = float(sale.get('sale_rate', 1.0))
        
        if not matched_buys:
            flat_records.append({
                'Date': sale_date,
                'Ticker': ticker,
                'TransactionType': 'Sale_P&L',
                'Buy_Date': 'MISSING',
                'Quantity': float(sale.get('quantity', 0)),
                'Proceeds_PLN': float(sale.get('sale_amount', 0)),
                'Cost_PLN': 0.0,
                'P&L_PLN': float(sale.get('profit_loss', 0)),
                'Holding_Days': 0,
                'Holding_Category': 'N/A'
            })
            continue

        for buy in matched_buys:
            buy_date = buy.get('date')
            qty = float(buy.get('qty', 0))
            cost_pln = float(buy.get('cost_pln', 0))
            lot_proceeds = qty * sale_price * sale_rate
            lot_pnl = lot_proceeds - cost_pln
            
            flat_records.append({
                'Date': sale_date,
                'Ticker': ticker,
                'TransactionType': 'Sale_P&L',
                'Buy_Date': buy_date,
                'Quantity': qty,
                'Proceeds_PLN': lot_proceeds,
                'Cost_PLN': cost_pln,
                'P&L_PLN': lot_pnl,
                'Holding_Days': calculate_days(buy_date, sale_date),
                'Holding_Category': calculate_holding_period(buy_date, sale_date)
            })

    df_realized = pd.DataFrame(flat_records)
    if not df_realized.empty:
        df_realized['Date'] = pd.to_datetime(df_realized['Date'], errors='coerce')
        df_realized = df_realized.sort_values(by=['Date', 'Ticker']).reset_index(drop=True)
        df_realized['Date'] = df_realized['Date'].dt.strftime('%Y-%m-%d')
        cols = ['Date', 'Ticker', 'TransactionType', 'Buy_Date', 'Quantity', 'Proceeds_PLN', 'Cost_PLN', 'P&L_PLN', 'Holding_Days', 'Holding_Category']
        df_realized = df_realized[[c for c in cols if c in df_realized.columns]]

    # --- 2. Dividends (FIXED: Show Tax in Cost Column) ---
    div_records = []
    for d in dividends:
        gross = d.get('gross_amount_pln', 0.0)
        tax = d.get('tax_withheld_pln', 0.0)
        
        div_records.append({
            'Date': d.get('ex_date'),
            'Ticker': d.get('ticker'),
            'TransactionType': 'Dividend',
            'Quantity': 0,
            'Gross_PLN': gross,
            'Tax_PLN': tax,
            # Mapping for Unified Excel View:
            'Proceeds_PLN': gross, # Выручка = Грязный дивиденд
            'Cost_PLN': tax,       # Затраты = Уплаченный налог
            'P&L_PLN': gross - tax, # Прибыль = Чистый дивиденд
            'Currency': d.get('currency'),
            'Rate': d.get('rate')
        })
    df_dividends = pd.DataFrame(div_records)
    if not df_dividends.empty:
        df_dividends['Date'] = pd.to_datetime(df_dividends['Date'], errors='coerce')
        df_dividends = df_dividends.sort_values(by=['Date', 'Ticker']).reset_index(drop=True)
        df_dividends['Date'] = df_dividends['Date'].dt.strftime('%Y-%m-%d')

    # --- 3. Inventory ---
    inv_records = []
    today_str = datetime.today().strftime('%Y-%m-%d')
    for i in inventory:
        buy_date = i.get('buy_date')
        inv_records.append({
            'Buy_Date': buy_date,
            'Ticker': i.get('ticker'),
            'TransactionType': 'Inventory',
            'Quantity': i.get('quantity'),
            'Cost_per_Share': i.get('cost_per_share'),
            'Total_Cost_PLN': i.get('total_cost'),
            'Holding_Days': calculate_days(buy_date, today_str)
        })
    df_inventory = pd.DataFrame(inv_records)
    if not df_inventory.empty:
        df_inventory = df_inventory.sort_values(by=['Ticker', 'Buy_Date'])

    sheets_collection = {
        'Sales P&L': df_realized,
        'Dividends': df_dividends,
        'Open Positions': df_inventory
    }

    ticker_summary = calculate_ticker_summary(flat_records)
    return sheets_collection, ticker_summary
```

# --- FILE: src/utils.py ---
```python
# src/utils.py
from decimal import Decimal, ROUND_HALF_UP

def money(value) -> Decimal:
    """Rounds a Decimal or float to 2 decimal places (financial standard)."""
    if not isinstance(value, Decimal):
        value = Decimal(str(value))
    return value.quantize(Decimal("0.01"), rounding=ROUND_HALF_UP)
```

# --- FILE: src/__init__.py ---
```python
```
