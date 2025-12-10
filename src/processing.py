# src/processing.py

from typing import List, Dict, Any, Tuple
from decimal import Decimal
from collections import defaultdict
import logging

# Project imports
from src.nbp import get_nbp_rate
from src.fifo import TradeMatcher 

def process_yearly_data(raw_trades: List[Dict[str, Any]], target_year: int) -> Tuple[List[Dict], List[Dict], List[Dict]]:
    """
    Orchestrates the processing of raw database records into calculated tax reports.
    Adapts SQLCipher data to the TradeMatcher input format.
    Matches Withholding Taxes to Dividends.
    """
    
    matcher = TradeMatcher()
    
    dividends = []
    fifo_input_list = []
    
    print(f"INFO: Processing {len(raw_trades)} trades via FIFO engine...")

    # --- 0. Pre-process Taxes (Link TAX rows to Dividends) ---
    # IBKR reports store Withholding Tax as separate rows.
    # We map (Date, Ticker) -> Total Tax Amount (absolute value)
    tax_map = defaultdict(Decimal)
    
    for t in raw_trades:
        if t['EventType'] == 'TAX':
            # Tax amount is usually negative in DB, we need positive magnitude
            amt = Decimal(str(t['Amount'])) if t['Amount'] else Decimal(0)
            key = (t['Date'], t['Ticker'])
            tax_map[key] += abs(amt)

    # Sort trades by date and ID
    sorted_trades = sorted(raw_trades, key=lambda x: (x['Date'], x['TradeId']))

    for trade in sorted_trades:
        # Extract basic fields from DB
        date_str = trade['Date']
        ticker = trade['Ticker']
        event_type = trade['EventType'] # BUY, SELL, DIVIDEND, SPLIT, TAX
        currency = trade['Currency']
        
        # Convert DB types to Decimal
        quantity = Decimal(str(trade['Quantity'])) if trade['Quantity'] else Decimal(0)
        price = Decimal(str(trade['Price'])) if trade['Price'] else Decimal(0)
        amount_currency = Decimal(str(trade['Amount'])) if trade['Amount'] else Decimal(0)
        fee = Decimal(str(trade['Fee'])) if trade['Fee'] else Decimal(0)
        
        description = trade.get('Description', '')

        # --- 1. Get NBP Rate ---
        rate = Decimal("1.0")
        if currency != 'PLN':
            try:
                rate = get_nbp_rate(currency, date_str)
            except Exception as e:
                print(f"WARNING: Could not fetch NBP rate for {currency} on {date_str}. Using 1.0. Error: {e}")
                rate = Decimal("1.0")

        # --- 2. Build Logic ---
        
        if event_type == 'DIVIDEND':
            # Calculate Dividend in PLN
            gross_pln = amount_currency * rate
            
            # Look up the tax for this specific dividend (Same Date, Same Ticker)
            tax_in_original_currency = tax_map.get((date_str, ticker), Decimal(0))
            tax_pln = tax_in_original_currency * rate
            
            div_record = {
                'ex_date': date_str,
                'ticker': ticker,
                'gross_amount_pln': float(gross_pln),
                'tax_withheld_pln': float(tax_pln), # <--- NOW FILLED
                'currency': currency,
                'rate': float(rate)
            }
            if date_str.startswith(str(target_year)):
                dividends.append(div_record)
        
        elif event_type == 'TAX':
            # Skip processing TAX rows here, as they are handled via tax_map above
            pass
            
        else:
            # Handle Trades (BUY, SELL, SPLIT, TRANSFER)
            matcher_type = event_type
            
            trade_record = {
                'type': matcher_type,
                'date': date_str,
                'ticker': ticker,
                'qty': quantity,
                'price': price,
                'commission': fee,
                'currency': currency,
                'rate': rate,
                'source': 'DB'
            }
            
            if matcher_type == 'SPLIT':
                trade_record['ratio'] = Decimal("1") 

            fifo_input_list.append(trade_record)

    # --- 3. Execute FIFO Logic ---
    matcher.process_trades(fifo_input_list)

    # --- 4. Extract Results ---
    all_realized = matcher.get_realized_gains()
    
    target_realized = [
        r for r in all_realized 
        if r['sale_date'].startswith(str(target_year))
    ]
    
    inventory = matcher.get_current_inventory()
    
    return target_realized, dividends, inventory