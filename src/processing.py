import hashlib
from decimal import Decimal
from .fifo import TradeMatcher
from .nbp import get_rate_for_tax_date
from .utils import money
from .db_manager import fetch_all_trades, fetch_dividends, fetch_taxes, fetch_assets_metadata

class TaxCalculator:
    def __init__(self, target_year: str):
        self.target_year = target_year
        self.report_data = {
            "dividends": [],
            "monthly_dividends": {},
            "capital_gains": [],
            "holdings": [],
            "trades_history": [],
            "corp_actions": [],
            "diagnostics": {},
            "per_currency": {}
        }
        self.matcher = TradeMatcher()

    def run_calculations(self):
        print(f"📊 [DB Mode] Fetching history up to end of {self.target_year}...")
        
        cutoff = f"{self.target_year}-12-31"
        all_trades = fetch_all_trades(cutoff_date=cutoff)
        assets_meta = fetch_assets_metadata()
        
        print(f"   Loaded {len(all_trades)} trades. Running FIFO Engine...")
        
        self.matcher.process_trades(all_trades)
        fifo_inventory = self.matcher.get_current_inventory()
        
        # Holdings
        self.report_data["holdings"] = []
        for ticker, qty in fifo_inventory.items():
            if qty > 0:
                meta = assets_meta.get(ticker, {})
                curr = meta.get('currency', 'USD')
                is_restr = meta.get('is_restricted', False)
                
                if ticker not in assets_meta and ticker in self.matcher.inventory:
                     if self.matcher.inventory[ticker]:
                         curr = self.matcher.inventory[ticker][0].get('currency', 'USD')
                         if curr == 'RUB': is_restr = True

                self.report_data["holdings"].append({
                    "ticker": ticker,
                    "qty": float(qty),
                    "currency": curr,
                    "is_restricted": is_restr,
                    "fifo_match": True
                })
        
        # History
        history = []
        actions = []
        for t in all_trades:
            if t['date'].startswith(self.target_year):
                is_split = t['type'] == 'SPLIT'
                if is_split or t['type'] == 'TRANSFER':
                    actions.append(t)
                else:
                    history.append(t)
                    
        self.report_data["trades_history"] = sorted(history, key=lambda x: x['date'])
        self.report_data["corp_actions"] = sorted(actions, key=lambda x: x['date'])
        
        # Dividends & Taxes
        raw_divs = fetch_dividends(year=self.target_year)
        raw_taxes = fetch_taxes(year=self.target_year)
        self._process_dividends(raw_divs, raw_taxes)

        # Capital Gains
        unique_tickers = set()
        for pnl in self.matcher.realized_pnl:
            if pnl['date_sell'].startswith(self.target_year):
                self.report_data["capital_gains"].append(pnl)
                unique_tickers.add(pnl['ticker'])
                
        self.report_data["diagnostics"] = {
            "tickers_count": len(unique_tickers),
            "div_rows_count": len(raw_divs),
            "tax_rows_count": len(raw_taxes)
        }

    def _process_dividends(self, raw_divs, raw_taxes):
        monthly_map = {} 
        currency_map = {}
        
        for div in raw_divs:
            rate = get_rate_for_tax_date(div['currency'], div['date'])
            amount_pln = money(div['amount'] * rate)
            
            curr = div['currency']
            if curr not in currency_map: currency_map[curr] = Decimal("0.00")
            currency_map[curr] += amount_pln
            
            # Ищем соответствующий налог (по дате и тикеру)
            # Примечание: Это простая эвристика. Если в один день 2 див. по одному тикеру, 
            # налог может быть суммирован. 
            tax_paid = Decimal("0")
            tax_paid_pln = Decimal("0")
            
            for t in raw_taxes:
                if t['ticker'] == div['ticker'] and t['date'] == div['date']:
                    # Налог в IBKR обычно отрицательный (списан), поэтому берем abs
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

    def get_results(self):
        return {"year": self.target_year, "data": self.report_data}
