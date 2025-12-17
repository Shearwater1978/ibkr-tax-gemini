# tests/test_parser.py

import pytest
from decimal import Decimal
from src.parser import (
    normalize_date,
    extract_ticker,
    parse_decimal,
    classify_trade_type,
)


# --- DATE TESTS ---
@pytest.mark.parametrize(
    "input_date, expected",
    [
        ("20250102", "2025-01-02"),
        ("01/02/2025", "2025-01-02"),
        ("2025-01-02, 15:00:00", "2025-01-02"),
        ("", None),
        (None, None),
    ],
)
def test_normalize_date(input_date, expected):
    assert normalize_date(input_date) == expected


# --- TICKER EXTRACTION TESTS ---
@pytest.mark.parametrize(
    "desc, symbol_col, qty, expected",
    [
        # 1. Standard case: Ticker immediately followed by ISIN
        ("AGR(US05351W1036) Cash Dividend", "", 0, "AGR"),
        # 2. THE FIX: Ticker separated by space from ISIN (e.g. MGA)
        ("MGA (CA5592224011) Cash Dividend", "", 0, "MGA"),
        # 3. Fallback: Symbol column has priority if valid
        ("Unknown Description", "AAPL", 0, "AAPL"),
        # 4. Fallback: Simple description, first word is uppercase
        ("TSLA Cash Div", "", 0, "TSLA"),
    ],
)
def test_extract_ticker(desc, symbol_col, qty, expected):
    result = extract_ticker(desc, symbol_col, Decimal(qty))
    assert result == expected


# --- DECIMAL PARSING TESTS ---
@pytest.mark.parametrize(
    "input_str, expected",
    [
        ("1,000.50", Decimal("1000.50")),
        ('"1,234.56"', Decimal("1234.56")),  # Quotes handling
        ("-500", Decimal("-500")),
        ("", Decimal("0")),
        (None, Decimal("0")),
    ],
)
def test_parse_decimal(input_str, expected):
    assert parse_decimal(input_str) == expected


# --- TRADE CLASSIFICATION TESTS ---
@pytest.mark.parametrize(
    "desc, qty, expected",
    [
        ("ACATS Transfer", 10, "TRANSFER"),
        ("Internal Transfer", 10, "TRANSFER"),
        ("Buy Order", 10, "BUY"),
        ("Sell Order", -5, "SELL"),
        ("Random Text", 0, "UNKNOWN"),
    ],
)
def test_classify_trade_type(desc, qty, expected):
    assert classify_trade_type(desc, Decimal(qty)) == expected
