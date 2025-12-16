# SECURITY GUIDE

## 🔐 Database Key Rotation

This project uses **SQLCipher** for database encryption. The key is managed via the `SQLCIPHER_KEY` environment variable.

### Changing the Password

If you need to rotate the encryption key:

1.  Ensure your `.env` file contains the **current** working password.
2.  Run the rotation tool:
```bash
python tools/change_key.py
```
3.  Follow the interactive prompts.
4.  **MANUALLY UPDATE** your `.env` file with the new key immediately after success:
```bash
SQLCIPHER_KEY=YourNewPassword
```