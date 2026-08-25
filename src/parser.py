# src/parser.py

import csv
import re
import glob
import argparse
import os
import hashlib
from datetime import datetime
from decimal import Decimal
from typing import List, Dict, Optional
from src.db_connector import DBConnector

# --- CONFIGURATION ---
# Leave empty to parse everything. Deduplication will handle overlaps.
FILE_DATE_LIMITS = {}
PROJECT_ROOT = os.path.dirname(os.path.dirname(os.path.abspath(__file__)))
MANUAL_FIXES_FILE = os.path.join(PROJECT_ROOT, "manual_fixes.csv")


def parse_decimal(value: str) -> Decimal:
    """Removes commas and quotes, parses number."""
    if not value:
        return Decimal(0)
    clean = value.replace(",", "").replace('"', "").strip()
    try:
        return Decimal(clean)
    except:
        return Decimal(0)


def normalize_date(date_str: str) -> Optional[str]:
    """Converts date to YYYY-MM-DD format."""
    if not date_str:
        return None
    clean = date_str.split(",")[0].strip().split(" ")[0]
    formats = ["%Y-%m-%d", "%Y%m%d", "%m/%d/%Y", "%d/%m/%Y", "%d-%b-%y"]
    for fmt in formats:
        try:
            return datetime.strptime(clean, fmt).strftime("%Y-%m-%d")
        except ValueError:
            continue
    return None


def extract_ticker(description: str, symbol_col: str, quantity: Decimal) -> str:
    """
    Extracts ticker symbol. Handles cases with spaces like 'MGA (ISIN)'
    and complex spinoff descriptions like 'FNF(...) Spinoff (FG, ...)'.
    """
    # 1. Deduction (Qty < 0) -> Trust Symbol column for exits
    if quantity < 0:
        if symbol_col and symbol_col.strip():
            clean_sym = symbol_col.strip().split(",")[0].strip()
            return clean_sym.split()[0]
        match_start = re.search(r"^([A-Za-z0-9\.]+)\s*\(", description)
        if match_start:
            return match_start.group(1).strip()

    # 2. Addition (Qty > 0) -> Priority to embedded tickers in description (Spinoffs/Mergers)
    if quantity > 0:
        # Matches (TICKER, ISIN, ...) inside description
        embedded_match = re.search(
            r"\(([A-Za-z0-9\.]+),\s+[^,]+,\s+[A-Za-z0-9]{9,}\)", description
        )
        if embedded_match:
            return embedded_match.group(1).strip()

    # 3. Fallback logic
    if symbol_col and symbol_col.strip():
        clean_sym = symbol_col.strip().split(",")[0].strip()
        return clean_sym.split()[0]

    match_start = re.search(r"^([A-Za-z0-9\.]+)\s*\(", description)
    if match_start:
        return match_start.group(1).strip()

    # 4. First word fallback
    parts = description.split()
    if parts:
        candidate = parts[0]
        if candidate.isupper() and len(candidate) < 12:
            return candidate

    return "UNKNOWN"


def classify_trade_type(description: str, quantity: Decimal) -> str:
    """Classifies standard trades and transfers."""
    desc_upper = description.upper()
    transfer_keywords = [
        "ACATS",
        "TRANSFER",
        "INTERNAL",
        "POSITION MOVEM",
        "RECEIVE DELIVER",
        "CASH IN LIEU",
    ]
    if any(k in desc_upper for k in transfer_keywords):
        return "TRANSFER"
    if quantity > 0:
        return "BUY"
    if quantity < 0:
        return "SELL"
    return "UNKNOWN"


def classify_corp_action(description: str, quantity: Decimal) -> str:
    """
    Classifies corporate actions.
    Explicitly detects SPINOFF for better reporting.
    """
    desc_upper = description.upper()
    if "SPINOFF" in desc_upper:
        return "SPINOFF"
    if quantity > 0:
        return "STOCK_DIV"
    if quantity < 0:
        return "MERGER"
    return "CORP_ACTION_INFO"


def extract_split_ratio(description: str) -> Optional[Decimal]:
    """Extracts ratios such as ``Split 2 for 1`` from an IBKR description."""
    match = re.search(
        r"\bSPLIT\s+(\d+(?:\.\d+)?)\s+FOR\s+(\d+(?:\.\d+)?)\b", description, re.I
    )
    if not match:
        return None
    denominator = Decimal(match.group(2))
    if denominator == 0:
        return None
    return Decimal(match.group(1)) / denominator


def get_col_idx(headers: Dict[str, int], possible_names: List[str]) -> Optional[int]:
    """Helper to find column index from a list of possible header names."""
    for name in possible_names:
        if name in headers:
            return headers[name]
    return None


def load_manual_fixes(filepath: str) -> List[Dict]:
    """Loads manual adjustments from a CSV file."""
    fixes = []
    if not os.path.exists(filepath):
        return fixes

    print(f"🔧 Loading manual fixes from {filepath}...")
    try:
        with open(filepath, "r", encoding="utf-8") as f:
            reader = csv.DictReader(f)
            for row in reader:
                if not row["Date"] or not row["Ticker"]:
                    continue

                fixes.append(
                    {
                        "ticker": row["Ticker"].strip(),
                        "currency": (
                            row["Currency"].strip() if row["Currency"] else "USD"
                        ),
                        "date": row["Date"].strip(),
                        "qty": parse_decimal(row["Quantity"]),
                        "price": parse_decimal(row["Price"]),
                        "commission": Decimal(0),
                        "type": row["Type"].strip(),
                        "source": "MANUAL_FIX",
                        "source_file": "manual_fixes.csv",
                    }
                )
    except Exception as e:
        print(f"❌ Error loading manual fixes: {e}")
    return fixes


def parse_csv(filepath: str) -> Dict[str, List]:
    """Parses IBKR Activity Flex Query CSV file."""
    data = {"trades": [], "dividends": [], "taxes": [], "corp_actions": []}
    section_headers = {}
    filename = os.path.basename(filepath)
    print(f"📂 Parsing file: {filename}")

    try:
        with open(filepath, "r", encoding="utf-8-sig") as f:
            reader = csv.reader(f)
            for row in reader:
                if len(row) < 2:
                    continue
                section, row_type = row[0], row[1]

                if row_type == "Header":
                    section_headers[section] = {n.strip(): i for i, n in enumerate(row)}
                    continue

                if row_type != "Data" or section not in section_headers:
                    continue
                headers = section_headers[section]

                def check_date_and_parse(row, idx_date_col):
                    d_str = normalize_date(row[idx_date_col])
                    return d_str if d_str else None

                # --- TRADES ---
                if section == "Trades":
                    col_asset = get_col_idx(headers, ["Asset Category", "Asset Class"])
                    if col_asset is not None and row[col_asset] not in [
                        "Stocks",
                        "Equity",
                    ]:
                        continue

                    idx_date = get_col_idx(headers, ["Date/Time", "Date", "TradeDate"])
                    idx_cur = get_col_idx(headers, ["Currency"])
                    idx_sym = get_col_idx(headers, ["Symbol", "Ticker"])
                    idx_qty = get_col_idx(headers, ["Quantity"])
                    idx_price = get_col_idx(
                        headers, ["T. Price", "TradePrice", "Price"]
                    )
                    idx_comm = get_col_idx(
                        headers, ["Comm/Fee", "IBCommission", "Commission"]
                    )
                    idx_desc = get_col_idx(headers, ["Description"])

                    if any(x is None for x in [idx_date, idx_qty, idx_price]):
                        continue
                    if idx_desc is not None and "Total" in row[idx_desc]:
                        continue

                    date_norm = check_date_and_parse(row, idx_date)
                    if not date_norm:
                        continue

                    qty = parse_decimal(row[idx_qty])
                    if qty == 0:
                        continue

                    sym_raw = row[idx_sym] if idx_sym else ""
                    desc_raw = row[idx_desc] if idx_desc else ""
                    ticker = extract_ticker(desc_raw, sym_raw, qty)

                    data["trades"].append(
                        {
                            "ticker": ticker,
                            "currency": row[idx_cur],
                            "date": date_norm,
                            "qty": qty,
                            "price": parse_decimal(row[idx_price]),
                            "commission": (
                                parse_decimal(row[idx_comm]) if idx_comm else Decimal(0)
                            ),
                            "type": classify_trade_type(desc_raw, qty),
                            "source": desc_raw or "IBKR Trade",
                            "source_file": filename,
                        }
                    )

                # --- CORPORATE ACTIONS ---
                elif section == "Corporate Actions":
                    col_asset = get_col_idx(headers, ["Asset Category"])
                    if col_asset is not None and row[col_asset] not in [
                        "Stocks",
                        "Equity",
                    ]:
                        continue

                    idx_date = get_col_idx(headers, ["Date/Time", "Report Date"])
                    idx_desc = get_col_idx(headers, ["Description"])
                    idx_qty = get_col_idx(headers, ["Quantity"])
                    idx_sym = get_col_idx(headers, ["Symbol", "Ticker"])

                    if any(x is None for x in [idx_date, idx_desc, idx_qty]):
                        continue
                    if "Total" in row[idx_desc]:
                        continue

                    date_norm = check_date_and_parse(row, idx_date)
                    if not date_norm:
                        continue

                    qty = parse_decimal(row[idx_qty])
                    desc = row[idx_desc]
                    sym_val = row[idx_sym] if idx_sym else ""
                    action_type = classify_corp_action(desc, qty)
                    split_ratio = extract_split_ratio(desc)
                    if split_ratio is not None:
                        action_type = "SPLIT"

                    # Now explicitly handling SPINOFF
                    if action_type in ["SPLIT", "STOCK_DIV", "MERGER", "SPINOFF"]:
                        real_ticker = extract_ticker(desc, sym_val, qty)
                        data["corp_actions"].append(
                            {
                                "ticker": real_ticker,
                                "currency": "USD",
                                "date": date_norm,
                                "qty": qty,
                                "price": Decimal(0),
                                "commission": Decimal(0),
                                "type": action_type,
                                "ratio": split_ratio,
                                "source": desc,  # Captures full spinoff description
                                "source_file": filename,
                            }
                        )

                # --- DIVIDENDS ---
                elif section == "Dividends":
                    idx_date = get_col_idx(headers, ["Date", "PayDate"])
                    idx_cur = get_col_idx(headers, ["Currency"])
                    idx_desc = get_col_idx(headers, ["Description", "Label"])
                    idx_amt = get_col_idx(
                        headers, ["Amount", "Gross Rate", "Gross Amount"]
                    )

                    if any(x is None for x in [idx_date, idx_desc, idx_amt]):
                        continue
                    if "Total" in row[idx_desc]:
                        continue

                    date_norm = check_date_and_parse(row, idx_date)
                    if not date_norm:
                        continue

                    ticker = extract_ticker(row[idx_desc], "", Decimal(0))
                    data["dividends"].append(
                        {
                            "ticker": ticker,
                            "currency": row[idx_cur],
                            "date": date_norm,
                            "amount": parse_decimal(row[idx_amt]),
                            "source_file": filename,
                        }
                    )

                # --- WITHHOLDING TAXES ---
                elif section == "Withholding Tax":
                    idx_date = get_col_idx(headers, ["Date"])
                    idx_cur = get_col_idx(headers, ["Currency"])
                    idx_desc = get_col_idx(headers, ["Description", "Label"])
                    idx_amt = get_col_idx(headers, ["Amount"])

                    if any(x is None for x in [idx_date, idx_amt]):
                        continue
                    if idx_desc is not None and "Total" in row[idx_desc]:
                        continue

                    date_norm = check_date_and_parse(row, idx_date)
                    if not date_norm:
                        continue

                    ticker = extract_ticker(
                        row[idx_desc] if idx_desc else "", "", Decimal(0)
                    )
                    data["taxes"].append(
                        {
                            "ticker": ticker,
                            "currency": row[idx_cur],
                            "date": date_norm,
                            "amount": parse_decimal(row[idx_amt]),
                            "source_file": filename,
                        }
                    )

    except Exception as e:
        print(f"❌ Error parsing {filename}: {e}")
    return data


def _source_key(record):
    values = [
        str(record.get("date", "")),
        str(record.get("type", "")),
        str(record.get("ticker", "")),
        str(record.get("qty", "")),
        str(record.get("price", "")),
        str(record.get("currency", "")),
        str(record.get("amount", "")),
        str(record.get("commission", "")),
        str(record.get("source", "")),
        str(record.get("ratio", "")),
    ]
    return hashlib.sha256("\x1f".join(values).encode("utf-8")).hexdigest()


def save_to_database(all_data):
    """Validates and atomically upserts normalized records."""
    manual_fixes = load_manual_fixes(MANUAL_FIXES_FILE)
    if manual_fixes:
        all_data["corp_actions"].extend(manual_fixes)

    seen_registry = set()
    unique_records = []
    duplicates_count = 0

    def process_list(datalist, category):
        nonlocal duplicates_count
        for t in datalist:
            qty_val = t.get("qty", 0)
            price_val = t.get("price", 0)
            amount_val = t.get("amount", 0)

            record_type = t.get("type", category)
            if not t.get("date") or not t.get("ticker") or not t.get("currency"):
                raise ValueError("Import record is missing date, ticker, or currency")
            if record_type == "UNKNOWN":
                raise ValueError("Import record has an unknown event type")

            key_record = dict(t)
            key_record["type"] = record_type
            sig = _source_key(key_record)

            if sig in seen_registry:
                duplicates_count += 1
                continue

            seen_registry.add(sig)

            if category == "DIVIDEND":
                unique_records.append(
                    (
                        t["date"],
                        "DIVIDEND",
                        t["ticker"],
                        0,
                        0,
                        t["currency"],
                        float(amount_val),
                        0,
                        "Dividend",
                        sig,
                        None,
                    )
                )
            elif category == "TAX":
                unique_records.append(
                    (
                        t["date"],
                        "TAX",
                        t["ticker"],
                        0,
                        0,
                        t["currency"],
                        float(amount_val),
                        0,
                        "Tax",
                        sig,
                        None,
                    )
                )
            else:
                unique_records.append(
                    (
                        t["date"],
                        t["type"],
                        t["ticker"],
                        float(qty_val),
                        float(price_val),
                        t["currency"],
                        float(qty_val * price_val),
                        float(t["commission"]),
                        t["source"],
                        sig,
                        float(t.get("ratio")) if t.get("ratio") is not None else None,
                    )
                )

    process_list(all_data["trades"], "TRADE")
    process_list(all_data["corp_actions"], "CORP")
    process_list(all_data["dividends"], "DIVIDEND")
    process_list(all_data["taxes"], "TAX")

    if duplicates_count > 0:
        print(
            f"🧹 Deduplication: Skipped {duplicates_count} duplicate records across files."
        )

    if not unique_records:
        print("WARNING: No valid records to save.")
        return {"inserted": 0, "skipped": duplicates_count}

    with DBConnector() as db:
        db.initialize_schema()
        legacy_rows = db.conn.execute(
            "SELECT rowid, Date, EventType, Ticker, Quantity, Price, Currency, "
            "Amount, Fee, Description, SplitRatio FROM transactions "
            "WHERE SourceKey IS NULL"
        ).fetchall()
        for row in legacy_rows:
            legacy_record = {
                "date": row[1],
                "type": row[2],
                "ticker": row[3],
                "qty": row[4] or 0,
                "price": row[5] or 0,
                "currency": row[6],
                "amount": row[7] or 0,
                "commission": row[8] or 0,
                "source": row[9] or "",
                "ratio": row[10],
            }
            db.conn.execute(
                "UPDATE transactions SET SourceKey = ? WHERE rowid = ?",
                (_source_key(legacy_record), row[0]),
            )
        existing_keys = {
            row[0]
            for row in db.conn.execute(
                "SELECT SourceKey FROM transactions WHERE SourceKey IS NOT NULL"
            ).fetchall()
        }
        records_to_insert = [
            record for record in unique_records if record[-2] not in existing_keys
        ]
        skipped_existing = len(unique_records) - len(records_to_insert)
        db.conn.execute("BEGIN")
        try:
            before_count = db.conn.execute(
                "SELECT COUNT(*) FROM transactions"
            ).fetchone()[0]
            db.conn.executemany(
                "INSERT OR IGNORE INTO transactions "
                "(Date, EventType, Ticker, Quantity, Price, Currency, Amount, Fee, Description, SourceKey, SplitRatio) "
                "VALUES (?,?,?,?,?,?,?,?,?,?,?)",
                records_to_insert,
            )
            after_count = db.conn.execute(
                "SELECT COUNT(*) FROM transactions"
            ).fetchone()[0]
            inserted = after_count - before_count
            db.conn.commit()
        except Exception:
            db.conn.rollback()
            raise
    skipped = duplicates_count + skipped_existing + len(records_to_insert) - inserted
    print(f"✅ Imported {inserted} new records; skipped {skipped} duplicates.")
    return {"inserted": inserted, "skipped": skipped}


if __name__ == "__main__":
    parser = argparse.ArgumentParser()
    parser.add_argument("--files", required=True)
    args = parser.parse_args()

    combined = {"trades": [], "dividends": [], "taxes": [], "corp_actions": []}
    files = sorted(glob.glob(args.files))

    for fp in files:
        parsed = parse_csv(fp)
        for k in combined:
            combined[k].extend(parsed[k])

    save_to_database(combined)
