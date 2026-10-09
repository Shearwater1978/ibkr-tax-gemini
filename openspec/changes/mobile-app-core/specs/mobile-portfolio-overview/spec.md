## Purpose

Gives the user one accumulated view of all stocks they hold, derived from imported broker reports.

## ADDED Requirements

### Requirement: Accumulated holdings per stock
The app SHALL show, for each stock, the total quantity held and the average purchase price, aggregated across all imported reports and accounts.

#### Scenario: Same stock in several reports
- **WHEN** the same stock appears in several imported reports
- **THEN** the overview shows a single row with the combined quantity and average cost

#### Scenario: Fully sold position
- **WHEN** a stock's quantity is zero after all trades
- **THEN** it is not listed among current holdings

### Requirement: Portfolio totals
The app SHALL show total cost and total current value of all holdings, and unrealized gain or loss, using the current prices from `mobile-market-prices`.

#### Scenario: Totals with prices
- **WHEN** prices are available for all holdings
- **THEN** totals equal the sum of quantity multiplied by price and cost for each holding

#### Scenario: Missing price
- **WHEN** a price is unavailable for some holding
- **THEN** the app marks that holding as having no price and states that totals are partial

### Requirement: Privacy of displayed data
The overview SHALL NOT show raw account numbers, names or tax IDs; any account reference SHALL be masked as defined by `mobile-encrypted-drive-backup`.

#### Scenario: Account shown
- **WHEN** an account is referenced in the overview
- **THEN** only a masked value is shown

### Requirement: Empty state
The app SHALL show a prompt to import a report when no reports have been imported.

#### Scenario: No data
- **WHEN** the user opens the overview without any imported report
- **THEN** the app shows an empty state with an action to upload a report
