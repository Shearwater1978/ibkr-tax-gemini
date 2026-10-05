# src/fifo.py

import json
import re
from decimal import Decimal
from collections import deque
from typing import List, Dict, Any

from .nbp import get_rate_for_tax_date
from .utils import money
from .diagnostics import CalculationDiagnostic, UnmatchedInventoryError


class TradeMatcher:
    def __init__(self):
        self.inventory = {}
        self.realized_pnl = []
        self.observed_isins = {}
        self.identity_sensitive_tickers = set()
        self._carry = {}

    def process_trades(self, trades_list: List[Dict[str, Any]]):
        newly_sensitive = set()
        for trade in trades_list:
            isin = trade.get("isin", "") or ""
            if isin:
                self.observed_isins.setdefault(trade["ticker"], set()).add(isin)
                if len(self.observed_isins[trade["ticker"]]) > 1:
                    newly_sensitive.add(trade["ticker"])
        for ticker in newly_sensitive - self.identity_sensitive_tickers:
            legacy_batches = self.inventory.pop(ticker, deque())
            for batch in legacy_batches:
                key = (ticker, batch.get("isin", "") or "")
                self.inventory.setdefault(key, deque()).append(batch)
        self.identity_sensitive_tickers.update(newly_sensitive)

        # Priority: SPLIT (process first if same day to adjust holdings) -> BUY -> SELL
        type_priority = {
            "SPLIT": 0,
            "STOCK_DIV": 1,
            "MERGER": 1,
            "SPLIT_ADD": 1,
            "BUY": 2,
            "TRANSFER": 2,
            "SELL": 3,
        }

        sorted_trades = sorted(
            trades_list, key=lambda x: (x["date"], type_priority.get(x["type"], 99))
        )

        added_qty = {}
        removal_groups = {}
        for trade in sorted_trades:
            group = self._action_group(trade)
            if not group:
                continue
            if trade.get("qty", Decimal(0)) > 0:
                added_qty[group] = added_qty.get(group, Decimal(0)) + trade["qty"]
            elif trade.get("qty", Decimal(0)) < 0:
                removal_groups[group] = removal_groups.get(group, 0) + 1
        deferred = {}

        for trade in sorted_trades:
            ticker = trade["ticker"]
            inventory_key = self._inventory_key(trade)
            if inventory_key not in self.inventory:
                self.inventory[inventory_key] = deque()

            t_type = trade["type"]
            qty = trade.get("qty", Decimal(0))

            # --- SPECIAL HANDLING FOR SPLITS ---
            if t_type == "SPLIT":
                self._process_split(trade)
                continue

            # --- 1. POSITIVE QUANTITY (ADD TO INVENTORY) ---
            if qty > 0:
                # Includes: BUY, STOCK_DIV (Split add), MERGER (New shares), SPINOFF
                if t_type == "BUY" or t_type == "TRANSFER":
                    self._process_buy(trade)
                else:
                    # Corporate Action Additions (Zero Cost usually)
                    # Force price to 0 if it's a Corp Action to avoid messing up cost basis
                    trade["price"] = Decimal(0)
                    group = self._action_group(trade)
                    if group in self._carry and removal_groups.get(group) == 0:
                        self._process_carried_add(trade, group, added_qty[group])
                    elif removal_groups.get(group):
                        # Wait for the matching removal so its cost can move over.
                        deferred.setdefault(group, []).append(trade)
                    else:
                        self._process_buy(trade)

            # --- 2. NEGATIVE QUANTITY (REMOVE FROM INVENTORY) ---
            elif qty < 0:
                # Includes: SELL, MERGER (Old shares removal), LIQUIDATION
                if t_type == "SELL":
                    self._process_sell(trade)
                else:
                    # Corporate Action Removals (Non-Taxable Transfer Out)
                    consumed = self._consume_inventory(trade, is_taxable=False)
                    group = self._action_group(trade)
                    if group:
                        removal_groups[group] -= 1
                        if consumed:
                            entry = self._carry.setdefault(group, {"lots": []})
                            entry["lots"].extend(consumed)
                        if removal_groups[group] == 0:
                            for pending in deferred.pop(group, []):
                                if group in self._carry:
                                    self._process_carried_add(
                                        pending, group, added_qty[group]
                                    )
                                else:
                                    self._process_buy(pending)

        for pending_trades in deferred.values():
            for pending in pending_trades:
                self._process_buy(pending)

    @staticmethod
    def _action_group(trade):
        """Key shared by the removal and addition rows of one corporate action."""
        description = trade.get("description") or ""
        if trade.get("type") not in ("MERGER", "STOCK_DIV") or not description:
            return None
        stem = re.sub(r"\s*\([^()]*\)\s*$", "", description)
        return (trade["date"], stem) if stem != description else None

    def _process_carried_add(self, trade, group, group_added_qty):
        # Cost basis and purchase dates of the old shares move to the new ones.
        lots = self._carry[group]["lots"]
        removed_qty = sum(lot["qty"] for lot in lots)
        share = trade["qty"] / group_added_qty
        scale = group_added_qty / removed_qty
        for lot in lots:
            new_qty = lot["qty"] * scale * share
            cost = money(lot["cost_pln"] * share)
            self.inventory[self._inventory_key(trade)].append(
                {
                    "ticker": trade["ticker"],
                    "isin": trade.get("isin", ""),
                    "date": lot["date"],
                    "qty": new_qty,
                    "price": lot["price"] / scale,
                    "rate": lot["rate"],
                    "cost_pln": cost,
                    "currency": lot["currency"],
                    "source": lot.get("source", "UNKNOWN"),
                }
            )

    def _process_split(self, trade):
        inventory_key = self._inventory_key(trade)
        ratio = trade.get("ratio", Decimal(1))

        # IBKR represents reverse splits (e.g., "1 for 8") as TWO records:
        # - One with positive qty and the split ratio (e.g., +1)
        # - One with negative qty that should be ignored (e.g., -8)
        # Skip processing for the negative qty record
        qty = trade.get("qty", Decimal(0))
        if qty < 0:
            return

        keys = [inventory_key] if self.inventory.get(inventory_key) else []
        if not keys and isinstance(inventory_key, tuple):
            # A split that changes the ISIN is booked under the new ISIN while
            # the shares still sit under the old one.
            keys = [
                key
                for key, lots in self.inventory.items()
                if isinstance(key, tuple) and key[0] == trade["ticker"] and lots
            ]
        if not keys:
            return

        # Apply split to all existing batches in inventory
        # New Qty = Old Qty * Ratio
        # New Price = Old Price / Ratio (Cost basis per batch stays same)
        for key in keys:
            new_deque = deque()
            while self.inventory[key]:
                batch = self.inventory[key].popleft()
                batch["qty"] = batch["qty"] * ratio
                if ratio != 0:
                    batch["price"] = batch["price"] / ratio
                new_deque.append(batch)
            self.inventory[key] = new_deque

    def _process_buy(self, trade):
        if "rate" in trade and trade["rate"]:
            rate = trade["rate"]
        else:
            rate = get_rate_for_tax_date(trade["currency"], trade["date"])

        price = trade.get("price", Decimal(0))
        comm = trade.get("commission", Decimal(0))
        # Cost is calculated here
        cost_pln = money((price * trade["qty"] * rate) + (abs(comm) * rate))

        batch = {
            "ticker": trade["ticker"],
            "isin": trade.get("isin", ""),
            "date": trade["date"],
            "qty": trade["qty"],
            "price": price,
            "rate": rate,
            "cost_pln": cost_pln,
            "currency": trade["currency"],
            "source": trade.get("source", "UNKNOWN"),
        }
        self.inventory[self._inventory_key(trade)].append(batch)

    def _process_sell(self, trade):
        self._consume_inventory(trade, is_taxable=True)

    def _consume_inventory(self, trade, is_taxable):
        ticker = trade["ticker"]
        inventory_key = self._inventory_key(trade)
        isin = trade.get("isin", "")
        qty_to_sell = abs(trade["qty"])

        if "rate" in trade and trade["rate"]:
            sell_rate = trade["rate"]
        else:
            sell_rate = get_rate_for_tax_date(trade["currency"], trade["date"])

        price = trade.get("price", Decimal(0))
        comm = trade.get("commission", Decimal(0))

        sell_revenue_pln = money(price * qty_to_sell * sell_rate)

        cost_basis_pln = Decimal("0.00")
        matched_buys = []

        while qty_to_sell > 0:
            if not self.inventory.get(inventory_key):
                break

            buy_batch = self.inventory[inventory_key][0]

            # Avoid precision issues with tiny leftovers
            if buy_batch["qty"] <= qty_to_sell + Decimal("0.00000001"):
                # Take whole batch
                cost_basis_pln += buy_batch["cost_pln"]
                taken_qty = buy_batch["qty"]

                matched_buys.append(self._lot_for_output(buy_batch, ticker))
                self.inventory[inventory_key].popleft()

                qty_to_sell -= taken_qty
            else:
                # Take partial batch
                ratio = qty_to_sell / buy_batch["qty"]
                part_cost = money(buy_batch["cost_pln"] * ratio)

                partial_record = self._lot_for_output(buy_batch, ticker)
                partial_record["qty"] = qty_to_sell
                partial_record["cost_pln"] = part_cost
                matched_buys.append(partial_record)

                cost_basis_pln += part_cost

                buy_batch["qty"] -= qty_to_sell
                buy_batch["cost_pln"] -= part_cost
                qty_to_sell = 0

        if qty_to_sell > Decimal("0.00000001"):
            raise UnmatchedInventoryError(
                CalculationDiagnostic(
                    code="UNMATCHED_SELL",
                    message=(
                        f"Sell exceeds available inventory for {ticker}"
                        f" ({isin}) on "
                        if isin
                        else f"Sell exceeds available inventory for {ticker} on "
                    )
                    + f"{trade['date']} by {qty_to_sell}.",
                    ticker=ticker,
                    isin=isin or None,
                    date=trade["date"],
                    quantity=float(qty_to_sell),
                )
            )

        if is_taxable:
            sell_comm_pln = money(abs(comm) * sell_rate)
            total_cost = cost_basis_pln + sell_comm_pln
            profit_pln = sell_revenue_pln - total_cost

            result = {
                "ticker": ticker,
                "sale_date": trade["date"],
                "date_sell": trade["date"],
                "quantity": float(abs(trade["qty"])),
                "sale_price": float(price),
                "sale_rate": float(sell_rate),
                "sale_amount": float(sell_revenue_pln),
                "cost_basis": float(total_cost),
                "profit_loss": float(profit_pln),
                "currency": trade["currency"],
                "matched_buys": matched_buys,
            }
            if ticker in self.identity_sensitive_tickers:
                result["isin"] = isin
            self.realized_pnl.append(result)

        return matched_buys

    def get_realized_gains(self):
        return self.realized_pnl

    def get_current_inventory(self):
        inventory_list = []
        for batches in self.inventory.values():
            for batch in batches:
                item = {
                    "ticker": batch["ticker"],
                    "buy_date": batch["date"],
                    "quantity": float(batch["qty"]),
                    "cost_per_share": float(batch["price"]),
                    "total_cost": float(batch["cost_pln"]),
                    "currency": batch["currency"],
                }
                if batch["ticker"] in self.identity_sensitive_tickers:
                    item["isin"] = batch["isin"]
                inventory_list.append(item)
        return inventory_list

    def _inventory_key(self, trade):
        ticker = trade["ticker"]
        if ticker not in self.identity_sensitive_tickers:
            return ticker
        return ticker, trade.get("isin", "") or ""

    def _lot_for_output(self, batch, ticker):
        lot = batch.copy()
        if ticker not in self.identity_sensitive_tickers:
            lot.pop("ticker", None)
            lot.pop("isin", None)
        return lot
