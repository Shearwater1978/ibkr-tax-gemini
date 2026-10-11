## Why

The app will support more brokers and, later, conversion into the user's own currency. Today it names one broker (IBKR) and one rate source (NBP) in its name and on many screens. That ties the app to both and confuses users of other brokers. Separately, Income screens with many years of data show too many year buttons.

## What Changes

- **Neutral identity:** the app is called **Portfolio Companion**. Fixed texts name no broker and no rate source.
  - Converted amounts say "approximate" and give the date of the exchange rates, without saying whose rates they are.
  - The app's internal identifier does not change, so installed apps update in place.
- **Broker choice:**
  - The user chooses their broker at first launch, after the skin, and can change it in Settings. Only supported brokers can be chosen; for now that is Interactive Brokers.
  - Broker-specific wording, such as "IBKR" or "Activity Flex Query CSV", appears only once a broker is chosen.
  - Adding reports asks for the broker first if none is chosen.
  - Existing users who already imported reports get Interactive Brokers preselected.
- **New backup folders** are named "Portfolio Companion backups". A backup location chosen earlier keeps its folder.
- **Year selection:** every skin shows at most three years directly: the newest year with dividends and up to two years before it that have dividends. Other years, and "All years" where a skin offers it, sit behind a selector.

## Capabilities

### New Capabilities
- `mobile-neutral-presentation`: app name, broker-neutral and rate-source-neutral texts, and backup folder naming.
- `mobile-broker-choice`: choosing the broker at first launch and in Settings, supported brokers, broker-specific wording, and import gated on a chosen broker.
- `mobile-year-selection`: the year choices shown on income screens in every skin.

### Modified Capabilities
<!-- Wording requirements in the unarchived changes mobile-skins and mobile-portfolio-analytics that named the rate source are aligned in place in those changes. -->

## Impact

- Android:
  - strings, including the app name and lock prompt;
  - a broker preference and a broker step in onboarding and Settings;
  - the import screen checks for a chosen broker first;
  - new backup folders get the new name;
  - a shared year-choice helper used by Classic, Calm and Bento.
- No change to parsing, storage, prices or calculations. Rates still come from the same source internally.
- Work is split into PRs:
  - **PR A:** these specs, rate-source-neutral wording, year selection.
  - **PR B:** app name, broker choice, broker-neutral texts, backup folder name.
