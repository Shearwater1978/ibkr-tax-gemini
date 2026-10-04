## Why

The desktop UI is a single long page of stacked cards. It has no navigation, no per-ticker view of results, and no documented visual or structural contract. A tabbed, dashboard-style portal modelled on the references in `gui/references/` makes results easier to review and gives future UI work a specified baseline.

## What Changes

- Restructure the renderer into a tabbed portal: Dashboard, Tax Calculation, Data, and Coverage.
- Move the year selector and calculate action to the Tax Calculation tab; show results on the Dashboard tab and switch to it after a successful calculation.
- Add a Dashboard breakdown: KPI cards, a per-ticker donut chart, and a per-ticker table (sales, P&L, dividends, share).
- Extend the calculate response with a `by_ticker` summary (additive, non-breaking).
- Keep the breakdown usable for large portfolios (hundreds of tickers): top-N slices plus "Other" in the chart, and a scrollable, searchable table.
- Apply a dark theme consistent with `gui/references/`.

## Capabilities

### New Capabilities

- `web-portal`: Navigation structure, dashboard results presentation, per-ticker breakdown, and visual theme of the renderer UI.

### Modified Capabilities

- `gui-api`: The calculation response gains a per-ticker summary.

## Impact

- `gui/ui/index.html` (layout, styles, scripts).
- `gui/backend/api.py` (`/calculate/{year}` response gains `by_ticker`).
- No new dependencies; charts are drawn with CSS. No database, calculation, or Electron security boundary changes.
