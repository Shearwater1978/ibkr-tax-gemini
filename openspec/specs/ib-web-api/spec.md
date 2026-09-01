# ib-web-api Specification

## Purpose

Provide a reliable, read-only connection to Interactive Brokers through the official Client Portal Web API (REST/JSON over HTTPS, session-authenticated via a locally running Client Portal Gateway) so the application can import live account data into the same normalized workflow used for tax calculation, as an alternative to the TWS/IB Gateway socket connector.

## Requirements

### Requirement: Client Portal Gateway session authentication

The system MUST authenticate to the Interactive Brokers Client Portal Web API by using an existing, already-authenticated Client Portal Gateway (CPGW) session (established by the user via browser login with their IBKR credentials and 2FA) and MUST reject a sync attempt when the required CPGW base URL configuration is invalid or missing, or when no authenticated session is detected.

#### Scenario: CPGW session is authenticated
- **WHEN** the configured Client Portal Gateway is running and has an authenticated browser session
- **THEN** the application MUST use that session to make subsequent read-only account requests

#### Scenario: CPGW is unreachable or session is not authenticated
- **WHEN** the Client Portal Gateway is not running, is unreachable, or has no authenticated session
- **THEN** the system MUST return a clear connection/authentication error directing the user to log in via the Gateway's browser page, and MUST NOT continue with a partially initialized live import

### Requirement: Read-only account sync over HTTPS

The system MUST support a read-only sync that requests account, position, and trade confirmation data from Interactive Brokers over HTTPS without creating, modifying, or cancelling orders on the broker side.

#### Scenario: Account data request
- **WHEN** the user requests a live import or refresh via the Web API
- **THEN** the system MUST collect the relevant account, position, and trade data over HTTPS and prepare it for normalization

### Requirement: Data normalization

The system MUST normalize Web API payloads into the same internal transaction structure already used by the CSV import path and the TWS/IB Gateway connector, so FIFO, tax, and report generation remain unchanged regardless of data source.

#### Scenario: Web API data arrives
- **WHEN** data from the Interactive Brokers Web API is retrieved
- **THEN** it MUST be converted into the project's existing trade/corporate-action format before it is stored or processed

### Requirement: Error presentation

The system MUST present authentication, authorization, rate-limit, and session-expiry failures explicitly so the user understands whether the issue is credential/login-related, permission-related, or transient.

#### Scenario: Session expired or not authenticated
- **WHEN** the Client Portal Gateway session is expired, was never authenticated, or is rejected by the broker
- **THEN** the user MUST see a precise diagnostic (directing them to re-authenticate via the Gateway's browser page) and the application MUST NOT silently drop the data

### Requirement: Optional, independent deployment profile

The Web API live connection MUST be optional and MUST NOT block the current CSV import workflow or the existing TWS/IB Gateway live connector (`ib-live-api`) for users who do not configure it.

#### Scenario: Existing workflows unaffected
- **WHEN** a user does not configure Web API access
- **THEN** the current CSV import flow and, if configured, the TWS/IB Gateway live connector MUST continue to operate without requiring Web API credentials

#### Scenario: Both live connectors configured
- **WHEN** a user has both the TWS/IB Gateway connector and the Web API connector configured
- **THEN** each MUST operate independently, and a failure in one MUST NOT block or corrupt a sync from the other
