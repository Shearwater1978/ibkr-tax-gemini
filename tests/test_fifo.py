import pytest
from decimal import Decimal
from src.fifo import TradeMatcher

@pytest.fixture
def matcher():
    return TradeMatcher()

def test_fifo_simple_profit(matcher):
    """Buy low, sell high."""
    trades = [
        {'type': 'BUY', 'date': '2024-01-01', 'ticker': 'AAPL', 'qty': Decimal(10), 'price': Decimal(100), 'commission': Decimal(5), 'currency': 'USD', 'rate': Decimal(4.0)},
        {'type': 'SELL', 'date': '2024-01-02', 'ticker': 'AAPL', 'qty': Decimal(5), 'price': Decimal(150), 'commission': Decimal(5), 'currency': 'USD', 'rate': Decimal(4.0)}
    ]
    matcher.process_trades(trades)
    results = matcher.get_realized_gains()
    
    assert len(results) == 1
    res = results[0]
    
    # Revenue: 5 * 150 * 4.0 = 3000
    assert res['sale_amount'] == 3000.0
    
    # Cost: (5 * 100 * 4.0) + (Half Buy Comm: 2.5 * 4.0 = 10) + (Full Sell Comm: 5 * 4.0 = 20)
    # Note: New logic adds Sell Comm to Cost Basis line
    # Cost Basis = 2000 (stock) + 10 (buy comm) + 20 (sell comm) = 2030
    assert res['cost_basis'] == 2030.0
    
    # Profit: 3000 - 2030 = 970
    assert res['profit_loss'] == 970.0

def test_fifo_multiple_buys(matcher):
    """Sell consumes first buy completely and part of second buy."""
    trades = [
        {'type': 'BUY', 'date': '2024-01-01', 'ticker': 'AAPL', 'qty': Decimal(10), 'price': Decimal(100), 'commission': Decimal(0), 'currency': 'USD', 'rate': Decimal(1.0)},
        {'type': 'BUY', 'date': '2024-01-02', 'ticker': 'AAPL', 'qty': Decimal(10), 'price': Decimal(200), 'commission': Decimal(0), 'currency': 'USD', 'rate': Decimal(1.0)},
        {'type': 'SELL', 'date': '2024-01-03', 'ticker': 'AAPL', 'qty': Decimal(15), 'price': Decimal(300), 'commission': Decimal(0), 'currency': 'USD', 'rate': Decimal(1.0)}
    ]
    matcher.process_trades(trades)
    results = matcher.get_realized_gains()
    
    # Should result in 2 split records (one for each batch) OR 1 aggregated record depending on implementation.
    # Our current implementation produces 1 record per SELL transaction, but aggregates internal matches?
    # No, looking at code: It appends to `self.realized_pnl` inside the loop.
    # Wait, the loop `while qty_to_sell > 0` appends ONCE per match? 
    # Let's check src/fifo.py logic:
    # It appends to `realized_pnl` ONLY AFTER the loop if is_taxable is True? 
    # Actually, looking at your latest fifo.py:
    # It appends ONE record per SELL event, containing a list of 'matched_buys'.
    
    assert len(results) == 1
    res = results[0]
    
    # Revenue: 15 * 300 = 4500
    assert res['sale_amount'] == 4500.0
    
    # Cost: (10 * 100) + (5 * 200) = 1000 + 1000 = 2000
    assert res['cost_basis'] == 2000.0
    
    # Profit
    assert res['profit_loss'] == 2500.0