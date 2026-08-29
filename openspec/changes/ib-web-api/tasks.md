## 1. Feasibility and API Boundary

- [x] 1.1 Confirm exact Web API scope needed (Trading API read-only endpoints for account summary, positions, trade confirmations) and record required Client Portal Gateway (CPGW) setup (base URL/port, session init/status/tickle endpoints) in design.md.
- [x] 1.2 Verify the target IBKR account can run the Client Portal Gateway and complete a browser login, and document the manual setup steps (download CPGW, run it, log in at https://localhost:5000) separate from TWS/Gateway API access.

## 2. Web API Connector

- [x] 2.1 Implement a session manager that calls `POST /iserver/auth/ssodh/init` to initialize the brokerage session against a locally running Client Portal Gateway, and verify it succeeds against a mocked CPGW response and fails clearly when CPGW is unreachable.
- [x] 2.2 Implement session-status checking (`GET /iserver/auth/status`) and periodic keep-alive (`POST /tickle`), and verify a health-check style request fails clearly when the session is not authenticated or has expired, directing the user to log in via the CPGW browser page.
- [x] 2.3 Implement read-only account/trade/position requests over HTTPS and verify responses are parsed into structured Python data (not raw JSON) with clear errors on malformed responses.
- [ ] 2.4 Add explicit error mapping for CPGW unreachable, session-not-authenticated, session-expiry, permission-denial, and rate-limiting scenarios, and verify each produces an actionable diagnostic (mirroring `IBDiagnostic`/`IBConnectionError` from `src/ib_connector.py`).

## 3. Data Normalization and Storage

- [ ] 3.1 Map Web API payloads into the existing `{"trades", "dividends", "taxes", "corp_actions"}` schema (the same shape used by `src/ib_normalizer.py`) and verify the normalized structure matches CSV-import and TWS/Gateway-import expectations.
- [ ] 3.2 Reuse the existing `SourceKey`-based deduplication in `save_to_database()` for Web API records, ensuring each record's `source` field is unique per broker transaction, and verify duplicate Web API syncs do not double-insert trades.
- [ ] 3.3 Run the project's existing import and FIFO tests against the new normalization path and verify the tax engine remains unchanged.

## 4. UI and Operational Controls

- [ ] 4.1 Add an opt-in configuration flag (`IB_WEB_API_ENABLED`, default `False`) plus CPGW base URL configuration, and verify the connector refuses to run when disabled or misconfigured, without affecting CSV import or the existing TWS/Gateway connector.
- [ ] 4.2 Add API endpoints (e.g., `/ib/web/status`, `/ib/web/sync`) and a UI action for manual Web API sync, and verify the workflow reports start, success, and failure states consistently (including a clear "log in via CPGW" state), matching the pattern used for `/ib/status` and `/ib/sync`.

## 5. Verification and Rollout

- [ ] 5.1 Run the relevant unit and integration tests for CPGW session handling, normalization, DB write safety, and connection diagnostics.
- [ ] 5.2 Verify the Web API connector, the existing TWS/Gateway connector, and CSV import can each be enabled/disabled independently without one affecting another.
- [ ] 5.3 Document setup requirements for the Client Portal Gateway (download, run, browser login/2FA) and note the operational limits of read-only account access.
