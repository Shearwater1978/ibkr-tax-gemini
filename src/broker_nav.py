import csv
import re
from datetime import datetime
from pathlib import Path
from typing import Optional

_PERIOD_DATE = re.compile(r"[A-Za-z]+ \d{1,2}, \d{4}")


def _parse_number(value: str) -> float:
    return float(value.replace(",", ""))


def parse_statement_nav(filepath: Path) -> Optional[dict]:
    """Read period, base currency and stock/total value from an Activity Statement."""
    base_currency = None
    period = None
    nav = {}
    with open(filepath, "r", encoding="utf-8-sig", newline="") as f:
        for row in csv.reader(f):
            if len(row) < 4 or row[1] != "Data":
                continue
            if row[0] == "Statement" and row[2] == "Period":
                dates = _PERIOD_DATE.findall(row[3])
                if len(dates) == 2:
                    period = tuple(
                        datetime.strptime(d, "%B %d, %Y").date() for d in dates
                    )
            elif row[0] == "Account Information" and row[2] == "Base Currency":
                base_currency = row[3].strip()
            elif row[0] == "Net Asset Value" and len(row) >= 7:
                label = row[2].strip()
                if label in ("Stock", "Total"):
                    try:
                        nav[label] = _parse_number(row[6])
                    except ValueError:
                        continue
    if not period or "Stock" not in nav or "Total" not in nav:
        return None
    return {
        "currency": base_currency or "USD",
        "as_of": period[1].isoformat(),
        "period_start": period[0],
        "stock_value": nav["Stock"],
        "net_asset_value": nav["Total"],
    }


def find_broker_nav(data_dir: Path, year: int) -> Optional[dict]:
    """Latest statement in data_dir that starts in `year`, or None."""
    best = None
    for path in sorted(Path(data_dir).glob("*.csv")):
        try:
            info = parse_statement_nav(path)
        except (OSError, csv.Error, ValueError):
            continue
        if not info or info["period_start"].year != year:
            continue
        if best is None or info["as_of"] > best["as_of"]:
            best = {**info, "source_file": path.name}
    if best:
        best.pop("period_start")
    return best
