## Purpose

Ensure that when one ticker symbol refers to more than one ISIN over the imported history, each ISIN is treated as a separate instrument with its own acquisition history, while the resulting identity change stays visible and verifiable to the user in calculation output and tax reports.

## ADDED Requirements

### Requirement: Instrument identity persistence

The system MUST persist the broker instrument identity for every imported transaction alongside the ticker, including the ISIN, the broker contract identifier, and the instrument description, and MUST read that identity from the broker data source rather than inferring it only from the ticker symbol.

#### Scenario: Trade description carries an ISIN

- **WHEN** an imported trade record for ticker `OKE` carries the description `OKE(US6826801036)` and the account statement lists ONEOK INC with conid `10794`
- **THEN** the stored transaction records ticker `OKE` together with ISIN `US6826801036` and conid `10794`

#### Scenario: Record has no ISIN available

- **WHEN** an imported record carries no ISIN in any supported broker section
- **THEN** the transaction is stored with an empty ISIN and the import MUST NOT fail and MUST NOT assign an invented identifier

#### Scenario: Ticker with a single ISIN

- **WHEN** every imported record for a ticker resolves to the same ISIN
- **THEN** the system MUST behave exactly as it does today, with no identity change recorded and no difference in calculation or report output

### Requirement: ISIN change detection

The system MUST detect when a single ticker symbol resolves to more than one ISIN in the imported history and MUST record that boundary as a dated instrument-identity change, using the date of the earliest transaction belonging to the new ISIN as the change date.

#### Scenario: Ticker remapped to a new ISIN

- **WHEN** imported history contains ticker `OKE` with ISIN `US6826801036` through 2024-12-31 and ticker `OKE` with a different ISIN from 2025-03-04 onward
- **THEN** the system records one identity change for `OKE` with previous ISIN `US6826801036`, new ISIN, and change date `2025-03-04`

#### Scenario: Change across many statements

- **WHEN** the ISIN change is observed for the first time in a later Activity Statement while earlier statements already contain the previous ISIN
- **THEN** the system MUST detect the change from the stored history alone and MUST NOT require the earlier statements to be re-imported

#### Scenario: Rows predating identity capture

- **WHEN** existing database rows have no stored ISIN and later rows for the same ticker do
- **THEN** the rows without an ISIN are associated with the ticker's earliest observed ISIN and MUST NOT be treated as a separate identity change

### Requirement: Per-identity FIFO lot tracking

The system MUST maintain FIFO acquisition lots per instrument identity rather than per raw ticker symbol, so that a sale is matched only against lots acquired in the same identity. Each identity MUST retain its own complete acquisition history, including purchases, transfers, splits, and corporate actions attributed to that identity.

#### Scenario: Sale matched within the current identity

- **WHEN** a sale of ticker `OKE` occurs after the ISIN change date
- **THEN** the cost basis is drawn only from lots acquired under the ISIN in effect on the sale date, and never from lots belonging to the previous ISIN

#### Scenario: Previous identity keeps its history

- **WHEN** the old ISIN still has open lots after the change
- **THEN** those lots remain in inventory attributed to the old ISIN with their original acquisition dates and prices, and are available to a sale of that identity

#### Scenario: Sale exceeding its own identity inventory

- **WHEN** a sale quantity exceeds the lots acquired under that sale's own ISIN
- **THEN** the system MUST raise the existing insufficient-inventory diagnostic naming the ticker and ISIN, and MUST NOT silently consume lots from the other identity

#### Scenario: Dividends and corporate actions follow their identity

- **WHEN** a dividend or corporate action is imported for ticker `OKE` under a given ISIN
- **THEN** it is attributed to that identity and MUST NOT alter the inventory of any other identity sharing the ticker symbol

### Requirement: Identity change diagnostic

The system MUST emit a non-blocking diagnostic when an ISIN change is detected, naming the ticker, the previous ISIN, the new ISIN, and the change date, and MUST complete the calculation rather than aborting it.

#### Scenario: Diagnostic reported during calculation

- **WHEN** a calculation is run for a year in which an identity change is present in the loaded history
- **THEN** the result includes a diagnostic identifying ticker `OKE`, previous ISIN, new ISIN, and change date, and the calculation completes and returns results

#### Scenario: No change means no diagnostic

- **WHEN** no ISIN change exists for any ticker in the loaded history
- **THEN** no identity-change diagnostic is produced and existing output is unchanged

### Requirement: Transparency in tax report output

The system MUST present the identity change transparently for the user: reported results MUST remain organized by ticker, the old ISIN MUST retain its full acquisition history, and the identity change MUST be disclosed in the exported report rather than causing a duplicated or unexplained ticker entry.

#### Scenario: Single reported row per ticker

- **WHEN** a report is generated for a ticker whose ISIN changed
- **THEN** the report shows the ticker once, with its history under both identities attributed to the correct ISIN and no unexplained duplicate ticker rows

#### Scenario: Acquisition history preserved in exports

- **WHEN** the user inspects the transaction history rows for the ticker
- **THEN** each row shows the ISIN it belongs to, and rows from the old ISIN retain their original acquisition dates, quantities, and prices

#### Scenario: Identity change disclosed in the report

- **WHEN** a report is generated for a year containing an identity change
- **THEN** the report discloses the ticker, previous ISIN, new ISIN, and change date so the user can verify the boundary against the broker statement

### Requirement: Ticker filtering across identities

Ticker-based selection MUST match all identities of that ticker, and any result MUST state which identity it refers to, so a user filtering by ticker is not silently given only one identity's lots.

#### Scenario: Calculating a single ticker

- **WHEN** the user calculates a single ticker that has more than one ISIN in history
- **THEN** the calculation includes the history of all of that ticker's identities and identifies the identity behind each reported lot and result

#### Scenario: Planned sale coverage for a remapped ticker

- **WHEN** a FIFO coverage check is run for a ticker whose ISIN changed, as of a date after the change
- **THEN** available quantity is computed from the identity in effect on that date, and the returned evidence identifies the ISIN behind each evidence lot


