## Purpose

Defines how the app obtains and shows current stock prices so that displayed values are correct and clearly up to date.

## ADDED Requirements

### Requirement: Current price retrieval
The app SHALL retrieve the latest available price for each held stock from a market data provider while the overview is open and on user refresh.

#### Scenario: Price refresh
- **WHEN** the user opens the overview or pulls to refresh
- **THEN** the app requests current prices for the held stocks and updates the displayed values

### Requirement: Price freshness and currency
The app SHALL show the time of each price and its currency, and SHALL mark a price as stale when it is older than the freshness limit defined in design.md.

#### Scenario: Fresh price
- **WHEN** a price was retrieved within the freshness limit
- **THEN** it is shown with its timestamp and currency

#### Scenario: Stale price
- **WHEN** the last successful price is older than the freshness limit
- **THEN** the app shows it as stale and never presents it as current

### Requirement: Failure handling
The app SHALL keep working when prices cannot be retrieved.

#### Scenario: Offline or provider error
- **WHEN** the price request fails
- **THEN** the app shows the last known price marked as stale, or no price if none exists, and informs the user

### Requirement: Minimal data sent to provider
The app SHALL send only the instrument identifiers (ticker symbols) needed to get prices, and SHALL NOT send quantities, account data, names or other personal data.

#### Scenario: Price request content
- **WHEN** the app requests prices
- **THEN** the request contains only instrument identifiers
