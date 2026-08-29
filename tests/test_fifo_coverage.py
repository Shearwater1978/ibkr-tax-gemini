from collections import deque
from decimal import Decimal

import pytest

from src.fifo_coverage import PlannedSale, check_coverage
from src.fifo import TradeMatcher


def row(event_type, date, ticker="AAPL", quantity=0, **extra):
    return {
        "TradeId": extra.pop("TradeId", 1),
        "Date": date,
        "EventType": event_type,
        "Ticker": ticker,
        "Quantity": quantity,
        "Currency": "PLN",
        "Price": 0,
        "Fee": 0,
        **extra,
    }


@pytest.mark.parametrize(
    "kwargs",
    [
        {"ticker": "", "quantity": 1, "as_of": "2024-01-01"},
        {"ticker": "AAPL", "quantity": 0, "as_of": "2024-01-01"},
        {"ticker": "AAPL", "quantity": 1, "as_of": "2024-02-30"},
    ],
)
def test_planned_sale_rejects_invalid_input(kwargs):
    with pytest.raises(ValueError):
        PlannedSale(**kwargs)


def test_coverage_traces_oldest_lots_and_does_not_mutate_rows():
    rows = [
        row("BUY", "2024-01-01", quantity=5, TradeId=10),
        row("BUY", "2024-02-01", quantity=7, TradeId=11),
    ]
    original_rows = [record.copy() for record in rows]

    result = check_coverage(rows, [PlannedSale("AAPL", 9, "2024-03-01")])

    assert result["status"] == "COVERED"
    coverage = result["results"][0]
    assert coverage["available"] == 12.0
    assert coverage["missing"] == 0.0
    assert [(lot["acquisition_date"], lot["quantity"]) for lot in coverage["lots"]] == [
        ("2024-01-01", 5.0),
        ("2024-02-01", 4.0),
    ]
    assert rows == original_rows
    assert not any(record["EventType"] == "SELL" for record in rows)


def test_split_changes_available_quantity_and_partial_status():
    rows = [
        row("BUY", "2024-01-01", ticker="NVDA", quantity=10),
        row("SPLIT", "2024-02-01", ticker="NVDA", SplitRatio=4),
    ]

    result = check_coverage(rows, [PlannedSale("NVDA", 45, "2024-03-01")])

    assert result["results"][0]["status"] == "PARTIAL"
    assert result["results"][0]["available"] == 40.0
    assert result["results"][0]["missing"] == 5.0
    assert result["results"][0]["lots"][0]["quantity"] == 40.0


def test_coverage_ignores_rows_with_blank_or_invalid_tickers():
    rows = [
        row("BUY", "2024-01-01", ticker="AAPL", quantity=5),
        {**row("BUY", "2024-01-02", ticker="", quantity=7), "Ticker": ""},
        {**row("BUY", "2024-01-03", ticker="MSFT", quantity=2), "Ticker": None},
    ]

    result = check_coverage(rows, [PlannedSale("AAPL", 5, "2024-02-01")])

    assert result["status"] == "COVERED"
    assert result["results"][0]["available"] == 5.0
    assert result["results"][0]["missing"] == 0.0


def test_alias_and_empty_history_are_explicit():
    result = check_coverage(
        [row("BUY", "2024-01-01", ticker="FB", quantity=3)],
        [
            PlannedSale("META", Decimal("3"), "2024-01-02"),
            PlannedSale("MSFT", 1, "2024-01-02"),
        ],
    )

    assert result["status"] == "INCOMPLETE"
    assert result["results"][0]["status"] == "COVERED"
    assert result["results"][1]["status"] == "NOT_COVERED"
    assert result["results"][1]["history_found"] is False


def test_coverage_matches_normal_fifo_inventory():
    events = [
        row("BUY", "2024-01-01", quantity=10),
        row("BUY", "2024-02-01", quantity=4),
        row("SELL", "2024-03-01", quantity=-3),
    ]
    matcher = TradeMatcher()
    matcher.process_trades(
        [
            {
                "type": record["EventType"],
                "date": record["Date"],
                "ticker": record["Ticker"],
                "qty": Decimal(str(record["Quantity"])),
                "price": Decimal(0),
                "commission": Decimal(0),
                "currency": "PLN",
                "rate": Decimal(1),
            }
            for record in events
        ]
    )

    result = check_coverage(events, [PlannedSale("AAPL", 8, "2024-04-01")])

    assert result["results"][0]["available"] == sum(
        item["quantity"] for item in matcher.get_current_inventory()
    )
    assert [lot["acquisition_date"] for lot in result["results"][0]["lots"]] == [
        "2024-01-01",
        "2024-02-01",
    ]
