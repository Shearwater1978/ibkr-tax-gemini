## 1. API

- [ ] 1.1 Add `sales` (ticker, sale_date, quantity, sale_price, currency, sale_amount_pln, cost_basis_pln, profit_loss_pln) to the `/calculate/{year}` response; verify with tests for a year with sales, one without, per-ticker profit equal to the sum of its sales, and unchanged existing fields

## 2. Ticker page

- [ ] 2.1 Save the successful calculation snapshot to `localStorage` and render the `?ticker=` view with summary, sales, dividends, and open lots tables; verify with mock data in the browser
- [ ] 2.2 Handle empty states (no snapshot, ticker without activity) and escape ticker text; verify including a markup ticker
- [ ] 2.3 Turn every ticker in Dashboard, Dividends, Analytics, and Portfolio into a new-tab link, separating the Portfolio lot expander; verify the origin view is unchanged

## 3. Hover card

- [ ] 3.1 Add the hover/focus card with share of open cost, realized P&L, dividends, and total result in the display currency; verify values with mock data
- [ ] 3.2 Verify the card hides on leave, blur, and Escape, and that it works for tickers without open lots or sales

## 4. Verification

- [ ] 4.1 Verify against the real backend (`/calculate/2024`) that sales match per-ticker profit and the ticker page opens in a new tab with correct data
- [ ] 4.2 Run `black --check .`, `openspec validate ticker-details --strict` and `python -m pytest tests -k "api or gui"` and verify all pass
