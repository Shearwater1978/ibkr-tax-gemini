## 1. Feasibility and API Boundary

- [ ] 1.1 Confirm the exact IB integration mode (TWS vs IB Gateway), required host/port/client ID settings, and supported read-only account requests for the target environment.
- [ ] 1.2 Verify the project can keep CSV import as the default workflow while adding an optional live-API sync path without breaking existing tax calculations.

## 2. API Integration Layer

- [ ] 2.1 Implement a connection manager for the IB socket client and verify connection health checks fail clearly when the gateway is unavailable.
- [ ] 2.2 Implement a read-only account sync process that requests the required trade and position payloads and verifies they are returned in a structured format.
- [ ] 2.3 Add explicit error mapping for auth, permission, and session-loss scenarios and verify the user sees actionable diagnostics.

## 3. Data Normalization and Storage

- [ ] 3.1 Map live IB payloads into the existing transaction/corporate-action schema and verify the normalized structure matches CSV-import expectations.
- [ ] 3.2 Add import deduplication and validation rules for live payloads and verify duplicate trades are not inserted into the encrypted database.
- [ ] 3.3 Run the project’s existing import and FIFO tests against the new normalization path and verify the tax engine remains unchanged.

## 4. UI and Operational Controls

- [ ] 4.1 Add a simple configuration surface for IB host, port, client ID, and connection status, and verify the UI shows ready/error states without blocking CSV flows.
- [ ] 4.2 Add a manual “live sync” action and verify the workflow reports start, success, and failure states consistently.

## 5. Verification and Rollout

- [ ] 5.1 Run the relevant unit and integration tests for import normalization, DB write safety, and connection diagnostics.
- [ ] 5.2 Verify the live sync feature works only when configured and does not break the existing CSV-only workflow.
- [ ] 5.3 Document setup requirements for IB Gateway/TWS and note the operational limits of read-only account access.
