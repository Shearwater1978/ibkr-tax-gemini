# tools/change_key.py

import sys
import os

# Add root directory to path to import src modules
sys.path.append(os.path.join(os.path.dirname(__file__), ".."))

from src.db_connector import DBConnector


def main():
    print("--- DATABASE PASSWORD ROTATION (SQLCipher) ---")

    # 1. Get current password (from .env or input)
    old_key = os.getenv("SQLCIPHER_KEY")
    if not old_key:
        old_key = input("Enter CURRENT password: ").strip()
    else:
        print("Current password found in SQLCIPHER_KEY env variable.")

    # 2. Connect with old password
    connector = DBConnector(key=old_key)

    try:
        connector.connect()
        # Verify connection integrity
        connector.conn.execute("SELECT count(*) FROM sqlite_master;")
    except Exception:
        print("ERROR: Could not open database with the current password.")
        connector.close()
        return

    # 3. Request new password
    new_key = input("Enter NEW password: ").strip()
    if not new_key:
        print("Cancelled: Empty password.")
        connector.close()
        return

    confirm = input("Confirm NEW password: ").strip()
    if new_key != confirm:
        print("ERROR: Passwords do not match.")
        connector.close()
        return

    # 4. Execute Rekey
    success = connector.change_password(new_key)
    connector.close()

    if success:
        verifier = DBConnector(key=new_key)
        try:
            verifier.connect()
        except Exception:
            print("ERROR: New password verification failed.")
            return
        finally:
            verifier.close()
        print("New password verified. Update SQLCIPHER_KEY in .env.")


if __name__ == "__main__":
    main()
