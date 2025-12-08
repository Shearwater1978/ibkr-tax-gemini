import sys
from decimal import Decimal
from unittest.mock import MagicMock

# 1. FIX: Create a mock NBP module
mock_nbp_module = MagicMock()

# Set the desired return value for the problematic function inside the mock module
mock_nbp_module.get_rate_for_tax_date.return_value = Decimal("1.00")

# Inject the mock module into the Python import system cache BEFORE src.processing tries to import it.
# This bypasses the actual module loading and prevents circular imports from starting.
sys.modules['src.nbp'] = mock_nbp_module 


# --- Standard Imports (must come AFTER the sys.modules patch) ---
import pytest
import json
import os
from src.processing import TaxCalculator


# Load mock data
with open("tests/mock_trades.json", "r") as f:
    MOCK_DATA = json.load(f)

# Convert mock data to Decimal format expected by TaxCalculator
def prepare_mock_data(data):
    # Convert string/float to Decimal for all financial fields
    def to_decimal(d):
        return {k: Decimal(str(v)) if k in ['qty', 'price', 'commission', 'amount'] else v for k, v in d.items()}

    trades = [to_decimal(t) for t in data['trades']]
    dividends = [to_decimal(d) for d in data['dividends']]
    taxes = [to_decimal(t) for t in data['taxes']]
    
    return trades, dividends, taxes

MOCK_TRADES, MOCK_DIVS, MOCK_TAXES = prepare_mock_data(MOCK_DATA)


# --- FIXTURE FOR MOCKING DB FUNCTIONS (No change needed here) ---

@pytest.fixture(scope="function")
def mock_db_data(monkeypatch):
    
    # 0. Mock os.path.exists to return True for the DB file path.
    def mock_os_path_exists(path):
        if path.endswith("ibkr_history.db"):
            return True 
        return os.path.exists(path)

    # Apply mock to os.path.exists
    monkeypatch.setattr('os.path.exists', mock_os_path_exists)
    
    # 1. Mock fetch_all_trades
    def mock_fetch_trades(cutoff_date=None):
        return MOCK_TRADES
        
    # 2. Mock fetch_dividends
    def mock_fetch_divs(year=None):
        return MOCK_DIVS
        
    # 3. Mock fetch_taxes
    def mock_fetch_taxes(year=None):
        return MOCK_TAXES

    # 4. Mock fetch_assets_metadata
    def mock_fetch_assets_metadata():
        return {"AAPL": {"currency": "USD", "is_restricted": False}}
    
    # 5. Apply mocks to src.processing
    monkeypatch.setattr('src.processing.fetch_all_trades', mock_fetch_trades)
    monkeypatch.setattr('src.processing.fetch_dividends', mock_fetch_divs)
    monkeypatch.setattr('src.processing.fetch_taxes', mock_fetch_taxes)
    monkeypatch.setattr('src.processing.fetch_assets_metadata', mock_fetch_assets_metadata)
    
    yield


def test_fifo_profit_calculation(mock_db_data):
    """Verifies the correctness of the FIFO profit calculation for the test trade."""
    
    # Currency rate is now mocked via sys.modules
    calc = TaxCalculator(target_year="2023")
    calc.run_calculations()
    results = calc.get_results()['data']
    
    expected_gain = 104.00
    
    assert len(results['capital_gains']) == 1, "Should be one P&L entry."
    
    realized_gain = results['capital_gains'][0]
    
    assert realized_gain['ticker'] == 'AAPL'
    assert realized_gain['pnl'] == expected_gain, f"Profit calculation error. Expected: {expected_gain}, received: {realized_gain['pnl']}."


def test_dividend_and_tax_retrieval(mock_db_data):
    """Verifies that dividends and withholding taxes are correctly retrieved."""
    
    # Currency rate is now mocked via sys.modules
    calc = TaxCalculator(target_year="2023")
    calc.run_calculations()
    results = calc.get_results()['data']
    
    assert len(results['dividends']) == 1, "Should be one dividend entry."
    
    div = results['dividends'][0]
    
    # Check Withholding Tax
    assert div['tax_paid'] == 1.50, f"Error in tax paid calculation. Expected: 1.50, received: {div['tax_paid']}."