from decimal import Decimal
from src.nbp import get_nbp_rate
from unittest.mock import patch, mock_open
import src.nbp

# FIX: Function to clear cache before each test run
def setup_function():
    src.nbp._MEMORY_CACHE.clear()

@patch('src.nbp.os.path.exists', return_value=False)
@patch('src.nbp.requests.get')
def test_get_nbp_rate_success(mock_get, mock_exists):
    mock_get.return_value.status_code = 200
    mock_get.return_value.json.return_value = {
        "rates": [
            {"effectiveDate": "2022-01-01", "mid": 4.1234},
            {"effectiveDate": "2022-01-03", "mid": 4.2000}
        ]
    }
    
    with patch("builtins.open", mock_open()):
        rate = get_nbp_rate("USD", "2022-01-03")
        
    assert rate == Decimal("4.2")

@patch('src.nbp.os.path.exists', return_value=False)
@patch('src.nbp.requests.get')
def test_get_nbp_rate_holiday_recursion(mock_get, mock_exists):
    # Setup DIFFERENT mock data
    mock_get.return_value.status_code = 200
    mock_get.return_value.json.return_value = {
        "rates": [
            {"effectiveDate": "2022-01-01", "mid": 4.1000},
            {"effectiveDate": "2022-01-04", "mid": 4.2000}
        ]
    }

    with patch("builtins.open", mock_open()):
        # Since we cleared cache in setup_function, this will call API again
        rate = get_nbp_rate("USD", "2022-01-02")
    
    # 2022-01-02 missing -> recurses to 2022-01-01 -> finds 4.1000
    assert rate == Decimal("4.1")
