from decimal import Decimal
from src.utils import money

def test_financial_rounding():
    assert money(Decimal('2.345')) == Decimal('2.35')
    assert money(Decimal('2.344')) == Decimal('2.34')
