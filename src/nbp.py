# src/nbp.py

import requests
import calendar
from datetime import datetime, timedelta, date
from decimal import Decimal
from typing import Dict, Optional

# Глобальный кэш: {(currency, year, month): {date_str: rate_decimal}}
_MONTHLY_CACHE: Dict[tuple, Dict[str, Decimal]] = {}

def fetch_month_rates(currency: str, year: int, month: int) -> None:
    """
    Загружает курсы валют за ВЕСЬ месяц одним запросом и сохраняет в глобальный кэш.
    """
    cache_key = (currency, year, month)
    if cache_key in _MONTHLY_CACHE:
        return  # Already uploaded

    # Calculate the first and last day of the month
    start_date = date(year, month, 1)
    last_day = calendar.monthrange(year, month)[1]
    end_date = date(year, month, last_day)

    # If we request the next month, there is no data, we cache the void and exit
    if start_date > date.today():
        _MONTHLY_CACHE[cache_key] = {}
        return

    # We limit the end to the current date (so as not to ask for courses from the future)
    if end_date > date.today():
        end_date = date.today()

    fmt_start = start_date.strftime("%Y-%m-%d")
    fmt_end = end_date.strftime("%Y-%m-%d")

    # We create a range request (Table A - average rates)
    url = f"http://api.nbp.pl/api/exchangerates/rates/a/{currency}/{fmt_start}/{fmt_end}/?format=json"

    try:
        # print(f"🌐 NBP API Fetch: {currency} for {fmt_start}..{fmt_end}")
        response = requests.get(url, timeout=10)
        
        rates_map = {}
        if response.status_code == 200:
            data = response.json()
            # Let's parse the response: [{'no': '...', 'effectiveDate': '2025-01-02', 'mid': 4.1012}, ...]
            for item in data.get('rates', []):
                d_str = item['effectiveDate']
                rate_val = Decimal(str(item['mid']))
                rates_map[d_str] = rate_val
        elif response.status_code == 404:
            # 404 for a range means that there are no courses in this range (for example, only holidays or the beginning of the month)
            # This is normal, save the empty dictionary
            pass
        else:
            print(f"⚠️ NBP API Warning: HTTP {response.status_code} for {url}")

        _MONTHLY_CACHE[cache_key] = rates_map

    except Exception as e:
        print(f"❌ NBP Network Error for {fmt_start}: {e}")
        # Don’t save it to the cache so that we can try again the next time we call?
        # Or do we keep it empty so as not to overdo it? It’s better not to save, in case the network blinked.
        pass

def get_nbp_rate(currency: str, date_str: str) -> Decimal:
    """
    Возвращает курс NBP (средний) для указанной валюты на день, 
    ПРЕДШЕСТВУЮЩИЙ указанной дате (правило T-1).
    Использует кэширование по месяцам.
    """
    if currency == 'PLN':
        return Decimal('1.0')

    try:
        event_date = datetime.strptime(date_str, "%Y-%m-%d").date()
    except ValueError:
        print(f"⚠️ NBP: Invalid date format {date_str}, using 1.0")
        return Decimal('1.0')

    # We start the search with T-1
    target_date = event_date - timedelta(days=1)

    # We are trying to find a course by rewinding back to 10 days
    # (usually 3-4 days is enough for a long weekend)
    for _ in range(10):
        t_year = target_date.year
        t_month = target_date.month
        t_str = target_date.strftime("%Y-%m-%d")

        # 1. Check if this month is loaded
        if (currency, t_year, t_month) not in _MONTHLY_CACHE:
            fetch_month_rates(currency, t_year, t_month)

        # 2. Looking for the date in the cache
        month_data = _MONTHLY_CACHE.get((currency, t_year, t_month), {})
        
        if t_str in month_data:
            return month_data[t_str]

        # If you haven’t found it, go back a day (and check the cache at the next iteration)
        target_date -= timedelta(days=1)

    print(f"❌ NBP FATAL: Could not find rate for {currency} around {date_str}. Using 1.0 fallback.")
    return Decimal('1.0')

def get_rate_for_tax_date(currency, trade_date):
    """Алиас для совместимости"""
    return get_nbp_rate(currency, trade_date)