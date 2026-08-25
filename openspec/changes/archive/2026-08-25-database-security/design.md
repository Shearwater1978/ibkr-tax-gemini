# Design: Establish Database Security

1. Introduce one database-driver boundary in `DBConnector` and select a documented SQLCipher-compatible Python package. Keep the existing context-manager and query APIs stable.
2. Resolve database path and key from explicit configuration, validate that a key is present, and verify the opened database by querying the schema after applying the key. A plain SQLite fallback must be rejected rather than treated as encrypted.
3. Replace PRAGMA string interpolation with the driver's supported safe key-application mechanism. Never log keys or include them in exception text.
4. Update `tools/change_key.py` to open with the supplied current key, rekey to the new key, close, and reopen to verify the new key. Preserve the old database until verification succeeds where the driver permits it.
5. Add isolated tests using a temporary database and a driver capability check. Add documentation that states installation prerequisites and the failure mode when SQLCipher is unavailable.

The change is compatible with existing callers because `DBConnector`, `save_to_database`, the CLI, and FastAPI continue to use the same high-level methods.