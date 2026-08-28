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
