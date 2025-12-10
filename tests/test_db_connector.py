import pytest
from unittest.mock import patch
from src.db_connector import DBConnector

def test_db_connector_init():
    # Проверяем, что без ключа падает ошибка (если конфиг пустой)
    with patch('src.db_connector.config') as mock_config:
        mock_config.return_value = ''
        with pytest.raises(ValueError):
            DBConnector()
