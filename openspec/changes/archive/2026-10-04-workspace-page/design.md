## Context

The three pages (`page-tax`, `page-data`, `page-coverage`) are independent `.page` blocks switched by `showPage`. The top bar year selector and Calculate button already live outside them, and `calculate()` calls `showPage('page-tax')`. Several empty-state buttons also link to `page-tax`.

## Goals / Non-Goals

**Goals:**
- One Workspace page containing all three sections with their existing element ids and handlers.
- Keep calculation progress and errors visible when a calculation starts.

**Non-Goals:**
- Redesigning the contents of the sections.
- Collapsible sections, sub-tabs, or any API change.
- Investigating data issues reported for specific tickers (tracked separately).

## Decisions

- Move the existing section markup into one `page-workspace` container instead of rewriting it, so ids used by JS (`status`, `loader`, `import-status`, `ib-status`, `coverage-results`, ...) stay valid.
- Keep the page id mapping simple: remove `page-tax`, `page-data`, `page-coverage` and point every `showPage('page-tax')` to `page-workspace`.
- Section order: Tax Calculation, Data, Coverage, matching the previous tab order.
- Breadcrumb reads "Tax Architect / Workspace".

## Risks / Trade-offs

- A longer page needs scrolling to reach Coverage; accepted since sections are short.
- Calculation progress sits at the top of the Workspace page, so it remains visible without scrolling.
