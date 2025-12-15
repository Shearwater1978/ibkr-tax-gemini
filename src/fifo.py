# src/fifo.py

import json
from decimal import Decimal
from collections import deque
from typing import List, Dict, Any

from .nbp import get_rate_for_tax_date
from .utils import money

class TradeMatcher:
    def __init__(self):
        self.inventory = {} 
        self.realized_pnl = []

    def save_state(self, filepath: str, cutoff_date: str):
        # ... (Save code same as before, omitted for brevity) ...
        pass # Implement/Copy from previous if needed, but core logic is below

    def load_state(self, filepath: str) -> str:
        # ... (Load code same as before) ...
        return "1900-01-01"

    def process_trades(self, trades_list: List[Dict[str, Any]]):
        # Priority: Adjustments (Splits/Mergers) -> Buys -> Sells
        type_priority = {
            'STOCK_DIV': 0, 'MERGER': 0, 'SPLIT_ADD': 0, 
            'BUY': 1, 'TRANSFER': 1, 
            'SELL': 2
        }
        
        sorted_trades = sorted(
            trades_list, 
            key=lambda x: (x['date'], type_priority.get(x['type'], 3))
        )

        for trade in sorted_trades:
            ticker = trade['ticker']
            if ticker not in self.inventory:
                self.inventory[ticker] = deque()

            t_type = trade['type']
            qty = trade['qty']

            # --- 1. POSITIVE QUANTITY (ADD TO INVENTORY) ---
            if qty > 0:
                # Includes: BUY, STOCK_DIV (Split add), MERGER (New shares), SPINOFF
                if t_type == 'BUY' or t_type == 'TRANSFER':
                    self._process_buy(trade)
                else:
                    # Corporate Action Additions (Zero Cost usually)
                    # Force price to 0 if it's a Corp Action to avoid messing up cost basis
                    trade['price'] = Decimal(0)
                    self._process_buy(trade)

            # --- 2. NEGATIVE QUANTITY (REMOVE FROM INVENTORY) ---
            elif qty < 0:
                # Includes: SELL, MERGER (Old shares removal), LIQUIDATION
                if t_type == 'SELL':
                    self._process_sell(trade)
                else:
                    # Corporate Action Removals (Non-Taxable Transfer Out)
                    # We remove the shares but DO NOT record a Capital Gain/Loss for tax report
                    self._process_transfer_out(trade)

    def _process_buy(self, trade):
        if 'rate' in trade and trade['rate']:
            rate = trade['rate']
        else:
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
            "currency": trade['currency'],
            "source": trade.get('source', 'UNKNOWN')
        })

    def _process_sell(self, trade):
        self._consume_inventory(trade, is_taxable=True)

    def _process_transfer_out(self, trade):
        self._consume_inventory(trade, is_taxable=False)

    def _consume_inventory(self, trade, is_taxable):
        ticker = trade['ticker']
        qty_to_sell = abs(trade['qty'])
        
        if 'rate' in trade and trade['rate']:
            sell_rate = trade['rate']
        else:
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
                "sale_date": trade['date'],
                "date_sell": trade['date'],
                "quantity": float(abs(trade['qty'])),
                "sale_price": float(price),
                "sale_rate": float(sell_rate),
                "sale_amount": float(sell_revenue_pln), 
                "cost_basis": float(total_cost),        
                "profit_loss": float(profit_pln),       
                "currency": trade['currency'],
                "matched_buys": matched_buys
            })

    def get_realized_gains(self):
        return self.realized_pnl

    def get_current_inventory(self):
        inventory_list = []
        for ticker, batches in self.inventory.items():
            for batch in batches:
                inventory_list.append({
                    'ticker': ticker,
                    'buy_date': batch['date'],
                    'quantity': float(batch['qty']),
                    'cost_per_share': float(batch['price']),
                    'total_cost': float(batch['cost_pln']),
                    'currency': batch['currency']
                })
        return inventory_list