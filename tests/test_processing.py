import pytest
from decimal import Decimal
from unittest.mock import patch
from src.processing import process_yearly_data

@pytest.fixture
def mock_trades_db():
    # Mock data structure mimicking SQLite rows (PascalCase keys)
    return [
        # Dividend
        {'TradeId': 1, 'Date': '2025-01-02', 'EventType': 'DIVIDEND', 'Ticker': 'AAPL', 'Quantity': 0, 'Price': 0, 'Amount': 10.0, 'Fee': 0, 'Currency': 'USD'},
        # Associated Tax
        {'TradeId': 2, 'Date': '2025-01-02', 'EventType': 'TAX', 'Ticker': 'AAPL', 'Quantity': 0, 'Price': 0, 'Amount': -1.5, 'Fee': 0, 'Currency': 'USD'},
        # Trade (Buy)
        {'TradeId': 3, 'Date': '2025-01-05', 'EventType': 'BUY', 'Ticker': 'AAPL', 'Quantity': 1, 'Price': 100, 'Amount': -100, 'Fee': -1, 'Currency': 'USD'}
    ]

@patch('src.processing.get_nbp_rate')
def test_processing_flow(mock_rate, mock_trades_db):
    # Fix NBP rate to ensure predictable math
    mock_rate.return_value = Decimal('4.0')
    
    realized, dividends, inventory = process_yearly_data(mock_trades_db, 2025)
    
    # 1. Check Dividends
    assert len(dividends) == 1
    div = dividends[0]
    # Gross: 10 * 4.0 = 40.0
    assert div['gross_amount_pln'] == 40.0
    # Tax: 1.5 * 4.0 = 6.0
    assert div['tax_withheld_pln'] == 6.0
    
    # 2. Check Inventory
    assert len(inventory) == 1
    assert inventory[0]['ticker'] == 'AAPL'
