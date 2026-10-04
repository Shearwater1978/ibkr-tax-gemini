## Why

All portal amounts are shown in PLN. Users who think in the broker's currencies want to read the same figures in USD or EUR, without leaving the portal or changing the PLN tax calculation.

## What Changes

- The calculation response includes display exchange rates (PLN per USD and per EUR) taken from NBP for the last day of the calculated year (or today for a year still in progress).
- A currency switcher (PLN, USD, EUR) in the top bar converts every monetary amount in Dashboard, Dividends, Analytics, and Portfolio for display, and remembers the choice.
- Excel/PDF reports, the PLN tax figures, and original-currency fields are not affected.

## Capabilities

### New Capabilities

### Modified Capabilities
- `web-portal`: add the currency switcher requirement.
- `gui-api`: add display exchange rates to the calculation response.

## Impact

- `gui/backend/api.py` (new `fx` field), `tests/test_api.py`, `gui/ui/index.html`. No dependency or data changes; reuses `src/nbp.py`.
