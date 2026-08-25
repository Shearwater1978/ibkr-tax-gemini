# tests/test_db_connector.py

import pytest
from unittest.mock import patch, MagicMock
from src.db_connector import DBConnector, DBConnectorError

DB_KEY = "test_key"
DB_PATH = "db/test.db"  # Use a path with a directory component


@pytest.fixture
def mock_db_connection():
    with patch("src.db_connector.sqlcipher") as mock_sqlcipher, patch(
        "src.db_connector.os.makedirs"
    ) as mock_makedirs:

        mock_conn = MagicMock()
        mock_sqlcipher.connect.return_value = mock_conn
        mock_sqlcipher.Row = object
        mock_conn.execute.return_value.fetchone.return_value = ("3.4.0",)
        yield mock_sqlcipher.connect, mock_conn


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


def test_missing_sqlcipher_driver_fails_closed(tmp_path):
    with patch("src.db_connector.sqlcipher", None):
        with pytest.raises(DBConnectorError, match="SQLCipher driver is unavailable"):
            DBConnector(db_path=str(tmp_path / "database.db"), key="secret").connect()


def test_missing_key_fails_closed(tmp_path):
    with patch("src.db_connector.sqlcipher"):
        with pytest.raises(DBConnectorError, match="SQLCIPHER_KEY is required"):
            DBConnector(db_path=str(tmp_path / "database.db"), key="").connect()


def test_wrong_key_is_reported_without_secret(mock_db_connection):
    mock_connect, mock_conn = mock_db_connection
    mock_conn.execute.side_effect = [RuntimeError("invalid key")]

    with pytest.raises(DBConnectorError, match="Could not open the encrypted database"):
        DBConnector(db_path="db/test.db", key="wrong-secret").connect()

    mock_connect.assert_called_once_with("db/test.db")
    assert "wrong-secret" not in str(mock_conn.execute.side_effect)


def test_change_password_escapes_password_for_pragma(mock_db_connection):
    _, mock_conn = mock_db_connection
    connector = DBConnector(db_path=DB_PATH, key=DB_KEY)
    connector.conn = mock_conn

    assert connector.change_password("new-secret") is True

    rekey_call = next(
        call for call in mock_conn.execute.call_args_list
        if call.args[0].startswith("PRAGMA rekey")
    )
    assert rekey_call.args[0] == "PRAGMA rekey = 'new-secret'"


def test_change_password_escapes_single_quotes(mock_db_connection):
    _, mock_conn = mock_db_connection
    connector = DBConnector(db_path=DB_PATH, key=DB_KEY)
    connector.conn = mock_conn

    assert connector.change_password("new'password") is True
    rekey_call = next(
        call for call in mock_conn.execute.call_args_list
        if call.args[0].startswith("PRAGMA rekey")
    )
    assert rekey_call.args[0] == "PRAGMA rekey = 'new''password'"


def test_real_sqlcipher_key_rotation(tmp_path):
    pytest.importorskip("sqlcipher3")
    database_path = str(tmp_path / "encrypted.db")

    connector = DBConnector(database_path, key="old-secret")
    connector.connect()
    connector.initialize_schema()
    connector.close()

    with pytest.raises(DBConnectorError):
        DBConnector(database_path, key="wrong-secret").connect()

    connector = DBConnector(database_path, key="old-secret")
    connector.connect()
    assert connector.change_password("new-secret") is True
    connector.close()

    connector = DBConnector(database_path, key="new-secret")
    connector.connect()
    connector.close()
