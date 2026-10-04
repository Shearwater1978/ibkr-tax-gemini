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
