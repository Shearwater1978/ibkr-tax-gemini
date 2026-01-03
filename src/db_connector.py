# src/db_connector.py

import sqlite3
import os
import sys
from decouple import config

# --- DATABASE CONFIGURATION ---
# Using python-decouple to ensure .env is read correctly
DB_PATH = config("DATABASE_PATH", default="db/ibkr_history.db.enc")
DB_KEY = config("SQLCIPHER_KEY", default=None)

class DBConnector:
    def __init__(self, db_path=None):
        self.db_path = db_path if db_path else DB_PATH
        self.conn = None

    def __enter__(self):
        """Allows the use of 'with DBConnector() as db:'"""
        self.connect()
        return self

    def __exit__(self, exc_type, exc_val, exc_tb):
        """Ensures the connection is closed after the 'with' block"""
        self.close()

    def connect(self):
        # Ensure the database directory exists
        os.makedirs(os.path.dirname(self.db_path), exist_ok=True)
        
        # Critical security check: the key MUST be present
        if not DB_KEY:
            print("FATAL ERROR: SQLCIPHER_KEY not found in environment!")
            sys.exit(1)

        try:
            self.conn = sqlite3.connect(self.db_path)
            self.conn.row_factory = sqlite3.Row
            
            # Apply the encryption key IMMEDIATELY after connecting.
            # This is mandatory for SQLCipher to encrypt the file correctly.
            self.conn.execute(f"PRAGMA key = '{DB_KEY}';")
            
            # Verification: attempt to read the master table.
            # If the database was created as plaintext, this will fail 
            # because SQLCipher expects an encrypted header.
            self.conn.execute("SELECT count(*) FROM sqlite_master;")
            
        except sqlite3.DatabaseError as e:
            print(f"FATAL ERROR: Encryption/Key error. Details: {e}")
            self.close()
            sys.exit(1)
        except Exception as e:
            print(f"FATAL ERROR: Connection failed. {e}")
            sys.exit(1)

    def change_password(self, new_password: str) -> bool:
        """Changes the encryption key (Rekey) for the existing database."""
        if not self.conn:
            return False
        try:
            self.conn.execute(f"PRAGMA rekey = '{new_password}';")
            self.conn.execute("VACUUM;")
            return True
        except Exception as e:
            print(f"ERROR: Could not change password: {e}")
            return False

    def close(self):
        if self.conn:
            self.conn.close()

    def initialize_schema(self):
        """Creates the transactions table if it doesn't exist."""
        query = """
        CREATE TABLE IF NOT EXISTS transactions (
            id INTEGER PRIMARY KEY AUTOINCREMENT,
            Date TEXT,
            EventType TEXT,
            Ticker TEXT,
            Quantity REAL,
            Price REAL,
            Currency TEXT,
            Amount REAL,
            Fee REAL,
            Description TEXT
        );
        """
        self.conn.execute(query)
        self.conn.commit()

    def save_transaction(self, data):
        """Saves a single transaction record to the database."""
        query = """
            INSERT INTO transactions 
            (Date, EventType, Ticker, Quantity, Price, Currency, Amount, Fee, Description)
            VALUES (?, ?, ?, ?, ?, ?, ?, ?, ?)
        """
        self.conn.execute(
            query,
            (
                data["date"], data["type"], data["ticker"],
                data["qty"], data["price"], data["currency"],
                data.get("amount", 0), data["fee"], data["desc"],
            ),
        )
        self.conn.commit()

    def get_trades_for_calculation(self, target_year=None, ticker=None):
        """
        Fetches transactions for FIFO and tax reporting with explicit columns.
        Explicit listing is required for test compliance and clarity.
        """
        query = """
            SELECT 
                rowid as TradeId, 
                Date, 
                EventType, 
                Ticker, 
                Quantity, 
                Price, 
                Currency, 
                Amount, 
                Fee, 
                Description 
            FROM transactions 
            WHERE 1=1
        """
        params = []
        
        if ticker:
            query += " AND Ticker = ?"
            params.append(ticker)
        
        if target_year:
            query += " AND Date <= ?"
            params.append(f"{target_year}-12-31")
            
        query += " ORDER BY Date ASC"
        
        cursor = self.conn.execute(query, params)
        return [dict(row) for row in cursor.fetchall()]