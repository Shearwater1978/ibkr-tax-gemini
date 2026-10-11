## Context

Strings currently name IBKR (the app name, import texts, the lock prompt, the backup folder) and NBP (rate labels). The skins share `ui/common` for import and Settings, and `UiPreferences` for small non-sensitive settings. Income screens: Classic shows a chip per year, Bento shows a pill per year, and Calm uses a year sheet.

## Goals / Non-Goals

**Goals:**
- The rules live in one place: a broker catalog, one helper for year choices, and neutral strings.
- Updates keep the app, its data and existing backup folders working.

**Non-Goals:**
- Supporting a second broker.
- Converting into the user's currency. Both come later; the wording is prepared for them.

## Decisions

### Broker catalog
- `BrokerId { INTERACTIVE_BROKERS }` with display names and the report-format text.
- `UiPreferences.broker` stores the name; null means not chosen.
- Import, empty states and onboarding read the chosen broker to pick their wording: neutral strings without one, broker-specific strings with one.
- **Alternative considered:** keep IBKR texts and hide them later. Not chosen: the request is to show no broker until one is chosen.

### First launch and updates
- `SkinRoot` already decides between the picker and the app.
- The broker step runs after the skin step when there is no stored broker and no data.
- With existing data, Interactive Brokers is stored silently, mirroring how Classic is kept for existing users.

### Backup folder
- Only the constant for new locations changes. `BackupLocationStore` already stores the subfolder per location, so existing users keep "IBKR Tax Assistant backups".

### Year choices
- A pure `YearChoices.of(years)` returns the direct years (the newest year with data, plus years with data within two years before it) and the rest.
- Classic and Bento render the direct years as chips or pills plus an "Older ▾" menu. Calm already uses a sheet, which counts as the selector.
- It is unit-tested with the spec scenarios.

## Risks / Trade-offs

- [The launcher name changes on users' phones] → Intended. The app identifier is unchanged, so data and updates are unaffected.
- [Two folder names exist side by side] → Only for users who choose a new location. Old backups stay where they are and remain restorable.
