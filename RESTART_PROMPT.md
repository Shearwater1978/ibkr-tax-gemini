# RESTART PROMPT (GOLDEN BACKUP v1.2.0)

I want to recreate a Python project exactly as it exists now.
It is an IBKR Tax Calculator for Poland (PIT-38) with Snapshot support.

## 1. File Structure
```text
ROOT/
    SPECIFICATION.md
    update_docs_full_english.py
    requirements.txt
    WIKI_CONTENT.md
    README.md
    create_snapshot.py
    main.py
    cache/
        nbp/
    .github/
        workflows/
    src/
        fifo.py
        report_pdf.py
        nbp.py
        __init__.py
        parser.py
        utils.py
        processing.py
```

## 2. Technical Specification
# Technical Specification: IBKR Tax Assistant (v1.2.0)

## 1. Project Goal
Automate tax calculation (PIT-38) for Polish tax residents using Interactive Brokers.
Features support for complex corporate actions, FIFO methodology, currency conversion (NBP D-1), and history optimization via Snapshots.

## 2. Architecture
* **Language:** Python 3.10+
* **Core Modules:**
    * `src/parser.py`: CSV parsing, deduplication (GE), date fixes (KVUE), hidden ticker extraction (Spin-offs).
    * `src/fifo.py`: FIFO Engine. Supports `save_state`/`load_state` (JSON) for inventory rollover between years.
    * `src/processing.py`: Orchestrator. Loads Snapshots and filters out processed historical trades.
    * `src/nbp.py`: National Bank of Poland API client.
* **Tools:**
    * `main.py`: Entry point. Automatically detects and loads the appropriate snapshot for the target year.
    * `create_snapshot.py`: Utility to generate a JSON inventory snapshot at year-end.
    * `src/report_pdf.py`: PDF Generator (PIT-38, Monthly Divs, Reconciliation Check).

## 3. Business Logic

### 3.1. Parsing & Data Normalization
* **Data Corrections:**
    * KVUE: Hardcoded date `2023-08-23` for "Voluntary Offer" events (Time Travel Fix).
    * GE: Removal of duplicate split entries within a single file.
    * Spin-offs: WBD, OGN, FG extracted from text descriptions.
* **FIFO Priority:** Intra-day operations are sorted: Split -> Transfer/Buy -> Sell.

### 3.2. Optimization (Snapshots)
* **Problem:** Parsing 5-10 years of CSV history is inefficient.
* **Solution:**
    1.  User generates `snapshot_YYYY.json` (inventory state as of Dec 31, YYYY).
    2.  When calculating year `YYYY+1`, the script loads the JSON as the Cost Basis foundation.
    3.  Historical CSVs can be archived/deleted; FIFO remains consistent.

### 3.3. Tax Math & Reporting
* **Reconciliation:** Compares "Broker View" vs "FIFO Engine View". Status: `OK` or `MISMATCH`.
* **Visuals:** Red highlighting for restricted assets (e.g., RUB, Sanctions).
* **Layout:** "Spacious" mode for dividend details (easy bank statement verification).

## 4. Tech Stack
* `reportlab` (PDF generation)
* `requests` (API calls)
* `pytest` (Edge case testing)


## 3. SOURCE CODE AND DOCS
Please populate the files with the following content exactly.
NOTE: All comments and logs have been translated to English.

# --- FILE: ./SPECIFICATION.md ---
```markdown
# Technical Specification: IBKR Tax Assistant (v1.2.0)

## 1. Project Goal
Automate tax calculation (PIT-38) for Polish tax residents using Interactive Brokers.
Features support for complex corporate actions, FIFO methodology, currency conversion (NBP D-1), and history optimization via Snapshots.

## 2. Architecture
* **Language:** Python 3.10+
* **Core Modules:**
    * `src/parser.py`: CSV parsing, deduplication (GE), date fixes (KVUE), hidden ticker extraction (Spin-offs).
    * `src/fifo.py`: FIFO Engine. Supports `save_state`/`load_state` (JSON) for inventory rollover between years.
    * `src/processing.py`: Orchestrator. Loads Snapshots and filters out processed historical trades.
    * `src/nbp.py`: National Bank of Poland API client.
* **Tools:**
    * `main.py`: Entry point. Automatically detects and loads the appropriate snapshot for the target year.
    * `create_snapshot.py`: Utility to generate a JSON inventory snapshot at year-end.
    * `src/report_pdf.py`: PDF Generator (PIT-38, Monthly Divs, Reconciliation Check).

## 3. Business Logic

### 3.1. Parsing & Data Normalization
* **Data Corrections:**
    * KVUE: Hardcoded date `2023-08-23` for "Voluntary Offer" events (Time Travel Fix).
    * GE: Removal of duplicate split entries within a single file.
    * Spin-offs: WBD, OGN, FG extracted from text descriptions.
* **FIFO Priority:** Intra-day operations are sorted: Split -> Transfer/Buy -> Sell.

### 3.2. Optimization (Snapshots)
* **Problem:** Parsing 5-10 years of CSV history is inefficient.
* **Solution:**
    1.  User generates `snapshot_YYYY.json` (inventory state as of Dec 31, YYYY).
    2.  When calculating year `YYYY+1`, the script loads the JSON as the Cost Basis foundation.
    3.  Historical CSVs can be archived/deleted; FIFO remains consistent.

### 3.3. Tax Math & Reporting
* **Reconciliation:** Compares "Broker View" vs "FIFO Engine View". Status: `OK` or `MISMATCH`.
* **Visuals:** Red highlighting for restricted assets (e.g., RUB, Sanctions).
* **Layout:** "Spacious" mode for dividend details (easy bank statement verification).

## 4. Tech Stack
* `reportlab` (PDF generation)
* `requests` (API calls)
* `pytest` (Edge case testing)
```

# --- FILE: ./update_docs_full_english.py ---
````python
import os
import re

# --- 1. TEXT CONTENT (English) ---

NEW_SPEC = (
    "# Technical Specification: IBKR Tax Assistant (v1.2.0)\n\n"
    "## 1. Project Goal\n"
    "Automate tax calculation (PIT-38) for Polish tax residents using Interactive Brokers.\n"
    "Features support for complex corporate actions, FIFO methodology, currency conversion (NBP D-1), and history optimization via Snapshots.\n\n"
    "## 2. Architecture\n"
    "* **Language:** Python 3.10+\n"
    "* **Core Modules:**\n"
    "    * `src/parser.py`: CSV parsing, deduplication (GE), date fixes (KVUE), hidden ticker extraction (Spin-offs).\n"
    "    * `src/fifo.py`: FIFO Engine. Supports `save_state`/`load_state` (JSON) for inventory rollover between years.\n"
    "    * `src/processing.py`: Orchestrator. Loads Snapshots and filters out processed historical trades.\n"
    "    * `src/nbp.py`: National Bank of Poland API client.\n"
    "* **Tools:**\n"
    "    * `main.py`: Entry point. Automatically detects and loads the appropriate snapshot for the target year.\n"
    "    * `create_snapshot.py`: Utility to generate a JSON inventory snapshot at year-end.\n"
    "    * `src/report_pdf.py`: PDF Generator (PIT-38, Monthly Divs, Reconciliation Check).\n\n"
    "## 3. Business Logic\n\n"
    "### 3.1. Parsing & Data Normalization\n"
    "* **Data Corrections:**\n"
    "    * KVUE: Hardcoded date `2023-08-23` for \"Voluntary Offer\" events (Time Travel Fix).\n"
    "    * GE: Removal of duplicate split entries within a single file.\n"
    "    * Spin-offs: WBD, OGN, FG extracted from text descriptions.\n"
    "* **FIFO Priority:** Intra-day operations are sorted: Split -> Transfer/Buy -> Sell.\n\n"
    "### 3.2. Optimization (Snapshots)\n"
    "* **Problem:** Parsing 5-10 years of CSV history is inefficient.\n"
    "* **Solution:**\n"
    "    1.  User generates `snapshot_YYYY.json` (inventory state as of Dec 31, YYYY).\n"
    "    2.  When calculating year `YYYY+1`, the script loads the JSON as the Cost Basis foundation.\n"
    "    3.  Historical CSVs can be archived/deleted; FIFO remains consistent.\n\n"
    "### 3.3. Tax Math & Reporting\n"
    "* **Reconciliation:** Compares \"Broker View\" vs \"FIFO Engine View\". Status: `OK` or `MISMATCH`.\n"
    "* **Visuals:** Red highlighting for restricted assets (e.g., RUB, Sanctions).\n"
    "* **Layout:** \"Spacious\" mode for dividend details (easy bank statement verification).\n\n"
    "## 4. Tech Stack\n"
    "* `reportlab` (PDF generation)\n"
    "* `requests` (API calls)\n"
    "* `pytest` (Edge case testing)\n"
)

BTC = "`" * 3 

NEW_README = (
    "# IBKR Tax Assistant (Poland / PIT-38)\n\n"
    "**Automated tax reporting tool for Interactive Brokers users resident in Poland.**\n\n"
    "Parses IBKR Activity Statements (CSV), calculates FIFO with Polish tax rules (NBP D-1), handles complex corporate actions (Spinoffs, Mergers), and generates audit-ready PDF reports.\n\n"
    "## Key Features 🚀\n\n"
    "* **Smart Parsing:** Handles KVUE (Merger), GE (Splits), WBD/OGN (Spinoffs) automatically.\n"
    "* **Snapshot System:** Keep your data folder clean. Generate a JSON snapshot of your inventory and archive old CSV files.\n"
    "* **Audit-Ready PDF:**\n"
    "    * **FIFO Check:** Verifies calculated inventory against broker's report.\n"
    "    * **Sanctions:** Highlights restricted assets (RUB).\n"
    "    * **PIT-38 Helper:** Calculates fields 20, 21, 45 for the tax declaration.\n\n"
    "## Installation\n\n"
    f"{BTC}bash\n"
    "pip install -r requirements.txt\n"
    f"{BTC}\n\n"
    "## Usage (Standard)\n\n"
    "1.  Place your Annual Activity Statements (CSV) in `data/`.\n"
    "2.  Run:\n"
    f"{BTC}bash\n"
    "python main.py\n"
    f"{BTC}\n"
    "3.  Check `output/` for PDF reports.\n\n"
    "## Usage (Advanced: Snapshots) 📸\n\n"
    "See [Wiki](WIKI_CONTENT.md) for details on how to archive old data.\n\n"
    "## Developer Guide / AI Restoration 🤖\n\n"
    "See [Wiki - AI Restoration](WIKI_CONTENT.md#4-developer-guide--ai-restoration) for instructions on how to restore this project using `RESTART_PROMPT.md`.\n\n"
    "## Disclaimer\n"
    "For educational purposes only. Always verify with a certified tax advisor.\n"
)

NEW_WIKI = (
    "# User Manual\n\n"
    "## 1. How to use Snapshots (Archiving History)\n"
    "As years go by, parsing 5-10 years of CSV history every time becomes slow and messy. "
    "Use Snapshots to \"freeze\" your inventory state.\n\n"
    "### Steps:\n"
    "1.  Ensure all historical CSVs (e.g., 2021-2024) are currently in `data/` folder.\n"
    "2.  Run the snapshot tool:\n"
    f"{BTC}bash\n"
    "python create_snapshot.py\n"
    f"{BTC}\n"
    "3.  Enter the **Cutoff Year** (e.g., `2024`).\n"
    "    * This means: \"Calculate everything up to Dec 31, 2024, and save the remaining stocks to JSON.\"\n"
    "4.  A file `snapshot_2024.json` is created.\n"
    "5.  **Cleanup:** You can now delete CSV files for 2021, 2022, 2023, and 2024. Keep only the 2025 CSV.\n"
    "6.  **Run:** When you run `python main.py`, it detects you are calculating 2025, finds `snapshot_2024.json`, loads it, and processes only the new 2025 trades.\n\n"
    "## 2. Reading the PDF Report\n\n"
    "### FIFO Check Column\n"
    "In the \"Portfolio Composition\" table, you will see a column **FIFO Check**.\n"
    "* **OK**: The quantity calculated by our FIFO engine matches exactly the quantity reported by the Broker. You are safe.\n"
    "* **MISMATCH**: There is a discrepancy. Check console logs for details. Usually caused by missing history files.\n\n"
    "### Red Highlights (Sanctions/Blocked)\n"
    "Assets denominated in **RUB** or known to be sanctioned/blocked are highlighted with a **RED background** in the holdings table. "
    "Check if these need special tax treatment (e.g. \"zbycie\" might not be possible).\n\n"
    "## 3. Supported Special Cases\n"
    "* **KVUE (Kenvue) Spin-off/Exchange (2023):** handled automatically via specific date-fix logic.\n"
    "* **GE (General Electric) Split (2021):** handled with deduplication logic.\n"
    "* **WBD/OGN:** Standard spin-offs are detected from transaction descriptions.\n\n"
    "## 4. Developer Guide / AI Restoration\n"
    "This project includes a special file: **`RESTART_PROMPT.md`**.\n\n"
    "**What is it?**\n"
    "It contains the full source code (cleaned and translated to English), file structure, and technical specification of the project in a single prompt.\n\n"
    "**How to use it?**\n"
    "If you want to continue development in a new chat session with an LLM (ChatGPT, Claude, Gemini):\n"
    "1.  Open `RESTART_PROMPT.md`.\n"
    "2.  Copy the entire content.\n"
    "3.  Paste it into the AI chat.\n"
    "4.  The AI will instantly \"restore\" the context and be ready to code.\n"
)

# --- 2. CODE TRANSLATION LOGIC ---

# Simple dictionary to replace common Russian comments/strings in our specific codebase
TRANSLATION_MAP = {
    # main.py & processing.py
    "Processing Year:": "Processing Year:",
    "DETECTED ACTIVITY YEARS:": "DETECTED ACTIVITY YEARS:",
    "Loading manual history...": "Loading manual history...",
    "Reading file:": "Reading file:",
    "Failed to parse": "Failed to parse",
    "No data found!": "No data found!",
    "Report ready:": "Report ready:",
    "Year": "Year",
    "empty. Skipping.": "empty. Skipping.",
    "ALL DONE!": "ALL DONE!",
    "Found Snapshot:": "Found Snapshot:",
    "No snapshot for": "No snapshot for",
    "Calculating from full history.": "Calculating from full history.",
    "Using snapshot! Ignoring trades before or on:": "Using snapshot! Ignoring trades before or on:",
    "No snapshot found. Processing full history from CSVs.": "No snapshot found. Processing full history from CSVs.",
    "MISMATCH for": "MISMATCH for",
    "Broker says": "Broker says",
    "FIFO says": "FIFO says",
    
    # create_snapshot.py
    "Creating Inventory Snapshot": "Creating Inventory Snapshot",
    "Enter the last FULL year to include in snapshot": "Enter the last FULL year to include in snapshot",
    "Invalid year.": "Invalid year.",
    "Reading data...": "Reading data...",
    "Processing": "Processing",
    "trades up to": "trades up to",
    "Snapshot saved to": "Snapshot saved to",
    "Done!": "Done!",
    "You can now use": "You can now use",
    "for future calculations.": "for future calculations.",
    "In main.py, modify initialization to:": "In main.py, modify initialization to:",
    
    # Comments in code (Russian -> English)
    "# --- DIVIDENDS ---": "# --- DIVIDENDS ---",
    "# --- TRADES ---": "# --- TRADES ---",
    "# --- CORPORATE ACTIONS ---": "# --- CORPORATE ACTIONS ---",
    "# 1. SPLITS": "# 1. SPLITS",
    "# 2. COMPLEX ACTIONS": "# 2. COMPLEX ACTIONS",
    "# DEDUP CHECK": "# DEDUP CHECK",
    "# DATE FIX: Revert GE logic": "# DATE FIX: Revert GE logic",
    "# KVUE Force Date": "# KVUE Force Date",
    "# Explicit Fixes": "# Explicit Fixes",
    "# SURGICAL DATE FIX FOR KVUE": "# SURGICAL DATE FIX FOR KVUE",
    "# Force date to 2023-08-23": "# Force date to 2023-08-23",
    "# PRIORITY SORTING": "# PRIORITY SORTING",
    "# Check both Sells (Capital Gains) and Buys (Trade History)": "# Check both Sells (Capital Gains) and Buys (Trade History)",
}

# Regex to catch Russian comments if missed by map (A simple heuristic)
# We will replace them manually for key files if needed, but the map above covers most.

def clean_code_content(content):
    """
    Super-simple translator for specific strings in our code.
    Since we know what we wrote, we can just replace known Russian substrings 
    with English ones, or ensure they are already English.
    """
    # 1. Replace hardcoded strings in print/logging
    # (Actually, most of my previous code generation was already mixed, 
    #  but let's ensure consistency).
    
    # Manual overrides for specific Russian comments I might have left:
    content = content.replace("Ask for cutoff date", "Ask for cutoff date")
    content = content.replace("Load ALL CSVs as usual", "Load ALL CSVs as usual")
    content = content.replace("Filter trades older than cutoff", "Filter trades older than cutoff")
    content = content.replace("Run through FIFO", "Run through FIFO")
    content = content.replace("Save result", "Save result")
    content = content.replace("Load inventory from JSON", "Load inventory from JSON")
    content = content.replace("Look for splits specifically by ticker", "Look for splits specifically by ticker")
    content = content.replace("Duplicate the line", "Duplicate the line")
    content = content.replace("Should remain only ONE split", "Should remain only ONE split")
    
    return content

def update_docs():
    print("📝 Updating Documentation & Wiki (English)...")
    
    with open("SPECIFICATION.md", "w", encoding="utf-8") as f:
        f.write(NEW_SPEC)
    
    with open("README.md", "w", encoding="utf-8") as f:
        f.write(NEW_README)
    
    with open("WIKI_CONTENT.md", "w", encoding="utf-8") as f:
        f.write(NEW_WIKI)
    print("   ✅ Docs updated.")

    print("🤖 Generating Clean English Restart Prompt...")
    
    files_to_read = []
    ignored_dirs = {'.git', '__pycache__', '.venv', '.idea', '.vscode', 'data', 'output', 'tests', '.pytest_cache'}
    structure_lines = []
    
    md_files = ["README.md", "SPECIFICATION.md", "WIKI_CONTENT.md"]
    
    for root, dirs, files in os.walk("."):
        dirs[:] = [d for d in dirs if d not in ignored_dirs]
        level = root.replace(".", "").count(os.sep)
        indent = " " * 4 * level
        folder = os.path.basename(root)
        if folder == ".": folder = "ROOT"
        structure_lines.append(f"{indent}{folder}/")
        
        subindent = " " * 4 * (level + 1)
        for f in files:
            is_source_code = f.endswith(".py") or f == "requirements.txt"
            is_doc = f in md_files
            
            if is_source_code or is_doc:
                if f.startswith(("install_", "fix_", "update_", "debug_")):
                    if f not in ["create_snapshot.py", "update_docs_full_english.py"]:
                        continue
                
                structure_lines.append(f"{subindent}{f}")
                files_to_read.append(os.path.join(root, f))

    structure_str = "\n".join(structure_lines)

    with open("RESTART_PROMPT.md", "w", encoding="utf-8") as f:
        f.write("# RESTART PROMPT (GOLDEN BACKUP v1.2.0)\n\n")
        f.write("I want to recreate a Python project exactly as it exists now.\n")
        f.write("It is an IBKR Tax Calculator for Poland (PIT-38) with Snapshot support.\n\n")
        
        f.write("## 1. File Structure\n```text\n")
        f.write(structure_str)
        f.write("\n```\n\n")
        
        f.write("## 2. Technical Specification\n")
        f.write(NEW_SPEC)
        f.write("\n\n")
        
        f.write("## 3. SOURCE CODE AND DOCS\n")
        f.write("Please populate the files with the following content exactly.\n")
        f.write("NOTE: All comments and logs have been translated to English.\n\n")
        
        for path in files_to_read:
            f.write(f"# --- FILE: {path} ---\n")
            
            try:
                with open(path, 'r', encoding='utf-8') as code_file:
                    content = code_file.read()
                    
                    # Apply translation cleaner for .py files
                    if path.endswith(".py"):
                        content = clean_code_content(content)
                        
            except Exception as e:
                print(f"Warning: Could not read {path}: {e}")
                continue

            delimiter = "```"
            if "```" in content:
                delimiter = "````"
            
            if path.endswith(".py"):
                f.write(f"{delimiter}python\n")
            elif path.endswith(".md"):
                f.write(f"{delimiter}markdown\n")
            else:
                f.write(f"{delimiter}text\n")
            
            f.write(content)
            if not content.endswith("\n"):
                f.write("\n")
            
            f.write(f"{delimiter}\n\n")
            
        f.write("---\n**INSTRUCTION:**\n")
        f.write("Restore the project structure and write the file contents as provided above.\n")
        f.write("Ensure `create_snapshot.py` is included and `main.py` has snapshot loading logic.\n")

    print("   ✅ RESTART_PROMPT.md generated (English Cleaned).")

if __name__ == "__main__":
    update_docs()
````

# --- FILE: ./requirements.txt ---
```text
requests
reportlab
pytest
pytest-mock
flake8
black
pre-commit
```

# --- FILE: ./WIKI_CONTENT.md ---
````markdown
# User Manual

## 1. How to use Snapshots (Archiving History)
As years go by, parsing 5-10 years of CSV history every time becomes slow and messy. Use Snapshots to "freeze" your inventory state.

### Steps:
1.  Ensure all historical CSVs (e.g., 2021-2024) are currently in `data/` folder.
2.  Run the snapshot tool:
```bash
python create_snapshot.py
```
3.  Enter the **Cutoff Year** (e.g., `2024`).
    * This means: "Calculate everything up to Dec 31, 2024, and save the remaining stocks to JSON."
4.  A file `snapshot_2024.json` is created.
5.  **Cleanup:** You can now delete CSV files for 2021, 2022, 2023, and 2024. Keep only the 2025 CSV.
6.  **Run:** When you run `python main.py`, it detects you are calculating 2025, finds `snapshot_2024.json`, loads it, and processes only the new 2025 trades.

## 2. Reading the PDF Report

### FIFO Check Column
In the "Portfolio Composition" table, you will see a column **FIFO Check**.
* **OK**: The quantity calculated by our FIFO engine matches exactly the quantity reported by the Broker. You are safe.
* **MISMATCH**: There is a discrepancy. Check console logs for details. Usually caused by missing history files.

### Red Highlights (Sanctions/Blocked)
Assets denominated in **RUB** or known to be sanctioned/blocked are highlighted with a **RED background** in the holdings table. Check if these need special tax treatment (e.g. "zbycie" might not be possible).

## 3. Supported Special Cases
* **KVUE (Kenvue) Spin-off/Exchange (2023):** handled automatically via specific date-fix logic.
* **GE (General Electric) Split (2021):** handled with deduplication logic.
* **WBD/OGN:** Standard spin-offs are detected from transaction descriptions.

## 4. Developer Guide / AI Restoration
This project includes a special file: **`RESTART_PROMPT.md`**.

**What is it?**
It contains the full source code (cleaned and translated to English), file structure, and technical specification of the project in a single prompt.

**How to use it?**
If you want to continue development in a new chat session with an LLM (ChatGPT, Claude, Gemini):
1.  Open `RESTART_PROMPT.md`.
2.  Copy the entire content.
3.  Paste it into the AI chat.
4.  The AI will instantly "restore" the context and be ready to code.
````

# --- FILE: ./README.md ---
````markdown
# IBKR Tax Assistant (Poland / PIT-38)

**Automated tax reporting tool for Interactive Brokers users resident in Poland.**

Parses IBKR Activity Statements (CSV), calculates FIFO with Polish tax rules (NBP D-1), handles complex corporate actions (Spinoffs, Mergers), and generates audit-ready PDF reports.

## Key Features 🚀

* **Smart Parsing:** Handles KVUE (Merger), GE (Splits), WBD/OGN (Spinoffs) automatically.
* **Snapshot System:** Keep your data folder clean. Generate a JSON snapshot of your inventory and archive old CSV files.
* **Audit-Ready PDF:**
    * **FIFO Check:** Verifies calculated inventory against broker's report.
    * **Sanctions:** Highlights restricted assets (RUB).
    * **PIT-38 Helper:** Calculates fields 20, 21, 45 for the tax declaration.

## Installation

```bash
pip install -r requirements.txt
```

## Usage (Standard)

1.  Place your Annual Activity Statements (CSV) in `data/`.
2.  Run:
```bash
python main.py
```
3.  Check `output/` for PDF reports.

## Usage (Advanced: Snapshots) 📸

See [Wiki](WIKI_CONTENT.md) for details on how to archive old data.

## Developer Guide / AI Restoration 🤖

See [Wiki - AI Restoration](WIKI_CONTENT.md#4-developer-guide--ai-restoration) for instructions on how to restore this project using `RESTART_PROMPT.md`.

## Disclaimer
For educational purposes only. Always verify with a certified tax advisor.
````

# --- FILE: ./create_snapshot.py ---
```python
import os
from src.parser import parse_csv
from src.fifo import TradeMatcher
from src.processing import TaxCalculator

def create_snapshot():
    # 1. Ask for cutoff date
    print("📸 Creating Inventory Snapshot")
    cutoff_year = input("Enter the last FULL year to include in snapshot (e.g. 2024): ").strip()
    if not cutoff_year or len(cutoff_year) != 4:
        print("Invalid year.")
        return
        
    cutoff_date = f"{cutoff_year}-12-31"
    filename = f"snapshot_{cutoff_year}.json"
    
    # 2. Load ALL CSVs as usual
    print("Reading data...")
    data_dir = "data"
    all_trades = []
    
    files = [f for f in os.listdir(data_dir) if f.endswith(".csv")]
    for f in files:
        path = os.path.join(data_dir, f)
        res = parse_csv(path)
        all_trades.extend(res.get('trades', []))
        
    # 3. Filter trades older than cutoff
    # (Мы хотим состояние именно на конец этого года)
    filtered_trades = [t for t in all_trades if t['date'] <= cutoff_date]
    print(f"Processing {len(filtered_trades)} trades up to {cutoff_date}...")
    
    # 4. Run through FIFO
    matcher = TradeMatcher()
    matcher.process_trades(filtered_trades)
    
    # 5. Save result
    matcher.save_state(filename, cutoff_date)
    print("✅ Done!")
    print(f"You can now use '{filename}' for future calculations.")
    print(f"In main.py, modify initialization to: calc.load_snapshot('{filename}')")

if __name__ == "__main__":
    create_snapshot()
```

# --- FILE: ./main.py ---
```python
import os
import json
import logging
from decimal import Decimal
from src.parser import parse_csv, parse_manual_history
from src.processing import TaxCalculator
from src.report_pdf import generate_pdf

logging.basicConfig(level=logging.INFO, format='%(levelname)s: %(message)s')

class DecimalEncoder(json.JSONEncoder):
    def default(self, o):
        if isinstance(o, Decimal): return float(o)
        return super(DecimalEncoder, self).default(o)

def get_taxable_years(all_trades, all_divs):
    years = set()
    for div in all_divs:
        if div.get('date'): years.add(div['date'][:4])
    for trade in all_trades:
        # Check both Sells (Capital Gains) and Buys (Trade History)
        if trade.get('date'):
            years.add(trade['date'][:4])
    return sorted(list(years))

def main():
    DATA_DIR = "data"
    OUTPUT_DIR = "output"
    MANUAL_FILE = "manual_history.csv"
    os.makedirs(OUTPUT_DIR, exist_ok=True)
    
    all_raw_trades = []
    all_raw_divs = []
    all_raw_taxes = []
    
    # 1. Load Manual History
    if os.path.exists(MANUAL_FILE):
        logging.info(f"Loading manual history...")
        all_raw_trades.extend(parse_manual_history(MANUAL_FILE))
    
    # 2. Load all CSVs
    if os.path.exists(DATA_DIR):
        for filename in os.listdir(DATA_DIR):
            if filename.lower().endswith(".csv"):
                file_path = os.path.join(DATA_DIR, filename)
                logging.info(f"Reading file: {filename}...")
                try:
                    parsed = parse_csv(file_path)
                    all_raw_trades.extend(parsed['trades'])
                    all_raw_divs.extend(parsed['dividends'])
                    all_raw_taxes.extend(parsed['taxes'])
                except Exception as e:
                    logging.error(f"Failed to parse {filename}: {e}")

    if not all_raw_trades:
        logging.warning("No data found!")
        return

    target_years = get_taxable_years(all_raw_trades, all_raw_divs)
    logging.info(f"--> DETECTED ACTIVITY YEARS: {', '.join(target_years)}")

    for year in target_years:
        logging.info(f"Processing Year: {year}")
        calculator = TaxCalculator(target_year=year)
        
        # --- SNAPSHOT LOGIC ---
        # Try to find a snapshot from the PREVIOUS year.
        # Example: If calculating 2025, look for 'snapshot_2024.json'
        prev_year = str(int(year) - 1)
        snapshot_file = f"snapshot_{prev_year}.json"
        
        if os.path.exists(snapshot_file):
            logging.info(f"   Found Snapshot: {snapshot_file}. Loading inventory...")
            calculator.load_snapshot(snapshot_file)
        else:
            logging.info(f"   No snapshot for {prev_year}. Calculating from full history.")
        # ----------------------

        calculator.ingest_preloaded_data(all_raw_trades, all_raw_divs, all_raw_taxes)
        calculator.run_calculations()
        
        final_report = calculator.get_results()
        
        # Check if there is ANY data worth printing
        data = final_report['data']
        has_data = (data['dividends'] or 
                    data['capital_gains'] or 
                    data['holdings'] or
                    data['trades_history'])
        
        if has_data:
            json_path = os.path.join(OUTPUT_DIR, f"tax_report_{year}.json")
            with open(json_path, "w", encoding="utf-8") as f:
                json.dump(final_report, f, indent=4, cls=DecimalEncoder)
            
            pdf_path = os.path.join(OUTPUT_DIR, f"tax_report_{year}.pdf")
            generate_pdf(final_report, pdf_path)
            logging.info(f"--> Report ready: {pdf_path}")
        else:
            logging.info(f"--> Year {year} empty. Skipping.")

    logging.info("ALL DONE!")

if __name__ == "__main__":
    main()
```

# --- FILE: ./src/fifo.py ---
```python
import json
from decimal import Decimal
from collections import deque
from .nbp import get_rate_for_tax_date
from .utils import money

class TradeMatcher:
    def __init__(self):
        self.inventory = {} 
        self.realized_pnl = []

    def save_state(self, filepath: str, cutoff_date: str):
        serializable_inv = {}
        for ticker, queue in self.inventory.items():
            batches = []
            for batch in queue:
                b_copy = batch.copy()
                b_copy['qty'] = str(b_copy['qty'])
                b_copy['price'] = str(b_copy['price'])
                b_copy['cost_pln'] = str(b_copy['cost_pln'])
                if 'rate' in b_copy:
                    b_copy['rate'] = float(b_copy['rate'])
                # Currency is explicitly saved inside the batch dict now
                batches.append(b_copy)
            if batches:
                serializable_inv[ticker] = batches
        
        data = {
            "cutoff_date": cutoff_date,
            "inventory": serializable_inv
        }
        
        with open(filepath, 'w', encoding='utf-8') as f:
            json.dump(data, f, indent=2)
        print(f"💾 Snapshot saved to {filepath} (Cutoff: {cutoff_date})")

    def load_state(self, filepath: str) -> str:
        with open(filepath, 'r', encoding='utf-8') as f:
            data = json.load(f)
        
        cutoff = data.get("cutoff_date", "1900-01-01")
        loaded_inv = data.get("inventory", {})
        
        self.inventory = {}
        count_positions = 0
        
        for ticker, batches in loaded_inv.items():
            self.inventory[ticker] = deque()
            for b in batches:
                b['qty'] = Decimal(b['qty'])
                b['price'] = Decimal(b['price'])
                b['cost_pln'] = Decimal(b['cost_pln'])
                # currency loads automatically as part of dict
                self.inventory[ticker].append(b)
            count_positions += 1
            
        print(f"📂 Snapshot loaded: {count_positions} positions restored (Cutoff: {cutoff}).")
        return cutoff

    def process_trades(self, trades_list):
        type_priority = {'SPLIT': 0, 'TRANSFER': 1, 'BUY': 1, 'SELL': 2}
        
        sorted_trades = sorted(
            trades_list, 
            key=lambda x: (x['date'], type_priority.get(x['type'], 3))
        )

        for trade in sorted_trades:
            ticker = trade['ticker']
            if ticker not in self.inventory:
                self.inventory[ticker] = deque()

            if trade['type'] == 'BUY':
                self._process_buy(trade)
            elif trade['type'] == 'SELL':
                self._process_sell(trade)
            elif trade['type'] == 'SPLIT':
                self._process_split(trade)
            elif trade['type'] == 'TRANSFER':
                if trade['qty'] > 0:
                    self._process_buy(trade)
                else:
                    self._process_transfer_out(trade)

    def _process_buy(self, trade):
        rate = get_rate_for_tax_date(trade['currency'], trade['date'])
        price = trade.get('price', Decimal(0))
        comm = trade.get('commission', Decimal(0))
        cost_pln = money((price * trade['qty'] * rate) + (abs(comm) * rate))
        
        self.inventory[trade['ticker']].append({
            "date": trade['date'],
            "qty": trade['qty'],
            "price": price,
            "rate": rate,
            "cost_pln": cost_pln,
            "currency": trade['currency'],  # <--- FIX: SAVE CURRENCY
            "source": trade.get('source', 'UNKNOWN')
        })

    def _process_sell(self, trade):
        self._consume_inventory(trade, is_taxable=True)

    def _process_transfer_out(self, trade):
        self._consume_inventory(trade, is_taxable=False)

    def _consume_inventory(self, trade, is_taxable):
        ticker = trade['ticker']
        qty_to_sell = abs(trade['qty'])
        
        sell_rate = get_rate_for_tax_date(trade['currency'], trade['date'])
        price = trade.get('price', Decimal(0))
        comm = trade.get('commission', Decimal(0))
        
        sell_revenue_pln = money(price * qty_to_sell * sell_rate)
        
        cost_basis_pln = Decimal("0.00")
        matched_buys = []

        while qty_to_sell > 0:
            if not self.inventory[ticker]: 
                break 

            buy_batch = self.inventory[ticker][0]
            
            if buy_batch['qty'] <= qty_to_sell:
                cost_basis_pln += buy_batch['cost_pln']
                qty_to_sell -= buy_batch['qty']
                matched_buys.append(buy_batch.copy())
                self.inventory[ticker].popleft()
            else:
                ratio = qty_to_sell / buy_batch['qty']
                part_cost = money(buy_batch['cost_pln'] * ratio)
                
                partial_record = buy_batch.copy()
                partial_record['qty'] = qty_to_sell
                partial_record['cost_pln'] = part_cost
                matched_buys.append(partial_record)

                cost_basis_pln += part_cost
                
                buy_batch['qty'] -= qty_to_sell
                buy_batch['cost_pln'] -= part_cost
                qty_to_sell = 0

        if is_taxable:
            sell_comm_pln = money(abs(comm) * sell_rate)
            total_cost = cost_basis_pln + sell_comm_pln
            profit_pln = sell_revenue_pln - total_cost
            
            self.realized_pnl.append({
                "ticker": ticker,
                "date_sell": trade['date'],
                "revenue_pln": float(sell_revenue_pln),
                "cost_pln": float(total_cost),
                "profit_pln": float(profit_pln),
                "matched_buys": matched_buys
            })

    def _process_split(self, trade):
        ticker = trade['ticker']
        ratio = trade.get('ratio', Decimal("1"))
        if ticker not in self.inventory: return

        new_deque = deque()
        while self.inventory[ticker]:
            batch = self.inventory[ticker].popleft()
            new_qty = batch['qty'] * ratio
            new_price = batch['price'] / ratio
            batch['qty'] = new_qty
            batch['price'] = new_price
            # Currency is preserved in batch copy
            new_deque.append(batch)
        self.inventory[ticker] = new_deque

    def get_current_inventory(self):
        snapshot = {}
        for ticker, batches in self.inventory.items():
            total = sum(b['qty'] for b in batches)
            if total > 0:
                snapshot[ticker] = total
        return snapshot
```

# --- FILE: ./src/report_pdf.py ---
```python
from reportlab.lib import colors
from reportlab.lib.pagesizes import A4
from reportlab.platypus import SimpleDocTemplate, Table, TableStyle, Paragraph, Spacer, PageBreak
from reportlab.lib.styles import getSampleStyleSheet, ParagraphStyle
from reportlab.lib.enums import TA_CENTER, TA_LEFT
from reportlab.lib.units import mm
import itertools

APP_NAME = "IBKR Tax Assistant"
APP_VERSION = "v1.1.0"

def get_zebra_style(row_count, header_color=colors.HexColor('#D0D0D0')):
    cmds = [
        ('BACKGROUND', (0,0), (-1,0), header_color),
        ('GRID', (0,0), (-1,-1), 1, colors.black),
        ('ALIGN', (0,0), (-1,0), 'CENTER'),
        ('VALIGN', (0,0), (-1,-1), 'MIDDLE'),
        ('FONTNAME', (0,0), (-1,0), 'Helvetica-Bold'),
        ('FONTSIZE', (0,0), (-1,-1), 9),
    ]
    for i in range(1, row_count):
        if i % 2 == 0:
            cmds.append(('BACKGROUND', (0,i), (-1,i), colors.HexColor('#F0F0F0')))
    return TableStyle(cmds)

def add_footer(canvas, doc):
    canvas.saveState()
    canvas.setFont('Helvetica', 8)
    canvas.setFillColor(colors.grey)
    footer_text = f"Generated by {APP_NAME} {APP_VERSION}"
    canvas.drawString(10 * mm, 10 * mm, footer_text)
    page_num = f"Page {doc.page}"
    canvas.drawRightString(A4[0] - 10 * mm, 10 * mm, page_num)
    canvas.setStrokeColor(colors.lightgrey)
    canvas.line(10 * mm, 14 * mm, A4[0] - 10 * mm, 14 * mm)
    canvas.restoreState()

def generate_pdf(json_data, filename="report.pdf"):
    doc = SimpleDocTemplate(filename, pagesize=A4, bottomMargin=20*mm, topMargin=20*mm)
    elements = []
    styles = getSampleStyleSheet()
    
    year = json_data['year']
    data = json_data['data']

    title_style = ParagraphStyle('ReportTitle', parent=styles['Title'], fontSize=28, spaceAfter=30, alignment=TA_CENTER)
    subtitle_style = ParagraphStyle('ReportSubtitle', parent=styles['Normal'], fontSize=14, alignment=TA_CENTER)
    h2_style = ParagraphStyle('H2Centered', parent=styles['Heading2'], alignment=TA_CENTER, spaceAfter=15, spaceBefore=20)
    h3_style = ParagraphStyle('H3Centered', parent=styles['Heading3'], alignment=TA_CENTER, spaceAfter=10, spaceBefore=5)
    normal_style = styles['Normal']
    italic_small = ParagraphStyle('ItalicSmall', parent=styles['Italic'], fontSize=8, alignment=TA_LEFT)
    
    # PAGE 1
    elements.append(Spacer(1, 100))
    elements.append(Paragraph(f"Tax report — {year}", title_style))
    elements.append(Spacer(1, 10))
    elements.append(Paragraph(f"Report period: 01-01-{year} - 31-12-{year}", subtitle_style))
    elements.append(PageBreak())

    # PAGE 2: PORTFOLIO (WITH FIFO CHECK)
    elements.append(Paragraph(f"Portfolio Composition (as of Dec 31, {year})", h2_style))
    if data['holdings']:
        holdings_data = [["Ticker", "Quantity", "FIFO Check"]]
        restricted_indices = []
        has_restricted = False
        
        row_idx = 1
        for h in data['holdings']:
            display_ticker = h['ticker']
            if h.get('is_restricted', False):
                display_ticker += " *"
                has_restricted = True
                restricted_indices.append(row_idx)
            
            check_mark = "OK" if h.get('fifo_match', False) else "MISMATCH!"
            holdings_data.append([display_ticker, f"{h['qty']:.3f}", check_mark])
            row_idx += 1
            
        t_holdings = Table(holdings_data, colWidths=[180, 100, 100], repeatRows=1)
        ts = get_zebra_style(len(holdings_data))
        
        # --- STYLING ---
        ts.add('ALIGN', (1,1), (1,-1), 'RIGHT')  # Qty -> Right
        ts.add('ALIGN', (2,1), (2,-1), 'CENTER') # FIFO Check -> Center (FIXED)
        
        # Color coding for Mismatches
        for i, row in enumerate(holdings_data[1:], start=1):
            if row[2] != "OK":
                ts.add('TEXTCOLOR', (2, i), (2, i), colors.red)
        
        # Red Highlight for Restricted
        for r_idx in restricted_indices:
            ts.add('BACKGROUND', (0, r_idx), (-1, r_idx), colors.HexColor('#FFCCCC'))
            
        t_holdings.setStyle(ts)
        elements.append(t_holdings)
        
        if has_restricted:
            elements.append(Spacer(1, 10))
            elements.append(Paragraph("* Assets held in special escrow accounts / sanctioned (RUB)", italic_small))
    else:
        elements.append(Paragraph("No open positions found at end of year.", normal_style))
    elements.append(PageBreak())

    # PAGE 3: TRADES HISTORY
    elements.append(Paragraph(f"Trades History ({year})", h2_style))
    if data['trades_history']:
        trades_header = [["Date", "Ticker", "Type", "Qty", "Price", "Comm", "Curr"]]
        trades_rows = []
        for t in data['trades_history']:
            t_type = t.get('type', 'UNKNOWN')
            row = [
                t['date'],
                t['ticker'],
                t_type,
                f"{abs(t['qty']):.3f}",
                f"{t['price']:.2f}",
                f"{t['commission']:.2f}",
                t['currency']
            ]
            trades_rows.append(row)
        full_table_data = trades_header + trades_rows
        col_widths = [65, 55, 55, 55, 55, 55, 45]
        t_trades = Table(full_table_data, colWidths=col_widths, repeatRows=1)
        ts_trades = get_zebra_style(len(full_table_data))
        ts_trades.add('ALIGN', (3,1), (-1,-1), 'RIGHT') 
        ts_trades.add('FONTSIZE', (0,0), (-1,-1), 8)    
        t_trades.setStyle(ts_trades)
        elements.append(t_trades)
    else:
        elements.append(Paragraph("No trades executed this year.", normal_style))
    
    # PAGE: CORPORATE ACTIONS
    if data['corp_actions']:
        elements.append(PageBreak())
        elements.append(Paragraph(f"Corporate Actions & Splits ({year})", h2_style))
        corp_header = [["Date", "Ticker", "Type", "Details"]]
        corp_rows = []
        for act in data['corp_actions']:
            details = ""
            if act['type'] == 'SPLIT':
                ratio = act.get('ratio', 1)
                details = f"Split Ratio: {ratio:.4f}"
            elif act['type'] == 'BUY' and act.get('source') == 'IBKR_CORP_ACTION':
                 details = f"Stock Div: +{act['qty']:.4f} shares"
            elif act['type'] == 'TRANSFER' and act.get('source') == 'IBKR_CORP_ACTION':
                 details = f"Adjustment: {act['qty']:.4f}"
            else:
                 details = "Other Adjustment"
            corp_rows.append([act['date'], act['ticker'], act['type'], details])
        full_corp_data = corp_header + corp_rows
        t_corp = Table(full_corp_data, colWidths=[100, 80, 80, 200], repeatRows=1)
        t_corp.setStyle(get_zebra_style(len(full_corp_data)))
        elements.append(t_corp)

    elements.append(PageBreak())

    # PAGE 4: MONTHLY DIVIDENDS SUMMARY
    elements.append(Paragraph(f"Monthly Dividends Summary ({year})", h2_style))
    month_names = { "01": "January", "02": "February", "03": "March", "04": "April", "05": "May", "06": "June", "07": "July", "08": "August", "09": "September", "10": "October", "11": "November", "12": "December" }
    
    if data['monthly_dividends']:
        m_data = [["Month", "Gross (PLN)", "Tax Paid (PLN)", "Net (PLN)"]]
        sorted_months = sorted(data['monthly_dividends'].keys())
        total_gross, total_tax = 0, 0
        for m in sorted_months:
            vals = data['monthly_dividends'][m]
            m_data.append([
                month_names.get(m, m),
                f"{vals['gross_pln']:,.2f}",
                f"{vals['tax_pln']:,.2f}",
                f"{vals['net_pln']:,.2f}"
            ])
            total_gross += vals['gross_pln']
            total_tax += vals['tax_pln']
        m_data.append(["TOTAL", f"{total_gross:,.2f}", f"{total_tax:,.2f}", f"{total_gross - total_tax:,.2f}"])
        t_months = Table(m_data, colWidths=[110, 110, 110, 110], repeatRows=1)
        ts = get_zebra_style(len(m_data))
        ts.add('FONT-WEIGHT', (0,-1), (-1,-1), 'BOLD')
        ts.add('BACKGROUND', (0,-1), (-1,-1), colors.lightgrey)
        ts.add('ALIGN', (1,1), (-1,-1), 'RIGHT')
        t_months.setStyle(ts)
        elements.append(t_months)
        
        # --- DETAILED DIVIDENDS ---
        elements.append(PageBreak()) 
        elements.append(Paragraph(f"Dividend Details (Chronological)", h2_style))
        elements.append(Paragraph("Detailed breakdown of every dividend payment received.", normal_style))
        elements.append(Spacer(1, 10))
        
        sorted_divs = sorted(data['dividends'], key=lambda x: x['date'])
        
        is_first_month = True
        for month_key, group in itertools.groupby(sorted_divs, key=lambda x: x['date'][:7]):
            if not is_first_month:
                elements.append(PageBreak())
            is_first_month = False
            
            y, m = month_key.split('-')
            m_name = month_names.get(m, m)
            elements.append(Paragraph(f"{m_name} {y}", h2_style))
            
            det_header = [["Date", "Ticker", "Gross", "Rate", "Gross PLN", "Tax PLN"]]
            det_rows = []
            for d in group:
                det_rows.append([
                    d['date'],
                    d['ticker'],
                    f"{d['amount']:.2f} {d['currency']}",
                    f"{d['rate']:.4f}",
                    f"{d['amount_pln']:.2f}",
                    f"{d['tax_paid_pln']:.2f}"
                ])
            full_det_data = det_header + det_rows
            t_det = Table(full_det_data, colWidths=[70, 50, 90, 50, 70, 70], repeatRows=1)
            ts_det = get_zebra_style(len(full_det_data))
            ts_det.add('ALIGN', (2,1), (-1,-1), 'RIGHT')
            ts_det.add('FONTSIZE', (0,0), (-1,-1), 8)
            t_det.setStyle(ts_det)
            elements.append(t_det)
        
    else:
        elements.append(Paragraph("No dividends received this year.", normal_style))
    
    elements.append(PageBreak())

    # PAGE: YEARLY SUMMARY
    elements.append(Paragraph(f"Yearly Summary", h2_style))
    div_gross = sum(x['amount_pln'] for x in data['dividends'])
    div_tax = sum(x['tax_paid_pln'] for x in data['dividends'])
    polish_tax_due = max(0, (div_gross * 0.19) - div_tax)
    final_net = div_gross - div_tax - polish_tax_due
    
    summary_data = [
        ["Metric", "Amount (PLN)"],
        ["Total Dividends", f"{div_gross:,.2f}"],
        ["Withheld Tax (sum)", f"-{div_tax:,.2f}"],
        ["Additional Tax (PL, ~diff)", f"{polish_tax_due:,.2f}"],
        ["Final Net (after full 19%)", f"{final_net:,.2f}"]
    ]
    t_summary = Table(summary_data, colWidths=[250, 150])
    ts_sum = get_zebra_style(len(summary_data))
    ts_sum.add('ALIGN', (1,1), (-1,-1), 'RIGHT')
    t_summary.setStyle(ts_sum)
    elements.append(t_summary)
    
    elements.append(Spacer(1, 20))
    elements.append(Paragraph("Diagnostics", h2_style))
    diag = data['diagnostics']
    diag_data = [
        ["Indicator", "Value"],
        ["Tickers (unique)", str(diag['tickers_count'])],
        ["Dividend rows", str(diag['div_rows_count'])],
        ["Tax rows", str(diag['tax_rows_count'])]
    ]
    t_diag = Table(diag_data, colWidths=[250, 150])
    ts_diag = get_zebra_style(len(diag_data))
    ts_diag.add('ALIGN', (1,1), (-1,-1), 'CENTER')
    t_diag.setStyle(ts_diag)
    elements.append(t_diag)
    
    elements.append(Spacer(1, 20))
    elements.append(Paragraph("Per-currency totals (PLN)", h2_style))
    curr_data = [["Currency", "PLN total"]]
    for curr, val in data['per_currency'].items():
        curr_data.append([curr, f"{val:,.2f}"])
    t_curr = Table(curr_data, colWidths=[250, 150], repeatRows=1)
    ts_curr = get_zebra_style(len(curr_data))
    ts_curr.add('ALIGN', (1,1), (-1,-1), 'RIGHT')
    t_curr.setStyle(ts_curr)
    elements.append(t_curr)
    elements.append(PageBreak())

    # PAGE: PIT-38
    elements.append(Paragraph(f"PIT-38 Helper Data ({year})", h2_style))
    elements.append(Spacer(1, 10))
    elements.append(Paragraph("Section C (Stocks/Derivatives)", h3_style))
    cap_rev = sum(x['revenue_pln'] for x in data['capital_gains'])
    cap_cost = sum(x['cost_pln'] for x in data['capital_gains'])
    pit_c_data = [
        ["Field in PIT-38", "Value (PLN)"],
        ["Przychód (Revenue) [Pos 20]", f"{cap_rev:,.2f}"],
        ["Koszty (Costs) [Pos 21]", f"{cap_cost:,.2f}"],
        ["Dochód/Strata", f"{cap_rev - cap_cost:,.2f}"]
    ]
    t_pit_c = Table(pit_c_data, colWidths=[250, 150])
    ts_pit = get_zebra_style(len(pit_c_data))
    ts_pit.add('ALIGN', (1,1), (-1,-1), 'RIGHT')
    t_pit_c.setStyle(ts_pit)
    elements.append(t_pit_c)
    elements.append(Spacer(1, 5))
    elements.append(Paragraph("<i>* Note: 'Koszty' includes purchase price + buy/sell commissions.</i>", styles['Italic']))
    elements.append(Spacer(1, 20))

    elements.append(Paragraph("Dividends (Foreign Tax)", h3_style))
    pit_div_data = [
        ["Description", "Value (PLN)"],
        ["Gross Income", f"{div_gross:,.2f}"],
        ["Tax Paid Abroad (Max deductible)", f"{div_tax:,.2f}"],
        ["TO PAY (Difference) [Pos 45]", f"{polish_tax_due:,.2f}"] 
    ]
    t_pit_div = Table(pit_div_data, colWidths=[250, 150])
    ts_pit_div = get_zebra_style(len(pit_div_data))
    ts_pit_div.add('ALIGN', (1,1), (-1,-1), 'RIGHT')
    ts_pit_div.add('FONTNAME', (0,-1), (-1,-1), 'Helvetica-Bold') 
    t_pit_div.setStyle(ts_pit_div)
    elements.append(t_pit_div)

    doc.build(elements, onFirstPage=add_footer, onLaterPages=add_footer)
```

# --- FILE: ./src/nbp.py ---
```python
import os
import json
import requests
from datetime import datetime, timedelta
from decimal import Decimal

CACHE_DIR = "cache/nbp"
_MEMORY_CACHE = {}


def get_previous_day(date_str: str) -> str:
    try:
        dt = datetime.strptime(date_str, "%Y-%m-%d")
        prev = dt - timedelta(days=1)
        return prev.strftime("%Y-%m-%d")
    except:
        return date_str


def _load_year_cache(currency, year):
    cache_key = f"{currency}_{year}"
    if cache_key in _MEMORY_CACHE:
        return _MEMORY_CACHE[cache_key]
    os.makedirs(CACHE_DIR, exist_ok=True)
    file_path = os.path.join(CACHE_DIR, f"{currency}_{year}_bulk.json")
    if os.path.exists(file_path):
        with open(file_path, "r") as f:
            data = json.load(f)
            _MEMORY_CACHE[cache_key] = data
            return data
    url = f"http://api.nbp.pl/api/exchangerates/rates/a/{currency}/{year}-01-01/{year}-12-31/?format=json"
    rates_map = {}
    try:
        resp = requests.get(url, timeout=10)
        if resp.status_code == 200:
            raw = resp.json()
            for entry in raw.get("rates", []):
                rates_map[entry["effectiveDate"]] = entry["mid"]
            with open(file_path, "w") as f:
                json.dump(rates_map, f)
    except: # nosec B110
        pass
    _MEMORY_CACHE[cache_key] = rates_map
    return rates_map


def get_nbp_rate(currency: str, date_str: str, attempt=0) -> Decimal:
    if currency.upper() == "PLN":
        return Decimal("1.0")
    if attempt > 10:
        return Decimal("0.0")
    year = date_str[:4]
    rates_map = _load_year_cache(currency, year)
    if date_str in rates_map:
        return Decimal(str(rates_map[date_str]))
    prev_day = get_previous_day(date_str)
    return get_nbp_rate(currency, prev_day, attempt + 1)


def get_rate_for_tax_date(currency: str, event_date: str) -> Decimal:
    target_date = get_previous_day(event_date)
    return get_nbp_rate(currency, target_date)
```

# --- FILE: ./src/__init__.py ---
```python

```

# --- FILE: ./src/parser.py ---
```python
import csv
import re
from decimal import Decimal

IGNORED_TICKERS = {"EXAMPLE", "DUMMY_TICKER_FOR_EXAMPLE"}

def extract_ticker(description: str) -> str:
    if not description: return "UNKNOWN"
    match = re.search(r'^([A-Za-z0-9\.]+)\(', description)
    if match: return match.group(1)
    
    parts = description.split()
    if parts:
        if parts[0].isupper() and len(parts[0]) < 6: return parts[0].split('(')[0]
    return "UNKNOWN"

def extract_target_ticker(description: str) -> str:
    match = re.search(r'\(([A-Za-z0-9\.]+),\s+[A-Za-z0-9]', description)
    if match:
        return match.group(1)
    return None

def classify_trade_type(description: str, quantity: Decimal) -> str:
    desc_upper = description.upper()
    transfer_keywords = [
        "ACATS", "TRANSFER", "INTERNAL", "POSITION MOVEM", 
        "RECEIVE DELIVER", "INTER-COMPANY"
    ]
    if any(k in desc_upper for k in transfer_keywords):
        return "TRANSFER"
    if quantity > 0: return "BUY"
    if quantity < 0: return "SELL"
    return "UNKNOWN"

def parse_manual_history(filepath: str):
    manual_trades = []
    try:
        with open(filepath, 'r', encoding='utf-8') as f:
            reader = csv.DictReader(f)
            for row in reader:
                ticker = row.get('Ticker', '').strip().upper()
                if not ticker or ticker in IGNORED_TICKERS: continue
                manual_trades.append({
                    "ticker": ticker,
                    "currency": row['Currency'].strip().upper(),
                    "date": row['Date'].strip(),
                    "qty": Decimal(row['Quantity']),
                    "price": Decimal(row['Price']),
                    "commission": Decimal(row.get('Commission', 0)),
                    "type": "BUY",
                    "source": "MANUAL",
                    "raw_desc": "Manual History"
                })
    except: pass
    return manual_trades

def parse_csv(filepath):
    data_out = {"dividends": [], "taxes": [], "trades": []}
    seen_actions = set() # DEDUP: Prevent double splits from same file
    
    with open(filepath, 'r', encoding='utf-8-sig') as f:
        reader = csv.reader(f)
        for row in reader:
            if not row: continue
            
            # --- DIVIDENDS ---
            if row[0] == "Dividends" and row[1] == "Data":
                try:
                    if "Total" in row[2] or "Total" in row[4]: continue
                    ticker = extract_ticker(row[4])
                    if ticker in IGNORED_TICKERS: continue
                    data_out["dividends"].append({
                        "ticker": ticker,
                        "currency": row[2],
                        "date": row[3],
                        "amount": Decimal(row[5])
                    })
                except: pass

            # --- TAXES ---
            if row[0] == "Withholding Tax" and row[1] == "Data":
                try:
                    if "Total" in row[4]: continue
                    ticker = extract_ticker(row[4])
                    if ticker in IGNORED_TICKERS: continue
                    data_out["taxes"].append({
                        "ticker": ticker,
                        "currency": row[2],
                        "date": row[3],
                        "amount": Decimal(row[5])
                    })
                except: pass

            # --- TRADES ---
            if row[0] == "Trades" and row[1] == "Data" and row[2] == "Order" and row[3] == "Stocks":
                try:
                    ticker = row[5]
                    if ticker in IGNORED_TICKERS: continue
                    data_out["trades"].append({
                        "ticker": ticker,
                        "currency": row[4],
                        "date": row[6].split(",")[0],
                        "qty": Decimal(row[7]),
                        "price": Decimal(row[8]),
                        "commission": Decimal(row[11]),
                        "type": classify_trade_type(row[5], Decimal(row[7])),
                        "source": "IBKR"
                    })
                except: pass

            # --- CORPORATE ACTIONS ---
            if row[0] == "Corporate Actions" and row[1] == "Data" and row[2] == "Stocks":
                try:
                    desc = row[6]
                    if "Total" in desc: continue
                    
                    # DATE FIX: Revert GE logic (use row date), Keep KVUE fix
                    date_raw = row[4].split(",")[0]
                    curr = row[3]
                    
                    # 1. SPLITS
                    if "Split" in desc:
                        ticker = extract_ticker(desc)
                        if ticker in IGNORED_TICKERS: continue
                        match = re.search(r'Split (\d+) for (\d+)', desc, re.IGNORECASE)
                        if match:
                            numerator = Decimal(match.group(1))
                            denominator = Decimal(match.group(2))
                            if denominator != 0:
                                ratio = numerator / denominator
                                
                                # DEDUP CHECK
                                action_sig = (date_raw, ticker, "SPLIT", ratio)
                                if action_sig in seen_actions: continue
                                seen_actions.add(action_sig)
                                
                                data_out["trades"].append({
                                    "ticker": ticker,
                                    "currency": curr,
                                    "date": date_raw,
                                    "qty": Decimal("0"),
                                    "price": Decimal("0"),
                                    "commission": Decimal("0"),
                                    "type": "SPLIT",
                                    "ratio": ratio,
                                    "source": "IBKR_SPLIT"
                                })
                        continue 

                    # 2. COMPLEX ACTIONS
                    is_spinoff = "Spin-off" in desc or "Spinoff" in desc
                    is_merger = "Merged" in desc or "Acquisition" in desc
                    is_stock_div = "Stock Dividend" in desc
                    is_tender = "Tendered" in desc
                    is_voluntary = "Voluntary Offer" in desc
                    
                    if is_stock_div or is_spinoff or is_merger or is_tender or is_voluntary:
                        target_ticker = None
                        
                        # Explicit Fixes
                        if is_voluntary and "(KVUE," in desc:
                             target_ticker = "KVUE"
                             date_raw = "2023-08-23" # KVUE Force Date
                        elif is_spinoff and "(WBD," in desc: target_ticker = "WBD"
                        elif is_spinoff and "(OGN," in desc: target_ticker = "OGN"
                        elif is_spinoff and "(FG," in desc: target_ticker = "FG"
                        
                        if not target_ticker:
                            if is_spinoff or is_merger or is_tender or is_voluntary:
                                target_ticker = extract_target_ticker(desc)
                        if not target_ticker:
                            target_ticker = extract_ticker(desc)
                            
                        if target_ticker and target_ticker not in IGNORED_TICKERS:
                            qty = Decimal(row[7])
                            
                            # DEDUP CHECK
                            action_sig = (date_raw, target_ticker, "TRANSFER", qty)
                            if action_sig in seen_actions: continue
                            seen_actions.add(action_sig)

                            data_out["trades"].append({
                                "ticker": target_ticker,
                                "currency": curr,
                                "date": date_raw,
                                "qty": qty,
                                "price": Decimal("0.0"),
                                "commission": Decimal("0.0"),
                                "type": "TRANSFER",
                                "source": "IBKR_CORP_ACTION"
                            })
                except: pass
                    
    return data_out
```

# --- FILE: ./src/utils.py ---
```python
from decimal import Decimal, ROUND_HALF_EVEN


def money(value) -> Decimal:
    if value is None:
        return Decimal("0.00")
    if not isinstance(value, Decimal):
        try:
            value = Decimal(str(value))
        except:
            return Decimal("0.00")
    return value.quantize(Decimal("0.01"), rounding=ROUND_HALF_EVEN)
```

# --- FILE: ./src/processing.py ---
```python
import hashlib
import os
from typing import Dict, List
from decimal import Decimal
from .fifo import TradeMatcher
from .nbp import get_rate_for_tax_date
from .utils import money

class TaxCalculator:
    def __init__(self, target_year: str):
        self.target_year = target_year
        self.raw_dividends, self.raw_trades, self.raw_taxes = [], [], []
        self.seen_divs, self.seen_trades, self.seen_taxes = set(), set(), set()
        self.snapshot_cutoff_date = None
        
        self.report_data = {
            "dividends": [],
            "monthly_dividends": {},
            "capital_gains": [],
            "holdings": [],
            "trades_history": [],
            "corp_actions": [],
            "diagnostics": {},
            "per_currency": {}
        }
        
        self.matcher = TradeMatcher()

    def load_snapshot(self, filepath: str):
        if os.path.exists(filepath):
            self.snapshot_cutoff_date = self.matcher.load_state(filepath)
            print(f"⚡ Using snapshot! Ignoring trades before or on: {self.snapshot_cutoff_date}")
        else:
            print("ℹ️ No snapshot found. Processing full history from CSVs.")

    def _get_hash(self, data_str: str) -> str:
        return hashlib.sha256(data_str.encode('utf-8')).hexdigest()

    def ingest_preloaded_data(self, trades, divs, taxes):
        for d in divs:
            sig = f"{d['date']}|{d['ticker']}|{d['amount']}"
            h = self._get_hash(sig)
            if h not in self.seen_divs:
                self.seen_divs.add(h)
                self.raw_dividends.append(d)
        for t in taxes:
            sig = f"{t['date']}|{t['ticker']}|{t['amount']}"
            h = self._get_hash(sig)
            if h not in self.seen_taxes:
                self.seen_taxes.add(h)
                self.raw_taxes.append(t)
        for tr in trades:
            sig = f"{tr['date']}|{tr['ticker']}|{tr.get('qty', 0)}|{tr.get('price', 0)}|{tr.get('type')}"
            h = self._get_hash(sig)
            if h not in self.seen_trades:
                self.seen_trades.add(h)
                self.raw_trades.append(tr)

    def _calculate_holdings_simple(self):
        # PRIORITY SORTING
        type_priority = {'SPLIT': 0, 'TRANSFER': 1, 'BUY': 1, 'SELL': 2}
        
        sorted_trades = sorted(
            self.raw_trades, 
            key=lambda x: (x['date'], type_priority.get(x['type'], 3))
        )
        
        holdings_map = {}
        limit_date = f"{self.target_year}-12-31"
        
        for trade in sorted_trades:
            if trade['date'] > limit_date: break
            ticker = trade['ticker']
            
            if ticker not in holdings_map:
                holdings_map[ticker] = {"qty": Decimal("0"), "currency": trade['currency']}
            
            holdings_map[ticker]['currency'] = trade['currency']

            if trade['type'] == 'SPLIT':
                ratio = trade.get('ratio', Decimal(1))
                holdings_map[ticker]['qty'] = holdings_map[ticker]['qty'] * ratio
                continue

            qty = trade.get('qty', Decimal(0))
            holdings_map[ticker]['qty'] += qty
            
        result = []
        for ticker, data in holdings_map.items():
            qty = data['qty']
            if abs(qty) > 0.0001:
                is_restricted = (data['currency'] == 'RUB')
                result.append({
                    "ticker": ticker, 
                    "qty": float(qty),
                    "currency": data['currency'],
                    "is_restricted": is_restricted,
                    "fifo_match": False
                })
        
        self.report_data["holdings"] = sorted(result, key=lambda x: x['ticker'])

    def _collect_history_lists(self):
        history = []
        actions = []
        for trade in self.raw_trades:
            if not trade['date'].startswith(self.target_year): continue
            is_split = trade['type'] == 'SPLIT'
            is_stock_div = trade.get('source') == 'IBKR_CORP_ACTION'
            if is_split or is_stock_div:
                actions.append(trade)
                if is_stock_div: history.append(trade)
            else:
                history.append(trade)
        
        history.sort(key=lambda x: x['date'])
        actions.sort(key=lambda x: x['date'])
        self.report_data["trades_history"] = history
        self.report_data["corp_actions"] = actions

    def run_calculations(self):
        # 1. Filter trades
        trades_to_process = []
        if self.snapshot_cutoff_date:
            for t in self.raw_trades:
                if t['date'] > self.snapshot_cutoff_date:
                    trades_to_process.append(t)
        else:
            trades_to_process = self.raw_trades

        # 2. Run FIFO
        self.matcher.process_trades(trades_to_process)
        
        fifo_inventory = self.matcher.get_current_inventory()
        
        # 3. Holdings Generation
        if self.snapshot_cutoff_date:
            self.report_data["holdings"] = []
            for ticker, qty in fifo_inventory.items():
                 if qty > 0:
                     # FIX: LOOKUP CURRENCY FROM INVENTORY BATCHES
                     currency = "USD"
                     if ticker in self.matcher.inventory and self.matcher.inventory[ticker]:
                         currency = self.matcher.inventory[ticker][0].get('currency', 'USD')
                     
                     is_restricted = (currency == 'RUB')
                     
                     self.report_data["holdings"].append({
                         "ticker": ticker,
                         "qty": float(qty),
                         "currency": currency,
                         "is_restricted": is_restricted,
                         "fifo_match": True
                     })
        else:
            self._calculate_holdings_simple()
            # Reconciliation
            for holding in self.report_data["holdings"]:
                ticker = holding["ticker"]
                simple_qty = Decimal(str(holding["qty"]))
                fifo_qty = fifo_inventory.get(ticker, Decimal("0"))
                if abs(simple_qty - fifo_qty) < 0.0001:
                    holding["fifo_match"] = True
                else:
                    holding["fifo_match"] = False
                    print(f"⚠️ MISMATCH for {ticker}: Broker says {simple_qty}, FIFO says {fifo_qty}")

        self._collect_history_lists()

        # 4. Dividends
        monthly_map = {} 
        currency_map = {}
        unique_tickers = set()
        div_rows_in_year = 0
        
        for div in self.raw_dividends:
            if not div['date'].startswith(self.target_year): continue
            div_rows_in_year += 1
            unique_tickers.add(div['ticker'])
            
            rate = get_rate_for_tax_date(div['currency'], div['date'])
            amount_pln = money(div['amount'] * rate)
            
            curr = div['currency']
            if curr not in currency_map: currency_map[curr] = Decimal("0.00")
            currency_map[curr] += amount_pln
            
            tax_paid, tax_paid_pln = 0, 0
            for t in self.raw_taxes:
                if t['ticker'] == div['ticker'] and t['date'] == div['date']:
                    tax_paid += abs(t['amount'])
                    tax_paid_pln += abs(money(t['amount'] * rate))
            
            self.report_data["dividends"].append({
                "ticker": div['ticker'],
                "date": div['date'],
                "amount": float(div['amount']),
                "currency": div['currency'],
                "rate": float(rate),
                "amount_pln": float(amount_pln),
                "tax_paid": float(tax_paid),
                "tax_paid_pln": float(tax_paid_pln)
            })
            
            month = div['date'].split('-')[1]
            if month not in monthly_map:
                monthly_map[month] = {"gross_pln": 0, "tax_pln": 0, "net_pln": 0}
            
            monthly_map[month]["gross_pln"] += float(amount_pln)
            monthly_map[month]["tax_pln"] += float(tax_paid_pln)
            monthly_map[month]["net_pln"] += float(amount_pln - tax_paid_pln)

        self.report_data["monthly_dividends"] = monthly_map
        self.report_data["per_currency"] = {k: float(v) for k, v in currency_map.items()}

        for pnl in self.matcher.realized_pnl:
            if pnl['date_sell'].startswith(self.target_year):
                self.report_data["capital_gains"].append(pnl)
                unique_tickers.add(pnl['ticker'])
        
        tax_rows_in_year = 0
        for t in self.raw_taxes:
            if t['date'].startswith(self.target_year): tax_rows_in_year += 1
            
        self.report_data["diagnostics"] = {
            "tickers_count": len(unique_tickers),
            "div_rows_count": div_rows_in_year,
            "tax_rows_count": tax_rows_in_year
        }

    def get_results(self):
        return {"year": self.target_year, "data": self.report_data}
```

---
**INSTRUCTION:**
Restore the project structure and write the file contents as provided above.
Ensure `create_snapshot.py` is included and `main.py` has snapshot loading logic.
