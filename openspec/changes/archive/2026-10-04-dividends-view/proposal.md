## Why

Dividends are a separate tax item in Poland, but the portal only shows a single gross total and per-ticker sums. Users cannot review the individual payments, the tax withheld at source, or the net amounts that feed the tax declaration. The reference screenshots (`gui/references/assets/`) show a dedicated dividends table with totals and pagination.

## What Changes

- Add a Dividends tab listing individual dividend payments for the calculated year: date, ticker, currency, NBP rate, gross PLN, tax withheld PLN, and net PLN.
- Show a totals row, ticker filter, sortable columns and pagination (default 25 rows per page, selectable).
- Extend the calculation response with the per-payment dividend list (additive; no extra calculation endpoint).
- Show an empty state until a calculation has succeeded.

## Capabilities

### New Capabilities

None.

### Modified Capabilities

- `web-portal`: adds the Dividends tab and its table behavior.
- `gui-api`: the calculation response gains a per-payment dividends list.

## Impact

- `gui/ui/index.html` (new tab, table, pagination).
- `gui/backend/api.py` (`/calculate/{year}` response gains `dividends`).
- `tests/test_api.py`.
- No dependency, database, or calculation changes. Market-data features in the references (P/E, EPS, upcoming events, top movers) are out of scope because that data is not available.
