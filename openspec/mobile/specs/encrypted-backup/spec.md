## ADDED Requirements

### Requirement: Client-side encryption before upload
Each imported report SHALL be encrypted on the device before any upload.
Encryption SHALL use AES-256-GCM with a unique nonce per file.

#### Scenario: Encrypting a report
- **WHEN** a report is imported and backup is enabled
- **THEN** the report is encrypted with the data key before any network request is made

#### Scenario: Nonce uniqueness
- **WHEN** two files are encrypted
- **THEN** each uses a different nonce

### Requirement: Versioned encrypted file format
Each encrypted backup file SHALL contain a versioned header including format version, KDF parameters
where applicable, and nonce. The format version SHALL be included in the authenticated data.

#### Scenario: Unknown format version
- **WHEN** the application reads a backup with an unsupported format version
- **THEN** it reports that the backup requires a newer application version and does not attempt decryption

### Requirement: Upload to app data folder
Encrypted backups SHALL be uploaded only to the Google Drive app data folder using the drive.appdata scope.

#### Scenario: Upload
- **WHEN** an encrypted report is ready for upload
- **THEN** it is uploaded to the appDataFolder space with a randomized file name

#### Scenario: Upload failure
- **WHEN** the upload fails due to network or quota error
- **THEN** the application retries with exponential backoff and keeps the original file until upload is verified

### Requirement: Verify before deleting the original
The original report SHALL NOT be deleted from the device until the uploaded backup is verified.

#### Scenario: Successful verification
- **WHEN** the uploaded file's size and checksum match the local encrypted file
- **THEN** the original report is deleted from the device

#### Scenario: Verification failure
- **WHEN** the checksum or size does not match
- **THEN** the uploaded file is deleted from Drive, the local original is kept, and the user is notified

### Requirement: Restore on a new device
The application SHALL allow restoring backups on a new device using the passphrase or recovery code.

#### Scenario: Restore
- **WHEN** the user signs in to Google Drive and provides the passphrase or recovery code on a new device
- **THEN** the manifest is read, the data key is unwrapped, and backup files are decrypted into local storage

#### Scenario: Wrong passphrase
- **WHEN** the provided passphrase cannot unwrap the data key
- **THEN** the application reports an authentication failure and does not partially restore data

### Requirement: Backup deletion
When a user deletes a report backup, the application SHALL permanently delete the Drive file, not only move it to trash.

#### Scenario: Delete backup
- **WHEN** the user deletes a backup
- **THEN** the application calls files.delete on the corresponding Drive file
