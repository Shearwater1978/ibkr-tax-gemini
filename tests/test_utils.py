# tests/test_utils

import pytest
from decimal import Decimal
from src.utils import money

def test_financial_rounding():
    """
    Taxes require ROUND_HALF_UP (2.345 -> 2.35), not Python's default Banker's Rounding (2.345 -> 2.34).
    """
    # Case 1: Standard round up
    assert money(Decimal("2.345")) == Decimal("2.35")
    
    # Case 2: Standard round down
    assert money(Decimal("2.344")) == Decimal("2.34")
    
    # Case 3: Negative numbers
    assert money(Decimal("-2.345")) == Decimal("-2.35")

def test_money_handles_floats_and_strings():
    """Ensure utility handles various input types."""
    assert money(2.345) == Decimal("2.35")
    assert money("2.345") == Decimal("2.35")
    assert money(2) == Decimal("2.00")