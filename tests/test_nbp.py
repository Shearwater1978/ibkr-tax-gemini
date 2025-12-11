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
