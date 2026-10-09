## Purpose

Lets the user add broker reports to the mobile app from the UI, with personal identifiers anonymized during processing and the original report secured via the encrypted Drive backup.

## ADDED Requirements

### Requirement: Upload broker report from UI
The app SHALL let the user select one or more broker report files from the device and import them through the UI.

#### Scenario: Successful import
- **WHEN** the user selects a supported broker report file
- **THEN** the app parses it and reports the number of imported records

#### Scenario: Unsupported or corrupted file
- **WHEN** the user selects a file that is not a supported report or cannot be parsed
- **THEN** the app shows a clear error and stores nothing from that file

#### Scenario: Duplicate import
- **WHEN** the user imports a report that was already imported
- **THEN** the app does not create duplicate records and tells the user the report was already imported

### Requirement: Anonymization during processing
The app SHALL pseudonymize direct identifiers (such as name, account number and tax ID) in a report during processing, before processed data is stored, logged or displayed. Pseudonymization SHALL follow the `data-anonymization` requirements of `mobile-encrypted-drive-backup`.

#### Scenario: Processed data contains no raw identifiers
- **WHEN** a report is processed
- **THEN** the stored processed records contain pseudonyms or masked values instead of raw direct identifiers

#### Scenario: Logs
- **WHEN** processing succeeds or fails
- **THEN** no raw identifiers or full report contents appear in logs or error messages

### Requirement: Secure storage of the report
The app SHALL store each imported report in Google Drive only in encrypted form, as defined by `mobile-encrypted-drive-backup`.

#### Scenario: Backup of imported report
- **WHEN** a report is imported
- **THEN** the app encrypts it on the device and uploads it to the Drive app data folder

#### Scenario: Drive unavailable
- **WHEN** the upload to Google Drive fails (offline, auth expired, quota)
- **THEN** the app keeps the report locally in encrypted form, shows the backup as pending, and retries later without losing data

### Requirement: Import progress and result
The app SHALL show the user the status of each import: processing, backed up, or failed.

#### Scenario: Status visible
- **WHEN** an import is running or has finished
- **THEN** the UI shows its current status and, for failures, the reason
