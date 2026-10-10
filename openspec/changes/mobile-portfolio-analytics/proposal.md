## Why

The app imports trades, dividends and withholding tax from IBKR reports, but the main page shows only current positions and daily or unrealized P&L. The imported dividends, the gains from past sales, and the transaction history are stored and never shown. Snowball Income–style views (references in `gui/references/`) answer the questions users ask most: how much did I invest, how much have I earned in total, what did dividends bring each month, and what moved today. All of these can be answered from data the app already holds, without a new data source.

## What Changes

- **Dividends tab**: received dividends per month for a chosen year, gross, withholding tax and net, with a year-on-year comparison by month and a per-position list. Values stay in their own currency; totals are shown in USD, approximate, at NBP cross rates.
- **Operations tab**: the imported buys, sells, dividends, withholding tax and corporate actions, newest first, searchable by ticker and filterable by type.
- **Position performance**:
  - summary cards at the top of the main page: value and invested amount, total profit (price gain + gain from sales + net dividends), today's change, and net dividends of the last 12 months;
  - a position detail view, opened by tapping a row, with invested, value, dividends received, price gain, gain from sales and total;
  - a "Show sold positions" switch, which adds fully sold instruments with their gain from sales and dividends.
- **USD view**: a switch on the main page between "Own currency" and "USD"; USD values use the latest NBP cross rates and are labelled approximate.
- **Top movers**: the holdings with the largest daily gains and losses, shown on the main page when today's prices are available.
- Navigation gains two tabs: Portfolio, Dividends, Operations, Imports, Settings.
- Not included: dividend forecasts and calendars, sector or country breakdown, fundamentals (P/E, EPS, beta), and goal tracking. These need data sources the app does not have.

## Capabilities

### New Capabilities
- `mobile-dividends`: Dividends tab with monthly gross, withholding tax and net amounts per year, a year-on-year comparison, and per-position totals from imported reports.
- `mobile-operations`: Operations tab listing imported transactions with search by ticker and filter by type.
- `mobile-position-performance`: Summary cards, gain from sales, per-position profit breakdown, and sold positions on the main page.
- `mobile-usd-view`: Switch to show main-page values in USD at NBP cross rates.
- `mobile-top-movers`: Daily top gainers and losers among the user's holdings.

### Modified Capabilities
<!-- The main page requirements live in `mobile-portfolio-overview`, which is still part of the unarchived `mobile-app-core` change and not yet in `openspec/specs/`. The new capabilities above add to that page without changing its existing requirements. -->

## Impact

- Android app: new screens `DividendsScreen` and `OperationsScreen`, changes to `PortfolioScreen` (cards, USD switch, sold positions, top movers, row tap), and navigation with five tabs.
- Portfolio code: the FIFO matcher also reports gains on matched sells (own currency, commissions excluded, as for open lots today). New read-only queries on the existing `transactions` table. No schema change.
- No new dependency: bar charts are drawn with Compose `Canvas`.
- No new network requests: prices come from the existing providers and rates from the existing NBP table A cache.
- Depends on `mobile-app-core`. Desktop, CLI, tax calculations and PIT-38 logic are unchanged. All amounts are informational and are not tax output.
