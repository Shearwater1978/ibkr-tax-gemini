## Why

Before filing a tax return, the user needs to know whether archived IBKR reports contain enough acquisition history to support a planned sale under FIFO. The current calculator detects an unmatched sale only after a real sell event is processed, which is too late and can make a missing historical report look like a calculation problem.

## What Changes

- Add a smoke/preflight operation that accepts one or more planned asset quantities by ticker.
- Reconstruct holdings from imported transactions through the requested as-of date using the same FIFO ordering and corporate-action rules as calculation.
- Report per ticker whether the requested quantity is fully covered, partially covered, or not covered.
- Show available quantity, requested quantity, missing quantity, and the source date range/lot evidence used for the result.
- Ignore price, commission, FX rate, and tax totals; this operation MUST NOT create or persist a SELL transaction.
- Support CLI and GUI/API invocation with machine-readable and human-readable output.

## Capabilities

### New Capabilities

- `fifo-coverage-check`: Non-destructive preflight validation of planned sales against imported FIFO inventory.

### Modified Capabilities

- None.

## Impact

Affected areas are the FIFO/processing service layer, CLI command surface, GUI/API adapter, reporting/export formatting, and focused tests. The existing tax calculation and database records remain unchanged. The operation may read imported transactions and corporate actions but must not mutate them.
