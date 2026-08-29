# tests/test_ib_connector.py

import pytest
from unittest.mock import patch, MagicMock

from src.ib_connector import IBConnector, IBConnectionError, classify_ib_error


@pytest.fixture
def mock_ib_class():
    with patch("src.ib_connector.IB") as mock_ib_cls, patch(
        "src.ib_connector.IB_LIVE_ENABLED", True
    ):
        mock_instance = MagicMock()
        mock_ib_cls.return_value = mock_instance
        yield mock_ib_cls, mock_instance


def test_connect_success(mock_ib_class):
    mock_ib_cls, mock_instance = mock_ib_class

    connector = IBConnector(host="127.0.0.1", port=4002, client_id=1)
    connector.connect()

    mock_instance.connect.assert_called_once_with(
        "127.0.0.1", 4002, clientId=1, timeout=connector.timeout
    )
    assert connector.ib is mock_instance


def test_connect_failure_raises_clear_error(mock_ib_class):
    mock_ib_cls, mock_instance = mock_ib_class
    mock_instance.connect.side_effect = ConnectionRefusedError("refused")

    connector = IBConnector(host="127.0.0.1", port=4002, client_id=1)

    with pytest.raises(IBConnectionError, match="Could not connect to IB Gateway"):
        connector.connect()
    assert connector.ib is None


def test_connect_without_ib_insync_installed():
    with patch("src.ib_connector.IB", None), patch(
        "src.ib_connector.IB_LIVE_ENABLED", True
    ):
        connector = IBConnector()
        with pytest.raises(IBConnectionError, match="ib_insync is unavailable"):
            connector.connect()


def test_connect_when_live_api_not_enabled():
    with patch("src.ib_connector.IB_LIVE_ENABLED", False):
        connector = IBConnector()
        with pytest.raises(IBConnectionError, match="not enabled"):
            connector.connect()


def test_health_check_without_connection_raises():
    connector = IBConnector()
    with pytest.raises(IBConnectionError, match="No active IB Gateway"):
        connector.health_check()


def test_health_check_success(mock_ib_class):
    mock_ib_cls, mock_instance = mock_ib_class
    mock_instance.isConnected.return_value = True
    mock_instance.reqCurrentTime.return_value = "2026-08-29T00:00:00"

    connector = IBConnector()
    connector.connect()

    result = connector.health_check()
    assert result == "2026-08-29T00:00:00"


def test_health_check_server_unresponsive(mock_ib_class):
    mock_ib_cls, mock_instance = mock_ib_class
    mock_instance.isConnected.return_value = True
    mock_instance.reqCurrentTime.side_effect = TimeoutError("no response")

    connector = IBConnector()
    connector.connect()

    with pytest.raises(IBConnectionError, match="did not respond"):
        connector.health_check()


def test_disconnect_closes_session(mock_ib_class):
    mock_ib_cls, mock_instance = mock_ib_class
    mock_instance.isConnected.return_value = True

    connector = IBConnector()
    connector.connect()
    connector.disconnect()

    mock_instance.disconnect.assert_called_once()
    assert connector.ib is None


def test_context_manager_disconnects_on_exit(mock_ib_class):
    mock_ib_cls, mock_instance = mock_ib_class
    mock_instance.isConnected.return_value = True

    with IBConnector() as connector:
        assert connector.is_connected()

    mock_instance.disconnect.assert_called_once()


def _make_contract(symbol="AAPL", currency="USD"):
    contract = MagicMock()
    contract.symbol = symbol
    contract.currency = currency
    return contract


def test_get_account_summary_requires_connection():
    connector = IBConnector()
    with pytest.raises(IBConnectionError, match="No active IB Gateway"):
        connector.get_account_summary()


def test_get_account_summary_returns_structured_rows(mock_ib_class):
    mock_ib_cls, mock_instance = mock_ib_class
    mock_instance.isConnected.return_value = True
    row = MagicMock(account="DU123", tag="NetLiquidation", value="1000", currency="USD")
    mock_instance.accountSummary.return_value = [row]

    connector = IBConnector()
    connector.connect()

    result = connector.get_account_summary()
    assert result == [
        {
            "account": "DU123",
            "tag": "NetLiquidation",
            "value": "1000",
            "currency": "USD",
        }
    ]


def test_get_positions_returns_structured_rows(mock_ib_class):
    mock_ib_cls, mock_instance = mock_ib_class
    mock_instance.isConnected.return_value = True
    row = MagicMock(
        account="DU123", contract=_make_contract(), position=10, avgCost=150.5
    )
    mock_instance.positions.return_value = [row]

    connector = IBConnector()
    connector.connect()

    result = connector.get_positions()
    assert result == [
        {
            "account": "DU123",
            "symbol": "AAPL",
            "currency": "USD",
            "position": 10.0,
            "avg_cost": 150.5,
        }
    ]


def test_get_fills_returns_structured_rows(mock_ib_class):
    mock_ib_cls, mock_instance = mock_ib_class
    mock_instance.isConnected.return_value = True
    fill = MagicMock()
    fill.contract = _make_contract()
    fill.execution.acctNumber = "DU123"
    fill.execution.side = "BOT"
    fill.execution.shares = 5
    fill.execution.price = 190.25
    fill.execution.execId = "exec-1"
    fill.execution.time = "2026-08-29"
    fill.commissionReport.commission = 1.5
    mock_instance.fills.return_value = [fill]

    connector = IBConnector()
    connector.connect()

    result = connector.get_fills()
    assert result == [
        {
            "account": "DU123",
            "symbol": "AAPL",
            "currency": "USD",
            "side": "BOT",
            "shares": 5.0,
            "price": 190.25,
            "commission": 1.5,
            "exec_id": "exec-1",
            "time": "2026-08-29",
        }
    ]


def test_get_open_orders_returns_structured_rows(mock_ib_class):
    mock_ib_cls, mock_instance = mock_ib_class
    mock_instance.isConnected.return_value = True
    order = MagicMock(account="DU123", action="BUY", totalQuantity=3, orderType="LMT")
    order.contract = _make_contract()
    mock_instance.reqAllOpenOrders.return_value = [order]

    connector = IBConnector()
    connector.connect()

    result = connector.get_open_orders()
    assert result == [
        {
            "account": "DU123",
            "symbol": "AAPL",
            "action": "BUY",
            "quantity": 3.0,
            "order_type": "LMT",
        }
    ]


def test_fetch_account_snapshot_aggregates_all_payloads(mock_ib_class):
    mock_ib_cls, mock_instance = mock_ib_class
    mock_instance.isConnected.return_value = True
    mock_instance.accountSummary.return_value = []
    mock_instance.positions.return_value = []
    mock_instance.fills.return_value = []
    mock_instance.reqAllOpenOrders.return_value = []

    connector = IBConnector()
    connector.connect()

    snapshot = connector.fetch_account_snapshot()
    assert set(snapshot.keys()) == {
        "account_summary",
        "positions",
        "fills",
        "open_orders",
    }


def test_classify_ib_error_known_codes():
    assert classify_ib_error(1100) == "session_loss"
    assert classify_ib_error(1300) == "auth"
    assert classify_ib_error(10167) == "permission"
    assert classify_ib_error(502) == "connection"


def test_classify_ib_error_unknown_code_is_not_dropped():
    assert classify_ib_error(999999) == "unknown"


def test_on_error_records_diagnostic(mock_ib_class):
    mock_ib_cls, mock_instance = mock_ib_class
    mock_instance.isConnected.return_value = True

    connector = IBConnector()
    connector.connect()
    connector._on_error(1, 1100, "Connectivity between IB and TWS has been lost")

    errors = connector.get_errors()
    assert len(errors) == 1
    assert errors[0].code == 1100
    assert errors[0].category == "session_loss"


def test_raise_if_session_lost_raises_actionable_error(mock_ib_class):
    mock_ib_cls, mock_instance = mock_ib_class
    mock_instance.isConnected.return_value = True

    connector = IBConnector()
    connector.connect()
    connector._on_error(1, 1100, "Connectivity between IB and TWS has been lost")

    with pytest.raises(IBConnectionError, match="session was lost"):
        connector.raise_if_session_lost()


def test_raise_if_auth_error_raises_actionable_error(mock_ib_class):
    mock_ib_cls, mock_instance = mock_ib_class
    mock_instance.isConnected.return_value = True

    connector = IBConnector()
    connector.connect()
    connector._on_error(1, 1300, "Session connected from a different IP")

    with pytest.raises(IBConnectionError, match="authentication issue"):
        connector.raise_if_auth_error()


def test_raise_if_permission_error_raises_actionable_error(mock_ib_class):
    mock_ib_cls, mock_instance = mock_ib_class
    mock_instance.isConnected.return_value = True

    connector = IBConnector()
    connector.connect()
    connector._on_error(1, 10167, "Requested market data is not subscribed")

    with pytest.raises(
        IBConnectionError, match="denied a permission-restricted request"
    ):
        connector.raise_if_permission_error()


def test_health_check_raises_on_session_loss(mock_ib_class):
    mock_ib_cls, mock_instance = mock_ib_class
    mock_instance.isConnected.return_value = True

    connector = IBConnector()
    connector.connect()
    connector._on_error(1, 1100, "Connectivity between IB and TWS has been lost")

    with pytest.raises(IBConnectionError, match="session was lost"):
        connector.health_check()


def test_raise_if_critical_errors_does_not_raise_on_permission_only(mock_ib_class):
    mock_ib_cls, mock_instance = mock_ib_class
    mock_instance.isConnected.return_value = True

    connector = IBConnector()
    connector.connect()
    connector._on_error(1, 10167, "Requested market data is not subscribed")

    connector.raise_if_critical_errors()  # permission errors are not connection-breaking
