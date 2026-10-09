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

### Requirement: Show currency-specific portfolio values
The app SHALL show cost, current value, and unrealized gain or loss as subtotals by currency and SHALL NOT show a converted grand total in the MVP. Market value SHALL use the corresponding current price from `mobile-market-prices`.

#### Scenario: Prices are available
- **WHEN** current prices are available for holdings
- **THEN** the app shows currency-specific values and gains or losses without converting between currencies

#### Scenario: Price is unavailable
- **WHEN** a current price is unavailable for a holding
- **THEN** the app marks that value unavailable and identifies the affected currency subtotal as incomplete

### Requirement: Keep portfolio values informational
The app SHALL label portfolio values as informational and SHALL NOT calculate or export a Polish PIT-38 report or present portfolio values as tax output.

#### Scenario: User reviews portfolio
- **WHEN** the user views holdings or portfolio values
- **THEN** the app identifies them as informational and provides no PIT-38 calculation or tax-report export action

### Requirement: Protect displayed personal data
The overview SHALL NOT show raw account numbers, names, or tax identifiers. Any account reference SHALL be masked according to `mobile-encrypted-drive-backup`.

#### Scenario: Account reference shown
- **WHEN** an account is referenced in the overview
- **THEN** only the permitted masked value is shown

### Requirement: Show empty state
The app SHALL prompt the user to import a supported report when there are no imported reports.

#### Scenario: No imported reports
- **WHEN** the user opens the overview before importing a report
- **THEN** the app shows an empty state with an action to import a report
