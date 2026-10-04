## ADDED Requirements

### Requirement: Dividend payment details

A successful calculation response MUST include a list of the dividend payments of the requested year, each with ex-date, ticker, currency, exchange rate, gross amount in PLN, and tax withheld in PLN. Existing response fields MUST remain unchanged, and the list MUST be empty when there are no dividends.

#### Scenario: Dividends present

- **WHEN** the requested year contains dividends
- **THEN** each payment appears once with its gross and withheld amounts

#### Scenario: No dividends

- **WHEN** the requested year contains no dividends
- **THEN** the list is present and empty
