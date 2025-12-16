import os

OUTPUT_FILE = 'RESTART_PROMPT.md'

# Файлы, которые лежат в корне и нужны для запуска
FILES_TO_INCLUDE = [
    'main.py',
    'requirements.txt',
    '.gitignore',
    '.env.example' # Если есть
]

# Текст, который увидит модель в самом начале
HEADER_TEXT = """# RESTART PROMPT (MASTER INDEX - v2.1.0)

**Role:** Expert Python Developer (Finance/Tax domain).
**Project:** IBKR Tax Calculator for Poland (PIT-38).
**Version:** v2.1.0 (Stable).

## ⚠️ RESTORATION INSTRUCTIONS (READ FIRST)
This project context is split into **3 parts** due to size limits. You are currently viewing Part 1.

1.  **PART 1 (Current File):** Contains Root files (`main.py`, config). Restore them immediately.
2.  **PART 2 (Source Code):** YOU MUST ASK THE USER for the file `RESTART_SRC.md` to restore the `src/` directory.
3.  **PART 3 (Tests):** YOU MUST ASK THE USER for the file `RESTART_TESTS.md` to restore the `tests/` directory.

**DO NOT proceed with major refactoring until you have received all 3 parts.**

## 1. ROOT FILES (Restore content below)
"""

def generate_root_backup():
    with open(OUTPUT_FILE, 'w', encoding='utf-8') as outfile:
        outfile.write(HEADER_TEXT + "\n")

        for filename in FILES_TO_INCLUDE:
            if not os.path.exists(filename):
                print(f"⚠️ Warning: File {filename} not found in root. Skipping.")
                continue
                
            print(f"Packing {filename}...")
            
            # Определяем язык
            if filename.endswith('.py'):
                lang = 'python'
            elif filename == 'requirements.txt' or filename == '.gitignore':
                lang = 'text'
            else:
                lang = 'bash'

            outfile.write(f"# --- FILE: {filename} ---\n")
            outfile.write(f"```{lang}\n")
            
            try:
                with open(filename, 'r', encoding='utf-8') as infile:
                    for line in infile:
                        outfile.write(line)
            except Exception as e:
                print(f"Error reading {filename}: {e}")
            
            outfile.write("```\n\n")

    print(f"✅ Файл {OUTPUT_FILE} успешно обновлен с инструкциями для восстановления.")

if __name__ == "__main__":
    generate_root_backup()
