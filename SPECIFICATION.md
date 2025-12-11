# Technical Specification: IBKR Tax Assistant (v2.1.0)

## 1. System Architecture
Modular architecture separating Data Ingestion, Encryption, Logic, and Reporting.

```text
[IBKR CSV] -> [Universal Parser] -> [SQLCipher DB] -> [FIFO Core] -> [Reporters]
```

## 2. Key Modules (v2.1.0)

### 2.1. Security (`src/db_connector.py`)
* **SQLCipher:** AES-256 encryption at rest.
* **Decoupled Key:** Managed via `.env`.

### 2.2. Ingestion (`src/parser.py`)
* **Dynamic Mapping:** Adapts to Activity Statements and Flex Queries.
* **Normalization:** Unifies date formats (`MM/DD/YYYY` -> ISO).
* **Sanitization:** Filters metadata and total rows.

### 2.3. FX Engine (`src/nbp.py`)
* **Algorithm:** Smart Batch Caching. Fetches monthly chunks to minimize API calls (12 calls/year).
* **Compliance:** Implements strict T-1 (business day lookback) rule.

### 2.4. Core Logic
* **FIFO:** Queue-based matching (First-In-First-Out).
* **Tax Linking:** Associates Withholding Tax records with Dividends.

## 3. Database Schema
Single source of truth: `transactions` table (TradeId, Date, EventType, Ticker, Quantity, Price, Amount, Fee).