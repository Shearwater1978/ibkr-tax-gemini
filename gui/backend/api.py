# gui/backend/api.py
import sys
import os
import uvicorn
import glob
from fastapi import FastAPI, HTTPException
from fastapi.middleware.cors import CORSMiddleware
from pydantic import BaseModel
from typing import List, Optional

# Add project root to path to access src modules
current_dir = os.path.dirname(os.path.abspath(__file__))
project_root = os.path.dirname(os.path.dirname(current_dir))
sys.path.append(project_root)

# Import core logic
from src.db_connector import DBConnector
from src.processing import process_yearly_data
from src.parser import parse_csv, save_to_database
from src.excel_exporter import export_to_excel
from src.data_collector import collect_all_trade_data

# PDF Import (Optional)
try:
    from src.report_pdf import generate_pdf
    # We need the adapter function from main.py or recreate it here.
    # For simplicity in POC, we will reuse the logic from main.py by importing it
    # if it was structured as a module, but here we might need to duplicate the adapter slightly
    # or better: refactor main.py later. For now, let's keep it simple.
    PDF_AVAILABLE = True
except ImportError:
    PDF_AVAILABLE = False

# We need the adapter logic for PDF. To avoid code duplication, in a real scenario
# we would move 'prepare_data_for_pdf' to a utility module.
# For this POC, we will import it dynamically if possible, or assume main.py is accessible.
try:
    from main import prepare_data_for_pdf
except ImportError:
    # Fallback if main.py is not easily importable as module
    def prepare_data_for_pdf(*args, **kwargs): return {}

app = FastAPI()

app.add_middleware(
    CORSMiddleware,
    allow_origins=["*"],
    allow_methods=["*"],
    allow_headers=["*"],
)

class ImportResponse(BaseModel):
    status: str
    message: str
    count: int

class ExportRequest(BaseModel):
    year: int
    type: str # 'pdf' or 'excel'

@app.get("/health")
def health_check():
    return {"status": "ok", "version": "2.1.0 POC"}

@app.get("/years")
def get_available_years():
    """Scan DB for available tax years."""
    try:
        with DBConnector() as db:
            # Query distinct years from Date string (YYYY-MM-DD)
            # SQLite: substr(Date, 1, 4)
            cursor = db.conn.cursor()
            cursor.execute("SELECT DISTINCT substr(Date, 1, 4) FROM transactions ORDER BY 1 DESC")
            years = [int(row[0]) for row in cursor.fetchall() if row[0] and row[0].isdigit()]
            return {"years": years}
    except Exception as e:
        raise HTTPException(status_code=500, detail=f"DB Error: {str(e)}")

@app.post("/import", response_model=ImportResponse)
def run_import():
    """Trigger import routine from data/ folder into EXISTING DB."""
    try:
        data_dir = os.path.join(project_root, 'data')
        files = glob.glob(os.path.join(data_dir, "*.csv"))
        files = [f for f in files if "manual_" not in os.path.basename(f)]
        
        combined = {'trades': [], 'dividends': [], 'taxes': [], 'corp_actions': []}
        
        for fp in files:
            parsed = parse_csv(fp)
            for k in combined:
                combined[k].extend(parsed[k])
                
        if any(combined.values()):
            # This saves to the DB defined in .env (DBConnector logic)
            save_to_database(combined)
            return {"status": "success", "message": "Import completed successfully", "count": len(files)}
        
        return {"status": "warning", "message": "No valid data found in CSV files", "count": 0}
        
    except Exception as e:
        raise HTTPException(status_code=500, detail=str(e))

@app.get("/calculate/{year}")
def calculate_year(year: int):
    """Run FIFO calculation and return JSON summary."""
    try:
        with DBConnector() as db:
            db.initialize_schema()
            raw_trades = db.get_trades_for_calculation(target_year=year)
            
        if not raw_trades:
            return {"error": f"No transactions found for {year}"}
            
        realized, dividends, inventory = process_yearly_data(raw_trades, year)
        
        total_pl = sum(r['profit_loss'] for r in realized)
        total_div_gross = sum(d['gross_amount_pln'] for d in dividends)
        total_div_tax = sum(d.get('tax_withheld_pln', 0) for d in dividends)
        
        return {
            "summary": {
                "pln_profit": total_pl,
                "pln_dividend_gross": total_div_gross,
                "pln_dividend_net": total_div_gross - total_div_tax,
                "open_positions_count": len(inventory),
                "trades_count": len(realized)
            },
            # Limit detail size for UI responsiveness
            "inventory": inventory[:20], 
            "last_trades": realized[-10:] 
        }
    except Exception as e:
        raise HTTPException(status_code=500, detail=str(e))

@app.post("/export")
def export_report(req: ExportRequest):
    """Generate PDF or Excel file to output/ folder."""
    try:
        year = req.year
        output_dir = os.path.join(project_root, 'output')
        os.makedirs(output_dir, exist_ok=True)
        
        # 1. Fetch Data
        with DBConnector() as db:
            raw_trades = db.get_trades_for_calculation(target_year=year)
        
        if not raw_trades:
            raise HTTPException(status_code=404, detail="No data for this year")
            
        realized, dividends, inventory = process_yearly_data(raw_trades, year)
        
        # 2. Export Logic
        if req.type == 'excel':
            sheets_dict, ticker_summary = collect_all_trade_data(realized_gains=realized, dividends=dividends, inventory=inventory)
            summary_metrics = {
                "Report Year": year,
                "Generated Via": "GUI v2.1"
            }
            filename = f"tax_report_{year}.xlsx"
            path = os.path.join(output_dir, filename)
            export_to_excel(sheets_dict, path, summary_metrics, ticker_summary)
            return {"status": "success", "file": path, "message": f"Excel saved to {path}"}
            
        elif req.type == 'pdf':
            if not PDF_AVAILABLE:
                 raise HTTPException(status_code=501, detail="PDF Module not available")
            
            filename = f"tax_report_{year}.pdf"
            path = os.path.join(output_dir, filename)
            
            # Use the adapter logic
            pdf_data = prepare_data_for_pdf(year, raw_trades, realized, dividends, inventory)
            generate_pdf(pdf_data, path)
            return {"status": "success", "file": path, "message": f"PDF saved to {path}"}
        
        else:
            raise HTTPException(status_code=400, detail="Unknown export type")
            
    except Exception as e:
        raise HTTPException(status_code=500, detail=str(e))

def start():
    uvicorn.run(app, host="127.0.0.1", port=8000)

if __name__ == "__main__":
    start()
