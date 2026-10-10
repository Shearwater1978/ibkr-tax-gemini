## Purpose

Gives users a privacy-preserving, informational view of current holdings aggregated from imported IBKR reports.

## ADDED Requirements

### Requirement: Show accumulated holdings using FIFO open lots
The app SHALL show current quantity and average purchase price for each current instrument position, aggregated across imported reports and accounts. Quantities and average prices SHALL be derived from remaining FIFO lots using the established instrument identity and corporate-action rules. Lots in different currencies SHALL be shown as separate currency subpositions rather than averaged together.

#### Scenario: Same instrument and currency in multiple reports
- **WHEN** the same instrument and currency appears in multiple imported reports
- **THEN** the app shows one subposition with the combined remaining quantity and FIFO-based average purchase price

#### Scenario: Fully sold position
- **WHEN** an instrument has no remaining quantity after FIFO matching
- **THEN** it is not listed as a current holding

#### Scenario: Multiple currencies
- **WHEN** remaining lots for an instrument use different currencies
- **THEN** the app shows separate subpositions and does not combine their prices or costs

### Requirement: Show positions with an approximate USD total
Each position SHALL show its values in its own currency. The page header SHALL show the total market value in USD, converting non-USD values with PLN cross rates from the latest NBP table A, labelled approximate and informational, with the rate date. Market value SHALL use the corresponding current price from `mobile-market-prices`.

#### Scenario: Mixed currencies
- **WHEN** positions are held in more than one currency and all prices and rates are available
- **THEN** each row keeps its own currency and the header shows one approximate USD total with the NBP rate date

#### Scenario: Price is unavailable
- **WHEN** a current price is unavailable for a position
- **THEN** the app marks that value unavailable and marks the USD total incomplete

#### Scenario: Rate is unavailable
- **WHEN** no NBP rate is available for a position's currency
- **THEN** the position keeps its own-currency values and the USD total is marked incomplete

### Requirement: Show positions in a sortable table
The app SHALL list positions in a compact table with symbol and listing exchange, last price, daily change, position, and P&L, sorted alphabetically by symbol by default. The user SHALL be able to sort by each column.

#### Scenario: Default order
- **WHEN** the user opens the main page
- **THEN** positions are listed alphabetically by symbol

#### Scenario: Sort by column
- **WHEN** the user selects a column header
- **THEN** positions are sorted by that column, and selecting it again reverses the order

### Requirement: Switch P&L between daily and unrealized
The P&L column SHALL switch between daily P&L (quantity × change since the previous close) and unrealized P&L (market value minus FIFO cost). The header SHALL show daily P&L in USD as an amount and a percentage.

#### Scenario: Daily P&L
- **WHEN** the P&L column is set to daily
- **THEN** each row shows quantity × daily change in its own currency

#### Scenario: Unrealized P&L
- **WHEN** the P&L column is set to unrealized
- **THEN** each row shows market value minus FIFO cost in its own currency

### Requirement: Keep tax output out of the app
The app SHALL NOT calculate or export a Polish PIT-38 report or present portfolio values as tax output. Screens SHALL NOT carry a standing tax or PIT-38 disclaimer.

#### Scenario: User reviews portfolio
- **WHEN** the user views holdings or portfolio values
- **THEN** the app provides no PIT-38 calculation or tax-report export action and shows no tax or PIT-38 notice

### Requirement: Protect displayed personal data
The overview SHALL NOT show account numbers, masked or not, names, or tax identifiers. Elsewhere in the app, any account reference SHALL be masked according to `mobile-encrypted-drive-backup`.

#### Scenario: Overview opened
- **WHEN** the user opens the overview
- **THEN** no account reference is shown

#### Scenario: Account reference shown elsewhere
- **WHEN** an account is referenced outside the overview, for example in the import history
- **THEN** only the permitted masked value is shown

### Requirement: Show empty state
The app SHALL prompt the user to import a supported report when there are no imported reports.

#### Scenario: No imported reports
- **WHEN** the user opens the overview before importing a report
- **THEN** the app shows an empty state with an action to import a report
