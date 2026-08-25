from decimal import Decimal

import pytest

from src.db_connector import DBConnector, DBConnectorError
from src.parser import extract_split_ratio, save_to_database


@pytest.fixture
def encrypted_database(monkeypatch, tmp_path):
    pytest.importorskip("sqlcipher3")
    import src.db_connector as db_connector

    database_path = str(tmp_path / "transactions.db")
    monkeypatch.setattr(db_connector, "DB_PATH", database_path)
    monkeypatch.setattr(db_connector, "DB_KEY", "test-key")
    monkeypatch.setattr("src.parser.MANUAL_FIXES_FILE", str(tmp_path / "missing.csv"))
    return database_path


def trade_record():
    return {
        "date": "2024-01-02",
        "type": "BUY",
        "ticker": "AAPL",
        "qty": Decimal("1"),
        "price": Decimal("100"),
        "currency": "PLN",
        "commission": Decimal("0"),
        "source": "IBKR trade",
    }


def test_import_is_idempotent_and_preserves_existing_rows(encrypted_database):
    data = {
        "trades": [trade_record()],
        "dividends": [],
        "taxes": [],
        "corp_actions": [],
    }

    first = save_to_database(data)
    second = save_to_database(data)

    assert first == {"inserted": 1, "skipped": 0}
    assert second == {"inserted": 0, "skipped": 1}

    with DBConnector(encrypted_database, key="test-key") as db:
        rows = db.get_trades_for_calculation()
    assert len(rows) == 1


def test_invalid_batch_does_not_delete_existing_rows(encrypted_database):
    save_to_database(
        {"trades": [trade_record()], "dividends": [], "taxes": [], "corp_actions": []}
    )
    invalid = trade_record()
    invalid["ticker"] = ""

    with pytest.raises(ValueError, match="missing date, ticker, or currency"):
        save_to_database(
            {"trades": [invalid], "dividends": [], "taxes": [], "corp_actions": []}
        )

    with DBConnector(encrypted_database, key="test-key") as db:
        assert len(db.get_trades_for_calculation()) == 1


def test_split_ratio_is_parsed():
    assert extract_split_ratio("WMT Split 3 for 1") == Decimal("3")
    assert extract_split_ratio("GE Split 1 for 8") == Decimal("0.125")
    assert extract_split_ratio("SCCO Stock Dividend 73 for 10000") is None


def test_split_ratio_reaches_fifo(encrypted_database):
    data = {
        "trades": [trade_record()],
        "dividends": [],
        "taxes": [],
        "corp_actions": [
            {
                "date": "2024-02-01",
                "type": "SPLIT",
                "ticker": "AAPL",
                "qty": Decimal("2"),
                "price": Decimal("0"),
                "currency": "PLN",
                "commission": Decimal("0"),
                "ratio": Decimal("2"),
                "source": "AAPL Split 2 for 1",
            }
        ],
    }
    save_to_database(data)

    with DBConnector(encrypted_database, key="test-key") as db:
        rows = db.get_trades_for_calculation(target_year=2024)

    from src.processing import process_yearly_data

    _, _, inventory = process_yearly_data(rows, 2024)
    assert inventory[0]["quantity"] == 2.0
    assert inventory[0]["cost_per_share"] == 50.0
