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

### Requirement: Open a company's dividend history from its position
In Bento, the gross-dividend and withholding-tax tiles of a position detail SHALL open that instrument's dividend history on the Income tab when the position has dividends, so that Back returns to Income. Without dividends the tiles SHALL NOT be tappable.

#### Scenario: From the position detail
- **WHEN** the user opens Apple from Home and taps "Dividends gross"
- **THEN** the Income tab is active and shows Apple's dividend history

### Requirement: Open income from the 12-month dividend total
Where a skin's portfolio screen shows the dividends of the last 12 months (Classic's summary card, Bento's dividends tile, Focus's "Last 12 months" card), tapping it SHALL open that skin's income screen.

#### Scenario: Classic summary card
- **WHEN** the user taps "Dividends, 12 months" on Classic's Portfolio tab
- **THEN** the Dividends tab opens
