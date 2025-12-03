import os
import json
import logging
from decimal import Decimal
from src.parser import parse_csv, parse_manual_history
from src.processing import TaxCalculator
from src.report_pdf import generate_pdf

logging.basicConfig(level=logging.INFO, format='%(levelname)s: %(message)s')

class DecimalEncoder(json.JSONEncoder):
    def default(self, o):
        if isinstance(o, Decimal): return float(o)
        return super(DecimalEncoder, self).default(o)

def get_taxable_years(all_trades, all_divs):
    years = set()
    for div in all_divs:
        if div.get('date'): years.add(div['date'][:4])
    for trade in all_trades:
        # Check both Sells (Capital Gains) and Buys (Trade History)
        if trade.get('date'):
            years.add(trade['date'][:4])
    return sorted(list(years))

def main():
    DATA_DIR = "data"
    OUTPUT_DIR = "output"
    MANUAL_FILE = "manual_history.csv"
    os.makedirs(OUTPUT_DIR, exist_ok=True)
    
    all_raw_trades = []
    all_raw_divs = []
    all_raw_taxes = []
    
    if os.path.exists(MANUAL_FILE):
        logging.info(f"Loading manual history...")
        all_raw_trades.extend(parse_manual_history(MANUAL_FILE))
    
    if os.path.exists(DATA_DIR):
        for filename in os.listdir(DATA_DIR):
            if filename.lower().endswith(".csv"):
                file_path = os.path.join(DATA_DIR, filename)
                logging.info(f"Reading file: {filename}...")
                try:
                    parsed = parse_csv(file_path)
                    all_raw_trades.extend(parsed['trades'])
                    all_raw_divs.extend(parsed['dividends'])
                    all_raw_taxes.extend(parsed['taxes'])
                except Exception as e:
                    logging.error(f"Failed to parse {filename}: {e}")

    if not all_raw_trades:
        logging.warning("No data found!")
        return

    target_years = get_taxable_years(all_raw_trades, all_raw_divs)
    logging.info(f"--> DETECTED ACTIVITY YEARS: {', '.join(target_years)}")

    for year in target_years:
        logging.info(f"Processing Year: {year}")
        calculator = TaxCalculator(target_year=year)
        calculator.ingest_preloaded_data(all_raw_trades, all_raw_divs, all_raw_taxes)
        calculator.run_calculations()
        
        final_report = calculator.get_results()
        
        # Check if there is ANY data worth printing (Holdings, Trades, Divs, Gains)
        data = final_report['data']
        has_data = (data['dividends'] or 
                    data['capital_gains'] or 
                    data['holdings'] or
                    data['trades_history'])
        
        if has_data:
            json_path = os.path.join(OUTPUT_DIR, f"tax_report_{year}.json")
            with open(json_path, "w", encoding="utf-8") as f:
                json.dump(final_report, f, indent=4, cls=DecimalEncoder)
            
            pdf_path = os.path.join(OUTPUT_DIR, f"tax_report_{year}.pdf")
            generate_pdf(final_report, pdf_path)
            logging.info(f"--> Report ready: {pdf_path}")
        else:
            logging.info(f"--> Year {year} empty. Skipping.")

    logging.info("ALL DONE!")

if __name__ == "__main__":
    main()
