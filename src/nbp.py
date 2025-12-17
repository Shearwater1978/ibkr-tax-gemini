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
        return  # Уже загружено

    # Вычисляем первый и последний день месяца
    start_date = date(year, month, 1)
    last_day = calendar.monthrange(year, month)[1]
    end_date = date(year, month, last_day)

    # Если запрашиваем будущий месяц, данных нет, кэшируем пустоту и выходим
    if start_date > date.today():
        _MONTHLY_CACHE[cache_key] = {}
        return

    # Ограничиваем конец текущей датой (чтобы не просить курсы из будущего)
    if end_date > date.today():
        end_date = date.today()

    fmt_start = start_date.strftime("%Y-%m-%d")
    fmt_end = end_date.strftime("%Y-%m-%d")

    # Формируем запрос диапазона (Table A - средние курсы)
    url = f"http://api.nbp.pl/api/exchangerates/rates/a/{currency}/{fmt_start}/{fmt_end}/?format=json"

    try:
        # print(f"🌐 NBP API Fetch: {currency} for {fmt_start}..{fmt_end}")
        response = requests.get(url, timeout=10)

        rates_map = {}
        if response.status_code == 200:
            data = response.json()
            # Разбираем ответ: [{'no': '...', 'effectiveDate': '2025-01-02', 'mid': 4.1012}, ...]
            for item in data.get("rates", []):
                d_str = item["effectiveDate"]
                rate_val = Decimal(str(item["mid"]))
                rates_map[d_str] = rate_val
        elif response.status_code == 404:
            # 404 для диапазона значит, что в этом диапазоне нет курсов (например, одни праздники или начало месяца)
            # Это нормально, сохраняем пустой словарь
            pass
        else:
            print(f"⚠️ NBP API Warning: HTTP {response.status_code} for {url}")

        _MONTHLY_CACHE[cache_key] = rates_map

    except Exception as e:
        print(f"❌ NBP Network Error for {fmt_start}: {e}")
        # Не сохраняем в кэш, чтобы при следующем вызове попробовать снова?
        # Или сохраняем пустоту, чтобы не ддосить? Лучше не сохранять, вдруг сеть моргнула.
        pass


def get_nbp_rate(currency: str, date_str: str) -> Decimal:
    """
    Возвращает курс NBP (средний) для указанной валюты на день,
    ПРЕДШЕСТВУЮЩИЙ указанной дате (правило T-1).
    Использует кэширование по месяцам.
    """
    if currency == "PLN":
        return Decimal("1.0")

    try:
        event_date = datetime.strptime(date_str, "%Y-%m-%d").date()
    except ValueError:
        print(f"⚠️ NBP: Invalid date format {date_str}, using 1.0")
        return Decimal("1.0")

    # Начинаем поиск с T-1
    target_date = event_date - timedelta(days=1)

    # Пытаемся найти курс, отматывая назад до 10 дней
    # (обычно достаточно 3-4 дней для длинных выходных)
    for _ in range(10):
        t_year = target_date.year
        t_month = target_date.month
        t_str = target_date.strftime("%Y-%m-%d")

        # 1. Проверяем, загружен ли этот месяц
        if (currency, t_year, t_month) not in _MONTHLY_CACHE:
            fetch_month_rates(currency, t_year, t_month)

        # 2. Ищем дату в кэше
        month_data = _MONTHLY_CACHE.get((currency, t_year, t_month), {})

        if t_str in month_data:
            return month_data[t_str]

        # Если не нашли, идем на день назад (и на следующей итерации проверим кэш)
        target_date -= timedelta(days=1)

    print(
        f"❌ NBP FATAL: Could not find rate for {currency} around {date_str}. Using 1.0 fallback."
    )
    return Decimal("1.0")


def get_rate_for_tax_date(currency, trade_date):
    """Алиас для совместимости"""
    return get_nbp_rate(currency, trade_date)
