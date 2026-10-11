## Purpose

Shows what the portfolio and each position have earned in total: price gain on held shares, gain from past sales and dividends, compared with the amount invested.

## ADDED Requirements

### Requirement: Compute gain from sales with FIFO
For each sell, the app SHALL compute the gain from sales as the sale quantity times the sale price minus the cost of the lots it closes, matched by FIFO with the same instrument identity and corporate-action rules as the open lots. Gains SHALL be in the instrument's own currency. Commissions SHALL be excluded, as for the cost of open lots. These values SHALL be labelled informational and SHALL NOT be presented as tax results.

#### Scenario: Partial sale
- **WHEN** 10 shares were bought at 100 and later 10 at 120, and 15 are sold at 130
- **THEN** the gain from that sale is 10 × (130 − 100) + 5 × (130 − 120) = 350

#### Scenario: Sale after a split
- **WHEN** shares are sold after a split
- **THEN** the closed lots use the split-adjusted quantities and prices

### Requirement: Show summary cards
The main page SHALL show summary cards above the positions:
- value: the market value in USD, with the invested amount (the FIFO cost of open lots) below it;
- total profit: price gain on open lots, plus gain from sales, plus net dividends, as an amount in USD and as a percentage of the invested amount;
- today: the daily P&L in USD and as a percentage;
- dividends: net dividends received in the last 12 months, in USD.

USD amounts SHALL use the latest NBP cross rates, be labelled approximate, and be marked incomplete when a price or rate is missing.

#### Scenario: All prices and rates available
- **WHEN** every position has a price and every currency has a rate
- **THEN** the cards show value, invested, total profit with its percentage, today's change and 12-month net dividends, without an incomplete marker

#### Scenario: Missing price
- **WHEN** a position has no price
- **THEN** the value and total-profit cards are marked incomplete, and the invested and dividend cards are unaffected

### Requirement: Show a position's profit breakdown
Tapping a position SHALL open its details, in the instrument's own currency: quantity, average price, invested amount, current value, dividends received (gross, withholding tax and net), price gain, gain from sales, and total (price gain + gain from sales + net dividends) with its percentage of the invested amount.

#### Scenario: Position with dividends and a past sale
- **WHEN** the user taps a position that paid dividends and was partly sold
- **THEN** the details show its dividends, its gain from sales and the total

#### Scenario: Position without a price
- **WHEN** the position has no current price
- **THEN** value, price gain and total are shown as unavailable, and dividends and gain from sales are still shown

### Requirement: Show sold positions on request
The main page SHALL offer a "Show sold positions" switch, off by default. When it is on, instruments that were fully sold SHALL be listed after the held positions with quantity zero, their gain from sales and their dividends, and SHALL open the same details when tapped. The switch setting SHALL be remembered.

#### Scenario: Switch on
- **WHEN** the user turns on "Show sold positions"
- **THEN** fully sold instruments appear after the held positions with their gain from sales and dividends

#### Scenario: Switch off
- **WHEN** the switch is off
- **THEN** only held positions are listed, and the summary cards still include gains from sales and dividends of sold instruments
