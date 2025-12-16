import os
import glob

# Configuration
SOURCE_DIR = 'src'
OUTPUT_FILE = 'RESTART_SRC.md'

# A header that tells the model what the file is
HEADER_TEXT = """# RESTART PROMPT: SOURCE CODE (v2.1.0)

**Context:** Part 2 of 3. Contains application source code.
**Instructions:** Restore these files to `src/` directory.
"""

def generate_src_backup():
    # We are looking for all .py files in the src folder
    files = glob.glob(os.path.join(SOURCE_DIR, '*.py'))
    files.sort()  # Sort for order

    with open(OUTPUT_FILE, 'w', encoding='utf-8') as outfile:
        outfile.write(HEADER_TEXT + "\n")

        for filepath in files:
            filename = os.path.basename(filepath)
            
            # Skip empty __init__.py if they are not needed, or leave
            # if filename == '__init__.py' and os.path.getsize(filepath) == 0: continue

            print(f"Packing {filename}...")
            
            # File header format
            outfile.write(f"# --- FILE: src/{filename} ---\n")
            outfile.write("```python\n")
            
            # Line by line read and write
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
