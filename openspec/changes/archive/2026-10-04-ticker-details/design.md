## Context

The portal keeps the last calculation only in page memory, so a new browser tab has no data. FIFO realized records (`ticker`, `sale_date`, `quantity`, `sale_price`, `currency`, `sale_amount`, `cost_basis`, `profit_loss`) are available in `process_yearly_data` but only aggregated into `by_ticker`. Dividends and open lots are already in the response.

## Goals / Non-Goals

**Goals:**
- A ticker page in a new tab with sales, dividends, and open lots of the calculated year.
- A fast hover card using data already in the browser.

**Non-Goals:**
- Market prices, "% of assets" by market value (only by cost basis is possible), charts, or history of years other than the calculated one.
- Purchases of lots that were fully sold in earlier years.
- Fixing broker placeholder symbols (e.g. `2682320D` for OKE); tracked separately.

## Decisions

- The page is the same `index.html` opened with `?ticker=SYMBOL`; it renders a compact ticker view instead of the tab layout, with a link back to the portal. No new route or server code beyond the `sales` field.
- On every successful calculation the UI saves `{year, fx, by_ticker, sales, dividends, inventory}` to `localStorage`; the ticker page reads it and shows an empty state when there is none. This avoids a slow recalculation per ticker.
- The ticker page and hover card use the display currency chosen in the top bar (stored in `localStorage`, set by the currency-switcher change).
- Share of total open cost = ticker open cost / total open cost of all tickers, shown as a percentage with two decimals; total result = realized P&L + dividends, as in Analytics.
- Links use `target="_blank"` with `rel="noopener"`. In the Portfolio table the lot expander becomes a separate arrow so the ticker itself is the link.
- The hover card is a single reused element positioned near the pointer, shown on hover and keyboard focus, hidden on leave and Escape; ticker text is escaped.
- `sales` entries: ticker, sale_date, quantity, sale_price, currency, sale_amount_pln, cost_basis_pln, profit_loss_pln.

## Risks / Trade-offs

- `localStorage` can be unavailable or full; the UI then still works but links open an empty-state ticker page.
- A snapshot can be stale if another tab recalculates; the ticker page shows the snapshot's year so this is visible.
- Aggregating by ticker symbol merges instruments that share a symbol.
