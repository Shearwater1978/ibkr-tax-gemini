import os
import json
import logging
from decimal import Decimal
from src.processing import TaxCalculator
from src.report_pdf import generate_pdf

logging.basicConfig(level=logging.INFO, format='%(levelname)s: %(message)s')

class DecimalEncoder(json.JSONEncoder):
    def default(self, o):
        if isinstance(o, Decimal): return float(o)
        return super(DecimalEncoder, self).default(o)

def generate_report_command(year):
    OUTPUT_DIR = "output"
    os.makedirs(OUTPUT_DIR, exist_ok=True)
    
    logging.info(f"🚀 Processing Report for {year} (Source: Decrypted DB)")
    
    try:
        calc = TaxCalculator(target_year=year)
        calc.run_calculations()
        
        final_report = calc.get_results()
        
        data = final_report['data']
        has_data = (data['dividends'] or 
                    data['capital_gains'] or 
                    data['holdings'] or
                    data['trades_history'])
        
        if not has_data:
            logging.info(f"--> Year {year} empty. Skipping report generation.")
            return

        json_path = os.path.join(OUTPUT_DIR, f"tax_report_{year}.json")
        with open(json_path, "w", encoding="utf-8") as f:
            json.dump(final_report, f, indent=4, cls=DecimalEncoder)
        
        pdf_path = os.path.join(OUTPUT_DIR, f"tax_report_{year}.pdf")
        generate_pdf(final_report, pdf_path)
        logging.info(f"✅ Report ready: {pdf_path}")
        
    except Exception as e:
         logging.error(f"Failed processing {year}: {e}")

if __name__ == "__main__":
    print("Use 'python tax_cli.py report <year>'")
