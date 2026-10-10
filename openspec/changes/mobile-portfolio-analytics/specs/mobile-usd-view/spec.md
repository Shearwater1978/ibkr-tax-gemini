## Purpose

Lets users compare positions held in different currencies by showing the main page's per-position values in USD at NBP cross rates.

## ADDED Requirements

### Requirement: Switch position values between own currency and USD
The main page SHALL offer a switch between "Own currency" (the default) and "USD". In USD mode, each position's last price, daily change, market value and P&L SHALL be converted with PLN cross rates from the latest NBP table A and labelled approximate, with the rate date. Sorting SHALL use the values shown. The selected mode SHALL be remembered.

#### Scenario: USD mode
- **WHEN** the user selects "USD"
- **THEN** every position's values are shown in USD with an approximate label and the NBP rate date, and positions already in USD are unchanged

#### Scenario: Back to own currency
- **WHEN** the user selects "Own currency"
- **THEN** each position shows its values in its own currency again

#### Scenario: No rate for a currency
- **WHEN** no NBP rate exists for a position's currency
- **THEN** that position's converted values are shown as unavailable in USD mode

#### Scenario: Sort in USD mode
- **WHEN** the user sorts by P&L in USD mode
- **THEN** positions are ordered by their USD P&L, with unavailable values last
