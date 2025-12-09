# src/excel_exporter.py

import pandas as pd
from typing import Dict, Any

def export_to_excel(data_frame: pd.DataFrame, file_path: str, summary_data: Dict[str, Any]):
    """
    Exports the combined financial data to a formatted Excel file, 
    including a summary sheet.

    Args:
        data_frame: The combined DataFrame containing all transaction history.
        file_path: The full path to save the .xlsx file.
        summary_data: Dictionary containing project summary metrics (e.g., total P&L).
    """
    
    try:
        # Create a Pandas Excel writer using openpyxl as the engine
        writer = pd.ExcelWriter(file_path, engine='openpyxl')

        # 1. Write Summary Sheet
        summary_df = pd.DataFrame(list(summary_data.items()), columns=['Metric', 'Value'])
        summary_df.to_excel(writer, sheet_name='Summary', index=False)

        # 2. Write Detailed Transactions Sheet
        data_frame.to_excel(writer, sheet_name='Transaction Details', index=False)

        # NOTE: Advanced formatting (like column width adjustment, colored rows, 
        # number formatting, and freezing panes) would be added here in a later task.
        
        # Save and close the Excel file
        writer.close()
        print(f"SUCCESS: Data exported to Excel at {file_path}")

    except Exception as e:
        print(f"ERROR: Failed to export Excel file at {file_path}. Reason: {e}")