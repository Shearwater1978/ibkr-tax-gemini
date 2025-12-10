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
        
        assert "AND Ticker = :ticker" in query