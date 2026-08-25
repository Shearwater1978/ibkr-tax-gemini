# Delta Specification: Calculation Reliability

## ADDED Requirements

### Requirement: Explicit exchange-rate failures

The calculation MUST NOT substitute rate `1.0` for a missing non-PLN NBP rate. It MUST either stop the calculation or return a result explicitly marked incomplete with the affected date and currency.

#### Scenario: NBP unavailable

- GIVEN a non-PLN transaction has no usable NBP rate
- WHEN the requested year is calculated
- THEN the result is rejected or marked incomplete and identifies the missing rate; it MUST NOT present a normal tax total

### Requirement: Unmatched sell diagnostics

The FIFO calculation MUST detect and expose any sell quantity that cannot be matched to inventory, and a normal report MUST NOT silently include revenue for an unmatched quantity.

#### Scenario: Sell exceeds inventory

- GIVEN inventory contains fewer shares than a SELL event requests
- WHEN FIFO processing runs
- THEN the calculation returns a blocking diagnostic containing ticker, date, and unmatched quantity

### Requirement: Calculation completeness

CLI, API, Excel, and PDF outputs MUST share a completeness status and MUST distinguish a successfully generated report from a report with calculation or export errors.

#### Scenario: Export failure

- GIVEN calculation succeeds but an exporter cannot write its output
- WHEN the report endpoint completes
- THEN the response indicates that export failed and MUST NOT claim that report as available

## MODIFIED Requirements

### Requirement: Calculation API errors

The API MUST preserve intentional client errors such as no data found as their corresponding HTTP status instead of converting them to generic 500 responses.

#### Scenario: No transactions for year

- GIVEN no transactions exist through the requested year
- WHEN `GET /calculate/{year}` is called
- THEN the API returns 404 with a stable error body

## REMOVED Requirements

- None.