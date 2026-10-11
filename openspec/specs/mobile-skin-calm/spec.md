# mobile-skin-calm Specification

## Purpose
Defines the Calm skin: a light, minimal interface that answers what the portfolio is worth, how today went and what is held, with everything else in bottom sheets.

## Requirements

### Requirement: Calm navigation
Calm SHALL use three bottom tabs: Home, Income and Activity. Report import SHALL open from a "+" button on Home, and Settings from an avatar button on Home.

#### Scenario: Tabs
- **WHEN** the user opens the app in Calm
- **THEN** Home is shown with the tabs Home, Income and Activity

### Requirement: Calm home
Home SHALL show:
- the portfolio value in USD, with an information button explaining the currency conversion and price sources;
- today's change as a coloured chip;
- a "Total return" line that opens a breakdown sheet with price gain, gains from sales and net dividends;
- the holdings as one row per position, with name, shares, listing exchange, value and today's change.

A "View" sheet SHALL offer:
- sort by value, today's change or name;
- own currency or USD;
- show sold positions.

The top gainers and losers SHALL be reachable through sorting by today's change.

#### Scenario: View options
- **WHEN** the user opens "View" and selects USD and "Show sold positions"
- **THEN** the rows show USD amounts and sold positions appear in a "Sold" group

#### Scenario: Position sheet
- **WHEN** the user taps a holding
- **THEN** a bottom sheet shows price, today's change, shares, average price, invested, value and the total return split into price gain, sales and dividends

### Requirement: Calm income and activity
Income SHALL show the year's net dividends after withholding tax, a monthly bar chart where tapping a bar shows that month, a year selector including all years, a comparison with the previous year, and the paying companies. Activity SHALL show operations grouped by month, with search and filter chips.

#### Scenario: Year selection
- **WHEN** the user selects 2025 in Income
- **THEN** the total, chart and payers show 2025
