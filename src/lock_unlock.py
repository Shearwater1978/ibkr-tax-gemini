import os
import getpass
from cryptography.fernet import Fernet
from cryptography.hazmat.primitives import hashes
from cryptography.hazmat.primitives.kdf.pbkdf2 import PBKDF2HMAC
import base64
import sqlite3

DB_DIR = "db"
PLAIN_DB_NAME = "ibkr_history.db"
ENCRYPTED_DB_NAME = "ibkr_history.db.enc"

DB_PATH_PLAIN = os.path.join(DB_DIR, PLAIN_DB_NAME)
DB_PATH_ENC = os.path.join(DB_DIR, ENCRYPTED_DB_NAME)

def _get_fernet_key(password: str) -> Fernet:
    # Используем PBKDF2 для вывода надежного ключа Fernet из пользовательского пароля
    # !!! Соль должна быть зафиксирована для одного проекта !!!
    salt = b'a-fixed-salt-for-ibkr-tax' 
    kdf = PBKDF2HMAC(
        algorithm=hashes.SHA256(),
        length=32,
        salt=salt,
        iterations=480000,
    )
    key = base64.urlsafe_b64encode(kdf.derive(password.encode()))
    return Fernet(key)

def unlock_db(password: str) -> bool:
    # Если зашифрованный файл не существует, но существует открытый - 
    # считаем, что все ОК, это первый запуск или база уже расшифрована.
    if not os.path.exists(DB_PATH_ENC):
        return os.path.exists(DB_PATH_PLAIN)

    try:
        f = _get_fernet_key(password)
        with open(DB_PATH_ENC, 'rb') as file_enc:
            encrypted_data = file_enc.read()
        
        # Попытка дешифровки
        decrypted_data = f.decrypt(encrypted_data)

        # Сохраняем расшифрованный файл (.db)
        with open(DB_PATH_PLAIN, 'wb') as file_plain:
            file_plain.write(decrypted_data)
            
        print("🔓 Database unlocked successfully.")
        return True
    
    except Exception as e:
        print(f"❌ Decryption Failed: Wrong password or corrupt file. ({e})")
        # Удаляем битый/частично записанный файл, чтобы не оставить следов
        if os.path.exists(DB_PATH_PLAIN):
            os.remove(DB_PATH_PLAIN)
        return False

def lock_db(password: str):
    # Шифруем текущий незашифрованный файл (.db)
    if not os.path.exists(DB_PATH_PLAIN):
        return
        
    f = _get_fernet_key(password)

    with open(DB_PATH_PLAIN, 'rb') as file_plain:
        plain_data = file_plain.read()

    encrypted_data = f.encrypt(plain_data)

    # Сохраняем зашифрованный файл (.enc)
    with open(DB_PATH_ENC, 'wb') as file_enc:
        file_enc.write(encrypted_data)

    # Удаляем незашифрованный оригинал
    os.remove(DB_PATH_PLAIN)
    print("🔒 Database locked successfully.")

# --- БЛОК ДЛЯ РУЧНОГО ЗАПУСКА (ОДНОКРАТНОЙ БЛОКИРОВКИ) ---
if __name__ == "__main__":
    if os.path.exists(DB_PATH_PLAIN) and not os.path.exists(DB_PATH_ENC):
        print("🚨 Initial Lock Required: Your DB is currently in plaintext.")
        password = getpass.getpass("Enter NEW password for encryption: ")
        
        if not password:
            print("❌ Password cannot be empty. Exiting.")
        else:
            lock_db(password)
            print("✅ Initial lock applied. The plaintext file has been replaced by ibkr_history.db.enc")
            print("You can now run 'python main.py'.")
    elif os.path.exists(DB_PATH_ENC):
        print("DB is already encrypted (ibkr_history.db.enc exists). Run 'python main.py' to unlock and process.")
    else:
        print("No plaintext DB found to encrypt (ibkr_history.db missing). Run 'python run_ingestion.py' first.")