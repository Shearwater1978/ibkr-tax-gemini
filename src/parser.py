import csv
import re
from decimal import Decimal

IGNORED_TICKERS = {"EXAMPLE", "DUMMY_TICKER_FOR_EXAMPLE"}

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
    # Expanded list of non-taxable transfer keywords
    transfer_keywords = [
        "ACATS", "TRANSFER", "INTERNAL", "POSITION MOVEM", 
        "RECEIVE DELIVER", "INTER-COMPANY"  # <--- Added this based on your log
    ]
    
    if any(k in desc_upper for k in transfer_keywords):
        return "TRANSFER"
    
    if quantity > 0: return "BUY"
    if quantity < 0: return "SELL"
    return "UNKNOWN"

def parse_manual_history(filepath: str):
    manual_trades = []
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
                    "type": "BUY",
                    "source": "MANUAL",
                    "raw_desc": "Manual History"
                })
    except: pass
    return manual_trades

def parse_csv(filepath):
    data_out = {"dividends": [], "taxes": [], "trades": []}
    
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
                    data_out["trades"].append({
                        "ticker": ticker,
                        "currency": row[4],
                        "date": row[6].split(",")[0],
                        "qty": Decimal(row[7]),
                        "price": Decimal(row[8]),
                        "commission": Decimal(row[11]),
                        "type": classify_trade_type(row[5], Decimal(row[7])), # Will check for INTER-COMPANY
                        "source": "IBKR"
                    })
                except: pass

            # --- CORPORATE ACTIONS ---
            if row[0] == "Corporate Actions" and row[1] == "Data" and row[2] == "Stocks":
                try:
                    desc = row[6]
                    if "Total" in desc: continue
                    ticker = extract_ticker(desc)
                    if ticker in IGNORED_TICKERS: continue
                    date_raw = row[4].split(",")[0]
                    curr = row[3]

                    if "Stock Dividend" in desc or "Spin-off" in desc:
                        data_out["trades"].append({
                            "ticker": ticker,
                            "currency": curr,
                            "date": date_raw,
                            "qty": Decimal(row[7]),
                            "price": Decimal("0.0"),
                            "commission": Decimal("0.0"),
                            "type": "BUY",
                            "source": "IBKR_CORP_ACTION"
                        })

                    if "Split" in desc:
                        match = re.search(r'Split (\d+) for (\d+)', desc, re.IGNORECASE)
                        if match:
                            numerator = Decimal(match.group(1))
                            denominator = Decimal(match.group(2))
                            if denominator != 0:
                                ratio = numerator / denominator
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
                except: pass
                    
    return data_out
