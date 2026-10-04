## ADDED Requirements

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
