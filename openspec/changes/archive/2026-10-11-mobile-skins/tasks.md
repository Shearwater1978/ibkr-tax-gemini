## 1. Skin infrastructure and Classic (PR 1)

- [x] 1.1 Move data loading into session-wide state holders (`PortfolioState`, `IncomeState`, `ActivityState`, `DataState`) and verify with unit tests that they produce the same values as today's screen code on synthetic data.
- [x] 1.2 Add the `Skin` contract, the skin catalog and `UiPreferences.skin` with the fallback to Classic, and verify with unit tests for stored, missing and unknown values.
- [x] 1.3 Make today's UI the Classic skin on top of the state holders, move the Settings list and import flow into `ui/common`, and verify on the emulator that every Classic screen looks and behaves as before.

## 2. Calm and skin choice (PR 2)

- [x] 2.1 Build the Calm skin (Home with sheets, Income, Activity) on the state holders and verify on the emulator with synthetic data against `mobile-skin-calm`.
- [x] 2.2 Add the skin picker with drawn previews to Settings and as the first-launch step, plus the one-time notice for existing users, and verify on the emulator: fresh install shows the picker with Calm preselected; an update keeps Classic and shows the notice; switching applies at once without a lock.
- [x] 2.3 Add a parity check: each skin declares where every feature lives, and a unit test fails if one is missing; verify it passes for Classic and Calm.

## 3. Bento (PR 3)

- [x] 3.1 Build the Bento skin (tiles, holdings with weight, position detail, Income comparison, Data screen) and verify on the emulator against `mobile-skin-bento`, including that the last list row scrolls above the tab bar.

## 4. Focus (PR 4)

- [x] 4.1 Implement the Focus sentences as pure code and verify with unit tests for up, down, flat, missing prices and income change.
- [x] 4.2 Build the Focus skin (summary, insight cards, donut, rising/falling holdings, stories, income timeline, "+" menu) and verify on the emulator against `mobile-skin-focus`.
- [x] 4.3 Update user documentation and the navigation contract in `mobile/README.md` per skin, and verify they match the specs.
