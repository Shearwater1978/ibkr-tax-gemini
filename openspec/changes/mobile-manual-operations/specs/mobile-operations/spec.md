## MODIFIED Requirements

### Requirement: List imported operations
The app SHALL provide an Operations tab that lists the stored transactions, imported and manual, newest first. Each
entry SHALL show the date, type, ticker, quantity and price where they apply, the amount with its currency, and the
commission where one exists. Manual operations SHALL carry a "Manual" label and open for editing when tapped. Account
numbers and report file names SHALL NOT be shown.

#### Scenario: Operations exist
- **WHEN** the user opens the Operations tab after importing reports
- **THEN** the app lists the transactions newest first with date, type, ticker, amount and currency

#### Scenario: Many operations
- **WHEN** thousands of transactions are imported
- **THEN** the list stays responsive while the user scrolls

#### Scenario: Manual operation in the list
- **WHEN** the user has added a manual buy
- **THEN** it appears in date order among imported operations with a "Manual" label, and tapping it opens edit

### Requirement: Show an empty state
The app SHALL prompt the user to import a report or add an operation when no operations exist.

#### Scenario: No imports
- **WHEN** the user opens the Operations tab before importing a report or adding an operation
- **THEN** the app shows an empty state with actions to import a report and to add an operation
