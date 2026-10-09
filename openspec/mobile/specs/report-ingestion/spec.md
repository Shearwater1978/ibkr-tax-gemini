## ADDED Requirements

### Requirement: Import broker reports from user-selected files
The application SHALL import broker reports only from files explicitly selected by the user
through the system document picker.

#### Scenario: User selects a report
- **WHEN** the user selects a report file via the document picker
- **THEN** the application reads the file and begins the import flow

#### Scenario: Unsupported file type
- **WHEN** the user selects a file that is not a supported report format
- **THEN** the application rejects the file and shows an error without storing it

### Requirement: Process reports without network access
The parsing and calculation of report data SHALL run entirely on the device and SHALL NOT send
report contents over the network.

#### Scenario: Processing offline
- **WHEN** the device has no network connection and a report is imported
- **THEN** the report is processed and results are stored locally

### Requirement: No uncontrolled copies of source files
The application SHALL NOT create copies of source report files outside the managed,
encrypted storage location.

#### Scenario: Temporary parsing file
- **WHEN** a parser requires a temporary file
- **THEN** the temporary file is created in the application's private storage and deleted after parsing
