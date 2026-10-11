## 1. Rate wording and year selection (PR A)

- [x] 1.1 Replace rate-source names in all strings with neutral wording ("exchange rates of <date>"), and verify no visible string mentions NBP.
- [x] 1.2 Add `YearChoices` (direct years and the rest) and verify with unit tests for the spec scenarios: five years, a gap, an older year chosen.
- [x] 1.3 Use it in Classic (chips plus an "Older" menu) and Bento (pills plus an "Older" menu); Calm keeps its year sheet. Verify on the emulator with synthetic dividends over four or more years.

## 2. App name and broker choice (PR B)

- [ ] 2.1 Rename the app to "Portfolio Companion" (launcher, lock prompt, recovery-code clip label), name new backup folders "Portfolio Companion backups", and verify that an existing backup location keeps its folder.
- [ ] 2.2 Add the broker catalog and preference, the broker step after the skin on first launch, the Settings entry, and Interactive Brokers preselected for existing users with data. Verify on the emulator for a fresh install and an update.
- [ ] 2.3 Make import ask for the broker first and use broker-neutral texts without a chosen broker. Verify on the emulator that no visible text mentions IBKR until Interactive Brokers is chosen.
