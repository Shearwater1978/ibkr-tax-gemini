# src/excel_exporter.py

import pandas as pd
from typing import Dict, Any

def export_to_excel(sheets_data: Dict[str, pd.DataFrame], 
                    file_path: str, 
                    summary_data: Dict[str, Any], 
                    ticker_summary: Dict[str, Dict[str, str]]):
    """
    Exports data to a formatted Excel file with multiple tabs.

    Args:
        sheets_data: Dictionary where Key = Sheet Name, Value = DataFrame.
        file_path: The full path to save the .xlsx file.
        summary_data: Dictionary containing project summary metrics.
        ticker_summary: Dictionary containing P&L breakdown aggregated by ticker.
    """
    
    try:
        writer = pd.ExcelWriter(file_path, engine='openpyxl')

        # 1. Write General Summary Sheet (First Tab)
        summary_df = pd.DataFrame(list(summary_data.items()), columns=['Metric', 'Value'])
        summary_df.to_excel(writer, sheet_name='Summary', index=False)

        # 2. Write Ticker Summary Sheet
        if ticker_summary:
            df_ticker_summary = pd.DataFrame.from_dict(ticker_summary, orient='index').reset_index()
            df_ticker_summary = df_ticker_summary.rename(columns={'index': 'Ticker'})
            
            if not df_ticker_summary.empty:
                cols = ['Ticker', 'Total_P&L_PLN', 'Total_Proceeds_PLN', 'Total_Cost_PLN']
                df_ticker_summary = df_ticker_summary.reindex(columns=cols)
            
            df_ticker_summary.to_excel(writer, sheet_name='Ticker Summary', index=False)

        # 3. Write Separate Data Sheets (Sales, Dividends, Inventory)
        for sheet_name, df in sheets_data.items():
            if not df.empty:
                df.to_excel(writer, sheet_name=sheet_name, index=False)
                print(f"INFO: Added sheet '{sheet_name}' with {len(df)} rows.")
            else:
                print(f"INFO: Skipping empty sheet '{sheet_name}'.")

        writer.close()
        print(f"SUCCESS: Data exported to Excel at {file_path}")

    except Exception as e:
        print(f"ERROR: Failed to export Excel file at {file_path}. Reason: {e}")