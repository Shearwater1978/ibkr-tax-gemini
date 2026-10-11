# mobile-year-selection Specification

## Purpose
Keeps year choices on income screens short and predictable for users with many years of data, in every skin.

## Requirements

### Requirement: Show at most three years directly
Income screens in every skin SHALL show at most three year choices directly: the newest year that has dividends and up to two years before it that have dividends, newest first or oldest first consistently within a skin. All other years SHALL be reachable through a selector (for example "Older ▾" or a year sheet), together with "All years" where a skin offers it. Selecting an older year SHALL show that year without adding a new direct choice.

#### Scenario: Five years of data
- **WHEN** dividends exist in 2022, 2023, 2024, 2025 and 2026
- **THEN** 2024, 2025 and 2026 are shown directly and 2022 and 2023 are in the selector

#### Scenario: Gap in the recent years
- **WHEN** dividends exist in 2021, 2023 and 2026
- **THEN** 2026 is shown directly, and 2023 and 2021 are in the selector because they are more than two years before 2026

#### Scenario: Older year chosen
- **WHEN** the user picks 2022 from the selector
- **THEN** the screen shows 2022 and the selector shows 2022 as the current choice
