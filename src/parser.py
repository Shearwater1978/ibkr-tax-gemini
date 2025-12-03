import csv
import re
from decimal import Decimal

# List of tickers to ignore (templates/placeholders)
IGNORED_TICKERS = {"EXAMPLE", "DUMMY_TICKER_FOR_EXAMPLE", "YOUR_TICKER_HERE"}

def extract_ticker(description: str) -> str:
    if not description: return "UNKNOWN"
    match = re.search(r'^([A-Za-z0-9\.]+)\(', description)
    if match: return match.group(1)
    parts = description.split()
    if parts:
        if parts[0].isupper() and len(parts[0]) < 6: return parts[0].split('(')[0]
    return "UNKNOWN"

def classify_trade_type(description: str, quantity: Decimal) -> str:
    desc_upper = description.upper()
    transfer_keywords = ["ACATS", "TRANSFER", "INTERNAL", "POSITION MOVEM", "RECEIVE DELIVER"]
    if any(k in desc_upper for k in transfer_keywords): return "TRANSFER"
    if quantity > 0: return "BUY"
    if quantity < 0: return "SELL"
    return "UNKNOWN"

def parse_manual_history(filepath: str):
    manual_trades = []
    try:
        with open(filepath, 'r', encoding='utf-8') as f:
            reader = csv.DictReader(f)
            for row in reader:
                ticker_raw = row.get('Ticker', '').strip().upper()
                date_raw = row.get('Date', '').strip()
                
                # VALIDATION CHECKS
                if not ticker_raw or not date_raw: 
                    continue
                
                # IGNORE DUMMY DATA
                if ticker_raw in IGNORED_TICKERS:
                    continue

                trade = {
                    "ticker": ticker_raw,
                    "currency": row['Currency'].strip().upper(),
                    "date": date_raw,
                    "qty": Decimal(row['Quantity']),
                    "price": Decimal(row['Price']),
                    "commission": Decimal(row.get('Commission', 0)),
                    "type": "BUY",
                    "source": "MANUAL",
                    "raw_desc": "Manual History"
                }
                manual_trades.append(trade)
    except Exception as e:
        print(f"Warning reading manual history: {e}")
    return manual_trades

def parse_csv(filepath):
    data_out = {"dividends": [], "taxes": [], "trades": []}
    with open(filepath, 'r', encoding='utf-8-sig') as f:
        reader = csv.reader(f)
        for row in reader:
            if not row: continue
            
            # Dividends
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

            # Taxes
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

            # Trades
            if row[0] == "Trades" and row[1] == "Data" and row[2] == "Order" and row[3] == "Stocks":
                try:
                    curr, ticker = row[4], row[5]
                    if ticker in IGNORED_TICKERS: continue

                    date_raw = row[6].split(",")[0]
                    qty, price, comm = Decimal(row[7]), Decimal(row[8]), Decimal(row[11])
                    t_type = classify_trade_type(ticker, qty)
                    
                    data_out["trades"].append({
                        "ticker": ticker,
                        "currency": curr,
                        "date": date_raw,
                        "qty": qty,
                        "price": price,
                        "commission": comm,
                        "type": t_type,
                        "source": "IBKR"
                    })
                except: pass
    return data_out
