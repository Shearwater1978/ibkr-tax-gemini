## Context

After unlock, `MainActivity` shows `AppScaffold`: five tabs and the screens in `ui/screens/`. Each screen loads its own data inside composables:
- `loadPortfolio` and `refreshMarketData` in `PortfolioScreen`;
- `produceState` blocks in Dividends, Imports and Settings.

The calculations (`MainPage`, `Performance`, `Dividends`, `TopMoversRanking`, `PriceService`, `OperationsRepository`) are already pure or repository code and do not depend on the UI. `UiPreferences` stores small non-sensitive settings. Clickable prototypes of Calm, Bento and Focus exist locally (git-ignored `design-concepts/`).

See `proposal.md` for motivation and `specs/` for the required behavior.

## Goals / Non-Goals

**Goals:**
- Every skin renders the same state from shared state holders, so the numbers are identical by construction.
- A new skin is a new package plus one catalog entry. No change to data, calculations or security code.
- Classic stays pixel-identical to today's UI.

**Non-Goals:**
- User-made or downloadable skins, colour pickers, or per-screen mixing of skins.
- Tablet or landscape layouts.
- iOS (paused).

## Decisions

### Shared state holders instead of per-screen loading
- Move data loading out of the composables into a few state holders, created once per app session in `AppContainer`:
  - `PortfolioState`: rows, performance, summary, top movers, rates, refresh progress;
  - `IncomeState`: years and per-year dividends;
  - `ActivityState`: paged operations with search and filters;
  - `DataState`: import, backup, keys, prices, version.
- Each holder exposes a Compose `State`/`StateFlow` plus action functions (refresh, import, set currency view, delete…).
- Today's Classic screens are refactored to read them first (PR 1), which proves the holders cover everything before a new skin depends on them.
- **Alternative considered:** Jetpack ViewModel per screen. Not chosen: the app has no ViewModel infrastructure yet, and session-wide holders are simpler to share between very different navigation graphs.

### A small `Skin` contract
```kotlin
interface Skin {
    val id: SkinId; val nameRes: Int; val descriptionRes: Int; val previewRes: Int
    @Composable fun App(app: AppStates)   // owns its theme, navigation and screens
}
```
- `MainActivity` keeps the lock screen and screenshot protection outside the skin, then calls `skin.App(states)`.
- Each skin wraps itself in its own `MaterialTheme`, with colours, typography and shapes. Material 3 components stay usable inside every skin.
- Shared building blocks live in `ui/common` and are reused by every skin, styled by the skin's theme: the Settings list (keys, backup location, price key, delete, version, skin picker), the import flow, and the formatting helpers.

### Skin preference and first launch
- `UiPreferences.skin` stores a `SkinId` name. Unknown names map to Classic.
- On the first unlock with no stored skin:
  - if there is no imported data, the first-launch picker is shown;
  - if data already exists (an update), Classic is stored and a one-time notice is shown.
- Switching is a state change at the root (`skin` as Compose state), so the new skin draws at once with the same holders. No activity restart and no new lock.

### Previews
- The picker shows small rendered previews: the skin's own home composable at reduced scale, fed by a fixed synthetic state. This avoids screenshots in resources, and previews never show the user's data.
- **Alternative considered:** static PNG previews. Not chosen: they go stale when a skin changes.

### Fonts and colours
- Focus uses `FontFamily.Serif`, the system serif, for headlines. No font files are bundled.
- Bento is dark-only. Calm and Focus are light-only. Classic keeps following the system.
- All text meets WCAG AA contrast in the skin's own palette.

### Focus sentences
- A pure `Summaries` object builds the sentences from `PortfolioState` and `IncomeState`: up or down with amount, top contributors, total return, the incomplete note, and the income change.
- Unit tests pin the wording for up, down, flat and missing-price cases. English only for now, like the rest of the app.

## Risks / Trade-offs

- [Four UIs to maintain] → Shared state holders and `ui/common` keep each skin to layout code. A parity checklist test (every `AppStates` action reachable) runs per skin.
- [Refactor regressions in Classic] → PR 1 changes data plumbing only. Emulator checks compare Classic screens before and after, using the same synthetic data.
- [Bento's floating bar hides content] → Bottom content padding equals the bar height plus margin, which the Bento spec requires.
- [Large PRs] → Split as described below. Each skin PR stays under about 1,500 lines by reusing `ui/common`.

## Migration Plan

- **PR 1:**
  - state holders;
  - the `Skin` contract;
  - Classic as the only skin;
  - the preference;
  - existing users stay on Classic.
- **PR 2:**
  - Calm;
  - the Settings picker;
  - the first-launch picker for new installs;
  - the one-time notice for existing users.
- **PR 3:** Bento.
- **PR 4:**
  - Focus;
  - its sentences;
  - user documentation.
- **Rollback:** removing a skin from the catalog makes users of it fall back to Classic. Data is untouched.

## Open Questions

- Should the preselected skin on first launch stay Calm, or follow usage feedback later? This changes only one constant.
