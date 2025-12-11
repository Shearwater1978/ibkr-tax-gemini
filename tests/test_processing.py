import pytest
from decimal import Decimal
from unittest.mock import patch
from src.processing import process_yearly_data

@pytest.fixture
def mock_trades_db():
    return [
        # Дивиденд
        {'TradeId': 1, 'Date': '2025-01-02', 'EventType': 'DIVIDEND', 'Ticker': 'AAPL', 'Quantity': 0, 'Price': 0, 'Amount': 10.0, 'Fee': 0, 'Currency': 'USD'},
        # Налог к нему
        {'TradeId': 2, 'Date': '2025-01-02', 'EventType': 'TAX', 'Ticker': 'AAPL', 'Quantity': 0, 'Price': 0, 'Amount': -1.5, 'Fee': 0, 'Currency': 'USD'},
        # Сделка (покупка)
        {'TradeId': 3, 'Date': '2025-01-05', 'EventType': 'BUY', 'Ticker': 'AAPL', 'Quantity': 1, 'Price': 100, 'Amount': -100, 'Fee': -1, 'Currency': 'USD'}
    ]

@patch('src.processing.get_nbp_rate')
def test_processing_flow(mock_rate, mock_trades_db):
    # Фиксируем курс, чтобы математика была предсказуемой
    mock_rate.return_value = Decimal('4.0')
    
    realized, dividends, inventory = process_yearly_data(mock_trades_db, 2025)
    
    # Проверка дивидендов
    assert len(dividends) == 1
    div = dividends[0]
    # Gross: 10 * 4.0 = 40.0
    assert div['gross_amount_pln'] == 40.0
    # Tax: 1.5 * 4.0 = 6.0
    assert div['tax_withheld_pln'] == 6.0
    
    # Проверка инвентаря
    assert len(inventory) == 1
    assert inventory[0]['ticker'] == 'AAPL'
