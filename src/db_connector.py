# src/db_connector.py
import sqlite3
import os
import sys

# Trying to import dotenv to read .env file
try:
    from dotenv import load_dotenv
    load_dotenv()  # Loading variables from .env
except ImportError:
    # If there is no library, the script will continue to run, but the environment variables must be set differently
    pass

# --- DATABASE CONFIGURATION ---
# We read the path and key from .env.
# If no variables are specified in .env, the default values ​​(or None) are used.
DB_PATH = os.getenv("DATABASE_PATH", "db/ibkr_history.db.enc")
DB_KEY = os.getenv("SQLCIPHER_KEY")

class DBConnector:
    def __init__(self, db_path=None):
        # If the path is passed explicitly during initialization, we use it, otherwise we take it from constants/.env
        self.db_path = db_path if db_path else DB_PATH
        self.conn = None

    def __enter__(self):
        self.connect()
        return self

    def __exit__(self, exc_type, exc_val, exc_tb):
        self.close()

    def connect(self):
        # Create a folder for the database if it doesn’t exist
        os.makedirs(os.path.dirname(self.db_path), exist_ok=True)
        
        try:
            self.conn = sqlite3.connect(self.db_path)
            
            # --- SQLCIPHER LOGIC ---
            if DB_KEY:
                # If the key is in the environment variables, use it.
                self.conn.execute(f"PRAGMA key = '{DB_KEY}';")
                
                # Checking the key: trying to execute a light command.
                # If the key is invalid, an exception will be thrown here.
                try:
                    self.conn.execute("SELECT count(*) FROM sqlite_master;")
                except sqlite3.DatabaseError:
                    print("ОШИБКА: Неверный ключ шифрования или база данных повреждена.")
                    sys.exit(1)
            
            # Using sqlite3.Row to access columns by name
            self.conn.row_factory = sqlite3.Row
            
        except Exception as e:
            print(f"FATAL ERROR: Could not connect to database. {e}")
            sys.exit(1)

    def change_password(self, new_password: str) -> bool:
        """
        Changes the encryption key of the current database (Rekey).
        The database must already be open with the old password.
        """
        if not self.conn:
            print("ERROR: Database not connected. Run connect() first.")
            return False

        try:
            # SQLCipher command to change the key
            self.conn.execute(f"PRAGMA rekey = '{new_password}';")
            # Vacuum to force rewrite of all pages with the new key
            self.conn.execute("VACUUM;")
            print("SUCCESS: Database password successfully changed.")
            return True
        except Exception as e:
            print(f"ERROR changing password: {e}")
            return False

    def close(self):
        if self.conn:
            self.conn.close()

    def initialize_schema(self):
        """Создает таблицу транзакций, если она не существует."""
        # Columns are named in PascalCase (Date, EventType...) as in the historical schema
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

    def get_trades_for_calculation(self, target_year=None, ticker=None):
        """
        Загружает сделки для FIFO.
        Использует rowid как TradeId, чтобы гарантировать наличие ID даже в старых БД.
        Возвращает имена колонок как есть (PascalCase), чтобы удовлетворить processing.py.
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

        # Filter by ticker
        if ticker:
            query += " AND Ticker = ?"
            params.append(ticker)

        # Filter by date: everything up to and including the end of the target year
        if target_year:
            end_date = f"{target_year}-12-31"
            query += " AND Date <= ?"
            params.append(end_date)

        query += " ORDER BY Date ASC"

        cursor = self.conn.execute(query, params)
        rows = cursor.fetchall()
        
        # Converting sqlite3.Row to regular dictionaries
        return [dict(row) for row in rows]

    def save_transaction(self, data):
        """Вспомогательный метод для ручного сохранения транзакции."""
        query = """
            INSERT INTO transactions 
            (Date, EventType, Ticker, Quantity, Price, Currency, Amount, Fee, Description)
            VALUES (?, ?, ?, ?, ?, ?, ?, ?, ?)
        """
        self.conn.execute(query, (
            data['date'], data['type'], data['ticker'], 
            data['qty'], data['price'], data['currency'], 
            data['amount'], data['fee'], data['desc']
        ))
        self.conn.commit()
