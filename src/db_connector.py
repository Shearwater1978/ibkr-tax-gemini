import sqlite3
from typing import List, Dict, Any, Optional
from decouple import config

class DBConnector:
    """
    Manages the connection to the SQLCipher encrypted SQLite database.
    Handles connection setup, key management, and data retrieval.
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
            self.conn.execute(f"PRAGMA key='{self.db_key}';")
            
            # print(f"INFO: Successfully connected to encrypted DB: {self.db_path}")
            return self

        except Exception as e:
            print(f"ERROR: Failed to open SQLCipher connection. Check key and path. {e}")
            self.conn = None
            raise

    def initialize_schema(self):
        """Creates the necessary database tables if they do not exist (e.g., 'transactions')."""
        if not self.conn:
            raise ConnectionError("Database connection is not open. Cannot initialize schema.")
            
        # Define the schema for the consolidated transactions table
        create_table_query = """
        CREATE TABLE IF NOT EXISTS transactions (
            TradeId INTEGER PRIMARY KEY,
            Date TEXT NOT NULL,
            EventType TEXT NOT NULL, -- e.g., 'BUY', 'SELL', 'DIVIDEND', 'MERGER', 'SPINOFF'
            Ticker TEXT NOT NULL,
            Quantity REAL,
            Price REAL,
            Currency TEXT,
            Amount REAL,
            Fee REAL,
            Description TEXT
        );
        """
        try:
            self.conn.execute(create_table_query)
            # Index for performance
            self.conn.execute("CREATE INDEX IF NOT EXISTS idx_date_ticker ON transactions(Date, Ticker);")
            self.conn.commit()
            print("INFO: Database schema (transactions table) initialized successfully.")
        except Exception as e:
            print(f"ERROR: Failed to initialize schema. {e}")
            raise

    def __exit__(self, exc_type, exc_val, exc_tb):
        """Closes the database connection."""
        if self.conn:
            self.conn.close()

    def get_trades_for_calculation(self, target_year: int, ticker: Optional[str]) -> List[Dict[str, Any]]:
        """
        Retrieves ALL trades (buys, sales, dividends, corporate actions) from the entire history.
        
        CRITICAL FIX:
        We intentionally IGNORE 'target_year' in the SQL WHERE clause.
        To calculate the correct Inventory (Cost Basis) and Realized Gains for the current year,
        the FIFO engine must replay the ENTIRE history of transactions (Sales, Mergers, Spinoffs)
        from previous years. 
        
        Filtering for the final report happens later in the processing layer.

        Args:
            target_year: Not used in SQL query anymore (see explanation above).
            ticker: Optional ticker to filter trades.

        Returns:
            A list of trade records as dictionaries.
        """
        if not self.conn:
            return []

        # Start building the query
        query = "SELECT * FROM transactions WHERE 1=1"
        params = {}
        
        # 1. Filter by Ticker (if specified)
        if ticker:
            query += " AND Ticker = :ticker"
            params['ticker'] = ticker

        # 2. Sort Order
        # Chronological order is essential for FIFO
        query += " ORDER BY Date ASC, TradeId ASC;"
        
        cursor = self.conn.execute(query, params)
        
        # Convert sqlite3.Row objects to standard dictionaries for processing
        return [dict(row) for row in cursor.fetchall()]