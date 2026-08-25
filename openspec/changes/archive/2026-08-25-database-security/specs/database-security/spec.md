# Delta Specification: Database Security

## ADDED Requirements

### Requirement: Encrypted database backend

The application MUST use a configured SQLCipher-compatible backend for the production database and MUST fail closed if encryption support or a non-empty key is unavailable.

#### Scenario: Missing encryption capability

- GIVEN the configured database cannot be opened through the SQLCipher-compatible backend
- WHEN a CLI, API, or import operation initializes `DBConnector`
- THEN the operation fails with an actionable error and MUST NOT create or use a plaintext database

### Requirement: Verified key handling

The application MUST apply database keys without interpolating raw secrets into SQL text, MUST avoid logging secrets, and MUST verify that the opened database can read its schema before returning a connection.

#### Scenario: Wrong key

- GIVEN an existing encrypted database and an incorrect configured key
- WHEN `DBConnector` connects
- THEN connection initialization fails and no transaction data is returned

### Requirement: Verified key rotation

The key rotation tool MUST use the current key, apply the new key, and verify reopening with the new key before reporting success.

#### Scenario: Successful rotation

- GIVEN a readable encrypted database and valid current and new keys
- WHEN the rotation command completes
- THEN the database opens with the new key, rejects the old key, and the tool reports success

## MODIFIED Requirements

### Requirement: Database configuration

Database path and key configuration MUST be documented with the actual runtime dependency and MUST be resolved consistently by CLI, API, and rotation-tool callers.

#### Scenario: Consistent configuration

- GIVEN the CLI and API use the same configured database path and key
- WHEN either caller initializes the connector
- THEN both callers access the same encrypted database using the same configuration

## REMOVED Requirements

- None.