# Tasks: Establish Database Security

- [ ] Select and document the supported SQLCipher Python driver and update `requirements.txt`.
- [ ] Refactor `DBConnector` key setup, capability validation, schema verification, and error handling.
- [ ] Refactor `tools/change_key.py` to pass the old key and verify the new key.
- [ ] Add temporary-database tests for encrypted open, wrong-key rejection, and key rotation.
- [ ] Update `README.md` and `DOCS_SECURITY.md` to match the verified workflow.
- [ ] Run `pytest`, security checks, and a manual CLI import/calculation smoke test with a temporary database.