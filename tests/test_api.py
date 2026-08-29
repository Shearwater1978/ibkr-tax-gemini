from decimal import Decimal
from types import SimpleNamespace
from unittest.mock import patch

from fastapi.testclient import TestClient

from gui.backend import api
from src.diagnostics import CalculationDiagnostic, CalculationError
from src.diagnostics import ReportExportError


class FakeDatabase:
    def __init__(self, rows=None):
        self.rows = rows or []
        self.conn = self

    def __enter__(self):
        return self

    def __exit__(self, exc_type, exc_val, exc_tb):
        return False

    def initialize_schema(self):
        return None

    def execute(self, query, params=None):
        if "COUNT(*)" in query:
            return SimpleNamespace(fetchone=lambda: (len(self.rows),))
        return SimpleNamespace(fetchall=lambda: self.rows)

    def get_trades_for_calculation(self, target_year=None):
        return self.rows


def test_health_reports_ready():
    with patch.object(api, "DBConnector", return_value=FakeDatabase()):
        response = TestClient(api.app).get("/health")

    assert response.status_code == 200
    assert response.json() == {"status": "ready"}


def test_years_are_sorted_newest_first():
    database = FakeDatabase([("2022",), ("2025",), ("2024",), ("2025",)])
    with patch.object(api, "DBConnector", return_value=database):
        response = TestClient(api.app).get("/years")

    assert response.status_code == 200
    assert response.json() == {"years": [2025, 2024, 2022]}


def test_import_reports_inserted_and_skipped_counts():
    database = FakeDatabase([("2025-01-01",)])
    with patch.object(
        api, "run_import_routine", return_value={"inserted": 2, "skipped": 1}
    ), patch.object(api, "DBConnector", return_value=database):
        response = TestClient(api.app).post("/import")

    assert response.status_code == 200
    assert response.json()["inserted"] == 2
    assert response.json()["skipped"] == 1
    assert response.json()["count"] == 1


def test_missing_year_data_preserves_404():
    with patch.object(api, "DBConnector", return_value=FakeDatabase()):
        response = TestClient(api.app).get("/calculate/2025")

    assert response.status_code == 404
    assert response.json()["detail"] == "No data found"


def test_ib_status_not_configured_by_default_without_mocking_connector():
    """No .env / IB_LIVE_ENABLED set: the real IBConnector must refuse to
    connect immediately (no socket attempt), so this must return fast and
    report configured=False without needing a live IB Gateway."""
    with patch.object(api, "IB_LIVE_ENABLED", False), patch(
        "src.ib_connector.IB_LIVE_ENABLED", False
    ):
        response = TestClient(api.app).get("/ib/status")

    assert response.status_code == 200
    data = response.json()
    assert data["configured"] is False
    assert data["connected"] is False
    assert "not enabled" in data["error"]


def test_ib_status_reports_connected_when_gateway_reachable():
    with patch.object(api, "IBConnector") as mock_connector_cls:
        mock_ib = mock_connector_cls.return_value.__enter__.return_value
        mock_ib.health_check.return_value = None
        response = TestClient(api.app).get("/ib/status")

    assert response.status_code == 200
    data = response.json()
    assert data["connected"] is True
    assert data["error"] is None
    assert data["port"] == api.IB_PORT


def test_ib_status_reports_error_without_raising_when_gateway_unreachable():
    with patch.object(api, "IBConnector") as mock_connector_cls:
        mock_connector_cls.return_value.__enter__.side_effect = api.IBConnectionError(
            "Could not connect to IB Gateway/TWS at 127.0.0.1:4002"
        )
        response = TestClient(api.app).get("/ib/status")

    assert response.status_code == 200
    data = response.json()
    assert data["connected"] is False
    assert "Could not connect" in data["error"]


def test_ib_status_does_not_block_csv_import_flow():
    database = FakeDatabase([("2025-01-01",)])
    with patch.object(api, "IBConnector") as mock_connector_cls, patch.object(
        api, "run_import_routine", return_value={"inserted": 1, "skipped": 0}
    ), patch.object(api, "DBConnector", return_value=database):
        mock_connector_cls.return_value.__enter__.side_effect = api.IBConnectionError(
            "Gateway unreachable"
        )
        client = TestClient(api.app)

        ib_response = client.get("/ib/status")
        import_response = client.post("/import")

    assert ib_response.status_code == 200
    assert ib_response.json()["connected"] is False
    assert import_response.status_code == 200
    assert import_response.json()["inserted"] == 1


def test_ib_sync_reports_success():
    with patch.object(
        api,
        "run_ib_sync_routine",
        return_value={
            "status": "success",
            "message": "IB live sync finished",
            "inserted": 3,
            "skipped": 1,
        },
    ):
        response = TestClient(api.app).post("/ib/sync")

    assert response.status_code == 200
    data = response.json()
    assert data["status"] == "success"
    assert data["inserted"] == 3
    assert data["skipped"] == 1


def test_ib_sync_reports_failure_without_raising():
    with patch.object(
        api,
        "run_ib_sync_routine",
        return_value={
            "status": "error",
            "message": "Could not connect to IB Gateway/TWS",
            "inserted": 0,
            "skipped": 0,
        },
    ):
        response = TestClient(api.app).post("/ib/sync")

    assert response.status_code == 200
    data = response.json()
    assert data["status"] == "error"
    assert "Could not connect" in data["message"]


def test_ib_sync_unexpected_exception_is_reported_not_raised():
    with patch.object(api, "run_ib_sync_routine", side_effect=RuntimeError("boom")):
        response = TestClient(api.app).post("/ib/sync")

    assert response.status_code == 200
    data = response.json()
    assert data["status"] == "error"
    assert "boom" in data["message"]


def test_calculation_diagnostic_is_structured():
    diagnostic = CalculationDiagnostic(
        code="NBP_RATE_MISSING",
        message="No NBP rate found.",
        currency="USD",
        date="2025-01-02",
    )
    with patch.object(
        api, "DBConnector", return_value=FakeDatabase([{"TradeId": 1}])
    ), patch.object(
        api, "process_yearly_data", side_effect=CalculationError(diagnostic)
    ):
        response = TestClient(api.app).get("/calculate/2025")

    assert response.status_code == 422
    assert response.json()["detail"]["code"] == "NBP_RATE_MISSING"
    assert response.json()["detail"]["currency"] == "USD"


def test_calculation_marks_failed_export_unavailable():
    database = FakeDatabase([{"TradeId": 1}])
    with patch.object(api, "DBConnector", return_value=database), patch.object(
        api, "process_yearly_data", return_value=([], [], [])
    ), patch.object(api, "collect_all_trade_data", return_value=({}, {})), patch.object(
        api, "export_to_excel", side_effect=ReportExportError("write failed")
    ), patch.object(
        api, "generate_pdf", None
    ):
        response = TestClient(api.app).get("/calculate/2025")

    assert response.status_code == 200
    assert response.json()["complete"] is False
    assert response.json()["excel_available"] is False
    assert response.json()["pdf_available"] is False


def test_missing_report_returns_404():
    response = TestClient(api.app).get("/open/excel/2099")
    assert response.status_code == 404


def test_coverage_returns_one_result_per_planned_sale():
    database = FakeDatabase(
        [
            {
                "TradeId": 1,
                "Date": "2024-01-01",
                "EventType": "BUY",
                "Ticker": "AAPL",
                "Quantity": 5,
                "Currency": "PLN",
            }
        ]
    )
    with patch.object(api, "DBConnector", return_value=database):
        response = TestClient(api.app).post(
            "/coverage",
            json={
                "planned_sales": [
                    {"ticker": "AAPL", "quantity": 2, "as_of": "2024-02-01"},
                    {"ticker": "MSFT", "quantity": 1, "as_of": "2024-02-01"},
                ]
            },
        )

    assert response.status_code == 200
    assert [item["ticker"] for item in response.json()["results"]] == ["AAPL", "MSFT"]
    assert response.json()["results"][0]["status"] == "COVERED"
    assert response.json()["results"][1]["status"] == "NOT_COVERED"


def test_coverage_rejects_invalid_planned_sale():
    response = TestClient(api.app).post(
        "/coverage",
        json={
            "planned_sales": [{"ticker": "AAPL", "quantity": 0, "as_of": "2024-02-01"}]
        },
    )

    assert response.status_code == 422
