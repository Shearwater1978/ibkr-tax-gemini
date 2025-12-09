# Wiki / Glossary

## Modules

* **`main.py`**: The CLI orchestrator. Handles arguments and dependency injection. Contains the `prepare_data_for_pdf` adapter.
* **`src.db_connector`**: Context manager for database connections. Handles `PRAGMA key` for encryption.
* **`src.fifo`**: Contains the `TradeMatcher` class. Implements the core accounting logic.
* **`src.parser`**: Handles the dirty work of parsing IBKR's specific CSV format.
* **`src.processing`**: The business logic layer. It prepares data for the FIFO engine and handles Dividend/Tax linking.

## Business Logic Concepts

* **Row-Level Flattening**: For Excel exports, every Sell transaction is "exploded" into multiple rows.
* **Aggregation**: For PDF reports, Inventory is aggregated by Ticker to show a clean portfolio view.
* **Sanctions Flagging**: The system maintains a list of restricted tickers and highlights them in the portfolio report.