# src/parser.py

import csv
import re
import glob
import argparse
import os
from datetime import datetime
from decimal import Decimal
from typing import List, Dict, Optional
from src.db_connector import DBConnector

# --- CONFIGURATION ---
# Leave empty to parse everything. Deduplication will handle overlaps.
FILE_DATE_LIMITS = {} 
MANUAL_FIXES_FILE = "manual_fixes.csv"

def parse_decimal(value: str) -> Decimal:
    """Removes commas and quotes, parses number."""
    if not value: return Decimal(0)
    clean = value.replace(',', '').replace('"', '').strip()
    try: return Decimal(clean)
    except: return Decimal(0)

def normalize_date(date_str: str) -> Optional[str]:
    """Converts date to YYYY-MM-DD format."""
    if not date_str: return None
    clean = date_str.split(',')[0].strip().split(' ')[0]
    formats = ["%Y-%m-%d", "%Y%m%d", "%m/%d/%Y", "%d/%m/%Y", "%d-%b-%y"]
    for fmt in formats:
        try: return datetime.strptime(clean, fmt).strftime("%Y-%m-%d")
        except ValueError: continue
    return None

def extract_ticker(description: str, symbol_col: str, quantity: Decimal) -> str:
    """
    Extracts ticker symbol. Handles cases with spaces like 'MGA (ISIN)'
    and simple cases like 'TSLA Cash Div'.
    """
    # 1. Deduction (Qty < 0) -> Always trust Symbol column (selling/removing old stock)
    if quantity < 0:
        if symbol_col and symbol_col.strip():
            # Handle comma-separated aliases (e.g., 'TTE, TOT')
            clean_sym = symbol_col.strip().split(',')[0].strip()
            return clean_sym.split()[0]  # Take first word
        match_start = re.search(r'^([A-Za-z0-9\.]+)\s*\(', description)
        if match_start:
            return match_start.group(1).strip()
            
    # 2. Addition (Qty > 0) -> Look for new ticker in description (for Spinoffs/Mergers)
    if quantity > 0:
        # Regex for patterns like "(NEWTICKER, ISIN, ...)" inside description
        embedded_match = re.search(r'\(([A-Za-z0-9\.]+),\s+[^,]+,\s+[A-Za-z0-9]{9,}\)', description)
        if embedded_match:
            return embedded_match.group(1).strip()

    # 3. Fallback logic
    if symbol_col and symbol_col.strip(): 
        # Handle comma-separated aliases (e.g., 'TTE, TOT')
        clean_sym = symbol_col.strip().split(',')[0].strip()
        return clean_sym.split()[0]  # Take first word
        
    # Priority: Regex looking for Ticker(ISIN) or Ticker (ISIN)
    match_start = re.search(r'^([A-Za-z0-9\.]+)\s*\(', description)
    if match_start:
        return match_start.group(1).strip()
    
    # 4. LAST RESORT: First word
    # This fixes cases like "TSLA Cash Div" where there are no parentheses.
    parts = description.split()
    if parts:
        candidate = parts[0]
        # Basic validation: Uppercase and reasonable length
        if candidate.isupper() and len(candidate) < 12:
            return candidate

    return "UNKNOWN"

def classify_trade_type(description: str, quantity: Decimal) -> str:
    desc_upper = description.upper()
    transfer_keywords = ["ACATS", "TRANSFER", "INTERNAL", "POSITION MOVEM", "RECEIVE DELIVER", "CASH IN LIEU"]
    if any(k in desc_upper for k in transfer_keywords): return "TRANSFER"
    if quantity > 0: return "BUY"
    if quantity < 0: return "SELL"
    return "UNKNOWN"

def classify_corp_action(description: str, quantity: Decimal) -> str:
    if quantity > 0: return "STOCK_DIV" 
    if quantity < 0: return "MERGER" 
    return "CORP_ACTION_INFO"

def get_col_idx(headers: Dict[str, int], possible_names: List[str]) -> Optional[int]:
    for name in possible_names:
        if name in headers: return headers[name]
    return None

def load_manual_fixes(filepath: str) -> List[Dict]:
    fixes = []
    if not os.path.exists(filepath):
        return fixes

    print(f"🔧 Loading manual fixes from {filepath}...")
    try:
        with open(filepath, 'r', encoding='utf-8') as f:
            reader = csv.DictReader(f)
            for row in reader:
                if not row['Date'] or not row['Ticker']: continue
                
                fixes.append({
                    'ticker': row['Ticker'].strip(),
                    'currency': row['Currency'].strip() if row['Currency'] else 'USD',
                    'date': row['Date'].strip(),
                    'qty': parse_decimal(row['Quantity']),
                    'price': parse_decimal(row['Price']),
                    'commission': Decimal(0),
                    'type': row['Type'].strip(),
                    'source': 'MANUAL_FIX',
                    'source_file': 'manual_fixes.csv' 
                })
    except Exception as e:
        print(f"❌ Error loading manual fixes: {e}")
    
    return fixes

def parse_csv(filepath: str) -> Dict[str, List]:
    data = {'trades': [], 'dividends': [], 'taxes': [], 'corp_actions': []}
    section_headers = {}
    filename = os.path.basename(filepath)
    print(f"📂 Parsing file: {filename}")
    
    try:
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

                def check_date_and_parse(row, idx_date_col):
                    d_str = normalize_date(row[idx_date_col])
                    if not d_str: return None
                    return d_str

                # --- TRADES ---
                if section == 'Trades':
                    col_asset = get_col_idx(headers, ['Asset Category', 'Asset Class'])
                    if col_asset and row[col_asset] not in ['Stocks', 'Equity']: continue
                    
                    idx_date = get_col_idx(headers, ['Date/Time', 'Date', 'TradeDate'])
                    idx_cur = get_col_idx(headers, ['Currency'])
                    idx_sym = get_col_idx(headers, ['Symbol', 'Ticker'])
                    idx_qty = get_col_idx(headers, ['Quantity'])
                    idx_price = get_col_idx(headers, ['T. Price', 'TradePrice', 'Price'])
                    idx_comm = get_col_idx(headers, ['Comm/Fee', 'IBCommission', 'Commission'])
                    idx_desc = get_col_idx(headers, ['Description'])

                    if any(x is None for x in [idx_date, idx_qty, idx_price]): continue
                    if idx_desc and "Total" in row[idx_desc]: continue

                    date_norm = check_date_and_parse(row, idx_date)
                    if not date_norm: continue

                    qty = parse_decimal(row[idx_qty])
                    if qty == 0: continue
                    
                    sym_raw = row[idx_sym] if idx_sym else ""
                    desc_raw = row[idx_desc] if idx_desc else ""
                    ticker = extract_ticker(desc_raw, sym_raw, qty)

                    data['trades'].append({
                        'ticker': ticker,
                        'currency': row[idx_cur],
                        'date': date_norm,
                        'qty': qty,
                        'price': parse_decimal(row[idx_price]),
                        'commission': parse_decimal(row[idx_comm]) if idx_comm else Decimal(0),
                        'type': classify_trade_type(desc_raw, qty),
                        'source': 'IBKR',
                        'source_file': filename 
                    })

                # --- CORPORATE ACTIONS ---
                elif section == 'Corporate Actions':
                    col_asset = get_col_idx(headers, ['Asset Category'])
                    if col_asset and row[col_asset] not in ['Stocks', 'Equity']: continue

                    idx_date = get_col_idx(headers, ['Date/Time', 'Report Date'])
                    idx_desc = get_col_idx(headers, ['Description'])
                    idx_qty = get_col_idx(headers, ['Quantity'])
                    idx_sym = get_col_idx(headers, ['Symbol', 'Ticker']) 

                    if any(x is None for x in [idx_date, idx_desc, idx_qty]): continue
                    if "Total" in row[idx_desc]: continue

                    date_norm = check_date_and_parse(row, idx_date)
                    if not date_norm: continue

                    qty = parse_decimal(row[idx_qty])
                    desc = row[idx_desc]
                    sym_val = row[idx_sym] if idx_sym else ""
                    
                    action_type = classify_corp_action(desc, qty)

                    if action_type in ['STOCK_DIV', 'MERGER']:
                        real_ticker = extract_ticker(desc, sym_val, qty)
                        data['corp_actions'].append({
                            'ticker': real_ticker,
                            'currency': 'USD', 
                            'date': date_norm,
                            'qty': qty,
                            'price': Decimal(0), 
                            'commission': Decimal(0),
                            'type': action_type,
                            'source': 'IBKR_CORP',
                            'source_file': filename
                        })

                # --- DIVIDENDS ---
                elif section == 'Dividends':
                    idx_date = get_col_idx(headers, ['Date', 'PayDate'])
                    idx_cur = get_col_idx(headers, ['Currency'])
                    idx_desc = get_col_idx(headers, ['Description', 'Label'])
                    idx_amt = get_col_idx(headers, ['Amount', 'Gross Rate', 'Gross Amount'])
                    
                    if any(x is None for x in [idx_date, idx_desc, idx_amt]): continue
                    if "Total" in row[idx_desc]: continue

                    date_norm = check_date_and_parse(row, idx_date)
                    if not date_norm: continue
                    
                    # FIX: Pass empty string as symbol to force regex extraction from description
                    ticker = extract_ticker(row[idx_desc], "", Decimal(0))

                    data['dividends'].append({
                        'ticker': ticker,
                        'currency': row[idx_cur],
                        'date': date_norm,
                        'amount': parse_decimal(row[idx_amt]),
                        'source_file': filename
                    })
                
                # --- TAXES ---
                elif section == 'Withholding Tax':
                    idx_date = get_col_idx(headers, ['Date'])
                    idx_cur = get_col_idx(headers, ['Currency'])
                    idx_desc = get_col_idx(headers, ['Description', 'Label'])
                    idx_amt = get_col_idx(headers, ['Amount'])
                    
                    if any(x is None for x in [idx_date, idx_amt]): continue
                    if idx_desc and "Total" in row[idx_desc]: continue

                    date_norm = check_date_and_parse(row, idx_date)
                    if not date_norm: continue
                    
                    ticker = extract_ticker(row[idx_desc] if idx_desc else "", "", Decimal(0))

                    data['taxes'].append({
                        'ticker': ticker,
                        'currency': row[idx_cur],
                        'date': date_norm,
                        'amount': parse_decimal(row[idx_amt]),
                        'source_file': filename
                    })

    except Exception as e:
        print(f"❌ Error parsing {filename}: {e}")
        
    return data

def save_to_database(all_data):
    # Load manual fixes
    manual_fixes = load_manual_fixes(MANUAL_FIXES_FILE)
    if manual_fixes:
        all_data['corp_actions'].extend(manual_fixes)

    seen_registry = {} 
    unique_records = []
    duplicates_count = 0
    
    # Universal list processing
    def process_list(datalist, category):
        nonlocal duplicates_count
        for t in datalist:
            # Use .get() to avoid KeyError (dividends have no qty)
            qty_val = t.get('qty', 0)
            price_val = t.get('price', 0)
            amount_val = t.get('amount', 0)
            
            # Create hash signature
            qty_sig = f"{qty_val:.6f}"
            price_sig = f"{price_val:.6f}"
            amt_sig = f"{amount_val:.6f}"
            
            sig = (
                t['date'], 
                t['ticker'], 
                qty_sig, 
                price_sig, 
                amt_sig,
                t.get('type', category)
            )
            
            current_file = t.get('source_file', 'UNKNOWN')

            if sig in seen_registry:
                # Duplicate found - skipping
                duplicates_count += 1
                continue
            
            # Register new unique record
            seen_registry[sig] = current_file
            
            # Add to DB list
            if category == 'DIVIDEND':
                unique_records.append((t['date'], 'DIVIDEND', t['ticker'], 0, 0, t['currency'], float(amount_val), 0, 'Dividend'))
            elif category == 'TAX':
                unique_records.append((t['date'], 'TAX', t['ticker'], 0, 0, t['currency'], float(amount_val), 0, 'Tax'))
            else:
                 unique_records.append((
                    t['date'], t['type'], t['ticker'], 
                    float(qty_val), float(price_val), t['currency'], 
                    float(qty_val * price_val), float(t['commission']), t['source']
                ))

    # Process all lists via unified logic
    process_list(all_data['trades'], 'TRADE')
    process_list(all_data['corp_actions'], 'CORP')
    process_list(all_data['dividends'], 'DIVIDEND')
    process_list(all_data['taxes'], 'TAX')

    if duplicates_count > 0:
        print(f"🧹 Deduplication: Skipped {duplicates_count} duplicate records found across overlapping files.")

    if not unique_records:
        print("WARNING: No valid records to save.")
        return

    with DBConnector() as db:
        db.initialize_schema()
        db.conn.execute("DELETE FROM transactions")
        db.conn.executemany('INSERT INTO transactions (Date, EventType, Ticker, Quantity, Price, Currency, Amount, Fee, Description) VALUES (?,?,?,?,?,?,?,?,?)', unique_records)
        db.conn.commit()
    print(f"✅ Imported {len(unique_records)} unique records.")

if __name__ == '__main__':
    parser = argparse.ArgumentParser()
    parser.add_argument('--files', required=True)
    args = parser.parse_args()
    
    combined = {'trades': [], 'dividends': [], 'taxes': [], 'corp_actions': []}
    files = sorted(glob.glob(args.files))
    
    for fp in files:
        parsed = parse_csv(fp)
        for k in combined: combined[k].extend(parsed[k])
        
    save_to_database(combined)