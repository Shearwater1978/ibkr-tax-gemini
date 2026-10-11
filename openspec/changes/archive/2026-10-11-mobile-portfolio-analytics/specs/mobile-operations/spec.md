## Purpose

Lets users review the transactions the app imported from IBKR reports (buys, sells, dividends, withholding tax and corporate actions) and find them by ticker or type.

## ADDED Requirements

### Requirement: List imported operations
The app SHALL provide an Operations tab that lists the imported transactions, newest first. Each entry SHALL show the date, type, ticker, quantity and price where they apply, the amount with its currency, and the commission where one exists. Account numbers and report file names SHALL NOT be shown.

#### Scenario: Operations exist
- **WHEN** the user opens the Operations tab after importing reports
- **THEN** the app lists the transactions newest first with date, type, ticker, amount and currency

#### Scenario: Many operations
- **WHEN** thousands of transactions are imported
- **THEN** the list stays responsive while the user scrolls

### Requirement: Search and filter operations
The user SHALL be able to search operations by ticker and to filter them by type: buys, sells, dividends, withholding tax and corporate actions. Search and filters SHALL combine.

#### Scenario: Search by ticker
- **WHEN** the user enters a ticker
- **THEN** only operations for tickers that contain the entered text, ignoring case, are listed

#### Scenario: Filter by type
- **WHEN** the user selects the "Dividends" filter
- **THEN** only dividend operations are listed

#### Scenario: Nothing matches
- **WHEN** no operation matches the search and filters
- **THEN** the app says that nothing matches and offers to clear the search and filters

### Requirement: Show an empty state
The app SHALL prompt the user to import a report when no operations exist.

#### Scenario: No imports
- **WHEN** the user opens the Operations tab before importing a report
- **THEN** the app shows an empty state with an action to import a report
