## Purpose

Lets users import supported IBKR reports safely from their device and review import progress without exposing report contents or direct identifiers.

## ADDED Requirements

### Requirement: Import supported IBKR report
The app SHALL let the user select one or more IBKR Activity Flex Query CSV files at once through the system file picker. Each file SHALL be validated and imported on its own, so one rejected file does not affect the others. The app SHALL validate the file format and parseability on-device before creating a backup or storing derived records.

#### Scenario: Supported report selected
- **WHEN** the user selects a valid IBKR Activity Flex Query CSV file
- **THEN** the app validates and processes it on-device and reports the import result

#### Scenario: Several reports selected
- **WHEN** the user selects several files at once
- **THEN** each file is imported on its own and the app reports a result per file, with account numbers in file names masked

#### Scenario: Unsupported or corrupted report
- **WHEN** the user selects a different format or a CSV file that cannot be parsed
- **THEN** the app explains the error and creates neither a backup nor partial derived records

### Requirement: Process reports locally and atomically
The app SHALL parse and normalize reports on-device without sending report contents over the network. A report import SHALL either store all valid derived records or store none.

#### Scenario: Offline processing
- **WHEN** the device has no network connection and the user imports a valid report
- **THEN** parsing and local processing complete without a network request

#### Scenario: Import failure
- **WHEN** parsing or validation fails
- **THEN** no partial derived records are committed and the user receives a clear error

### Requirement: Protect report data and identifiers
The app SHALL encrypt report data before any network request and SHALL store processed records only in the encrypted local database. Direct identifiers SHALL be pseudonymized before derived records are persisted, logged, or displayed. Logs and diagnostics SHALL NOT contain report contents, direct identifiers, or person-linked financial values.

#### Scenario: Successful processing
- **WHEN** a report is processed successfully
- **THEN** derived records contain pseudonymized identifiers and are stored in encrypted local storage

#### Scenario: Processing error
- **WHEN** report processing succeeds or fails
- **THEN** logs and diagnostics contain no raw report contents or direct identifiers

### Requirement: Back up reports securely
The app SHALL back up imported reports only in client-side encrypted form to the user-chosen backup folder, following `mobile-encrypted-drive-backup`. It SHALL verify the written backup before removing the app-managed source copy. A backup failure SHALL NOT lose the local import or its protected source data.

#### Scenario: Backup verified
- **WHEN** the backup file read back from the backup folder matches the local encrypted file in size and SHA-256
- **THEN** the app marks the backup complete and may remove the app-managed source copy

#### Scenario: Backup unavailable
- **WHEN** the backup folder is unavailable, full, or no longer accessible
- **THEN** the app retains protected local data, marks the backup pending, and retries without blocking local processing

### Requirement: Report duplicate imports
The app SHALL detect a report that has already been imported and SHALL NOT create duplicate derived records.

#### Scenario: Duplicate report selected
- **WHEN** the user imports the same report again
- **THEN** the app reports that it was already imported and leaves the existing records unchanged

### Requirement: Show import progress and result
The app SHALL show the status of each import and distinguish processing, backup pending, backed up, and failed states.

#### Scenario: Import status changes
- **WHEN** processing or backup status changes
- **THEN** the user can see the current status and a useful reason for any failure
