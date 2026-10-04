## 1. API per-ticker summary

- [x] 1.1 Add `by_ticker` (ticker, profit, dividends, sales; sorted by ticker) to `/calculate/{year}` in `gui/backend/api.py` and verify with an API test covering a dividend-only ticker and a sale-only ticker
- [x] 1.2 Add a test asserting existing response fields (`summary`, `pdf_available`, `excel_available`, `complete`) are unchanged

## 2. Portal structure

- [x] 2.1 Implement Dashboard, Tax Calculation, Data and Coverage tabs with a single active page and verify by switching tabs in the browser
- [x] 2.2 Keep the calculate flow on the Tax Calculation tab, preserve the previous Dashboard result on failure, and verify with a mocked failing response
- [x] 2.3 Show the Dashboard empty state and auto-switch to the Dashboard after success, verified with a mocked successful response

## 3. Per-ticker breakdown

- [x] 3.1 Render the donut and table with escaped ticker text and verify a ticker such as `<b>X</b>` displays literally
- [x] 3.2 Cap the chart at 10 slices plus "Other", and add a scrollable table with ticker filter; verify with mock data of 30+ tickers
- [x] 3.3 Verify against the real backend (`/calculate/2024`) that the Dashboard renders without layout problems

## 4. Documentation

- [x] 4.1 Run `openspec validate web-portal-ui --strict` and the existing API/GUI tests (`python -m pytest tests -k "api or gui"`) and verify both pass
