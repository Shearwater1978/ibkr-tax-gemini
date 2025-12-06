from decimal import Decimal
from collections import deque
from .nbp import get_rate_for_tax_date
from .utils import money

class TradeMatcher:
    def __init__(self):
        self.inventory = {} 
        self.realized_pnl = []

    def process_trades(self, trades_list):
        # PRIORITY SORTING:
        # On the same date, we want to process INCOMING items before OUTGOING.
        # Priority (lower is first): SPLIT (0) -> TRANSFER/BUY (1) -> SELL (2)
        
        type_priority = {
            'SPLIT': 0,
            'TRANSFER': 1,
            'BUY': 1,
            'SELL': 2
        }
        
        # Sort by Date first, then by Priority
        sorted_trades = sorted(
            trades_list, 
            key=lambda x: (x['date'], type_priority.get(x['type'], 3))
        )

        for trade in sorted_trades:
            ticker = trade['ticker']
            if ticker not in self.inventory:
                self.inventory[ticker] = deque()

            if trade['type'] == 'BUY':
                self._process_buy(trade)
            elif trade['type'] == 'SELL':
                self._process_sell(trade)
            elif trade['type'] == 'SPLIT':
                self._process_split(trade)
            elif trade['type'] == 'TRANSFER':
                if trade['qty'] > 0:
                    self._process_buy(trade)
                else:
                    self._process_transfer_out(trade)

    def _process_buy(self, trade):
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
            "source": trade.get('source', 'UNKNOWN')
        })

    def _process_sell(self, trade):
        self._consume_inventory(trade, is_taxable=True)

    def _process_transfer_out(self, trade):
        self._consume_inventory(trade, is_taxable=False)

    def _consume_inventory(self, trade, is_taxable):
        ticker = trade['ticker']
        qty_to_sell = abs(trade['qty'])
        
        sell_rate = get_rate_for_tax_date(trade['currency'], trade['date'])
        price = trade.get('price', Decimal(0))
        comm = trade.get('commission', Decimal(0))
        
        sell_revenue_pln = money(price * qty_to_sell * sell_rate)
        
        cost_basis_pln = Decimal("0.00")
        matched_buys = []

        while qty_to_sell > 0:
            if not self.inventory[ticker]: 
                # Safety break if selling more than we have
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
                "date_sell": trade['date'],
                "revenue_pln": float(sell_revenue_pln),
                "cost_pln": float(total_cost),
                "profit_pln": float(profit_pln),
                "matched_buys": matched_buys
            })

    def _process_split(self, trade):
        ticker = trade['ticker']
        ratio = trade.get('ratio', Decimal("1"))
        if ticker not in self.inventory: return

        new_deque = deque()
        while self.inventory[ticker]:
            batch = self.inventory[ticker].popleft()
            new_qty = batch['qty'] * ratio
            new_price = batch['price'] / ratio
            batch['qty'] = new_qty
            batch['price'] = new_price
            new_deque.append(batch)
        self.inventory[ticker] = new_deque

    def get_current_inventory(self):
        snapshot = {}
        for ticker, batches in self.inventory.items():
            total = sum(b['qty'] for b in batches)
            if total > 0:
                snapshot[ticker] = total
        return snapshot
