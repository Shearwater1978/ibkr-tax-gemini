# gui/backend/api.py
import sys
import os
import uvicorn
from fastapi import FastAPI, HTTPException
from fastapi.middleware.cors import CORSMiddleware
from pydantic import BaseModel

# Добавляем корень проекта в путь, чтобы видеть src
current_dir = os.path.dirname(os.path.abspath(__file__))
project_root = os.path.dirname(os.path.dirname(current_dir))
sys.path.append(project_root)

# Импортируем вашу логику
from src.db_connector import DBConnector
from src.processing import process_yearly_data
from src.parser import parse_csv, save_to_database
import glob

app = FastAPI()

# Разрешаем Electron обращаться к серверу
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

@app.get("/health")
def health_check():
    return {"status": "ok", "version": "2.1.0"}

@app.post("/import", response_model=ImportResponse)
def run_import():
    """Trigger import routine from data/ folder"""
    try:
        data_dir = os.path.join(project_root, 'data')
        files = glob.glob(os.path.join(data_dir, "*.csv"))
        # Filter manual/system files
        files = [f for f in files if "manual_" not in os.path.basename(f)]
        
        combined = {'trades': [], 'dividends': [], 'taxes': [], 'corp_actions': []}
        
        for fp in files:
            parsed = parse_csv(fp)
            for k in combined:
                combined[k].extend(parsed[k])
                
        # Save to DB logic (simplified for API)
        # Note: In real app, you might want to capture stdout or logs
        if any(combined.values()):
            save_to_database(combined)
            return {"status": "success", "message": "Import completed", "count": len(files)}
        
        return {"status": "warning", "message": "No data found", "count": 0}
        
    except Exception as e:
        raise HTTPException(status_code=500, detail=str(e))

@app.get("/calculate/{year}")
def calculate_year(year: int):
    """Run FIFO calculation and return JSON for UI"""
    try:
        with DBConnector() as db:
            db.initialize_schema()
            raw_trades = db.get_trades_for_calculation(target_year=year)
            
        if not raw_trades:
            return {"error": "No data found for this year"}
            
        realized, dividends, inventory = process_yearly_data(raw_trades, year)
        
        total_pl = sum(r['profit_loss'] for r in realized)
        total_div_gross = sum(d['gross_amount_pln'] for d in dividends)
        total_div_tax = sum(d.get('tax_withheld_pln', 0) for d in dividends)
        
        return {
            "summary": {
                "pln_profit": total_pl,
                "pln_dividend_gross": total_div_gross,
                "pln_dividend_net": total_div_gross - total_div_tax,
                "open_positions_count": len(inventory)
            },
            "inventory": inventory[:10], # Send top 10 for preview
            "last_trades": realized[-5:] # Send last 5 trades
        }
    except Exception as e:
        raise HTTPException(status_code=500, detail=str(e))

def start():
    """Function to start server from python script"""
    uvicorn.run(app, host="127.0.0.1", port=8000)

if __name__ == "__main__":
    start()