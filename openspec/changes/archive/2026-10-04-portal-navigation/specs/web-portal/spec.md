## ADDED Requirements

### Requirement: Reports navigation menu

The top bar MUST group the Dividends, Analytics, and Portfolio views under a "Reports" dropdown menu, opened by click and closed by selecting an item, clicking outside, or pressing Escape. The "Reports" entry MUST appear active whenever one of its views is shown.

#### Scenario: Open a report from the menu

- **WHEN** the user opens the Reports menu and selects Analytics
- **THEN** the Analytics view is shown, the menu closes, and the Reports entry is highlighted

#### Scenario: Dismiss the menu

- **WHEN** the menu is open and the user presses Escape or clicks elsewhere
- **THEN** the menu closes without changing the current view

### Requirement: Global calculation controls

The top bar MUST contain the year selector and a Calculate button available on every view. Starting a calculation MUST show the Tax Calculation view with its progress indicator and status, and the Calculate button MUST be disabled while a calculation is running.

#### Scenario: Calculate from a report view

- **WHEN** the user selects a year and presses Calculate while viewing Dividends
- **THEN** the Tax Calculation view with the progress indicator is shown, and on success the Dashboard is shown with the new results

#### Scenario: Calculation fails

- **WHEN** a calculation started from the top bar fails
- **THEN** the Tax Calculation view remains visible with the error and previous results are unchanged
