import pytest
from decimal import Decimal
from src.parser import normalize_date, extract_ticker, parse_decimal, classify_trade_type

def test_normalize_date():
    # Тестируем разные форматы из Activity Statement
    assert normalize_date("20250102") == "2025-01-02"
    assert normalize_date("01/02/2025") == "2025-01-02" # US format MM/DD/YYYY
    assert normalize_date("2025-01-02, 15:00:00") == "2025-01-02"
    
    # Проверка на пустые/битые значения (Total row)
    assert normalize_date("") is None
    assert normalize_date(None) is None

def test_extract_ticker():
    # Стандартный случай с ISIN
    assert extract_ticker("AGR(US05351W1036) Cash Dividend", "") == "AGR"
    
    # Fallback случай (без ISIN), тикер должен быть коротким (<6 символов)
    # БЫЛО: "SIMPLE" (6 букв - фейл), СТАЛО: "TEST" (4 буквы - ок)
    assert extract_ticker("TEST Cash Div", "") == "TEST"
    
    # Приоритет колонки Symbol
    assert extract_ticker("Unknown Desc", "AAPL") == "AAPL" 

def test_parse_decimal():
    assert parse_decimal("1,000.50") == Decimal("1000.50")
    assert parse_decimal("-500") == Decimal("-500")
    assert parse_decimal("") == Decimal("0")

def test_classify_trade():
    assert classify_trade_type("ACATS Transfer", Decimal(10)) == "TRANSFER"
    assert classify_trade_type("Buy Order", Decimal(10)) == "BUY"
    assert classify_trade_type("Sell", Decimal(-5)) == "SELL"
