from reportlab.lib import colors
from reportlab.lib.pagesizes import A4
from reportlab.platypus import SimpleDocTemplate, Table, TableStyle, Paragraph, Spacer, PageBreak
from reportlab.lib.styles import getSampleStyleSheet, ParagraphStyle
from reportlab.lib.enums import TA_CENTER, TA_LEFT

def get_zebra_style(row_count, header_color=colors.HexColor('#D0D0D0')):
    """
    Enhanced Zebra style with better contrast and centered headers.
    """
    cmds = [
        ('BACKGROUND', (0,0), (-1,0), header_color),       # Header background
        ('GRID', (0,0), (-1,-1), 1, colors.black),         # Full grid
        ('ALIGN', (0,0), (-1,0), 'CENTER'),                # Header text centered
        ('VALIGN', (0,0), (-1,-1), 'MIDDLE'),              # Vertical alignment
        ('FONTNAME', (0,0), (-1,0), 'Helvetica-Bold'),     # Bold header font
        ('FONTSIZE', (0,0), (-1,-1), 9),                   # Smaller font
    ]
    
    # Alternating rows (Zebra)
    for i in range(1, row_count):
        if i % 2 == 0:
            cmds.append(('BACKGROUND', (0,i), (-1,i), colors.HexColor('#F0F0F0')))
    
    return TableStyle(cmds)

def generate_pdf(json_data, filename="report.pdf"):
    doc = SimpleDocTemplate(filename, pagesize=A4)
    elements = []
    styles = getSampleStyleSheet()
    
    year = json_data['year']
    data = json_data['data']

    # --- TEXT STYLES ---
    title_style = ParagraphStyle('ReportTitle', parent=styles['Title'], fontSize=28, spaceAfter=30, alignment=TA_CENTER)
    subtitle_style = ParagraphStyle('ReportSubtitle', parent=styles['Normal'], fontSize=14, alignment=TA_CENTER)
    
    # H2 Centered for main section titles
    h2_style = ParagraphStyle('H2Centered', parent=styles['Heading2'], alignment=TA_CENTER, spaceAfter=15, spaceBefore=20)
    
    # H3 Centered for subsection titles (like in PIT-38)
    h3_style = ParagraphStyle('H3Centered', parent=styles['Heading3'], alignment=TA_CENTER, spaceAfter=10, spaceBefore=10)
    
    normal_style = styles['Normal']
    
    # ==========================================
    # PAGE 1: TITLE PAGE
    # ==========================================
    elements.append(Spacer(1, 100))
    elements.append(Paragraph(f"Tax report — {year}", title_style))
    elements.append(Spacer(1, 10))
    elements.append(Paragraph(f"Report period: 01-01-{year} - 31-12-{year}", subtitle_style))
    elements.append(PageBreak())

    # ==========================================
    # PAGE 2: PORTFOLIO (HOLDINGS)
    # ==========================================
    elements.append(Paragraph(f"Portfolio Composition (as of Dec 31, {year})", h2_style))
    
    if data['holdings']:
        holdings_data = [["Ticker", "Quantity"]]
        for h in data['holdings']:
            holdings_data.append([h['ticker'], f"{h['qty']:.3f}"])
        
        t_holdings = Table(holdings_data, colWidths=[200, 150], repeatRows=1)
        ts = get_zebra_style(len(holdings_data))
        ts.add('ALIGN', (1,1), (-1,-1), 'RIGHT')
        t_holdings.setStyle(ts)
        elements.append(t_holdings)
    else:
        elements.append(Paragraph("No open positions found at end of year.", normal_style))
    
    elements.append(PageBreak())

    # ==========================================
    # PAGE 3: TRADES HISTORY
    # ==========================================
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

    elements.append(PageBreak())

    # ==========================================
    # PAGE 4: MONTHLY DIVIDENDS
    # ==========================================
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
    else:
        elements.append(Paragraph("No dividends received this year.", normal_style))

    elements.append(PageBreak())

    # ==========================================
    # PAGE 5: YEARLY SUMMARY
    # ==========================================
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

    # ==========================================
    # PAGE 6: PIT-38 HELPER
    # ==========================================
    elements.append(Paragraph(f"PIT-38 Helper Data ({year})", h2_style))
    elements.append(Spacer(1, 10))

    # --- SECTION C (STOCKS) ---
    # Centered Title using h3_style
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

    # --- DIVIDENDS (FOREIGN TAX) ---
    # Centered Title using h3_style
    elements.append(Paragraph("Dividends (Foreign Tax)", h3_style))
    
    # Clean Data (NO HTML tags here)
    pit_div_data = [
        ["Description", "Value (PLN)"],
        ["Gross Income", f"{div_gross:,.2f}"],
        ["Tax Paid Abroad (Max deductible)", f"{div_tax:,.2f}"],
        ["TO PAY (Difference) [Pos 45]", f"{polish_tax_due:,.2f}"] 
    ]
    
    t_pit_div = Table(pit_div_data, colWidths=[250, 150])
    
    ts_pit_div = get_zebra_style(len(pit_div_data))
    ts_pit_div.add('ALIGN', (1,1), (-1,-1), 'RIGHT')
    
    # Bold the last row explicitly using TableStyle
    # (0, -1) = first column, last row
    # (-1, -1) = last column, last row
    ts_pit_div.add('FONTNAME', (0,-1), (-1,-1), 'Helvetica-Bold') 
    
    t_pit_div.setStyle(ts_pit_div)
    elements.append(t_pit_div)

    doc.build(elements)
