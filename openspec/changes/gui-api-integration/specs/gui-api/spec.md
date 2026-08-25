## Purpose

Provide a stable local HTTP contract that lets the desktop dashboard import transactions, calculate tax reports, surface diagnostics, and open generated files without duplicating calculation logic.

## ADDED Requirements

### Requirement: Year discovery

The local API MUST return available transaction years in descending order and MUST return an empty list when the database contains no dated transactions.

#### Scenario: Years are available

- **WHEN** the client requests available years
- **THEN** the API returns a successful response containing unique years sorted newest first

### Requirement: Import result

The import endpoint MUST execute the atomic import workflow and MUST report inserted and skipped duplicate counts.

#### Scenario: Repeated import

- **WHEN** the client imports the same source data twice
- **THEN** the first response reports inserted records and the second reports zero new records with skipped duplicates

### Requirement: Calculation diagnostics

The calculation endpoint MUST preserve calculation diagnostics as structured error responses and MUST return 404 when no transactions exist through the requested year.

#### Scenario: Missing year data

- **WHEN** the client requests a year with no available transactions
- **THEN** the API returns HTTP 404 with a stable error body

#### Scenario: Unreliable calculation

- **WHEN** NBP rates are unavailable or FIFO reports an unmatched quantity
- **THEN** the API returns a non-success response containing the diagnostic code and relevant date, ticker, currency, or quantity

### Requirement: Report availability

The calculation endpoint MUST report Excel and PDF availability independently and MUST set availability to false when the corresponding export fails.

#### Scenario: One export fails

- **WHEN** calculation succeeds but PDF generation fails
- **THEN** the response reports a successful calculation, marks PDF unavailable, and does not claim a PDF file exists

### Requirement: Safe report opening

Report-opening endpoints MUST accept only generated files for the requested year and MUST return 404 when the file does not exist.

#### Scenario: Missing report

- **WHEN** the client requests an output file that has not been generated
- **THEN** the API returns HTTP 404 without attempting to open an arbitrary path
