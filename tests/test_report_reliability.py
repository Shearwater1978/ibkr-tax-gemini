import pandas as pd
import pytest

from src.diagnostics import ReportExportError
from src.data_collector import collect_all_trade_data
from src.excel_exporter import export_to_excel
from main import prepare_data_for_pdf
from src.report_pdf import generate_pdf


def test_excel_export_failure_is_raised(monkeypatch, tmp_path):
    def fail_writer(*args, **kwargs):
        raise OSError("disk full")

    monkeypatch.setattr(pd, "ExcelWriter", fail_writer)

    with pytest.raises(ReportExportError):
        export_to_excel({}, str(tmp_path / "report.xlsx"), {}, {})


def test_excel_data_keeps_ticker_summary_and_discloses_identity_change():
    sheets, ticker_summary = collect_all_trade_data(
        [
            {
                "ticker": "OKE",
                "isin": "NEW-ISIN",
                "sale_date": "2025-03-05",
                "sale_price": 30,
                "sale_rate": 1,
                "matched_buys": [
                    {
                        "isin": "NEW-ISIN",
                        "date": "2025-03-04",
                        "qty": 1,
                        "cost_pln": 20,
                    }
                ],
            }
        ],
        [],
        [{"ticker": "OKE", "isin": "OLD-ISIN", "buy_date": "2024-01-01", "quantity": 5}],
        [
            {
                "ticker": "OKE",
                "previous_isin": "OLD-ISIN",
                "new_isin": "NEW-ISIN",
                "date": "2025-03-04",
            }
        ],
    )

    assert list(ticker_summary) == ["OKE"]
    assert sheets["Sales P&L"].iloc[0]["ISIN"] == "NEW-ISIN"
    assert sheets["Open Positions"].iloc[0]["ISIN"] == "OLD-ISIN"
    assert sheets["Identity Changes"].iloc[0]["Previous ISIN"] == "OLD-ISIN"


def test_pdf_for_remapped_ticker_keeps_one_holding_and_discloses_both_isins(tmp_path):
    diagnostic = {
        "ticker": "OKE",
        "previous_isin": "OLD-ISIN",
        "new_isin": "NEW-ISIN",
        "date": "2025-03-04",
    }
    payload = prepare_data_for_pdf(
        2025,
        [
            {
                "Date": "2025-03-04",
                "EventType": "BUY",
                "Ticker": "OKE",
                "ISIN": "NEW-ISIN",
                "Quantity": 3,
                "Price": 20,
                "Fee": 0,
                "Currency": "PLN",
            },
            {
                "Date": "2025-03-05",
                "EventType": "SELL",
                "Ticker": "OKE",
                "ISIN": "OLD-ISIN",
                "Quantity": -1,
                "Price": 30,
                "Fee": 0,
                "Currency": "PLN",
            },
        ],
        [],
        [],
        [
            {"ticker": "OKE", "isin": "OLD-ISIN", "quantity": 2, "currency": "PLN"},
            {"ticker": "OKE", "isin": "NEW-ISIN", "quantity": 3, "currency": "PLN"},
        ],
        [diagnostic],
    )

    data = payload["data"]
    assert len(data["holdings"]) == 1
    assert {trade["isin"] for trade in data["trades_history"]} == {
        "OLD-ISIN",
        "NEW-ISIN",
    }
    assert data["identity_changes"] == [diagnostic]

    output = tmp_path / "identity-report.pdf"
    generate_pdf(payload, str(output))
    assert output.is_file()
