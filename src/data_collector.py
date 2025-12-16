# src/data_collector.py

import pandas as pd
from typing import List, Dict, Any, Tuple
from datetime import datetime

# Helper to categorize holding period
def calculate_holding_period(buy_date_str: str, sell_date_str: str) -> str:
    try:
        if not buy_date_str or not sell_date_str:
            return "N/A"
        fmt = '%Y-%m-%d'
        d1 = datetime.strptime(buy_date_str, fmt).date()
        d2 = datetime.strptime(sell_date_str, fmt).date()
        days = (d2 - d1).days + 1
        return "Long-Term" if days > 365 else "Short-Term"
    except Exception:
        return "Error"

def calculate_days(buy_date_str: str, sell_date_str: str) -> int:
    try:
        fmt = '%Y-%m-%d'
        d1 = datetime.strptime(buy_date_str, fmt).date()
        d2 = datetime.strptime(sell_date_str, fmt).date()
        return (d2 - d1).days + 1
    except:
        return 0

def calculate_ticker_summary(flat_gains: List[Dict[str, Any]]) -> Dict[str, Dict[str, str]]:
    summary = {}
    for record in flat_gains:
        ticker = record.get('Ticker')
        pl_pln = record.get('P&L_PLN', 0.0)
        proceeds = record.get('Proceeds_PLN', 0.0)
        cost = record.get('Cost_PLN', 0.0)
        
        if ticker not in summary:
            summary[ticker] = {'Total_P&L_PLN': 0.0, 'Total_Proceeds_PLN': 0.0, 'Total_Cost_PLN': 0.0}
        
        summary[ticker]['Total_P&L_PLN'] += pl_pln
        summary[ticker]['Total_Proceeds_PLN'] += proceeds
        summary[ticker]['Total_Cost_PLN'] += cost

    formatted = {}
    for t, m in summary.items():
        formatted[t] = {k: f"{v:.2f}" for k, v in m.items()}
    return formatted

def collect_all_trade_data(realized_gains: List[Dict[str, Any]], 
                           dividends: List[Dict[str, Any]], 
                           inventory: List[Dict[str, Any]]) -> Tuple[Dict[str, pd.DataFrame], Dict[str, Dict[str, str]]]:
    
    # --- 1. Sales P&L ---
    flat_records = []
    for sale in realized_gains:
        matched_buys = sale.get('matched_buys', [])
        sale_date = sale.get('date_sell') or sale.get('sale_date')
        ticker = sale.get('ticker')
        sale_price = float(sale.get('sale_price', 0))
        sale_rate = float(sale.get('sale_rate', 1.0))
        
        if not matched_buys:
            flat_records.append({
                'Date': sale_date,
                'Ticker': ticker,
                'TransactionType': 'Sale_P&L',
                'Buy_Date': 'MISSING',
                'Quantity': float(sale.get('quantity', 0)),
                'Proceeds_PLN': float(sale.get('sale_amount', 0)),
                'Cost_PLN': 0.0,
                'P&L_PLN': float(sale.get('profit_loss', 0)),
                'Holding_Days': 0,
                'Holding_Category': 'N/A'
            })
            continue

        for buy in matched_buys:
            buy_date = buy.get('date')
            qty = float(buy.get('qty', 0))
            cost_pln = float(buy.get('cost_pln', 0))
            lot_proceeds = qty * sale_price * sale_rate
            lot_pnl = lot_proceeds - cost_pln
            
            flat_records.append({
                'Date': sale_date,
                'Ticker': ticker,
                'TransactionType': 'Sale_P&L',
                'Buy_Date': buy_date,
                'Quantity': qty,
                'Proceeds_PLN': lot_proceeds,
                'Cost_PLN': cost_pln,
                'P&L_PLN': lot_pnl,
                'Holding_Days': calculate_days(buy_date, sale_date),
                'Holding_Category': calculate_holding_period(buy_date, sale_date)
            })

    df_realized = pd.DataFrame(flat_records)
    if not df_realized.empty:
        df_realized['Date'] = pd.to_datetime(df_realized['Date'], errors='coerce')
        df_realized = df_realized.sort_values(by=['Date', 'Ticker']).reset_index(drop=True)
        df_realized['Date'] = df_realized['Date'].dt.strftime('%Y-%m-%d')
        cols = ['Date', 'Ticker', 'TransactionType', 'Buy_Date', 'Quantity', 'Proceeds_PLN', 'Cost_PLN', 'P&L_PLN', 'Holding_Days', 'Holding_Category']
        df_realized = df_realized[[c for c in cols if c in df_realized.columns]]

    # --- 2. Dividends (FIXED: Show Tax in Cost Column) ---
    div_records = []
    for d in dividends:
        gross = d.get('gross_amount_pln', 0.0)
        tax = d.get('tax_withheld_pln', 0.0)
        
        div_records.append({
            'Date': d.get('ex_date'),
            'Ticker': d.get('ticker'),
            'TransactionType': 'Dividend',
            'Quantity': 0,
            'Gross_PLN': gross,
            'Tax_PLN': tax,
            # Mapping for Unified Excel View:
            'Proceeds_PLN': gross, # Revenue = Dirty Dividend
            'Cost_PLN': tax,       # Cost = Tax Paid
            'P&L_PLN': gross - tax, # Profit = Net dividend
            'Currency': d.get('currency'),
            'Rate': d.get('rate')
        })
    df_dividends = pd.DataFrame(div_records)
    if not df_dividends.empty:
        df_dividends['Date'] = pd.to_datetime(df_dividends['Date'], errors='coerce')
        df_dividends = df_dividends.sort_values(by=['Date', 'Ticker']).reset_index(drop=True)
        df_dividends['Date'] = df_dividends['Date'].dt.strftime('%Y-%m-%d')

    # --- 3. Inventory ---
    inv_records = []
    today_str = datetime.today().strftime('%Y-%m-%d')
    for i in inventory:
        buy_date = i.get('buy_date')
        inv_records.append({
            'Buy_Date': buy_date,
            'Ticker': i.get('ticker'),
            'TransactionType': 'Inventory',
            'Quantity': i.get('quantity'),
            'Cost_per_Share': i.get('cost_per_share'),
            'Total_Cost_PLN': i.get('total_cost'),
            'Holding_Days': calculate_days(buy_date, today_str)
        })
    df_inventory = pd.DataFrame(inv_records)
    if not df_inventory.empty:
        df_inventory = df_inventory.sort_values(by=['Ticker', 'Buy_Date'])

    sheets_collection = {
        'Sales P&L': df_realized,
        'Dividends': df_dividends,
        'Open Positions': df_inventory
    }

    ticker_summary = calculate_ticker_summary(flat_records)
    return sheets_collection, ticker_summary