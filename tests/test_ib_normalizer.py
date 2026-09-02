# tests/test_ib_normalizer.py

import datetime
import sqlite3
from decimal import Decimal

from src.ib_normalizer import (
    normalize_fill,
    normalize_snapshot,
    normalize_web_snapshot,
    normalize_web_trade,
)
from src.parser import save_to_database


def _make_fill(**overrides):
    fill = {
        "account": "DU123",
        "symbol": "AAPL",
        "currency": "USD",
        "side": "BOT",
        "shares": 10.0,
        "price": 190.5,
        "commission": 1.25,
        "exec_id": "exec-1",
        "time": datetime.datetime(2026, 8, 29, 14, 30, 0),
    }
    fill.update(overrides)
    return fill


def _make_web_trade(**overrides):
    trade = {
        "execution_id": "web-exec-1",
        "symbol": "AAPL",
        "side": "B",
        "size": 10,
        "price": 190.5,
        "currency": "USD",
        "commission": 1.25,
        "trade_time": "20260829-14:30:00",
    }
    trade.update(overrides)
    return trade


def test_normalize_fill_buy_maps_to_trade_schema():
    record = normalize_fill(_make_fill())

    assert record["ticker"] == "AAPL"
    assert record["currency"] == "USD"
    assert record["date"] == "2026-08-29"
    assert record["qty"] == Decimal("10.0")
    assert record["price"] == Decimal("190.5")
    assert record["commission"] == Decimal("1.25")
    assert record["type"] == "BUY"
    assert record["source_file"] == "ib_live_api"


def test_normalize_fill_sell_produces_negative_qty():
    record = normalize_fill(_make_fill(side="SLD", shares=4.0))

    assert record["type"] == "SELL"
    assert record["qty"] == Decimal("-4.0")


def test_normalize_fill_string_time_is_parsed():
    record = normalize_fill(_make_fill(time="20260829  14:30:00"))
    assert record["date"] == "2026-08-29"


def test_normalize_fill_missing_required_field_returns_none():
    assert normalize_fill(_make_fill(symbol=None)) is None
    assert normalize_fill(_make_fill(currency=None)) is None
    assert normalize_fill(_make_fill(time=None)) is None
    assert normalize_fill(_make_fill(exec_id=None)) is None


def test_normalize_web_trade_maps_to_common_trade_schema():
    record = normalize_web_trade(
        {
            "execution_id": "web-exec-1",
            "symbol": "AAPL",
            "side": "B",
            "size": "10",
            "price": "190.50",
            "currency": "USD",
            "commission": "1.25",
            "trade_time": "20260829-14:30:00",
        }
    )

    assert record == {
        "ticker": "AAPL",
        "currency": "USD",
        "date": "2026-08-29",
        "qty": Decimal("10"),
        "price": Decimal("190.50"),
        "commission": Decimal("1.25"),
        "type": "BUY",
        "source": "IB Web Trade web-exec-1",
        "source_file": "ib_web_api",
    }


def test_normalize_web_trade_skips_cash_fx_conversions():
    trade = {
        "execution_id": "web-exec-fx",
        "symbol": "USD",
        "side": "B",
        "size": "938",
        "price": "3.7313",
        "currency": "PLN",
        "commission": "7.49",
        "trade_time": "20260831-09:12:32",
        "sec_type": "CASH",
    }
    assert normalize_web_trade(trade) is None


def test_normalize_web_snapshot_returns_the_common_import_shape():
    normalized = normalize_web_snapshot(
        {
            "trades": [
                {
                    "execution_id": "web-exec-1",
                    "symbol": "AAPL",
                    "side": "S",
                    "size": 4,
                    "price": 200,
                    "currency": "USD",
                    "commission": 1,
                    "trade_time": "2026-08-29 14:30:00",
                }
            ],
            "positions": [],
        }
    )

    assert normalized["trades"][0]["type"] == "SELL"
    assert normalized["trades"][0]["qty"] == Decimal("-4")
    assert normalized["dividends"] == []
    assert normalized["taxes"] == []
    assert normalized["corp_actions"] == []


def test_normalize_snapshot_skips_invalid_fills_and_fills_other_lists():
    snapshot = {
        "account_summary": [{"account": "DU123", "tag": "NetLiquidation"}],
        "positions": [{"symbol": "AAPL", "position": 10}],
        "fills": [_make_fill(), _make_fill(symbol=None)],
        "open_orders": [],
    }

    normalized = normalize_snapshot(snapshot)

    assert len(normalized["trades"]) == 1
    assert normalized["dividends"] == []
    assert normalized["taxes"] == []
    assert normalized["corp_actions"] == []


def test_normalized_snapshot_is_compatible_with_save_to_database(mocker):
    mock_db_connector = mocker.patch("src.parser.DBConnector")
    mock_conn = mock_db_connector.return_value.__enter__.return_value.conn
    mock_conn.execute.return_value.fetchall.return_value = []
    mock_conn.execute.return_value.fetchone.side_effect = [(0,), (1,)]

    snapshot = {"fills": [_make_fill()]}
    normalized = normalize_snapshot(snapshot)

    result = save_to_database(normalized)

    assert result["inserted"] == 1
    assert mock_conn.executemany.called


def test_duplicate_live_fill_is_not_reinserted_on_repeat_sync(mocker):
    """A real (in-memory) DB enforces the SourceKey unique index, so this
    verifies duplicate live syncs of the same execution do not double-insert
    trades, not just that the mocked call count looks right."""
    real_conn = sqlite3.connect(":memory:")
    real_conn.row_factory = sqlite3.Row

    mock_db_connector = mocker.patch("src.parser.DBConnector")
    mock_instance = mock_db_connector.return_value.__enter__.return_value
    mock_instance.conn = real_conn
    mock_instance.initialize_schema = lambda: _init_schema(real_conn)

    snapshot = {"fills": [_make_fill()]}
    normalized = normalize_snapshot(snapshot)

    first = save_to_database(normalized)
    second = save_to_database(normalized)

    total_rows = real_conn.execute("SELECT COUNT(*) FROM transactions").fetchone()[0]
    assert first["inserted"] == 1
    assert second["inserted"] == 0
    assert total_rows == 1


def test_distinct_fills_with_same_price_and_qty_are_both_inserted(mocker):
    """Two different executions that otherwise share date/ticker/qty/price
    must both be kept: exec_id in the source text must differentiate them."""
    real_conn = sqlite3.connect(":memory:")
    real_conn.row_factory = sqlite3.Row

    mock_db_connector = mocker.patch("src.parser.DBConnector")
    mock_instance = mock_db_connector.return_value.__enter__.return_value
    mock_instance.conn = real_conn
    mock_instance.initialize_schema = lambda: _init_schema(real_conn)

    snapshot = {"fills": [_make_fill(exec_id="exec-1"), _make_fill(exec_id="exec-2")]}
    normalized = normalize_snapshot(snapshot)

    result = save_to_database(normalized)

    total_rows = real_conn.execute("SELECT COUNT(*) FROM transactions").fetchone()[0]
    assert result["inserted"] == 2
    assert total_rows == 2


def test_duplicate_web_trade_is_not_reinserted_on_repeat_sync(mocker):
    real_conn = sqlite3.connect(":memory:")
    real_conn.row_factory = sqlite3.Row

    mock_db_connector = mocker.patch("src.parser.DBConnector")
    mock_instance = mock_db_connector.return_value.__enter__.return_value
    mock_instance.conn = real_conn
    mock_instance.initialize_schema = lambda: _init_schema(real_conn)

    normalized = normalize_web_snapshot({"trades": [_make_web_trade()]})
    first = save_to_database(normalized)
    second = save_to_database(normalized)

    total_rows = real_conn.execute("SELECT COUNT(*) FROM transactions").fetchone()[0]
    assert normalized["trades"][0]["source"] == "IB Web Trade web-exec-1"
    assert first["inserted"] == 1
    assert second["inserted"] == 0
    assert total_rows == 1


def _init_schema(conn):
    conn.execute("""
        CREATE TABLE IF NOT EXISTS transactions (
            id INTEGER PRIMARY KEY AUTOINCREMENT,
            Date TEXT,
            EventType TEXT,
            Ticker TEXT,
            Quantity REAL,
            Price REAL,
            Currency TEXT,
            Amount REAL,
            Fee REAL,
            Description TEXT,
            SourceKey TEXT,
            SplitRatio REAL
        );
        """)
    conn.execute(
        "CREATE UNIQUE INDEX IF NOT EXISTS idx_transactions_source_key "
        "ON transactions(SourceKey) WHERE SourceKey IS NOT NULL"
    )
    conn.commit()


def test_normalized_live_trades_produce_same_fifo_result_as_csv_shape(mocker):
    """Feeds normalize_snapshot() output straight into the FIFO engine to
    confirm live-sourced trades are matched identically to CSV-sourced
    trades; the tax engine must not need any live-specific branching."""
    from src.fifo import TradeMatcher

    mocker.patch("src.fifo.get_rate_for_tax_date", return_value=Decimal("4.00"))

    buy_fill = _make_fill(
        side="BOT",
        shares=10.0,
        price=150.0,
        commission=1.0,
        exec_id="exec-buy",
        time=datetime.datetime(2026, 1, 10, 15, 0, 0),
    )
    sell_fill = _make_fill(
        side="SLD",
        shares=4.0,
        price=190.0,
        commission=1.0,
        exec_id="exec-sell",
        time=datetime.datetime(2026, 6, 1, 15, 0, 0),
    )
    normalized = normalize_snapshot({"fills": [buy_fill, sell_fill]})

    matcher = TradeMatcher()
    matcher.process_trades(normalized["trades"])

    assert len(matcher.realized_pnl) == 1
    sale = matcher.realized_pnl[0]
    assert sale["quantity"] == 4.0
    assert matcher.inventory["AAPL"][0]["qty"] == Decimal("6.0")
