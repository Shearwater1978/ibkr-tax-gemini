## Purpose

Defines how the app retrieves and presents market prices with clear currency, freshness, privacy, and failure behavior.

## ADDED Requirements

### Requirement: Retrieve prices for held instruments
The app SHALL retrieve the latest available price for held instruments in the supported price scope when the overview opens and when the user refreshes it. In the MVP, the supported scope is US-listed instruments held in USD; holdings outside the scope SHALL NOT be requested. Before price integration is released, the selected provider's terms SHALL be verified to permit a user's personal API key to be used from this client app.

#### Scenario: Price refresh
- **WHEN** the user opens the overview or refreshes prices
- **THEN** the app requests and displays the latest available prices for held instruments

#### Scenario: Provider not approved
- **WHEN** the provider's terms for use of a personal key from this app have not been verified
- **THEN** the app does not enable that provider for release

#### Scenario: Holding outside the price scope
- **WHEN** a holding is not a USD holding of a US-listed instrument
- **THEN** the app requests no price for it, shows its price as unavailable, and marks its currency subtotal incomplete

### Requirement: Minimize price-request data
The app SHALL send only the instrument identifiers required to retrieve prices (the ticker symbol in the MVP) and the user's provider API key, and SHALL NOT send quantities, account data, names, report contents, or other personal data.

#### Scenario: Price request content
- **WHEN** the app requests market prices
- **THEN** the request contains only the required instrument identifiers and the user's provider API key

### Requirement: Use a user-supplied provider key
The app SHALL NOT ship with a market-data provider API key. Price retrieval SHALL use an API key entered by the user, stored only in encrypted local storage protected by the device key, sent only to the provider, and never written to logs, diagnostics, or backups in plaintext.

#### Scenario: No key configured
- **WHEN** no provider API key has been entered
- **THEN** the app makes no price requests, shows prices as unavailable, and offers to add a key in Settings

#### Scenario: Key entered
- **WHEN** the user enters a provider API key
- **THEN** the app stores it encrypted and uses it for subsequent price requests

#### Scenario: Key rejected
- **WHEN** the provider rejects the API key
- **THEN** the app informs the user and shows only stale last-known prices or an unavailable state

### Requirement: Show quote currency and freshness
The app SHALL show the currency and retrieval time for each price. During market hours, a price older than 15 minutes SHALL be marked stale. Outside market hours, the last available close MAY be shown as the latest close, but SHALL NOT be described as a live price. Values SHALL remain in their quote currency without FX conversion.

#### Scenario: Fresh market-hours quote
- **WHEN** a quote is no more than 15 minutes old during market hours
- **THEN** the app shows its currency and retrieval time without a stale marker

#### Scenario: Stale market-hours quote
- **WHEN** a quote is more than 15 minutes old during market hours
- **THEN** the app marks it stale and does not present it as current

#### Scenario: Outside market hours
- **WHEN** the latest available quote is the prior market close
- **THEN** the app labels it as the latest close and shows its currency and timestamp

### Requirement: Handle price retrieval failures
The app SHALL remain usable when a price request fails and SHALL show the last known price as stale, or indicate that no price is available.

#### Scenario: Offline or provider error
- **WHEN** a price request fails
- **THEN** the app informs the user and shows only a stale last-known price or an unavailable state
