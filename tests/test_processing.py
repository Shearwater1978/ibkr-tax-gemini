import pytest
from decimal import Decimal
from unittest.mock import patch
from src.processing import process_yearly_data

@pytest.fixture
def mock_trades():
    return [
        {'TradeId': 1, 'Date': '2024-05-01', 'EventType': 'DIVIDEND', 'Ticker': 'AAPL', 'Quantity': 0, 'Price': 0, 'Amount': 10.0, 'Fee': 0, 'Currency': 'USD'},
        {'TradeId': 2, 'Date': '2024-05-01', 'EventType': 'TAX', 'Ticker': 'AAPL', 'Quantity': 0, 'Price': 0, 'Amount': -1.5, 'Fee': 0, 'Currency': 'USD'}
    ]

@patch('src.processing.get_nbp_rate')
def test_dividend_tax_linking(mock_rate, mock_trades):
    mock_rate.return_value = Decimal('4.0')
    _, dividends, _ = process_yearly_data(mock_trades, 2024)
    
    assert len(dividends) == 1
    div = dividends[0]
    # Gross: 10 * 4 = 40
    assert div['gross_amount_pln'] == 40.0
    # Tax: 1.5 * 4 = 6.0
    assert div['tax_withheld_pln'] == 6.0
