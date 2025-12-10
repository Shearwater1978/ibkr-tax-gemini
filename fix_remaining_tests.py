import os

def delete_file(filepath):
    if os.path.exists(filepath):
        try:
            os.remove(filepath)
            print(f"🗑️  Deleted obsolete test: {filepath}")
        except Exception as e:
            print(f"❌ Error deleting {filepath}: {e}")
    else:
        print(f"⚠️  Already gone: {filepath}")

# Список "забытых" тестов, которые ссылаются на удаленные модули
obsolete_tests = [
    "tests/test_crypto.py",   # Ссылается на src.lock_unlock
    "tests/test_hashing.py"   # Ссылается на src.utils_db
]

print("🚀 Removing remaining obsolete tests...")
print("-" * 30)

for t in obsolete_tests:
    delete_file(t)

print("-" * 30)
print("✅ Done. Now run 'pytest' again!")
