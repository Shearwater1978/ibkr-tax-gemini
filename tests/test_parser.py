# tests/test_parser.py

import pytest
from decimal import Decimal
from pathlib import Path
from src.parser import (
    normalize_date,
    extract_ticker,
    extract_isin,
    parse_decimal,
    classify_trade_type,
    parse_csv,
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


@pytest.mark.parametrize(
    "description, expected",
    [
        ("OKE(US6826801036) Cash Dividend USD 0.99 per Share", "US6826801036"),
        ("MGA (CA5592224011) Cash Dividend", "CA5592224011"),
        ("Cash dividend without security identifier", ""),
    ],
)
def test_extract_isin(description, expected):
    assert extract_isin(description) == expected


def test_extract_isin_uses_child_ticker_in_spinoff_description():
    description = (
        "MRK(US58933Y1055) Spinoff 1 for 10 " "(OGN, ORGANON & CO-W/I, US68622V1061)"
    )

    assert extract_isin(description, "OGN") == "US68622V1061"


def test_real_statement_supplies_identity_and_narrow_variant_parses():
    data_dir = Path(__file__).parent.parent / "data"
    parsed = parse_csv(str(data_dir / "U1601_2024_2024.csv"))
    oke_dividend = next(
        record for record in parsed["dividends"] if record["ticker"] == "OKE"
    )
    assert oke_dividend["isin"] == "US6826801036"
    assert oke_dividend["conid"] == "10794"
    assert oke_dividend["instrument_description"] == "ONEOK INC"
    mga_dividend = next(
        record for record in parsed["dividends"] if record["ticker"] == "MGA"
    )
    assert mga_dividend["isin"] == "CA5592224011"

    older_statement = parse_csv(str(data_dir / "U5801_20210315_20220107.csv"))
    assert older_statement["trades"]
    ogn_spinoff = next(
        record
        for record in older_statement["corp_actions"]
        if record["ticker"] == "OGN"
    )
    ogn_sale = next(
        record for record in older_statement["trades"] if record["ticker"] == "OGN"
    )
    assert ogn_spinoff["isin"] == ogn_sale["isin"] == "US68622V1061"


def test_duplicate_symbol_identity_uses_currency_context_from_statement():
    data_dir = Path(__file__).parent.parent / "data"
    parsed = parse_csv(str(data_dir / "U1601_U7701_20220103_20221230.csv"))

    sber_buy = next(
        record
        for record in parsed["trades"]
        if record["ticker"] == "SBER" and record["date"] == "2022-01-14"
    )
    sber_adr_tender = next(
        record
        for record in parsed["corp_actions"]
        if record["ticker"] == "SBER" and record["qty"] < 0
    )
    sber_rub_addition = next(
        record
        for record in parsed["corp_actions"]
        if record["ticker"] == "SBER" and record["qty"] > 0
    )

    assert sber_buy["isin"] == sber_adr_tender["isin"] == "US80585Y3080"
    assert sber_buy["conid"] == "90581067"
    assert sber_rub_addition["isin"] == "RU0009029540"
    assert sber_rub_addition["currency"] == "RUB"


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
