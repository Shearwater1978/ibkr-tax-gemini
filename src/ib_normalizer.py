"""Normalizes live IB API payloads into the same transaction schema used by
the CSV import pipeline (src/parser.py).

Only IBConnector.fetch_account_snapshot() output is accepted as input. This
module has no knowledge of sockets or ib_insync; it is a pure data mapping
layer so the same normalized shape can be handed to
src/parser.py::save_to_database() regardless of data source.
"""

import datetime
from decimal import Decimal, InvalidOperation


def _normalize_execution_date(raw_time):
    """Convert an IB execution timestamp (datetime or string) to YYYY-MM-DD."""
    if isinstance(raw_time, (datetime.datetime, datetime.date)):
        return raw_time.strftime("%Y-%m-%d")
    if isinstance(raw_time, str):
        for fmt in (
            "%Y%m%d  %H:%M:%S",
            "%Y%m%d %H:%M:%S",
            "%Y-%m-%d %H:%M:%S",
            "%Y-%m-%d",
        ):
            try:
                return datetime.datetime.strptime(raw_time, fmt).strftime("%Y-%m-%d")
            except ValueError:
                continue
    return None


def _to_decimal(value, default="0"):
    if value is None:
        return Decimal(default)
    try:
        return Decimal(str(value))
    except InvalidOperation:
        return Decimal(default)


def normalize_fill(fill: dict):
    """Convert one IBConnector.get_fills() row into a trade record.

    Returns None if the fill is missing fields required by
    src/parser.py::save_to_database() (date, ticker, currency), or is
    missing exec_id. exec_id is required because it is embedded in the
    "source" text that feeds the dedup SourceKey hash: without it, two
    distinct executions sharing date/ticker/qty/price/currency would
    collide and one would be wrongly dropped as a duplicate.
    """
    symbol = fill.get("symbol")
    currency = fill.get("currency")
    exec_id = fill.get("exec_id")
    date_norm = _normalize_execution_date(fill.get("time"))
    if not symbol or not currency or not date_norm or not exec_id:
        return None

    side = (fill.get("side") or "").upper()
    shares = _to_decimal(fill.get("shares"))
    if side == "BOT":
        qty, trade_type = shares, "BUY"
    elif side == "SLD":
        qty, trade_type = -shares, "SELL"
    else:
        qty, trade_type = shares, "UNKNOWN"

    return {
        "ticker": symbol,
        "currency": currency,
        "date": date_norm,
        "qty": qty,
        "price": _to_decimal(fill.get("price")),
        "commission": _to_decimal(fill.get("commission")),
        "type": trade_type,
        "source": f"IB Live Fill {exec_id}",
        "source_file": "ib_live_api",
    }


def normalize_snapshot(snapshot: dict) -> dict:
    """Convert an IBConnector.fetch_account_snapshot() payload into the
    {"trades", "dividends", "taxes", "corp_actions"} shape expected by
    src/parser.py::save_to_database().

    Only executed fills map to trades today: live read-only IB Gateway
    requests do not expose dividend, withholding-tax, or corporate-action
    history, so those lists stay empty and continue to come from CSV
    import. Matching the CSV-import shape lets both sources share
    save_to_database() without special-casing either one.
    """
    fills = snapshot.get("fills", [])
    trades = [record for fill in fills if (record := normalize_fill(fill)) is not None]
    return {
        "trades": trades,
        "dividends": [],
        "taxes": [],
        "corp_actions": [],
    }
