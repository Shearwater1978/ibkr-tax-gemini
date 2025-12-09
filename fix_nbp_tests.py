import os
import re

def fix_nbp_tests(filepath="tests/test_nbp.py"):
    """
    Applies the final fix to tests/test_nbp.py by implementing aggressive module cache
    clearing (del sys.modules) and mocking the internal memory cache to bypass global conflicts.
    Replaces Russian comments with English ones.
    """
    print(f"🛠️ Applying final fix to {filepath}...")
    
    # --- Define the new, clean content (English comments only) ---
    new_content = """
import pytest
import sys 
from decimal import Decimal
from unittest.mock import patch, mock_open, MagicMock
import src 
import requests 

# --- Mock functions for side_effect (as before) ---

def mock_requests_get_success(url, **kwargs):
    mock_response = MagicMock()
    mock_response.status_code = 200
    mock_response.json.return_value = {
        "rates": [
            {"effectiveDate": "2022-01-01", "mid": 4.1234},
            {"effectiveDate": "2022-01-03", "mid": 4.2000}
        ]
    }
    return mock_response

def mock_requests_get_holiday(url, **kwargs):
    mock_response = MagicMock()
    mock_response.status_code = 200
    mock_response.json.return_value = {
        "rates": [
            {"effectiveDate": "2022-01-01", "mid": 4.1000},
            {"effectiveDate": "2022-01-04", "mid": 4.2000}
        ]
    }
    return mock_response


# --- TESTS ---

def test_get_nbp_rate_success():
    
    # FIX: Remove global mock applied by test_calculation_logic.py
    if 'src.nbp' in sys.modules:
        del sys.modules['src.nbp']
    
    # FIX: Mock _MEMORY_CACHE to guarantee no internal cache hit occurs
    with patch('src.nbp._MEMORY_CACHE', new_callable=MagicMock) as mock_cache:
        
        mock_cache.clear() 
        mock_cache.__contains__.return_value = False 
        
        # Import the function AFTER clearing the module and mocking the cache
        from src.nbp import get_nbp_rate 

        # Patch requests.get in the module where it is used (src.nbp)
        with patch('src.nbp.requests.get', side_effect=mock_requests_get_success) as mock_get:
            with patch('src.nbp.os.path.exists', return_value=False):
                with patch("builtins.open", mock_open()):
                    
                    rate = get_nbp_rate("USD", "2022-01-03")
                    
                    # Assert API was called once
                    mock_get.assert_called_once()
                
                assert rate == Decimal("4.2") 


def test_get_nbp_rate_holiday_recursion():
    
    # FIX: Remove global mock applied by test_calculation_logic.py
    if 'src.nbp' in sys.modules:
        del sys.modules['src.nbp']

    # FIX: Mock _MEMORY_CACHE to guarantee no internal cache hit occurs
    with patch('src.nbp._MEMORY_CACHE', new_callable=MagicMock) as mock_cache:
        
        mock_cache.clear()
        mock_cache.__contains__.return_value = False 
        
        # Import the function AFTER clearing the module and mocking the cache
        from src.nbp import get_nbp_rate 

        # Patch requests.get in the module where it is used (src.nbp)
        with patch('src.nbp.requests.get', side_effect=mock_requests_get_holiday) as mock_get:
            with patch('src.nbp.os.path.exists', return_value=False):
                with patch("builtins.open", mock_open()):
                    
                    rate = get_nbp_rate("USD", "2022-01-02")
                
                # Assert API was called twice (recursion check)
                assert mock_get.call_count == 2
                assert rate == Decimal("4.1")
"""
    
    # --- Execute file write ---
    try:
        with open(filepath, 'w', encoding='utf-8') as f:
            f.write(new_content)
        print("✅ tests/test_nbp.py updated with final fix and English comments.")
    except Exception as e:
        print(f"❌ Error writing file: {e}")

if __name__ == "__main__":
    fix_nbp_tests()