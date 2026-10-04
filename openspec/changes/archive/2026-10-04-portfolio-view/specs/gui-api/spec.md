## ADDED Requirements

### Requirement: Open lot details

A successful calculation response MUST include a list of the open lots at the end of the requested year, each with ticker, buy date, quantity, cost per share in the original currency, total cost in PLN, and currency. Existing response fields MUST remain unchanged, and the list MUST be empty when there are no open lots.

#### Scenario: Open lots present

- **WHEN** the requested year ends with open positions
- **THEN** each open lot appears once and the list length equals the reported open positions count

#### Scenario: No open lots

- **WHEN** there are no open positions
- **THEN** the list is present and empty
