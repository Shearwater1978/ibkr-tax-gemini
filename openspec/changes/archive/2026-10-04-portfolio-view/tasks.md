## 1. API

- [x] 1.1 Add `inventory` (ticker, buy_date, quantity, cost_per_share, total_cost, currency) to the `/calculate/{year}` response in `gui/backend/api.py`; verify with API tests for a year with open lots and one without
- [x] 1.2 Verify existing response fields are unchanged and the list length equals `open_positions_count`

## 2. Portfolio tab

- [x] 2.1 Add the Portfolio tab with empty state, summary cards and per-ticker table (aggregation by ticker, average cost); verify with mock lots in the browser
- [x] 2.2 Add totals row, ticker filter, column sorting and pagination with "Showing X-Y of N"; verify with 60+ mock tickers
- [x] 2.3 Add expandable lot rows per ticker and verify expand/collapse
- [x] 2.4 Verify markup in ticker text renders literally, the no-positions state, and that a failed calculation leaves the tab unchanged

## 3. Verification

- [x] 3.1 Verify against the real backend (`/calculate/2024`) that lot count and total cost match the report and the tab renders without layout problems
- [x] 3.2 Run `black --check .`, `openspec validate portfolio-view --strict` and `python -m pytest tests -k "api or gui"` and verify all pass
