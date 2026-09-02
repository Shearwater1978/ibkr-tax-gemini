import os
import platform
import subprocess
import sys
from pathlib import Path
from datetime import date
from decimal import Decimal
from typing import List

import uvicorn
from fastapi import FastAPI, HTTPException
from fastapi.middleware.cors import CORSMiddleware
from pydantic import BaseModel

current_dir = Path(__file__).resolve().parent
project_root = current_dir.parents[1]
sys.path.insert(0, str(project_root))

from main import (
    prepare_data_for_pdf,
    run_import_routine,
    run_ib_sync_routine,
    run_ib_web_sync_routine,
)
from src.data_collector import collect_all_trade_data
from src.db_connector import DBConnector
from src.diagnostics import CalculationError, ReportExportError
from src.excel_exporter import export_to_excel
from src.processing import process_yearly_data
from src.fifo_coverage import PlannedSale, check_coverage
from src.ib_connector import (
    IBConnector,
    IBConnectionError,
    IB_HOST,
    IB_PORT,
    IB_CLIENT_ID,
    IB_LIVE_ENABLED,
)
from src.ib_web_connector import (
    IBWebConnector,
    IBWebConnectionError,
    IB_WEB_API_ENABLED,
    IB_WEB_BASE_URL,
)

try:
    from src.report_pdf import generate_pdf
except ImportError:
    generate_pdf = None


class YearResponse(BaseModel):
    years: List[int]


class ImportResponse(BaseModel):
    status: str
    message: str
    count: int
    inserted: int
    skipped: int


class PlannedSaleRequest(BaseModel):
    ticker: str
    quantity: Decimal
    as_of: date


class CoverageRequest(BaseModel):
    planned_sales: List[PlannedSaleRequest]


class IBStatusResponse(BaseModel):
    configured: bool
    host: str
    port: int
    client_id: int
    connected: bool
    error: str | None = None


class IBTradeSummary(BaseModel):
    ticker: str
    date: str
    type: str
    qty: str
    price: str
    currency: str


class IBSyncResponse(BaseModel):
    status: str
    message: str
    inserted: int
    skipped: int
    trades: List[IBTradeSummary] = []


class IBWebStatusResponse(BaseModel):
    configured: bool
    base_url: str
    connected: bool
    error: str | None = None


app = FastAPI(title="IBKR Tax Calculator API")
app.add_middleware(
    CORSMiddleware,
    allow_origins=["null", "http://localhost", "http://127.0.0.1"],
    allow_methods=["GET", "POST"],
    allow_headers=["Content-Type"],
)


def get_file_paths(year: int):
    if year < 1900 or year > 2200:
        raise HTTPException(status_code=422, detail="Invalid report year")
    output_dir = project_root / "output"
    return output_dir / f"tax_report_{year}.xlsx", output_dir / f"tax_report_{year}.pdf"


def open_file_system(filepath: Path):
    if not filepath.is_file():
        raise HTTPException(status_code=404, detail="Report file not found")
    try:
        if platform.system() == "Darwin":
            subprocess.call(("open", str(filepath)))
        elif platform.system() == "Windows":
            os.startfile(str(filepath))
        else:
            subprocess.call(("xdg-open", str(filepath)))
    except Exception as exc:
        raise HTTPException(
            status_code=500, detail="Could not open report file"
        ) from exc


@app.get("/health")
def health_check():
    try:
        with DBConnector() as db:
            db.initialize_schema()
        return {"status": "ready"}
    except Exception as exc:
        raise HTTPException(status_code=503, detail="Backend is not ready") from exc


@app.get("/ib/status", response_model=IBStatusResponse)
def ib_status():
    """Report IB Gateway/TWS configuration and connectivity.

    This is a diagnostic, best-effort check: it never raises, so a
    missing/unreachable Gateway cannot block the CSV import workflow.
    """
    base = {
        "configured": IB_LIVE_ENABLED,
        "host": IB_HOST,
        "port": IB_PORT,
        "client_id": IB_CLIENT_ID,
    }
    try:
        with IBConnector(timeout=3) as ib:
            ib.health_check()
        return {**base, "connected": True, "error": None}
    except IBConnectionError as exc:
        return {**base, "connected": False, "error": str(exc)}
    except Exception as exc:
        return {**base, "connected": False, "error": f"Unexpected error: {exc}"}


@app.post("/ib/sync", response_model=IBSyncResponse)
def ib_sync():
    """Manually trigger a read-only IB Gateway/TWS live sync.

    Reports success or failure consistently in the response body (status
    200 either way) instead of raising, since a Gateway connection issue
    is an expected, actionable condition rather than a server bug. This
    never touches the CSV import workflow.
    """
    try:
        result = run_ib_sync_routine()
        return result
    except Exception as exc:
        import traceback

        print(f"IB sync error: {exc}")
        traceback.print_exc()
        return {
            "status": "error",
            "message": f"IB live sync failed unexpectedly: {exc}",
            "inserted": 0,
            "skipped": 0,
        }


@app.get("/ib/web/status", response_model=IBWebStatusResponse)
def ib_web_status():
    base = {"configured": IB_WEB_API_ENABLED, "base_url": IB_WEB_BASE_URL}
    try:
        with IBWebConnector(timeout=3) as ib:
            ib.health_check()
        return {**base, "connected": True, "error": None}
    except IBWebConnectionError as exc:
        return {**base, "connected": False, "error": str(exc)}
    except Exception as exc:
        return {**base, "connected": False, "error": f"Unexpected error: {exc}"}


@app.post("/ib/web/sync", response_model=IBSyncResponse)
def ib_web_sync():
    try:
        return run_ib_web_sync_routine()
    except Exception as exc:
        print(f"IB Web API sync error: {exc}")
        return {
            "status": "error",
            "message": f"IB Web API sync failed unexpectedly: {exc}",
            "inserted": 0,
            "skipped": 0,
        }


@app.get("/years", response_model=YearResponse)
def get_available_years():
    try:
        with DBConnector() as db:
            rows = db.conn.execute(
                "SELECT DISTINCT SUBSTR(Date, 1, 4) FROM transactions "
                "WHERE Date IS NOT NULL"
            ).fetchall()
        years = sorted(
            {int(row[0]) for row in rows if row[0] and str(row[0]).isdigit()},
            reverse=True,
        )
        return {"years": years}
    except Exception as exc:
        raise HTTPException(status_code=503, detail="Database is unavailable") from exc


@app.post("/import", response_model=ImportResponse)
def run_import():
    try:
        result = run_import_routine()
        with DBConnector() as db:
            count_row = db.conn.execute("SELECT COUNT(*) FROM transactions").fetchone()
            count = count_row[0] if count_row else 0
        return {
            "status": "success",
            "message": "Import finished",
            "count": count,
            "inserted": result.get("inserted", 0),
            "skipped": result.get("skipped", 0),
        }
    except Exception as exc:
        import traceback

        print(f"Import error: {exc}")
        traceback.print_exc()
        raise HTTPException(status_code=500, detail="Import failed") from exc


@app.post("/coverage")
def coverage_check(request: CoverageRequest):
    if not request.planned_sales:
        raise HTTPException(
            status_code=422, detail="At least one planned sale is required"
        )
    try:
        planned_sales = [
            PlannedSale(item.ticker, item.quantity, item.as_of.isoformat())
            for item in request.planned_sales
        ]
        with DBConnector() as db:
            raw_trades = db.get_trades_for_calculation()
        return check_coverage(raw_trades, planned_sales)
    except ValueError as exc:
        raise HTTPException(status_code=422, detail=str(exc)) from exc
    except Exception as exc:
        raise HTTPException(status_code=503, detail="Database is unavailable") from exc


@app.get("/calculate/{year}")
def calculate_report(year: int):
    try:
        with DBConnector() as db:
            raw_trades = db.get_trades_for_calculation(target_year=year)
        if not raw_trades:
            raise HTTPException(status_code=404, detail="No data found")

        realized_gains, dividends, inventory = process_yearly_data(raw_trades, year)
        output_dir = project_root / "output"
        output_dir.mkdir(parents=True, exist_ok=True)
        excel_path, pdf_path = get_file_paths(year)
        errors = []

        excel_generated = False
        try:
            sheets, ticker_summary = collect_all_trade_data(
                realized_gains, dividends, inventory
            )
            export_to_excel(sheets, str(excel_path), {"Year": year}, ticker_summary)
            excel_generated = excel_path.is_file()
        except ReportExportError as exc:
            errors.append({"type": "excel", "message": str(exc)})

        pdf_generated = False
        if generate_pdf is not None:
            try:
                pdf_data = prepare_data_for_pdf(
                    year, raw_trades, realized_gains, dividends, inventory
                )
                generate_pdf(pdf_data, str(pdf_path))
                pdf_generated = pdf_path.is_file()
            except Exception:
                errors.append({"type": "pdf", "message": "PDF export failed"})

        return {
            "status": "success",
            "complete": not errors,
            "errors": errors,
            "pdf_available": pdf_generated,
            "excel_available": excel_generated,
            "summary": {
                "pln_profit": sum(r["profit_loss"] for r in realized_gains),
                "pln_dividend_gross": sum(d["gross_amount_pln"] for d in dividends),
                "open_positions_count": len(inventory),
            },
        }
    except HTTPException:
        raise
    except CalculationError as exc:
        diagnostic = exc.diagnostic
        raise HTTPException(
            status_code=422,
            detail={
                "code": diagnostic.code,
                "message": diagnostic.message,
                "ticker": diagnostic.ticker,
                "date": diagnostic.date,
                "currency": diagnostic.currency,
                "quantity": diagnostic.quantity,
            },
        ) from exc
    except Exception as exc:
        raise HTTPException(status_code=500, detail="Calculation failed") from exc


@app.get("/open/excel/{year}")
def open_excel(year: int):
    excel_path, _ = get_file_paths(year)
    open_file_system(excel_path)
    return {"status": "success"}


@app.get("/open/pdf/{year}")
def open_pdf(year: int):
    _, pdf_path = get_file_paths(year)
    open_file_system(pdf_path)
    return {"status": "success"}


if __name__ == "__main__":
    uvicorn.run(app, host="127.0.0.1", port=8000)
