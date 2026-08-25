# Database Security Specification

## Purpose

Protect locally stored financial transactions with an explicitly verified encrypted database and a controlled key-rotation workflow.

## Requirements

### Requirement: Encrypted database backend

The application MUST use a configured SQLCipher-compatible backend for the production database and MUST fail closed if encryption support or a non-empty key is unavailable.

#### Scenario: Missing encryption capability

- **WHEN** a CLI, API, or import operation initializes database access without a usable SQLCipher backend
- **THEN** the operation fails with an actionable error and MUST NOT create or use a plaintext database

### Requirement: Verified key handling

The application MUST apply database keys without exposing raw secrets in logs or exception messages, and MUST verify that the opened database can read its schema before returning a connection.

#### Scenario: Wrong key

- **WHEN** a connection is attempted with an incorrect key
- **THEN** initialization fails and no transaction data is returned

### Requirement: Verified key rotation

The key rotation tool MUST use the current key, apply the new key, and verify reopening with the new key before reporting success.

#### Scenario: Successful rotation

- **WHEN** a readable encrypted database is rotated from a valid current key to a valid new key
- **THEN** the database opens with the new key, rejects the old key, and the tool reports success

### Requirement: Consistent database configuration

Database path and key configuration MUST be resolved consistently by CLI, API, and rotation-tool callers.

#### Scenario: Shared configuration

- **WHEN** the CLI and API initialize database access with the configured path and key
- **THEN** both callers access the same encrypted database using the same configuration