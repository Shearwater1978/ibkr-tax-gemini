# main.py

import argparse
from datetime import date
from collections import defaultdict
import sys
import pandas as pd 

# Import project modules
from src.data_collector import collect_all_trade_data
from src.excel_exporter import export_to_excel
from src.db_connector import DBConnector 
from src.processing import process_yearly_data 

# Attempt to import the PDF generator
try:
    from src.report_pdf import generate_pdf
    PDF_AVAILABLE = True
except ImportError:
    print("WARNING: src/report_pdf.py not found. PDF export is disabled.")
    PDF_AVAILABLE = False

def prepare_data_for_pdf(target_year, raw_trades, realized_gains, dividends, inventory):
    """
    Adapter: Converts processing results into a dictionary structure
    expected by src/report_pdf.py.
    """
    
    # --- RESTRICTED SECURITIES ---
    RESTRICTED_TICKERS = {
        "YNDX", "OZON", "VKCO", "FIVE", "FIXP", "HHR", "QIWI", "CIAN", "GEMC", "HMSG", "MDMG",
        "POLY", "PLZL", "GMKN", "NLMK", "CHMF", "MAGN", "RUAL", "ALRS", "PHOR", "GLTR",
        "GAZP", "LKOH", "NVTK", "ROSN", "TATN", "SNGS", "SNGSP",
        "SBER", "SBERP", "VTBR", "TCSG", "CBOM",
        "MTSS", "AFKS", "AFLT"
    }
    RESTRICTED_CURRENCIES = {"RUB"}

    # 1. Filter raw trades for the "History" section
    history_trades = []
    corp_actions = []
    
    # Sort by 'Date' key (PascalCase from DB)
    raw_trades.sort(key=lambda x: x['Date'])
    
    for t in raw_trades:
        # Check year. Key 'Date'
        if t['Date'].startswith(str(target_year)):
            event_type = t['EventType'] # Key 'EventType'
            
            # Split events. Only BUY and SELL go into history.
            if event_type in ['SPLIT', 'TRANSFER', 'MERGER', 'SPINOFF']:
                corp_actions.append({
                    'date': t['Date'], 
                    'ticker': t['Ticker'], 
                    'type': event_type,
                    'qty': float(t['Quantity']) if t['Quantity'] else 0,
                    'ratio': 1, 
                    'source': t.get('Description', 'DB')
                })
            
            elif event_type in ['BUY', 'SELL']: # <--- STRICT FILTER
                history_trades.append({
                    'date': t['Date'], 
                    'ticker': t['Ticker'], 
                    'type': event_type,
                    'qty': float(t['Quantity']) if t['Quantity'] else 0,
                    'price': float(t['Price']) if t['Price'] else 0,
                    'commission': float(t['Fee']) if t['Fee'] else 0,
                    'currency': t['Currency']
                })
            # DIVIDEND and TAX events do NOT belong here (they go to dividends)

    # 2. Aggregate dividends by month
    monthly_divs = defaultdict(lambda: {'gross_pln': 0.0, 'tax_pln': 0.0, 'net_pln': 0.0})
    formatted_divs = []
    
    for d in dividends:
        # dividends come from processing.py module, which usually returns snake_case
        date_str = d['ex_date']
        month_key = date_str[5:7] # MM
        
        gross = d['gross_amount_pln']
        tax = d.get('tax_withheld_pln', 0.0)
        net = gross - tax
        
        monthly_divs[month_key]['gross_pln'] += gross
        monthly_divs[month_key]['tax_pln'] += tax
        monthly_divs[month_key]['net_pln'] += net
        
        formatted_divs.append({
            'date': date_str,
            'ticker': d['ticker'],
            'amount': d.get('gross_amount_pln', 0) / d.get('rate', 1) if d.get('rate') else 0,
            'currency': d.get('currency', 'UNK'),
            'rate': d.get('rate', 1.0),
            'amount_pln': gross,
            'tax_paid_pln': tax
        })

    # 3. Capital growth (Capital Gains)
    cap_gains_data = []
    for g in realized_gains:
        # Similarly, realized_gains comes from processing.py in snake_case
        cap_gains_data.append({
            'revenue_pln': g['sale_amount'],
            'cost_pln': g['cost_basis']
        })

    # 4. End-of-period assets (Inventory)
    aggregated_holdings = defaultdict(float)
    restricted_status = {}

    for i in inventory:
        ticker = i['ticker']
        qty = i['quantity']
        aggregated_holdings[ticker] += qty
        
        if ticker in RESTRICTED_TICKERS or i.get('currency') in RESTRICTED_CURRENCIES:
            restricted_status[ticker] = True

    holdings_data = []
    for ticker, total_qty in aggregated_holdings.items():
        if abs(total_qty) > 0.000001:
            holdings_data.append({
                'ticker': ticker,
                'qty': total_qty,
                'is_restricted': restricted_status.get(ticker, False),
                'fifo_match': True 
            })
    
    holdings_data.sort(key=lambda x: x['ticker'])

    # 5. Diagnostics
    per_curr = defaultdict(float)
    for d in dividends:
        per_curr[d.get('currency', 'UNK')] += d['gross_amount_pln']

    pdf_payload = {
        'year': target_year,
        'data': {
            'holdings': holdings_data,
            'trades_history': history_trades,
            'corp_actions': corp_actions,
            'monthly_dividends': dict(monthly_divs),
            'dividends': formatted_divs,
            'capital_gains': cap_gains_data,
            'per_currency': dict(per_curr),
            'diagnostics': {
                'tickers_count': len(aggregated_holdings),
                'div_rows_count': len(dividends),
                'tax_rows_count': 0 
            }
        }
    }
    return pdf_payload

def main():
    parser = argparse.ArgumentParser(description="Tax Calculator IBKR")
    
    # Filtering arguments
    parser.add_argument('--target-year', type=int, default=date.today().year, 
                        help='Year for tax calculation (e.g., 2024).')
    parser.add_argument('--ticker', type=str, default=None, 
                        help='Filter by stock ticker (e.g., AAPL).')
    
    # Export arguments
    parser.add_argument('--export-excel', action='store_true', help='Export full history to Excel.')
    parser.add_argument('--export-pdf', action='store_true', help='Export tax report to PDF.')
    
    args = parser.parse_args()
    
    print(f"Launch tax calculation for {args.target_year} year...")
    
    # --- 1. Load data from DB ---
    raw_trades = []
    try:
        # Initialize connection (env vars will be loaded inside)
        with DBConnector() as db:
            db.initialize_schema() 
            raw_trades = db.get_trades_for_calculation(target_year=args.target_year, ticker=args.ticker)
            print(f"INFO: Loaded {len(raw_trades)} records from DB.")
    except Exception as e:
        # Print error but do not exit here since the context manager will close the connection
        print(f"CRITICAL ERROR: Could not connect or retrieve data. {e}")
        sys.exit(1)
        
    if not raw_trades:
        print("WARNING: No trades found. Please import the data first.")
        return

    # --- 2. Run FIFO logic ---
    print("INFO: Running FIFO matching and conversion using CB rates...")
    try:
        # process_yearly_data works with the original DB keys (PascalCase + TradeId)
        realized_gains, dividends, inventory = process_yearly_data(raw_trades, args.target_year)
    except Exception as e:
        print(f"CRITICAL ERROR during processing (processing): {e}")
        sys.exit(1)
    
    # Calculate totals
    total_pl = sum(r['profit_loss'] for r in realized_gains)
    total_dividends = sum(d['gross_amount_pln'] for d in dividends)
    
    print(f"\n--- Results for {args.target_year} year ---")
    print(f"Realized P&L (FIFO): {total_pl:.2f} PLN")
    print(f"Dividends (Gross): {total_dividends:.2f} PLN")
    print(f"Open positions (lots): {len(inventory)}")

    # Prepare data for export
    file_name_suffix = f"_{args.ticker}" if args.ticker else ""

    # --- 3. Export to Excel ---
    if args.export_excel:
        print("\nStarting export to Excel...")
        try:
            sheets_dict, ticker_summary = collect_all_trade_data(realized_gains, dividends, inventory)
            
            summary_metrics = {
                "Total P&L": f"{total_pl:.2f} PLN", 
                "Total Dividends (Gross)": f"{total_dividends:.2f} PLN",
                "Report Year": args.target_year,
                "Filtered Ticker": args.ticker if args.ticker else "All tickers",
                "Database Records": len(raw_trades)
            }
            output_path_xlsx = f"output/tax_report_{args.target_year}{file_name_suffix}.xlsx"
            export_to_excel(sheets_dict, output_path_xlsx, summary_metrics, ticker_summary)
            print(f"SUCCESS: Excel report saved to {output_path_xlsx}")
        except Exception as e:
            print(f"ERROR exporting to Excel: {e}")

    # --- 4. Export to PDF ---
    if args.export_pdf:
        if PDF_AVAILABLE:
            print("\nStarting export to PDF...")
            output_path_pdf = f"output/tax_report_{args.target_year}{file_name_suffix}.pdf"
            
            # Prepare data for PDF considering PascalCase keys
            try:
                pdf_data = prepare_data_for_pdf(args.target_year, raw_trades, realized_gains, dividends, inventory)
                generate_pdf(pdf_data, output_path_pdf)
                print(f"SUCCESS: PDF report saved to {output_path_pdf}")
            except Exception as e:
                print(f"ERROR: Could not create PDF: {e}")
        else:
            print("ERROR: PDF generation module (src/report_pdf.py) not found.")

    print("Processing complete.")

if __name__ == "__main__":
    main()