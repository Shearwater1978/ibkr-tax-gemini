## ADDED Requirements

### Requirement: Ticker page in a new tab

Every ticker shown in the Dashboard, Dividends, Analytics, and Portfolio views MUST be a link that opens a ticker page in a new browser tab. The ticker page MUST show, for the most recently calculated year, summary figures (realized P&L, dividends, total result, open cost, and share of total open cost) and tables of its sales, dividend payments, and open lots, with amounts in the selected display currency. It MUST provide a link back to the portal, and MUST show an empty state when no calculation is available. Ticker text MUST be rendered without interpreting markup.

#### Scenario: Open a ticker

- **WHEN** the user selects a ticker in any table
- **THEN** a new browser tab opens with the ticker page and the original view stays unchanged

#### Scenario: No calculation available

- **WHEN** the ticker page is opened and no calculation was saved
- **THEN** an empty state is shown instead of tables

#### Scenario: Ticker without activity in a table

- **WHEN** a ticker has no sales, dividends, or open lots
- **THEN** the corresponding table states that there are none

### Requirement: Ticker hover card

Hovering or focusing a ticker anywhere in the portal MUST show a card with its share of total open cost, realized P&L, dividends, and total result in the selected display currency, and the card MUST disappear when the pointer or focus leaves the ticker or Escape is pressed.

#### Scenario: Hover a ticker

- **WHEN** the user hovers a ticker
- **THEN** a card shows its share of open cost as a percentage, realized P&L, dividends, and total result

#### Scenario: Leave the ticker

- **WHEN** the pointer leaves the ticker
- **THEN** the card is hidden
