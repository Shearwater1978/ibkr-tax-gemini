from decimal import Decimal
from src.fifo import TradeMatcher
from unittest.mock import patch

@patch('src.fifo.get_rate_for_tax_date', return_value=Decimal("4.00"))
def test_fifo_simple_profit(mock_rate):
    matcher = TradeMatcher()
    trades = [
        {'date': '2022-01-01', 'ticker': 'ABC', 'qty': Decimal('10'), 'price': Decimal('10'), 'commission': Decimal('0'), 'currency': 'USD', 'type': 'BUY'},
        {'date': '2022-02-01', 'ticker': 'ABC', 'qty': Decimal('-10'), 'price': Decimal('20'), 'commission': Decimal('0'), 'currency': 'USD', 'type': 'SELL'}
    ]
    matcher.process_trades(trades)
    pnl = matcher.realized_pnl[0]
    assert pnl['profit_pln'] == 400.00

@patch('src.fifo.get_rate_for_tax_date', return_value=Decimal("1.00")) 
def test_fifo_multiple_buys(mock_rate):
    matcher = TradeMatcher()
    trades = [
        {'date': '2022-01-01', 'ticker': 'ABC', 'qty': Decimal('10'), 'price': Decimal('100'), 'commission': Decimal('0'), 'currency': 'USD', 'type': 'BUY'},
        {'date': '2022-01-02', 'ticker': 'ABC', 'qty': Decimal('10'), 'price': Decimal('200'), 'commission': Decimal('0'), 'currency': 'USD', 'type': 'BUY'},
        {'date': '2022-01-05', 'ticker': 'ABC', 'qty': Decimal('-15'), 'price': Decimal('300'), 'commission': Decimal('0'), 'currency': 'USD', 'type': 'SELL'}
    ]
    matcher.process_trades(trades)
    pnl = matcher.realized_pnl[0]
    assert pnl['revenue_pln'] == 4500.00
    assert pnl['cost_pln'] == 2000.00
    assert pnl['profit_pln'] == 2500.00
