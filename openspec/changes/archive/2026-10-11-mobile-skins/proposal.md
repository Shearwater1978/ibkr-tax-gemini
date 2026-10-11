## Why

Users find the app complicated. Its five tabs, five-column positions table, switches and footnotes suit experienced investors but overwhelm others. Three UI concepts (Calm, Bento, Focus) simplify it in different ways, and each suits a different kind of user. Rather than force one design on everyone, the app will offer them as **skins**: complete, interchangeable user interfaces over the same data. New skins can be added later without touching the data or calculations.

## What Changes

- A skin decides the app's navigation, screen layouts, colours and fonts. All skins show the same data and numbers, computed by the same shared code.
- Four skins ship in this change:
  - **Classic:** today's UI, unchanged, for experienced users.
  - **Calm:** light and minimal. Three tabs (Home, Income, Activity); import behind "+", settings behind the avatar, options in bottom sheets.
  - **Bento:** dark tile dashboard. Four tabs (Home, Income, Activity, Data) in a floating bar; every key number in a labelled tile.
  - **Focus:** plain-language summaries, insight cards and an allocation donut. Two tabs (Today, Income) and a "+" menu for data, activity and settings.
- **Choosing a skin:**
  - On first launch, the user picks a skin from previews.
  - It can be changed any time in Settings.
  - The choice applies at once and is remembered.
- **Feature parity:** every skin must give access to everything the app can do: holdings, position details, dividends, activity, import, backup and keys, price settings, currency view, sold positions and data deletion. A skin may only arrange or hide these behind taps.
- Security stays outside the skins: the app lock, screenshot blocking and the encrypted storage are shared and behave the same in every skin.

## Capabilities

### New Capabilities
- `mobile-skins`: The skin system: catalog, first-launch choice, switching in Settings, persistence, feature parity, shared data and security.
- `mobile-skin-calm`: Layout and navigation of the Calm skin.
- `mobile-skin-bento`: Layout and navigation of the Bento skin.
- `mobile-skin-focus`: Layout and navigation of the Focus skin, including its generated summaries.

### Modified Capabilities
<!-- The current UI requirements (mobile-portfolio-overview, mobile-portfolio-analytics capabilities) live in unarchived changes and stay valid for the Classic skin; the new skins must meet the same data rules through the parity requirement in mobile-skins. -->

## Impact

- Android app:
  - shared state holders move out of today's screens, so every skin reads the same data;
  - a `Skin` contract and one package per skin (`ui/skins/classic`, `calm`, `bento`, `focus`);
  - the skin preference, a first-launch picker and a Settings entry.
- No change to the database, import, prices, FX, FIFO or backups. No new network requests or dependencies; system serif fonts are used where a skin needs a serif.
- iOS stays paused; the navigation contract in `mobile/README.md` becomes per skin.
- Work is split into PRs:
  1. Infrastructure plus Classic.
  2. Calm, with the picker.
  3. Bento.
  4. Focus.
