## Purpose

Defines how the app retrieves and presents market prices with clear currency, freshness, privacy, and failure behavior.

## ADDED Requirements

### Requirement: Retrieve prices for held instruments
The app SHALL retrieve the latest available price for held instruments in the supported price scope when the overview opens and when the user refreshes it. The supported scope is US-listed instruments held in USD, and instruments listed on a supported European exchange (see "Retrieve European prices from Yahoo Finance"); holdings outside the scope SHALL NOT be requested. A holding whose listing exchange is a supported European exchange SHALL be priced as a European listing even when it is held in USD. Before price integration is released, the selected provider's terms SHALL be verified to permit a user's personal API key to be used from this client app. This verification does not cover the keyless Yahoo Finance fallback, which the project owner has accepted as unofficial and unlicensed; it SHALL be reviewed again before a public release.

#### Scenario: Price refresh
- **WHEN** the user opens the overview or refreshes prices
- **THEN** the app requests and displays the latest available prices for held instruments

#### Scenario: Provider not approved
- **WHEN** the provider's terms for use of a personal key from this app have not been verified
- **THEN** the app does not enable that provider for release

#### Scenario: Holding outside the price scope
- **WHEN** a holding is neither a USD holding of a US-listed instrument nor listed on a supported European exchange
- **THEN** the app requests no price for it, shows its price as unavailable, and marks its currency subtotal incomplete

### Requirement: Minimize price-request data
The app SHALL send only the instrument identifiers required to retrieve prices (the ticker symbol in the MVP) and, for Finnhub, the user's provider API key, and SHALL NOT send quantities, account data, names, report contents, or other personal data.

#### Scenario: Price request content
- **WHEN** the app requests market prices
- **THEN** the request contains only the required instrument identifiers and, for Finnhub, the user's provider API key

### Requirement: Use a user-supplied provider key
The app SHALL NOT ship with a market-data provider API key. Price retrieval SHALL use an API key entered by the user, stored only in encrypted local storage protected by the device key, sent only to the provider, and never written to logs, diagnostics, or backups in plaintext.

#### Scenario: No key configured
- **WHEN** no provider API key has been entered
- **THEN** the app sends no request to Finnhub and retrieves prices from the Yahoo Finance fallback

#### Scenario: Key entered
- **WHEN** the user enters a provider API key
- **THEN** the app stores it encrypted and uses it for subsequent price requests

#### Scenario: Key rejected
- **WHEN** the provider rejects the API key
- **THEN** the app informs the user and shows only stale last-known prices or an unavailable state

### Requirement: Fall back to Yahoo Finance without a key
While no Finnhub key is set, the app SHALL retrieve prices for the same scope from Yahoo Finance's public batch (spark) endpoint, which needs no key. Each request SHALL cover up to 20 symbols, so a refresh needs one request per 20 holdings, and SHALL contain only the ticker symbols (with share-class separators written the way Yahoo expects, e.g. `BRK-B`) and a generic browser user agent. The endpoint returns no currency; because only USD holdings are requested and symbols without an exchange suffix are US listings, the quotes SHALL be treated as USD. The main page SHALL name Yahoo Finance as the price source, and Settings SHALL explain that this source is unofficial and may be delayed, limited, or stop working. Once a Finnhub key is set, the app SHALL use only Finnhub for US-listed instruments; European listings always use Yahoo Finance.

#### Scenario: No key
- **WHEN** no Finnhub key is set and the user opens the overview or refreshes prices
- **THEN** the app requests in-scope symbols from Yahoo Finance and shows that prices come from Yahoo Finance

#### Scenario: Key set
- **WHEN** a Finnhub key is set
- **THEN** the app requests prices for US-listed instruments only from Finnhub, and requests only European listings from Yahoo Finance

#### Scenario: Symbol unknown to Yahoo Finance
- **WHEN** Yahoo Finance does not know a symbol
- **THEN** the app shows that holding's price as unavailable

### Requirement: Retrieve European prices from Yahoo Finance
Holdings listed on a supported European exchange SHALL be priced from Yahoo Finance's public chart endpoint, one request per symbol, whether or not a Finnhub key is set, because Finnhub's free plan has no European prices. The request symbol SHALL be the IBKR ticker, with a trailing dot removed and spaces or dots written as hyphens, followed by the exchange suffix below. Requests SHALL contain only that symbol and a generic browser user agent. A quote SHALL be accepted only when its currency equals the holding's currency; prices Yahoo gives in pence (`GBp`) SHALL be converted to pounds (`GBP`) first. Quotes SHALL be cached under the request symbol, so a European listing never overwrites a US listing with the same ticker. When a Finnhub key is set and European listings are held, the main page SHALL say that European prices come from Yahoo Finance. An IBKR exchange code not in the table is outside the scope.

| IBKR listing exchange | Yahoo suffix | Exchange time zone | Regular session |
|---|---|---|---|
| IBIS, IBIS2 (Xetra) | `.DE` | Europe/Berlin | 09:00–17:30 |
| FWB, FWB2 (Frankfurt) | `.F` | Europe/Berlin | 08:00–22:00 |
| SWB (Stuttgart) | `.SG` | Europe/Berlin | 08:00–22:00 |
| LSE, LSEETF (London) | `.L` | Europe/London | 08:00–16:30 |
| AEB (Amsterdam) | `.AS` | Europe/Amsterdam | 09:00–17:30 |
| SBF (Paris) | `.PA` | Europe/Paris | 09:00–17:30 |
| ENEXT.BE (Brussels) | `.BR` | Europe/Brussels | 09:00–17:30 |
| BVL (Lisbon) | `.LS` | Europe/Lisbon | 08:00–16:30 |
| EBS (SIX Swiss) | `.SW` | Europe/Zurich | 09:00–17:30 |
| BVME (Milan) | `.MI` | Europe/Rome | 09:00–17:30 |
| BM (Madrid) | `.MC` | Europe/Madrid | 09:00–17:30 |
| VSE (Vienna) | `.VI` | Europe/Vienna | 09:00–17:30 |
| SFB (Stockholm) | `.ST` | Europe/Stockholm | 09:00–17:30 |
| CPH (Copenhagen) | `.CO` | Europe/Copenhagen | 09:00–17:00 |
| OSE (Oslo) | `.OL` | Europe/Oslo | 09:00–16:20 |
| HEX (Helsinki) | `.HE` | Europe/Helsinki | 10:00–18:30 |
| WSE (Warsaw) | `.WA` | Europe/Warsaw | 09:00–17:00 |

#### Scenario: Xetra listing
- **WHEN** the user holds SAP in EUR with listing exchange IBIS
- **THEN** the app requests `SAP.DE` from Yahoo Finance and shows its EUR price

#### Scenario: London listing in pence
- **WHEN** Yahoo Finance quotes an LSE listing in `GBp` and the holding is in GBP
- **THEN** the app divides the price and previous close by 100 and shows them in GBP

#### Scenario: USD fund on a European exchange
- **WHEN** the user holds a fund in USD with listing exchange LSE
- **THEN** the app requests the `.L` symbol and accepts the quote only if Yahoo Finance quotes it in USD

#### Scenario: Currency mismatch
- **WHEN** Yahoo Finance quotes a European symbol in a currency other than the holding's
- **THEN** the app shows that holding's price as unavailable

#### Scenario: Finnhub key set
- **WHEN** a Finnhub key is set and the user holds European listings
- **THEN** US listings are priced from Finnhub, European listings from Yahoo Finance, and the main page names both sources

### Requirement: Show quote currency and freshness
The app SHALL show the currency and retrieval time for each price. Market hours are those of the instrument's exchange: the US regular session for US listings and the table in "Retrieve European prices from Yahoo Finance" for European listings; exchange holidays are not modelled. European quotes from Yahoo Finance are delayed: during their session, a quote up to 30 minutes old SHALL be labelled delayed, and an older one SHALL be marked stale. For US listings, during market hours, a price older than 15 minutes SHALL be marked stale. Outside market hours, the last available close MAY be shown as the latest close, but SHALL NOT be described as a live price. Each price SHALL also show its change since the previous close. Prices and per-position values SHALL remain in their quote currency; only the informational header total in `mobile-portfolio-overview` is converted.

#### Scenario: Fresh market-hours quote
- **WHEN** a quote is no more than 15 minutes old during market hours
- **THEN** the app shows its currency and retrieval time without a stale marker

#### Scenario: Stale market-hours quote
- **WHEN** a quote is more than 15 minutes old during market hours
- **THEN** the app marks it stale and does not present it as current

#### Scenario: Outside market hours
- **WHEN** the latest available quote is the prior market close
- **THEN** the app labels it as the latest close and shows its currency and timestamp

#### Scenario: Delayed European quote
- **WHEN** a European quote is 20 minutes old during its exchange's session
- **THEN** the app labels it delayed, not live and not stale

### Requirement: Handle price retrieval failures
The app SHALL remain usable when a price request fails and SHALL show the last known price as stale, or indicate that no price is available.

#### Scenario: Offline or provider error
- **WHEN** a price request fails
- **THEN** the app informs the user and shows only a stale last-known price or an unavailable state

### Requirement: Retrieve exchange rates from NBP
The app SHALL retrieve the latest NBP table A mid rates for the informational USD total. Requests SHALL contain only currency codes and SHALL NOT require a key. Rates SHALL be cached locally with their table date.

#### Scenario: Rates available
- **WHEN** the main page opens or the user refreshes and NBP responds
- **THEN** the app caches the rates with their table date and uses them for the USD total

#### Scenario: Offline
- **WHEN** NBP cannot be reached and cached rates exist
- **THEN** the app uses the cached rates and shows their table date

#### Scenario: No rate available
- **WHEN** no rate exists for a position's currency
- **THEN** the USD total is marked incomplete
