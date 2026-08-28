## Context

The current FIFO engine already reconstructs inventory by processing buys, transfers, splits, and other corporate actions, but its sell path is intentionally mutating and requires price/rate data. A coverage check must answer a narrower question: quantity and lot availability in imported history as of a date. See proposal.md and the coverage spec for the user-facing contract.

## Goals / Non-Goals

**Goals:**

- Reuse the existing chronological FIFO event handling so the check agrees with tax calculation.
- Compare multiple planned sales independently and return traceable lot evidence.
- Keep the operation read-only with respect to the database and separate from realized P&L.
- Expose both a CLI command and GUI/API operation with stable structured output.

**Non-Goals:**

- Calculating sale proceeds, cost basis, profit, tax, FX, or commissions.
- Creating a simulated SELL row in the database.
- Automatically downloading missing broker reports or deciding whether a tax return is legally acceptable.

## Decisions

1. Add a dedicated coverage service/function that receives normalized planned-sale items and raw historical transactions. Do not call the mutating sell operation to simulate the check.
2. Build inventory once through the as-of date, applying BUY, TRANSFER, SPLIT, STOCK_DIV, MERGER, and SPINOFF events with the existing ordering rules. For each request, walk FIFO lots without removing them from the shared inventory, producing a lot trace.
3. Normalize ticker aliases consistently with calculation and reject non-positive quantities, invalid dates, and duplicate/ambiguous request items with actionable validation errors.
4. Define `COVERED` when available quantity is greater than or equal to requested quantity, `PARTIAL` when available is positive but insufficient, and `NOT_COVERED` when available is zero. Overall status is complete only when every item is covered.
5. Add a CLI option that accepts a JSON file or repeated structured arguments, and add an API endpoint with request/response models. The GUI can render the returned structured data without calculation logic.
6. Include source identity from the database query in lot evidence where available. Do not expose prices or FX values because they are outside this check's purpose.

## Risks / Trade-offs

- [Risk] Coverage can be correct only if imported corporate actions are complete → show as-of date and lot/source evidence, and distinguish missing history from a zero holding.
- [Risk] A separate non-mutating implementation can diverge from FIFO → share event normalization/order helpers with the normal calculation path and add parity tests.
- [Risk] Users may confuse coverage with a tax result → label output as preflight evidence and explicitly state that no sale was persisted and no tax was calculated.
- [Risk] Multiple requests may overlap → evaluate each request against the same pre-sale inventory snapshot unless the user explicitly asks for sequential planned sales.

## Migration Plan

1. Implement the service and result models without changing the transactions schema.
2. Add CLI/API adapters and focused tests, then run the existing full suite.
3. Add the GUI view and smoke workflow.
4. Roll back by removing the new command/endpoint and service; existing import and tax calculation remain unchanged.
