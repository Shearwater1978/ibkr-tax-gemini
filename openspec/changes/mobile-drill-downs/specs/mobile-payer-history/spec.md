## Purpose

Lets the user see every dividend payment of one company, in every skin.

## ADDED Requirements

### Requirement: Show a company's dividend history
Tapping an instrument in the per-position income list of any skin SHALL open its dividend history across all years, whatever year is selected. The history SHALL list one line per booking date, newest first, with gross amount, withholding tax and net amount in the payment currency. A withholding-tax row booked on a date without a dividend SHALL appear as its own line with no gross amount. Totals of gross, withholding tax and net SHALL be shown for the whole history. Amounts in different currencies SHALL NOT be added together.

#### Scenario: Open from the income list
- **WHEN** the user taps Apple in the income list for 2025
- **THEN** Apple's payments from all years are listed, newest first, with gross, withholding tax and net, and the totals

#### Scenario: Later tax correction
- **WHEN** a withholding-tax correction for Apple is booked a month after the dividend
- **THEN** the correction is its own line with only the tax amount, and the totals include it

#### Scenario: Same ticker in two currencies
- **WHEN** an instrument paid dividends in two currencies
- **THEN** each currency's history opens from its own line in the income list
