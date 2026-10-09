# tests/test_mobile_fixtures.py
#
# The mobile clients share these synthetic Flex Query fixtures. The Python parser
# is the behavioral reference, so its output must match the committed expectations.

import json
import re
from decimal import Decimal
from pathlib import Path

import pytest

from src.parser import _source_key, parse_csv

FIXTURES = Path(__file__).resolve().parents[1] / "mobile" / "fixtures" / "flex-query"
EXPECTED = FIXTURES / "expected"

SYNTHETIC_ACCOUNT = re.compile(r"^U0000000\d$")
ACCOUNT_LIKE = re.compile(r"\b[UF]\d{7,8}\b")


def _as_json(data):
    return json.loads(
        json.dumps(data, default=lambda v: str(v) if isinstance(v, Decimal) else v)
    )


@pytest.mark.parametrize("expected_file", sorted(EXPECTED.glob("*.json")), ids=str)
def test_parser_output_matches_mobile_expectations(expected_file):
    fixture = FIXTURES / f"{expected_file.stem}.csv"
    expected = json.loads(expected_file.read_text(encoding="utf-8"))

    assert _as_json(parse_csv(str(fixture))) == expected


def test_every_valid_fixture_has_expectations():
    valid = {p.stem for p in FIXTURES.glob("valid_*.csv")}
    assert valid
    assert valid == {p.stem for p in EXPECTED.glob("*.json")}
    assert valid == {p.stem for p in (EXPECTED / "source_keys").glob("*.json")}


@pytest.mark.parametrize(
    "keys_file", sorted((EXPECTED / "source_keys").glob("*.json")), ids=str
)
def test_source_keys_match_mobile_expectations(keys_file):
    # Mobile dedup must produce the same keys as save_to_database, in this order.
    parsed = parse_csv(str(FIXTURES / f"{keys_file.stem}.csv"))
    order = [
        ("trades", "TRADE"),
        ("corp_actions", "CORP"),
        ("dividends", "DIVIDEND"),
        ("taxes", "TAX"),
    ]
    keys = [
        _source_key({**record, "type": record.get("type", category)})
        for section, category in order
        for record in parsed[section]
    ]

    assert keys == json.loads(keys_file.read_text(encoding="utf-8"))


FIFO = Path(__file__).resolve().parents[1] / "mobile" / "fixtures" / "fifo"


def reference_open_lots(transactions):
    """Open FIFO lots from the desktop pipeline (identity resolution + TradeMatcher).

    NBP rates are stubbed to 1: open lots do not depend on PLN conversion, and
    tests must not use the network.
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


def test_fifo_scenarios_match_mobile_expectations():
    scenarios = json.loads((FIFO / "scenarios.json").read_text(encoding="utf-8"))
    expected = json.loads(
        (FIFO / "expected_open_lots.json").read_text(encoding="utf-8")
    )

    actual = {s["name"]: reference_open_lots(s["transactions"]) for s in scenarios}
    assert actual == expected


@pytest.mark.parametrize("fixture", sorted(FIXTURES.glob("*.csv")), ids=str)
def test_fixtures_use_only_synthetic_account_identifiers(fixture):
    text = fixture.read_text(encoding="utf-8")

    for account in ACCOUNT_LIKE.findall(text):
        assert SYNTHETIC_ACCOUNT.match(account), account
    for line in text.splitlines():
        if line.startswith("Account Information,Data,Name,"):
            assert line.endswith("Synthetic Test User")
