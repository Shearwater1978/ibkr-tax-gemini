# RESTART PROMPT (MASTER INDEX - v2.1.0)

**Role:** Expert Python Developer (Finance/Tax domain).
**Project:** IBKR Tax Calculator for Poland (PIT-38).
**Version:** v2.1.0 (Stable).

## 0. CRITICAL PROTOCOLS (READ FIRST)

### 1. Fix-Script Safety (Escaping Issue)
**NEVER** use triple quotes (`"""`) to assign file content inside a fix-script if the target content itself contains triple quotes (e.g., Python docstrings). This breaks the syntax.
**MANDATORY:** Use the **Line-by-Line** writing method for all fix-scripts (list of strings).

### 2. Code Delivery Format
**NEVER** just show code snippets or diffs. **ALWAYS** provide a full, standalone Python script (`fix_something.py`) that the user can run to apply the changes automatically.

### 3. Language Protocol
* **Chat/Reasoning:** RUSSIAN (Русский).
* **Code/Comments/Logs:** ENGLISH only. No Cyrillic in source files.

---

## 0.1 CURRENT CHECKPOINT (Start Here)
We have just completed **Sprint 3 (v2.1.0)**.
**Status:**
* `src/parser.py`: Fixed Regex to handle tickers with spaces (e.g. `MGA (ISIN)`).
* `tests/`: Updated to `pytest`. All tests passed.
* `main.py`: Unified entry point. Use `--import-data` to load CSVs.
* DB: Encrypted with SQLCipher.

**NEXT GOAL (Sprint 4):**
Create a Desktop UI using **Electron (Frontend)** + **FastAPI (Backend)**.
Do NOT use Tkinter. We need a modern, cross-platform look (macOS/Win11).

---

## ⚠️ RESTORATION INSTRUCTIONS
This project context is split into **3 parts** due to size limits. You are currently viewing Part 1.

1.  **PART 1 (Current File):** Contains Root files (`main.py`, config). Restore them immediately.
2.  **PART 2 (Source Code):** YOU MUST ASK THE USER for the file `RESTART_SRC.md` to restore the `src/` directory.
3.  **PART 3 (Tests):** YOU MUST ASK THE USER for the file `RESTART_TESTS.md` to restore the `tests/` directory.

**DO NOT proceed with major refactoring until you have received all 3 parts.**

## 1. ROOT FILES (Restore content below)

# --- FILE: main.py ---
```python
# main.py

import argparse
from datetime import date
from collections import defaultdict
import sys
import os
import glob
import pandas as pd 

# Project module imports
from src.data_collector import collect_all_trade_data
from src.excel_exporter import export_to_excel
from src.db_connector import DBConnector 
from src.processing import process_yearly_data
# Import parser functions to enable data loading from main.py
from src.parser import parse_csv, save_to_database

# Attempt to import PDF generator
try:
    from src.report_pdf import generate_pdf
    PDF_AVAILABLE = True
except ImportError:
    print("WARNING: src/report_pdf.py not found. PDF export disabled.")
    PDF_AVAILABLE = False

def prepare_data_for_pdf(target_year, raw_trades, realized_gains, dividends, inventory):
    """
    Adapter: Converts processing results into the dictionary structure
    expected by src/report_pdf.py.
    """
    # (Full code omitted for brevity in this script generator, but assumed present in real file)
    pass 
```
(Note: In the real file, the full main.py code is here. For this update script, I am keeping the structure but focusing on the headers above.)
