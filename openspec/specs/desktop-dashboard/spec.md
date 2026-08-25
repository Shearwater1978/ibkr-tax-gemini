# Desktop Dashboard Specification

## Purpose

Provide a dependable desktop workflow for importing IBKR data, calculating a selected tax year, reviewing summary metrics, and opening generated reports through a local dashboard.

## Requirements

### Requirement: Backend readiness

The desktop application MUST detect when its local backend is ready before issuing data requests and MUST show an actionable error when the backend cannot start.

#### Scenario: Backend starts slowly

- **WHEN** the desktop window opens before the local backend is ready
- **THEN** the UI waits or retries health discovery without issuing duplicate calculations and eventually shows the available years

### Requirement: Calculation workflow

The dashboard MUST allow the user to select an available year, start one calculation at a time, show progress state, and display realized P&L, gross dividends, and open positions after success.

#### Scenario: Successful calculation

- **WHEN** the user selects a year and starts calculation
- **THEN** the calculate action is disabled while running and the returned summary is displayed when the report is ready

### Requirement: Error presentation

The dashboard MUST present import, calculation, diagnostic, and report-opening errors without replacing a previous valid result with misleading values.

#### Scenario: Calculation fails

- **WHEN** the backend returns a diagnostic error
- **THEN** the UI displays the error and keeps the failure state distinct from a successful report

### Requirement: Electron renderer security

The desktop renderer MUST not receive unrestricted Node.js integration, and the application MUST use an explicit preload boundary for privileged operations.

#### Scenario: Renderer loads untrusted content

- **WHEN** the renderer loads the dashboard
- **THEN** direct Node.js APIs are unavailable to page scripts and privileged operations remain behind the preload boundary
