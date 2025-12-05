from decimal import Decimal
from src.fifo import TradeMatcher
from unittest.mock import patch

@patch('src.fifo.get_rate_for_tax_date', return_value=Decimal("4.00"))
def test_forward_split_4_to_1(mock_rate):
    matcher = TradeMatcher()
    trades = [
        # Buy 1 @ 100$ (Cost = 400 PLN)
        {'date': '2022-01-01', 'ticker': 'AAPL', 'qty': Decimal('1'), 'price': Decimal('100'), 'commission': 0, 'currency': 'USD', 'type': 'BUY'},
        
        # Split 4 for 1 (Now we should have 4 shares, Price effectively 25$)
        {'date': '2022-06-01', 'ticker': 'AAPL', 'type': 'SPLIT', 'ratio': Decimal('4'), 'currency': 'USD'},
        
        # Sell 4 @ 30$ (Revenue = 4 * 30 * 4.0 = 480 PLN)
        {'date': '2022-12-01', 'ticker': 'AAPL', 'qty': Decimal('-4'), 'price': Decimal('30'), 'commission': 0, 'currency': 'USD', 'type': 'SELL'}
    ]
    
    matcher.process_trades(trades)
    
    assert len(matcher.realized_pnl) == 1
    pnl = matcher.realized_pnl[0]
    
    # Revenue: 480 PLN
    # Cost: 400 PLN (The original cost of that 1 share)
    # Profit: 80 PLN
    assert pnl['revenue_pln'] == 480.00
    assert pnl['cost_pln'] == 400.00
    assert pnl['profit_pln'] == 80.00

@patch('src.fifo.get_rate_for_tax_date', return_value=Decimal("4.00"))
def test_reverse_split_1_to_10(mock_rate):
    matcher = TradeMatcher()
    trades = [
        # Buy 100 @ 1$ (Cost = 100 * 1 * 4.0 = 400 PLN)
        {'date': '2022-01-01', 'ticker': 'PENNY', 'qty': Decimal('100'), 'price': Decimal('1'), 'commission': 0, 'currency': 'USD', 'type': 'BUY'},
        
        # Reverse Split 1 for 10 (Ratio 0.1). Now we have 10 shares.
        {'date': '2022-06-01', 'ticker': 'PENNY', 'type': 'SPLIT', 'ratio': Decimal('0.1'), 'currency': 'USD'},
        
        # Sell 10 @ 15$ (Revenue = 10 * 15 * 4.0 = 600 PLN)
        {'date': '2022-12-01', 'ticker': 'PENNY', 'qty': Decimal('-10'), 'price': Decimal('15'), 'commission': 0, 'currency': 'USD', 'type': 'SELL'}
    ]
    
    matcher.process_trades(trades)
    
    assert len(matcher.realized_pnl) == 1
    pnl = matcher.realized_pnl[0]
    
    assert pnl['revenue_pln'] == 600.00
    assert pnl['cost_pln'] == 400.00
    assert pnl['profit_pln'] == 200.00
