import csv
import re
import os
import glob
import argparse
from decimal import Decimal
from typing import List, Dict, Any

# Import the DBConnector
from src.db_connector import DBConnector 

IGNORED_TICKERS = {"EXAMPLE", "DUMMY_TICKER_FOR_EXAMPLE"}

# --- YOUR EXISTING PARSING LOGIC (UNCHANGED) ---

def extract_ticker(description: str) -> str:
    if not description: return "UNKNOWN"
    match = re.search(r'^([A-Za-z0-9\.]+)\(', description)
    if match: return match.group(1)
    
    parts = description.split()
    if parts:
        if parts[0].isupper() and len(parts[0]) < 6: return parts[0].split('(')[0]
    return "UNKNOWN"

def extract_target_ticker(description: str) -> str:
    match = re.search(r'\(([A-Za-z0-9\.]+),\s+[A-Za-z0-9]', description)
    if match:
        return match.group(1)
    return None

def classify_trade_type(description: str, quantity: Decimal) -> str:
    desc_upper = description.upper()
    transfer_keywords = [
        "ACATS", "TRANSFER", "INTERNAL", "POSITION MOVEM", 
        "RECEIVE DELIVER", "INTER-COMPANY"
    ]
    if any(k in desc_upper for k in transfer_keywords):
        return "TRANSFER"
    if quantity > 0: return "BUY"
    if quantity < 0: return "SELL"
    return "UNKNOWN"

def parse_manual_history(filepath: str):
    manual_trades = []
    if not os.path.exists(filepath):
        return manual_trades
        
    try:
        with open(filepath, 'r', encoding='utf-8') as f:
            reader = csv.DictReader(f)
            for row in reader:
                ticker = row.get('Ticker', '').strip().upper()
                if not ticker or ticker in IGNORED_TICKERS: continue
                manual_trades.append({
                    "ticker": ticker,
                    "currency": row['Currency'].strip().upper(),
                    "date": row['Date'].strip(),
                    "qty": Decimal(row['Quantity']),
                    "price": Decimal(row['Price']),
                    "commission": Decimal(row.get('Commission', 0)),
                    "type": "BUY", # Usually manual history are initial buys
                    "source": "MANUAL",
                    "raw_desc": "Manual History"
                })
    except Exception as e: 
        print(f"WARNING: Error parsing manual history: {e}")
    return manual_trades

def parse_csv(filepath):
    data_out = {"dividends": [], "taxes": [], "trades": []}
    seen_actions = set() # DEDUP: Prevent double splits from same file
    
    with open(filepath, 'r', encoding='utf-8-sig') as f:
        reader = csv.reader(f)
        for row in reader:
            if not row: continue
            
            # --- DIVIDENDS ---
            if row[0] == "Dividends" and row[1] == "Data":
                try:
                    if "Total" in row[2] or "Total" in row[4]: continue
                    ticker = extract_ticker(row[4])
                    if ticker in IGNORED_TICKERS: continue
                    data_out["dividends"].append({
                        "ticker": ticker,
                        "currency": row[2],
                        "date": row[3],
                        "amount": Decimal(row[5])
                    })
                except: pass

            # --- TAXES ---
            if row[0] == "Withholding Tax" and row[1] == "Data":
                try:
                    if "Total" in row[4]: continue
                    ticker = extract_ticker(row[4])
                    if ticker in IGNORED_TICKERS: continue
                    data_out["taxes"].append({
                        "ticker": ticker,
                        "currency": row[2],
                        "date": row[3],
                        "amount": Decimal(row[5])
                    })
                except: pass

            # --- TRADES ---
            if row[0] == "Trades" and row[1] == "Data" and row[2] == "Order" and row[3] == "Stocks":
                try:
                    ticker = row[5]
                    if ticker in IGNORED_TICKERS: continue
                    
                    # Clean currency
                    curr = row[4]
                    
                    data_out["trades"].append({
                        "ticker": ticker,
                        "currency": curr,
                        "date": row[6].split(",")[0],
                        "qty": Decimal(row[7]),
                        "price": Decimal(row[8]),
                        "commission": Decimal(row[11]),
                        "type": classify_trade_type(row[5], Decimal(row[7])),
                        "source": "IBKR"
                    })
                except: pass

            # --- CORPORATE ACTIONS ---
            if row[0] == "Corporate Actions" and row[1] == "Data" and row[2] == "Stocks":
                try:
                    desc = row[6]
                    if "Total" in desc: continue
                    
                    # DATE FIX: Revert GE logic (use row date), Keep KVUE fix
                    date_raw = row[4].split(",")[0]
                    curr = row[3]
                    
                    # 1. SPLITS
                    if "Split" in desc:
                        ticker = extract_ticker(desc)
                        if ticker in IGNORED_TICKERS: continue
                        match = re.search(r'Split (\d+) for (\d+)', desc, re.IGNORECASE)
                        if match:
                            numerator = Decimal(match.group(1))
                            denominator = Decimal(match.group(2))
                            if denominator != 0:
                                ratio = numerator / denominator
                                
                                # DEDUP CHECK
                                action_sig = (date_raw, ticker, "SPLIT", ratio)
                                if action_sig in seen_actions: continue
                                seen_actions.add(action_sig)
                                
                                data_out["trades"].append({
                                    "ticker": ticker,
                                    "currency": curr,
                                    "date": date_raw,
                                    "qty": Decimal("0"),
                                    "price": Decimal("0"),
                                    "commission": Decimal("0"),
                                    "type": "SPLIT",
                                    "ratio": ratio,
                                    "source": "IBKR_SPLIT"
                                })
                        continue 

                    # 2. COMPLEX ACTIONS
                    is_spinoff = "Spin-off" in desc or "Spinoff" in desc
                    is_merger = "Merged" in desc or "Acquisition" in desc
                    is_stock_div = "Stock Dividend" in desc
                    is_tender = "Tendered" in desc
                    is_voluntary = "Voluntary Offer" in desc
                    
                    if is_stock_div or is_spinoff or is_merger or is_tender or is_voluntary:
                        target_ticker = None
                        
                        # Explicit Fixes
                        if is_voluntary and "(KVUE," in desc:
                             target_ticker = "KVUE"
                             date_raw = "2023-08-23" # KVUE Force Date
                        elif is_spinoff and "(WBD," in desc: target_ticker = "WBD"
                        elif is_spinoff and "(OGN," in desc: target_ticker = "OGN"
                        elif is_spinoff and "(FG," in desc: target_ticker = "FG"
                        
                        if not target_ticker:
                            if is_spinoff or is_merger or is_tender or is_voluntary:
                                target_ticker = extract_target_ticker(desc)
                        if not target_ticker:
                            target_ticker = extract_ticker(desc)
                            
                        if target_ticker and target_ticker not in IGNORED_TICKERS:
                            qty = Decimal(row[7])
                            
                            # DEDUP CHECK
                            action_sig = (date_raw, target_ticker, "TRANSFER", qty)
                            if action_sig in seen_actions: continue
                            seen_actions.add(action_sig)

                            data_out["trades"].append({
                                "ticker": target_ticker,
                                "currency": curr,
                                "date": date_raw,
                                "qty": qty,
                                "price": Decimal("0.0"),
                                "commission": Decimal("0.0"),
                                "type": "TRANSFER",
                                "source": "IBKR_CORP_ACTION"
                            })
                except: pass
                    
    return data_out

# --- NEW: DATABASE WRITING LOGIC ---

def save_to_database(all_data: Dict[str, List[Dict]]):
    """
    Takes the parsed dictionary (trades, dividends, taxes) and inserts it into SQLCipher.
    """
    
    # Flatten the dictionary into a list of tuples for the DB schema
    # DB Schema: TradeId (Auto), Date, EventType, Ticker, Quantity, Price, Currency, Amount, Fee, Description
    
    db_records = []
    
    # 1. Process TRADES (Buys, Sells, Splits, Transfers)
    for t in all_data.get('trades', []):
        # Calculate Amount (Approximate for now, normally Qty * Price)
        qty = float(t.get('qty', 0))
        price = float(t.get('price', 0))
        fee = float(t.get('commission', 0))
        amount = qty * price
        
        # Special handling for SPLIT ratio storage if needed, usually stored in Quantity or Description
        # For this schema, we stick to standard fields.
        
        record = (
            t['date'],
            t['type'], # EventType
            t['ticker'],
            qty,
            price,
            t['currency'],
            amount,
            fee,
            t.get('source', 'IBKR') # Description
        )
        db_records.append(record)

    # 2. Process DIVIDENDS
    for d in all_data.get('dividends', []):
        amount = float(d['amount'])
        record = (
            d['date'],
            'DIVIDEND',
            d['ticker'],
            0.0, # Qty
            0.0, # Price
            d['currency'],
            amount,
            0.0, # Fee
            'Dividend Payout'
        )
        db_records.append(record)
        
    # 3. Process TAXES
    for x in all_data.get('taxes', []):
        amount = float(x['amount']) # Usually negative in reports
        record = (
            x['date'],
            'TAX',
            x['ticker'],
            0.0,
            0.0,
            x['currency'],
            amount,
            0.0,
            'Withholding Tax'
        )
        db_records.append(record)

    if not db_records:
        print("INFO: No records found to insert.")
        return

    # INSERT into DB
    try:
        with DBConnector() as db:
            db.initialize_schema()
            
            # We use executemany for performance
            query = """
            INSERT INTO transactions 
            (Date, EventType, Ticker, Quantity, Price, Currency, Amount, Fee, Description) 
            VALUES (?, ?, ?, ?, ?, ?, ?, ?, ?)
            """
            
            db.conn.executemany(query, db_records)
            db.conn.commit() # <--- CRITICAL: SAVES DATA
            
            print(f"SUCCESS: Imported {len(db_records)} records into SQLCipher.")
            
    except Exception as e:
        print(f"FATAL ERROR writing to DB: {e}")


# --- MAIN EXECUTION ---

if __name__ == "__main__":
    parser = argparse.ArgumentParser(description="IBKR Report Parser & Importer")
    parser.add_argument('--files', type=str, required=True, help="Folder with CSVs or single file")
    parser.add_argument('--manual', type=str, default="data/manual_history.csv", help="Path to manual history CSV")
    
    args = parser.parse_args()
    
    # 1. Collect CSV files
    if os.path.isdir(args.files):
        file_paths = glob.glob(os.path.join(args.files, "*.csv"))
    else:
        file_paths = glob.glob(args.files)
        
    # 2. Parse Everything
    combined_data = {"trades": [], "dividends": [], "taxes": []}
    
    # A. Parse Broker Reports
    for fp in file_paths:
        print(f"Parsing: {fp}")
        file_data = parse_csv(fp)
        combined_data["trades"].extend(file_data["trades"])
        combined_data["dividends"].extend(file_data["dividends"])
        combined_data["taxes"].extend(file_data["taxes"])

    # B. Parse Manual History
    if args.manual and os.path.exists(args.manual):
        print(f"Parsing Manual History: {args.manual}")
        manual_recs = parse_manual_history(args.manual)
        combined_data["trades"].extend(manual_recs)
        
    # 3. Save to DB
    print("Writing to database...")
    save_to_database(combined_data)