# src/db_connector.py

import sqlite3
from typing import List, Dict, Any, Optional
from decouple import config

class DBConnector:
    """
    Manages the connection to the SQLCipher encrypted SQLite database.
    Handles connection setup, key management, and data retrieval with filtering.
    """
    def __init__(self):
        # Configuration is loaded from .env via python-decouple
        self.db_path = config('DATABASE_PATH', default='data/ibkr_history.db')
        self.db_key = config('SQLCIPHER_KEY', default='')
        self.conn: Optional[sqlite3.Connection] = None

        if not self.db_key:
            raise ValueError("SQLCIPHER_KEY is not set in the .env file. Cannot connect to encrypted database.")

    def __enter__(self):
        """Opens the encrypted database connection."""
        try:
            # NOTE: We use the standard sqlite3 interface, assuming the underlying
            # environment (like pysqlcipher3) handles the encryption settings via PRAGMA.
            self.conn = sqlite3.connect(self.db_path)
            self.conn.row_factory = sqlite3.Row  # Allows accessing columns by name
            
            # Execute PRAGMA key to set the decryption key for SQLCipher
            # SECURITY NOTE: Using PRAGMA key is secure over a local connection.
            self.conn.execute(f"PRAGMA key='{self.db_key}';")
            
            # Test connection and key validity (e.g., by checking a table)
            # self.conn.execute("SELECT count(*) FROM trades;").fetchone()
            
            print(f"INFO: Successfully connected to encrypted DB: {self.db_path}")
            return self

        except Exception as e:
            print(f"ERROR: Failed to open SQLCipher connection. Check key and path. {e}")
            self.conn = None
            raise

    def __exit__(self, exc_type, exc_val, exc_tb):
        """Closes the database connection."""
        if self.conn:
            self.conn.close()

    def get_trades_for_calculation(self, target_year: int, ticker: Optional[str]) -> List[Dict[str, Any]]:
        """
        Retrieves all trades (buys, sales, dividends) relevant to the calculation.
        Filters data based on the target year and optional ticker.
        
        Args:
            target_year: The year used to determine which sales and dividends to include.
            ticker: Optional ticker to filter trades.

        Returns:
            A list of trade records as dictionaries.
        """
        if not self.conn:
            return []

        # We assume the database has a consolidated 'transactions' table
        query_parts = ["SELECT * FROM transactions WHERE 1=1"]
        params = {}
        
        # 1. Filter by Target Year (Sales/Dividends that occurred in that year)
        # We need all prior BUYS too, so we only filter the event date.
        
        start_date = f"{target_year}-01-01"
        end_date = f"{target_year}-12-31"
        
        # NOTE: This complex query simplifies filtering: it includes all Buys (Type='BUY') 
        # and all other events (Sales, Divs) that fall within the target year.
        query_parts.append(
            f"AND (Type='BUY' OR Date BETWEEN :start_date AND :end_date)"
        )
        params['start_date'] = start_date
        params['end_date'] = end_date

        # 2. Filter by Ticker (if specified)
        if ticker:
            query_parts.append("AND Ticker = :ticker")
            params['ticker'] = ticker

        query = " ".join(query_parts) + " ORDER BY Date ASC, TradeId ASC;"
        
        cursor = self.conn.execute(query, params)
        
        # Convert sqlite3.Row objects to standard dictionaries for processing
        return [dict(row) for row in cursor.fetchall()]

# Example usage (simulated)
# if __name__ == "__main__":
#     try:
#         with DBConnector() as db:
#             trades = db.get_trades_for_calculation(target_year=2024, ticker='AAPL')
#             print(f"Loaded {len(trades)} records.")
#     except Exception:
#         print("Connection failed.")