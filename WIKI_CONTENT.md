# User Manual

## 1. How to use Snapshots (Archiving History)
As years go by, parsing 5-10 years of CSV history every time becomes slow and messy. Use Snapshots to "freeze" your inventory state.

### Steps:
1.  Ensure all historical CSVs (e.g., 2021-2024) are currently in `data/` folder.
2.  Run the snapshot tool:
```bash
python create_snapshot.py
```
3.  Enter the **Cutoff Year** (e.g., `2024`).
    * This means: "Calculate everything up to Dec 31, 2024, and save the remaining stocks to JSON."
4.  A file `snapshot_2024.json` is created.
5.  **Cleanup:** You can now delete CSV files for 2021, 2022, 2023, and 2024. Keep only the 2025 CSV.
6.  **Run:** When you run `python main.py`, it detects you are calculating 2025, finds `snapshot_2024.json`, loads it, and processes only the new 2025 trades.

## 2. Reading the PDF Report

### FIFO Check Column
In the "Portfolio Composition" table, you will see a column **FIFO Check**.
* **OK**: The quantity calculated by our FIFO engine matches exactly the quantity reported by the Broker. You are safe.
* **MISMATCH**: There is a discrepancy. Check console logs for details. Usually caused by missing history files.

### Red Highlights (Sanctions/Blocked)
Assets denominated in **RUB** or known to be sanctioned/blocked are highlighted with a **RED background** in the holdings table. Check if these need special tax treatment (e.g. "zbycie" might not be possible).

## 3. Supported Special Cases
* **KVUE (Kenvue) Spin-off/Exchange (2023):** handled automatically via specific date-fix logic.
* **GE (General Electric) Split (2021):** handled with deduplication logic.
* **WBD/OGN:** Standard spin-offs are detected from transaction descriptions.

## 4. Developer Guide / AI Restoration
This project includes a special file: **`RESTART_PROMPT.md`**.

**What is it?**
It contains the full source code (cleaned and translated to English), file structure, and technical specification of the project in a single prompt.

**How to use it?**
If you want to continue development in a new chat session with an LLM (ChatGPT, Claude, Gemini):
1.  Open `RESTART_PROMPT.md`.
2.  Copy the entire content.
3.  Paste it into the AI chat.
4.  The AI will instantly "restore" the context and be ready to code.

### Data Security: SQLCipher Implementation

The H2 database was replaced by SQLCipher to meet strict data security requirements.

**SQLCipher Benefits:**
* Provides native AES-256 encryption on the entire database file, making it unreadable without the correct key.
* Seamlessly replaces the standard SQLite engine, requiring minimal changes to SQL queries.

**Key Management (`src/lock_unlock.py`):**
The encryption key is managed by the `lock_unlock` module. The module uses `cryptography.fernet` to generate and securely wrap the master database key, ensuring the key is stored separately from the database itself.
