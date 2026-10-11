## Purpose

Highlights which of the user's holdings gained or lost the most today, using the prices the app already retrieves.

## ADDED Requirements

### Requirement: Show today's top gainers and losers
The main page SHALL show up to five held positions with the largest positive daily change in percent, and up to five with the largest negative daily change in percent. Each entry SHALL show the ticker, the position's market value, the daily change in percent and the daily P&L amount, in the position's own currency. Only positions with a price and a previous close SHALL be ranked. A position SHALL NOT appear in both lists.

#### Scenario: Prices available
- **WHEN** at least one held position has a daily change
- **THEN** the app shows the gainers, largest first, and the losers, largest fall first

#### Scenario: No movement data
- **WHEN** no held position has a price with a previous close
- **THEN** the top-movers section is hidden

#### Scenario: Only gains today
- **WHEN** every ranked position rose today
- **THEN** the losers list says that nothing fell today

#### Scenario: Stale prices
- **WHEN** the prices come from the latest close or are stale
- **THEN** the section says which, using the same freshness label as the positions table
