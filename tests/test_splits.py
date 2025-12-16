import pytest
from decimal import Decimal
from src.fifo import TradeMatcher

@pytest.fixture
def matcher():
    return TradeMatcher()

def test_forward_split_4_to_1(matcher):
    """
    Buy 10 @ 100. Split 4:1 -> Own 40 @ 25. Sell 40 @ 30.
    """
    trades = [
        {'type': 'BUY', 'date': '2024-01-01', 'ticker': 'NVDA', 'qty': Decimal(10), 'price': Decimal(100), 'commission': Decimal(0), 'currency': 'USD', 'rate': Decimal(1.0)},
        # FIX: Added qty: 0 to avoid KeyError in fifo loop
        {'type': 'SPLIT', 'date': '2024-02-01', 'ticker': 'NVDA', 'ratio': Decimal(4), 'qty': Decimal(0), 'currency': 'USD'},
        # FIX: SELL quantity must be negative
        {'type': 'SELL', 'date': '2024-03-01', 'ticker': 'NVDA', 'qty': Decimal(-40), 'price': Decimal(30), 'commission': Decimal(0), 'currency': 'USD', 'rate': Decimal(1.0)}
    ]
    matcher.process_trades(trades)
    results = matcher.get_realized_gains()
    
    assert len(results) == 1
    res = results[0]
    
    # Cost was 10*100 = 1000.
    # Revenue is 40*30 = 1200.
    # Profit = 200.
    
    assert res['profit_loss'] == 200.0
    assert res['cost_basis'] == 1000.0

def test_reverse_split_1_to_10(matcher):
    """
    Buy 100 @ 1. Reverse Split 1:10 (0.1) -> Own 10 @ 10. Sell 10 @ 12.
    """
    trades = [
        {'type': 'BUY', 'date': '2024-01-01', 'ticker': 'PENNY', 'qty': Decimal(100), 'price': Decimal(1), 'commission': Decimal(0), 'currency': 'USD', 'rate': Decimal(1.0)},
        # FIX: Added qty: 0
        {'type': 'SPLIT', 'date': '2024-02-01', 'ticker': 'PENNY', 'ratio': Decimal("0.1"), 'qty': Decimal(0), 'currency': 'USD'},
        # FIX: SELL quantity must be negative
        {'type': 'SELL', 'date': '2024-03-01', 'ticker': 'PENNY', 'qty': Decimal(-10), 'price': Decimal(12), 'commission': Decimal(0), 'currency': 'USD', 'rate': Decimal(1.0)}
    ]
    matcher.process_trades(trades)
    results = matcher.get_realized_gains()
    
    # Cost: 100 * 1 = 100.
    # Revenue: 10 * 12 = 120.
    # Profit: 20.
    
    assert results[0]['profit_loss'] == 20.0
