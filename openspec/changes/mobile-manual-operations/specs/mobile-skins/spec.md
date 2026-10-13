## MODIFIED Requirements

### Requirement: Keep feature parity across skins
Every skin SHALL give access, directly or behind taps, to:
- the portfolio value with today's change;
- every held position with its details, including the profit breakdown;
- sold positions;
- the own-currency and USD views;
- today's top gainers and losers;
- dividends by year and month with withholding tax, and each company's dividend history;
- the activity list with search and type filters;
- report import;
- adding, editing and deleting operations by hand;
- backup encryption, recovery code and backup location;
- price key settings;
- deleting imports;
- the app version.

Data-related wording that the specs require SHALL appear in every skin. This covers approximate USD labels, exchange-rate dates, price freshness, incomplete markers, and "informational" notes on profit figures.

#### Scenario: Feature reachable in every skin
- **WHEN** a feature from the list is checked in each skin
- **THEN** it can be reached in every skin

#### Scenario: Freshness label
- **WHEN** a price is delayed or stale
- **THEN** every skin labels it as such wherever the price is shown

#### Scenario: Manual entry in every skin
- **WHEN** the skin catalog test checks the manual-entry feature
- **THEN** Classic, Calm, Bento and Focus each name where "Add operation" is
