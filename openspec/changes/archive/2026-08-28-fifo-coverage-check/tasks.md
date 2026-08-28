## 1. Coverage Core

- [x] 1.1 Define planned-sale request/result models with validation and `COVERED`/`PARTIAL`/`NOT_COVERED` statuses, then verify invalid dates, tickers, and quantities are rejected
- [x] 1.2 Implement a read-only inventory snapshot builder that reuses normal FIFO event ordering through an as-of date, then verify corporate actions and splits affect available quantity
- [x] 1.3 Implement FIFO lot tracing without mutating the snapshot, then verify oldest lots are listed first and quantities sum to the covered amount
- [x] 1.4 Add tests proving coverage checks never insert SELL rows or alter subsequent normal calculation inventory

## 2. CLI and Report

- [x] 2.1 Add a CLI command accepting one or more planned assets from structured arguments or JSON, then verify single- and multi-asset invocations
- [x] 2.2 Produce human-readable and machine-readable coverage reports with requested, available, missing, as-of, status, and lot evidence fields, then verify mixed-result output
- [x] 2.3 Document that coverage is preflight evidence and does not calculate tax or persist a sale, then verify the documented command works from the project root

## 3. GUI/API

- [x] 3.1 Add an API endpoint and request/response models for coverage checks, then verify validation errors and one result per requested ticker
- [x] 3.2 Add a dashboard view for planned assets, as-of date, statuses, missing quantities, and FIFO lot evidence, then verify it does not modify the normal calculation result
- [x] 3.3 Show explicit empty-history and incomplete-result states, then verify users can distinguish missing broker history from zero holdings

## 4. Verification

- [x] 4.1 Add parity tests comparing coverage lot ordering and available quantities with normal FIFO inventory reconstruction, then verify valid existing calculations are unchanged
- [x] 4.2 Run `pytest`, `black --check .`, API tests, and GUI/npm checks, then verify all quality gates pass
- [x] 4.3 Run a smoke check for multiple planned assets against imported archive fixtures, then verify no database rows or report totals change
