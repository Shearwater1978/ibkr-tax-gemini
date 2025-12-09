## Project Status and Core Context

### 1. Database Upgrade (Sprint Plan)
- **Status:** Complete.
- **Change:** The database solution was successfully migrated from H2 to **SQLCipher** (encrypted SQLite).
- **Key Files:** `src/lock_unlock.py` added for key management.

### 2. Testing Environment and Validation
- **Status:** All tests are **PASSING (100%)** in the Continuous Integration (CI) environment.
- **Resolution:** The stubborn `AssertionError` and `RecursionError` in tests/test_nbp.py were due to **local environmental conflicts** (global mocking, import cache issues) and not flawed business logic.
- **NBP Test Logic:** The final working version of `tests/test_nbp.py` ensures correct mocking of `requests.get` and handles memory cache clearing to guarantee accurate API call counting.

### 3. Current Focus
- **Next Step:** Proceed with documentation updates and other planned sprint features.
