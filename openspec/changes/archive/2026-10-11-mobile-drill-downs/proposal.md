## Why

Two numbers on the income and data screens look tappable but lead nowhere. Users want to see where a company's dividends came from over time, and which reports a "Reports imported" count stands for.

## What Changes

- **Payer history:** in every skin, tapping a paying company in the income list opens its full dividend history across all years: each payment with date, gross amount, withholding tax and net amount in the payment currency, plus totals. A withholding-tax correction booked on a later date shows as its own line.
- **Report list from the count:** in every skin that shows a count of imported reports, tapping the count opens the list of imported reports with the same per-report delete as the import screen.

## Capabilities

### New Capabilities
- `mobile-payer-history`: the per-company dividend history and where it opens in each skin.
- `mobile-report-list`: opening the imported-reports list from a report count.

### Modified Capabilities
<!-- The parity list in the unarchived change mobile-skins gains "a company's dividend history" in place. -->

## Impact

- Android: a pure history builder next to the dividend views, a history screen or sheet per skin, and a tappable report count. No change to parsing, storage, prices or calculations.
- Work is split into PRs:
  - **PR A:** these specs and payer history.
  - **PR B:** the report list from the count.
