import pytest
import requests
from decimal import Decimal
from unittest.mock import patch, MagicMock
from src.nbp import get_nbp_rate, _MONTHLY_CACHE

@pytest.fixture(autouse=True)
def clear_cache():
    # Clear cache before each test
    _MONTHLY_CACHE.clear()

@patch('src.nbp.requests.get')
def test_fetch_month_rates_success(mock_get):
    # Simulating an API response for January 2025
    mock_response = MagicMock()
    mock_response.status_code = 200
    mock_response.json.return_value = {
        "rates": [
            {"effectiveDate": "2025-01-02", "mid": 4.10},
            {"effectiveDate": "2025-01-03", "mid": 4.15}
        ]
    }
    mock_get.return_value = mock_response

    # We request a rate for January 3 (must take T-1 = January 2)
    rate = get_nbp_rate("USD", "2025-01-03")
    
    assert rate == Decimal("4.10") # Course for 2nd number
    assert mock_get.call_count == 1 # There was exactly 1 request to the network
    
    # We request a rate for January 4 (T-1 = January 3).
    # There should NOT be a request to the network; the data is already in the cache.
    rate2 = get_nbp_rate("USD", "2025-01-04")
    assert rate2 == Decimal("4.15")
    assert mock_get.call_count == 1 # The request counter has not changed!

@patch('src.nbp.requests.get')
def test_weekend_lookback(mock_get):
    # Weekend test: Mon 6.01, take the course for Fri 3.01 (T-1=5 (Sun), T-2=4 (Sat), T-3=3 (Fri))
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
