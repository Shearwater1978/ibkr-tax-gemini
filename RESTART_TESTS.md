# RESTART PROMPT: TESTS (v2.1.0)

**Context:** Part 3 of 3. Contains pytest suite and mock data.
**Instructions:** Restore these files to `tests/` directory.

# --- FILE: tests/__init__.py ---
```python
```

# --- FILE: tests/mock_trades.json ---
```json
{
    "trades": [
        {
            "date": "2023-01-01",
            "ticker": "AAPL",
            "type": "BUY",
            "qty": 10,
            "price": 100.00,
            "commission": 1.00,
            "currency": "USD"
        },
        {
            "date": "2023-03-01",
            "ticker": "AAPL",
            "type": "SELL",
            "qty": 5,
            "price": 121.00,
            "commission": 0.50,
            "currency": "USD"
        }
    ],
    "dividends": [
        {
            "date": "2023-05-15",
            "ticker": "AAPL",
            "amount": 10.00,
            "currency": "USD"
        }
    ],
    "taxes": [
        {
            "date": "2023-05-15",
            "ticker": "AAPL",
            "amount": -1.50,
            "currency": "USD"
        }
    ]
}```

# --- FILE: tests/test_calculation_logic.py ---
```python
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
    assert sale['profit_loss'] == 970.0 # 3000 - 2030```

# --- FILE: tests/test_db_connector.py ---
```python
import pytest
import sqlite3
from unittest.mock import patch, MagicMock
from src.db_connector import DBConnector

DB_KEY = "test_key"
DB_PATH = "test.db"

@pytest.fixture
def mock_config():
    with patch('src.db_connector.config') as mock:
        def side_effect(key, default=None):
            if key == 'DATABASE_PATH': return DB_PATH
            if key == 'SQLCIPHER_KEY': return DB_KEY
            return default
        mock.side_effect = side_effect
        yield mock

@pytest.fixture
def mock_db_connection():
    with patch('src.db_connector.sqlite3.connect') as mock_connect:
        mock_conn = MagicMock()
        mock_conn.row_factory = sqlite3.Row
        mock_connect.return_value = mock_conn
        yield mock_connect, mock_conn

def test_get_trades_no_ticker_filter(mock_config, mock_db_connection):
    mock_connect, mock_conn = mock_db_connection
    
    with DBConnector() as db:
        db.get_trades_for_calculation(2024, None)
        
        # Check that query uses EventType, NOT Type
        call_args = mock_conn.execute.call_args
        query = call_args[0][0]
        
        assert "EventType='BUY'" in query
        assert "Date BETWEEN :start_date AND :end_date" in query

def test_get_trades_with_ticker_filter(mock_config, mock_db_connection):
    mock_connect, mock_conn = mock_db_connection
    
    with DBConnector() as db:
        db.get_trades_for_calculation(2024, "AAPL")
        
        call_args = mock_conn.execute.call_args
        query = call_args[0][0]
        
        assert "AND Ticker = :ticker" in query```

# --- FILE: tests/test_edge_cases.py ---
```python
import pytest
from decimal import Decimal
from src.fifo import TradeMatcher

def test_fifo_sorting_priority():
    """Ensure trades are processed in correct order: SPLIT -> BUY -> SELL within same day."""
    matcher = TradeMatcher()
    
    # Unsorted input: Sell comes before Buy in list, but same date
    trades = [
        {'type': 'SELL', 'date': '2024-01-01', 'ticker': 'A', 'qty': Decimal(10), 'price': 10, 'commission': 0, 'currency': 'USD', 'rate': 1},
        {'type': 'BUY', 'date': '2024-01-01', 'ticker': 'A', 'qty': Decimal(10), 'price': 5, 'commission': 0, 'currency': 'USD', 'rate': 1},
    ]
    
    # Should not crash. If sorted incorrectly, Sell would happen with empty inventory.
    matcher.process_trades(trades)
    results = matcher.get_realized_gains()
    
    assert len(results) == 1
    # Cost 50, Rev 100 -> Profit 50
    assert results[0]['profit_loss'] == 50.0```

# --- FILE: tests/test_fifo.py ---
```python
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
    assert res['profit_loss'] == 2500.0```

# --- FILE: tests/test_nbp.py ---
```python
import pytest
import requests
from decimal import Decimal
from unittest.mock import patch, MagicMock
from src.nbp import get_nbp_rate, _MONTHLY_CACHE

@pytest.fixture(autouse=True)
def clear_cache():
    # Очищаем кэш перед каждым тестом
    _MONTHLY_CACHE.clear()

@patch('src.nbp.requests.get')
def test_fetch_month_rates_success(mock_get):
    # Симулируем ответ API за Январь 2025
    mock_response = MagicMock()
    mock_response.status_code = 200
    mock_response.json.return_value = {
        "rates": [
            {"effectiveDate": "2025-01-02", "mid": 4.10},
            {"effectiveDate": "2025-01-03", "mid": 4.15}
        ]
    }
    mock_get.return_value = mock_response

    # Запрашиваем курс на 3 января (должен взять T-1 = 2 января)
    rate = get_nbp_rate("USD", "2025-01-03")
    
    assert rate == Decimal("4.10") # Курс за 2-е число
    assert mock_get.call_count == 1 # Был ровно 1 запрос в сеть
    
    # Запрашиваем курс на 4 января (T-1 = 3 января). 
    # Запроса в сеть быть НЕ должно, данные уже в кэше.
    rate2 = get_nbp_rate("USD", "2025-01-04")
    assert rate2 == Decimal("4.15")
    assert mock_get.call_count == 1 # Счетчик запросов не изменился!

@patch('src.nbp.requests.get')
def test_weekend_lookback(mock_get):
    # Тест на выходные: Пн 6.01, берем курс за Пт 3.01 (T-1=5(вс), T-2=4(сб), T-3=3(пт))
    mock_response = MagicMock()
    mock_response.status_code = 200
    mock_response.json.return_value = {
        "rates": [
            {"effectiveDate": "2025-01-03", "mid": 4.20} 
        ]
    }
    mock_get.return_value = mock_response

    rate = get_nbp_rate("USD", "2025-01-06")
    assert rate == Decimal("4.20")

def test_pln_is_always_one():
    assert get_nbp_rate("PLN", "2025-01-01") == Decimal("1.0")
```

# --- FILE: tests/test_parser.py ---
```python
import pytest
from decimal import Decimal
from src.parser import normalize_date, extract_ticker, parse_decimal, classify_trade_type

def test_normalize_date():
    # Тестируем разные форматы из Activity Statement
    assert normalize_date("20250102") == "2025-01-02"
    assert normalize_date("01/02/2025") == "2025-01-02" # US format MM/DD/YYYY
    assert normalize_date("2025-01-02, 15:00:00") == "2025-01-02"
    
    # Проверка на пустые/битые значения (Total row)
    assert normalize_date("") is None
    assert normalize_date(None) is None

def test_extract_ticker():
    # Стандартный случай с ISIN
    assert extract_ticker("AGR(US05351W1036) Cash Dividend", "") == "AGR"
    
    # Fallback случай (без ISIN), тикер должен быть коротким (<6 символов)
    # БЫЛО: "SIMPLE" (6 букв - фейл), СТАЛО: "TEST" (4 буквы - ок)
    assert extract_ticker("TEST Cash Div", "") == "TEST"
    
    # Приоритет колонки Symbol
    assert extract_ticker("Unknown Desc", "AAPL") == "AAPL" 

def test_parse_decimal():
    assert parse_decimal("1,000.50") == Decimal("1000.50")
    assert parse_decimal("-500") == Decimal("-500")
    assert parse_decimal("") == Decimal("0")

def test_classify_trade():
    assert classify_trade_type("ACATS Transfer", Decimal(10)) == "TRANSFER"
    assert classify_trade_type("Buy Order", Decimal(10)) == "BUY"
    assert classify_trade_type("Sell", Decimal(-5)) == "SELL"
```

# --- FILE: tests/test_processing.py ---
```python
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
```

# --- FILE: tests/test_splits.py ---
```python
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
        {'type': 'SPLIT', 'date': '2024-02-01', 'ticker': 'NVDA', 'ratio': Decimal(4), 'currency': 'USD'},
        {'type': 'SELL', 'date': '2024-03-01', 'ticker': 'NVDA', 'qty': Decimal(40), 'price': Decimal(30), 'commission': Decimal(0), 'currency': 'USD', 'rate': Decimal(1.0)}
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
        {'type': 'SPLIT', 'date': '2024-02-01', 'ticker': 'PENNY', 'ratio': Decimal("0.1"), 'currency': 'USD'},
        {'type': 'SELL', 'date': '2024-03-01', 'ticker': 'PENNY', 'qty': Decimal(10), 'price': Decimal(12), 'commission': Decimal(0), 'currency': 'USD', 'rate': Decimal(1.0)}
    ]
    matcher.process_trades(trades)
    results = matcher.get_realized_gains()
    
    # Cost: 100 * 1 = 100.
    # Revenue: 10 * 12 = 120.
    # Profit: 20.
    
    assert results[0]['profit_loss'] == 20.0```

# --- FILE: tests/test_utils.py ---
```python
import pytest
from decimal import Decimal
from src.utils import money

def test_financial_rounding():
    """
    Taxes require ROUND_HALF_UP (2.345 -> 2.35), not Python's default Banker's Rounding (2.345 -> 2.34).
    """
    # Case 1: Standard round up
    assert money(Decimal("2.345")) == Decimal("2.35")
    
    # Case 2: Standard round down
    assert money(Decimal("2.344")) == Decimal("2.34")
    
    # Case 3: Negative numbers
    assert money(Decimal("-2.345")) == Decimal("-2.35")

def test_money_handles_floats_and_strings():
    """Ensure utility handles various input types."""
    assert money(2.345) == Decimal("2.35")
    assert money("2.345") == Decimal("2.35")
    assert money(2) == Decimal("2.00")```

