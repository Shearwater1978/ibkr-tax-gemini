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
