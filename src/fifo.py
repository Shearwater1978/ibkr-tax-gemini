from decimal import Decimal
from collections import deque
from .nbp import get_rate_for_tax_date
from .utils import money

class TradeMatcher:
    def __init__(self):
        self.inventory = {} 
        self.realized_pnl = []

    def process_trades(self, trades_list):
        # Sort chronologically
        sorted_trades = sorted(trades_list, key=lambda x: x['date'])

        for trade in sorted_trades:
            if trade['type'] == 'TRANSFER': continue
            
            ticker = trade['ticker']
            if ticker not in self.inventory:
                self.inventory[ticker] = deque()

            # NEW LOGIC: Dispatch based on TYPE, not QTY
            # This is crucial for SPLITS which might not have a 'qty' field.
            if trade['type'] == 'BUY':
                self._process_buy(trade)
            elif trade['type'] == 'SELL':
                self._process_sell(trade)
            elif trade['type'] == 'SPLIT':
                self._process_split(trade)

    def _process_buy(self, trade):
        rate = get_rate_for_tax_date(trade['currency'], trade['date'])
        cost_pln = money((trade['price'] * trade['qty'] * rate) + (abs(trade['commission']) * rate))
        
        self.inventory[trade['ticker']].append({
            "date": trade['date'],
            "qty": trade['qty'],
            "price": trade['price'],
            "rate": rate,
            "cost_pln": cost_pln,
            "source": trade.get('source', 'UNKNOWN')
        })

    def _process_sell(self, trade):
        ticker = trade['ticker']
        qty_to_sell = abs(trade['qty'])
        
        sell_rate = get_rate_for_tax_date(trade['currency'], trade['date'])
        sell_revenue_pln = money(trade['price'] * qty_to_sell * sell_rate)
        sell_comm_pln = money(abs(trade['commission']) * sell_rate)
        
        cost_basis_pln = Decimal("0.00")
        matched_buys = []

        while qty_to_sell > 0:
            if not self.inventory[ticker]: break # Short sale or missing history

            buy_batch = self.inventory[ticker][0]
            
            if buy_batch['qty'] <= qty_to_sell:
                # Consumed full batch
                cost_basis_pln += buy_batch['cost_pln']
                qty_to_sell -= buy_batch['qty']
                matched_buys.append(buy_batch.copy())
                self.inventory[ticker].popleft()
            else:
                # Consumed partial batch
                ratio = qty_to_sell / buy_batch['qty']
                part_cost = money(buy_batch['cost_pln'] * ratio)
                
                partial_record = buy_batch.copy()
                partial_record['qty'] = qty_to_sell
                partial_record['cost_pln'] = part_cost
                matched_buys.append(partial_record)

                cost_basis_pln += part_cost
                
                # Adjust remaining batch
                buy_batch['qty'] -= qty_to_sell
                buy_batch['cost_pln'] -= part_cost
                qty_to_sell = 0

        total_cost = cost_basis_pln + sell_comm_pln
        profit_pln = sell_revenue_pln - total_cost
        
        self.realized_pnl.append({
            "ticker": ticker,
            "date_sell": trade['date'],
            "revenue_pln": float(sell_revenue_pln),
            "cost_pln": float(total_cost),
            "profit_pln": float(profit_pln),
            "matched_buys": matched_buys
        })

    def _process_split(self, trade):
        """
        Applies a stock split to the EXISTING inventory.
        Does NOT trigger a taxable event.
        Adjusts Qty and Cost Per Share, but Total Cost (in PLN) remains same.
        """
        ticker = trade['ticker']
        ratio = trade.get('ratio', Decimal("1"))
        
        if ticker not in self.inventory:
            return

        # Iterate over all batches in the deque and update them in-place
        new_deque = deque()
        while self.inventory[ticker]:
            batch = self.inventory[ticker].popleft()
            
            # Update Quantity: Old Qty * Ratio
            # Example: 10 shares * 4 (4:1 split) = 40 shares
            new_qty = batch['qty'] * ratio
            
            # Update Price: Old Price / Ratio (for reference)
            new_price = batch['price'] / ratio
            
            # Total Cost (cost_pln) remains UNCHANGED! 
            
            batch['qty'] = new_qty
            batch['price'] = new_price
            new_deque.append(batch)
            
        self.inventory[ticker] = new_deque
