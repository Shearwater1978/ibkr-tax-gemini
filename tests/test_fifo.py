import pytest
from decimal import Decimal
from src.fifo import TradeMatcher

@pytest.fixture
def matcher():
    return TradeMatcher()

def test_fifo_simple_profit(matcher):
    # Buy 10 @ 100, Sell 5 @ 150
    trades = [
        {'type': 'BUY', 'date': '2024-01-01', 'ticker': 'AAPL', 'qty': Decimal(10), 'price': Decimal(100), 'commission': Decimal(5), 'currency': 'USD', 'rate': Decimal(4.0)},
        {'type': 'SELL', 'date': '2024-01-02', 'ticker': 'AAPL', 'qty': Decimal(5), 'price': Decimal(150), 'commission': Decimal(5), 'currency': 'USD', 'rate': Decimal(4.0)}
    ]
    matcher.process_trades(trades)
    results = matcher.get_realized_gains()
    assert len(results) == 1
    res = results[0]
    # Revenue: 5 * 150 * 4 = 3000
    assert res['sale_amount'] == 3000.0
    # Cost: (5 * 100 * 4) + (Half Buy Comm: 2.5 * 4 = 10) + (Full Sell Comm: 5 * 4 = 20) = 2030
    assert res['cost_basis'] == 2030.0
    # Profit: 3000 - 2030 = 970
    assert res['profit_loss'] == 970.0
