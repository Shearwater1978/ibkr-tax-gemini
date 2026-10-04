## Purpose

Define the navigation, results presentation, and visual conventions of the renderer UI so the tax workflow is reviewable per ticker and consistent across tabs.

## Requirements

### Requirement: Tabbed navigation

The portal MUST provide top-level navigation with Dashboard, Tax Calculation, Data, and Coverage tabs, MUST show exactly one tab's content at a time, and MUST highlight the active tab.

#### Scenario: Switching tabs

- **WHEN** the user selects a tab
- **THEN** only that tab's content is visible and the tab is marked active

#### Scenario: Initial view

- **WHEN** the portal loads
- **THEN** the Dashboard tab is active

### Requirement: Calculation on its own tab

The year selector, calculate action, progress indicator, and calculation status or error messages MUST be located on the Tax Calculation tab, and the calculate action MUST be disabled while a calculation is running.

#### Scenario: Calculation fails

- **WHEN** the backend returns an error for a calculation
- **THEN** the error is shown on the Tax Calculation tab and any previously displayed Dashboard results remain unchanged

### Requirement: Dashboard results

The Dashboard MUST show an empty state with a link to the Tax Calculation tab until a calculation succeeds. After a successful calculation it MUST show the selected year, realized P&L, gross dividends, open lots, and the report-opening actions available for that year, and MUST switch to the Dashboard automatically.

#### Scenario: No calculation yet

- **WHEN** the Dashboard is opened before any successful calculation
- **THEN** an empty state is shown and no result values are displayed

#### Scenario: Successful calculation

- **WHEN** a calculation succeeds
- **THEN** the portal shows the Dashboard with results labelled by the calculated year

#### Scenario: Sign coloring

- **WHEN** realized P&L is negative
- **THEN** it is visually distinguished from a non-negative value

### Requirement: Per-ticker breakdown

The Dashboard MUST present a per-ticker breakdown as a proportional chart and a table with ticker, number of sales, P&L, gross dividends, and share. Share MUST be derived from the ticker's absolute P&L plus dividends relative to the total, and ticker symbols MUST be rendered as text, not markup.

#### Scenario: Losing ticker

- **WHEN** a ticker has negative P&L
- **THEN** it still receives a proportional share and its P&L is shown as negative

#### Scenario: Unsafe ticker text

- **WHEN** a ticker value contains markup characters
- **THEN** they are displayed literally and not interpreted

### Requirement: Large portfolio usability

For portfolios with many tickers, the chart MUST group the smallest tickers into a single "Other" slice beyond a fixed maximum number of slices, and the table MUST remain scrollable and searchable by ticker.

#### Scenario: Many tickers

- **WHEN** the breakdown contains more tickers than the maximum slice count
- **THEN** the chart shows the largest tickers individually plus one "Other" slice, and the table still lists every ticker

### Requirement: Visual theme

The portal MUST use a dark theme with card-based layout, with gains and losses distinguished by color in addition to sign.

#### Scenario: Reference consistency

- **WHEN** a screen is rendered
- **THEN** it uses the shared dark palette, cards, and top navigation bar across all tabs

### Requirement: Dividends tab

The portal MUST provide a Dividends tab, in addition to the existing tabs, that lists the dividend payments of the most recently calculated year with date, ticker, currency, exchange rate, gross amount, tax withheld, and net amount in PLN. Before any successful calculation it MUST show an empty state linking to the Tax Calculation tab. A failed calculation MUST NOT replace previously displayed dividends.

#### Scenario: Payments listed

- **WHEN** a calculation succeeds with dividend payments
- **THEN** the Dividends tab lists each payment with its gross, withheld, and net amounts

#### Scenario: No calculation yet

- **WHEN** the Dividends tab is opened before any successful calculation
- **THEN** an empty state is shown instead of an empty table

#### Scenario: Year without dividends

- **WHEN** a calculation succeeds with no dividends
- **THEN** the tab states that no dividends were received and shows zero totals

### Requirement: Dividends totals

The Dividends tab MUST show a totals row for gross, withheld, and net amounts that covers all payments of the year regardless of the active filter or page, and MUST also show the filtered totals when a filter is active.

#### Scenario: Filter active

- **WHEN** the user filters by ticker
- **THEN** the table shows only matching payments and the filtered totals are shown alongside the unfiltered year totals

### Requirement: Dividends table navigation

The Dividends table MUST support filtering by ticker, sorting by any column, and pagination with a selectable page size, and MUST render ticker text without interpreting markup.

#### Scenario: Pagination

- **WHEN** there are more payments than the page size
- **THEN** only one page is shown with a position indicator such as "Showing 1-25 of 64", and the user can move between pages

#### Scenario: Sorting

- **WHEN** the user selects a column header
- **THEN** rows are ordered by that column and selecting it again reverses the order

### Requirement: Analytics tab

The portal MUST provide an Analytics tab showing, for the most recently calculated year, summary cards for realized P&L, gross dividends, total result (their sum), and number of tickers, and a per-ticker table with ticker, number of sales, realized P&L, gross dividends, and total result in PLN. Before any successful calculation it MUST show an empty state, and a failed calculation MUST NOT replace previously displayed data.

#### Scenario: Table listed

- **WHEN** a calculation succeeds
- **THEN** the Analytics tab shows the summary cards and one row per ticker

#### Scenario: No calculation yet

- **WHEN** the Analytics tab is opened before any successful calculation
- **THEN** an empty state is shown instead of a table

### Requirement: Analytics table navigation

The Analytics table MUST support filtering by ticker, sorting by any column, pagination with a selectable page size, a toggle to hide tickers without sales, and a totals row covering all tickers of the year regardless of filter or page. Ticker text MUST be rendered without interpreting markup and positive and negative amounts MUST be visually distinguished.

#### Scenario: Sorting

- **WHEN** the user selects a column header
- **THEN** rows are ordered by that column and selecting it again reverses the order

#### Scenario: Hide tickers without sales

- **WHEN** the toggle is enabled
- **THEN** tickers with zero sales are hidden and the year totals are unchanged

### Requirement: Portfolio tab

The portal MUST provide a Portfolio tab showing the open positions after the most recently calculated year: summary cards for number of tickers, number of open lots, and total cost in PLN, and a per-ticker table with ticker, currency, quantity, number of lots, total cost in PLN, average cost per share in PLN, and oldest buy date. Before any successful calculation it MUST show an empty state, a failed calculation MUST NOT replace previously displayed data, and a year with no open positions MUST state so with zero totals.

#### Scenario: Positions listed

- **WHEN** a calculation succeeds with open lots
- **THEN** the Portfolio tab shows one row per ticker with aggregated quantity and cost

#### Scenario: No calculation yet

- **WHEN** the Portfolio tab is opened before any successful calculation
- **THEN** an empty state is shown instead of a table

#### Scenario: No open positions

- **WHEN** a calculation succeeds with no open lots
- **THEN** the tab states there are no open positions and shows zero totals

### Requirement: Portfolio lot details

Each ticker row MUST be expandable to show its individual open lots with buy date, quantity, cost per share in the original currency, and total cost in PLN.

#### Scenario: Expand a ticker

- **WHEN** the user expands a ticker row
- **THEN** the lots of that ticker are listed, and collapsing hides them

### Requirement: Portfolio table navigation

The Portfolio table MUST support filtering by ticker, sorting by any column, pagination with a selectable page size, and a totals row covering all tickers regardless of filter or page, and MUST render ticker text without interpreting markup.

#### Scenario: Filter and sort

- **WHEN** the user filters by ticker and selects a column header
- **THEN** only matching tickers are shown, ordered by that column, with year totals unchanged

### Requirement: Reports navigation menu

The top bar MUST group the Dividends, Analytics, and Portfolio views under a "Reports" dropdown menu, opened by click and closed by selecting an item, clicking outside, or pressing Escape. The "Reports" entry MUST appear active whenever one of its views is shown.

#### Scenario: Open a report from the menu

- **WHEN** the user opens the Reports menu and selects Analytics
- **THEN** the Analytics view is shown, the menu closes, and the Reports entry is highlighted

#### Scenario: Dismiss the menu

- **WHEN** the menu is open and the user presses Escape or clicks elsewhere
- **THEN** the menu closes without changing the current view

### Requirement: Global calculation controls

The top bar MUST contain the year selector and a Calculate button available on every view. Starting a calculation MUST show the Workspace view with its progress indicator and status, and the Calculate button MUST be disabled while a calculation is running.

#### Scenario: Calculate from a report view

- **WHEN** the user selects a year and presses Calculate while viewing Dividends
- **THEN** the Workspace view with the progress indicator is shown, and on success the Dashboard is shown with the new results

#### Scenario: Calculation fails

- **WHEN** a calculation started from the top bar fails
- **THEN** the Workspace view remains visible with the error and previous results are unchanged

### Requirement: Workspace tab

The portal MUST provide a single Workspace tab that shows, on one page, the Tax Calculation progress and status, the Data management controls (CSV import and IB connections), and the FIFO coverage preflight. The separate Tax Calculation, Data, and Coverage tabs MUST NOT be present, and all their existing controls MUST keep working unchanged.

#### Scenario: All tools on one page

- **WHEN** the user opens the Workspace tab
- **THEN** calculation status, data controls, and the coverage check are all visible on the same page

#### Scenario: Existing controls keep working

- **WHEN** the user runs an import, an IB check, or a coverage check from the Workspace page
- **THEN** each behaves and reports its result as it did before

### Requirement: Automatic data loading on start

When the portal starts and the backend is ready, it MUST import the statement files, load the available years, select the latest year, and calculate it without any user action, showing the Dashboard when finished. A failed import MUST NOT prevent calculating already stored data. When no years are available, the portal MUST NOT calculate and MUST leave manual import available.

#### Scenario: Start with data

- **WHEN** the portal starts with stored or importable data
- **THEN** the Dashboard shows results for the latest year without interaction

#### Scenario: Import fails

- **WHEN** the automatic import fails but years exist in storage
- **THEN** the latest year is still calculated and the import error is visible in the Workspace

#### Scenario: No data

- **WHEN** no years are available
- **THEN** no calculation runs and the empty state remains

### Requirement: Currency switcher

The top bar MUST contain a currency selector offering PLN, USD, and EUR. Selecting a currency MUST convert every monetary amount shown in the Dashboard, Dividends, Analytics, and Portfolio views, including totals and summary cards, using the display rates of the last calculation, and MUST label amounts with the selected currency. The selection MUST be remembered between sessions, MUST fall back to PLN when no rate is available for it, and a note MUST state that conversion is for display only. Original-currency fields and the exchange-rate column MUST NOT be converted.

#### Scenario: Switch to USD

- **WHEN** a calculation has succeeded and the user selects USD
- **THEN** all amounts in the views are shown in USD at the displayed rate and the labels say USD

#### Scenario: No calculation yet

- **WHEN** no calculation has succeeded
- **THEN** only PLN is selectable

#### Scenario: Rate unavailable

- **WHEN** the display rate for a currency is missing from the calculation response
- **THEN** that currency is disabled and PLN is shown
