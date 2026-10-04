## ADDED Requirements

### Requirement: Automatic data loading on start

When the portal starts and the backend is ready, it MUST import the statement files, load the available years, select the latest year, and calculate it without any user action, showing the Dashboard when finished. A failed import MUST NOT prevent calculating already stored data. When no years are available, the portal MUST NOT calculate and MUST leave manual import available.

#### Scenario: Start with data

- **WHEN** the portal starts with stored or importable data
- **THEN** the Dashboard shows results for the latest year without interaction

#### Scenario: Import fails

- **WHEN** the automatic import fails but years exist in storage
- **THEN** the latest year is still calculated and the import error is visible in the Workspace

#### Scenario: No data

- **WHEN** no years are available
- **THEN** no calculation runs and the empty state remains
