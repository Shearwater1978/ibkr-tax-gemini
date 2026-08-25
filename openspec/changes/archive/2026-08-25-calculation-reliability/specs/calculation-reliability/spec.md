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

### Requirement: Report completeness

CLI, Excel, and PDF outputs MUST distinguish a successfully generated report from a report with calculation or export errors.

#### Scenario: Export failure

- GIVEN calculation succeeds but an exporter cannot write its output
- WHEN report generation is requested
- THEN the operation reports an export error and MUST NOT claim that report as successfully generated

## REMOVED Requirements

- None.