# gui/backend/api.py
import sys
import os
import uvicorn
import platform
import subprocess
from fastapi import FastAPI, HTTPException
from fastapi.middleware.cors import CORSMiddleware
from pydantic import BaseModel
from typing import List, Optional

# Add project root to path to access main.py and src modules
current_dir = os.path.dirname(os.path.abspath(__file__))
project_root = os.path.dirname(os.path.dirname(current_dir))
sys.path.append(project_root)

# Import the existing routine from your main script
try:
    from main import run_import_routine
except ImportError as e:
    print(f"Error: Could not import run_import_routine from main.py: {e}")
    def run_import_routine():
        raise NotImplementedError("run_import_routine not found in main.py")

# SQLCipher integration (needed for /years and /export if they query DB directly)
try:
    from pysqlcipher3 import dbapi2 as sqlite
except ImportError:
    import sqlite3 as sqlite

# Configuration
DB_PATH = os.path.join(project_root, 'db', 'ibkr_history.db.enc')
DB_PASSPHRASE = "your_secure_password_here" 

# --- MODELS (Defined BEFORE routes to avoid NameError) ---

class ImportResponse(BaseModel):
    status: str
    message: str
    count: int

class ExportRequest(BaseModel):
    year: int
    type: str

app = FastAPI()

app.add_middleware(
    CORSMiddleware,
    allow_origins=["*"],
    allow_methods=["*"],
    allow_headers=["*"],
)

# --- DATABASE HELPERS ---

def get_db_connection():
    """Connect to SQLCipher database and apply key."""
    conn = sqlite.connect(DB_PATH)
    conn.execute(f"PRAGMA key = '{DB_PASSPHRASE}';")
    return conn

# --- ENDPOINTS ---

@app.get("/years")
def get_available_years():
    """Fetch unique years from the database."""
    conn = None
    try:
        conn = get_db_connection()
        cursor = conn.cursor()
        query = "SELECT DISTINCT CAST(strftime('%Y', date) AS INTEGER) as year FROM trades WHERE date IS NOT NULL ORDER BY year DESC"
        cursor.execute(query)
        years = [row[0] for row in cursor.fetchall()]
        return years
    except Exception as e:
        print(f"Error fetching years: {e}")
        return []
    finally:
        if conn: conn.close()

@app.post("/import", response_model=ImportResponse)
def run_import():
    """Reuse the existing import logic from main.py."""
    try:
        # Call your existing function that handles the full logic
        # This function likely scans the folder, parses, and saves to DB
        result_count = run_import_routine()
        
        return {
            "status": "success", 
            "message": "Import completed using main routine", 
            "count": result_count if isinstance(result_count, int) else 0
        }
    except Exception as e:
        print(f"Import Error: {e}")
        raise HTTPException(status_code=500, detail=str(e))

@app.post("/export")
def export_report(req: ExportRequest):
    """Placeholder for export logic (can also be imported from main)."""
    # ... (rest of your export logic)
    return {"status": "success", "message": "Export triggered"}

if __name__ == "__main__":
    uvicorn.run(app, host="127.0.0.1", port=8000)
