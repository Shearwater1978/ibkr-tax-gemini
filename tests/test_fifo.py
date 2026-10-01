# tests/test_fifo.py

import pytest
from decimal import Decimal
from src.fifo import TradeMatcher
from src.diagnostics import UnmatchedInventoryError


@pytest.fixture
def matcher():
    return TradeMatcher()


def test_fifo_simple_profit(matcher):
    """Buy low, sell high."""
    trades = [
        {
            "type": "BUY",
            "date": "2024-01-01",
            "ticker": "AAPL",
            "qty": Decimal(10),
            "price": Decimal(100),
            "commission": Decimal(5),
            "currency": "USD",
            "rate": Decimal(4.0),
        },
        # FIX: SELL quantity must be negative for the engine to recognize it as a disposal
        {
            "type": "SELL",
            "date": "2024-01-02",
            "ticker": "AAPL",
            "qty": Decimal(-5),
            "price": Decimal(150),
            "commission": Decimal(5),
            "currency": "USD",
            "rate": Decimal(4.0),
        },
    ]
    matcher.process_trades(trades)
    results = matcher.get_realized_gains()

    assert len(results) == 1
    res = results[0]

    # Revenue: 5 * 150 * 4.0 = 3000
    assert res["sale_amount"] == 3000.0

    # Cost: (5 * 100 * 4.0) + (Half Buy Comm: 2.5 * 4.0 = 10) + (Full Sell Comm: 5 * 4.0 = 20)
    # Cost Basis = 2000 (stock) + 10 (buy comm) + 20 (sell comm) = 2030
    assert res["cost_basis"] == 2030.0

    # Profit: 3000 - 2030 = 970
    assert res["profit_loss"] == 970.0


def test_fifo_multiple_buys(matcher):
    """Sell consumes first buy completely and part of second buy."""
    trades = [
        {
            "type": "BUY",
            "date": "2024-01-01",
            "ticker": "AAPL",
            "qty": Decimal(10),
            "price": Decimal(100),
            "commission": Decimal(0),
            "currency": "USD",
            "rate": Decimal(1.0),
        },
        {
            "type": "BUY",
            "date": "2024-01-02",
            "ticker": "AAPL",
            "qty": Decimal(10),
            "price": Decimal(200),
            "commission": Decimal(0),
            "currency": "USD",
            "rate": Decimal(1.0),
        },
        # FIX: SELL quantity must be negative
        {
            "type": "SELL",
            "date": "2024-01-03",
            "ticker": "AAPL",
            "qty": Decimal(-15),
            "price": Decimal(300),
            "commission": Decimal(0),
            "currency": "USD",
            "rate": Decimal(1.0),
        },
    ]
    matcher.process_trades(trades)
    results = matcher.get_realized_gains()

    assert len(results) == 1
    res = results[0]

    # Revenue: 15 * 300 = 4500
    assert res["sale_amount"] == 4500.0

    # Cost: (10 * 100) + (5 * 200) = 1000 + 1000 = 2000
    assert res["cost_basis"] == 2000.0

    # Profit: 4500 - 2000 = 2500
    assert res["profit_loss"] == 2500.0


def test_fifo_rejects_sell_exceeding_inventory(matcher):
    trades = [
        {
            "type": "BUY",
            "date": "2024-01-01",
            "ticker": "AAPL",
            "qty": Decimal(1),
            "price": Decimal(100),
            "commission": Decimal(0),
            "currency": "PLN",
            "rate": Decimal(1),
        },
        {
            "type": "SELL",
            "date": "2024-01-02",
            "ticker": "AAPL",
            "qty": Decimal(-2),
            "price": Decimal(150),
            "commission": Decimal(0),
            "currency": "PLN",
            "rate": Decimal(1),
        },
    ]

    with pytest.raises(UnmatchedInventoryError) as error:
        matcher.process_trades(trades)

    assert error.value.diagnostic.code == "UNMATCHED_SELL"
    assert error.value.diagnostic.quantity == 1.0


def test_fifo_matches_and_retains_lots_by_isin(matcher):
    matcher.process_trades(
        [
            {
                "type": "BUY",
                "date": "2024-01-01",
                "ticker": "OKE",
                "isin": "OLD-ISIN",
                "qty": Decimal(10),
                "price": Decimal(10),
                "commission": Decimal(0),
                "currency": "PLN",
                "rate": Decimal(1),
            },
            {
                "type": "BUY",
                "date": "2025-03-04",
                "ticker": "OKE",
                "isin": "NEW-ISIN",
                "qty": Decimal(3),
                "price": Decimal(20),
                "commission": Decimal(0),
                "currency": "PLN",
                "rate": Decimal(1),
            },
            {
                "type": "SELL",
                "date": "2025-03-05",
                "ticker": "OKE",
                "isin": "NEW-ISIN",
                "qty": Decimal(-2),
                "price": Decimal(30),
                "commission": Decimal(0),
                "currency": "PLN",
                "rate": Decimal(1),
            },
        ]
    )

    gain = matcher.get_realized_gains()[0]
    inventory = matcher.get_current_inventory()
    assert gain["ticker"] == "OKE"
    assert gain["isin"] == "NEW-ISIN"
    assert gain["matched_buys"][0]["isin"] == "NEW-ISIN"
    assert [(item["isin"], item["quantity"]) for item in inventory] == [
        ("OLD-ISIN", 10.0),
        ("NEW-ISIN", 1.0),
    ]


def test_fifo_insufficient_inventory_names_identity(matcher):
    matcher.process_trades(
        [
            {
                "type": "BUY",
                "date": "2024-01-01",
                "ticker": "OKE",
                "isin": "OLD-ISIN",
                "qty": Decimal(10),
                "price": Decimal(10),
                "commission": Decimal(0),
                "currency": "PLN",
                "rate": Decimal(1),
            }
        ]
    )

    with pytest.raises(UnmatchedInventoryError) as error:
        matcher.process_trades(
            [
                {
                    "type": "SELL",
                    "date": "2025-03-05",
                    "ticker": "OKE",
                    "isin": "NEW-ISIN",
                    "qty": Decimal(-1),
                    "price": Decimal(30),
                    "commission": Decimal(0),
                    "currency": "PLN",
                    "rate": Decimal(1),
                }
            ]
        )

    assert error.value.diagnostic.ticker == "OKE"
    assert error.value.diagnostic.isin == "NEW-ISIN"
    assert "NEW-ISIN" in error.value.diagnostic.message


def test_splits_and_transfers_only_change_their_identity(matcher):
    matcher.process_trades(
        [
            {
                "type": "BUY",
                "date": "2024-01-01",
                "ticker": "OKE",
                "isin": "OLD-ISIN",
                "qty": Decimal(5),
                "price": Decimal(10),
                "commission": Decimal(0),
                "currency": "PLN",
                "rate": Decimal(1),
            },
            {
                "type": "BUY",
                "date": "2024-01-02",
                "ticker": "OKE",
                "isin": "NEW-ISIN",
                "qty": Decimal(2),
                "price": Decimal(20),
                "commission": Decimal(0),
                "currency": "PLN",
                "rate": Decimal(1),
            },
            {
                "type": "STOCK_DIV",
                "date": "2024-01-03",
                "ticker": "OKE",
                "isin": "NEW-ISIN",
                "qty": Decimal(1),
                "price": Decimal(0),
                "commission": Decimal(0),
                "currency": "PLN",
                "rate": Decimal(1),
            },
            {
                "type": "SPLIT",
                "date": "2024-02-01",
                "ticker": "OKE",
                "isin": "OLD-ISIN",
                "qty": Decimal(1),
                "ratio": Decimal(2),
                "price": Decimal(0),
                "commission": Decimal(0),
                "currency": "PLN",
                "rate": Decimal(1),
            },
            {
                "type": "TRANSFER",
                "date": "2024-03-01",
                "ticker": "OKE",
                "isin": "OLD-ISIN",
                "qty": Decimal(-1),
                "price": Decimal(0),
                "commission": Decimal(0),
                "currency": "PLN",
                "rate": Decimal(1),
            },
        ]
    )

    inventory = matcher.get_current_inventory()
    assert [
        (item["isin"], item["buy_date"], item["quantity"]) for item in inventory
    ] == [
        ("OLD-ISIN", "2024-01-01", 9.0),
        ("NEW-ISIN", "2024-01-02", 2.0),
        ("NEW-ISIN", "2024-01-03", 1.0),
    ]
