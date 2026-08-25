from dataclasses import dataclass
from typing import Optional


@dataclass(frozen=True)
class CalculationDiagnostic:
    code: str
    message: str
    ticker: Optional[str] = None
    date: Optional[str] = None
    currency: Optional[str] = None
    quantity: Optional[float] = None


class CalculationError(RuntimeError):
    """Raised when a tax result cannot be considered complete."""

    def __init__(self, diagnostic: CalculationDiagnostic):
        self.diagnostic = diagnostic
        super().__init__(diagnostic.message)


class NBPRateError(CalculationError):
    pass


class UnmatchedInventoryError(CalculationError):
    pass


class ReportExportError(RuntimeError):
    """Raised when a report cannot be written successfully."""
