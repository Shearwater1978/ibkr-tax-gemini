from collections import deque
from dataclasses import dataclass
from datetime import date
from decimal import Decimal, InvalidOperation
from enum import Enum
from typing import Any, Dict, Iterable, List

from src.fifo import TradeMatcher
from src.instrument_identity import resolve_instrument_identities

TICKER_ALIASES = {"TOT": "TTE", "FB": "META"}
EPSILON = Decimal("0.00000001")


class CoverageStatus(str, Enum):
    COVERED = "COVERED"
    PARTIAL = "PARTIAL"
    NOT_COVERED = "NOT_COVERED"


def normalize_ticker(ticker: str) -> str:
    if not isinstance(ticker, str):
        raise ValueError("ticker must be a string")
    normalized = ticker.strip().upper()
    if not normalized or not normalized.isalnum():
        raise ValueError("ticker must contain only letters and numbers")
    return TICKER_ALIASES.get(normalized, normalized)


def decimal_quantity(value: Any) -> Decimal:
    try:
        quantity = Decimal(str(value))
    except (InvalidOperation, TypeError, ValueError) as exc:
        raise ValueError("quantity must be a positive number") from exc
    if not quantity.is_finite() or quantity <= 0:
        raise ValueError("quantity must be a positive number")
    return quantity


def valid_date(value: Any) -> str:
    if not isinstance(value, str):
        raise ValueError("as_of must be an ISO date (YYYY-MM-DD)")
    try:
        parsed = date.fromisoformat(value)
    except ValueError as exc:
        raise ValueError("as_of must be an ISO date (YYYY-MM-DD)") from exc
    if parsed.isoformat() != value:
        raise ValueError("as_of must be an ISO date (YYYY-MM-DD)")
    return value


@dataclass(frozen=True)
class PlannedSale:
    ticker: str
    quantity: Decimal
    as_of: str

    def __post_init__(self):
        object.__setattr__(self, "ticker", normalize_ticker(self.ticker))
        object.__setattr__(self, "quantity", decimal_quantity(self.quantity))
        object.__setattr__(self, "as_of", valid_date(self.as_of))


@dataclass(frozen=True)
class CoverageLot:
    ticker: str
    isin: str
    acquisition_date: str
    quantity: Decimal
    source: str

    def as_dict(self) -> Dict[str, Any]:
        return {
            "ticker": self.ticker,
            "isin": self.isin,
            "acquisition_date": self.acquisition_date,
            "quantity": float(self.quantity),
            "source": self.source,
        }


@dataclass(frozen=True)
class CoverageResult:
    ticker: str
    requested: Decimal
    available: Decimal
    missing: Decimal
    as_of: str
    status: CoverageStatus
    lots: List[CoverageLot]
    history_found: bool

    def as_dict(self) -> Dict[str, Any]:
        return {
            "ticker": self.ticker,
            "requested": float(self.requested),
            "available": float(self.available),
            "missing": float(self.missing),
            "as_of": self.as_of,
            "status": self.status.value,
            "history_found": self.history_found,
            "additional_history_required": self.missing > EPSILON,
            "lots": [lot.as_dict() for lot in self.lots],
        }


def _normalize_event(row: Dict[str, Any]) -> Dict[str, Any]:
    event_type = row.get("EventType", row.get("type"))
    if event_type in {"DIVIDEND", "TAX"}:
        return {}

    raw_ticker = row.get("Ticker", row.get("ticker", ""))
    if raw_ticker is None:
        return {}

    try:
        ticker = normalize_ticker(raw_ticker)
    except ValueError:
        return {}

    quantity = Decimal(str(row.get("Quantity", row.get("qty", 0)) or 0))
    return {
        "type": event_type,
        "date": row.get("Date", row.get("date")),
        "ticker": ticker,
        "isin": row.get("ISIN", row.get("isin", "")) or "",
        "qty": quantity,
        "price": Decimal(0),
        "commission": Decimal(0),
        "currency": row.get("Currency", row.get("currency", "PLN")) or "PLN",
        "rate": Decimal(1),
        "source": str(row.get("TradeId", row.get("id", row.get("SourceKey", "DB")))),
        "ratio": Decimal(str(row.get("SplitRatio", row.get("ratio", 1)) or 1)),
    }


def _row_matches_ticker(row: Dict[str, Any], ticker: str) -> bool:
    try:
        raw_ticker = row.get("Ticker", row.get("ticker", ""))
        return normalize_ticker(raw_ticker) == ticker
    except ValueError:
        return False


def build_inventory_snapshot(
    rows: Iterable[Dict[str, Any]], as_of: str
) -> Dict[Any, deque]:
    as_of = valid_date(as_of)
    rows, _ = resolve_instrument_identities(rows)
    events = []
    for row in rows:
        event = _normalize_event(row)
        if event and event["date"] <= as_of:
            events.append(event)
    matcher = TradeMatcher()
    matcher.process_trades(events)
    snapshot = {}
    for key, batches in matcher.inventory.items():
        if isinstance(key, tuple):
            identity = key
        else:
            identity = (key, batches[0].get("isin", "") if batches else "")
        snapshot[identity] = deque(batch.copy() for batch in batches)
    return snapshot


def check_coverage(
    rows: Iterable[Dict[str, Any]], planned_sales: Iterable[PlannedSale]
) -> Dict[str, Any]:
    requests = list(planned_sales)
    if not requests:
        raise ValueError("at least one planned sale is required")
    tickers = [request.ticker for request in requests]
    if len(set(tickers)) != len(tickers):
        raise ValueError("planned sales must not contain duplicate tickers")

    all_rows, _ = resolve_instrument_identities(rows)
    results = []
    for request in requests:
        snapshot = build_inventory_snapshot(all_rows, request.as_of)
        identity_starts = {}
        for row in all_rows:
            raw_ticker = row.get("Ticker", row.get("ticker", ""))
            try:
                row_ticker = normalize_ticker(raw_ticker)
            except ValueError:
                continue
            row_date = row.get("Date", row.get("date", "")) or ""
            isin = row.get("ISIN", row.get("isin", "")) or ""
            if row_ticker == request.ticker and row_date <= request.as_of:
                if isin not in identity_starts or row_date < identity_starts[isin]:
                    identity_starts[isin] = row_date
        active_isin = max(
            identity_starts.items(), key=lambda item: (item[1], item[0])
        )[0] if identity_starts else ""
        inventory = snapshot.get((request.ticker, active_isin), deque())
        available = sum((batch["qty"] for batch in inventory), Decimal(0))
        remaining = min(request.quantity, available)
        lots = []
        for batch in inventory:
            if remaining <= EPSILON:
                break
            contribution = min(batch["qty"], remaining)
            lots.append(
                CoverageLot(
                    request.ticker,
                    str(batch.get("isin", "")),
                    batch["date"],
                    contribution,
                    str(batch.get("source", "UNKNOWN")),
                )
            )
            remaining -= contribution
        missing = max(request.quantity - available, Decimal(0))
        if available >= request.quantity:
            status = CoverageStatus.COVERED
        elif available > EPSILON:
            status = CoverageStatus.PARTIAL
        else:
            status = CoverageStatus.NOT_COVERED
        results.append(
            CoverageResult(
                request.ticker,
                request.quantity,
                available,
                missing,
                request.as_of,
                status,
                lots,
                any(
                    _row_matches_ticker(row, request.ticker)
                    and row.get("Date", row.get("date", "")) <= request.as_of
                    for row in all_rows
                ),
            )
        )
    return {
        "status": (
            "COVERED"
            if all(result.status == CoverageStatus.COVERED for result in results)
            else "INCOMPLETE"
        ),
        "results": [result.as_dict() for result in results],
    }
