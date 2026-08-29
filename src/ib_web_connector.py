"""Session manager for the Interactive Brokers Client Portal Web API (CPGW).

This module is intentionally isolated from src/ib_connector.py (TWS/IB
Gateway socket connector) and from the CSV import pipeline. It only talks
to a locally running Client Portal Gateway over HTTPS; the user must start
CPGW and complete the browser-based login (credentials + 2FA) themselves -
this module never attempts to automate that login.
"""

import os

try:
    import requests
except ImportError:
    requests = None

try:
    from decouple import config
except ImportError:

    def config(name, default=None, cast=None):
        value = os.getenv(name, default)
        if cast is bool and isinstance(value, str):
            return value.strip().lower() in ("1", "true", "yes", "on")
        return cast(value) if cast and value is not None else value


IB_WEB_BASE_URL = config("IB_WEB_BASE_URL", default="https://localhost:5000/v1/api")
IB_WEB_VERIFY_SSL = config("IB_WEB_VERIFY_SSL", default=False, cast=bool)
IB_WEB_REQUEST_TIMEOUT = config("IB_WEB_REQUEST_TIMEOUT", default=10, cast=int)

# Web API connectivity is opt-in: CSV import and the TWS/Gateway connector
# must keep working with zero Web API configuration, so nothing here should
# attempt a request unless a user explicitly sets IB_WEB_API_ENABLED=True.
IB_WEB_API_ENABLED = config("IB_WEB_API_ENABLED", default=False, cast=bool)


class IBWebConnectionError(RuntimeError):
    """Raised when a Client Portal Gateway session cannot be established or verified."""


class IBWebConnector:
    """Manages a session against a locally running Client Portal Gateway.

    Usage:
        with IBWebConnector() as ib:
            ib.health_check()
    """

    def __init__(self, base_url=None, verify_ssl=None, timeout=None):
        self.base_url = base_url if base_url is not None else IB_WEB_BASE_URL
        self.verify_ssl = verify_ssl if verify_ssl is not None else IB_WEB_VERIFY_SSL
        self.timeout = timeout if timeout is not None else IB_WEB_REQUEST_TIMEOUT
        self.session = None

    def __enter__(self):
        self.connect()
        return self

    def __exit__(self, exc_type, exc_val, exc_tb):
        self.disconnect()

    def connect(self):
        """Initialize the brokerage session against a running CPGW instance.

        Calls POST /iserver/auth/ssodh/init. This does not perform the
        browser login itself: it only (re)activates the brokerage session
        for an already browser-authenticated CPGW instance.
        """
        if not IB_WEB_API_ENABLED:
            raise IBWebConnectionError(
                "IB Web API is not enabled. Set IB_WEB_API_ENABLED=True in your "
                "environment/.env to opt in; CSV import does not require this."
            )
        if requests is None:
            raise IBWebConnectionError(
                "requests is unavailable. Install dependencies from requirements.txt."
            )

        self.session = requests.Session()
        try:
            response = self.session.post(
                f"{self.base_url}/iserver/auth/ssodh/init",
                json={},
                verify=self.verify_ssl,
                timeout=self.timeout,
            )
            response.raise_for_status()
        except Exception as exc:
            self.session = None
            raise IBWebConnectionError(
                f"Could not initialize a brokerage session at {self.base_url}. "
                "Is the Client Portal Gateway running and are you logged in via "
                "the browser at its base URL?"
            ) from exc

    def disconnect(self):
        if self.session is not None:
            self.session.close()
        self.session = None

    def is_connected(self) -> bool:
        return self.session is not None

    def _require_session(self):
        if not self.is_connected():
            raise IBWebConnectionError(
                "No active Client Portal Gateway session. Call connect() first."
            )

    def auth_status(self) -> dict:
        """Return the raw POST /iserver/auth/status payload.

        Reflects whether the underlying browser login is still valid, not
        just whether this process opened an HTTP session.
        """
        self._require_session()
        try:
            response = self.session.post(
                f"{self.base_url}/iserver/auth/status",
                json={},
                verify=self.verify_ssl,
                timeout=self.timeout,
            )
            response.raise_for_status()
            return response.json()
        except Exception as exc:
            raise IBWebConnectionError(
                "Failed to check Client Portal Gateway auth status."
            ) from exc

    def health_check(self) -> dict:
        """Verify the CPGW session is authenticated and ready for requests.

        Raises IBWebConnectionError with an actionable message directing
        the user to log in via the browser if the session is not
        authenticated (e.g. never logged in, or the login expired).
        """
        status = self.auth_status()
        if not status.get("authenticated"):
            raise IBWebConnectionError(
                f"Client Portal Gateway session is not authenticated. Log in via "
                f"the browser at {self.base_url.split('/v1/api')[0]} and retry."
            )
        return status

    def tickle(self) -> dict:
        """Send a keep-alive request so the CPGW session does not expire."""
        self._require_session()
        try:
            response = self.session.post(
                f"{self.base_url}/tickle",
                json={},
                verify=self.verify_ssl,
                timeout=self.timeout,
            )
            response.raise_for_status()
            return response.json()
        except Exception as exc:
            raise IBWebConnectionError(
                "Client Portal Gateway keep-alive (tickle) request failed; "
                "the session may have expired."
            ) from exc

    def _get_json_list(self, path: str, error_message: str):
        """GET a JSON array endpoint and validate the shape before returning."""
        self._require_session()
        try:
            response = self.session.get(
                f"{self.base_url}{path}",
                verify=self.verify_ssl,
                timeout=self.timeout,
            )
            response.raise_for_status()
            payload = response.json()
        except Exception as exc:
            raise IBWebConnectionError(error_message) from exc
        if not isinstance(payload, list):
            raise IBWebConnectionError(
                f"{error_message} (expected a JSON array, got {type(payload).__name__})"
            )
        return payload

    def get_accounts(self) -> list:
        """Return read-only account records as plain dicts (GET /portfolio/accounts)."""
        rows = self._get_json_list(
            "/portfolio/accounts", "Failed to request accounts from the Web API."
        )
        return [
            {
                "account_id": row.get("accountId") or row.get("id"),
                "account_title": row.get("accountTitle"),
                "currency": row.get("currency"),
                "type": row.get("type"),
            }
            for row in rows
        ]

    def get_positions(self, account_id: str, page: int = 0) -> list:
        """Return current open positions for an account as plain dicts.

        Calls GET /portfolio/{account_id}/positions/{page}.
        """
        rows = self._get_json_list(
            f"/portfolio/{account_id}/positions/{page}",
            f"Failed to request positions for account {account_id} from the Web API.",
        )
        return [
            {
                "account_id": row.get("acctId"),
                "conid": row.get("conid"),
                "symbol": row.get("contractDesc"),
                "position": row.get("position"),
                "avg_cost": row.get("avgCost"),
                "currency": row.get("currency"),
                "asset_class": row.get("assetClass"),
            }
            for row in rows
        ]

    def get_trades(self) -> list:
        """Return recent trade confirmations as plain dicts (GET /iserver/account/trades).

        IBKR limits this endpoint to a recent trading window (not full
        history); use CSV/Flex Query import for historical trades.
        """
        rows = self._get_json_list(
            "/iserver/account/trades", "Failed to request trades from the Web API."
        )
        return [
            {
                "execution_id": row.get("execution_id"),
                "account_id": row.get("account"),
                "symbol": row.get("symbol"),
                "side": row.get("side"),
                "size": row.get("size"),
                "price": row.get("price"),
                "currency": row.get("currency"),
                "commission": row.get("commission"),
                "trade_time": row.get("trade_time"),
            }
            for row in rows
        ]
