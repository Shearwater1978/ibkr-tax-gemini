# src/processing.py

from typing import List, Dict, Any, Tuple
from decimal import Decimal
from collections import defaultdict
import logging

# Project imports
from src.nbp import get_nbp_rate
from src.fifo import TradeMatcher
from src.diagnostics import CalculationDiagnostic
from src.instrument_identity import TICKER_ALIASES, resolve_instrument_identities


def process_yearly_data(
    raw_trades: List[Dict[str, Any]], target_year: int, include_diagnostics=False
):
    """
    Main Processing Pipeline:
    1. Fetches raw data from DB.
    2. Maps Withholding Taxes to Dividends.
    3. Feeds all events (Trades, Corp Actions, Dividends) into the FIFO engine.
    4. Returns calculated Realized Gains, Dividends, and Inventory.
    """

    raw_trades, identity_changes = resolve_instrument_identities(raw_trades)
    changed_tickers = {change["ticker"] for change in identity_changes}
    matcher = TradeMatcher()

    dividends = []
    fifo_input_list = []

    print(f"INFO: Processing {len(raw_trades)} trades via FIFO engine...")

    # --- 1. Pre-process Taxes ---
    # IBKR stores Withholding Tax as separate rows.
    # We aggregate them into a map: (Date, Ticker) -> Total Tax Amount
    tax_map = defaultdict(Decimal)

    for t in raw_trades:
        if t["EventType"] == "TAX":
            # Tax amount in DB is usually negative. We store the absolute magnitude.
            amt = Decimal(str(t["Amount"])) if t["Amount"] else Decimal(0)
            isin = t.get("ISIN", "") or ""
            key = (t["Date"], t["Ticker"], isin)
            tax_map[key] += abs(amt)

    # Sort trades chronologically to ensure correct processing order
    sorted_trades = sorted(raw_trades, key=lambda x: (x["Date"], x["TradeId"]))

    for trade in sorted_trades:
        # Extract fields
        date_str = trade["Date"]
        ticker = trade["Ticker"]
        # Apply normalization (e.g., TOT -> TTE)
        ticker = TICKER_ALIASES.get(ticker, ticker)
        isin = trade.get("ISIN", "") or ""
        event_type = trade[
            "EventType"
        ]  # BUY, SELL, SPLIT, DIVIDEND, STOCK_DIV, MERGER, etc.
        currency = trade["Currency"]

        # Convert to Decimal for precision
        quantity = Decimal(str(trade["Quantity"])) if trade["Quantity"] else Decimal(0)
        price = Decimal(str(trade["Price"])) if trade["Price"] else Decimal(0)
        amount_currency = (
            Decimal(str(trade["Amount"])) if trade["Amount"] else Decimal(0)
        )
        fee = Decimal(str(trade["Fee"])) if trade["Fee"] else Decimal(0)

        description = trade.get("Description", "")

        # --- 2. Get Exchange Rate (NBP) ---
        rate = Decimal("1.0")
        needs_exchange_rate = event_type in {"BUY", "SELL", "DIVIDEND"} or (
            event_type == "TRANSFER" and quantity > 0 and (price != 0 or fee != 0)
        )
        if currency != "PLN" and needs_exchange_rate:
            rate = get_nbp_rate(currency, date_str)

        # --- 3. Event Routing ---

        if event_type == "DIVIDEND":
            # --- Handle Cash Dividends ---
            gross_pln = amount_currency * rate

            # Find matching tax
            tax_in_original_currency = tax_map.get(
                (date_str, trade["Ticker"], isin), Decimal(0)
            )
            tax_pln = tax_in_original_currency * rate

            div_record = {
                "ex_date": date_str,
                "ticker": ticker,
                "gross_amount_pln": float(gross_pln),
                "tax_withheld_pln": float(tax_pln),
                "currency": currency,
                "rate": float(rate),
            }
            if ticker in changed_tickers:
                div_record["isin"] = isin
            # Only include dividends from the target year in the report
            if date_str.startswith(str(target_year)):
                dividends.append(div_record)

        elif event_type == "TAX":
            # Already handled in Pre-process step
            pass

        else:
            # --- Handle FIFO Events (Trades & Corp Actions) ---
            # BUY, SELL, SPLIT, TRANSFER, STOCK_DIV, MERGER, SPINOFF
            matcher_type = event_type

            trade_record = {
                "type": matcher_type,
                "date": date_str,
                "ticker": ticker,
                "qty": quantity,
                "price": price,
                "commission": fee,
                "currency": currency,
                "rate": rate,
                "source": "DB",
                "isin": isin,
            }

            if matcher_type == "SPLIT":
                trade_record["ratio"] = Decimal(str(trade.get("SplitRatio", 1)))

            fifo_input_list.append(trade_record)

    # --- 4. Execute FIFO Engine ---
    matcher.process_trades(fifo_input_list)

    # --- 5. Extract Final Results ---
    all_realized = matcher.get_realized_gains()

    # Filter P&L for the requested tax year
    target_realized = [
        r for r in all_realized if r["sale_date"].startswith(str(target_year))
    ]

    inventory = matcher.get_current_inventory()

    if include_diagnostics:
        diagnostics = [
            CalculationDiagnostic(
                code="IDENTITY_CHANGE",
                message=(
                    f"Ticker {change['ticker']} changed identity from "
                    f"{change['previous_isin']} to {change['new_isin']} on {change['date']}."
                ),
                ticker=change["ticker"],
                date=change["date"],
                previous_isin=change["previous_isin"],
                new_isin=change["new_isin"],
            )
            for change in identity_changes
        ]
        return target_realized, dividends, inventory, diagnostics
    return target_realized, dividends, inventory
