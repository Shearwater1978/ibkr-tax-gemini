## ADDED Requirements

### Requirement: Per-ticker calculation summary

A successful calculation response MUST include a per-ticker summary list, sorted by ticker, where each entry contains the ticker, the sum of realized profit or loss in PLN, the sum of gross dividends in PLN for the requested year, and the number of realized sales. Tickers having only dividends or only sales MUST still be included, and existing response fields MUST remain unchanged.

#### Scenario: Ticker with dividends only

- **WHEN** a ticker has dividends but no realized sales in the requested year
- **THEN** the summary contains that ticker with zero profit and zero sales

#### Scenario: Backward compatibility

- **WHEN** a client ignores the per-ticker summary
- **THEN** all previously documented response fields are present with unchanged meaning
