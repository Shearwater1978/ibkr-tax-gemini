# Tasks: Establish Database Security

- [x] Select and document the supported SQLCipher Python driver and update `requirements.txt`.
- [x] Refactor `DBConnector` key setup, capability validation, schema verification, and error handling.
- [x] Refactor `tools/change_key.py` to pass the old key and verify the new key.
- [x] Add temporary-database tests for encrypted open, wrong-key rejection, and key rotation.
- [x] Update `README.md` and `DOCS_SECURITY.md` to match the verified workflow.
- [x] Run `pytest`, security checks, and a manual CLI import/calculation smoke test with a temporary database.