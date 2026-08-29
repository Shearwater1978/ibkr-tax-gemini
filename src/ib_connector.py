"""Connection manager for the Interactive Brokers TWS/IB Gateway socket API.

This module is intentionally isolated from the CSV import pipeline
(src/parser.py, src/data_collector.py). It only manages the live socket
connection and health checks; data normalization lives elsewhere.
"""

import os
from dataclasses import dataclass
from typing import Optional

try:
    from ib_insync import IB
except ImportError:
    IB = None

try:
    from decouple import config
except ImportError:

    def config(name, default=None, cast=None):
        value = os.getenv(name, default)
        if cast is bool and isinstance(value, str):
            return value.strip().lower() in ("1", "true", "yes", "on")
        return cast(value) if cast and value is not None else value


IB_HOST = config("IB_HOST", default="127.0.0.1")
IB_PORT = config("IB_PORT", default=4002, cast=int)
IB_CLIENT_ID = config("IB_CLIENT_ID", default=1, cast=int)
IB_CONNECT_TIMEOUT = config("IB_CONNECT_TIMEOUT", default=10, cast=int)

# Live IB connectivity is opt-in: CSV import must keep working with zero
# IB configuration, so nothing here should attempt a connection unless a
# user explicitly sets IB_LIVE_ENABLED=True in their environment/.env.
IB_LIVE_ENABLED = config("IB_LIVE_ENABLED", default=False, cast=bool)


class IBConnectionError(RuntimeError):
    """Raised when a live IB Gateway/TWS session cannot be established or verified."""

    def __init__(self, message, diagnostic=None):
        self.diagnostic = diagnostic
        super().__init__(message)


# Known IB TWS API error codes mapped to a diagnostic category. Codes not
# listed here classify as "unknown" but are still surfaced, never dropped.
IB_ERROR_CATEGORIES = {
    326: "connection",  # clientId already in use
    502: "connection",  # couldn't connect to TWS/Gateway
    503: "connection",  # TWS out of date
    504: "connection",  # not connected
    507: "connection",  # bad message
    1100: "session_loss",  # connectivity between IB and TWS lost
    1101: "session_loss",  # connectivity restored, data lost
    2110: "session_loss",  # connectivity between TWS and server broken
    1300: "auth",  # session connected from a different IP
    200: "permission",  # no security definition / not entitled
    354: "permission",  # requested market data not subscribed
    10167: "permission",  # market data not subscribed
}


@dataclass(frozen=True)
class IBDiagnostic:
    code: Optional[int]
    category: str
    message: str


def classify_ib_error(code):
    """Map an IB API error code to a diagnostic category."""
    return IB_ERROR_CATEGORIES.get(code, "unknown")


class IBConnector:
    """Manages a single read-only IB Gateway/TWS session.

    Usage:
        with IBConnector() as ib:
            ib.health_check()
    """

    def __init__(self, host=None, port=None, client_id=None, timeout=None):
        self.host = host if host is not None else IB_HOST
        self.port = port if port is not None else IB_PORT
        self.client_id = client_id if client_id is not None else IB_CLIENT_ID
        self.timeout = timeout if timeout is not None else IB_CONNECT_TIMEOUT
        self.ib = None
        self.errors = []

    def __enter__(self):
        self.connect()
        return self

    def __exit__(self, exc_type, exc_val, exc_tb):
        self.disconnect()

    def connect(self):
        if not IB_LIVE_ENABLED:
            raise IBConnectionError(
                "IB live API is not enabled. Set IB_LIVE_ENABLED=True in your "
                "environment/.env to opt in; CSV import does not require this."
            )
        if IB is None:
            raise IBConnectionError(
                "ib_insync is unavailable. Install dependencies from requirements.txt."
            )

        self.ib = IB()
        self.ib.errorEvent += self._on_error
        try:
            self.ib.connect(
                self.host, self.port, clientId=self.client_id, timeout=self.timeout
            )
        except Exception as exc:
            self.ib = None
            raise IBConnectionError(
                f"Could not connect to IB Gateway/TWS at {self.host}:{self.port} "
                f"(clientId={self.client_id}). Is Gateway running and reachable?"
            ) from exc

    def _on_error(self, reqId, errorCode, errorString, contract=None):
        """Record every IB API error/notification as a structured diagnostic.

        Never silently drops an error: unmapped codes are stored under the
        "unknown" category so callers can still inspect the raw message.
        """
        diagnostic = IBDiagnostic(
            code=errorCode,
            category=classify_ib_error(errorCode),
            message=errorString,
        )
        self.errors.append(diagnostic)

    def get_errors(self, category=None):
        """Return recorded diagnostics, optionally filtered by category."""
        if category is None:
            return list(self.errors)
        return [d for d in self.errors if d.category == category]

    def _raise_for_category(self, category, prefix):
        matches = self.get_errors(category)
        if matches:
            last = matches[-1]
            raise IBConnectionError(
                f"{prefix} (code {last.code}): {last.message}", diagnostic=last
            )

    def raise_if_session_lost(self):
        """Raise a clear IBConnectionError if the session reported connectivity loss."""
        self._raise_for_category("session_loss", "IB Gateway/TWS session was lost")

    def raise_if_auth_error(self):
        """Raise a clear IBConnectionError if the session reported an auth issue."""
        self._raise_for_category(
            "auth", "IB Gateway/TWS rejected the session (authentication issue)"
        )

    def raise_if_permission_error(self):
        """Raise a clear IBConnectionError if a request was denied by account permissions."""
        self._raise_for_category(
            "permission", "IB Gateway/TWS denied a permission-restricted request"
        )

    def raise_if_critical_errors(self):
        """Raise on any connection-breaking error category (session loss or auth)."""
        self.raise_if_session_lost()
        self.raise_if_auth_error()

    def disconnect(self):
        if self.ib is not None and self.ib.isConnected():
            self.ib.disconnect()
        self.ib = None

    def is_connected(self) -> bool:
        return self.ib is not None and self.ib.isConnected()

    def health_check(self):
        """Verify the session is live and the server accepts requests.

        Raises IBConnectionError with an actionable message if the
        connection is missing, was lost, was rejected, or is unresponsive.
        """
        if not self.is_connected():
            raise IBConnectionError(
                "No active IB Gateway/TWS session. Call connect() before health_check()."
            )
        self.raise_if_critical_errors()
        try:
            server_time = self.ib.reqCurrentTime()
        except Exception as exc:
            raise IBConnectionError(
                "IB Gateway/TWS session did not respond to a health check request."
            ) from exc
        self.raise_if_critical_errors()
        if server_time is None:
            raise IBConnectionError(
                "IB Gateway/TWS returned no server time; session may be stale."
            )
        return server_time

    def _require_connection(self):
        if not self.is_connected():
            raise IBConnectionError(
                "No active IB Gateway/TWS session. Call connect() before requesting data."
            )

    def get_account_summary(self) -> list:
        """Return read-only account summary rows as plain dicts."""
        self._require_connection()
        try:
            rows = self.ib.accountSummary()
        except Exception as exc:
            raise IBConnectionError(
                "Failed to request account summary from IB Gateway/TWS."
            ) from exc
        return [
            {
                "account": row.account,
                "tag": row.tag,
                "value": row.value,
                "currency": row.currency,
            }
            for row in rows
        ]

    def get_positions(self) -> list:
        """Return current open positions as plain dicts."""
        self._require_connection()
        try:
            rows = self.ib.positions()
        except Exception as exc:
            raise IBConnectionError(
                "Failed to request positions from IB Gateway/TWS."
            ) from exc
        return [
            {
                "account": row.account,
                "symbol": row.contract.symbol,
                "currency": row.contract.currency,
                "position": float(row.position),
                "avg_cost": float(row.avgCost),
            }
            for row in rows
        ]

    def get_fills(self) -> list:
        """Return executed trades (fills) as plain dicts.

        Fills reflect executions already known to this session; callers that
        need historical executions beyond the current session should use the
        broker's Flex/Activity report (existing CSV import path) instead.
        """
        self._require_connection()
        try:
            rows = self.ib.fills()
        except Exception as exc:
            raise IBConnectionError(
                "Failed to request fills from IB Gateway/TWS."
            ) from exc
        return [
            {
                "account": fill.execution.acctNumber,
                "symbol": fill.contract.symbol,
                "currency": fill.contract.currency,
                "side": fill.execution.side,
                "shares": float(fill.execution.shares),
                "price": float(fill.execution.price),
                "commission": (
                    float(fill.commissionReport.commission)
                    if fill.commissionReport
                    else None
                ),
                "exec_id": fill.execution.execId,
                "time": fill.execution.time,
            }
            for fill in rows
        ]

    def get_open_orders(self) -> list:
        """Return open orders as plain dicts (informational only)."""
        self._require_connection()
        try:
            rows = self.ib.reqAllOpenOrders() or self.ib.openOrders()
        except Exception as exc:
            raise IBConnectionError(
                "Failed to request open orders from IB Gateway/TWS."
            ) from exc
        return [
            {
                "account": order.account,
                "symbol": order.contract.symbol if order.contract else None,
                "action": order.action,
                "quantity": float(order.totalQuantity),
                "order_type": order.orderType,
            }
            for order in rows
        ]

    def fetch_account_snapshot(self) -> dict:
        """Collect the full read-only payload needed for normalization.

        Returns a structured dict with keys: account_summary, positions,
        fills, open_orders. This is the single entry point the sync/import
        layer should call to retrieve live data for one account.

        Raises IBConnectionError if the session was lost, rejected, or
        denied by account permissions during the requests above.
        """
        snapshot = {
            "account_summary": self.get_account_summary(),
            "positions": self.get_positions(),
            "fills": self.get_fills(),
            "open_orders": self.get_open_orders(),
        }
        self.raise_if_critical_errors()
        self.raise_if_permission_error()
        return snapshot
