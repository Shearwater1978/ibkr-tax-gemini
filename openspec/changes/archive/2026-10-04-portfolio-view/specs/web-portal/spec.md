## ADDED Requirements

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
