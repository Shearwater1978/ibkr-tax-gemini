import os

PROJECT_UPDATE = {
    # 1. FIFO: Учим отдавать текущий остаток
    "src/fifo.py": """from decimal import Decimal
from collections import deque
from .nbp import get_rate_for_tax_date
from .utils import money

class TradeMatcher:
    def __init__(self):
        self.inventory = {} 
        self.realized_pnl = []

    def process_trades(self, trades_list):
        sorted_trades = sorted(trades_list, key=lambda x: x['date'])

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
                # Transfers adjust inventory quantity without tax event
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
        sell_comm_pln = money(abs(comm) * sell_rate)
        
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
        # Returns {ticker: total_qty} based on FIFO queue
        snapshot = {}
        for ticker, batches in self.inventory.items():
            total = sum(b['qty'] for b in batches)
            if total > 0:
                snapshot[ticker] = total
        return snapshot
""",

    # 2. PROCESSING: Сравниваем суммы
    "src/processing.py": """import hashlib
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
            "diagnostics": {},
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

    def _calculate_holdings_simple(self):
        # Calculates holdings based on simple summation (Broker view)
        sorted_trades = sorted(self.raw_trades, key=lambda x: x['date'])
        holdings_map = {}
        limit_date = f"{self.target_year}-12-31"
        
        for trade in sorted_trades:
            if trade['date'] > limit_date: break
            ticker = trade['ticker']
            
            if ticker not in holdings_map:
                holdings_map[ticker] = {"qty": Decimal("0"), "currency": trade['currency"]}
            
            holdings_map[ticker]['currency'] = trade['currency']

            if trade['type'] == 'SPLIT':
                ratio = trade.get('ratio', Decimal(1))
                holdings_map[ticker]['qty'] = holdings_map[ticker]['qty'] * ratio
                continue

            qty = trade.get('qty', Decimal(0))
            holdings_map[ticker]['qty'] += qty
            
        result = []
        for ticker, data in holdings_map.items():
            qty = data['qty']
            if abs(qty) > 0.0001:
                is_restricted = (data['currency'] == 'RUB')
                result.append({
                    "ticker": ticker, 
                    "qty": float(qty),
                    "currency": data['currency'],
                    "is_restricted": is_restricted,
                    "fifo_match": False # Will be updated later
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
                if is_stock_div: history.append(trade)
            else:
                history.append(trade)
        
        history.sort(key=lambda x: x['date'])
        actions.sort(key=lambda x: x['date'])
        self.report_data["trades_history"] = history
        self.report_data["corp_actions"] = actions

    def run_calculations(self):
        # 1. Calculate Simple Sum Holdings
        self._calculate_holdings_simple()
        self._collect_history_lists()

        # 2. Run FIFO Engine
        matcher = TradeMatcher()
        matcher.process_trades(self.raw_trades)
        
        # 3. FIFO Reconciliation (The "Check")
        fifo_inventory = matcher.get_current_inventory()
        
        for holding in self.report_data["holdings"]:
            ticker = holding["ticker"]
            simple_qty = Decimal(str(holding["qty"]))
            fifo_qty = fifo_inventory.get(ticker, Decimal("0"))
            
            # Check difference (tolerance for float errors)
            if abs(simple_qty - fifo_qty) < 0.0001:
                holding["fifo_match"] = True
            else:
                holding["fifo_match"] = False
                print(f"⚠️ MISMATCH for {ticker}: Broker says {simple_qty}, FIFO engine says {fifo_qty}")

        # 4. Dividends & Rest
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

        for pnl in matcher.realized_pnl:
            if pnl['date_sell'].startswith(self.target_year):
                self.report_data["capital_gains"].append(pnl)
                unique_tickers.add(pnl['ticker'])
        
        tax_rows_in_year = 0
        for t in self.raw_taxes:
            if t['date'].startswith(self.target_year): tax_rows_in_year += 1
            
        self.report_data["diagnostics"] = {
            "tickers_count": len(unique_tickers),
            "div_rows_count": div_rows_in_year,
            "tax_rows_count": tax_rows_in_year
        }

    def get_results(self):
        return {"year": self.target_year, "data": self.report_data}
""",

    # 3. PDF: Рисуем новый столбец
    "src/report_pdf.py": """from reportlab.lib import colors
from reportlab.lib.pagesizes import A4
from reportlab.platypus import SimpleDocTemplate, Table, TableStyle, Paragraph, Spacer, PageBreak
from reportlab.lib.styles import getSampleStyleSheet, ParagraphStyle
from reportlab.lib.enums import TA_CENTER, TA_LEFT
from reportlab.lib.units import mm
import itertools

APP_NAME = "IBKR Tax Assistant"
APP_VERSION = "v1.1.0" # Added Reconciliation Check

def get_zebra_style(row_count, header_color=colors.HexColor('#D0D0D0')):
    cmds = [
        ('BACKGROUND', (0,0), (-1,0), header_color),
        ('GRID', (0,0), (-1,-1), 1, colors.black),
        ('ALIGN', (0,0), (-1,0), 'CENTER'),
        ('VALIGN', (0,0), (-1,-1), 'MIDDLE'),
        ('FONTNAME', (0,0), (-1,0), 'Helvetica-Bold'),
        ('FONTSIZE', (0,0), (-1,-1), 9),
    ]
    for i in range(1, row_count):
        if i % 2 == 0:
            cmds.append(('BACKGROUND', (0,i), (-1,i), colors.HexColor('#F0F0F0')))
    return TableStyle(cmds)

def add_footer(canvas, doc):
    canvas.saveState()
    canvas.setFont('Helvetica', 8)
    canvas.setFillColor(colors.grey)
    footer_text = f"Generated by {APP_NAME} {APP_VERSION}"
    canvas.drawString(10 * mm, 10 * mm, footer_text)
    page_num = f"Page {doc.page}"
    canvas.drawRightString(A4[0] - 10 * mm, 10 * mm, page_num)
    canvas.setStrokeColor(colors.lightgrey)
    canvas.line(10 * mm, 14 * mm, A4[0] - 10 * mm, 14 * mm)
    canvas.restoreState()

def generate_pdf(json_data, filename="report.pdf"):
    doc = SimpleDocTemplate(filename, pagesize=A4, bottomMargin=20*mm, topMargin=20*mm)
    elements = []
    styles = getSampleStyleSheet()
    
    year = json_data['year']
    data = json_data['data']

    title_style = ParagraphStyle('ReportTitle', parent=styles['Title'], fontSize=28, spaceAfter=30, alignment=TA_CENTER)
    subtitle_style = ParagraphStyle('ReportSubtitle', parent=styles['Normal'], fontSize=14, alignment=TA_CENTER)
    h2_style = ParagraphStyle('H2Centered', parent=styles['Heading2'], alignment=TA_CENTER, spaceAfter=15, spaceBefore=20)
    h3_style = ParagraphStyle('H3Centered', parent=styles['Heading3'], alignment=TA_CENTER, spaceAfter=10, spaceBefore=5)
    normal_style = styles['Normal']
    italic_small = ParagraphStyle('ItalicSmall', parent=styles['Italic'], fontSize=8, alignment=TA_LEFT)
    
    # PAGE 1
    elements.append(Spacer(1, 100))
    elements.append(Paragraph(f"Tax report — {year}", title_style))
    elements.append(Spacer(1, 10))
    elements.append(Paragraph(f"Report period: 01-01-{year} - 31-12-{year}", subtitle_style))
    elements.append(PageBreak())

    # PAGE 2: PORTFOLIO (WITH FIFO CHECK)
    elements.append(Paragraph(f"Portfolio Composition (as of Dec 31, {year})", h2_style))
    if data['holdings']:
        # Header updated
        holdings_data = [["Ticker", "Quantity", "FIFO Check"]]
        restricted_indices = []
        has_restricted = False
        
        row_idx = 1
        for h in data['holdings']:
            display_ticker = h['ticker']
            if h.get('is_restricted', False):
                display_ticker += " *"
                has_restricted = True
                restricted_indices.append(row_idx)
            
            # FIFO Check Logic
            check_mark = "OK" if h.get('fifo_match', False) else "MISMATCH!"
            
            holdings_data.append([display_ticker, f"{h['qty']:.3f}", check_mark])
            row_idx += 1
            
        # Update column widths to fit 3 cols
        t_holdings = Table(holdings_data, colWidths=[180, 100, 100], repeatRows=1)
        ts = get_zebra_style(len(holdings_data))
        ts.add('ALIGN', (1,1), (1,-1), 'RIGHT') # Qty right align
        
        # Color coding for Mismatches (Safety feature)
        for i, row in enumerate(holdings_data[1:], start=1):
            if row[2] != "OK":
                ts.add('TEXTCOLOR', (2, i), (2, i), colors.red)
        
        # Red Highlight for Restricted
        for r_idx in restricted_indices:
            ts.add('BACKGROUND', (0, r_idx), (-1, r_idx), colors.HexColor('#FFCCCC'))
            
        t_holdings.setStyle(ts)
        elements.append(t_holdings)
        
        if has_restricted:
            elements.append(Spacer(1, 10))
            elements.append(Paragraph("* Assets held in special escrow accounts / sanctioned (RUB)", italic_small))
    else:
        elements.append(Paragraph("No open positions found at end of year.", normal_style))
    elements.append(PageBreak())

    # PAGE 3: TRADES HISTORY
    elements.append(Paragraph(f"Trades History ({year})", h2_style))
    if data['trades_history']:
        trades_header = [["Date", "Ticker", "Type", "Qty", "Price", "Comm", "Curr"]]
        trades_rows = []
        for t in data['trades_history']:
            t_type = t.get('type', 'UNKNOWN')
            row = [
                t['date'],
                t['ticker'],
                t_type,
                f"{abs(t['qty']):.3f}",
                f"{t['price']:.2f}",
                f"{t['commission']:.2f}",
                t['currency']
            ]
            trades_rows.append(row)
        full_table_data = trades_header + trades_rows
        col_widths = [65, 55, 55, 55, 55, 55, 45]
        t_trades = Table(full_table_data, colWidths=col_widths, repeatRows=1)
        ts_trades = get_zebra_style(len(full_table_data))
        ts_trades.add('ALIGN', (3,1), (-1,-1), 'RIGHT') 
        ts_trades.add('FONTSIZE', (0,0), (-1,-1), 8)    
        t_trades.setStyle(ts_trades)
        elements.append(t_trades)
    else:
        elements.append(Paragraph("No trades executed this year.", normal_style))
    
    # PAGE: CORPORATE ACTIONS
    if data['corp_actions']:
        elements.append(PageBreak())
        elements.append(Paragraph(f"Corporate Actions & Splits ({year})", h2_style))
        corp_header = [["Date", "Ticker", "Type", "Details"]]
        corp_rows = []
        for act in data['corp_actions']:
            details = ""
            if act['type'] == 'SPLIT':
                ratio = act.get('ratio', 1)
                details = f"Split Ratio: {ratio:.4f}"
            elif act['type'] == 'BUY' and act.get('source') == 'IBKR_CORP_ACTION':
                 details = f"Stock Div: +{act['qty']:.4f} shares"
            elif act['type'] == 'TRANSFER' and act.get('source') == 'IBKR_CORP_ACTION':
                 details = f"Adjustment: {act['qty']:.4f}"
            else:
                 details = "Other Adjustment"
            corp_rows.append([act['date'], act['ticker'], act['type'], details])
        full_corp_data = corp_header + corp_rows
        t_corp = Table(full_corp_data, colWidths=[100, 80, 80, 200], repeatRows=1)
        t_corp.setStyle(get_zebra_style(len(full_corp_data)))
        elements.append(t_corp)

    elements.append(PageBreak())

    # PAGE 4: MONTHLY DIVIDENDS SUMMARY
    elements.append(Paragraph(f"Monthly Dividends Summary ({year})", h2_style))
    month_names = { "01": "January", "02": "February", "03": "March", "04": "April", "05": "May", "06": "June", "07": "July", "08": "August", "09": "September", "10": "October", "11": "November", "12": "December" }
    
    if data['monthly_dividends']:
        m_data = [["Month", "Gross (PLN)", "Tax Paid (PLN)", "Net (PLN)"]]
        sorted_months = sorted(data['monthly_dividends'].keys())
        total_gross, total_tax = 0, 0
        for m in sorted_months:
            vals = data['monthly_dividends'][m]
            m_data.append([
                month_names.get(m, m),
                f"{vals['gross_pln']:,.2f}",
                f"{vals['tax_pln']:,.2f}",
                f"{vals['net_pln']:,.2f}"
            ])
            total_gross += vals['gross_pln']
            total_tax += vals['tax_pln']
        m_data.append(["TOTAL", f"{total_gross:,.2f}", f"{total_tax:,.2f}", f"{total_gross - total_tax:,.2f}"])
        t_months = Table(m_data, colWidths=[110, 110, 110, 110], repeatRows=1)
        ts = get_zebra_style(len(m_data))
        ts.add('FONT-WEIGHT', (0,-1), (-1,-1), 'BOLD')
        ts.add('BACKGROUND', (0,-1), (-1,-1), colors.lightgrey)
        ts.add('ALIGN', (1,1), (-1,-1), 'RIGHT')
        t_months.setStyle(ts)
        elements.append(t_months)
        
        # --- DETAILED DIVIDENDS ---
        elements.append(PageBreak()) 
        elements.append(Paragraph(f"Dividend Details (Chronological)", h2_style))
        elements.append(Paragraph("Detailed breakdown of every dividend payment received.", normal_style))
        elements.append(Spacer(1, 10))
        
        sorted_divs = sorted(data['dividends'], key=lambda x: x['date'])
        
        is_first_month = True
        for month_key, group in itertools.groupby(sorted_divs, key=lambda x: x['date'][:7]):
            if not is_first_month:
                elements.append(PageBreak())
            is_first_month = False
            
            y, m = month_key.split('-')
            m_name = month_names.get(m, m)
            elements.append(Paragraph(f"{m_name} {y}", h2_style))
            
            det_header = [["Date", "Ticker", "Gross", "Rate", "Gross PLN", "Tax PLN"]]
            det_rows = []
            for d in group:
                det_rows.append([
                    d['date'],
                    d['ticker'],
                    f"{d['amount']:.2f} {d['currency']}",
                    f"{d['rate']:.4f}",
                    f"{d['amount_pln']:.2f}",
                    f"{d['tax_paid_pln']:.2f}"
                ])
            full_det_data = det_header + det_rows
            t_det = Table(full_det_data, colWidths=[70, 50, 90, 50, 70, 70], repeatRows=1)
            ts_det = get_zebra_style(len(full_det_data))
            ts_det.add('ALIGN', (2,1), (-1,-1), 'RIGHT')
            ts_det.add('FONTSIZE', (0,0), (-1,-1), 8)
            t_det.setStyle(ts_det)
            elements.append(t_det)
        
    else:
        elements.append(Paragraph("No dividends received this year.", normal_style))
    
    elements.append(PageBreak())

    # PAGE: YEARLY SUMMARY
    elements.append(Paragraph(f"Yearly Summary", h2_style))
    div_gross = sum(x['amount_pln'] for x in data['dividends'])
    div_tax = sum(x['tax_paid_pln'] for x in data['dividends'])
    polish_tax_due = max(0, (div_gross * 0.19) - div_tax)
    final_net = div_gross - div_tax - polish_tax_due
    
    summary_data = [
        ["Metric", "Amount (PLN)"],
        ["Total Dividends", f"{div_gross:,.2f}"],
        ["Withheld Tax (sum)", f"-{div_tax:,.2f}"],
        ["Additional Tax (PL, ~diff)", f"{polish_tax_due:,.2f}"],
        ["Final Net (after full 19%)", f"{final_net:,.2f}"]
    ]
    t_summary = Table(summary_data, colWidths=[250, 150])
    ts_sum = get_zebra_style(len(summary_data))
    ts_sum.add('ALIGN', (1,1), (-1,-1), 'RIGHT')
    t_summary.setStyle(ts_sum)
    elements.append(t_summary)
    
    elements.append(Spacer(1, 20))
    elements.append(Paragraph("Diagnostics", h2_style))
    diag = data['diagnostics']
    diag_data = [
        ["Indicator", "Value"],
        ["Tickers (unique)", str(diag['tickers_count'])],
        ["Dividend rows", str(diag['div_rows_count'])],
        ["Tax rows", str(diag['tax_rows_count'])]
    ]
    t_diag = Table(diag_data, colWidths=[250, 150])
    ts_diag = get_zebra_style(len(diag_data))
    ts_diag.add('ALIGN', (1,1), (-1,-1), 'CENTER')
    t_diag.setStyle(ts_diag)
    elements.append(t_diag)
    
    elements.append(Spacer(1, 20))
    elements.append(Paragraph("Per-currency totals (PLN)", h2_style))
    curr_data = [["Currency", "PLN total"]]
    for curr, val in data['per_currency'].items():
        curr_data.append([curr, f"{val:,.2f}"])
    t_curr = Table(curr_data, colWidths=[250, 150], repeatRows=1)
    ts_curr = get_zebra_style(len(curr_data))
    ts_curr.add('ALIGN', (1,1), (-1,-1), 'RIGHT')
    t_curr.setStyle(ts_curr)
    elements.append(t_curr)
    elements.append(PageBreak())

    # PAGE: PIT-38
    elements.append(Paragraph(f"PIT-38 Helper Data ({year})", h2_style))
    elements.append(Spacer(1, 10))
    elements.append(Paragraph("Section C (Stocks/Derivatives)", h3_style))
    cap_rev = sum(x['revenue_pln'] for x in data['capital_gains'])
    cap_cost = sum(x['cost_pln'] for x in data['capital_gains'])
    pit_c_data = [
        ["Field in PIT-38", "Value (PLN)"],
        ["Przychód (Revenue) [Pos 20]", f"{cap_rev:,.2f}"],
        ["Koszty (Costs) [Pos 21]", f"{cap_cost:,.2f}"],
        ["Dochód/Strata", f"{cap_rev - cap_cost:,.2f}"]
    ]
    t_pit_c = Table(pit_c_data, colWidths=[250, 150])
    ts_pit = get_zebra_style(len(pit_c_data))
    ts_pit.add('ALIGN', (1,1), (-1,-1), 'RIGHT')
    t_pit_c.setStyle(ts_pit)
    elements.append(t_pit_c)
    elements.append(Spacer(1, 5))
    elements.append(Paragraph("<i>* Note: 'Koszty' includes purchase price + buy/sell commissions.</i>", styles['Italic']))
    elements.append(Spacer(1, 20))

    elements.append(Paragraph("Dividends (Foreign Tax)", h3_style))
    pit_div_data = [
        ["Description", "Value (PLN)"],
        ["Gross Income", f"{div_gross:,.2f}"],
        ["Tax Paid Abroad (Max deductible)", f"{div_tax:,.2f}"],
        ["TO PAY (Difference) [Pos 45]", f"{polish_tax_due:,.2f}"] 
    ]
    t_pit_div = Table(pit_div_data, colWidths=[250, 150])
    ts_pit_div = get_zebra_style(len(pit_div_data))
    ts_pit_div.add('ALIGN', (1,1), (-1,-1), 'RIGHT')
    ts_pit_div.add('FONTNAME', (0,-1), (-1,-1), 'Helvetica-Bold') 
    t_pit_div.setStyle(ts_pit_div)
    elements.append(t_pit_div)

    doc.build(elements, onFirstPage=add_footer, onLaterPages=add_footer)
"""
}

def install_fifo_check():
    print("⚖️ Installing FIFO Reconciliation Logic...")
    for file_path, content in PROJECT_UPDATE.items():
        with open(file_path, "w", encoding="utf-8") as f:
            f.write(content)
        print(f"   Updated: {file_path}")
    print("\n✅ Installed! Run 'python main.py'.")
    print("   Look for the 'FIFO Check' column in the Portfolio table.")
    print("   If you see 'MISMATCH!', check the console output for details.")

if __name__ == "__main__":
    install_fifo_check()