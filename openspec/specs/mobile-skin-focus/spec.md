# mobile-skin-focus Specification

## Purpose
Defines the Focus skin: a warm, conversational interface that explains the portfolio in plain sentences first and numbers second, for users new to investing.

## Requirements

### Requirement: Focus navigation
Focus SHALL have two destinations, Today and Income, and a central "+" button. Today SHALL also show a Settings button at the top. The "+" button SHALL open a menu with:
- adding reports;
- activity;
- choosing own currency or USD;
- backup and price status;
- security and recovery;
- Settings (including skins);
- deleting imports.

#### Scenario: Menu
- **WHEN** the user taps "+"
- **THEN** the menu lists adding reports, activity, currency, backup and prices, security, settings and deletion

### Requirement: Focus summaries
Today SHALL open with the portfolio value and one generated sentence. The sentence states whether the portfolio is up or down today, by how much in USD, and which one or two positions contributed most in that direction. It then states the total return since the first import as a percentage. When prices are missing for some positions, the sentence SHALL say the figures are incomplete. Sentences SHALL be built only from the app's own numbers, on the device.

#### Scenario: Day up
- **WHEN** the portfolio rose $110 today, mostly from NVIDIA and SAP
- **THEN** the sentence reads like "You’re up $110 today, mostly from NVIDIA and SAP."

#### Scenario: Missing prices
- **WHEN** a held position has no price
- **THEN** the sentence notes that some prices are missing

### Requirement: Focus cards, holdings, stories and income
Focus SHALL provide the following:
- **Insight cards:** swipeable cards, with page dots below them that show the current card, for this month's dividends, the biggest move today, price freshness (tapping it refreshes prices) and dividends of the last 12 months.
- **Allocation donut:** the four largest positions and "others" by weight. Tapping it opens all holdings, grouped as "Rising today" and "Falling today".
- **Position stories:** tapping a holding opens its story in sentences: invested and current value, gains from sales, dividends after tax, then the total.
- **Income:** year pills including "All years", a sentence with the period's net dividends and the change against the previous period, and a timeline of payments.

#### Scenario: Story
- **WHEN** the user opens Apple
- **THEN** the story states the invested amount, the current value, the gain from sales, the net dividends and the total return
