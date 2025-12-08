import os
import apsw
import getpass

DB_DIR = "db"
PLAIN_DB = os.path.join(DB_DIR, "ibkr_history.db")
ENCRYPTED_DB = os.path.join(DB_DIR, "ibkr_history.enc")

def migrate():
    if not os.path.exists(PLAIN_DB):
        print(f"❌ Error: Source database {PLAIN_DB} not found!")
        return

    print("🔐 ENCRYPTION SETUP (APSW Version)")
    password = getpass.getpass("Enter NEW password for database: ")
    confirm = getpass.getpass("Confirm password: ")
    
    if password != confirm:
        print("❌ Passwords do not match!")
        return
    if not password:
        print("❌ Password cannot be empty!")
        return

    if os.path.exists(ENCRYPTED_DB):
        os.remove(ENCRYPTED_DB)

    print(f"⚙️  Encrypting {PLAIN_DB} -> {ENCRYPTED_DB} ...")

    try:
        # 1. Открываем обычную базу
        conn = apsw.Connection(PLAIN_DB)
        cur = conn.cursor()

        # 2. Присоединяем новую (зашифрованную)
        # В APSW синтаксис URI или ATTACH работает. 
        # Используем стандартный SQL подход.
        
        # ВНИМАНИЕ: Чтобы создать зашифрованную базу через ATTACH в SQLCipher 3/4:
        # ATTACH DATABASE 'file.enc' AS encrypted KEY 'password';
        cur.execute(f"ATTACH DATABASE '{ENCRYPTED_DB}' AS encrypted KEY '{password}'")

        # 3. Экспортируем данные
        cur.execute("SELECT sqlcipher_export('encrypted')")

        # 4. Отключаем
        cur.execute("DETACH DATABASE encrypted")
        conn.close()

        print("✅ Encryption complete.")
        
        backup_name = PLAIN_DB + ".bak"
        if os.path.exists(backup_name):
            os.remove(backup_name)
        os.rename(PLAIN_DB, backup_name)
        os.rename(ENCRYPTED_DB, PLAIN_DB)
        
        print(f"🔄 Swapped files. Backup is '{backup_name}'.")
        print("🚀 System is now encrypted.")

    except Exception as e:
        print(f"❌ Error: {e}")
        print("Tip: Ensure your APSW is compiled with SQLCipher support!")

if __name__ == "__main__":
    migrate()
