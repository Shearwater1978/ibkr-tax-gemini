import argparse
import getpass
import logging
import sys
import os
from src.db_manager import set_db_password, get_connection, fetch_available_years
from src.lock_unlock import unlock_db, lock_db
from src.ingest import ingest_command
from main import generate_report_command

logging.basicConfig(level=logging.INFO, format='%(levelname)s: %(message)s')

def initialize_security(args):
    # Handles password input, unlocking the DB, and setting the global password.
    print("🔒 Iron Bank CLI Security")
    
    password = getpass.getpass("Enter Database Password: ")
    set_db_password(password)
    
    # Attempt decryption (unlocking)
    if not unlock_db(password):
        print("Exiting: Decryption failed. Check your password or run initial lock.")
        sys.exit(1)
        
    # Check if the plaintext DB is accessible (first run check)
    try:
        con = get_connection()
        con.close()
    except FileNotFoundError:
        print("🚨 Error: Database file not found. Run 'python src/lock_unlock.py' first.")
        sys.exit(1)
    except Exception as e:
        print(f"Database access failed after unlock: {e}")
        sys.exit(1)
        
    return password

def run_report_command(args):
    # Executes the report generation for the specified year.
    if not args.year:
        print("❌ Error: Report year must be specified (e.g., report 2024).")
        
        available_years = fetch_available_years()
        if available_years:
            print(f"   Available years in DB: {', '.join(available_years)}")
        
        sys.exit(1)
        
    # Validation
    available_years = fetch_available_years()
    if args.year not in available_years:
        print(f"⚠️ Warning: No trade data found for year {args.year}.")
        print(f"   Available years in DB: {', '.join(available_years) or 'None'}")
        
    generate_report_command(args.year)

def run_ingest_command(args):
    # Executes the data ingestion command.
    ingest_command()

def main():
    parser = argparse.ArgumentParser(
        description="IBKR Tax Assistant CLI (Iron Bank Edition).",
        formatter_class=argparse.RawTextHelpFormatter
    )

    # Global security initializer will run first
    password = initialize_security(None) 
    
    # Subcommands
    subparsers = parser.add_subparsers(title='Available Commands', dest='command')

    # 1. REPORT Command
    report_parser = subparsers.add_parser('report', help='Generate tax report for a specific year.')
    report_parser.add_argument('year', type=str, nargs='?', help='The target tax year (e.g., 2024).')
    report_parser.set_defaults(func=run_report_command)
    
    # 2. INGEST Command
    ingest_parser = subparsers.add_parser('ingest', help='Process and load new CSV files from the data/ folder.')
    ingest_parser.set_defaults(func=run_ingest_command)

    args = parser.parse_args()
    
    if 'func' in args:
        args.func(args)
    elif not args.command:
        parser.print_help()
    
    # Final step: Lock the database
    lock_db(password)
    logging.info("ALL DONE! Database locked.")

if __name__ == "__main__":
    main()
