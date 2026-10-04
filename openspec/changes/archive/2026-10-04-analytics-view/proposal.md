## Why

The reference portal's Analytics tab gives a sortable per-asset table with totals, which is the main way to see which assets drive results. Our Dashboard only shows a top-10 donut and a short per-ticker list, so users cannot compare all tickers.

## What Changes

- Add an Analytics tab with summary cards (realized P&L, dividends, total result, tickers) and a full per-ticker table.
- The table shows ticker, sales count, realized P&L, gross dividends and total result, with ticker filter, sorting, pagination, totals row, and a "hide tickers without sales" toggle.
- Uses the existing `by_ticker` data of the last calculation; no backend change.

## Capabilities

### New Capabilities

### Modified Capabilities
- `web-portal`: add the Analytics tab requirements.

## Impact

- `gui/ui/index.html` only (new tab, table, JS). No API, dependency or data changes.
