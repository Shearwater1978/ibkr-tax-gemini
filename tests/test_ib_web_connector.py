# tests/test_ib_web_connector.py

import pytest
from unittest.mock import patch, MagicMock

from src.ib_web_connector import IBWebConnector, IBWebConnectionError


@pytest.fixture
def mock_requests():
    with patch("src.ib_web_connector.requests") as mock_requests_module, patch(
        "src.ib_web_connector.IB_WEB_API_ENABLED", True
    ):
        mock_session = MagicMock()
        mock_requests_module.Session.return_value = mock_session
        yield mock_requests_module, mock_session


def test_connect_initializes_brokerage_session(mock_requests):
    mock_requests_module, mock_session = mock_requests
    mock_session.post.return_value = MagicMock(raise_for_status=lambda: None)

    connector = IBWebConnector(base_url="https://localhost:5000/v1/api")
    connector.connect()

    mock_session.post.assert_called_once_with(
        "https://localhost:5000/v1/api/iserver/auth/ssodh/init",
        json={},
        verify=connector.verify_ssl,
        timeout=connector.timeout,
    )
    assert connector.is_connected()


def test_connect_fails_clearly_when_cpgw_unreachable(mock_requests):
    mock_requests_module, mock_session = mock_requests
    mock_session.post.side_effect = ConnectionError("refused")

    connector = IBWebConnector(base_url="https://localhost:5000/v1/api")

    with pytest.raises(
        IBWebConnectionError, match="Could not initialize a brokerage session"
    ):
        connector.connect()
    assert connector.session is None


def test_connect_fails_clearly_on_http_error_status(mock_requests):
    mock_requests_module, mock_session = mock_requests

    def raise_for_status():
        raise Exception("500 Server Error")

    mock_session.post.return_value = MagicMock(raise_for_status=raise_for_status)

    connector = IBWebConnector(base_url="https://localhost:5000/v1/api")

    with pytest.raises(
        IBWebConnectionError, match="Could not initialize a brokerage session"
    ):
        connector.connect()


def test_connect_when_web_api_not_enabled():
    with patch("src.ib_web_connector.IB_WEB_API_ENABLED", False):
        connector = IBWebConnector()
        with pytest.raises(IBWebConnectionError, match="not enabled"):
            connector.connect()


def test_connect_without_requests_installed():
    with patch("src.ib_web_connector.requests", None), patch(
        "src.ib_web_connector.IB_WEB_API_ENABLED", True
    ):
        connector = IBWebConnector()
        with pytest.raises(IBWebConnectionError, match="requests is unavailable"):
            connector.connect()


def test_disconnect_closes_session(mock_requests):
    mock_requests_module, mock_session = mock_requests
    mock_session.post.return_value = MagicMock(raise_for_status=lambda: None)

    connector = IBWebConnector()
    connector.connect()
    connector.disconnect()

    mock_session.close.assert_called_once()
    assert connector.session is None
    assert not connector.is_connected()


def test_context_manager_disconnects_on_exit(mock_requests):
    mock_requests_module, mock_session = mock_requests
    mock_session.post.return_value = MagicMock(raise_for_status=lambda: None)

    with IBWebConnector() as connector:
        assert connector.is_connected()

    mock_session.close.assert_called_once()


def _connected_connector(mock_session):
    mock_session.post.return_value = MagicMock(raise_for_status=lambda: None)
    connector = IBWebConnector()
    connector.connect()
    mock_session.post.reset_mock()
    return connector


def test_auth_status_requires_connection():
    connector = IBWebConnector()
    with pytest.raises(IBWebConnectionError, match="No active Client Portal Gateway"):
        connector.auth_status()


def test_auth_status_returns_json_payload(mock_requests):
    mock_requests_module, mock_session = mock_requests
    connector = _connected_connector(mock_session)
    mock_session.post.return_value = MagicMock(
        raise_for_status=lambda: None,
        json=lambda: {"authenticated": True, "connected": True},
    )

    status = connector.auth_status()

    mock_session.post.assert_called_once_with(
        f"{connector.base_url}/iserver/auth/status",
        json={},
        verify=connector.verify_ssl,
        timeout=connector.timeout,
    )
    assert status == {"authenticated": True, "connected": True}


def test_health_check_passes_when_authenticated(mock_requests):
    mock_requests_module, mock_session = mock_requests
    connector = _connected_connector(mock_session)
    mock_session.post.return_value = MagicMock(
        raise_for_status=lambda: None, json=lambda: {"authenticated": True}
    )

    result = connector.health_check()
    assert result["authenticated"] is True


def test_health_check_raises_when_not_authenticated(mock_requests):
    mock_requests_module, mock_session = mock_requests
    connector = _connected_connector(mock_session)
    mock_session.post.return_value = MagicMock(
        raise_for_status=lambda: None, json=lambda: {"authenticated": False}
    )

    with pytest.raises(IBWebConnectionError, match="Log in via the browser"):
        connector.health_check()


def test_tickle_requires_connection():
    connector = IBWebConnector()
    with pytest.raises(IBWebConnectionError, match="No active Client Portal Gateway"):
        connector.tickle()


def test_tickle_sends_keep_alive_request(mock_requests):
    mock_requests_module, mock_session = mock_requests
    connector = _connected_connector(mock_session)
    mock_session.post.return_value = MagicMock(
        raise_for_status=lambda: None, json=lambda: {"session": "abc"}
    )

    result = connector.tickle()

    mock_session.post.assert_called_once_with(
        f"{connector.base_url}/tickle",
        json={},
        verify=connector.verify_ssl,
        timeout=connector.timeout,
    )
    assert result == {"session": "abc"}


def test_tickle_raises_clear_error_on_failure(mock_requests):
    mock_requests_module, mock_session = mock_requests
    connector = _connected_connector(mock_session)
    mock_session.post.side_effect = Exception("boom")

    with pytest.raises(
        IBWebConnectionError, match="keep-alive .tickle. request failed"
    ):
        connector.tickle()


def test_get_accounts_requires_connection():
    connector = IBWebConnector()
    with pytest.raises(IBWebConnectionError, match="No active Client Portal Gateway"):
        connector.get_accounts()


def test_get_accounts_returns_structured_rows(mock_requests):
    mock_requests_module, mock_session = mock_requests
    connector = _connected_connector(mock_session)
    mock_session.get.return_value = MagicMock(
        raise_for_status=lambda: None,
        json=lambda: [
            {
                "accountId": "U123",
                "accountTitle": "Test",
                "currency": "USD",
                "type": "INDIVIDUAL",
            }
        ],
    )

    result = connector.get_accounts()

    mock_session.get.assert_called_once_with(
        f"{connector.base_url}/portfolio/accounts",
        verify=connector.verify_ssl,
        timeout=connector.timeout,
    )
    assert result == [
        {
            "account_id": "U123",
            "account_title": "Test",
            "currency": "USD",
            "type": "INDIVIDUAL",
        }
    ]


def test_get_accounts_raises_on_malformed_response(mock_requests):
    mock_requests_module, mock_session = mock_requests
    connector = _connected_connector(mock_session)
    mock_session.get.return_value = MagicMock(
        raise_for_status=lambda: None, json=lambda: {"not": "a list"}
    )

    with pytest.raises(IBWebConnectionError, match="expected a JSON array"):
        connector.get_accounts()


def test_get_positions_returns_structured_rows(mock_requests):
    mock_requests_module, mock_session = mock_requests
    connector = _connected_connector(mock_session)
    mock_session.get.return_value = MagicMock(
        raise_for_status=lambda: None,
        json=lambda: [
            {
                "acctId": "U123",
                "conid": 13824,
                "contractDesc": "WMT",
                "position": 3.0,
                "avgCost": 46.12,
                "currency": "USD",
                "assetClass": "STK",
            }
        ],
    )

    result = connector.get_positions("U123", page=0)

    mock_session.get.assert_called_once_with(
        f"{connector.base_url}/portfolio/U123/positions/0",
        verify=connector.verify_ssl,
        timeout=connector.timeout,
    )
    assert result == [
        {
            "account_id": "U123",
            "conid": 13824,
            "symbol": "WMT",
            "position": 3.0,
            "avg_cost": 46.12,
            "currency": "USD",
            "asset_class": "STK",
        }
    ]


def test_get_trades_returns_structured_rows(mock_requests):
    mock_requests_module, mock_session = mock_requests
    connector = _connected_connector(mock_session)
    mock_session.get.return_value = MagicMock(
        raise_for_status=lambda: None,
        json=lambda: [
            {
                "execution_id": "exec-1",
                "account": "U123",
                "symbol": "AAPL",
                "side": "B",
                "size": 10,
                "price": "150.00",
                "currency": "USD",
                "commission": "1.00",
                "trade_time": "20260829-14:30:00",
            }
        ],
    )

    result = connector.get_trades()

    mock_session.get.assert_called_once_with(
        f"{connector.base_url}/iserver/account/trades",
        verify=connector.verify_ssl,
        timeout=connector.timeout,
    )
    assert result == [
        {
            "execution_id": "exec-1",
            "account_id": "U123",
            "symbol": "AAPL",
            "side": "B",
            "size": 10,
            "price": "150.00",
            "currency": "USD",
            "commission": "1.00",
            "trade_time": "20260829-14:30:00",
        }
    ]


def test_get_trades_raises_clear_error_on_request_failure(mock_requests):
    mock_requests_module, mock_session = mock_requests
    connector = _connected_connector(mock_session)
    mock_session.get.side_effect = Exception("boom")

    with pytest.raises(IBWebConnectionError, match="Failed to request trades"):
        connector.get_trades()
