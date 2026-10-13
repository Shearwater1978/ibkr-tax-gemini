## MODIFIED Requirements

### Requirement: Delete imported reports
The app SHALL let the user delete one import, after a confirmation, or all imported reports, after entering the backup
passphrase. Deleting an import SHALL remove only the records that no other remaining import contains, so overlapping
reports stay complete. Deleting SHALL also delete the corresponding encrypted backup files from the backup folder; backup
encryption keys SHALL NOT change. Manual operations and their backup journal SHALL NOT be removed by deleting imports.

#### Scenario: Delete one import
- **WHEN** the user deletes an import and confirms
- **THEN** its report entry and the records only it contains are removed, records also contained in other imports remain, and its backup file is deleted

#### Scenario: Delete all imports
- **WHEN** the user chooses to delete all imported reports and enters the correct passphrase
- **THEN** all reports and derived records are removed and their backup files are deleted from the backup folder, while manual operations and the manual journal stay

## ADDED Requirements

### Requirement: Report possible duplicates of manual operations
After an import, the result SHALL list each manual operation that matches an imported operation (same date, type,
ticker, currency and absolute quantity, or absolute amount for dividends and tax) and offer to delete the manual copy.

#### Scenario: Matching manual buy
- **WHEN** an imported report contains a buy matching a manual buy
- **THEN** the import result shows it under "Possible duplicates" with "Delete manual copy"
