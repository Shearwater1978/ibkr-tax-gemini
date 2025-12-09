# IBKR Tax Calculator 📊

This project provides a comprehensive tool for calculating capital gains and dividends according to FIFO rules, handling currency conversions (PLN) using the National Bank of Poland (NBP) API, and ensuring data security with SQLCipher encryption.

---

## 🚀 1. Setup and Installation

### Dependencies
The project uses standard Python libraries, a secure database solution, and Pytest for validation.

* **requests** (API calls)
* **python-decouple** (Environment variable management)
* **SQLCipher & cryptography** (Database encryption)

### Local Environment Setup

1.  **Clone the repository:**
    ```bash
    git clone [your-repo-link]
    cd ibkr-tax-calculator
    ```
2.  **Install dependencies:**
    ```bash
    pip install -r requirements.txt
    ```

---

## 🔑 2. Configuration and Security

The project requires sensitive configurations to be stored in a **`.env`** file in the root directory.

### `.env` Example

Create a file named `.env` and configure the following:

```dotenv
# --- General Settings ---
TARGET_YEAR=2024
DATABASE_PATH=data/ibkr_history.db

# --- Security: Database Encryption Key ---
# This key is mandatory for initializing and unlocking the SQLCipher database.
# MUST BE KEPT CONFIDENTIAL.
# You can generate a new key using the lock_unlock module.
SQLCIPHER_KEY='[YOUR_32_BYTE_FERNET_KEY]' 
```

### Database Security (SQLCipher)

The H2 database solution has been replaced by **SQLCipher**. This ensures that all sensitive financial history data is stored with **AES-256 encryption** at rest. The `src/lock_unlock.py` module manages the generation and handling of the `SQLCIPHER_KEY`.

---

## ⚙️ 3. Usage: Generating the Report

The core logic is executed via the `TaxCalculator` class, which handles data retrieval, FIFO matching, currency conversion, and final report generation.

### Step 1: Initialize/Unlock the Database
Before running the main calculations, ensure your database is unlocked or initialized using the provided key in `.env`.

### Step 2: Run Calculations

Execute the main processing script (assuming your entry point is `main.py` or similar):

```bash
python main.py
```
*(Note: Replace `main.py` with your project's primary execution file, e.g., `python src/processing.py`)*

### Output
The system generates a report (e.g., PDF, CSV, or JSON) containing:
* Realized **Capital Gains** (P&L for sales matched via FIFO).
* **Dividends** and Withholding Taxes paid.
* **Year-end Inventory** (unmatched buy batches).

---

## 💸 4. Exchange Rate Logic

The system strictly follows NBP rules for currency conversion to PLN.

* **Source:** National Bank of Poland (NBP) API.
* **Holiday/Weekend Handling:** If the NBP API returns no rate for the event date, the system automatically performs a **recursive lookup** for the rate on the preceding working day.
* **Caching:** An aggressive memory cache and disk cache are used to prevent redundant API calls for historical data.

---

## 🧪 5. Testing Environment

**WARNING:** All critical integration and unit tests are designed to run in the **CI/CD environment** (GitHub Actions). Local execution may lead to ambiguous environmental errors (e.g., `RecursionError`, `AssertionError`) due to external factors (like local mocking behavior or dependency conflicts).

To validate the code logic, rely on the **CI Pipeline**:

```bash
# Command used in CI/CD pipeline
pytest
```
