import hashlib
from decimal import Decimal

def generate_trade_hash(date, ticker, type_op, qty, price):
    # Создаем уникальный отпечаток сделки.
    # Если мы загрузим тот же CSV второй раз, хэш совпадет.
    # Если мы загрузим Месячный отчет, а потом Годовой (где эта сделка есть), хэш совпадет.
    
    # Приводим к строке с фиксированной точностью, чтобы 10.00 и 10.0 не давали разный хэш
    q_str = f"{Decimal(qty):.6f}"
    p_str = f"{Decimal(price):.6f}"
    
    raw = f"{date}|{ticker}|{type_op}|{q_str}|{p_str}"
    return hashlib.sha256(raw.encode('utf-8')).hexdigest()

def generate_div_hash(date, ticker, amount):
    a_str = f"{Decimal(amount):.6f}"
    raw = f"{date}|{ticker}|{a_str}"
    return hashlib.sha256(raw.encode('utf-8')).hexdigest()
