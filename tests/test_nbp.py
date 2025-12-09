import pytest
from decimal import Decimal
from unittest.mock import patch, mock_open, MagicMock
import src 
# УДАЛЯЕМ ИМПОРТ get_nbp_rate и _MEMORY_CACHE ИЗ ГЛОБАЛЬНОЙ ОБЛАСТИ
import requests 

# --- Мок-функции для side_effect (как было) ---

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


# --- ТЕСТЫ БЕЗ ДЕКОРАТОРОВ ---

def test_get_nbp_rate_success():
    
    # 🚨 ФИНАЛЬНЫЙ FIX: Мокируем и очищаем кеш перед импортом функции
    with patch('src.nbp._MEMORY_CACHE', new_callable=MagicMock) as mock_cache:
        
        # Убедимся, что кеш пуст
        mock_cache.clear() 
        mock_cache.__contains__.return_value = False 
        
        # Импортируем функцию ПОСЛЕ мокирования кеша
        from src.nbp import get_nbp_rate 

        with patch('src.nbp.requests.get', side_effect=mock_requests_get_success) as mock_get:
            with patch('src.nbp.os.path.exists', return_value=False):
                with patch("builtins.open", mock_open()):
                    
                    rate = get_nbp_rate("USD", "2022-01-03")
                    
                    mock_get.assert_called_once()
                
                assert rate == Decimal("4.2") 


def test_get_nbp_rate_holiday_recursion():
    
    # 🚨 ФИНАЛЬНЫЙ FIX: Мокируем и очищаем кеш перед импортом функции
    with patch('src.nbp._MEMORY_CACHE', new_callable=MagicMock) as mock_cache:
        
        mock_cache.clear()
        mock_cache.__contains__.return_value = False 
        
        # Импортируем функцию ПОСЛЕ мокирования кеша
        from src.nbp import get_nbp_rate 

        with patch('src.nbp.requests.get', side_effect=mock_requests_get_holiday) as mock_get:
            with patch('src.nbp.os.path.exists', return_value=False):
                with patch("builtins.open", mock_open()):
                    
                    rate = get_nbp_rate("USD", "2022-01-02")
                
                # Проверяем вызов API (должен быть 2 раза)
                assert mock_get.call_count == 2
                assert rate == Decimal("4.1")