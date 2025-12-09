import sqlite3
import os
from decimal import Decimal

DB_DIR = "db"
DB_FILE = "ibkr_history.db"
DB_PATH = os.path.join(DB_DIR, DB_FILE)

_DB_PASSWORD = None

def set_db_password(password):
    global _DB_PASSWORD
    _DB_PASSWORD = password

def get_connection():
    if not os.path.exists(DB_PATH):
         raise FileNotFoundError(f"Database file not found: {DB_PATH}. It might be encrypted or missing.")

    con = sqlite3.connect(DB_PATH)
    con.row_factory = sqlite3.Row
    return con

def init_db():
    con = get_connection()
    cur = con.cursor()
    
    # 1. ASSETS - FIXED: Outer quotes are now double (")
    cur.execute("CREATE TABLE IF NOT EXISTS assets (ticker TEXT PRIMARY KEY, currency TEXT, type TEXT DEFAULT 'STK', is_restricted BOOLEAN DEFAULT 0)")
    
    # 2. TRADES - FIXED: Outer quotes are now double (")
    cur.execute("CREATE TABLE IF NOT EXISTS trades (id INTEGER PRIMARY KEY AUTOINCREMENT, unique_hash TEXT UNIQUE, date TEXT, ticker TEXT, type TEXT, qty TEXT, price TEXT, commission TEXT, currency TEXT, source_file TEXT, created_at TIMESTAMP DEFAULT CURRENT_TIMESTAMP)")
    
    # 3. DIVIDENDS - FIXED: Outer quotes are now double (")
    cur.execute("CREATE TABLE IF NOT EXISTS dividends (id INTEGER PRIMARY KEY AUTOINCREMENT, unique_hash TEXT UNIQUE, date TEXT, ticker TEXT, amount TEXT, currency TEXT, tax_paid TEXT, source_file TEXT)")
    
    # 4. CORP ACTIONS - FIXED: Outer quotes are now double (")
    cur.execute("CREATE TABLE IF NOT EXISTS corp_actions (id INTEGER PRIMARY KEY AUTOINCREMENT, unique_hash TEXT UNIQUE, date TEXT, ticker TEXT, type TEXT, ratio TEXT, target_ticker TEXT, description TEXT)")

    # 5. TAXES - FIXED: Outer quotes are now double (")
    cur.execute("CREATE TABLE IF NOT EXISTS taxes (id INTEGER PRIMARY KEY AUTOINCREMENT, unique_hash TEXT UNIQUE, date TEXT, ticker TEXT, amount TEXT, currency TEXT, source_file TEXT)")
    
    con.commit()
    con.close()

def fetch_all_trades(cutoff_date=None):
    con = get_connection()
    cur = con.cursor()
    query = "SELECT * FROM trades"
    params = []
    if cutoff_date:
        query += " WHERE date <= ?"
        params.append(cutoff_date)
    query += " ORDER BY date ASC, created_at ASC"
    cur.execute(query, params)
    rows = cur.fetchall()
    trades = []
    for r in rows:
        trades.append({
            "date": r['date'],
            "ticker": r['ticker'],
            "type": r['type'],
            "qty": Decimal(r['qty']),
            "price": Decimal(r['price']),
            "commission": Decimal(r['commission']),
            "currency": r['currency'],
            "source": r['source_file']
        })
    con.close()
    return trades

def fetch_dividends(year=None):
    con = get_connection()
    cur = con.cursor()
    query = "SELECT * FROM dividends"
    params = []
    if year:
        query += " WHERE strftime('%Y', date) = ?"
        params.append(str(year))
    query += " ORDER BY date ASC"
    cur.execute(query, params)
    rows = cur.fetchall()
    divs = []
    for r in rows:
        divs.append({
            "date": r['date'],
            "ticker": r['ticker'],
            "amount": Decimal(r['amount']),
            "currency": r['currency'],
        })
    con.close()
    return divs

def fetch_taxes(year=None):
    con = get_connection()
    cur = con.cursor()
    query = "SELECT * FROM taxes"
    params = []
    if year:
        query += " WHERE strftime('%Y', date) = ?"
        params.append(str(year))
    cur.execute(query, params)
    rows = cur.fetchall()
    taxes = []
    for r in rows:
        taxes.append({
            "date": r['date'],
            "ticker": r['ticker'],
            "amount": Decimal(r['amount']),
            "currency": r['currency']
        })
    con.close()
    return taxes

def fetch_assets_metadata():
    con = get_connection()
    cur = con.cursor()
    cur.execute("SELECT * FROM assets")
    rows = cur.fetchall()
    meta = {}
    for r in rows:
        meta[r['ticker']] = {
            "currency": r['currency'],
            "is_restricted": bool(r['is_restricted'])
        }
    con.close()
    return meta

def fetch_available_years():
    con = get_connection()
    cur = con.cursor()
    cur.execute("SELECT DISTINCT strftime('%Y', date) AS year FROM trades ORDER BY year ASC")
    years = [row['year'] for row in cur.fetchall()]
    con.close()
    return years