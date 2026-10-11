## Why

New users do not know which IBKR report to download. The app and its documentation call the format "IBKR Activity Flex Query CSV", but the parser reads the layout of IBKR's **Activity Statement** in CSV format: the section name in the first column, "Header" or "Data" in the second, and Activity Statement column names such as "T. Price", "Comm/Fee", "Listing Exch" and "Field Name". A Flex Query export is laid out differently. The parser also matches English section names, so a statement in another language does not import.

## What Changes

- The format is named **IBKR Activity Statement (CSV, English)** wherever the app or documentation names it.
- The import screen offers **"How to get this report"**: short steps for downloading the statement from the IBKR portal, shown only when Interactive Brokers is the chosen broker.
- The user guide gets a detailed section with the same steps, the period to choose per year, and how to move the files to the phone.
- No change to parsing or to which files are accepted.

## Capabilities

### New Capabilities
<!-- none -->

### Modified Capabilities
- `mobile-broker-choice`: the import screen names the format as an Activity Statement and explains how to get it.
- `mobile-neutral-presentation`: the allowed IBKR format wording changes accordingly.

## Impact

- Android: one string renamed, an expandable help block on the shared import screen, the user guide and mobile README. No parser, storage or calculation change.
