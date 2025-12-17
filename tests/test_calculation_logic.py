# tests/test_calculation_logic.py

import pytest
from decimal import Decimal
from unittest.mock import patch
from src.processing import process_yearly_data

@pytest.fixture
def mock_raw_trades():
    return [
        # Buy: Cost = (10 * 100 * 4) + (5 * 4) = 4020 PLN total.
        {'TradeId': 1, 'Date': '2023-01-10', 'Ticker': 'AAPL', 'EventType': 'BUY', 'Quantity': 10.0, 'Price': 100.0, 'Amount': -1000.0, 'Fee': 5.0, 'Currency': 'USD', 'Description': ''},
        
        # Sell half (5): 
        # Revenue = 5 * 150 * 4 = 3000 PLN.
        # Sell Fee = 5 * 4 = 20 PLN.
        # Cost Portion (from Buy) = 4020 / 2 = 2010 PLN.
        # Total Cost Basis (for Report) = Cost Portion (2010) + Sell Fee (20) = 2030 PLN.
        {'TradeId': 3, 'Date': '2024-02-20', 'Ticker': 'AAPL', 'EventType': 'SELL', 'Quantity': -5.0, 'Price': 150.0, 'Amount': 750.0, 'Fee': 5.0, 'Currency': 'USD', 'Description': ''},
    ]

@patch('src.processing.get_nbp_rate')
def test_process_yearly_data_fifo_logic(mock_get_rate, mock_raw_trades):
    mock_get_rate.return_value = Decimal("4.0")
    
    realized_gains, _, _ = process_yearly_data(mock_raw_trades, 2024)
    
    assert len(realized_gains) == 1
    sale = realized_gains[0]
    
    assert sale['sale_amount'] == 3000.0
    assert sale['cost_basis'] == 2030.0 # Updated expectation to include full transaction costs
    assert sale['profit_loss'] == 970.0 # 3000 - 2030