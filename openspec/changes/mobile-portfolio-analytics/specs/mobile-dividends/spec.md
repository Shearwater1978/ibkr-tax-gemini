## Purpose

Shows the dividends received according to imported IBKR reports: per month, per year and per position, gross, with withholding tax and net, so users can follow their passive income.

## ADDED Requirements

### Requirement: Show received dividends per month
The app SHALL provide a Dividends tab that shows, for a selected calendar year, the dividends received in each month as a bar chart. Each month SHALL show gross dividends, withholding tax and net dividends (gross minus withholding tax). Dividends and withholding tax SHALL be assigned to the month of their own date in the report. The current year SHALL be selected by default, and the user SHALL be able to select any year that contains dividends.

#### Scenario: Months of the selected year
- **WHEN** the user opens the Dividends tab
- **THEN** the app shows twelve monthly bars for the current year with the net amount for each month, and the gross, withholding tax and net totals for the year

#### Scenario: Another year
- **WHEN** the user selects another year that contains dividends
- **THEN** the chart and totals show that year

#### Scenario: Withholding tax correction in a later month
- **WHEN** a report contains a withholding tax correction dated in a later month than the dividend
- **THEN** the correction counts in the month of its own date

### Requirement: Keep currencies apart and total in USD
Amounts SHALL be added up only within the same currency. Totals per month and per year SHALL be shown in USD, converting other currencies with PLN cross rates from the latest NBP table A, labelled approximate, with the rate date. Totals per currency SHALL also be listed in their own currency.

#### Scenario: Dividends in two currencies
- **WHEN** the selected year has dividends in USD and in EUR
- **THEN** the app lists the USD and EUR totals separately and shows one approximate USD total with the NBP rate date

#### Scenario: No rate for a currency
- **WHEN** no NBP rate exists for a dividend currency
- **THEN** that currency's own total is still listed and the USD total is marked incomplete

### Requirement: Compare years by month
The app SHALL show a chart with net dividends per month for up to the three most recent years that contain dividends, side by side for each month.

#### Scenario: Three years of dividends
- **WHEN** dividends exist in 2024, 2025 and 2026
- **THEN** each month shows three bars, one per year, with a legend naming the years

### Requirement: Show dividends per position
For the selected year, the app SHALL list each instrument that paid dividends, with its gross amount, withholding tax and net amount in its own currency, sorted by net amount in USD, largest first.

#### Scenario: Position list
- **WHEN** the user scrolls below the charts
- **THEN** the app lists the paying instruments for the selected year with gross, withholding tax and net amounts

### Requirement: Show an empty state
The app SHALL show a short explanation when the imported reports contain no dividends.

#### Scenario: No dividends
- **WHEN** no imported report contains dividends
- **THEN** the Dividends tab says that no dividends were found and that reports with a Dividends section add them
