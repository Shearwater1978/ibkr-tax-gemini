import os

VERSION = "v2.1.0"

def write_file(filename, lines):
    content = "\n".join(lines)
    try:
        with open(filename, 'w', encoding='utf-8') as f:
            f.write(content)
        print(f"✅ Updated {filename} to {VERSION}")
    except Exception as e:
        print(f"❌ Error writing {filename}: {e}")

# ==============================================================================
# 1. README.MD
# ==============================================================================
readme_lines = [
    f"# 🇵🇱 IBKR Tax Calculator (PIT-38 Poland) {VERSION}",
    "",
    "**Automated Capital Gains & Dividend Tax Calculator for Polish Residents using Interactive Brokers.**",
    "",
    "![Python](https://img.shields.io/badge/Python-3.10%2B-blue)",
    "![License](https://img.shields.io/badge/License-MIT-green)",
    "![Security](https://img.shields.io/badge/Security-SQLCipher%20AES--256-red)",
    f"![Version](https://img.shields.io/badge/Release-{VERSION}-orange)",
    "",
    "## 🚀 Key Features",
    "",
    "* **Privacy First:** All financial data is stored in a local **SQLCipher (AES-256)** encrypted database.",
    "* **Universal Parser:** Supports both **Activity Statements** and **Flex Queries**.",
    "* **Smart NBP Rates:** T-1 rule compliant, using **Batch Caching** logic.",
    "* **FIFO Algorithm:** Strictly follows tax laws for Cost Basis.",
    "* **PIT-38 Ready:** Generates a PDF report compatible with Polish tax forms.",
    "",
    "## 📦 Installation",
    "",
    "1.  **Clone:**",
    "    ```bash",
    "    git clone [https://github.com/your-repo/ibkr-tax-pl.git](https://github.com/your-repo/ibkr-tax-pl.git)",
    "    cd ibkr-tax-pl",
    "    ```",
    "",
    "2.  **Install:**",
    "    ```bash",
    "    pip install -r requirements.txt",
    "    ```",
    "",
    "3.  **Setup Security:**",
    "    Create `.env`:",
    "    ```ini",
    "    SQLCIPHER_KEY=your_secret_key",
    "    DATABASE_PATH=data/ibkr_history.db.enc",
    "    ```",
    "",
    "## 🏃 Usage",
    "",
    "```bash",
    "# 1. Import Data",
    "python -m src.parser --files \"data/*.csv\"",
    "",
    "# 2. Generate Report",
    "python main.py --target-year 2024 --export-pdf --export-excel",
    "```",
    "",
    "## ⚠️ Disclaimer",
    "Educational purpose only. Not financial advice."
]

# ==============================================================================
# 2. SPECIFICATION.MD
# ==============================================================================
spec_lines = [
    f"# Technical Specification: IBKR Tax Assistant ({VERSION})",
    "",
    "## 1. System Architecture",
    "Modular architecture separating Data Ingestion, Encryption, Logic, and Reporting.",
    "",
    "```text",
    "[IBKR CSV] -> [Universal Parser] -> [SQLCipher DB] -> [FIFO Core] -> [Reporters]",
    "```",
    "",
    f"## 2. Key Modules ({VERSION})",
    "",
    "### 2.1. Security (`src/db_connector.py`)",
    "* **SQLCipher:** AES-256 encryption at rest.",
    "* **Decoupled Key:** Managed via `.env`.",
    "",
    "### 2.2. Ingestion (`src/parser.py`)",
    "* **Dynamic Mapping:** Adapts to Activity Statements and Flex Queries.",
    "* **Normalization:** Unifies date formats (`MM/DD/YYYY` -> ISO).",
    "* **Sanitization:** Filters metadata and total rows.",
    "",
    "### 2.3. FX Engine (`src/nbp.py`)",
    "* **Algorithm:** Smart Batch Caching. Fetches monthly chunks to minimize API calls (12 calls/year).",
    "* **Compliance:** Implements strict T-1 (business day lookback) rule.",
    "",
    "### 2.4. Core Logic",
    "* **FIFO:** Queue-based matching (First-In-First-Out).",
    "* **Tax Linking:** Associates Withholding Tax records with Dividends.",
    "",
    "## 3. Database Schema",
    "Single source of truth: `transactions` table (TradeId, Date, EventType, Ticker, Quantity, Price, Amount, Fee)."
]

# ==============================================================================
# 3. WIKI_CONTENT.MD
# ==============================================================================
wiki_lines = [
    f"# Project Documentation ({VERSION})",
    "",
    "Welcome to the **IBKR Tax Calculator** Wiki.",
    f"Current Release: **{VERSION}** (Stable).",
    "",
    "---",
    "",
    f"## 🚀 What's New in {VERSION}?",
    "* **Full Encryption:** SQLCipher integration.",
    "* **Universal Parser:** Activity Statement support added.",
    "* **Smart NBP:** Reduced API load by 95% via Batch Caching.",
    "",
    "---",
    "",
    "## 📥 Input Data",
    "We support:",
    "1. **Activity Statements** (CSV) - Recommended.",
    "2. **Flex Queries** (CSV) - Supported.",
    "",
    "Run imports via:",
    "`python -m src.parser --files \"data/*.csv\"`",
    "",
    "---",
    "",
    "## 🧮 PIT-38 Mapping",
    "| Field | Report Source |",
    "|:---|:---|",
    "| **Przychód (Revenue)** | PDF Page 5 -> Revenue |",
    "| **Koszty (Costs)** | PDF Page 5 -> Costs |",
    "| **Podatek (Tax Paid)** | PDF Page 4 -> Withholding Tax |",
    "",
    "---"
]

# ==============================================================================
# 4. LINKEDIN STORY
# ==============================================================================
linkedin_lines = [
    "# LinkedIn Content Series: The AI-Assisted Engineering Journey",
    "",
    f"Here are updated articles reflecting the **{VERSION}** milestone.",
    "",
    "---",
    "",
    f"## 📝 Article 2: Debugging the \"Black Box\" (Release {VERSION})",
    "**Headline:** The AI wrote a bug. I had to find it. The reality of AI-Assisted Development 🕵️‍♂️💻",
    "",
    "**Body:**",
    "",
    "In my last post, I shared how I built an IBKR Tax Calculator using AI. Today, I'm releasing **version " + VERSION + "**.",
    "",
    "But getting here wasn't easy. In **Sprint 3**, we migrated to an Encrypted SQL Database. Suddenly, the reports came out empty.",
    "",
    "**The Debugging Loop:**",
    "1.  **The Symptom:** Database had 1900 records, report said \"No Data\".",
    "2.  **The Investigation:** I analyzed the logs and found `NBP Rate: 0.0`.",
    "3.  **The Breakthrough:** The AI didn't handle weekends correctly in the currency converter.",
    "",
    f"**The Fix in {VERSION}:**",
    "I guided the AI to implement **Batch Caching** and recursive date lookups. Now, instead of 500 API calls, the system makes just 12 calls per year and handles holidays perfectly.",
    "",
    "**Key Takeaway:**",
    "AI-Assisted Development isn't \"Auto-Pilot.\" It's **Co-Pilot**. You need to understand the logic to guide the AI.",
    "",
    "**Hashtags:**",
    f"#Debugging #SoftwareEngineering #AI #Python #Release{VERSION.replace('.', '')} #Tech #IBKR",
    "",
    "---"
]

if __name__ == "__main__":
    write_file("README.md", readme_lines)
    write_file("SPECIFICATION.md", spec_lines)
    write_file("WIKI_CONTENT.md", wiki_lines)
    write_file("LINKEDIN_STORY.md", linkedin_lines)
    print(f"✨ All documentation bumped to {VERSION} successfully!")
