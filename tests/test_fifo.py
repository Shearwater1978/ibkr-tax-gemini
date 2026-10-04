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


def _action(kind, qty, ticker, isin, tail, date="2026-07-01"):
    return {
        "type": kind,
        "date": date,
        "ticker": ticker,
        "qty": Decimal(qty),
        "price": Decimal("0"),
        "commission": Decimal("0"),
        "currency": "USD",
        "rate": Decimal("4"),
        "isin": isin,
        "description": f"XOM(OLD) CUSIP/ISIN Change to (NEW) ({tail})",
    }


def _buy(date, qty, price):
    return {
        "type": "BUY",
        "date": date,
        "ticker": "XOM",
        "qty": Decimal(qty),
        "price": Decimal(price),
        "commission": Decimal("0"),
        "currency": "USD",
        "rate": Decimal("4"),
        "isin": "OLD",
    }


def test_isin_change_carries_cost_and_purchase_date(matcher):
    matcher.process_trades(
        [
            _buy("2024-01-10", "2", "100"),
            _buy("2024-02-10", "1", "110"),
            _action("STOCK_DIV", "3", "XOM", "NEW", "XOM, NEW CO, NEW"),
            _action("MERGER", "-3", "XOM", "OLD", "X.OLD, OLD CO, OLD"),
        ]
    )
    lots = sorted(matcher.get_current_inventory(), key=lambda lot: lot["buy_date"])
    assert [(lot["buy_date"], lot["quantity"], lot["total_cost"]) for lot in lots] == [
        ("2024-01-10", 2.0, 800.0),
        ("2024-02-10", 1.0, 440.0),
    ]
    assert {lot["isin"] for lot in lots} == {"NEW"}


def test_exchange_ratio_changes_quantity_not_cost(matcher):
    matcher.process_trades(
        [
            _buy("2024-01-10", "10", "100"),
            _action("STOCK_DIV", "3.44", "XOM", "NEW", "XOM, NEW CO, NEW"),
            _action("MERGER", "-10", "XOM", "OLD", "X.OLD, OLD CO, OLD"),
        ]
    )
    (lot,) = matcher.get_current_inventory()
    assert lot["quantity"] == 3.44
    assert lot["total_cost"] == 4000.0


def test_corporate_action_without_removal_stays_zero_cost(matcher):
    matcher.process_trades(
        [_action("STOCK_DIV", "1.5", "XOM", "NEW", "XOM, NEW CO, NEW")]
    )
    (lot,) = matcher.get_current_inventory()
    assert lot["total_cost"] == 0.0
