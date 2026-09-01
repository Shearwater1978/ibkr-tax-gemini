# tests/test_ib_sync.py

from unittest.mock import patch, MagicMock

import main
from src.ib_connector import IBConnectionError
from src.ib_web_connector import IBWebConnectionError


def _mock_connector(snapshot):
    mock_instance = MagicMock()
    mock_instance.health_check.return_value = None
    mock_instance.fetch_account_snapshot.return_value = snapshot
    mock_instance.__enter__.return_value = mock_instance
    mock_instance.__exit__.return_value = False
    return mock_instance


def test_run_ib_sync_routine_success():
    snapshot = {"fills": [], "positions": [], "account_summary": [], "open_orders": []}
    normalized = {
        "trades": [{"ticker": "AAPL"}],
        "dividends": [],
        "taxes": [],
        "corp_actions": [],
    }

    with patch.object(
        main, "IBConnector", return_value=_mock_connector(snapshot)
    ), patch.object(main, "normalize_snapshot", return_value=normalized), patch.object(
        main, "save_to_database", return_value={"inserted": 1, "skipped": 0}
    ):
        result = main.run_ib_sync_routine()

    assert result["status"] == "success"
    assert result["inserted"] == 1
    assert result["skipped"] == 0


def test_run_ib_sync_routine_no_new_data():
    snapshot = {"fills": [], "positions": [], "account_summary": [], "open_orders": []}
    normalized = {"trades": [], "dividends": [], "taxes": [], "corp_actions": []}

    with patch.object(
        main, "IBConnector", return_value=_mock_connector(snapshot)
    ), patch.object(main, "normalize_snapshot", return_value=normalized):
        result = main.run_ib_sync_routine()

    assert result["status"] == "success"
    assert result["inserted"] == 0
    assert result["skipped"] == 0


def test_run_ib_sync_routine_connection_failure_reports_error():
    with patch.object(
        main, "IBConnector", side_effect=IBConnectionError("Gateway unreachable")
    ):
        result = main.run_ib_sync_routine()

    assert result["status"] == "error"
    assert "Gateway unreachable" in result["message"]
    assert result["inserted"] == 0
    assert result["skipped"] == 0


def test_run_ib_sync_routine_does_not_import_parser_csv_path():
    """Live sync must not touch parse_csv/run_import_routine's file-based path."""
    with patch.object(
        main, "IBConnector", side_effect=IBConnectionError("down")
    ), patch.object(main, "parse_csv") as mock_parse_csv:
        main.run_ib_sync_routine()
        mock_parse_csv.assert_not_called()


def test_run_ib_web_sync_routine_success():
    snapshot = {"trades": [], "positions": [], "accounts": []}
    normalized = {
        "trades": [{"ticker": "AAPL"}],
        "dividends": [],
        "taxes": [],
        "corp_actions": [],
    }

    with patch.object(
        main, "IBWebConnector", return_value=_mock_connector(snapshot)
    ), patch.object(
        main, "normalize_web_snapshot", return_value=normalized
    ), patch.object(
        main, "save_to_database", return_value={"inserted": 1, "skipped": 0}
    ):
        result = main.run_ib_web_sync_routine()

    assert result == {
        "status": "success",
        "message": "IB Web API sync finished",
        "inserted": 1,
        "skipped": 0,
    }


def test_run_ib_web_sync_routine_reports_connection_failure():
    with patch.object(
        main, "IBWebConnector", side_effect=IBWebConnectionError("Log in via CPGW")
    ):
        result = main.run_ib_web_sync_routine()

    assert result["status"] == "error"
    assert result["message"] == "Log in via CPGW"
