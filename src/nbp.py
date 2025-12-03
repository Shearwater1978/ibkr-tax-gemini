import os
import json
import requests
from datetime import datetime, timedelta
from decimal import Decimal

CACHE_DIR = "cache/nbp"
_MEMORY_CACHE = {}


def get_previous_day(date_str: str) -> str:
    try:
        dt = datetime.strptime(date_str, "%Y-%m-%d")
        prev = dt - timedelta(days=1)
        return prev.strftime("%Y-%m-%d")
    except:
        return date_str


def _load_year_cache(currency, year):
    cache_key = f"{currency}_{year}"
    if cache_key in _MEMORY_CACHE:
        return _MEMORY_CACHE[cache_key]
    os.makedirs(CACHE_DIR, exist_ok=True)
    file_path = os.path.join(CACHE_DIR, f"{currency}_{year}_bulk.json")
    if os.path.exists(file_path):
        with open(file_path, "r") as f:
            data = json.load(f)
            _MEMORY_CACHE[cache_key] = data
            return data
    url = f"http://api.nbp.pl/api/exchangerates/rates/a/{currency}/{year}-01-01/{year}-12-31/?format=json"
    rates_map = {}
    try:
        resp = requests.get(url, timeout=10)
        if resp.status_code == 200:
            raw = resp.json()
            for entry in raw.get("rates", []):
                rates_map[entry["effectiveDate"]] = entry["mid"]
            with open(file_path, "w") as f:
                json.dump(rates_map, f)
    except: # nosec B110
        pass
    _MEMORY_CACHE[cache_key] = rates_map
    return rates_map


def get_nbp_rate(currency: str, date_str: str, attempt=0) -> Decimal:
    if currency.upper() == "PLN":
        return Decimal("1.0")
    if attempt > 10:
        return Decimal("0.0")
    year = date_str[:4]
    rates_map = _load_year_cache(currency, year)
    if date_str in rates_map:
        return Decimal(str(rates_map[date_str]))
    prev_day = get_previous_day(date_str)
    return get_nbp_rate(currency, prev_day, attempt + 1)


def get_rate_for_tax_date(currency: str, event_date: str) -> Decimal:
    target_date = get_previous_day(event_date)
    return get_nbp_rate(currency, target_date)
