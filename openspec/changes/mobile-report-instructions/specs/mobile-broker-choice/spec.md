## MODIFIED Requirements

### Requirement: Ask for the broker before importing
Adding reports SHALL require a chosen broker. If none is chosen, the import screen SHALL ask for it first. Report formats SHALL be described in the chosen broker's terms, and the import screen SHALL explain how to get a supported report from that broker.

#### Scenario: Import without a broker
- **WHEN** no broker is chosen and the user opens "Add reports"
- **THEN** the app asks which broker the reports come from before offering to pick files

#### Scenario: Import with Interactive Brokers
- **WHEN** Interactive Brokers is chosen
- **THEN** the import screen says it accepts IBKR Activity Statements in CSV format, in English

#### Scenario: How to get the report
- **WHEN** Interactive Brokers is chosen and the user opens "How to get this report"
- **THEN** the app lists the steps to download an Activity Statement as CSV in English from the IBKR portal, one file per year, and that several files can be imported at once
