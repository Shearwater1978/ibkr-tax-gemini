## ADDED Requirements

### Requirement: Workspace tab

The portal MUST provide a single Workspace tab that shows, on one page, the Tax Calculation progress and status, the Data management controls (CSV import and IB connections), and the FIFO coverage preflight. The separate Tax Calculation, Data, and Coverage tabs MUST NOT be present, and all their existing controls MUST keep working unchanged.

#### Scenario: All tools on one page

- **WHEN** the user opens the Workspace tab
- **THEN** calculation status, data controls, and the coverage check are all visible on the same page

#### Scenario: Existing controls keep working

- **WHEN** the user runs an import, an IB check, or a coverage check from the Workspace page
- **THEN** each behaves and reports its result as it did before

## MODIFIED Requirements

### Requirement: Global calculation controls

The top bar MUST contain the year selector and a Calculate button available on every view. Starting a calculation MUST show the Workspace view with its progress indicator and status, and the Calculate button MUST be disabled while a calculation is running.

#### Scenario: Calculate from a report view

- **WHEN** the user selects a year and presses Calculate while viewing Dividends
- **THEN** the Workspace view with the progress indicator is shown, and on success the Dashboard is shown with the new results

#### Scenario: Calculation fails

- **WHEN** a calculation started from the top bar fails
- **THEN** the Workspace view remains visible with the error and previous results are unchanged
