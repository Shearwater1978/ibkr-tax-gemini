import os
import sqlite3 
from decimal import Decimal
from .parser import parse_csv, parse_manual_history
from .db_manager import get_connection, init_db
from .utils_db import generate_trade_hash, generate_div_hash

DATA_DIR = "data"
MANUAL_FILE = "manual_history.csv"

def ingest_command():
    init_db()
    con = get_connection()
    
    print("🚀 Starting Data Ingestion (APSW Engine)...")
    
    if os.path.exists(MANUAL_FILE):
        print(f"📄 Processing Manual History: {MANUAL_FILE}")
        trades = parse_manual_history(MANUAL_FILE)
        con.execute("BEGIN TRANSACTION")
        insert_trades(con, trades, "MANUAL_ENTRY")
        con.execute("COMMIT")

    if os.path.exists(DATA_DIR):
        files = [f for f in os.listdir(DATA_DIR) if f.lower().endswith(".csv")]
        print(f"📂 Found {len(files)} CSV files.")
        
        for filename in files:
            file_path = os.path.join(DATA_DIR, filename)
            print(f"   Processing: {filename} ...", end=" ")
            try:
                data = parse_csv(file_path)
                
                con.execute("BEGIN TRANSACTION")
                added_t = insert_trades(con, data['trades'], filename)
                added_d = insert_dividends(con, data['dividends'], filename)
                added_tx = insert_taxes(con, data['taxes'], filename)
                con.execute("COMMIT")
                
                print(f"✅ OK (+{added_t} trades, +{added_d} divs, +{added_tx} taxes)")
                
            except Exception as e:
                print(f"❌ ERROR: {e}")
                try: con.execute("ROLLBACK")
                except: pass

    con.close()
    print("\n🏁 Ingestion Complete.")

def insert_trades(con, trades, source):
    cur = con.cursor()
    count = 0
    for t in trades:
        h = generate_trade_hash(t['date'], t['ticker'], t['type'], t['qty'], t['price'])
        is_restricted = 1 if t.get('currency') == 'RUB' else 0
        
        cur.execute('''
            INSERT INTO assets (ticker, currency, type, is_restricted)
            VALUES (?, ?, ?, ?)
            ON CONFLICT(ticker) DO UPDATE SET
                currency=excluded.currency,
                is_restricted=max(is_restricted, excluded.is_restricted)
        ''', (t['ticker'], t['currency'], 'STK', is_restricted))

        cur.execute('''
            INSERT OR IGNORE INTO trades 
            (unique_hash, date, ticker, type, qty, price, commission, currency, source_file)
            VALUES (?, ?, ?, ?, ?, ?, ?, ?, ?)
        ''', (h, t['date'], t['ticker'], t['type'], str(t['qty']), str(t['price']), str(t['commission']), t['currency'], source))
        
        if con.changes() > 0: count += 1
    return count

def insert_dividends(con, dividends, source):
    cur = con.cursor()
    count = 0
    for d in dividends:
        h = generate_div_hash(d['date'], d['ticker'], d['amount'])
        cur.execute('''
            INSERT OR IGNORE INTO dividends
            (unique_hash, date, ticker, amount, currency, source_file)
            VALUES (?, ?, ?, ?, ?, ?)
        ''', (h, d['date'], d['ticker'], str(d['amount']), d['currency'], source))
        if con.changes() > 0: count += 1
    return count

def insert_taxes(con, taxes, source):
    cur = con.cursor()
    count = 0
    for t in taxes:
        h = generate_div_hash(t['date'], t['ticker'], t['amount'])
        cur.execute('''
            INSERT OR IGNORE INTO taxes
            (unique_hash, date, ticker, amount, currency, source_file)
            VALUES (?, ?, ?, ?, ?, ?)
        ''', (h, t['date'], t['ticker'], str(t['amount']), t['currency'], source))
        if con.changes() > 0: count += 1
    return count
