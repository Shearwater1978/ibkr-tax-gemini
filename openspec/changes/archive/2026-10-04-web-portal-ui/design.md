## Context

The renderer is a single static `gui/ui/index.html` loaded by Electron with context isolation and no Node integration; it talks to the FastAPI backend over HTTP. See proposal.md for motivation. Reference screenshots live in `gui/references/`. The calculate endpoint already holds realized gains and dividends in memory, each tagged by ticker. Real data can contain hundreds of tickers (about 280 open lots in one tax year).

## Goals / Non-Goals

**Goals:**
- Tabbed structure and dashboard layout with no new dependencies.
- Per-ticker data supplied by the backend so the UI does no tax logic.

**Non-Goals:**
- No framework, bundler, or charting library.
- No change to FIFO, NBP, or report generation, and no Electron security changes.
- No historical comparison across years.

## Decisions

- **Single file, vanilla JS tabs.** Tabs toggle `.page` sections via a small `showPage` function. Alternative: a SPA framework; rejected as unnecessary weight for four static views and a packaging change.
- **CSS `conic-gradient` donut.** Gives a chart with no dependency and no CSP changes. Alternative: Chart.js; rejected to avoid a bundled dependency. Trade-off: no tooltips, so the table carries the detail.
- **Aggregate in the API, not the client.** `by_ticker` is built from `realized_gains` and `dividends` inside `/calculate/{year}`. Keeps one source of truth and an additive, backward-compatible response.
- **Share metric uses |P&L| + dividends.** A signed share would give losing tickers zero or negative slices; absolute weight keeps every contributor visible. It is a visualization weight, not a tax figure, and the UI labels it as share.
- **Top-N with "Other".** The chart caps slices (10) and folds the rest into one slice; the table lists all tickers in a scroll container with a client-side ticker filter.
- **Escape ticker text.** Ticker values originate from imported broker files, so the table escapes them before insertion.
- **Dashboard keeps last good result.** Failures are shown on the Tax Calculation tab only, consistent with the existing error-presentation requirement in `desktop-dashboard`.

## Risks / Trade-offs

- [Large tables slow the renderer] → Scroll container and filter; the data is a few hundred rows at most.
- [Share could be mistaken for a tax value] → Column labelled "Share" with a hint that it is a display weight.
- [Single HTML file grows] → Acceptable now; revisit splitting scripts and styles if it keeps growing.

## Migration Plan

No data migration. Ship UI and API together; older clients ignore the new `by_ticker` field. Rollback is reverting the two files.
