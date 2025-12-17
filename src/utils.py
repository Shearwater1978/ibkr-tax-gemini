# src/utils.py
from decimal import Decimal, ROUND_HALF_UP


def money(value) -> Decimal:
    """Rounds a Decimal or float to 2 decimal places (financial standard)."""
    if not isinstance(value, Decimal):
        value = Decimal(str(value))
    return value.quantize(Decimal("0.01"), rounding=ROUND_HALF_UP)
