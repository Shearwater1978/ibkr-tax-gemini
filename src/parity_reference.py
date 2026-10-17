# src/parity_reference.py
#
# Reference outputs for the parity fixtures shared with the mobile clients. The
# Python parser and FIFO engine are the behavioral reference: mobile parsers and
# FIFO ports must reproduce what these functions return for the same fixtures.

import json
from decimal import Decimal

from src.parser import _source_key, parse_csv

# Section order of save_to_database, which mobile deduplication must follow.
SOURCE_KEY_ORDER = [
    ("trades", "TRADE"),
    ("corp_actions", "CORP"),
    ("dividends", "DIVIDEND"),
    ("taxes", "TAX"),
]


def as_json(data):
    """Round-trips through JSON with decimals as strings, as the fixtures store them."""
    return json.loads(
        json.dumps(data, default=lambda v: str(v) if isinstance(v, Decimal) else v)
    )


def parsed_records(csv_path):
    """Normalized records the reference parser produces for a Flex Query CSV."""
    return as_json(parse_csv(str(csv_path)))


def source_keys(csv_path):
    """Deduplication keys in save_to_database order."""
    parsed = parse_csv(str(csv_path))
    return [
        _source_key({**record, "type": record.get("type", category)})
        for section, category in SOURCE_KEY_ORDER
        for record in parsed[section]
    ]


def reference_open_lots(transactions):
    """Open FIFO lots from the desktop pipeline (identity resolution + TradeMatcher).

    NBP rates are stubbed to 1: open lots do not depend on PLN conversion, and
    fixture generation must not use the network.
    """
    import src.processing as processing
    from src.diagnostics import UnmatchedInventoryError

    rows = [
        {
            "TradeId": index,
            "Date": t["date"],
            "EventType": t["event_type"],
            "Ticker": t["ticker"],
            "Quantity": t["quantity"],
            "Price": t["price"],
            "Amount": t.get("amount", "0"),
            "Fee": t["fee"],
            "Currency": t["currency"],
            "ISIN": t.get("isin", ""),
            "Description": t.get("description", ""),
            "SplitRatio": t.get("split_ratio"),
        }
        for index, t in enumerate(transactions)
    ]
    captured = []

    class CapturingMatcher(processing.TradeMatcher):
        def __init__(self):
            super().__init__()
            captured.append(self)

    original = (processing.TradeMatcher, processing.get_nbp_rate)
    processing.TradeMatcher = CapturingMatcher
    processing.get_nbp_rate = lambda currency, date: Decimal("1")
    try:
        processing.process_yearly_data(rows, 2000)
    except UnmatchedInventoryError as error:
        diagnostic = error.diagnostic
        return {
            "error": diagnostic.code,
            "ticker": diagnostic.ticker,
            "date": diagnostic.date,
        }
    finally:
        processing.TradeMatcher, processing.get_nbp_rate = original

    return {
        "lots": [
            {
                "ticker": lot["ticker"],
                "isin": lot["isin"],
                "date": lot["date"],
                "quantity": str(lot["qty"]),
                "price": str(lot["price"]),
                "currency": lot["currency"],
            }
            for lots in captured[0].inventory.values()
            for lot in lots
        ]
    }


def fifo_expectations(scenarios):
    """Expected open lots (or the unmatched-sell diagnostic) per named scenario."""
    return {s["name"]: reference_open_lots(s["transactions"]) for s in scenarios}
