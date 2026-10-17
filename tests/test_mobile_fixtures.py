# tests/test_mobile_fixtures.py
#
# Parity fixtures owned by this repository (tests/fixtures/parity) and copied to the
# mobile clients (broker-companion). The Python parser and FIFO are the behavioral
# reference, so their output must match the committed expectations.

import json
import re
from pathlib import Path

import pytest

from src.parity_reference import fifo_expectations, parsed_records, source_keys

PARITY = Path(__file__).resolve().parent / "fixtures" / "parity"
FIXTURES = PARITY / "flex-query"
EXPECTED = FIXTURES / "expected"
FIFO = PARITY / "fifo"

SYNTHETIC_ACCOUNT = re.compile(r"^U0000000\d$")
ACCOUNT_LIKE = re.compile(r"\b[UF]\d{7,8}\b")


@pytest.mark.parametrize("expected_file", sorted(EXPECTED.glob("*.json")), ids=str)
def test_parser_output_matches_mobile_expectations(expected_file):
    fixture = FIXTURES / f"{expected_file.stem}.csv"
    expected = json.loads(expected_file.read_text(encoding="utf-8"))

    assert parsed_records(fixture) == expected


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
    keys = source_keys(FIXTURES / f"{keys_file.stem}.csv")

    assert keys == json.loads(keys_file.read_text(encoding="utf-8"))


def test_fifo_scenarios_match_mobile_expectations():
    scenarios = json.loads((FIFO / "scenarios.json").read_text(encoding="utf-8"))
    expected = json.loads(
        (FIFO / "expected_open_lots.json").read_text(encoding="utf-8")
    )

    assert fifo_expectations(scenarios) == expected


def test_generator_reproduces_committed_expectations():
    # tools/generate_parity_fixtures.py writes exactly what these tests check.
    import sys

    sys.path.insert(0, str(Path(__file__).resolve().parents[1] / "tools"))
    from generate_parity_fixtures import expected_files

    for path, content in expected_files(PARITY).items():
        assert json.loads(path.read_text(encoding="utf-8")) == json.loads(content), path


@pytest.mark.parametrize("fixture", sorted(FIXTURES.glob("*.csv")), ids=str)
def test_fixtures_use_only_synthetic_account_identifiers(fixture):
    text = fixture.read_text(encoding="utf-8")

    for account in ACCOUNT_LIKE.findall(text):
        assert SYNTHETIC_ACCOUNT.match(account), account
    for line in text.splitlines():
        if line.startswith("Account Information,Data,Name,"):
            assert line.endswith("Synthetic Test User")
