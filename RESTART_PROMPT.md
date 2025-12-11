# RESTART PROMPT (MASTER INDEX - v2.1.0)

**Role:** Expert Python Developer (Finance/Tax domain).
**Project:** IBKR Tax Calculator for Poland (PIT-38).
**Version:** v2.1.0 (Stable).
**Architecture:** Modular (Source Code and Tests are split into separate backup files).

## ⚠️ RESTORATION INSTRUCTIONS
This project is split into 3 parts due to context limits.
1.  **ROOT FILES:** Restore them from **this file** (see below).
2.  **SOURCE CODE:** Ask the user for `RESTART_SRC.md` to restore `src/` directory.
3.  **TEST SUITE:** Ask the user for `RESTART_TESTS.md` to restore `tests/` directory.

## 1. Complete File Structure
```text
ROOT/
    .env                       # Secrets (User must create)
    .gitignore                 # Git ignore rules
    main.py                    # Entry point (contained here)
    requirements.txt           # Dependencies (contained here)
    RESTART_SRC.md             # Backup Part 1
    RESTART_TESTS.md           # Backup Part 2
    src/                       # See RESTART_SRC.md
        db_connector.py
        parser.py
        nbp.py
        fifo.py
        processing.py
        report_pdf.py
        excel_exporter.py
        data_collector.py
        utils.py
    tests/                     # See RESTART_TESTS.md
        test_*.py
```

## 2. ROOT FILES (Restore Immediately)

# --- FILE: main.py ---
```python
import argparse
import sys
from src.db_connector import DBConnector
from src.processing import process_yearly_data
from src.data_collector import collect_all_trade_data
from src.excel_exporter import export_to_excel

try:
    from src.report_pdf import generate_pdf
    PDF_AVAILABLE = True
except ImportError:
    print('WARNING: src/report_pdf.py not found. PDF export disabled.')
    PDF_AVAILABLE = False

def prepare_data_for_pdf(target_year, raw_trades, realized_gains, dividends, inventory):
    monthly_divs = {}
    for d in dividends:
        m = d['date'][5:7]
        if m not in monthly_divs: monthly_divs[m] = {'gross_pln': 0.0, 'tax_pln': 0.0, 'net_pln': 0.0}
        monthly_divs[m]['gross_pln'] += d['gross_amount_pln']
        monthly_divs[m]['tax_pln'] += d.get('tax_withheld_pln', 0)
        monthly_divs[m]['net_pln'] += (d['gross_amount_pln'] - d.get('tax_withheld_pln', 0))
    
    return {
        'year': target_year,
        'data': {
            'holdings': inventory,
            'trades_history': [t for t in raw_trades if t['Date'].startswith(str(target_year))],
            'monthly_dividends': monthly_divs,
            'capital_gains': realized_gains,
            'dividends': dividends
        }
    }

def main():
    parser = argparse.ArgumentParser()
    parser.add_argument('--target-year', type=int, required=True)
    parser.add_argument('--ticker', type=str)
    parser.add_argument('--export-excel', action='store_true')
    parser.add_argument('--export-pdf', action='store_true')
    args = parser.parse_args()

    print(f'Starting calculation for {args.target_year}...')
    with DBConnector() as db:
        raw_trades = db.get_trades_for_calculation(args.target_year, args.ticker)
        if not raw_trades:
            print('No records found. Run parser first.')
            return
        
        realized, divs, inv = process_yearly_data(raw_trades, args.target_year)
        
        print(f'Realized P&L: {sum(x["profit_loss"] for x in realized):.2f} PLN')
        print(f'Dividends: {sum(x["gross_amount_pln"] for x in divs):.2f} PLN')

        if args.export_excel:
            sheets, summary, ticker_sum = collect_all_trade_data(realized, divs, inv)
            export_to_excel(sheets, f'output/tax_report_{args.target_year}.xlsx', summary, ticker_sum)
            print('Excel exported.')

        if args.export_pdf and PDF_AVAILABLE:
            pdf_data = prepare_data_for_pdf(args.target_year, raw_trades, realized, divs, inv)
            generate_pdf(pdf_data, f'output/tax_report_{args.target_year}.pdf')
            print('PDF exported.')

if __name__ == '__main__':
    main()
```

# --- FILE: requirements.txt ---
```text
pandas
requests
reportlab
openpyxl
python-decouple
pysqlcipher3
pytest
```

# --- FILE: .gitignore ---
```text
__pycache__/
*.pyc
.env
data/*.db
data/*.db.enc
data/*.csv
output/*
.DS_Store
```
