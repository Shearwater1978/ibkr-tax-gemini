## Purpose

Lets the user say which broker their reports come from, so the app can use that broker's terms and import only formats it supports.

## ADDED Requirements

### Requirement: Choose a broker
The app SHALL offer a broker choice listing the supported brokers; for now only Interactive Brokers. On first launch it SHALL follow the skin choice, and it SHALL be changeable at any time in Settings in every skin. The choice SHALL be stored on the device and SHALL NOT be part of backups.

#### Scenario: First launch
- **WHEN** the user finished choosing a skin on first launch
- **THEN** the app asks which broker their reports come from, listing Interactive Brokers, with an option to choose later

#### Scenario: Change in Settings
- **WHEN** the user opens Settings
- **THEN** a "Broker" entry shows the chosen broker or "Not chosen" and lets the user change it

### Requirement: Preselect the broker for existing users
When the app updates from a version without a broker choice and already holds imported reports, Interactive Brokers SHALL be chosen automatically, because those reports can only come from it.

#### Scenario: Update with data
- **WHEN** an app with imported reports updates to this version
- **THEN** Interactive Brokers is chosen and no broker question is shown

### Requirement: Ask for the broker before importing
Adding reports SHALL require a chosen broker. If none is chosen, the import screen SHALL ask for it first. Report formats SHALL be described in the chosen broker's terms.

#### Scenario: Import without a broker
- **WHEN** no broker is chosen and the user opens "Add reports"
- **THEN** the app asks which broker the reports come from before offering to pick files

#### Scenario: Import with Interactive Brokers
- **WHEN** Interactive Brokers is chosen
- **THEN** the import screen says it accepts IBKR Activity Flex Query CSV reports
