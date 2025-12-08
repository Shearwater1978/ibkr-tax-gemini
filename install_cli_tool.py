import os
import argparse
import sys 
import logging

logging.basicConfig(level=logging.INFO, format='%(levelname)s: %(message)s')

# --- 1. CONTENT DEFINITIONS (Cleaned) ---

MAIN_CONTENT = """import os
import json
import logging
from decimal import Decimal
from src.processing import TaxCalculator
from src.report_pdf import generate_pdf

logging.basicConfig(level=logging.INFO, format='%(levelname)s: %(message)s')

class DecimalEncoder(json.JSONEncoder):
    def default(self, o):
        if isinstance(o, Decimal): return float(o)
        return super(DecimalEncoder, self).default(o)

def generate_report_command(year):
    OUTPUT_DIR = "output"
    os.makedirs(OUTPUT_DIR, exist_ok=True)
    
    logging.info(f"🚀 Processing Report for {year} (Source: Decrypted DB)")
    
    try:
        calc = TaxCalculator(target_year=year)
        calc.run_calculations()
        
        final_report = calc.get_results()
        
        data = final_report['data']
        has_data = (data['dividends'] or 
                    data['capital_gains'] or 
                    data['holdings'] or
                    data['trades_history'])
        
        if not has_data:
            logging.info(f"--> Year {year} empty. Skipping report generation.")
            return

        json_path = os.path.join(OUTPUT_DIR, f"tax_report_{year}.json")
        with open(json_path, "w", encoding="utf-8") as f:
            json.dump(final_report, f, indent=4, cls=DecimalEncoder)
        
        pdf_path = os.path.join(OUTPUT_DIR, f"tax_report_{year}.pdf")
        generate_pdf(final_report, pdf_path)
        logging.info(f"✅ Report ready: {pdf_path}")
        
    except Exception as e:
         logging.error(f"Failed processing {year}: {e}")

if __name__ == "__main__":
    print("Use 'python tax_cli.py report <year>'")
"""

INGEST_CONTENT = """import os
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
    print("\\n🏁 Ingestion Complete.")

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
"""

DB_MANAGER_CONTENT = """import sqlite3
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
    
    cur.execute('CREATE TABLE IF NOT EXISTS assets (ticker TEXT PRIMARY KEY, currency TEXT, type TEXT DEFAULT \'STK\', is_restricted BOOLEAN DEFAULT 0)')
    cur.execute('CREATE TABLE IF NOT EXISTS trades (id INTEGER PRIMARY KEY AUTOINCREMENT, unique_hash TEXT UNIQUE, date TEXT, ticker TEXT, type TEXT, qty TEXT, price TEXT, commission TEXT, currency TEXT, source_file TEXT, created_at TIMESTAMP DEFAULT CURRENT_TIMESTAMP)')
    cur.execute('CREATE TABLE IF NOT EXISTS dividends (id INTEGER PRIMARY KEY AUTOINCREMENT, unique_hash TEXT UNIQUE, date TEXT, ticker TEXT, amount TEXT, currency TEXT, tax_paid TEXT, source_file TEXT)')
    cur.execute('CREATE TABLE IF NOT EXISTS corp_actions (id INTEGER PRIMARY KEY AUTOINCREMENT, unique_hash TEXT UNIQUE, date TEXT, ticker TEXT, type TEXT, ratio TEXT, target_ticker TEXT, description TEXT)')
    cur.execute('CREATE TABLE IF NOT EXISTS taxes (id INTEGER PRIMARY KEY AUTOINCREMENT, unique_hash TEXT UNIQUE, date TEXT, ticker TEXT, amount TEXT, currency TEXT, source_file TEXT)')
    
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
"""

# 4. CLI CONTENT (FIXED DOCSTRING)
CLI_CONTENT = """import argparse
import getpass
import logging
import sys
import os
from src.db_manager import set_db_password, get_connection, fetch_available_years
from src.lock_unlock import unlock_db, lock_db
from src.ingest import ingest_command
from main import generate_report_command

logging.basicConfig(level=logging.INFO, format='%(levelname)s: %(message)s')

def initialize_security(args):
    # Handles password input, unlocking the DB, and setting the global password.
    print("🔒 Iron Bank CLI Security")
    
    password = getpass.getpass("Enter Database Password: ")
    set_db_password(password)
    
    # Attempt decryption (unlocking)
    if not unlock_db(password):
        print("Exiting: Decryption failed. Check your password or run initial lock.")
        sys.exit(1)
        
    # Check if the plaintext DB is accessible (first run check)
    try:
        con = get_connection()
        con.close()
    except FileNotFoundError:
        print("🚨 Error: Database file not found. Run 'python src/lock_unlock.py' first.")
        sys.exit(1)
    except Exception as e:
        print(f"Database access failed after unlock: {e}")
        sys.exit(1)
        
    return password

def run_report_command(args):
    # Executes the report generation for the specified year.
    if not args.year:
        print("❌ Error: Report year must be specified (e.g., report 2024).")
        
        available_years = fetch_available_years()
        if available_years:
            print(f"   Available years in DB: {', '.join(available_years)}")
        
        sys.exit(1)
        
    # Validation
    available_years = fetch_available_years()
    if args.year not in available_years:
        print(f"⚠️ Warning: No trade data found for year {args.year}.")
        print(f"   Available years in DB: {', '.join(available_years) or 'None'}")
        
    generate_report_command(args.year)

def run_ingest_command(args):
    # Executes the data ingestion command.
    ingest_command()

def main():
    parser = argparse.ArgumentParser(
        description="IBKR Tax Assistant CLI (Iron Bank Edition).",
        formatter_class=argparse.RawTextHelpFormatter
    )

    # Global security initializer will run first
    password = initialize_security(None) 
    
    # Subcommands
    subparsers = parser.add_subparsers(title='Available Commands', dest='command')

    # 1. REPORT Command
    report_parser = subparsers.add_parser('report', help='Generate tax report for a specific year.')
    report_parser.add_argument('year', type=str, nargs='?', help='The target tax year (e.g., 2024).')
    report_parser.set_defaults(func=run_report_command)
    
    # 2. INGEST Command
    ingest_parser = subparsers.add_parser('ingest', help='Process and load new CSV files from the data/ folder.')
    ingest_parser.set_defaults(func=run_ingest_command)

    args = parser.parse_args()
    
    if 'func' in args:
        args.func(args)
    elif not args.command:
        parser.print_help()
    
    # Final step: Lock the database
    lock_db(password)
    logging.info("ALL DONE! Database locked.")

if __name__ == "__main__":
    main()
"""


# --- PROJECT UPDATE DICTIONARY (using clean variable references) ---

# --- CLEANUP (Delete old entry point) ---
if os.path.exists("run_ingestion.py"):
    try:
        os.remove("run_ingestion.py")
        print("🗑️ Removed obsolete file: run_ingestion.py")
    except Exception as e:
        print(f"⚠️ Could not remove run_ingestion.py: {e}")


PROJECT_UPDATE = {
    # File contents are now referenced by clean variables, ensuring the dictionary syntax is simple and correct.
    "src/db_manager.py": DB_MANAGER_CONTENT, 
    "src/ingest.py": INGEST_CONTENT, 
    "main.py": MAIN_CONTENT, 
    "tax_cli.py": CLI_CONTENT
}

def install_cli():
    print("🛠️  Installing Command Line Utility (tax_cli.py)...")
    
    for file_path, content in PROJECT_UPDATE.items():
        with open(file_path, "w", encoding="utf-8") as f:
            f.write(content)
        print(f"   Created/Updated: {file_path}")

    print("\n✅ CLI installed.")

if __name__ == "__main__":
    install_cli()