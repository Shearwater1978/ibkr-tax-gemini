import pytest
from decimal import Decimal
from unittest.mock import patch
from src.processing import process_yearly_data


@pytest.fixture
def mock_trades_db():
    # Mock data structure mimicking SQLite rows (PascalCase keys)
    return [
        # Dividend
        {
            "TradeId": 1,
            "Date": "2025-01-02",
            "EventType": "DIVIDEND",
            "Ticker": "AAPL",
            "Quantity": 0,
            "Price": 0,
            "Amount": 10.0,
            "Fee": 0,
            "Currency": "USD",
        },
        # Associated Tax
        {
            "TradeId": 2,
            "Date": "2025-01-02",
            "EventType": "TAX",
            "Ticker": "AAPL",
            "Quantity": 0,
            "Price": 0,
            "Amount": -1.5,
            "Fee": 0,
            "Currency": "USD",
        },
        # Trade (Buy)
        {
            "TradeId": 3,
            "Date": "2025-01-05",
            "EventType": "BUY",
            "Ticker": "AAPL",
            "Quantity": 1,
            "Price": 100,
            "Amount": -100,
            "Fee": -1,
            "Currency": "USD",
        },
    ]


@patch("src.processing.get_nbp_rate")
def test_processing_flow(mock_rate, mock_trades_db):
    # Fix NBP rate to ensure predictable math
    mock_rate.return_value = Decimal("4.0")

    realized, dividends, inventory = process_yearly_data(mock_trades_db, 2025)

    # 1. Check Dividends
    assert len(dividends) == 1
    div = dividends[0]
    # Gross: 10 * 4.0 = 40.0
    assert div["gross_amount_pln"] == 40.0
    # Tax: 1.5 * 4.0 = 6.0
    assert div["tax_withheld_pln"] == 6.0

    # 2. Check Inventory
    assert len(inventory) == 1
    assert inventory[0]["ticker"] == "AAPL"


@patch("src.processing.get_nbp_rate", return_value=Decimal("1"))
def test_processing_resolves_legacy_rows_and_reports_identity_boundary(mock_rate):
    rows = [
        {
            "TradeId": 1,
            "Date": "2023-01-01",
            "EventType": "BUY",
            "Ticker": "OKE",
            "ISIN": "",
            "Quantity": 2,
            "Price": 5,
            "Currency": "PLN",
            "Fee": 0,
            "Amount": 10,
        },
        {
            "TradeId": 2,
            "Date": "2024-01-01",
            "EventType": "BUY",
            "Ticker": "OKE",
            "ISIN": "OLD-ISIN",
            "Quantity": 5,
            "Price": 10,
            "Currency": "PLN",
            "Fee": 0,
            "Amount": 50,
        },
        {
            "TradeId": 3,
            "Date": "2025-03-04",
            "EventType": "BUY",
            "Ticker": "OKE",
            "ISIN": "NEW-ISIN",
            "Quantity": 3,
            "Price": 20,
            "Currency": "PLN",
            "Fee": 0,
            "Amount": 60,
        },
        {
            "TradeId": 4,
            "Date": "2025-03-05",
            "EventType": "SELL",
            "Ticker": "OKE",
            "ISIN": "NEW-ISIN",
            "Quantity": -2,
            "Price": 30,
            "Currency": "PLN",
            "Fee": 0,
            "Amount": 60,
        },
    ]

    realized, _, inventory, diagnostics = process_yearly_data(
        rows, 2025, include_diagnostics=True
    )

    assert realized[0]["isin"] == "NEW-ISIN"
    assert realized[0]["matched_buys"][0]["isin"] == "NEW-ISIN"
    assert [
        (item["isin"], item["buy_date"], item["quantity"]) for item in inventory
    ] == [
        ("OLD-ISIN", "2023-01-01", 2.0),
        ("OLD-ISIN", "2024-01-01", 5.0),
        ("NEW-ISIN", "2025-03-04", 1.0),
    ]
    assert len(diagnostics) == 1
    assert diagnostics[0].code == "IDENTITY_CHANGE"
    assert diagnostics[0].previous_isin == "OLD-ISIN"
    assert diagnostics[0].new_isin == "NEW-ISIN"
    assert diagnostics[0].date == "2025-03-04"


@patch("src.processing.get_nbp_rate", return_value=Decimal("1"))
def test_processing_emits_no_identity_diagnostic_for_single_isin(mock_rate):
    row = {
        "TradeId": 1,
        "Date": "2024-01-01",
        "EventType": "BUY",
        "Ticker": "AAPL",
        "ISIN": "US0378331005",
        "Quantity": 1,
        "Price": 10,
        "Currency": "PLN",
        "Fee": 0,
        "Amount": 10,
    }

    _, _, inventory, diagnostics = process_yearly_data(
        [row], 2024, include_diagnostics=True
    )

    assert diagnostics == []
    assert inventory[0]["ticker"] == "AAPL"
    assert "isin" not in inventory[0]


@patch("src.processing.get_nbp_rate", return_value=Decimal("1"))
def test_dividend_and_tax_records_remain_attributed_to_each_identity(mock_rate):
    rows = []
    for trade_id, isin, amount, tax in (
        (1, "OLD-ISIN", 100, -10),
        (3, "NEW-ISIN", 200, -20),
    ):
        rows.extend(
            [
                {
                    "TradeId": trade_id,
                    "Date": "2025-01-02",
                    "EventType": "DIVIDEND",
                    "Ticker": "OKE",
                    "ISIN": isin,
                    "Quantity": 0,
                    "Price": 0,
                    "Amount": amount,
                    "Fee": 0,
                    "Currency": "PLN",
                },
                {
                    "TradeId": trade_id + 1,
                    "Date": "2025-01-02",
                    "EventType": "TAX",
                    "Ticker": "OKE",
                    "ISIN": isin,
                    "Quantity": 0,
                    "Price": 0,
                    "Amount": tax,
                    "Fee": 0,
                    "Currency": "PLN",
                },
            ]
        )

    _, dividends, _ = process_yearly_data(rows, 2025)

    assert [(item["isin"], item["tax_withheld_pln"]) for item in dividends] == [
        ("OLD-ISIN", 10.0),
        ("NEW-ISIN", 20.0),
    ]
