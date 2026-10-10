## 1. Operations (PR 1)

- [ ] 1.1 Add a read-only analytics repository that pages operations newest first, filtered by type and ticker substring, and verify with an instrumented test on 20,000 synthetic rows that a page loads in under 200 ms and filters combine.
- [ ] 1.2 Add the Operations tab (bottom bar with five tabs, list, search field, type chips, empty and no-match states) and verify on the emulator with synthetic reports that search, filters and "clear" behave as in `mobile-operations`.

## 2. Dividends (PR 2)

- [ ] 2.1 Implement dividend aggregation by month, year and position (gross, withholding tax, net per currency, approximate USD totals with incomplete marking) and verify with unit tests, including a tax correction dated in a later month and a missing rate.
- [ ] 2.2 Add a Canvas bar chart composable with per-bar accessibility labels and verify a Compose UI test reads the month values.
- [ ] 2.3 Add the Dividends tab (year selector, monthly chart, three-year comparison, per-position list, empty state, covered months) and verify on the emulator with synthetic reports spanning two years.

## 3. Position performance (PR 3)

- [ ] 3.1 Extend the FIFO pass to report gains from each sell, and verify the open-lot parity test on existing fixtures and new tests for partial sales, splits and identity changes.
- [ ] 3.2 Implement the summary (value, invested, total profit and percentage, today, 12-month net dividends) and per-position breakdown, and verify with unit tests, including missing prices and unmatched sells.
- [ ] 3.3 Add the summary cards, the position details bottom sheet and the remembered "Show sold positions" switch, and verify on the emulator that a sold synthetic instrument appears only with the switch on and that the cards include its gain.

## 4. USD view (PR 4)

- [ ] 4.1 Move the cross-rate conversion into a shared converter used by the header and rows, and verify existing header-total tests still pass and new tests cover row conversion and missing rates.
- [ ] 4.2 Add the remembered "Own currency / USD" switch with approximate labels and USD-based sorting, and verify on the emulator with a EUR and a USD synthetic position.

## 5. Top movers (PR 5)

- [ ] 5.1 Implement ranking by daily change percent (up to five each, no overlap, only positions with a previous close) and verify with unit tests, including all-up days and no data.
- [ ] 5.2 Add the collapsible top-movers section with freshness labels, and verify on the emulator that it hides without price data and shows gainers and losers with Yahoo prices.

## 6. Documentation

- [ ] 6.1 Update the mobile user documentation (with `mobile-app-core` task 5.3) to describe the new tabs, the approximate USD conversion and that amounts are informational, and verify it matches these specs.
