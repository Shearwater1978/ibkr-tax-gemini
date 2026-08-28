# fifo-coverage-check Specification

## Purpose

Provide a non-destructive preflight check that confirms whether imported broker history contains enough asset quantity and FIFO lot evidence to support a planned sale before the user relies on the result for a tax return.

## Requirements

### Requirement: Planned sale input

The coverage check MUST accept one or more planned sale items, each containing a ticker, a positive quantity, and an as-of date. Price, commission, currency conversion, and tax amount MUST be optional or ignored by this operation.

#### Scenario: Multiple planned assets

- **WHEN** the user submits planned sales for multiple tickers
- **THEN** the operation evaluates every item independently and returns one result per ticker

### Requirement: FIFO quantity coverage

The coverage check MUST reconstruct available quantity as of the requested date using chronological transaction ordering and applicable corporate actions, then compare it with the requested quantity without persisting a sale.

#### Scenario: Fully covered sale

- **WHEN** imported history contains at least the requested quantity for a ticker as of the planned sale date
- **THEN** the result status is `COVERED` and includes requested and available quantities

#### Scenario: Missing acquisition history

- **WHEN** imported history contains less than the requested quantity for a ticker
- **THEN** the result status is `PARTIAL` or `NOT_COVERED`, includes the missing quantity, and identifies that additional broker history is required

### Requirement: FIFO lot evidence

A covered or partially covered result MUST include the acquisition lots consumed in FIFO order, including ticker, acquisition date, quantity contribution, and source record identity when available.

#### Scenario: Lot trace

- **WHEN** a planned sale is covered by multiple historical lots
- **THEN** the result lists the lots in oldest-first order and their quantities sum to the covered quantity

### Requirement: Non-destructive operation

The coverage check MUST NOT insert, update, delete, or persist a `SELL` transaction and MUST NOT alter the inventory used by a subsequent tax calculation.

#### Scenario: Check followed by calculation

- **WHEN** the user runs a coverage check and then runs the normal tax calculation
- **THEN** the calculation sees the same database records and inventory it would have seen without the check

### Requirement: Report output

The operation MUST produce human-readable report output and machine-readable results containing an overall status, per-asset status, requested quantity, available quantity, missing quantity, as-of date, and evidence lots.

#### Scenario: Mixed result report

- **WHEN** one planned asset is covered and another is not covered
- **THEN** the report shows both per-asset outcomes and an overall incomplete status
