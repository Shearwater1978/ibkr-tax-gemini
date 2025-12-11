# Technical Specification: IBKR Tax Assistant (v1.5.0)

## 1. System Architecture

The application follows a modular architecture separating Data Ingestion, Storage, Processing, and Reporting.

```text
[IBKR CSV] -> [Parser] -> [SQLCipher DB] -> [Processing Core] -> [Reporters] -> [PDF/XLSX]
                                  ^                  |
                                  |                  v
                             (.env Key)        [FIFO Engine] <-> [NBP Client]
```

## 2. Modules Description

### 2.1. Security & Storage (`src/db_connector.py`)
* **Engine:** SQLite with SQLCipher extension (AES-256 encryption).
* **Schema:** Single consolidated table `transactions`.
* **Access:** Managed via Context Manager (`with DBConnector() as db:`).
* **Key Management:** Decoupled via `.env` file.

### 2.2. Data Ingestion (`src/parser.py`)
* **Strategy:** "Dynamic Header Mapping". The parser reads the CSV header row to determine column indices, making it robust against IBKR format changes.
* **Supported Formats:**
    * *Activity Statement* (Default, Multi-section CSV).
    * *Flex Query* (Custom XML/CSV).
* **Normalization:** Converts dates (`MM/DD/YYYY`, `YYYYMMDD`) to ISO `YYYY-MM-DD`. Filters out "Total" rows and metadata.

### 2.3. FX Conversion (`src/nbp.py`)
* **Logic:** Uses the "Day Before" (T-1) rule mandated by Polish law.
* **Optimization:** **Batch Caching**. Instead of making 1 API call per trade, it fetches the entire month's rates in 1 call and caches them in memory.
* **Fallback:** If T-1 is a holiday, it iterates backwards (T-2, T-3...) up to 10 days using local cache lookup.

### 2.4. Core Logic (`src/processing.py` & `src/fifo.py`)
* **FIFO Engine:** Matches SELL operations against the oldest available BUY lots.
* **Dividend Linking:** Automatically maps Withholding Tax records (negative values in CSV) to their corresponding Dividend records based on Date and Ticker.
* **Sanctions Check:** Cross-references assets against a hardcoded list of restricted tickers (e.g., SBER, YNDX) to flag them in reports.

## 3. Database Schema

**Table:** `transactions`

| Column | Type | Description |
| :--- | :--- | :--- |
| `TradeId` | INTEGER PK | Auto-increment ID. |
| `Date` | TEXT | ISO Date `YYYY-MM-DD`. |
| `EventType` | TEXT | `BUY`, `SELL`, `DIVIDEND`, `TAX`, `TRANSFER`. |
| `Ticker` | TEXT | Stock Symbol (e.g., `AAPL`). |
| `Quantity` | REAL | Number of shares. |
| `Price` | REAL | Price per share (original currency). |
| `Currency` | TEXT | `USD`, `EUR`, etc. |
| `Amount` | REAL | Total value (`Price * Qty`). |
| `Fee` | REAL | Commission paid. |

## 4. Future Roadmap (Sprint 4)
* [ ] **GUI:** Desktop interface using CustomTkinter.
* [ ] **Secure Login:** Runtime password input (removing key from .env).
* [ ] **Corporate Actions Wizard:** UI for handling Spin-offs and Mergers manually.