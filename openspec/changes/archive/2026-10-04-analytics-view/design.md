## Context

`/calculate/{year}` already returns `by_ticker` (ticker, profit, dividends, sales), used by the Dashboard. The Dividends tab established the client-side filter/sort/pagination pattern in the single-file UI.

## Goals / Non-Goals

**Goals:**
- Full per-ticker analytics table reusing `by_ticker`, in the existing dark style.
- Reuse the sort/filter/pagination approach of the Dividends tab.

**Non-Goals:**
- Cost basis, current value, price growth and daily change columns of the reference: that data is not available from the tax calculation.
- Sub-tabs (Diversification, Growth, Metrics, ...), category picker, and the Assets/Portfolio tab.
- Backend changes.

## Decisions

- Total result per ticker is realized P&L plus gross dividends, computed client-side; it is labelled as pre-tax PLN.
- The table is client-side only, like Dividends, since data volume is small.
- Positive/negative values use the existing green/red sign colouring.
- Default sort is total result descending; "hide tickers without sales" is off by default.

## Risks / Trade-offs

- The view is narrower than the reference because of missing market data; acceptable and documented as a non-goal.
- Duplicated table logic with Dividends; accepted to keep the single-file vanilla UI simple.
