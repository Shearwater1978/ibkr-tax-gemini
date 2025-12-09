# main.py

import argparse
from datetime import date
# Import the new modules
from src.data_collector import collect_all_trade_data
from src.excel_exporter import export_to_excel
from src.db_connector import DBConnector # Import the new database connector

def main():
    parser = argparse.ArgumentParser(description="IBKR Tax Calculator")
    
    # C2: Add Filtering Arguments
    parser.add_argument('--target-year', type=int, default=date.today().year, 
                        help='Year for which to calculate taxes (e.g., 2024).')
    parser.add_argument('--ticker', type=str, default=None, 
                        help='Filter results by a specific stock ticker (e.g., AAPL).')
    
    parser.add_argument('--export-excel', action='store_true', help='Export full transaction history to Excel.')
    
    args = parser.parse_args()
    
    print(f"Starting tax calculation for {args.target_year}...")
    
    
    # --- 1. Load Data from Encrypted Database (D1 Logic) ---
    
    raw_trades = []
    
    try:
        # Use the DBConnector as a context manager to handle connection/disconnection
        with DBConnector() as db:
            # C2: The filtering happens inside the SQL query
            raw_trades = db.get_trades_for_calculation(
                target_year=args.target_year, 
                ticker=args.ticker
            )
            print(f"INFO: Loaded {len(raw_trades)} transaction records.")

    except Exception as e:
        print(f"FATAL ERROR: Database connection failed. Cannot proceed with calculations. {e}")
        return # Exit if the database connection fails
        
    
    # --- 2. Run FIFO Matching Logic ---
    
    # NOTE: The actual P&L/Dividend calculation logic (e.g., run_fifo_matching(raw_trades)) 
    # would be executed here, producing the final calculated lists.
    
    # --- Dummy results (REPLACE WITH ACTUAL RESULTS FROM FIFO PROCESSOR) ---
    # These structures should represent the final calculated P&L and dividends
    realized_gains = [
        {'sale_date': '2024-05-10', 'ticker': 'AAPL', 'sale_amount': 2500.00, 'cost_basis': 2000.00, 'profit_loss': 500.00, 'buy_date': '2023-01-05'},
        {'sale_date': '2024-11-20', 'ticker': 'MSFT', 'sale_amount': 300.00, 'cost_basis': 350.00, 'profit_loss': -50.00, 'buy_date': '2024-06-15'},
    ]
    dividends = [
        {'ex_date': '2024-03-01', 'ticker': 'AAPL', 'gross_amount_pln': 50.00, 'tax_withheld_pln': 5.00},
        {'ex_date': '2024-09-15', 'ticker': 'MSFT', 'gross_amount_pln': 75.00, 'tax_withheld_pln': 7.50},
    ]
    inventory = [
        {'buy_date': '2025-01-01', 'ticker': 'GOOGL', 'quantity': 10, 'cost_per_share': 150.00, 'total_cost': 1500.00},
        {'buy_date': '2024-12-01', 'ticker': 'MSFT', 'quantity': 20, 'cost_per_share': 400.00, 'total_cost': 8000.00},
    ]
    
    # Total calculations for summary report
    total_pl = sum(r['profit_loss'] for r in realized_gains)
    total_dividends = sum(d['gross_amount_pln'] for d in dividends)
    
    # Print results to console (optional)
    print(f"\n--- Tax Results for {args.target_year} ---")
    print(f"Total Loaded Records: {len(raw_trades)}")
    print(f"Total P&L: {total_pl:.2f} PLN")
    print(f"Total Dividends (Gross): {total_dividends:.2f} PLN")

    # --- 3. Export Logic (A1, A2) ---
    if args.export_excel:
        print("\nStarting Excel export...")
        
        # A1: Collect Data 
        combined_df = collect_all_trade_data(realized_gains, dividends, inventory)
        
        # A2: Export to File (C2: dynamic file naming)
        summary_metrics = {
            "Total P&L": f"{total_pl:.2f} PLN", 
            "Total Dividends (Gross)": f"{total_dividends:.2f} PLN",
            "Report Year": args.target_year,
            "Filtered Ticker": args.ticker if args.ticker else "All Tickers"
        }
        
        file_name_suffix = f"_{args.ticker}" if args.ticker else ""
        export_to_excel(
            combined_df, 
            f"output/tax_report_{args.target_year}{file_name_suffix}.xlsx", 
            summary_metrics
        )
        
    print("Processing complete.")

if __name__ == "__main__":
    main()