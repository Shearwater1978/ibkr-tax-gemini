import pytest
import os
import sqlite3
from src.lock_unlock import unlock_db, lock_db

# --- CONSTANTS ---
TEST_PASSWORD = "super-secret-password-123" 
TEST_DB_NAME = "ibkr_test.db"
TEST_ENC_NAME = "ibkr_test.db.enc"
TEST_DB_PATH = os.path.join("tests", TEST_DB_NAME)
TEST_ENC_PATH = os.path.join("tests", TEST_ENC_NAME)

@pytest.fixture(autouse=True)
def setup_teardown_paths(monkeypatch):
    """
    Fixture that temporarily overrides DB paths in src.lock_unlock
    to point to the isolated 'tests/' directory, and cleans up files.
    """
    # 1. Ensure 'tests/' directory exists
    os.makedirs("tests", exist_ok=True)

    # 2. Patch the constants in the module under test (src.lock_unlock)
    monkeypatch.setattr('src.lock_unlock.DB_PATH_PLAIN', TEST_DB_PATH)
    monkeypatch.setattr('src.lock_unlock.DB_PATH_ENC', TEST_ENC_PATH)
    
    # 3. Setup: Create a new plaintext DB for encryption testing
    conn = sqlite3.connect(TEST_DB_PATH)
    conn.execute("CREATE TABLE test (id INTEGER, name TEXT)")
    conn.execute("INSERT INTO test VALUES (1, 'Hello')")
    
    # --- FIX: COMMIT THE TRANSACTION ---
    conn.commit() 
    
    conn.close()

    yield # Run the tests

    # 4. Teardown: Clean up the temporary files
    if os.path.exists(TEST_DB_PATH):
        os.remove(TEST_DB_PATH)
    if os.path.exists(TEST_ENC_PATH):
        os.remove(TEST_ENC_PATH)

def test_full_lock_unlock_cycle():
    """Scenario 1: Successful end-to-end lock and decrypt cycle."""
    
    # Initial state check: PLAIN DB exists, ENC DB does not.
    assert os.path.exists(TEST_DB_PATH)
    assert not os.path.exists(TEST_ENC_PATH)

    # 1. Lock (PLANTEXT -> ENCRYPTED)
    lock_db(TEST_PASSWORD)
    assert not os.path.exists(TEST_DB_PATH), "After locking, the PLAIN DB must be deleted."
    assert os.path.exists(TEST_ENC_PATH), "The encrypted file must be created."

    # 2. Unlock (ENCRYPTED -> PLAINTEXT)
    result = unlock_db(TEST_PASSWORD)
    assert result is True, "Decryption with the correct password must succeed."
    assert os.path.exists(TEST_DB_PATH), "After decryption, the PLAIN DB must be restored."
    
    # 3. Check data integrity
    conn = sqlite3.connect(TEST_DB_PATH)
    cursor = conn.cursor()
    cursor.execute("SELECT name FROM test WHERE id=1")
    # The error was here: fetchone() will now return ('Hello',), so [0] is safe.
    assert cursor.fetchone()[0] == 'Hello', "Data integrity must be maintained after the full cycle."
    conn.close()

def test_unlock_with_wrong_password():
    """Scenario 2: Failed decryption attempt with an incorrect password."""
    
    # Pre-condition: Encrypt the database using the correct password
    lock_db(TEST_PASSWORD)
    
    # Attempt decryption with a wrong password
    result = unlock_db("wrong-password-456")
    
    assert result is False, "Decryption with the wrong password must fail."
    assert not os.path.exists(TEST_DB_PATH), "The PLAIN DB must NOT exist after a failed decryption attempt."

def test_unlock_when_already_unlocked():
    """Scenario 3: Verify that unlock() is safe to call when the database is already plaintext."""
    
    # Initial state: PLAIN DB exists, ENC DB is missing (see fixture setup)
    assert os.path.exists(TEST_DB_PATH)
    assert not os.path.exists(TEST_ENC_PATH)
    
    # Call unlock_db()
    result = unlock_db(TEST_PASSWORD)
    
    assert result is True, "Unlock must return True if the DB is already open."
    assert os.path.exists(TEST_DB_PATH), "The PLAIN DB must remain intact."