## Context

Every amount returned by `/calculate/{year}` is PLN (NBP T-1 per transaction). `src/nbp.py` provides `get_nbp_rate(currency, date)` with a monthly cache. The UI formats amounts in several places (`fmt` helpers per view and the dashboard).

## Goals / Non-Goals

**Goals:**
- Display-only conversion of PLN amounts to USD or EUR using one rate per currency per calculation.
- A single shared money formatter so all views switch consistently.

**Non-Goals:**
- Re-computing tax in another currency, or converting each transaction at its own historical rate.
- Currencies beyond PLN, USD, and EUR, and market prices.
- Changing Excel/PDF output.

## Decisions

- Rate date is the last day of the calculated year, capped at today for the current year, so the date is always one NBP can answer; the response states the date used and the rates.
- The API returns `fx: {date, rates: {USD, EUR}}` in PLN per one foreign unit; a currency whose rate cannot be fetched is omitted instead of failing the calculation.
- Conversion divides by the rate in the UI so the switch is instant and needs no new request.
- The selection is stored in `localStorage` and falls back to PLN when the chosen currency has no rate or before the first calculation.
- Fields already in an original currency (cost per share, NBP rate, dividend currency) are never converted. A note near the switcher states that conversion is for display only.

## Risks / Trade-offs

- A single year-end rate means USD totals differ from the sum of USD transactions; accepted and disclosed by the note and the rate date.
- Cap at today means the current year uses a recent rate rather than a year-end one.
