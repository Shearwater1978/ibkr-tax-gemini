## ADDED Requirements

### Requirement: Client-side encryption before writing
Each imported report SHALL be encrypted on the device before it is written to the backup folder.
Encryption SHALL use AES-256-GCM with a unique nonce per file.

#### Scenario: Encrypting a report
- **WHEN** a report is imported and backup is enabled
- **THEN** the report is encrypted with the data key before anything is written to the backup folder

#### Scenario: Nonce uniqueness
- **WHEN** two files are encrypted
- **THEN** each uses a different nonce

### Requirement: Versioned encrypted file format
Each encrypted backup file SHALL contain a versioned header including format version, KDF parameters
where applicable, and nonce. The format version SHALL be included in the authenticated data.

#### Scenario: Unknown format version
- **WHEN** the application reads a backup with an unsupported format version
- **THEN** it reports that the backup requires a newer application version and does not attempt decryption

### Requirement: Write to the backup folder
Encrypted backups SHALL be written only to the backup folder chosen under `backup-location`.

#### Scenario: Write
- **WHEN** an encrypted report is ready for backup
- **THEN** it is written to the backup folder with a randomized file name

#### Scenario: Write failure
- **WHEN** writing fails because the storage provider is unavailable, full, or offline
- **THEN** the application retries with exponential backoff and keeps the original file until the write is verified

### Requirement: Verify before deleting the original
The original report SHALL NOT be deleted from the device until the uploaded backup is verified.

#### Scenario: Successful verification
- **WHEN** the file read back from the backup folder matches the local encrypted file in size and SHA-256
- **THEN** the original report is deleted from the device

#### Scenario: Verification failure
- **WHEN** the checksum or size does not match
- **THEN** the written file is deleted from the backup folder, the local original is kept, and the user is notified

### Requirement: Restore on a new device
The application SHALL allow restoring backups on a new device. Restore SHALL be offered on the import screen while no backup encryption is set up and no report is imported. The user picks the backup folder (or the folder that contains the app's backup subfolder); the data key comes from Android Block Store when that copy matches the manifest, otherwise from the passphrase or recovery code. Restored reports SHALL be imported like new imports (with this device's pseudonyms) and recorded as backed up, the unlocked keys SHALL be kept on this device, and the picked folder SHALL become the backup folder. A backup file that cannot be decrypted SHALL be reported and skipped without stopping the others.

#### Scenario: Restore
- **WHEN** the user picks the backup folder and provides the passphrase or recovery code on a new device
- **THEN** the manifest is read, the data key is unwrapped, and backup files are decrypted into local storage

#### Scenario: Wrong passphrase
- **WHEN** the provided passphrase cannot unwrap the data key
- **THEN** the application reports an authentication failure and does not partially restore data

#### Scenario: Restore without a passphrase
- **WHEN** the new phone was restored from the user's Android backup, so Block Store holds the data key, and the user picks the backup folder
- **THEN** the backups are restored without asking for the passphrase or recovery code

### Requirement: Backup deletion
When a user deletes a report backup, the application SHALL delete the file from the backup folder. Because storage
providers may keep trash or version history, erasure of all data SHALL also destroy the keys so that remaining copies
cannot be decrypted.

#### Scenario: Delete backup
- **WHEN** the user deletes a backup
- **THEN** the application deletes the corresponding file from the backup folder

#### Scenario: Erase all backups
- **WHEN** the user erases all data
- **THEN** the backup files and manifest are deleted and the data key and device keys are destroyed
