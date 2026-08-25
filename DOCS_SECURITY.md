# SECURITY GUIDE

## 🔐 Database Key Rotation

This project uses **SQLCipher** for database encryption. The key is managed via the `SQLCIPHER_KEY` environment variable.

### Changing the Password

If you need to rotate the encryption key:

1.  Ensure your `.env` file contains the **current** working password.
2.  Install dependencies from `requirements.txt`; the application requires the `sqlcipher3` driver and refuses plaintext SQLite.
3.  Run the rotation tool:
```bash
python tools/change_key.py
```
4.  Follow the interactive prompts. The tool verifies that the database reopens with the new key before reporting success.
5.  **MANUALLY UPDATE** your `.env` file with the new key after successful verification:
```bash
SQLCIPHER_KEY=YourNewPassword
```

Keys are never printed by the rotation tool. Keep the `.env` file private and do not commit it.