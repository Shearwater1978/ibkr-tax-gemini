## ADDED Requirements

### Requirement: Choose the backup folder
The application SHALL offer two backup locations, "This phone" and "Google Drive". For either, the user confirms a
parent folder in the system folder picker; the application keeps a persisted permission for that folder only and creates
and uses its own subfolder "IBKR Tax Assistant backups" inside it. If the chosen folder already contains a backup
manifest, the application SHALL use it as is. The application SHALL NOT require a Google Cloud project, OAuth client,
or account sign-in of its own for backups.

#### Scenario: First backup setup
- **WHEN** the user chooses "This phone" or "Google Drive" and confirms a folder in the system picker
- **THEN** the app keeps a persisted permission for that folder and stores backups in its "IBKR Tax Assistant backups" subfolder

#### Scenario: Location mismatch
- **WHEN** the folder picked in the system picker does not belong to the chosen location, for example a phone folder after choosing "Google Drive"
- **THEN** the app explains the mismatch, keeps the previous backup location, and lets the user try again

#### Scenario: Google Drive not installed
- **WHEN** the Google Drive app is not installed
- **THEN** the "Google Drive" option is unavailable and the app explains why

#### Scenario: Existing backup folder chosen
- **WHEN** the picked folder already contains a backup manifest
- **THEN** the app uses that folder directly without creating a nested subfolder

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
