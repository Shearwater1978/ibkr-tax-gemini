## Purpose

Defines the Bento skin: a dark tile dashboard where every key number sits in its own labelled tile, for users who like numbers at a glance.

## ADDED Requirements

### Requirement: Bento navigation
Bento SHALL use a dark theme and a floating pill-shaped tab bar with Home, Income, Activity and Data. The tab bar SHALL NOT permanently hide content: the last item of every list SHALL be scrollable above it.

#### Scenario: Last row reachable
- **WHEN** the user scrolls to the end of the holdings list
- **THEN** the last holding is fully visible above the tab bar

### Requirement: Bento home tiles
Home SHALL show tiles for:
- portfolio value with today's change;
- total return;
- net dividends of the last 12 months with a small monthly chart;
- best and worst position today;
- value by currency.

Below the tiles, the holdings list SHALL show each position's value, today's change and weight in the portfolio, sortable by value or today's change. A header pill SHALL switch between USD and own currency. Tapping a tile SHALL open the matching detail: the dividends tile opens Income, and the best and worst tiles open those positions.

#### Scenario: Best today
- **WHEN** the user taps the "Best today" tile
- **THEN** the detail screen of that position opens

### Requirement: Bento details, income and data
Bento SHALL provide the following screens:
- **Position detail:** a full screen showing the return as steps: invested, price gain, sales, dividends, total. It also shows gross dividends and tax withheld.
- **Income:** year pills and a chart comparing net dividends per month for up to three years, where tapping a month shows each year's value.
- **Data:** report import, plus status badges for backup, US and EU price sources, imported reports and FX rates, and entries for keys, deletion and the app version.

#### Scenario: Data status
- **WHEN** no Finnhub key is set
- **THEN** the US prices badge says Yahoo without a key
