## Why

Tickers appear in four tables, but there is no way to see everything that happened with one ticker, and no quick context when pointing at it. Users want a hover summary and a dedicated page per ticker that opens in a new browser tab so the table they were reading stays in place.

## What Changes

- The calculation response includes the year's sales (`sales`) so a ticker's full activity can be shown.
- Every ticker in Dashboard, Dividends, Analytics, and Portfolio becomes a link that opens a ticker page in a new browser tab, showing summary figures and three tables: sales (with proceeds, cost, and P&L), dividend payments, and open lots.
- Hovering or focusing a ticker shows a hover card with its share of total open cost, realized P&L, dividends, and total result, in the selected display currency.
- The last successful calculation is saved in the browser so the new tab can read it without recalculating.

## Capabilities

### New Capabilities

### Modified Capabilities
- `web-portal`: add the ticker page, new-tab links, and hover card requirements.
- `gui-api`: add sale details to the calculation response.

## Impact

- `gui/backend/api.py` (new `sales` field), `tests/test_api.py`, `gui/ui/index.html`. No dependency or data changes. Builds on the currency switcher for display currency.
