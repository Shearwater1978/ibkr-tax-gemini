# Proposal: Prevent Silent Calculation Errors

## Problem

The calculation pipeline silently substitutes an NBP rate of `1.0` after lookup failures and records sales even when inventory cannot cover the sold quantity. Imported splits are currently forced to ratio `1`. These behaviors can produce materially wrong tax values without a visible failure.

## Goal

Make calculation uncertainty explicit and preserve correctness: missing FX rates and unmatched quantities must be surfaced as structured diagnostics or blocking errors, and reports/API responses must not claim a clean result when required inputs are unreliable.

## Non-goals

- Replacing the NBP service or changing the legal T-1 rule.
- Changing FIFO from first-in-first-out.
- Adding tax advice or filing submission.
- Changing or introducing the Electron/FastAPI GUI layer; API behavior belongs to a separate GUI integration change.