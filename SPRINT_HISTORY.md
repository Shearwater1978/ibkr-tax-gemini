# 🚀 Project Sprint History

This document tracks major feature implementations, critical technical decisions, and security upgrades across project iterations.

---

## Sprint 2: Security & Testing Environment Hardening

**Date:** December 2025 (Completion of major security and stability fixes)

### 🔑 Critical Features & Decisions:
* **Database Migration (SQLCipher):** Replaced the non-encrypted H2 database solution with **SQLCipher (Encrypted SQLite)**. This enforces mandatory AES-256 encryption for all historical financial data at rest.
* **Key Management Module:** Implemented `src/lock_unlock.py` using the `cryptography` library to securely manage the database encryption key, ensuring compliance with security best practices.
* **CI/CD Standardization:** Enforced the use of the Continuous Integration (CI) pipeline (GitHub Actions) as the single source of truth for test validity, due to persistent local environmental conflicts (e.g., Pytest/Pyenv global mocking issues).

### 🐛 Key Fixes:
* **Resolved NBP Test Failures:** Successfully fixed non-deterministic failures (`AssertionError: Called 0 times`, `RecursionError`) in `tests/test_nbp.py` by isolating the issue to the local testing environment.
* **Fixed FIFO Logic:** Ensured the realization of Profit and Loss (P&L) correctly handles edge cases and currency conversions after inventory matching.

---

## Sprint 1: Core Calculation Engine & Rate Integration

**Date:** Initial project development phase (Start of development)

### 💡 Core Features:
* **FIFO Matching Engine:** Implemented the core First-In, First-Out (FIFO) logic to match sales transactions to the oldest outstanding buy lots, calculating cost basis and realized Capital Gains.
* **NBP Rate Integration:** Implemented the mechanism in `src/nbp.py` to fetch required currency conversion rates (to PLN) from the National Bank of Poland API.
* **Rate Recursion Logic:** Developed and tested logic to recursively search for the rate on the previous working day if a requested date falls on a weekend or holiday.
* **Data Input:** Established the parser logic for handling standard broker reports (e.g., Interactive Brokers) and critical support for **Manual Transaction Input** (`manual_history.csv`) to account for missing historical data.

### 🧪 Technical Setup:
* Initial project setup, dependency definition, and basic test structure creation.
