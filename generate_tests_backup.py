import os
import glob

SOURCE_DIR = "tests"
OUTPUT_FILE = "RESTART_TESTS.md"

HEADER_TEXT = """# RESTART PROMPT: TESTS (v2.1.0)

**Context:** Part 3 of 3. Contains pytest suite and mock data.
**Instructions:** Restore these files to `tests/` directory.
"""


def generate_tests_backup():
    # Собираем py и json файлы
    files = glob.glob(os.path.join(SOURCE_DIR, "*.py")) + glob.glob(
        os.path.join(SOURCE_DIR, "*.json")
    )
    files.sort()

    with open(OUTPUT_FILE, "w", encoding="utf-8") as outfile:
        outfile.write(HEADER_TEXT + "\n")

        for filepath in files:
            filename = os.path.basename(filepath)
            print(f"Packing {filename}...")

            # Определяем подсветку синтаксиса
            lang = "json" if filename.endswith(".json") else "python"

            outfile.write(f"# --- FILE: tests/{filename} ---\n")
            outfile.write(f"```{lang}\n")

            try:
                with open(filepath, "r", encoding="utf-8") as infile:
                    for line in infile:
                        outfile.write(line)
            except Exception as e:
                print(f"Error reading {filename}: {e}")

            outfile.write("```\n\n")

    print(f"✅ Файл {OUTPUT_FILE} успешно обновлен (собрано файлов: {len(files)})")


if __name__ == "__main__":
    generate_tests_backup()
