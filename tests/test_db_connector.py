# tests/test_db_connector.py

import pytest
import sqlite3
import os
from unittest.mock import patch, MagicMock
from src.db_connector import DBConnector

DB_KEY = "test_key"
DB_PATH = "db/test.db"  # Use a path with a directory component


@pytest.fixture
def mock_db_connection():
    # 1. Patch sqlite3.connect to avoid real DB
    # 2. Patch os.makedirs to avoid FileNotFoundError on empty paths or permission issues
    with patch("src.db_connector.sqlite3.connect") as mock_connect, patch(
        "src.db_connector.os.makedirs"
    ) as mock_makedirs:

        mock_conn = MagicMock()
        mock_conn.row_factory = sqlite3.Row
        mock_connect.return_value = mock_conn
        yield mock_connect, mock_conn


def test_get_trades_no_ticker_filter(mock_db_connection):
    mock_connect, mock_conn = mock_db_connection

    with patch("src.db_connector.DB_PATH", DB_PATH), patch(
        "src.db_connector.DB_KEY", DB_KEY
    ):

        with DBConnector() as db:
            db.get_trades_for_calculation(2024, None)

            call_args = mock_conn.execute.call_args
            query = call_args[0][0]
            assert "EventType" in query


def test_get_trades_with_ticker_filter(mock_db_connection):
    mock_connect, mock_conn = mock_db_connection

    with patch("src.db_connector.DB_PATH", DB_PATH), patch(
        "src.db_connector.DB_KEY", DB_KEY
    ):

        with DBConnector() as db:
            db.get_trades_for_calculation(2024, "AAPL")
            call_args = mock_conn.execute.call_args
            query = call_args[0][0]
            assert "AND Ticker = ?" in query
