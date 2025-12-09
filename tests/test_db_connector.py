# tests/test_db_connector.py

import pytest
import sqlite3
from unittest.mock import patch, MagicMock
from src.db_connector import DBConnector
from typing import Dict
import sys

# --- Configuration Mocks ---

DB_KEY = "test_super_secret_key"
DB_PATH = "test/path/to/db.db"

@pytest.fixture
def mock_decouple_config():
    """Mocks the decouple.config function to provide test key and path."""
    with patch('src.db_connector.config') as mock_config:
        # FIX: Use *args, **kwargs to safely capture 'default=' passed by DBConnector
        def side_effect(key, *args, **kwargs):
            if key == 'DATABASE_PATH':
                return DB_PATH
            if key == 'SQLCIPHER_KEY':
                return DB_KEY
            # Handle the actual default argument safely
            return kwargs.get('default', '') 
        mock_config.side_effect = side_effect
        yield mock_config

@pytest.fixture
def mock_db_connection():
    """Mocks the sqlite3.connect call."""
    with patch('src.db_connector.sqlite3.connect') as mock_connect:
        # Mock the connection object itself
        mock_conn = MagicMock(spec=sqlite3.Connection)
        mock_connect.return_value = mock_conn
        
        # Mock the cursor to return a specific row factory
        mock_conn.row_factory = sqlite3.Row
        
        # Mock fetchall to return some dummy data (if needed)
        mock_cursor = MagicMock()
        mock_cursor.fetchall.return_value = []
        mock_conn.execute.return_value = mock_cursor
        
        yield mock_connect, mock_conn

# --- Test Cases ---

def test_initialization_missing_key(mock_decouple_config):
    """Tests that a ValueError is raised if SQLCIPHER_KEY is empty."""
    with patch('src.db_connector.config') as mock_config_missing:
        # FIX: Use *args, **kwargs in the side_effect for the same reason
        mock_config_missing.side_effect = lambda k, *args, **kwargs: '' if k == 'SQLCIPHER_KEY' else DB_PATH
        
        with pytest.raises(ValueError) as excinfo:
            DBConnector()
        assert "SQLCIPHER_KEY is not set" in str(excinfo.value)

def test_connection_success(mock_decouple_config, mock_db_connection):
    """Tests that connection opens and PRAGMA key is executed."""
    mock_connect, mock_conn = mock_db_connection
    
    with DBConnector() as db:
        # 1. Check if connect was called with the correct path
        mock_connect.assert_called_once_with(DB_PATH)
        
        # 2. Check if PRAGMA key was executed with the secret key
        expected_pragma = f"PRAGMA key='{DB_KEY}';"
        mock_conn.execute.assert_any_call(expected_pragma)
        
        # 3. Check if the connection object is stored
        assert db.conn is mock_conn

def test_connection_closed_on_exit(mock_decouple_config, mock_db_connection):
    """Tests that the connection is closed when exiting the context manager."""
    mock_connect, mock_conn = mock_db_connection
    
    with DBConnector():
        pass
    
    mock_conn.close.assert_called_once()


# --- Test Data Retrieval and Filtering Logic ---

def get_expected_query(filter_condition: str) -> str:
    """Helper to construct the expected final SQL query base (without ORDER BY)."""
    base_query = (
        "SELECT * FROM transactions WHERE 1=1 AND (Type='BUY' OR Date BETWEEN :start_date AND :end_date)"
    )
    if filter_condition:
        return f"{base_query} {filter_condition}"
    return base_query

def test_get_trades_no_ticker_filter(mock_decouple_config, mock_db_connection):
    """Tests the SQL query generated when filtering only by year."""
    mock_connect, mock_conn = mock_db_connection
    target_year = 2024
    
    with DBConnector() as db:
        db.get_trades_for_calculation(target_year=target_year, ticker=None)
        
        expected_query_base = get_expected_query("")
        
        expected_params: Dict[str, str] = {
            'start_date': '2024-01-01',
            'end_date': '2024-12-31'
        }
        
        # The first call is PRAGMA, the second is the query.
        call_args, call_kwargs = mock_conn.execute.call_args_list[1]
        
        # We manually remove the final ' ORDER BY...' from the called query for comparison
        called_query_base = call_args[0].strip().rsplit(' ORDER BY', 1)[0]
        
        assert called_query_base == expected_query_base
        assert call_args[1] == expected_params


def test_get_trades_with_ticker_filter(mock_decouple_config, mock_db_connection):
    """Tests the SQL query generated when filtering by year and ticker."""
    mock_connect, mock_conn = mock_db_connection
    target_year = 2025
    target_ticker = "GOOGL"
    
    with DBConnector() as db:
        db.get_trades_for_calculation(target_year=target_year, ticker=target_ticker)
        
        expected_query_base = get_expected_query("AND Ticker = :ticker")
        
        expected_params: Dict[str, str] = {
            'start_date': '2025-01-01',
            'end_date': '2025-12-31',
            'ticker': target_ticker
        }
        
        # The first call is PRAGMA, the second is the query.
        call_args, call_kwargs = mock_conn.execute.call_args_list[1]
        
        # We manually remove the final ' ORDER BY...' from the called query for comparison
        called_query_base = call_args[0].strip().rsplit(' ORDER BY', 1)[0]
        
        assert called_query_base == expected_query_base
        assert call_args[1] == expected_params