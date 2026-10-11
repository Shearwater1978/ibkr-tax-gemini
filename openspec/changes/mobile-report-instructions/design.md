## Context

The Android parser (`FlexQueryParser`, mirroring the desktop `src/parser.py`) reads IBKR's Activity Statement CSV: every row starts with the section name ("Trades", "Dividends", "Withholding Tax", "Corporate Actions", "Financial Instrument Information", "Account Information") and "Header" or "Data". It also accepts a few Flex field names ("TradePrice", "IBCommission") as alternative column names, but not the Flex Query CSV layout with section codes. Users were told to export a "Flex Query".

## Decisions

- **Name what works.** The format is called "IBKR Activity Statement (CSV, English)"; "English" because section names are matched literally. The parser class keeps its name; renaming code is not needed for users.
- **Steps per broker.** The steps live in a string array per broker (`BrokerId.reportStepsRes`), shown on the shared import screen in an expandable block, so every skin gets them and a future broker brings its own.
- **Portal menu names may change.** The steps use IBKR's current menu names (Performance & Reports → Statements → Activity Statement) and say what to choose, not where to tap pixel by pixel; the user guide notes that labels can differ slightly.

## Risks / Trade-offs

- [IBKR renames menus] → Steps describe the report by name and options (Activity Statement, CSV, English), which IBKR keeps stable; update the strings when it changes.
- [Users exported Flex Queries before] → Import rejects them as before; the new steps tell them what to download instead.
