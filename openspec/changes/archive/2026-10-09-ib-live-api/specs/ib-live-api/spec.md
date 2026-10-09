## Purpose

Provide a reliable, read-only connection to Interactive Brokers through the official TWS/IB Gateway API so the application can import live account data into the same normalized workflow used for tax calculation.

## ADDED Requirements

### Requirement: IB Gateway connectivity

The system MUST support establishing a connection to an Interactive Brokers TWS or IB Gateway instance using the official socket-based API and MUST reject startup when the required host, port, and client ID configuration are invalid.

#### Scenario: Gateway is available
- **WHEN** the configured IB Gateway or TWS endpoint is reachable
- **THEN** the application MUST create a stable session and accept subsequent account requests

#### Scenario: Gateway is unavailable
- **WHEN** the required IB process is not running or the socket is blocked
- **THEN** the system MUST return a clear connection error and MUST NOT continue with a partially initialized live import

### Requirement: Read-only account sync

The system MUST support a read-only sync that requests account and trade metadata from Interactive Brokers without creating or modifying positions on the broker side.

#### Scenario: Trade snapshot request
- **WHEN** the user requests a live import or refresh
- **THEN** the system MUST collect the relevant account, trade, and position data needed to normalize it into the existing transaction model

### Requirement: Data normalization

The system MUST normalize live IB API payloads into the same internal transaction structure already used by the CSV import path so FIFO, tax, and report generation remain unchanged.

#### Scenario: Live data arrives
- **WHEN** data from Interactive Brokers is retrieved
- **THEN** it MUST be converted into the project’s existing trade/corporate-action format before it is stored or processed

### Requirement: Error presentation

The system MUST present connection, permission, and synchronization failures explicitly so the user understands whether the issue is network-related, account-related, or a data-shape problem.

#### Scenario: Permission denial or invalid session
- **WHEN** the IB session is unauthorized, expired, or blocked by the broker
- **THEN** the user MUST see a precise diagnostic and the application MUST not silently drop the data

### Requirement: Optional deployment profile

The live IB API connection MUST be optional and MUST NOT block the current CSV import workflow for users who only work from local IBKR export files.

#### Scenario: Existing CSV workflow
- **WHEN** a user does not configure IB API access
- **THEN** the current import-from-file flow MUST continue to operate without requiring a live IB Gateway connection
