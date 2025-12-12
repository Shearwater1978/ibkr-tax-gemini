import glob
import os
import csv

# Тикер для проверки
TARGET_TICKER = "VZ"

def trace_balance():
    print(f"🕵️‍♂️ Трассировка баланса для: {TARGET_TICKER}")
    
    # 1. Собираем все файлы из data и data_ignored
    # Используем относительные пути, чтобы видеть папку в выводе
    files_data = glob.glob("data/*.csv")
    files_ignored = glob.glob("data_ignored/*.csv")
    all_files = sorted(files_data + files_ignored)
    
    print(f"📂 Файлов для анализа: {len(all_files)}")
    print(f"   - data/: {len(files_data)}")
    print(f"   - data_ignored/: {len(files_ignored)}\n")
    
    events = []

    for fp in all_files:
        filename = os.path.basename(fp)    # Только имя файла
        folder = os.path.dirname(fp)       # Папка (data или data_ignored)
        full_path_display = f"{folder}/{filename}" # Для вывода
        
        try:
            with open(fp, 'r', encoding='utf-8-sig', errors='replace') as f:
                reader = csv.reader(f)
                
                # Словари для хранения индексов колонок для каждой секции
                headers = {}
                
                for row in reader:
                    if len(row) < 2: continue
                    
                    section = row[0]
                    row_type = row[1]
                    
                    if row_type == 'Header':
                        # Запоминаем индексы колонок: { 'Date': 3, 'Quantity': 5 ... }
                        headers[section] = {col.strip(): i for i, col in enumerate(row)}
                    
                    elif row_type == 'Data':
                        # Проверяем наличие тикера в строке (быстрая фильтрация)
                        if TARGET_TICKER not in str(row):
                            continue
                            
                        # Определяем тип события
                        is_trade = (section == 'Trades')
                        is_corp = (section == 'Corporate Actions')
                        
                        if not (is_trade or is_corp):
                            continue
                            
                        # Получаем схему колонок для текущей секции
                        h = headers.get(section, {})
                        
                        # Ищем нужные колонки (IBKR может менять их названия)
                        idx_qty = h.get('Quantity')
                        # Дата может называться по-разному
                        idx_date = h.get('Date/Time') or h.get('Date') or h.get('TradeDate') or h.get('Report Date')
                        idx_desc = h.get('Description') or h.get('Label') or h.get('Symbol') # fallback
                        
                        if idx_qty is not None and idx_date is not None:
                            try:
                                # Чистим число от запятых (напр. "1,000.00")
                                qty_str = row[idx_qty].replace(',', '').strip()
                                if not qty_str: continue
                                qty = float(qty_str)
                                
                                # Берем только дату (отрезаем время)
                                date_str = row[idx_date].split(',')[0].strip().split(' ')[0]
                                
                                desc = row[idx_desc] if idx_desc is not None else section
                                
                                # Фильтр нулевых количеств (как в основном парсере)
                                if is_corp and qty == 0:
                                    continue
                                    
                                events.append({
                                    'date': date_str,
                                    'qty': qty,
                                    'desc': desc,
                                    'type': 'TRADE' if is_trade else 'CORP',
                                    'file': full_path_display # Сохраняем путь для отладки
                                })
                            except ValueError:
                                # Пропускаем строки, где Quantity не число
                                continue

        except Exception as e:
            print(f"❌ Ошибка в {full_path_display}: {e}")

    # 2. Сортируем события хронологически
    events.sort(key=lambda x: x['date'])

    # 3. Выводим таблицу
    balance = 0.0
    # Форматированный заголовок
    print(f"{'DATE':<12} | {'TYPE':<6} | {'QTY':>8} | {'BAL':>8} | {'SOURCE FILE (Folder/Name)':<50} | DESCRIPTION")
    print("-" * 140)
    
    for e in events:
        balance += e['qty']
        # Обрезаем описание, если слишком длинное
        desc_short = (e['desc'][:40] + '..') if len(e['desc']) > 40 else e['desc']
        
        print(f"{e['date']:<12} | {e['type']:<6} | {e['qty']:>8.2f} | {balance:>8.2f} | {e['file']:<50} | {desc_short}")

if __name__ == "__main__":
    trace_balance()