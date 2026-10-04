## ADDED Requirements

### Requirement: Currency switcher

The top bar MUST contain a currency selector offering PLN, USD, and EUR. Selecting a currency MUST convert every monetary amount shown in the Dashboard, Dividends, Analytics, and Portfolio views, including totals and summary cards, using the display rates of the last calculation, and MUST label amounts with the selected currency. The selection MUST be remembered between sessions, MUST fall back to PLN when no rate is available for it, and a note MUST state that conversion is for display only. Original-currency fields and the exchange-rate column MUST NOT be converted.

#### Scenario: Switch to USD

- **WHEN** a calculation has succeeded and the user selects USD
- **THEN** all amounts in the views are shown in USD at the displayed rate and the labels say USD

#### Scenario: No calculation yet

- **WHEN** no calculation has succeeded
- **THEN** only PLN is selectable

#### Scenario: Rate unavailable

- **WHEN** the display rate for a currency is missing from the calculation response
- **THEN** that currency is disabled and PLN is shown
