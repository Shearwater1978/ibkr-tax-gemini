import os
from src.parser import parse_csv
from src.fifo import TradeMatcher
from src.processing import TaxCalculator

def create_snapshot():
    # 1. Спрашиваем дату отсечки
    print("📸 Creating Inventory Snapshot")
    cutoff_year = input("Enter the last FULL year to include in snapshot (e.g. 2024): ").strip()
    if not cutoff_year or len(cutoff_year) != 4:
        print("Invalid year.")
        return
        
    cutoff_date = f"{cutoff_year}-12-31"
    filename = f"snapshot_{cutoff_year}.json"
    
    # 2. Грузим ВСЕ CSV, как обычно
    print("Reading data...")
    data_dir = "data"
    all_trades = []
    
    files = [f for f in os.listdir(data_dir) if f.endswith(".csv")]
    for f in files:
        path = os.path.join(data_dir, f)
        res = parse_csv(path)
        all_trades.extend(res.get('trades', []))
        
    # 3. Фильтруем сделки, которые старше даты отсечки
    # (Мы хотим состояние именно на конец этого года)
    filtered_trades = [t for t in all_trades if t['date'] <= cutoff_date]
    print(f"Processing {len(filtered_trades)} trades up to {cutoff_date}...")
    
    # 4. Прогоняем через FIFO
    matcher = TradeMatcher()
    matcher.process_trades(filtered_trades)
    
    # 5. Сохраняем результат
    matcher.save_state(filename, cutoff_date)
    print("✅ Done!")
    print(f"You can now use '{filename}' for future calculations.")
    print(f"In main.py, modify initialization to: calc.load_snapshot('{filename}')")

if __name__ == "__main__":
    create_snapshot()
