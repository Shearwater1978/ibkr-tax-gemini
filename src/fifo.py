from decimal import Decimal
from collections import deque
from .nbp import get_rate_for_tax_date
from .utils import money


class TradeMatcher:
    def __init__(self):
        self.inventory = {}
        self.realized_pnl = []

    def process_trades(self, trades_list):
        sorted_trades = sorted(trades_list, key=lambda x: x["date"])
        for trade in sorted_trades:
            if trade["type"] == "TRANSFER":
                continue
            ticker = trade["ticker"]
            if ticker not in self.inventory:
                self.inventory[ticker] = deque()
            if trade["qty"] > 0:
                self._process_buy(trade)
            elif trade["qty"] < 0:
                self._process_sell(trade)

    def _process_buy(self, trade):
        rate = get_rate_for_tax_date(trade["currency"], trade["date"])
        cost_pln = money(
            (trade["price"] * trade["qty"] * rate) + (abs(trade["commission"]) * rate)
        )
        self.inventory[trade["ticker"]].append(
            {
                "date": trade["date"],
                "qty": trade["qty"],
                "price": trade["price"],
                "rate": rate,
                "cost_pln": cost_pln,
                "source": trade.get("source", "UNKNOWN"),
            }
        )

    def _process_sell(self, trade):
        ticker = trade["ticker"]
        qty_to_sell = abs(trade["qty"])
        sell_rate = get_rate_for_tax_date(trade["currency"], trade["date"])
        sell_revenue_pln = money(trade["price"] * qty_to_sell * sell_rate)
        sell_comm_pln = money(abs(trade["commission"]) * sell_rate)
        cost_basis_pln = Decimal("0.00")
        matched_buys = []
        while qty_to_sell > 0:
            if not self.inventory[ticker]:
                break
            buy_batch = self.inventory[ticker][0]
            if buy_batch["qty"] <= qty_to_sell:
                cost_basis_pln += buy_batch["cost_pln"]
                qty_to_sell -= buy_batch["qty"]
                matched_buys.append(buy_batch.copy())
                self.inventory[ticker].popleft()
            else:
                ratio = qty_to_sell / buy_batch["qty"]
                part_cost = money(buy_batch["cost_pln"] * ratio)
                partial = buy_batch.copy()
                partial["qty"] = qty_to_sell
                partial["cost_pln"] = part_cost
                matched_buys.append(partial)
                cost_basis_pln += part_cost
                buy_batch["qty"] -= qty_to_sell
                buy_batch["cost_pln"] -= part_cost
                qty_to_sell = 0
        total_cost = cost_basis_pln + sell_comm_pln
        profit_pln = sell_revenue_pln - total_cost
        self.realized_pnl.append(
            {
                "ticker": ticker,
                "date_sell": trade["date"],
                "revenue_pln": float(sell_revenue_pln),
                "cost_pln": float(total_cost),
                "profit_pln": float(profit_pln),
                "matched_buys": matched_buys,
            }
        )
