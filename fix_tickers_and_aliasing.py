# fix_tickers_and_aliasing.py

import os

def apply_fixes():
    """
    Fixes ticker extraction for comma-separated symbols (TTE, TOT)
    and adds ticker normalization (aliasing) to the processing pipeline.
    """

    # --- 1. Fix src/parser.py (extract_ticker function) ---
    parser_path = "src/parser.py"
    if os.path.exists(parser_path):
        with open(parser_path, "r", encoding="utf-8") as f:
            lines = f.readlines()

        new_lines = []
        for line in lines:
            # Fixing the fallback logic to handle comma-separated tickers like "TTE, TOT"
            if 'return symbol_col.strip()' in line:
                indent = line[:line.find('return')]
                new_lines.append(f"{indent}# Handle comma-separated aliases (e.g., 'TTE, TOT')\n")
                new_lines.append(f"{indent}clean_sym = symbol_col.strip().split(',')[0].strip()\n")
                new_lines.append(f"{indent}return clean_sym.split()[0]  # Take first word\n")
            else:
                new_lines.append(line)

        with open(parser_path, "w", encoding="utf-8") as f:
            f.writelines(new_lines)
        print(f"SUCCESS: Updated {parser_path} to handle ticker aliases in CSV.")

    # --- 2. Fix src/processing.py (ticker normalization mapping) ---
    proc_path = "src/processing.py"
    if os.path.exists(proc_path):
        with open(proc_path, "r", encoding="utf-8") as f:
            lines = f.readlines()

        new_lines = []
        mapping_added = False
        for line in lines:
            # Injecting the Ticker Mapping table
            if "def process_yearly_data" in line and not mapping_added:
                new_lines.append(line)
                new_lines.append("    # Ticker Aliases Mapping (Normalization)\n")
                new_lines.append("    TICKER_MAP = {\n")
                new_lines.append("        'TOT': 'TTE',   # TotalEnergies old ticker\n")
                new_lines.append("        'FB': 'META',   # Facebook old ticker\n")
                new_lines.append("    }\n\n")
                mapping_added = True
            # Normalizing ticker inside the loop
            elif "ticker = trade['Ticker']" in line:
                indent = line[:line.find('ticker')]
                new_lines.append(line)
                new_lines.append(f"{indent}# Apply normalization (e.g., TOT -> TTE)\n")
                new_lines.append(f"{indent}ticker = TICKER_MAP.get(ticker, ticker)\n")
            else:
                new_lines.append(line)

        with open(proc_path, "w", encoding="utf-8") as f:
            f.writelines(new_lines)
        print(f"SUCCESS: Updated {proc_path} with Ticker Normalization (TOT -> TTE).")

if __name__ == "__main__":
    apply_fixes()
