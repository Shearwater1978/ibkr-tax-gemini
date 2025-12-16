import pytest
from decimal import Decimal
from src.fifo import TradeMatcher

def test_fifo_sorting_priority():
    """Ensure trades are processed in correct order: SPLIT -> BUY -> SELL within same day."""
    matcher = TradeMatcher()
    
    # Unsorted input: Sell comes before Buy in list, but same date
    trades = [
        # FIX: SELL quantity must be negative
        {'type': 'SELL', 'date': '2024-01-01', 'ticker': 'A', 'qty': Decimal(-10), 'price': 10, 'commission': 0, 'currency': 'USD', 'rate': 1},
        {'type': 'BUY', 'date': '2024-01-01', 'ticker': 'A', 'qty': Decimal(10), 'price': 5, 'commission': 0, 'currency': 'USD', 'rate': 1},
    ]
    
    matcher.process_trades(trades)
    results = matcher.get_realized_gains()
    
    assert len(results) == 1
    # Cost 50 (10*5), Rev 100 (10*10) -> Profit 50
    assert results[0]['profit_loss'] == 50.0
