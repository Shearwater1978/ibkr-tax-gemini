## Why

The project already has a plan/implementation for a live IBKR connection through the classic TWS/IB Gateway socket API (`ib-live-api`). That path requires a locally running desktop Gateway process. Interactive Brokers also publishes a separate **Client Portal Web API** (REST + JSON over HTTPS, documented at https://www.interactivebrokers.com/docs/web-api/introduction) that exposes the same read-only account/trade data. For retail/individual clients (this project's use case), authentication is handled by the **Client Portal Gateway (CPGW)** — a local Java process the user runs and logs into via browser (standard IBKR credentials + 2FA) — not the OAuth 2.0 `private_key_jwt` flow, which is reserved for institutional/third-party integrations that register with IBKR's API Solutions team. Evaluating and adding the CPGW-based Web API as an alternative live data source would let the app fetch account/trade data over HTTPS instead of a raw TCP socket, while keeping the same "local companion process + session" model already familiar from IB Gateway.

## What Changes

- Add a dedicated live-connection capability for Interactive Brokers through the official **Web API** (Client Portal / REST), distinct from the existing TWS/IB Gateway socket capability.
- Support a read-only sync flow (account summary, positions, trade confirmations) using HTTPS + JSON, authenticated through a locally running **Client Portal Gateway (CPGW)** session (browser-based login with 2FA, `/iserver/auth/ssodh/init` session initialization, periodic `/tickle` keep-alive).
- Normalize Web API payloads into the same internal transaction schema already used by CSV import and the TWS/IB Gateway connector, so the tax engine and FIFO logic remain unchanged.
- Keep this connector fully optional and isolated: CSV import remains the default workflow, and the existing `ib-live-api` (TWS/Gateway) capability continues to work unmodified if configured instead.
- Surface authentication, session-expiry, authorization, and request failures as explicit diagnostics, consistent with the error-handling approach used for the TWS/Gateway connector.

## Capabilities

### New Capabilities
- `ib-web-api`: Live, read-only connection and account/trade synchronization with Interactive Brokers via the Client Portal Web API (REST over HTTPS, authenticated through a locally running Client Portal Gateway session), as an HTTPS-based alternative to the TWS/IB Gateway socket connector.

### Modified Capabilities
- None. This does not change the requirements of `ib-live-api` (TWS/Gateway); it adds a second, independent live data source alongside it.

## Impact

Affected areas: a new connector module (parallel to `src/ib_connector.py`), reuse of the existing normalization layer (`src/ib_normalizer.py`) or an equivalent Web-API-specific normalizer feeding the same `save_to_database()` schema, new configuration (CPGW base URL/port, session timeout), and optional new UI/API surface for status and manual sync (parallel to `/ib/status` and `/ib/sync`). No changes to CSV import, FIFO/tax calculation, or the existing TWS/Gateway connector are required. Setup also requires the user to separately download/run the Client Portal Gateway and complete a one-time-per-session browser login; this is an external, manual precondition outside the app's code.

