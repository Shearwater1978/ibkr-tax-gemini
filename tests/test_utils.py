from decimal import Decimal
from src.utils import money

def test_bankers_rounding():
    assert money(Decimal("2.345")) == Decimal("2.34") 
    assert money(Decimal("2.355")) == Decimal("2.36")
    assert money(Decimal("2.344")) == Decimal("2.34")

def test_money_handles_floats_and_strings():
    assert money(2.345) == Decimal("2.34")
    assert money("2.355") == Decimal("2.36")
    assert money(None) == Decimal("0.00")
