## 1. API

- [x] 1.1 Add `dividends` (ex_date, ticker, currency, rate, gross_pln, tax_withheld_pln) to the `/calculate/{year}` response in `gui/backend/api.py` and verify with an API test for a year with dividends and an empty list for a year without
- [x] 1.2 Verify existing response fields are unchanged by asserting them in the same tests

## 2. Dividends tab

- [x] 2.1 Add the Dividends tab with empty state and the table (date, ticker, currency, rate, gross, withheld, net) and verify with mocked data in the browser
- [x] 2.2 Add totals row for the full year, plus filtered totals when a ticker filter is active, and verify both numbers with mock data
- [x] 2.3 Add column sorting with toggle and verify ascending and descending order
- [x] 2.4 Add pagination with page size selector and "Showing X-Y of N", and verify with 60+ mock payments
- [x] 2.5 Verify ticker text with markup characters renders literally, and that a failed calculation leaves the Dividends tab unchanged

## 3. Verification

- [x] 3.1 Verify against the real backend (`/calculate/2024`) that the tab renders without layout problems
- [x] 3.2 Run `black --check .`, `openspec validate dividends-view --strict` and `python -m pytest tests -k "api or gui"` and verify all pass
