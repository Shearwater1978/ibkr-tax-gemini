from decimal import Decimal

import pytest

from src.db_connector import DBConnector, DBConnectorError
from src.parser import extract_split_ratio, parse_csv, save_to_database


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


def test_isin_remap_import_calculation_and_report_end_to_end(
    encrypted_database, tmp_path
):
    from main import prepare_data_for_pdf
    from src.data_collector import collect_all_trade_data
    from src.processing import process_yearly_data
    from src.report_pdf import generate_pdf

    trades = [
        {
            **trade_record(),
            "date": "2024-01-02",
            "ticker": "OKE",
            "isin": "OLD-ISIN",
            "conid": "10794",
            "instrument_description": "OLD ONEOK",
            "source": "old identity buy",
        },
        {
            **trade_record(),
            "date": "2025-03-04",
            "ticker": "OKE",
            "isin": "NEW-ISIN",
            "conid": "99999",
            "instrument_description": "NEW INSTRUMENT",
            "qty": Decimal("2"),
            "source": "new identity buy",
        },
        {
            **trade_record(),
            "date": "2025-03-05",
            "type": "SELL",
            "ticker": "OKE",
            "isin": "NEW-ISIN",
            "conid": "99999",
            "instrument_description": "NEW INSTRUMENT",
            "qty": Decimal("-1"),
            "price": Decimal("150"),
            "source": "new identity sale",
        },
    ]
    save_to_database(
        {"trades": trades, "dividends": [], "taxes": [], "corp_actions": []}
    )

    with DBConnector(encrypted_database, key="test-key") as db:
        rows = db.get_trades_for_calculation(target_year=2025)

    realized, dividends, inventory, diagnostics = process_yearly_data(
        rows, 2025, include_diagnostics=True
    )
    sheets, ticker_summary = collect_all_trade_data(
        realized, dividends, inventory, diagnostics
    )
    pdf_data = prepare_data_for_pdf(
        2025, rows, realized, dividends, inventory, diagnostics
    )

    assert [(lot["ticker"], lot["isin"]) for lot in inventory] == [
        ("OKE", "OLD-ISIN"),
        ("OKE", "NEW-ISIN"),
    ]
    assert sheets["Open Positions"]["ISIN"].tolist() == ["OLD-ISIN", "NEW-ISIN"]
    assert list(ticker_summary) == ["OKE"]
    assert pdf_data["data"]["identity_changes"][0]["date"] == "2025-03-04"
    assert len(pdf_data["data"]["holdings"]) == 1

    report_path = tmp_path / "isin-remap.pdf"
    generate_pdf(pdf_data, str(report_path))
    assert report_path.is_file()


def test_added_identity_columns_allow_legacy_query_and_calculation(encrypted_database):
    save_to_database(
        {
            "trades": [trade_record()],
            "dividends": [],
            "taxes": [],
            "corp_actions": [],
        }
    )
    with DBConnector(encrypted_database, key="test-key") as db:
        legacy_rows = [
            dict(row)
            for row in db.conn.execute(
                "SELECT rowid as TradeId, Date, EventType, Ticker, Quantity, "
                "Price, Currency, Amount, Fee, Description, SplitRatio "
                "FROM transactions ORDER BY Date"
            ).fetchall()
        ]

    from src.processing import process_yearly_data

    _, _, inventory = process_yearly_data(legacy_rows, 2024)
    assert inventory[0]["ticker"] == "AAPL"
    assert inventory[0]["quantity"] == 1.0
    assert "isin" not in inventory[0]


def test_ogn_spinoff_lot_matches_sale_under_child_isin(encrypted_database, monkeypatch):
    from src.processing import process_yearly_data

    monkeypatch.setattr(
        "src.processing.get_nbp_rate", lambda currency, trade_date: Decimal("1")
    )
    statement = parse_csv("data/U5801_20210315_20220107.csv")
    data = {
        "trades": [
            record for record in statement["trades"] if record["ticker"] == "OGN"
        ],
        "corp_actions": [
            record for record in statement["corp_actions"] if record["ticker"] == "OGN"
        ],
        "dividends": [],
        "taxes": [],
    }
    save_to_database(data)

    with DBConnector(encrypted_database, key="test-key") as db:
        rows = db.get_trades_for_calculation(target_year=2021, ticker="OGN")

    assert {row["ISIN"] for row in rows} == {"US68622V1061"}
    realized, _, _, _ = process_yearly_data(rows, 2021, include_diagnostics=True)
    assert len(realized) == 1
    assert realized[0]["matched_buys"][0]["date"] == "2021-06-02"


def test_sber_adr_tender_uses_adr_purchase_not_common_share_identity(
    encrypted_database, monkeypatch
):
    from src.processing import process_yearly_data

    rate_calls = []
    monkeypatch.setattr(
        "src.processing.get_nbp_rate",
        lambda currency, trade_date: (
            rate_calls.append((currency, trade_date)) or Decimal("1")
        ),
    )
    statement = parse_csv("data/U1601_U7701_20220103_20221230.csv")
    selected_tickers = {"SBER", "SBER.CNV4"}
    save_to_database(
        {
            "trades": [
                record
                for record in statement["trades"]
                if record["ticker"] in selected_tickers
            ],
            "corp_actions": [
                record
                for record in statement["corp_actions"]
                if record["ticker"] in selected_tickers
            ],
            "dividends": [],
            "taxes": [],
        }
    )

    with DBConnector(encrypted_database, key="test-key") as db:
        rows = db.get_trades_for_calculation(target_year=2022)

    sber_buy = next(
        row for row in rows if row["Ticker"] == "SBER" and row["EventType"] == "BUY"
    )
    assert sber_buy["ISIN"] == "US80585Y3080"
    _, _, _, _ = process_yearly_data(rows, 2022, include_diagnostics=True)
    assert ("RUB", "2022-05-24") not in rate_calls
