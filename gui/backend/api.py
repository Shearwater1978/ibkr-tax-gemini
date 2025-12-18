# gui/backend/api.py
import sys
import os
import uvicorn
import platform
import subprocess
from fastapi import FastAPI, HTTPException
from fastapi.middleware.cors import CORSMiddleware
from pydantic import BaseModel
from typing import List

# --- UTF-8 Fix for Windows ---
if platform.system() == 'Windows':
    import io
    sys.stdout = io.TextIOWrapper(sys.stdout.buffer, encoding='utf-8')

# --- Paths Configuration ---
current_dir = os.path.dirname(os.path.abspath(__file__))
project_root = os.path.abspath(os.path.join(current_dir, "../../"))
sys.path.append(project_root)

# Import original project modules
from main import run_import_routine, prepare_data_for_pdf
from src.db_connector import DBConnector
from src.processing import process_yearly_data
from src.data_collector import collect_all_trade_data
from src.excel_exporter import export_to_excel

# PDF Import (Optional)
try:
    from src.report_pdf import generate_pdf
    PDF_AVAILABLE = True
except ImportError:
    PDF_AVAILABLE = False

# --- MODELS ---
class YearResponse(BaseModel):
    years: List[int]

class ImportResponse(BaseModel):
    status: str
    message: str
    count: int

class CalcResponse(BaseModel):
    status: str
    pdf_available: bool
    excel_available: bool
    message: str

app = FastAPI()
app.add_middleware(CORSMiddleware, allow_origins=["*"], allow_methods=["*"], allow_headers=["*"])

# --- HELPERS ---

def get_file_paths(year: int):
    """Utility to centralize file path logic."""
    output_dir = os.path.join(project_root, 'output')
    excel_path = os.path.join(output_dir, f"tax_report_{year}.xlsx")
    pdf_path = os.path.join(output_dir, f"tax_report_{year}.pdf")
    return excel_path, pdf_path

def open_file_system(filepath: str):
    """Opens a file using the system's default application."""
    if not os.path.exists(filepath):
        raise HTTPException(status_code=404, detail=f"File not found: {filepath}")
    
    try:
        if platform.system() == 'Darwin':       # macOS
            subprocess.call(('open', filepath))
        elif platform.system() == 'Windows':    # Windows
            os.startfile(filepath)
        else:                                   # Linux
            subprocess.call(('xdg-open', filepath))
    except Exception as e:
        print(f"DEBUG: Failed to open file: {e}")
        raise HTTPException(status_code=500, detail="Could not open file")

# --- ENDPOINTS ---

@app.get("/years", response_model=YearResponse)
def get_available_years():
    try:
        with DBConnector() as db:
            cursor = db.conn.cursor()
            query = "SELECT DISTINCT SUBSTR(Date, 1, 4) FROM transactions WHERE Date IS NOT NULL"
            cursor.execute(query)
            rows = cursor.fetchall()
            years = sorted([int(row[0]) for row in rows if row[0] and str(row[0]).isdigit()], reverse=True)
            return {"years": years}
    except Exception as e:
        return {"years": []}

@app.post("/import", response_model=ImportResponse)
def run_import():
    try:
        run_import_routine()
        with DBConnector() as db:
            cursor = db.conn.cursor()
            cursor.execute("SELECT COUNT(*) FROM transactions")
            count = cursor.fetchone()[0]
        return {"status": "success", "message": "Import finished", "count": count}
    except Exception as e:
        raise HTTPException(status_code=500, detail=str(e))

@app.get("/calculate/{year}")
def calculate_report(year: int):
    try:
        with DBConnector() as db:
            raw_trades = db.get_trades_for_calculation(target_year=year)

        if not raw_trades:
            raise HTTPException(status_code=404, detail="No data found")

        realized_gains, dividends, inventory = process_yearly_data(raw_trades, year)
        
        # --- 1. ОПРЕДЕЛЯЕМ ПАПКУ ВЫВОДА В НАЧАЛЕ ---
        output_dir = os.path.join(project_root, 'output')
        os.makedirs(output_dir, exist_ok=True)
        
        # ОПРЕДЕЛЯЕМ ПУТИ К ФАЙЛАМ
        excel_path = os.path.join(output_dir, f"tax_report_{year}.xlsx")
        pdf_path = os.path.join(output_dir, f"tax_report_{year}.pdf")

        # --- 2. EXPORT EXCEL ---
        sheets_dict, ticker_summary = collect_all_trade_data(realized_gains, dividends, inventory)
        export_to_excel(sheets_dict, excel_path, {"Year": year}, ticker_summary)
        # print(f"SUCCESS: Data exported to Excel at {excel_path}")

        # --- 3. EXPORT PDF ---
        pdf_generated = False
        if PDF_AVAILABLE:
            try:
                pdf_data = prepare_data_for_pdf(year, raw_trades, realized_gains, dividends, inventory)
                generate_pdf(pdf_data, pdf_path)
                print(f"SUCCESS: PDF report saved to {pdf_path}")
                pdf_generated = True
            except Exception as pdf_err:
                # Теперь output_dir определена, и ошибки не будет
                print(f"ERROR: PDF generation failed: {pdf_err}")

        # --- 4. RETURN DATA ---
        return {
            "status": "success",
            "pdf_available": pdf_generated,
            "excel_available": True,
            "summary": {
                "pln_profit": sum(r['profit_loss'] for r in realized_gains),
                "pln_dividend_gross": sum(d['gross_amount_pln'] for d in dividends),
                "open_positions_count": len(inventory)
            }
        }
    except Exception as e:
        print(f"Calc error: {e}")
        raise HTTPException(status_code=500, detail=str(e))

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