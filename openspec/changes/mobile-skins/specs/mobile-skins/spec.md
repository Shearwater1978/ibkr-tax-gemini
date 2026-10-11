## Purpose

Lets users choose between complete, interchangeable user interfaces ("skins") for the same portfolio data, so the app suits beginners and experienced investors alike and can gain new designs later.

## ADDED Requirements

### Requirement: Offer a catalog of skins
The app SHALL offer the skins Classic, Calm, Bento and Focus. A skin SHALL define the navigation, screen layouts, colours and fonts of the whole app after unlock. Each skin SHALL have a name, a one-line description and a preview image shown wherever skins are chosen.

#### Scenario: Catalog shown
- **WHEN** the user opens the skin picker
- **THEN** the app lists Classic, Calm, Bento and Focus, each with its name, description and preview

### Requirement: Choose a skin on first launch
On first launch, after the app lock and before the first screen, the app SHALL ask the user to choose a skin from the catalog. Calm SHALL be preselected. The user SHALL be able to continue with the preselected skin in one tap. The picker SHALL say that the skin can be changed later in Settings.

#### Scenario: First launch
- **WHEN** the app starts for the first time and is unlocked
- **THEN** the skin picker appears with Calm preselected and a note that the choice can be changed in Settings

#### Scenario: Update from a version without skins
- **WHEN** an app that already holds imported data is updated to the first version with skins
- **THEN** the app keeps the Classic skin, shows no first-launch picker, and shows a one-time notice that new skins are available in Settings

### Requirement: Change the skin in Settings
Every skin SHALL give access to a skin setting that opens the same catalog. Choosing another skin SHALL apply it immediately without restarting the app or locking it, and SHALL open the new skin's start screen.

#### Scenario: Switch skin
- **WHEN** the user picks Bento in Settings while using Calm
- **THEN** the app shows Bento's home screen at once, with the same data

### Requirement: Remember the chosen skin
The chosen skin SHALL be stored on the device and used on every later start. It is not personal data and SHALL NOT be part of encrypted backups. If the stored skin is unknown (for example after a downgrade), the app SHALL fall back to Classic.

#### Scenario: Restart
- **WHEN** the user chose Focus and restarts the app
- **THEN** the app opens in Focus after unlock

#### Scenario: Unknown skin
- **WHEN** the stored skin name is not in the catalog
- **THEN** the app uses Classic

### Requirement: Keep feature parity across skins
Every skin SHALL give access, directly or behind taps, to:
- the portfolio value with today's change;
- every held position with its details, including the profit breakdown;
- sold positions;
- the own-currency and USD views;
- today's top gainers and losers;
- dividends by year and month with withholding tax;
- the activity list with search and type filters;
- report import;
- backup encryption, recovery code and backup location;
- price key settings;
- deleting imports;
- the app version.

Data-related wording that the specs require SHALL appear in every skin. This covers approximate USD labels, exchange-rate dates, price freshness, incomplete markers, and "informational" notes on profit figures.

#### Scenario: Feature reachable in every skin
- **WHEN** a feature from the list is checked in each skin
- **THEN** it can be reached in every skin

#### Scenario: Freshness label
- **WHEN** a price is delayed or stale
- **THEN** every skin labels it as such wherever the price is shown

### Requirement: Share data, numbers and security across skins
All skins SHALL read the same portfolio, price, FX, dividend and activity data through shared code and SHALL show identical numbers for the same data. The app lock, screenshot blocking, encrypted storage and backups SHALL be shared and SHALL behave the same in every skin.

#### Scenario: Same numbers
- **WHEN** the user switches from Classic to Bento
- **THEN** the portfolio value, today's change and each position's total return are identical in both skins

#### Scenario: Lock in every skin
- **WHEN** the app returns from the background after the lock timeout in any skin
- **THEN** the shared lock screen appears before any skin content
