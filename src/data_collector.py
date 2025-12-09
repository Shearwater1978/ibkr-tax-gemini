# src/data_collector.py

import pandas as pd
from typing import List, Dict, Any
from datetime import date
from .date_utils import calculate_holding_period_days, determine_holding_category 

# NOTE: In a real project, we would import the database accessor here (e.g., from src.database import get_all_records)

def collect_all_trade_data(realized_gains: List[Dict[str, Any]], 
                           dividends: List[Dict[str, Any]], 
                           inventory: List[Dict[str, Any]]) -> pd.DataFrame:
    """
    Collects realized gains, dividends, and current inventory into a single 
    structured DataFrame for comprehensive reporting, now including holding period.

    Args:
        realized_gains: List of realized P&L records (FIFO matched sales).
        dividends: List of dividend records.
        inventory: List of current open buy lots (unmatched inventory).

    Returns:
        A pandas DataFrame combining all three datasets with an added 
        'TransactionType' and 'Holding_Category' column.
    """
    
    # 1. Process Realized Gains (Sales P&L)
    df_realized = pd.DataFrame(realized_gains)
    if not df_realized.empty:
        df_realized['TransactionType'] = 'Sale_P&L'
        
        # Calculate Holding Period for Sales (B1)
        df_realized['Holding_Days'] = df_realized.apply(
            lambda row: calculate_holding_period_days(
                row.get('buy_date'), row.get('sale_date')
            ), 
            axis=1
        )
        df_realized['Holding_Category'] = df_realized['Holding_Days'].apply(determine_holding_category)

        # Rename and select columns
        df_realized = df_realized.rename(columns={
            'sale_date': 'Date',
            'ticker': 'Ticker',
            'sale_amount': 'Proceeds_PLN',
            'cost_basis': 'Cost_PLN',
            'profit_loss': 'P&L_PLN'
        })
        df_realized = df_realized[[
            'Date', 'Ticker', 'Proceeds_PLN', 'Cost_PLN', 'P&L_PLN', 
            'Holding_Days', 'Holding_Category', 'TransactionType'
        ]]
    
    # 2. Process Dividends
    df_dividends = pd.DataFrame(dividends)
    if not df_dividends.empty:
        df_dividends['TransactionType'] = 'Dividend'
        df_dividends['Holding_Days'] = 'N/A' 
        df_dividends['Holding_Category'] = 'N/A' 
        
        # Rename and select columns
        df_dividends = df_dividends.rename(columns={
            'ex_date': 'Date',
            'ticker': 'Ticker',
            'gross_amount_pln': 'Gross_PLN',
            'tax_withheld_pln': 'Tax_PLN'
        })
        df_dividends = df_dividends[['Date', 'Ticker', 'Gross_PLN', 'Tax_PLN', 'Holding_Days', 'Holding_Category', 'TransactionType']]
    
    # 3. Process Inventory (Open Positions)
    df_inventory = pd.DataFrame(inventory)
    if not df_inventory.empty:
        df_inventory['TransactionType'] = 'Inventory'
        
        # Calculate current holding days for open positions (B1)
        current_date = date.today().strftime('%Y-%m-%d')
        df_inventory['Holding_Days'] = df_inventory.apply(
            lambda row: calculate_holding_period_days(row.get('buy_date'), current_date), 
            axis=1
        )
        df_inventory['Holding_Category'] = 'Open' 

        # Rename and select columns
        df_inventory = df_inventory.rename(columns={
            'buy_date': 'Date',
            'ticker': 'Ticker',
            'quantity': 'Quantity',
            'cost_per_share': 'Cost_per_Share_PLN',
            'total_cost': 'Total_Cost_PLN'
        })
        df_inventory = df_inventory[['Date', 'Ticker', 'Quantity', 'Total_Cost_PLN', 'Holding_Days', 'Holding_Category', 'TransactionType']]


    # Combine all DataFrames (fills missing columns with NaN)
    combined_df = pd.concat([df_realized, df_dividends, df_inventory], ignore_index=True)
    
    # Sort the final result by date for chronological viewing
    if not combined_df.empty:
        combined_df['Date'] = pd.to_datetime(combined_df['Date'])
        combined_df = combined_df.sort_values(by='Date').reset_index(drop=True)
        combined_df['Date'] = combined_df['Date'].dt.strftime('%Y-%m-%d')
        
    return combined_df