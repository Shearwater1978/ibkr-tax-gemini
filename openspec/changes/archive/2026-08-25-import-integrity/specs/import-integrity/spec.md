# Delta Specification: Import Integrity

## ADDED Requirements

### Requirement: Atomic import

An import MUST validate and persist its complete batch atomically; a failed batch MUST NOT delete or partially overwrite previously imported transactions.

#### Scenario: Invalid batch

- GIVEN the database contains valid historical transactions
- WHEN an import batch contains an invalid record that prevents persistence
- THEN the operation fails and the historical transactions remain available unchanged

### Requirement: Idempotent import

Repeated imports of the same source records MUST preserve one logical transaction per source record and MUST report skipped duplicates separately from inserted records.

#### Scenario: Overlapping files

- GIVEN two input files contain overlapping normalized transactions
- WHEN both files are imported
- THEN only one copy of each logical transaction is stored and the import result exposes the duplicate count

### Requirement: Corporate-action fidelity

The import pipeline MUST preserve supported stock split ratios from source data through persistence and calculation.

#### Scenario: Split reaches FIFO

- GIVEN a source corporate-action row describes a 2-for-1 split for a held ticker
- WHEN the row is imported and the target year is calculated
- THEN the FIFO inventory quantity doubles and the per-share cost is halved while total cost remains unchanged

## MODIFIED Requirements

### Requirement: Import path resolution

CLI and API imports MUST resolve configured data and manual-fix paths independently of the caller's current working directory.

#### Scenario: Launch outside project root

- GIVEN the user launches the CLI or GUI from another directory
- WHEN an import is requested
- THEN the configured project data directory and manual-fix file are used

## REMOVED Requirements

- None.