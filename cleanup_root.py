import os

def delete_file(filepath):
    if os.path.exists(filepath):
        try:
            os.remove(filepath)
            print(f"🗑️  Deleted obsolete file: {filepath}")
        except Exception as e:
            print(f"❌ Error deleting {filepath}: {e}")
    else:
        print(f"⚠️  Already gone: {filepath}")

# Файлы в корне, которые больше не нужны в архитектуре Sprint 3
root_files_to_nuke = [
    "create_snapshot.py",
    "encrypt_db.py",
    "install_cli_tool.py",
    "tax_cli.py",
    
    # Также удалим скрипты очистки, которые мы создавали сегодня, 
    # чтобы они не валялись в проекте после использования
    "cleanup_project.py",
    "cleanup_tests.py",
    "fix_remaining_tests.py",
    "update_docs_sprint3_safe.py",
    "update_restart_prompt_v2.py",
    "finalize_with_tests.py",
    "generate_golden_prompt.py",
    "update_wiki_robust.py"
]

print("🚀 Cleaning up ROOT directory...")
print("-" * 30)

for f in root_files_to_nuke:
    delete_file(f)

print("-" * 30)
print("✨ Root directory is clean! Only 'main.py' and config files remain.")
