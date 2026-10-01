# tests/test_parser.py

import csv
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


def test_tracked_example_statement_supplies_identity():
    statement = (
        Path(__file__).parent.parent
        / "example_reports_2020_2024"
        / "U12345678_2020.csv"
    )
    parsed = parse_csv(str(statement))
    aapl_dividend = next(
        record for record in parsed["dividends"] if record["ticker"] == "AAPL"
    )
    assert aapl_dividend["isin"] == "US0378331005"
    assert aapl_dividend["conid"] == "265598"
    assert aapl_dividend["instrument_description"] == "APPLE INC"


def test_narrow_financial_instrument_section_supplies_missing_identity(tmp_path):
    statement = tmp_path / "narrow_statement.csv"
    rows = [
        [
            "Trades",
            "Header",
            "Asset Category",
            "Currency",
            "Symbol",
            "Date/Time",
            "Quantity",
            "T. Price",
            "Comm/Fee",
            "Description",
        ],
        ["Trades", "Data", "Stocks", "USD", "XYZ", "2024-01-02", "1", "10", "0", ""],
        [
            "Financial Instrument Information",
            "Header",
            "Symbol",
            "Security ID",
            "Conid",
            "Description",
        ],
        [
            "Financial Instrument Information",
            "Data",
            "XYZ",
            "US0378331005",
            "12345",
            "TEST CORPORATION",
        ],
    ]
    with statement.open("w", newline="", encoding="utf-8") as output:
        csv.writer(output).writerows(rows)

    parsed = parse_csv(str(statement))
    assert parsed["trades"][0]["isin"] == "US0378331005"
    assert parsed["trades"][0]["conid"] == "12345"
    assert parsed["trades"][0]["instrument_description"] == "TEST CORPORATION"


def test_duplicate_symbol_identity_uses_currency_context_from_statement(tmp_path):
    statement = tmp_path / "duplicate_symbol_statement.csv"
    rows = [
        [
            "Trades",
            "Header",
            "Asset Category",
            "Currency",
            "Symbol",
            "Date/Time",
            "Quantity",
            "T. Price",
            "Comm/Fee",
            "Description",
        ],
        [
            "Trades",
            "Data",
            "Stocks",
            "USD",
            "SBER",
            "2022-01-14",
            "5",
            "13.335",
            "-0.525",
            "",
        ],
        [
            "Corporate Actions",
            "Header",
            "Asset Category",
            "Currency",
            "Report Date",
            "Description",
            "Quantity",
        ],
        [
            "Corporate Actions",
            "Data",
            "Stocks",
            "USD",
            "2022-05-24",
            "SBER(US80585Y3080) Tendered to US80585Y3CNV (SBER, SBERBANK ADR, US80585Y3080)",
            "-5",
        ],
        [
            "Corporate Actions",
            "Data",
            "Stocks",
            "RUB",
            "2022-05-24",
            "SBER.CNV4(563839405) Merged WITH SBER (SBER, SBERBANK COMMON, RU0009029540)",
            "20",
        ],
        [
            "Financial Instrument Information",
            "Header",
            "Symbol",
            "Security ID",
            "Conid",
            "Description",
        ],
        [
            "Financial Instrument Information",
            "Data",
            "SBER",
            "US80585Y3080",
            "90581067",
            "SBERBANK ADR",
        ],
        [
            "Financial Instrument Information",
            "Data",
            "SBER",
            "RU0009029540",
            "360308912",
            "SBERBANK COMMON",
        ],
    ]
    with statement.open("w", newline="", encoding="utf-8") as output:
        csv.writer(output).writerows(rows)

    parsed = parse_csv(str(statement))

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
