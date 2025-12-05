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
            "trades_history": [],
            "corp_actions": [],
            "diagnostics": {
                "tickers_count": 0,
                "div_rows_count": 0,
                "tax_rows_count": 0
            },
            "per_currency": {}
        }

    def _get_hash(self, data_str: str) -> str:
        return hashlib.sha256(data_str.encode('utf-8')).hexdigest()

    def ingest_preloaded_data(self, trades, divs, taxes):
        for d in divs:
            sig = f"{d['date']}|{d['ticker']}|{d['amount']}"
            h = self._get_hash(sig)
            if h not in self.seen_divs:
                self.seen_divs.add(h)
                self.raw_dividends.append(d)
        for t in taxes:
            sig = f"{t['date']}|{t['ticker']}|{t['amount']}"
            h = self._get_hash(sig)
            if h not in self.seen_taxes:
                self.seen_taxes.add(h)
                self.raw_taxes.append(t)
        for tr in trades:
            sig = f"{tr['date']}|{tr['ticker']}|{tr.get('qty', 0)}|{tr.get('price', 0)}|{tr.get('type')}"
            h = self._get_hash(sig)
            if h not in self.seen_trades:
                self.seen_trades.add(h)
                self.raw_trades.append(tr)

    def _calculate_holdings(self):
        sorted_trades = sorted(self.raw_trades, key=lambda x: x['date'])
        
        # Structure: ticker -> {'qty': Decimal, 'currency': str}
        holdings_map = {}
        limit_date = f"{self.target_year}-12-31"
        
        for trade in sorted_trades:
            if trade['date'] > limit_date: break
            ticker = trade['ticker']
            
            # Init if not exists
            if ticker not in holdings_map:
                holdings_map[ticker] = {"qty": Decimal("0"), "currency": trade['currency']}
            
            # Update currency (in case it changes, keeps latest)
            holdings_map[ticker]['currency'] = trade['currency']

            # Handle Splits logic
            if trade['type'] == 'SPLIT':
                ratio = trade.get('ratio', Decimal(1))
                holdings_map[ticker]['qty'] = holdings_map[ticker]['qty'] * ratio
                continue

            # Standard Buy/Sell/Transfer
            qty = trade.get('qty', Decimal(0))
            holdings_map[ticker]['qty'] += qty
            
        result = []
        for ticker, data in holdings_map.items():
            qty = data['qty']
            if abs(qty) > 0.0001:
                # Determine if restricted (RUB assets)
                is_restricted = (data['currency'] == 'RUB')
                
                result.append({
                    "ticker": ticker, 
                    "qty": float(qty),
                    "currency": data['currency'],
                    "is_restricted": is_restricted
                })
        
        self.report_data["holdings"] = sorted(result, key=lambda x: x['ticker'])

    def _collect_history_lists(self):
        history = []
        actions = []
        
        for trade in self.raw_trades:
            if not trade['date'].startswith(self.target_year): continue
            
            is_split = trade['type'] == 'SPLIT'
            is_stock_div = trade.get('source') == 'IBKR_CORP_ACTION'
            
            if is_split or is_stock_div:
                actions.append(trade)
                if is_stock_div:
                    history.append(trade)
            else:
                history.append(trade)
        
        history.sort(key=lambda x: x['date'])
        actions.sort(key=lambda x: x['date'])
        
        self.report_data["trades_history"] = history
        self.report_data["corp_actions"] = actions

    def run_calculations(self):
        self._calculate_holdings()
        self._collect_history_lists()

        monthly_map = {} 
        currency_map = {}
        unique_tickers = set()
        div_rows_in_year = 0
        
        for div in self.raw_dividends:
            if not div['date'].startswith(self.target_year): continue
            div_rows_in_year += 1
            unique_tickers.add(div['ticker'])
            
            rate = get_rate_for_tax_date(div['currency'], div['date'])
            amount_pln = money(div['amount'] * rate)
            
            curr = div['currency']
            if curr not in currency_map: currency_map[curr] = Decimal("0.00")
            currency_map[curr] += amount_pln
            
            tax_paid, tax_paid_pln = 0, 0
            for t in self.raw_taxes:
                if t['ticker'] == div['ticker'] and t['date'] == div['date']:
                    tax_paid += abs(t['amount'])
                    tax_paid_pln += abs(money(t['amount'] * rate))
            
            self.report_data["dividends"].append({
                "ticker": div['ticker'],
                "date": div['date'],
                "amount": float(div['amount']),
                "currency": div['currency'],
                "rate": float(rate),
                "amount_pln": float(amount_pln),
                "tax_paid": float(tax_paid),
                "tax_paid_pln": float(tax_paid_pln)
            })
            
            month = div['date'].split('-')[1]
            if month not in monthly_map:
                monthly_map[month] = {"gross_pln": 0, "tax_pln": 0, "net_pln": 0}
            
            monthly_map[month]["gross_pln"] += float(amount_pln)
            monthly_map[month]["tax_pln"] += float(tax_paid_pln)
            monthly_map[month]["net_pln"] += float(amount_pln - tax_paid_pln)

        self.report_data["monthly_dividends"] = monthly_map
        self.report_data["per_currency"] = {k: float(v) for k, v in currency_map.items()}

        matcher = TradeMatcher()
        matcher.process_trades(self.raw_trades)
        for pnl in matcher.realized_pnl:
            if pnl['date_sell'].startswith(self.target_year):
                self.report_data["capital_gains"].append(pnl)
                unique_tickers.add(pnl['ticker'])
        
        tax_rows_in_year = 0
        for t in self.raw_taxes:
            if t['date'].startswith(self.target_year): tax_rows_in_year += 1
            
        self.report_data["diagnostics"]["tickers_count"] = len(unique_tickers)
        self.report_data["diagnostics"]["div_rows_count"] = div_rows_in_year
        self.report_data["diagnostics"]["tax_rows_count"] = tax_rows_in_year

    def get_results(self):
        return {"year": self.target_year, "data": self.report_data}
