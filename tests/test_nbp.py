# tests/test_nbp.py

import pytest
import requests
from decimal import Decimal
from unittest.mock import patch, MagicMock
from src.nbp import get_nbp_rate, _MONTHLY_CACHE

@pytest.fixture(autouse=True)
def clear_cache():
    # Clear cache before every test to ensure isolation
    _MONTHLY_CACHE.clear()

@patch('src.nbp.requests.get')
def test_fetch_month_rates_success(mock_get):
    # Simulate API response for January 2025
    mock_response = MagicMock()
    mock_response.status_code = 200
    mock_response.json.return_value = {
        "rates": [
            {"effectiveDate": "2025-01-02", "mid": 4.10},
            {"effectiveDate": "2025-01-03", "mid": 4.15}
        ]
    }
    mock_get.return_value = mock_response

    # Request rate for Jan 3rd (T-1 rule implies finding rate for Jan 2nd)
    rate = get_nbp_rate("USD", "2025-01-03")
    
    assert rate == Decimal("4.10") # Rate for Jan 2nd
    assert mock_get.call_count == 1 # Exactly one API call made
    
    # Request rate for Jan 4th (T-1 = Jan 3rd).
    # Should NOT trigger a new API call because data is cached.
    rate2 = get_nbp_rate("USD", "2025-01-04")
    assert rate2 == Decimal("4.15")
    assert mock_get.call_count == 1 # Call count remains 1

@patch('src.nbp.requests.get')
def test_weekend_lookback(mock_get):
    # Weekend Test: Mon 6.01 -> should take Fri 3.01 (T-1=Sun, T-2=Sat, T-3=Fri)
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
