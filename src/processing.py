import hashlib
from typing import Dict, List
from decimal import Decimal
from .fifo import TradeMatcher
from .nbp import get_rate_for_tax_date
from .utils import money


class TaxCalculator:
    def __init__(self, target_year: str):
        self.target_year = target_year
        self.raw_dividends, self.raw_trades, self.raw_taxes = [], [], []
        self.seen_divs, self.seen_trades, self.seen_taxes = set(), set(), set()

        self.report_data = {
            "dividends": [],
            "monthly_dividends": {},
            "capital_gains": [],
            "holdings": [],
            "trades_history": [],  # New section
            "diagnostics": {
                "tickers_count": 0,
                "div_rows_count": 0,
                "tax_rows_count": 0,
            },
            "per_currency": {},
        }

    def ingest_preloaded_data(self, trades, divs, taxes):
        for d in divs:
            h = hashlib.md5(
                f"{d['date']}|{d['ticker']}|{d['amount']}".encode()
            ).hexdigest()
            if h not in self.seen_divs:
                self.seen_divs.add(h)
                self.raw_dividends.append(d)
        for t in taxes:
            h = hashlib.md5(
                f"{t['date']}|{t['ticker']}|{t['amount']}".encode()
            ).hexdigest()
            if h not in self.seen_taxes:
                self.seen_taxes.add(h)
                self.raw_taxes.append(t)
        for tr in trades:
            h = hashlib.md5(
                f"{tr['date']}|{tr['ticker']}|{tr['qty']}|{tr['price']}".encode()
            ).hexdigest()
            if h not in self.seen_trades:
                self.seen_trades.add(h)
                self.raw_trades.append(tr)

    def _calculate_holdings(self):
        sorted_trades = sorted(self.raw_trades, key=lambda x: x["date"])
        holdings = {}
        limit_date = f"{self.target_year}-12-31"

        for trade in sorted_trades:
            if trade["date"] > limit_date:
                break
            ticker = trade["ticker"]
            qty = trade["qty"]
            if ticker not in holdings:
                holdings[ticker] = Decimal("0")
            holdings[ticker] += qty

        result = []
        for ticker, qty in holdings.items():
            if abs(qty) > 0.0001:
                result.append({"ticker": ticker, "qty": float(qty)})

        self.report_data["holdings"] = sorted(result, key=lambda x: x["ticker"])

    def _collect_trades_history(self):
        # Filters all trades that happened strictly within the target year
        history = []
        for trade in self.raw_trades:
            if trade["date"].startswith(self.target_year):
                # We include BUY, SELL and even TRANSFER if present for completeness
                history.append(trade)

        # Sort by date
        history.sort(key=lambda x: x["date"])
        self.report_data["trades_history"] = history

    def run_calculations(self):
        # 0. Holdings & History
        self._calculate_holdings()
        self._collect_trades_history()

        # 1. DIVIDENDS & PER CURRENCY
        monthly_map = {}
        currency_map = {}
        unique_tickers = set()
        div_rows_in_year = 0

        for div in self.raw_dividends:
            if not div["date"].startswith(self.target_year):
                continue
            div_rows_in_year += 1
            unique_tickers.add(div["ticker"])

            rate = get_rate_for_tax_date(div["currency"], div["date"])
            amount_pln = money(div["amount"] * rate)

            curr = div["currency"]
            if curr not in currency_map:
                currency_map[curr] = Decimal("0.00")
            currency_map[curr] += amount_pln

            tax_paid, tax_paid_pln = 0, 0
            for t in self.raw_taxes:
                if t["ticker"] == div["ticker"] and t["date"] == div["date"]:
                    tax_paid += abs(t["amount"])
                    tax_paid_pln += abs(money(t["amount"] * rate))

            self.report_data["dividends"].append(
                {
                    "ticker": div["ticker"],
                    "date": div["date"],
                    "amount": float(div["amount"]),
                    "currency": div["currency"],
                    "rate": float(rate),
                    "amount_pln": float(amount_pln),
                    "tax_paid": float(tax_paid),
                    "tax_paid_pln": float(tax_paid_pln),
                }
            )

            month = div["date"].split("-")[1]
            if month not in monthly_map:
                monthly_map[month] = {"gross_pln": 0, "tax_pln": 0, "net_pln": 0}

            monthly_map[month]["gross_pln"] += float(amount_pln)
            monthly_map[month]["tax_pln"] += float(tax_paid_pln)
            monthly_map[month]["net_pln"] += float(amount_pln - tax_paid_pln)

        self.report_data["monthly_dividends"] = monthly_map
        self.report_data["per_currency"] = {
            k: float(v) for k, v in currency_map.items()
        }

        # 2. CAPITAL GAINS
        matcher = TradeMatcher()
        matcher.process_trades(self.raw_trades)
        for pnl in matcher.realized_pnl:
            if pnl["date_sell"].startswith(self.target_year):
                self.report_data["capital_gains"].append(pnl)
                unique_tickers.add(pnl["ticker"])

        # 3. DIAGNOSTICS
        tax_rows_in_year = 0
        for t in self.raw_taxes:
            if t["date"].startswith(self.target_year):
                tax_rows_in_year += 1

        self.report_data["diagnostics"]["tickers_count"] = len(unique_tickers)
        self.report_data["diagnostics"]["div_rows_count"] = div_rows_in_year
        self.report_data["diagnostics"]["tax_rows_count"] = tax_rows_in_year

    def get_results(self):
        return {"year": self.target_year, "data": self.report_data}
