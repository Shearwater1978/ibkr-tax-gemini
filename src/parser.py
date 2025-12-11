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