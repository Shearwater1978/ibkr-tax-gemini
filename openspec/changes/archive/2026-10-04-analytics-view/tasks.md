## 1. Analytics tab

- [x] 1.1 Add the Analytics tab with empty state, summary cards (realized P&L, dividends, total, tickers) and verify with mocked data in the browser
- [x] 1.2 Add the per-ticker table with sign colouring, totals row, and escaping of ticker text; verify totals match mock data and markup renders literally
- [x] 1.3 Add ticker filter, "hide tickers without sales" toggle, and column sorting (default total descending); verify each with mock data
- [x] 1.4 Add pagination with page size selector and "Showing X-Y of N"; verify with 60+ mock tickers
- [x] 1.5 Verify a failed calculation leaves the Analytics tab unchanged

## 2. Verification

- [x] 2.1 Verify against the real backend (`/calculate/2024`) that the tab renders without layout problems
- [x] 2.2 Run `black --check .`, `openspec validate analytics-view --strict` and `python -m pytest tests -k "api or gui"` and verify all pass
