## ADDED Requirements

### Requirement: Encrypted local database
The local database containing processed results SHALL be encrypted at rest using SQLCipher
with a key protected by the device key.

#### Scenario: Database creation
- **WHEN** the database is created
- **THEN** it is encrypted with a key retrieved from the device keystore

#### Scenario: Database file extraction
- **WHEN** the database file is copied from the device without the device key
- **THEN** its contents cannot be read

### Requirement: Database included in encrypted backup
The local database SHALL be included in the encrypted backup set, encrypted with the data key.

#### Scenario: Database backup
- **WHEN** a backup cycle runs
- **THEN** a consistent snapshot of the database is encrypted with the data key and uploaded

### Requirement: Exclude local data from OS cloud backups
Local files containing financial data SHALL be excluded from Android and iOS system backups.

#### Scenario: Android backup
- **WHEN** Android system backup runs
- **THEN** sensitive files are excluded through backup rules and allowBackup is disabled for them

#### Scenario: iOS backup
- **WHEN** iCloud backup runs
- **THEN** sensitive files are marked as excluded from backup and use complete file protection

### Requirement: No plaintext caches
The application SHALL NOT write decrypted report contents or results to caches or temporary storage that persists beyond the processing session.

#### Scenario: Session end
- **WHEN** the user closes the processing screen or the app goes to background
- **THEN** temporary decrypted data is cleared from disk and memory where feasible
