## ADDED Requirements

### Requirement: Add operations without a broker
Adding, editing and deleting operations by hand SHALL work without a chosen broker. The broker choice SHALL only
affect report import and broker-specific wording.

#### Scenario: Manual user without a broker
- **WHEN** no broker is chosen and the user opens "Add operation"
- **THEN** the form opens without asking for a broker
