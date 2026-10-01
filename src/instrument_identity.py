from collections import defaultdict
from typing import Any, Dict, Iterable, List, Tuple


TICKER_ALIASES = {"TOT": "TTE", "FB": "META"}


def resolve_instrument_identities(
    rows: Iterable[Dict[str, Any]],
) -> Tuple[List[Dict[str, Any]], List[Dict[str, str]]]:
    """Resolve legacy blank ISINs and derive chronologically ordered changes."""
    resolved = [dict(row) for row in rows]
    first_seen = defaultdict(dict)

    for row in resolved:
        ticker = row.get("Ticker", row.get("ticker", ""))
        if not ticker:
            continue
        ticker = TICKER_ALIASES.get(str(ticker).strip().upper(), str(ticker).strip().upper())
        isin = row.get("ISIN", row.get("isin", "")) or ""
        date = row.get("Date", row.get("date", "")) or ""
        if isin and (isin not in first_seen[ticker] or date < first_seen[ticker][isin]):
            first_seen[ticker][isin] = date

    earliest_isin = {}
    changes = []
    for ticker, identities in first_seen.items():
        ordered = sorted(identities.items(), key=lambda item: (item[1], item[0]))
        if ordered:
            earliest_isin[ticker] = ordered[0][0]
        for (previous_isin, _), (new_isin, change_date) in zip(ordered, ordered[1:]):
            changes.append(
                {
                    "ticker": ticker,
                    "previous_isin": previous_isin,
                    "new_isin": new_isin,
                    "date": change_date,
                }
            )

    for row in resolved:
        ticker = row.get("Ticker", row.get("ticker", ""))
        if ticker:
            normalized_ticker = TICKER_ALIASES.get(
                str(ticker).strip().upper(), str(ticker).strip().upper()
            )
            if not row.get("ISIN") and not row.get("isin"):
                row["ISIN"] = earliest_isin.get(normalized_ticker, "")
            elif "ISIN" not in row:
                row["ISIN"] = row.get("isin", "")
        elif "ISIN" not in row:
            row["ISIN"] = row.get("isin", "") or ""

    return resolved, changes