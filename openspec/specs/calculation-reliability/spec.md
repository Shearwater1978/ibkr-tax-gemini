# Calculation Reliability Specification

## Purpose

Ensure tax calculations and generated reports fail visibly when required exchange rates, inventory matches, or output writes are unavailable, so users never receive silently incorrect totals.

## Requirements

### Requirement: Explicit exchange-rate failures

The calculation MUST NOT substitute rate `1.0` for a missing non-PLN NBP rate. It MUST either stop the calculation or return a result explicitly marked incomplete with the affected date and currency.

#### Scenario: NBP unavailable

- **WHEN** a non-PLN transaction has no usable NBP rate
- **THEN** the calculation is rejected or marked incomplete and identifies the missing rate; it MUST NOT present a normal tax total

### Requirement: Unmatched sell diagnostics

The FIFO calculation MUST detect and expose any sell quantity that cannot be matched to inventory, and a normal report MUST NOT silently include revenue for an unmatched quantity.

#### Scenario: Sell exceeds inventory

- **WHEN** inventory contains fewer shares than a SELL event requests
- **THEN** the calculation returns a blocking diagnostic containing ticker, date, and unmatched quantity

### Requirement: Report completeness

CLI, Excel, and PDF outputs MUST distinguish a successfully generated report from a report with calculation or export errors.

#### Scenario: Export failure

- **WHEN** calculation succeeds but an exporter cannot write its output
- **THEN** the operation reports an export error and MUST NOT claim that report as successfully generated
