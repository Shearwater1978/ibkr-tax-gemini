## Why

The repository's current main branch contains the calculation core but no tracked Electron/FastAPI GUI, while the existing `gui-start` branch contains a working proof-of-concept dashboard. The GUI should be reintroduced as a deliberate, tested application layer that consumes the current core without overwriting its security, import, and calculation improvements.

## What Changes

- Add a maintained Electron desktop shell and vanilla HTML/JavaScript dashboard based on the `gui-start` reference.
- Add a local FastAPI backend exposing year discovery, import, calculation, and report-opening workflows.
- Preserve calculation diagnostics and return stable HTTP errors, including 404 when no data exists for a requested year.
- Report inserted/skipped import counts and calculation/export completeness to the UI.
- Add backend readiness handling, API tests, and an Electron startup smoke test.
- Harden the GUI boundary with context isolation, disabled Node integration in the renderer, restricted CORS, and validated file-opening paths.

## Capabilities

### New Capabilities

- `gui-api`: Local FastAPI contract for import, calculation, diagnostics, and report access.
- `desktop-dashboard`: Electron dashboard for the local tax workflow.

### Modified Capabilities

- None.

## Impact

Affected areas are `gui/`, the Python API adapter, report and import response contracts, and API/integration tests. The implementation must use the current `src/` modules from `main`, not copy the older `src/` implementation from `gui-start`. Electron dependencies remain isolated under `gui/package.json`.
