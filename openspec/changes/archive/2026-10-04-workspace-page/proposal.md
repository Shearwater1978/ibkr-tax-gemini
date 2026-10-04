## Why

Tax Calculation, Data, and Coverage are three separate tabs, each holding a few controls on an otherwise empty page. They are all working tools rather than reports, and users switch between them while preparing a calculation (import data, check coverage, calculate).

## What Changes

- Replace the Tax Calculation, Data, and Coverage tabs with a single "Workspace" tab whose page shows the three groups as stacked sections: Tax Calculation (progress and status), Data (import and IB connections), and FIFO Coverage preflight.
- Starting a calculation opens the Workspace page; "Go to Tax Calculation" links point to it.
- All existing controls, element behaviour, and API calls remain unchanged.

## Capabilities

### New Capabilities

### Modified Capabilities
- `web-portal`: replace the separate tool tabs with one Workspace tab, and update the calculation navigation behaviour.

## Impact

- `gui/ui/index.html` only. No API, dependency, or data changes.
