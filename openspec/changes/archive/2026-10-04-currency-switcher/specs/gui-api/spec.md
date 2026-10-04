## ADDED Requirements

### Requirement: Display exchange rates

A successful calculation response MUST include display exchange rates: the NBP rate date and the PLN value of one USD and one EUR on that date, where the date is the last day of the requested year or the current date for a year still in progress. A currency whose rate cannot be obtained MUST be omitted without failing the calculation, and existing response fields MUST remain unchanged.

#### Scenario: Rates present

- **WHEN** a calculation succeeds for a completed year
- **THEN** the response contains the rate date and USD and EUR rates

#### Scenario: Rate unavailable

- **WHEN** NBP has no rate for EUR
- **THEN** the response omits EUR, still succeeds, and keeps the USD rate
