## 1. GUI/API Foundation

- [ ] 1.1 Copy only `gui/` application assets from `gui-start` onto current `main` and verify current Python tests remain green
- [ ] 1.2 Add or update Electron dependencies and preload boundary, then verify `npm install` completes without modifying Python dependencies
- [ ] 1.3 Add FastAPI health/readiness endpoint and backend lifecycle handling, then verify readiness is observable before data requests

## 2. API Contract

- [ ] 2.1 Implement year discovery and import endpoints using current DB/parser APIs, then verify response models include sorted years and inserted/skipped counts
- [ ] 2.2 Implement calculation endpoint with current diagnostics and independent Excel/PDF availability, then verify diagnostic errors do not become successful reports
- [ ] 2.3 Preserve HTTP 404 for missing-year data and map calculation failures to stable error bodies, then verify API contract tests
- [ ] 2.4 Implement safe report-opening endpoints restricted to generated project output paths, then verify missing files return 404

## 3. Desktop Dashboard

- [ ] 3.1 Port the `gui-start` dashboard workflow to the current API without copying stale core modules, then verify year loading/import/calculation interactions
- [ ] 3.2 Add loading, error, empty, and successful-result states, then verify old results are not replaced by misleading values after a failed request
- [ ] 3.3 Enforce Electron renderer security with `nodeIntegration` disabled and `contextIsolation` enabled, then verify privileged operations use preload only

## 4. Verification and Documentation

- [ ] 4.1 Add API tests for readiness, import counts, 404 behavior, diagnostics, and export failures, then verify the focused API suite passes
- [ ] 4.2 Add Electron startup smoke test against a local backend, then verify the app starts and reaches the ready state
- [ ] 4.3 Run `pytest`, API tests, `black --check .`, and `npm` checks, then verify all commands pass
- [ ] 4.4 Document GUI/API setup, local security boundary, and recovery behavior, then verify README instructions work from a clean checkout
