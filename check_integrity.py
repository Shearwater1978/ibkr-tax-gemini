import os

def check_file_content(filepath, search_strings):
    if not os.path.exists(filepath):
        print(f"❌ Файл не найден: {filepath}")
        return False
    
    with open(filepath, 'r', encoding='utf-8') as f:
        content = f.read()
    
    all_found = True
    for s in search_strings:
        if s not in content:
            print(f"❌ В файле {filepath} НЕ НАЙДЕН код:\n   '{s[:50]}...'")
            all_found = False
    
    if all_found:
        print(f"✅ Файл {filepath} содержит нужные правки.")
    return all_found

def run_check():
    print("🔍 Проверка целостности кода для сложных корпоративных действий...\n")
    
    # 1. Проверяем парсер на наличие логики Spinoff/Merger (для WBD, FG, OGN)
    parser_ok = check_file_content("src/parser.py", [
        "def extract_target_ticker(description: str)", # Функция поиска скрытого тикера
        "is_spinoff = \"Spin-off\" in desc",           # Определение спин-оффа
        "match = re.search(r'\(([A-Za-z0-9\.]+),\s+[A-Za-z0-9]', description)" # Regex для (WBD, ...)
    ])

    # 2. Проверяем FIFO на поддержку трансферов (для устранения минусов при переносах)
    fifo_ok = check_file_content("src/fifo.py", [
        "elif trade['type'] == 'TRANSFER':",
        "self._process_transfer_out(trade)"
    ])

    print("-" * 30)
    if parser_ok and fifo_ok:
        print("🎉 ВСЕ ПРАВКИ НА МЕСТЕ! Код готов к работе.")
        print("Попробуйте запустить main.py — позиции OGN, FG, WBD должны сойтись.")
    else:
        print("⚠️ КАКИЕ-ТО ПРАВКИ ОТСУТСТВУЮТ. Возможно, вы забыли сделать git pull или скопировать файлы.")

if __name__ == "__main__":
    run_check()
