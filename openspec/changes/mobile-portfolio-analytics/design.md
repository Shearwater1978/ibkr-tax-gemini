## Context

The Android app keeps imported data in the SQLCipher `transactions` table. Its event types include `BUY`, `SELL`, `DIVIDEND`, `TAX` (withholding tax) and corporate actions. Each row has a date, ticker, quantity, price, amount, currency and fee.

`FifoInventory` replays these events to produce the open lots behind the main page. It applies splits and identity changes, and consumes lots on sells, but it discards what it consumes. Prices come from `PriceService` (Finnhub, or Yahoo without a key). NBP table A rates are cached by `FxService`, and `MainPage.usdTotals` already converts with PLN cross rates.

See `proposal.md` for motivation and `specs/` for the required behavior.

## Goals / Non-Goals

**Goals:**
- Compute everything on the device from the existing table, with no schema change and no new network request.
- Ship the five capabilities as five independent PRs, each under about 1,500 changed lines.
- Keep the FIFO results for open lots unchanged, so the main page numbers stay the same.

**Non-Goals:**
- Historical exchange rates. USD values use the latest NBP table, as the existing header total does.
- Including commissions in cost or gains. This stays an open question across the mobile app; see Open Questions.
- Charts beyond simple bars: no zoom, no tooltips beyond the tapped bar's value.

## Decisions

### Gains from sales come out of the same FIFO pass
`FifoInventory` will return, alongside the open lots, one realized entry per sell: ticker, ISIN, currency, date, quantity, proceeds and the cost of the consumed lots.
- **Alternative considered:** a second matcher just for realized gains. Rejected: it would duplicate the split and identity-change handling and could drift from the open-lot results.
- **Check:** a parity test asserts that open lots are identical before and after the change, on the existing FIFO fixtures.

### Dividends are summed per row date, not linked to their dividend
Monthly and per-position totals add `DIVIDEND` and `TAX` rows by their own date, ticker and currency.
- The desktop tax engine links withholding tax to its dividend for PIT-38. For an informational monthly view, summing by row date gives the same totals per position and year. It also places later corrections in the month they happen, which is what the spec asks for.
- **Alternative considered:** reuse the desktop linking. Rejected: it is unnecessary for these totals and would bring tax logic into the app.

### Queries in a read-only repository, aggregation in pure Kotlin
- An `AnalyticsRepository` reads the rows it needs:
  - dividend and tax rows for a year range;
  - operations, paged with `LIMIT`/`OFFSET` and filtered in SQL by type and `ticker LIKE`.
- Pure Kotlin objects aggregate the rows: `Dividends.byMonth`, `Dividends.byPosition`, `Performance.summary`, `Performance.position`, `TopMovers.rank` and the USD conversion. This keeps the logic unit-testable on the JVM, like `MainPage` today.
- The existing `ticker` and `date` index covers the queries. An operations query with no ticker filter scans by date, which is acceptable for the expected tens of thousands of rows. The instrumented test checks it with 20,000 synthetic rows.

### Bars drawn with Compose Canvas
- Bars are drawn with a small `BarChart` composable. Each bar has an accessible label (month and value) through `semantics`, so tests and screen readers can read it.
- **Alternative considered:** a chart library such as Vico or MPAndroidChart. Rejected: a new dependency for two simple bar charts. AGENTS.md asks for new dependencies only when needed.

### Navigation and placement
- The bottom bar gets five tabs: Portfolio, Dividends, Operations, Imports, Settings.
- On the Portfolio page, top to bottom:
  1. summary cards (in a two-by-two grid);
  2. the price status line;
  3. top movers, collapsed to three per list with a "Show more" action;
  4. the P&L and currency switches;
  5. the positions table;
  6. the sold-positions switch.
- Tapping a row opens a details sheet: a modal bottom sheet, not a new screen, so the main page keeps its scroll position.

### USD view reuses the header conversion
- Row conversion uses the same cross-rate function as `usdTotals`, moved into a shared `UsdConverter`. Rows and totals then cannot disagree.
- The mode and the sold-positions switch are remembered in the existing settings preferences. They are not sensitive.

### PR order
Each PR stands on `main` alone. The order follows shared groundwork:
1. **Operations:** repository paging and the new tab structure.
2. **Dividends:** dividend aggregation and the bar chart.
3. **Position performance:** realized FIFO output, summary cards, details sheet and sold positions. This uses the dividend aggregation from PR 2.
4. **USD view.**
5. **Top movers.**

## Risks / Trade-offs

- [Latest-rate conversion misstates past dividends and gains in USD] → Label all USD amounts approximate with the rate date. Every own-currency total is also shown.
- [Reports imported before this change lack sells or dividends from earlier years] → Totals cover only imported periods. The Dividends tab names the first and last month that has data.
- [Unmatched sells (a sell without enough bought shares)] → The main page already reports these as incomplete data. The performance cards show the same incomplete state and no partial numbers.
- [Five tabs crowd small screens] → Use icon plus short label. On widths under 360 dp, the labels show only for the selected tab.
- [Main page grows long] → Top movers collapse to three per list. The cards use a compact two-by-two grid.

## Migration Plan

- No schema or data migration. Each PR is backward compatible and can be reverted alone.
- The version code keeps increasing with the commit count, as today.

## Open Questions

- Should commissions be included in cost and gains? The mobile app excludes them today (`mobile-app-core` open question). If the answer changes, the change applies to open lots and gains together, without changing these specs' structure.
