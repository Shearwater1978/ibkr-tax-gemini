## Why

The portal opens empty: the user must go to Workspace, import data, pick a year, and press Calculate before anything appears. The portal should show the data right after it starts.

## What Changes

- On start, once the backend is ready, the portal imports the statement files automatically, loads the available years, selects the latest, and runs the calculation, ending on the Dashboard.
- A failed import does not block loading already stored data; the failure is shown in the Workspace import status.
- Manual Import and Calculate stay available.

## Capabilities

### New Capabilities

### Modified Capabilities
- `web-portal`: automatic data loading on start.

## Impact

- `gui/ui/index.html` only (startup sequence). Uses the existing idempotent import endpoint.
