from decimal import Decimal, ROUND_HALF_EVEN


def money(value) -> Decimal:
    if value is None:
        return Decimal("0.00")
    if not isinstance(value, Decimal):
        try:
            value = Decimal(str(value))
        except:
            return Decimal("0.00")
    return value.quantize(Decimal("0.01"), rounding=ROUND_HALF_EVEN)
