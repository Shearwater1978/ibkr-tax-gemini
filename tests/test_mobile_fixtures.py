# tests/test_mobile_fixtures.py
#
# The mobile clients share these synthetic Flex Query fixtures. The Python parser
# is the behavioral reference, so its output must match the committed expectations.

import json
import re
from decimal import Decimal
from pathlib import Path

import pytest

from src.parser import parse_csv

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


@pytest.mark.parametrize("fixture", sorted(FIXTURES.glob("*.csv")), ids=str)
def test_fixtures_use_only_synthetic_account_identifiers(fixture):
    text = fixture.read_text(encoding="utf-8")

    for account in ACCOUNT_LIKE.findall(text):
        assert SYNTHETIC_ACCOUNT.match(account), account
    for line in text.splitlines():
        if line.startswith("Account Information,Data,Name,"):
            assert line.endswith("Synthetic Test User")
