## Context

The UI is a single HTML file with `showPage(id)` toggling `.page` and `.tab` elements. `calculate()` reads `#yearSelect` and finds its button with `button[onclick="calculate()"]`, and reports progress in `#loader` and `#status` on the Tax Calculation page.

## Goals / Non-Goals

**Goals:**
- Dropdown grouping for the report tabs, usable by mouse and keyboard.
- Year selector and Calculate in the top bar, available on every page.

**Non-Goals:**
- Search, account, and currency controls of the reference (no data behind them).
- Merging Tax Calculation, Data, and Coverage (a separate follow-up change).
- Changes to API or calculation logic.

## Decisions

- Dropdown is plain HTML/CSS/JS: a button toggling a menu, closed by outside click or Escape; the parent button is highlighted when one of its pages is active.
- The existing `#yearSelect` element and `calculate()` are moved, not duplicated, so there is one source of truth for the year.
- `calculate()` first navigates to the Tax Calculation page so the loader and any error are visible; success still switches to the Dashboard.
- The Tax Calculation page keeps loader and status but drops its own year and Calculate controls.

## Risks / Trade-offs

- Starting a calculation from a report page jumps away from it; accepted so progress and errors are never hidden.
- The Tax Calculation page becomes thin until the follow-up merge change.
