## 1. API

- [ ] 1.1 Add `fx` (date, USD and EUR rates via `src/nbp.py`, date capped at today, failed currencies omitted) to the `/calculate/{year}` response; verify with tests for present rates, one missing rate, and unchanged existing fields

## 2. Switcher

- [ ] 2.1 Add the currency selector to the top bar with PLN only before a calculation, persisted choice, fallback to PLN, and the display-only note; verify in the browser
- [ ] 2.2 Add a shared money formatter and use it for Dashboard cards, per-ticker table and donut labels; verify values against mock rates
- [ ] 2.3 Use it in Dividends, Analytics, and Portfolio including totals; verify originals (rate, currency, cost per share) stay unconverted
- [ ] 2.4 Verify a missing rate disables that currency and a failed calculation keeps the previous selection and data

## 3. Verification

- [ ] 3.1 Verify against the real backend that `/calculate/2024` and the in-progress year return sensible rates and the views switch without layout problems
- [ ] 3.2 Run `black --check .`, `openspec validate currency-switcher --strict` and `python -m pytest tests -k "api or gui"` and verify all pass
