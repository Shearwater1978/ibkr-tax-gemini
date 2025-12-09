import pytest
from decimal import Decimal
# Import the module under test
from src.utils_db import generate_trade_hash, generate_div_hash

def test_trade_hash_consistency():
    """Verifies that identical trades always produce an identical hash."""
    h1 = generate_trade_hash("2024-01-01", "AAPL", "BUY", Decimal("10.00"), Decimal("150.50"))
    h2 = generate_trade_hash("2024-01-01", "AAPL", "BUY", Decimal("10.00"), Decimal("150.50"))
    assert h1 == h2, "Identical trades must have identical hashes."

def test_trade_hash_uniqueness():
    """Verifies that a minimal change in price or date changes the hash (collision prevention)."""
    h1 = generate_trade_hash("2024-01-01", "AAPL", "BUY", Decimal("10.00"), Decimal("150.50"))
    h3 = generate_trade_hash("2024-01-01", "AAPL", "BUY", Decimal("10.00"), Decimal("150.51"))
    assert h1 != h3, "Different prices must produce different hashes."
    
    h4 = generate_trade_hash("2024-01-01", "AAPL", "BUY", Decimal("10.00"), Decimal("150.50"))
    h5 = generate_trade_hash("2024-01-02", "AAPL", "BUY", Decimal("10.00"), Decimal("150.50"))
    assert h4 != h5, "Different dates must produce different hashes."

def test_decimal_precision():
    """Verifies that hashing is resilient to changes in Decimal input precision (e.g., 10 vs 10.000000)."""
    # The utils_db module ensures fixed precision before hashing.
    h4 = generate_trade_hash("2024-01-01", "MSFT", "SELL", Decimal("5"), Decimal("200"))
    h5 = generate_trade_hash("2024-01-01", "MSFT", "SELL", Decimal("5.000000"), Decimal("200.00"))
    assert h4 == h5, "Hashes must be identical regardless of input Decimal precision."

def test_div_hash_uniqueness():
    """Checks the sensitivity of the dividend hash."""
    d1 = generate_div_hash("2024-03-15", "KO", Decimal("1.00"))
    d2 = generate_div_hash("2024-03-15", "KO", Decimal("1.01"))
    assert d1 != d2, "Different dividend amounts must produce different hashes."