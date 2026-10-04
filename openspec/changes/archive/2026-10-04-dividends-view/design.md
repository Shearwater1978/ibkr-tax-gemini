## Context

The calculate endpoint already builds the dividend records (`ex_date`, `ticker`, `gross_amount_pln`, `tax_withheld_pln`, `currency`, `rate`) in memory for the requested year. A calculation can be slow because it fetches NBP rates. See proposal.md for motivation and the `web-portal` spec for the existing tabs.

## Goals / Non-Goals

**Goals:**
- Per-payment dividend review inside the existing portal with no new dependencies.
- Reuse the calculation result so no second calculation is needed.

**Non-Goals:**
- No market data (P/E, EPS, yield, upcoming events).
- No new tax logic: net is displayed as gross minus withheld and is not a tax liability.
- No export from this tab; Excel and PDF reports remain the export path.

## Decisions

- **Extend `/calculate/{year}` with `dividends`** instead of a new `/dividends/{year}` endpoint. A separate endpoint would recompute NBP conversions. Trade-off: a larger response, a few hundred small records.
- **Compute net in the client** (gross minus withheld) to keep the API contract to source values only.
- **Client-side sort, filter and pagination.** The data set is small, so no server paging. Totals are computed over the full list, and over the filtered list when a filter is active.
- **Reuse the existing escape helper and empty-state pattern** from the Dashboard so ticker text stays safe and behavior is consistent.
- **Keep last good result on failure**, matching the Dashboard.

## Risks / Trade-offs

- [Floating point sums displayed with rounding drift] -> Round to 2 decimals for display only; values come from the backend already rounded for reports.
- [Large payment lists slow rendering] -> Pagination renders at most one page of rows.
- [`index.html` keeps growing] -> Acceptable for now; revisit splitting scripts if another tab is added.

## Migration Plan

No migration. Older clients ignore the new `dividends` field. Rollback is reverting the two source files.
