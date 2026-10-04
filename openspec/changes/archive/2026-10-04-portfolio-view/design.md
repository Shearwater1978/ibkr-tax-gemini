## Context

`process_yearly_data` already returns `inventory` (the open lots from `FIFOCalculator.get_current_inventory`), passed to exports and counted in `open_positions_count`. Lots carry ticker, buy_date, quantity, cost_per_share (original currency), total_cost (PLN), currency.

## Goals / Non-Goals

**Goals:**
- Show holdings at the end of the calculated year with their PLN cost basis.
- Reuse the client-side filter/sort/pagination pattern of the Dividends and Analytics tabs.

**Non-Goals:**
- Market value, price growth, rating, next dividend and other market data (not available offline).
- A currency toggle, a sold-assets toggle, and per-lot editing.
- Changes to FIFO logic or exports.

## Decisions

- Expose lots as returned by FIFO under `inventory`, rather than pre-aggregated, so the UI can show both per-ticker totals and expandable lots; aggregation is client-side.
- Average cost is total PLN cost divided by total quantity; quantity may be fractional so it is shown with up to 4 decimals.
- Tickers sharing a symbol but with different ISINs are aggregated by ticker only, since the UI does not show ISIN.
- Existing response fields are unchanged.

## Risks / Trade-offs

- Response size grows with lot count (hundreds of lots at most for a retail account); acceptable.
- Aggregating by ticker may merge distinct instruments with the same symbol; acceptable for display and noted as a limitation.
