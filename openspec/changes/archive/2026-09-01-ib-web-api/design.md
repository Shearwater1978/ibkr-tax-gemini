## Context

See proposal.md for motivation. The project already has `ib-live-api` (TWS/IB Gateway socket connector) with a source-agnostic normalization layer (`src/ib_normalizer.py`) that feeds `src/parser.py::save_to_database()`. This design adds a second, independent live connector using IBKR's Client Portal Web API (REST/JSON over HTTPS). For retail/individual clients, IBKR requires running a local **Client Portal Gateway (CPGW)** - a Java process the user starts and logs into via browser (standard credentials + 2FA) - rather than the OAuth 2.0 `private_key_jwt` flow, which is reserved for institutional/third-party integrations registered with IBKR's API Solutions team and is out of scope here. This design reuses that same normalization contract rather than the TWS connector's shape directly.

## Goals / Non-Goals

**Goals:**
- Add a Web-API-based live connector that talks to a locally running Client Portal Gateway (CPGW) over HTTPS and performs read-only account/trade/position requests using its authenticated browser session.
- Reuse the existing normalized transaction schema (the same shape produced by `src/ib_normalizer.py` for the TWS connector) so FIFO/tax code needs no changes.
- Keep this connector fully independent of `ib-live-api`: either, both, or neither can be configured without interference.
- Model connection, session-not-authenticated, session-expiry, and permission failures as explicit diagnostics, following the same pattern as `IBConnectionError`/`IBDiagnostic` in `src/ib_connector.py`.

**Non-Goals:**
- Order placement, modification, or cancellation.
- Replacing the TWS/IB Gateway connector or the CSV import pipeline.
- Implementing OAuth 2.0 `private_key_jwt` / institutional third-party registration flows - those require a formal IBKR API Solutions engagement and are not applicable to this project's retail use case.
- Implementing the Account Management / Introducing Broker portion of the Web API (client registration, funding) - only the Trading API's read-only account/trade endpoints are in scope.
- Automating the CPGW process lifecycle or the browser-based login/2FA step itself; the user starts CPGW and logs in manually, same operational shape as starting IB Gateway/TWS for the socket connector.

## Decisions

1. **New, separate module (`src/ib_web_connector.py`), not a modification of `src/ib_connector.py`.** The Web API is a different transport (HTTPS/REST vs. TCP socket) and a different session model (CPGW browser-authenticated session vs. Gateway socket login), so sharing a class would force one class to support two unrelated protocols. Keeping them separate follows the existing decision to keep live connectivity isolated and optional.
2. **Reuse the existing normalized dict shape.** Rather than inventing a new intermediate format, the Web API connector's sync layer will produce the same `{"trades": [], "dividends": [], "taxes": [], "corp_actions": []}` structure already consumed by `save_to_database()`. This avoids a third code path in the tax engine.
3. **Client Portal Gateway (CPGW) session, not OAuth 2.0 `private_key_jwt`.** IBKR only supports OAuth 2.0 `private_key_jwt` for institutional/third-party integrations that register with its API Solutions team; retail/individual access is via a locally running CPGW process authenticated through a one-time-per-session browser login (credentials + 2FA). The connector will call `POST /iserver/auth/ssodh/init` to (re)initialize the brokerage session, check status via `GET /iserver/auth/status`, and issue periodic `POST /tickle` keep-alive requests. The CPGW base URL/port is configured via the project's existing `python-decouple` `.env` convention, consistent with `SQLCIPHER_KEY` and the TWS connector's `IB_HOST`/`IB_PORT`.
4. **Independent opt-in flag.** A separate `IB_WEB_API_ENABLED` flag (default `False`) gates this connector, mirroring `IB_LIVE_ENABLED` for the TWS connector. Both flags are independent so either connector can be enabled without the other.
5. **HTTP client library.** Use `requests` (already a project dependency) for synchronous REST calls, with TLS verification configurable to accommodate CPGW's default self-signed local certificate; no new HTTP dependency is introduced unless streaming/session-keepalive needs prove insufficient during implementation.

## Risks / Trade-offs

- [Risk] CPGW sessions require periodic keep-alive (`/tickle`) requests or they expire, and ultimately require a fresh manual browser login after some hours → mitigate with an explicit session-status check (`/iserver/auth/status`) before each sync, surfaced as a clear "please re-authenticate via https://localhost:5000" diagnostic if the session is not authenticated.
- [Risk] CPGW uses a self-signed local TLS certificate by default, which can complicate `requests`-based HTTPS calls → mitigate with an explicit, documented configuration option for certificate handling rather than silently disabling verification everywhere.
- [Risk] Web API trade/position payload shapes differ from both CSV and TWS/Gateway shapes → mitigate with a dedicated normalizer function (or extension of the existing normalizer) with its own unit tests, verified against `save_to_database()` like the TWS connector's normalizer.
- [Risk] Running two independent live connectors (TWS/Gateway and Web API) simultaneously could double-import the same trade → mitigate by relying on the existing `SourceKey` dedup logic in `save_to_database()`, with connector-specific fields (e.g., a distinct `source` prefix) to keep IDs distinguishable while still deduping true repeats from the same connector.

## Migration Plan

1. Add the Web API connector and normalizer behind `IB_WEB_API_ENABLED=False` by default.
2. Verify normalized output is accepted by the existing `save_to_database()` and FIFO engine without modification, mirroring the verification already done for `ib-live-api`.
3. Add UI/API status and manual-sync surfaces parallel to `/ib/status` and `/ib/sync`, scoped under a distinct path (e.g., `/ib/web/status`, `/ib/web/sync`) so both connectors can be operated independently from the same GUI. The status endpoint should clearly indicate when the user needs to log in via the CPGW browser page.
4. Run the project's CSV import and FIFO tests to confirm the tax engine is unaffected.
5. Merge as a feature branch; CSV import remains the baseline path, and both live connectors stay optional.

## Open Questions

- Should the Web API connector eventually replace the TWS/Gateway connector, or should both remain supported long-term as alternative transports?

## Verified Against a Live Account (Task 1.2)

Confirmed end-to-end against a real IBKR account during planning:

1. Installed a JRE (Microsoft Build of OpenJDK 21) and downloaded/unzipped the Client Portal Gateway (`clientportal.gw.zip`).
2. Started it with the bundled `bin\run.bat root\conf.yaml`; it listens on `https://localhost:5000` with a self-signed certificate.
3. Logged in via browser at `https://localhost:5000` with standard IBKR credentials + 2FA - no code involved, purely manual.
4. `POST /v1/api/iserver/auth/status` returned `{"authenticated": true, "connected": true, "competing": false, ...}`, confirming the session-status check this connector will rely on.
5. `GET /v1/api/portfolio/accounts` returned a JSON array of account objects with fields including `id`/`accountId`, `currency`, `type` (e.g. `INDIVIDUAL`), and `tradingType` - confirms this endpoint is usable for the account-summary/account-selection part of the sync without further registration.

Conclusion: no separate Web API registration/approval is needed for retail read-only access beyond the manual CPGW browser login. The connector's implementation tasks (2.x) can proceed using `/iserver/auth/status`, `/tickle`, and `/portfolio/accounts`-family endpoints as verified above.

