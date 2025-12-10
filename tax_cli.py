# tax_cli.py (Восстановленная обертка)

import sys
import argparse
import subprocess
import os

# NOTE: We no longer import anything from main.py. We run main.py as a subprocess.

def main_cli():
    """
    Command Line Interface wrapper to emulate the old tax_cli.py interface 
    by executing main.py with the correct arguments.
    """
    parser = argparse.ArgumentParser(description="Tax CLI for broker reports.")
    subparsers = parser.add_subparsers(dest="command", required=True)

    # 1. REPORT Command (Maps to main.py --target-year <YEAR> --export-excel)
    report_parser = subparsers.add_parser('report', help='Generate tax report for a specific year.')
    report_parser.add_argument('year', type=int, help='The target tax year (e.g., 2024).')
    
    # 2. IMPORT Command (Maps to src.parser.py --files <PATH>) - Optional for future sprints
    import_parser = subparsers.add_parser('import', help='Import and parse raw broker data.')
    import_parser.add_argument('path', type=str, help='Path to the data folder or file.')


    args = parser.parse_args()
    
    # --- Execute Logic ---
    
    if args.command == 'report':
        target_year = args.year
        
        # Construct the command to execute main.py
        # We assume main.py is in the current working directory
        main_command = [
            sys.executable,  # python interpreter
            'main.py',       # main script
            f'--target-year={target_year}',
            f'--export-excel' # Always export Excel for the 'report' command
        ]
        
        print(f"Executing: {' '.join(main_command)}")
        try:
            # Execute main.py
            subprocess.run(main_command, check=True)
        except subprocess.CalledProcessError as e:
            print(f"ERROR: Report generation failed. Main script exited with code {e.returncode}.")
            sys.exit(e.returncode)
            
    elif args.command == 'import':
        # Example for the 'import' command
        parser_command = [
            sys.executable,
            '-m', 'src.parser', # Execute parser as a module
            '--files', args.path
        ]
        
        print(f"Executing: {' '.join(parser_command)}")
        try:
            subprocess.run(parser_command, check=True)
        except subprocess.CalledProcessError as e:
            print(f"ERROR: Data import failed. Parser exited with code {e.returncode}.")
            sys.exit(e.returncode)

if __name__ == "__main__":
    main_cli()