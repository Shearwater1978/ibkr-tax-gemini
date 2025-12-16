import os

# --- 1. Corrected tests/test_parser.py ---
# Fixes: Added missing 'quantity' argument to extract_ticker calls.
content_test_parser = """import pytest
from decimal import Decimal
from src.parser import normalize_date, extract_ticker, parse_decimal, classify_trade_type

def test_normalize_date():
    # Test various formats from Activity Statement
    assert normalize_date("20250102") == "2025-01-02"
    assert normalize_date("01/02/2025") == "2025-01-02" # US format MM/DD/YYYY
    assert normalize_date("2025-01-02, 15:00:00") == "2025-01-02"
    
    # Check empty/broken values
    assert normalize_date("") is None
    assert normalize_date(None) is None

def test_extract_ticker():
    # Standard case with ISIN
    # Note: Passed Decimal(0) as dummy quantity
    assert extract_ticker("AGR(US05351W1036) Cash Dividend", "", Decimal(0)) == "AGR"
    
    # Fallback case (no ISIN), ticker must be short
    assert extract_ticker("TEST Cash Div", "", Decimal(0)) == "TEST"
    
    # Priority of Symbol column
    assert extract_ticker("Unknown Desc", "AAPL", Decimal(0)) == "AAPL" 

def test_parse_decimal():
    assert parse_decimal("1,000.50") == Decimal("1000.50")
    assert parse_decimal("-500") == Decimal("-500")
    assert parse_decimal("") == Decimal("0")

def test_classify_trade():
    assert classify_trade_type("ACATS Transfer", Decimal(10)) == "TRANSFER"
    assert classify_trade_type("Buy Order", Decimal(10)) == "BUY"
    assert classify_trade_type("Sell", Decimal(-5)) == "SELL"
"""

# --- 2. Corrected tests/test_fifo.py ---
# Fixes: Changed SELL quantities to negative numbers (e.g., -5) to match src/fifo.py logic.
content_test_fifo = """import pytest
from decimal import Decimal
from src.fifo import TradeMatcher

@pytest.fixture
def matcher():
    return TradeMatcher()

def test_fifo_simple_profit(matcher):
    \"\"\"Buy low, sell high.\"\"\"
    trades = [
        {'type': 'BUY', 'date': '2024-01-01', 'ticker': 'AAPL', 'qty': Decimal(10), 'price': Decimal(100), 'commission': Decimal(5), 'currency': 'USD', 'rate': Decimal(4.0)},
        # FIX: SELL quantity must be negative for the engine to recognize it as a disposal
        {'type': 'SELL', 'date': '2024-01-02', 'ticker': 'AAPL', 'qty': Decimal(-5), 'price': Decimal(150), 'commission': Decimal(5), 'currency': 'USD', 'rate': Decimal(4.0)}
    ]
    matcher.process_trades(trades)
    results = matcher.get_realized_gains()
    
    assert len(results) == 1
    res = results[0]
    
    # Revenue: 5 * 150 * 4.0 = 3000
    assert res['sale_amount'] == 3000.0
    
    # Cost: (5 * 100 * 4.0) + (Half Buy Comm: 2.5 * 4.0 = 10) + (Full Sell Comm: 5 * 4.0 = 20)
    # Cost Basis = 2000 (stock) + 10 (buy comm) + 20 (sell comm) = 2030
    assert res['cost_basis'] == 2030.0
    
    # Profit: 3000 - 2030 = 970
    assert res['profit_loss'] == 970.0

def test_fifo_multiple_buys(matcher):
    \"\"\"Sell consumes first buy completely and part of second buy.\"\"\"
    trades = [
        {'type': 'BUY', 'date': '2024-01-01', 'ticker': 'AAPL', 'qty': Decimal(10), 'price': Decimal(100), 'commission': Decimal(0), 'currency': 'USD', 'rate': Decimal(1.0)},
        {'type': 'BUY', 'date': '2024-01-02', 'ticker': 'AAPL', 'qty': Decimal(10), 'price': Decimal(200), 'commission': Decimal(0), 'currency': 'USD', 'rate': Decimal(1.0)},
        # FIX: SELL quantity must be negative
        {'type': 'SELL', 'date': '2024-01-03', 'ticker': 'AAPL', 'qty': Decimal(-15), 'price': Decimal(300), 'commission': Decimal(0), 'currency': 'USD', 'rate': Decimal(1.0)}
    ]
    matcher.process_trades(trades)
    results = matcher.get_realized_gains()
    
    assert len(results) == 1
    res = results[0]
    
    # Revenue: 15 * 300 = 4500
    assert res['sale_amount'] == 4500.0
    
    # Cost: (10 * 100) + (5 * 200) = 1000 + 1000 = 2000
    assert res['cost_basis'] == 2000.0
    
    # Profit: 4500 - 2000 = 2500
    assert res['profit_loss'] == 2500.0
"""

# --- 3. Corrected tests/test_edge_cases.py ---
# Fixes: Changed SELL quantity to negative.
content_test_edge = """import pytest
from decimal import Decimal
from src.fifo import TradeMatcher

def test_fifo_sorting_priority():
    \"\"\"Ensure trades are processed in correct order: SPLIT -> BUY -> SELL within same day.\"\"\"
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
"""

# --- 4. Corrected tests/test_splits.py ---
# Fixes: Added 'qty': 0 to SPLIT events (required by fifo loop) and made SELL quantities negative.
content_test_splits = """import pytest
from decimal import Decimal
from src.fifo import TradeMatcher

@pytest.fixture
def matcher():
    return TradeMatcher()

def test_forward_split_4_to_1(matcher):
    \"\"\"
    Buy 10 @ 100. Split 4:1 -> Own 40 @ 25. Sell 40 @ 30.
    \"\"\"
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
    \"\"\"
    Buy 100 @ 1. Reverse Split 1:10 (0.1) -> Own 10 @ 10. Sell 10 @ 12.
    \"\"\"
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
"""

# --- 5. Corrected tests/test_db_connector.py ---
# Fixes: Replaced invalid mock of 'config' with direct patching of module attributes.
content_test_db = """import pytest
import sqlite3
import os
from unittest.mock import patch, MagicMock
from src.db_connector import DBConnector

DB_KEY = "test_key"
DB_PATH = "test.db"

@pytest.fixture
def mock_db_connection():
    # Patch sqlite3.connect to avoid creating real files
    with patch('src.db_connector.sqlite3.connect') as mock_connect:
        mock_conn = MagicMock()
        mock_conn.row_factory = sqlite3.Row
        mock_connect.return_value = mock_conn
        yield mock_connect, mock_conn

def test_get_trades_no_ticker_filter(mock_db_connection):
    mock_connect, mock_conn = mock_db_connection
    
    # Patch the global variables in src.db_connector directly
    with patch('src.db_connector.DB_PATH', DB_PATH), \\
         patch('src.db_connector.DB_KEY', DB_KEY):
        
        with DBConnector() as db:
            db.get_trades_for_calculation(2024, None)
            
            # Check that query uses EventType, NOT Type
            call_args = mock_conn.execute.call_args
            query = call_args[0][0]
            
            assert "EventType" in query
            assert "Date <=" in query

def test_get_trades_with_ticker_filter(mock_db_connection):
    mock_connect, mock_conn = mock_db_connection
    
    with patch('src.db_connector.DB_PATH', DB_PATH), \\
         patch('src.db_connector.DB_KEY', DB_KEY):
         
        with DBConnector() as db:
            db.get_trades_for_calculation(2024, "AAPL")
            
            call_args = mock_conn.execute.call_args
            query = call_args[0][0]
            
            assert "AND Ticker = ?" in query
"""

def write_file(path, content):
    try:
        with open(path, 'w', encoding='utf-8') as f:
            f.write(content)
        print(f"✅ Fixed: {path}")
    except Exception as e:
        print(f"❌ Error writing {path}: {e}")

if __name__ == "__main__":
    print("--- Fixing Test Suite ---")
    write_file("tests/test_parser.py", content_test_parser)
    write_file("tests/test_fifo.py", content_test_fifo)
    write_file("tests/test_edge_cases.py", content_test_edge)
    write_file("tests/test_splits.py", content_test_splits)
    write_file("tests/test_db_connector.py", content_test_db)
    print("--- Done. Run 'pytest' again. ---")
