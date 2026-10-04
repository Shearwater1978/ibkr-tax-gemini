## Why

The reference portal's Portfolio tab lists held assets with quantity and cost. Our app computes open FIFO lots for the tax report but the UI only shows their count, so users cannot see what they hold or at what cost.

## What Changes

- `/calculate/{year}` returns the open FIFO lots (`inventory`) with ticker, buy date, quantity, cost per share, total cost in PLN, and currency.
- Add a Portfolio tab with summary cards (open tickers, open lots, total cost PLN) and a per-ticker table (ticker, currency, quantity, lots, total cost PLN, average cost PLN per share, oldest buy date) with filter, sorting, pagination, and totals.
- Each ticker row can be expanded to show its individual lots.

## Capabilities

### New Capabilities

### Modified Capabilities
- `web-portal`: add the Portfolio tab requirements.
- `gui-api`: add open lot details to the calculation response.

## Impact

- `gui/backend/api.py` (one new response field), `tests/test_api.py`, `gui/ui/index.html`. No dependency or data changes.
