import os
import glob

# Конфигурация
SOURCE_DIR = 'src'
OUTPUT_FILE = 'RESTART_SRC.md'

# Заголовок, который объясняет модели, что это за файл
HEADER_TEXT = """# RESTART PROMPT: SOURCE CODE (v2.1.0)

**Context:** Part 2 of 3. Contains application source code.
**Instructions:** Restore these files to `src/` directory.
"""

def generate_src_backup():
    # Ищем все .py файлы в папке src
    files = glob.glob(os.path.join(SOURCE_DIR, '*.py'))
    files.sort()  # Сортируем для порядка

    with open(OUTPUT_FILE, 'w', encoding='utf-8') as outfile:
        outfile.write(HEADER_TEXT + "\n")

        for filepath in files:
            filename = os.path.basename(filepath)
            
            # Пропускаем пустые __init__.py, если они не нужны, или оставляем
            # if filename == '__init__.py' and os.path.getsize(filepath) == 0: continue

            print(f"Packing {filename}...")
            
            # Формат заголовка файла
            outfile.write(f"# --- FILE: src/{filename} ---\n")
            outfile.write("```python\n")
            
            # Построчное чтение и запись
            try:
                with open(filepath, 'r', encoding='utf-8') as infile:
                    for line in infile:
                        outfile.write(line)
            except Exception as e:
                print(f"Error reading {filename}: {e}")
            
            outfile.write("```\n\n")

    print(f"✅ Файл {OUTPUT_FILE} успешно обновлен (собрано файлов: {len(files)})")

if __name__ == "__main__":
    generate_src_backup()
