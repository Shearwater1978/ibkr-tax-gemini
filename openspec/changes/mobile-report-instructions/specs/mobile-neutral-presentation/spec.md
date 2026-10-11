## MODIFIED Requirements

### Requirement: Name a broker only when it was chosen
Fixed texts SHALL NOT name a broker or a broker-specific report format unless the user has chosen that broker (see `mobile-broker-choice`). Without a chosen broker, texts SHALL speak of "broker reports".

#### Scenario: No broker chosen
- **WHEN** no broker is chosen and the user opens the empty portfolio
- **THEN** the text asks for broker reports and names no broker or format

#### Scenario: Interactive Brokers chosen
- **WHEN** the user chose Interactive Brokers
- **THEN** import texts may say "IBKR Activity Statement (CSV)" and explain how to download it
