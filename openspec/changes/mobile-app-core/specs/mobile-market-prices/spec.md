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
The app SHALL send only the instrument identifiers required to retrieve prices (the ticker symbol in the MVP) and, for Finnhub and Twelve Data, the user's own key for that provider, and SHALL NOT send quantities, account data, names, report contents, or other personal data.

#### Scenario: Price request content
- **WHEN** the app requests market prices
- **THEN** the request contains only the required instrument identifiers and, for Finnhub and Twelve Data, the user's key for that provider in a request header

### Requirement: Choose a price provider and use the user's own key
The user SHALL choose one market-data provider: **None**, **Finnhub** or **Twelve Data** (Android first; iOS follows in a later change). The app SHALL NOT ship with a provider API key and has no backend: every price request goes directly from the device to the chosen provider, using the user's own key and quota. Each provider's key SHALL be stored separately, only in encrypted local storage protected by the device key, sent only to that provider (in a request header, never in a URL), and never written to logs, diagnostics, or backups in plaintext. An existing Finnhub key SHALL keep working: without a stored choice, the app SHALL treat Finnhub as chosen when a Finnhub key exists, and None otherwise.

#### Scenario: None chosen
- **WHEN** the chosen provider is None
- **THEN** the app sends no price request to any provider (Finnhub, Twelve Data or Yahoo Finance), for US and European listings alike, and shows only previously cached prices, marked stale

#### Scenario: Chosen provider without a key
- **WHEN** Finnhub or Twelve Data is chosen but has no key
- **THEN** the app requests no US prices, tells the user to add the key, and requests no prices from another provider

#### Scenario: Key entered
- **WHEN** the user enters a key for a provider
- **THEN** the app checks it with one request for a well-known test symbol, stores it encrypted for that provider only, and uses it while that provider is chosen

#### Scenario: Key rejected
- **WHEN** the provider rejects the API key
- **THEN** the app informs the user and shows only stale last-known prices or an unavailable state

#### Scenario: Existing Finnhub user
- **WHEN** the app updates on a phone that has a Finnhub key and no stored provider choice
- **THEN** Finnhub is the chosen provider and prices keep coming from Finnhub

### Requirement: Never switch providers automatically
When the chosen provider has no key, rejects the key, is rate-limited, is offline, or fails, the app SHALL NOT request prices from another provider. Quotes received before a failure SHALL be kept; older quotes SHALL be shown as stale; holdings without any cached quote SHALL be shown as unavailable.

#### Scenario: Twelve Data rate-limited
- **WHEN** Twelve Data answers a refresh with its rate-limit error
- **THEN** the app keeps the quotes received so far, marks the others stale or unavailable, and sends no request to Finnhub or Yahoo Finance for those symbols

### Requirement: Retrieve US prices from Twelve Data
While Twelve Data is chosen and has a key, US-listed holdings SHALL be priced from Twelve Data's `/quote` endpoint (`https://api.twelvedata.com/quote`), with the key in the header `Authorization: apikey <key>`. Symbols SHALL be sent comma-separated (`symbol=A,B,...`) in chunks of at most 8, because each symbol costs one API credit and the free plan allows 8 credits per minute and 800 per day; a credit-based rate limiter SHALL keep requests within 8 credits per minute. The app SHALL take each quote's currency from the response and its time from `last_quote_at`, else `timestamp`. A cached Twelve Data quote SHALL NOT be requested again within 15 minutes (Finnhub keeps one minute). Symbols SHALL be priced largest position first, and a running refresh SHALL show its progress and the estimated time left (for example "Twelve Data · 24 of 80 · about 7 min left"). When the daily limit is reached, the refresh SHALL stop, keep the quotes received, and explain that the Twelve Data daily limit resets at 00:00 UTC. Twelve Data errors arrive as an HTTP status, or as a body `{"code", "message", "status": "error"}` (also accepted with HTTP 200); 401 means an invalid key, 429 a reached limit, 403 and 404 a symbol not available to the user (left unpriced), 414 a too-long symbol list (the chunk is split and retried), 400 and 5xx a provider error.

#### Scenario: First refresh with 80 holdings
- **WHEN** Twelve Data is chosen on the free plan and 80 US holdings have no cached price
- **THEN** the app requests them 8 at a time, about one request per minute, largest positions first, shows progress with the time left, and shows each price as soon as its chunk arrives

#### Scenario: Daily limit reached
- **WHEN** Twelve Data answers with error 429 after the day's credits are used
- **THEN** the refresh stops, received prices are kept, the remaining holdings show stale or unavailable prices, and the app says the Twelve Data daily limit resets at 00:00 UTC

#### Scenario: Symbol not on the user's plan
- **WHEN** one symbol in a chunk returns error 403 or 404
- **THEN** that holding stays unpriced and the other symbols of the chunk are priced

#### Scenario: Attribution
- **WHEN** a displayed price comes from Twelve Data
- **THEN** the app shows "Source: Twelve Data" near it, and tapping it opens https://twelvedata.com in a browser tab

### Requirement: Retrieve European prices from Yahoo Finance
Holdings listed on a supported European exchange SHALL be priced from Yahoo Finance's public chart endpoint, one request per symbol, while Finnhub or Twelve Data is chosen (never while None is chosen), because neither provider is used for European listings yet. Yahoo Finance is a temporary internal source: it SHALL NOT be offered as a choosable provider, and it will be replaced. The request symbol SHALL be the IBKR ticker, with a trailing dot removed and spaces or dots written as hyphens, followed by the exchange suffix below. Requests SHALL contain only that symbol and a generic browser user agent. A quote SHALL be accepted only when its currency equals the holding's currency; prices Yahoo gives in pence (`GBp`) SHALL be converted to pounds (`GBP`) first. Quotes SHALL be cached under the request symbol, so a European listing never overwrites a US listing with the same ticker. When a Finnhub key is set and European listings are held, the main page SHALL say that European prices come from Yahoo Finance. An IBKR exchange code not in the table is outside the scope.

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

#### Scenario: Provider chosen with European holdings
- **WHEN** Finnhub or Twelve Data is chosen and the user holds European listings
- **THEN** US listings are priced from the chosen provider, European listings from Yahoo Finance, and each displayed price names its own source

### Requirement: Show quote currency and freshness
The app SHALL show the currency, retrieval time and source of each price. The source is the provider that actually produced the cached quote, which can differ from the currently chosen provider; quotes cached before sources were recorded have none. Market hours are those of the instrument's exchange: the US regular session for US listings and the table in "Retrieve European prices from Yahoo Finance" for European listings; exchange holidays are not modelled. European quotes from Yahoo Finance are delayed: during their session, a quote up to 30 minutes old SHALL be labelled delayed, and an older one SHALL be marked stale. For US listings, during market hours, a price older than 15 minutes SHALL be marked stale. Outside market hours, the last available close MAY be shown as the latest close, but SHALL NOT be described as a live price. Each price SHALL also show its change since the previous close. Prices and per-position values SHALL remain in their quote currency; only the informational header total in `mobile-portfolio-overview` is converted.

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
