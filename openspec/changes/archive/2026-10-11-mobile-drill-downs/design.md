## Context

Income lists, report counts and dividend totals looked tappable but led nowhere. Each skin already has its own income screen and import screen on top of the shared state holders (`mobile-skins`).

## Goals / Non-Goals

**Goals:**
- One pure history builder, so every skin shows the same lines and totals.
- Reuse each skin's existing sheets and screens; no new data or storage.

**Non-Goals:**
- Converting a company's history to USD, or adding up different currencies.
- New report details beyond what the import screen already shows.

## Decisions

- **History builder:** `Dividends.history` groups an instrument's dividend and tax rows by booking date, newest first, and sums totals per currency. A tax correction on its own date stays its own line, matching how the monthly views count it.
- **Where it opens:** each skin keeps its idiom. Bento uses a full screen on the Income tab (so Back returns to Income); Calm, Classic and Focus use a sheet. From a position, the history opens only when the position has dividends.
- **Report list:** `ImportsScreen` gains a list-only mode, so the delete actions and dialogs stay in one place.

## Risks / Trade-offs

- [Sheets stacked on sheets] → In Calm and Classic the history replaces the position sheet instead of stacking.
- [Many bookings for old holdings] → The history is a scrolling list; totals are shown first.
