import os

try:
    from sqlcipher3 import dbapi2 as sqlcipher
except ImportError:
    sqlcipher = None

try:
    from decouple import config
except ImportError:

    def config(name, default=None):
        return os.getenv(name, default)


DB_PATH = config("DATABASE_PATH", default="db/ibkr_history.db.enc")
DB_KEY = config("SQLCIPHER_KEY", default=None)


class DBConnectorError(RuntimeError):
    """Raised when an encrypted database cannot be opened safely."""


class DBConnector:
    def __init__(self, db_path=None, key=None):
        self.db_path = db_path if db_path else DB_PATH
        self.key = DB_KEY if key is None else key
        self.conn = None

    def __enter__(self):
        self.connect()
        return self

    def __exit__(self, exc_type, exc_val, exc_tb):
        self.close()

    def connect(self):
        if sqlcipher is None:
            raise DBConnectorError(
                "SQLCipher driver is unavailable. Install dependencies from requirements.txt."
            )
        if not self.key:
            raise DBConnectorError(
                "SQLCIPHER_KEY is required; refusing to open a plaintext database."
            )

        db_dir = os.path.dirname(os.path.abspath(self.db_path))
        os.makedirs(db_dir, exist_ok=True)

        try:
            self.conn = sqlcipher.connect(self.db_path)
            self._set_pragma_key("key", self.key)
            cipher_version = self.conn.execute("PRAGMA cipher_version").fetchone()
            if not cipher_version or not cipher_version[0]:
                raise DBConnectorError(
                    "SQLCipher is not active; refusing to use an unencrypted database."
                )
            self.conn.execute("SELECT count(*) FROM sqlite_master").fetchone()
            self.conn.row_factory = sqlcipher.Row
        except DBConnectorError:
            self.close()
            raise
        except Exception as exc:
            self.close()
            raise DBConnectorError(
                "Could not open the encrypted database with the configured key."
            ) from exc

    def change_password(self, new_password: str) -> bool:
        if not self.conn:
            print("ERROR: Database not connected. Run connect() first.")
            return False
        if not new_password:
            print("ERROR: New database password cannot be empty.")
            return False
        try:
            self._set_pragma_key("rekey", new_password)
            self.conn.execute("VACUUM")
            self.conn.execute("SELECT count(*) FROM sqlite_master").fetchone()
            print("SUCCESS: Database password successfully changed.")
            return True
        except Exception as exc:
            print(f"ERROR changing password: {exc}")
            return False

    def _set_pragma_key(self, pragma_name: str, password: str):
        """Apply a SQLCipher key using an escaped SQL literal."""
        escaped = password.replace("'", "''")
        self.conn.execute(f"PRAGMA {pragma_name} = '{escaped}'")

    def close(self):
        if self.conn:
            self.conn.close()
            self.conn = None

    def initialize_schema(self):
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
        query = """
            INSERT INTO transactions
            (Date, EventType, Ticker, Quantity, Price, Currency, Amount, Fee, Description)
            VALUES (?, ?, ?, ?, ?, ?, ?, ?, ?)
        """
        self.conn.execute(
            query,
            (
                data["date"],
                data["type"],
                data["ticker"],
                data["qty"],
                data["price"],
                data["currency"],
                data.get("amount", 0),
                data["fee"],
                data["desc"],
            ),
        )
        self.conn.commit()

    def get_trades_for_calculation(self, target_year=None, ticker=None):
        query = """
            SELECT rowid as TradeId, Date, EventType, Ticker, Quantity,
                   Price, Currency, Amount, Fee, Description
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
