## ADDED Requirements

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
