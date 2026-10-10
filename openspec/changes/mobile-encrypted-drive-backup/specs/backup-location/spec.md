## ADDED Requirements

### Requirement: Choose the backup folder
The application SHALL let the user choose a backup folder once through the system folder picker and SHALL keep
permission for that folder only. The application SHALL NOT require a Google Cloud project, OAuth client, or account
sign-in of its own for backups.

#### Scenario: First backup setup
- **WHEN** the user enables backup
- **THEN** the system folder picker opens and the app keeps a persisted permission for the chosen folder

#### Scenario: Local-only folder
- **WHEN** the chosen folder is stored only on the device
- **THEN** the app warns that a lost or reset device would also lose the backup and recommends a cloud folder

### Requirement: Neutral file names
Backup files SHALL have random, neutral names and SHALL contain only encrypted data and the wrapped data key.

#### Scenario: Folder inspection
- **WHEN** the user or the storage provider lists the backup folder
- **THEN** no file name or content reveals report contents, account numbers, or names

### Requirement: Lost folder access
The application SHALL detect when the folder permission is no longer valid and ask the user to choose the folder
again without losing local data.

#### Scenario: Permission revoked or folder removed
- **WHEN** writing or reading the backup folder fails because access was lost
- **THEN** the app keeps local data, marks backups pending, and asks the user to choose the folder again

### Requirement: Changing the backup folder
Choosing a different backup folder SHALL NOT delete existing backups unless the user requests it.

#### Scenario: New folder chosen
- **WHEN** the user chooses a different backup folder
- **THEN** new backups go to the new folder and the old backup files remain until the user deletes them
