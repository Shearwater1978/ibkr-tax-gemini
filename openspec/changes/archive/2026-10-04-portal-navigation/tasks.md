## 1. Navigation

- [x] 1.1 Add the Reports dropdown (Dividends, Analytics, Portfolio) with open/close by click, outside click and Escape, and active highlighting; verify in the browser
- [x] 1.2 Verify existing tabs (Dashboard, Tax Calculation, Data, Coverage) and in-page links to `showPage` still work

## 2. Global controls

- [x] 2.1 Move the year selector and Calculate button into the top bar, remove them from the Tax Calculation page, and keep a single `#yearSelect`
- [x] 2.2 Make `calculate()` open the Tax Calculation page first and disable the button while running; verify with mocked success and failure responses that success lands on the Dashboard and failure keeps the error visible with previous results intact

## 3. Verification

- [x] 3.1 Verify against the real backend that years load in the top bar and `/calculate/2024` works from the Dividends view
- [x] 3.2 Run `black --check .`, `openspec validate portal-navigation --strict` and `python -m pytest tests -k "api or gui"` and verify all pass
