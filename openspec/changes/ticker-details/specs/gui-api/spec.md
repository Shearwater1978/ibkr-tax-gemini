## ADDED Requirements

### Requirement: Sale details

A successful calculation response MUST include a list of the realized sales of the requested year, each with ticker, sale date, quantity, sale price, currency, sale amount in PLN, cost basis in PLN, and profit or loss in PLN. Existing response fields MUST remain unchanged, and the list MUST be empty when there are no sales.

#### Scenario: Sales present

- **WHEN** the requested year contains sales
- **THEN** each sale appears once and the per-ticker profit equals the sum of its sales' profit

#### Scenario: No sales

- **WHEN** the requested year contains no sales
- **THEN** the list is present and empty
