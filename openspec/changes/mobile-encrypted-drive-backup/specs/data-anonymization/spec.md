## ADDED Requirements

### Requirement: Data classification
The application SHALL classify every data field handled by the application as one of:
direct identifier, quasi-identifier, financial value, or non-personal metadata.
The classification SHALL be documented and applied consistently across storage, UI, logs, and exports.

#### Scenario: New field added
- **WHEN** a developer adds a new field that comes from a broker report
- **THEN** the field is assigned a classification before it is stored or displayed

### Requirement: Pseudonymization of direct identifiers
Direct identifiers used for internal linking (such as account number or tax ID) SHALL be replaced
with pseudonyms derived using HMAC-SHA256 with a secret key held in the device keystore.
The secret key SHALL NOT be exported or included in backups in plaintext.

#### Scenario: Pseudonym generation
- **WHEN** a report with an account number is processed
- **THEN** the stored record references a pseudonym derived via HMAC-SHA256, not the raw account number

#### Scenario: Pseudonym without key
- **WHEN** the pseudonym is observed without access to the device keystore
- **THEN** the original identifier cannot be recovered from it using the stored data alone

#### Scenario: Key rotation
- **WHEN** the pseudonymization key is rotated
- **THEN** existing records are re-linked to new pseudonyms during a controlled migration, and the old key is destroyed

### Requirement: Masking in user interface
Account numbers, tax identifiers, and full names SHALL be masked by default in the UI, showing only
the minimum needed to recognize the record (for example, the last four characters).

#### Scenario: Account number display
- **WHEN** an account number is shown in a list or detail view
- **THEN** only the last four characters are visible unless the user explicitly reveals the value after re-authentication

#### Scenario: Reveal action
- **WHEN** the user requests to reveal a masked value
- **THEN** the application requires biometric or device credential authentication before showing it

### Requirement: No direct identifiers in logs, diagnostics, and analytics
Logs, crash reports, analytics events, and support diagnostics SHALL contain only pseudonyms, aggregate counts,
or masked values. Direct identifiers, financial amounts tied to a person, and raw report content SHALL NOT be included.

#### Scenario: Crash report
- **WHEN** a crash occurs during report processing
- **THEN** the crash report contains no raw report fields and no unmasked identifiers

#### Scenario: Analytics event
- **WHEN** an analytics event is sent
- **THEN** the event payload contains no direct identifiers and no per-person financial values

### Requirement: Full identity only in tax output
The full identity SHALL be used only to generate the tax output and its export. This is a documented exception
to the pseudonymization requirements. The tax output SHALL be generated on the device and SHALL be
protected with the same controls as the local database.

#### Scenario: Tax report generation
- **WHEN** the user generates a tax report
- **THEN** the full identifiers are included only in the generated document and are not written to logs

#### Scenario: Exported tax report
- **WHEN** the user exports the tax report
- **THEN** the export is generated on demand, is not stored in the application's cache, and is deleted from temporary storage after sharing

### Requirement: Anonymization for aggregate statistics
If the application produces aggregate statistics (for example, totals across all users or across all reports of one user
that are shared outside the device), the statistics SHALL be anonymized. Anonymization SHALL aggregate values
so that no individual record or person can be singled out, and small groups SHALL be suppressed.

#### Scenario: Small group suppression
- **WHEN** an aggregate statistic would be based on fewer than the configured minimum number of records
- **THEN** the statistic is not produced or is merged with other groups

#### Scenario: Statistics export
- **WHEN** aggregate statistics are exported
- **THEN** the export contains no pseudonyms, no identifiers, and no values that can be traced back to one record

### Requirement: Erasure and minimization
When the user requests erasure, the application SHALL remove all personal data linked to the user from the local
database, the Google Drive backups (via files.delete), and the device keystore entries. The application SHALL NOT retain
data that is no longer needed for processing or for the statutory retention period, if one applies.

#### Scenario: Erasure request
- **WHEN** the user requests erasure of their data
- **THEN** local records, Drive backups, manifests, and related keys are deleted, and the user receives confirmation

#### Scenario: Data retention
- **WHEN** a retention period expires for an imported report
- **THEN** the report and its derived records are deleted automatically
