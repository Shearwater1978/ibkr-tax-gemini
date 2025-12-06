import pytest
from decimal import Decimal
import csv
import os
from src.parser import parse_csv
from src.fifo import TradeMatcher

# --- HELPER ---
def create_temp_csv(filename, lines):
    with open(filename, 'w', encoding='utf-8') as f:
        for line in lines:
            f.write(line + "\n")

def teardown_file(filename):
    if os.path.exists(filename):
        os.remove(filename)

# --- ТЕСТ 1: KVUE Time Travel Fix ---
def test_kvue_date_fix():
    filename = "test_kvue.csv"
    # NOTE: Добавлена запятая перед описанием (...,2023-08-24,,...)
    # Структура: Type, Data, Asset, Curr, Date, EMPTY, Desc, Qty
    csv_lines = [
        "Corporate Actions,Data,Stocks,USD,2023-08-24,,\"2023-08-23, 20:25:00, JNJ.ODD(US478990ODD9) Merged(Voluntary Offer Allocation) WITH US49177J1025 20081 for 2500 (KVUE, KENVUE INC, US49177J1025)\",8.0324,0,190.046584,0"
    ]
    create_temp_csv(filename, csv_lines)
    
    try:
        data = parse_csv(filename)
        trades = data['trades']
        kvue_trade = next((t for t in trades if t['ticker'] == 'KVUE'), None)
        
        assert kvue_trade is not None, "KVUE trade not found! Parser ignored the line."
        
        # Проверяем, что дата подменилась на 23-е
        assert kvue_trade['date'] == "2023-08-23", f"Date fix failed! Expected 2023-08-23, got {kvue_trade['date']}"
        assert kvue_trade['type'] == "TRANSFER"
        assert kvue_trade['qty'] == Decimal("8.0324")
    finally:
        teardown_file(filename)

# --- ТЕСТ 2: GE Duplicate Split Fix ---
def test_ge_deduplication():
    filename = "test_ge.csv"
    # NOTE: Добавлена запятая перед описанием
    split_line = "Corporate Actions,Data,Stocks,USD,2021-08-02,,\"GE(US3696043013) Split 1 for 8 (GE, GENERAL ELECTRIC CO, US3696043013)\",0,0,0,0,0,0,0,0,0"
    
    # Дублируем строку
    csv_lines = [split_line, split_line] 
    create_temp_csv(filename, csv_lines)
    
    try:
        data = parse_csv(filename)
        trades = data['trades']
        
        # Ищем сплиты
        splits = [t for t in trades if t['type'] == 'SPLIT' and t['ticker'] == 'GE']
        
        assert len(splits) > 0, "No GE splits found! Ticker extraction might have failed."
        
        # Дедупликация: должен остаться только 1
        assert len(splits) == 1, f"Deduplication failed! Found {len(splits)} splits instead of 1."
        assert splits[0]['ratio'] == Decimal("0.125")
    finally:
        teardown_file(filename)

# --- ТЕСТ 3: FIFO Sorting ---
def test_fifo_sorting_priority():
    matcher = TradeMatcher()
    
    trades = [
        # SELL первый в списке
        {
            "ticker": "TEST", "date": "2024-01-01", "type": "SELL", 
            "qty": Decimal("-5"), "price": Decimal(100), "currency": "USD", "commission": Decimal(1)
        },
        # TRANSFER второй в списке
        {
            "ticker": "TEST", "date": "2024-01-01", "type": "TRANSFER", 
            "qty": Decimal("10"), "price": Decimal(0), "currency": "USD", "commission": Decimal(0)
        }
    ]
    
    matcher.process_trades(trades)
    inventory = matcher.get_current_inventory()
    
    # Должен сначала начислить 10, потом списать 5 -> Остаток 5
    assert inventory.get("TEST") == Decimal("5"), f"Sorting logic failed! Inventory is {inventory.get('TEST')}"
